import os
import json
import time
import logging
from datetime import datetime, timezone
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException

LOKI_URL = os.getenv("LOKI_URL", "http://loki.observability.svc.cluster.local:3100")
PROMETHEUS_URL = os.getenv(
    "PROMETHEUS_URL",
    "http://kube-prometheus-stack-prometheus.observability.svc.cluster.local:9090",
)

app = FastAPI(title="Shalm AI Observability Agent", version="1.0.0")
log = logging.getLogger("uvicorn.error")


async def _loki_query(logql: str, hours: int = 1) -> list[dict]:
    now_ns = int(time.time() * 1e9)
    start_ns = int((time.time() - hours * 3600) * 1e9)
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(
            f"{LOKI_URL}/loki/api/v1/query_range",
            params={"query": logql, "start": start_ns, "end": now_ns, "limit": 500, "direction": "backward"},
        )
        resp.raise_for_status()
        return resp.json().get("data", {}).get("result", [])


async def _prom_query(promql: str) -> list[dict]:
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{PROMETHEUS_URL}/api/v1/query", params={"query": promql})
        resp.raise_for_status()
        return resp.json().get("data", {}).get("result", [])


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/summary")
async def summary():
    try:
        streams = await _loki_query('{namespace="quarkus-api"}', hours=1)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"Loki unavailable: {exc}")

    transfers: list[dict] = []
    errors: list[dict] = []
    error_freq: dict[str, int] = {}

    for stream in streams:
        for _ts, line in stream.get("values", []):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            status = entry.get("status", "")
            if status == "SUCCESS":
                transfers.append(entry)
            elif status in ("ERROR", "FAILED") or entry.get("level") == "ERROR":
                errors.append(entry)
                msg = entry.get("message", entry.get("msg", "unknown error"))
                error_freq[msg] = error_freq.get(msg, 0) + 1

    top_errors = sorted(error_freq.items(), key=lambda x: x[1], reverse=True)[:5]
    recent = sorted(transfers, key=lambda x: x.get("timestamp", ""), reverse=True)[:10]

    return {
        "window": "last 1 hour",
        "transfer_count": len(transfers),
        "error_count": len(errors),
        "top_errors": [{"message": m, "count": c} for m, c in top_errors],
        "recent_transfers": [
            {
                "transaction_id": t.get("transaction_id"),
                "from": t.get("from_account"),
                "to": t.get("to_account"),
                "amount": t.get("amount"),
                "status": t.get("status"),
            }
            for t in recent
        ],
    }


@app.get("/anomalies")
async def anomalies():
    checks: list[tuple[str, str, Any, str, Any]] = [
        (
            "high_latency",
            "histogram_quantile(0.95, rate(transfer_request_latency_seconds_bucket[5m]))",
            lambda v: float(v) > 0.2,
            "warning",
            lambda v: f"p95 transfer latency is {float(v) * 1000:.0f}ms (threshold: 200ms)",
        ),
        (
            "high_error_rate",
            "rate(transfer_error_count_total[5m]) / rate(transfer_request_count_total[5m])",
            lambda v: float(v) > 0.01,
            "critical",
            lambda v: f"Transfer error rate is {float(v) * 100:.1f}% (threshold: 1%)",
        ),
        (
            "pod_not_ready",
            'kube_pod_status_ready{condition="true",namespace=~"quarkus-api|quarkus-ui|fabric"} == 0',
            lambda v: float(v) == 0,
            "critical",
            lambda _: "Pod is not ready",
        ),
        (
            "crash_looping",
            'rate(kube_pod_container_status_restarts_total{namespace=~"quarkus-api|quarkus-ui|fabric"}[15m]) * 900',
            lambda v: float(v) > 3,
            "warning",
            lambda v: f"Container restarted {float(v):.0f} times in the last 15 minutes",
        ),
    ]

    detected: list[dict] = []
    query_errors: list[dict] = []

    for name, promql, threshold_fn, severity, describe_fn in checks:
        try:
            results = await _prom_query(promql)
        except Exception as exc:
            query_errors.append({"check": name, "error": str(exc)})
            continue

        for result in results:
            raw = result.get("value", [None, None])[1]
            if raw is None:
                continue
            try:
                if threshold_fn(raw):
                    detected.append({
                        "anomaly": name,
                        "severity": severity,
                        "description": describe_fn(raw),
                        "labels": result.get("metric", {}),
                    })
            except (ValueError, ZeroDivisionError):
                continue

    return {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "anomaly_count": len(detected),
        "anomalies": detected,
        "query_errors": query_errors,
    }
