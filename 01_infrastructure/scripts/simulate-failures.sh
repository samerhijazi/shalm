#!/usr/bin/env bash
# simulate-failures.sh — inject failures and generate trace traffic for Kiali/Jaeger observation

set -euo pipefail

NS_API="quarkus-api"
NS_UI="quarkus-ui"
NS_FABRIC="fabric"
API_URL="http://192.168.105.3:30800"
KIALI_URL="http://192.168.105.3:30088"
JAEGER_URL="http://192.168.105.3:30686"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Failure injection:
  kill-api              Delete the quarkus-api pod (ArgoCD respawns it)
  kill-ui               Delete the quarkus-ui pod  (ArgoCD respawns it)
  kill-peer             Scale fabric peer0-org1 to 0 (simulates peer outage)
  restore-peer          Scale fabric peer0-org1 back to 1
  latency-inject        Inject 500ms delay on quarkus-api via Istio fault injection
  latency-remove        Remove Istio latency fault injection
  flood                 Send 50 concurrent transfer requests to stress the API
  status                Show pod readiness across all shalm namespaces

Kiali / Jaeger trace observation:
  mesh-enable           Label quarkus-api + quarkus-ui namespaces for Istio sidecar
                        injection and restart pods — required before trace commands
  mesh-disable          Remove sidecar injection labels and restart pods
  trace-steady [N] [s]  Send N transfers (default 30) one per s seconds (default 1)
                        Produces steady green traffic in the Kiali graph
  trace-errors          Send 20 concurrent invalid transfers — red edges in Kiali,
                        failed spans in Jaeger
  trace-scenario        Full 4-step scenario: green → yellow (latency) → red (errors)
                        → green (recovery) — produces a complete story in Kiali + Jaeger
  trace-open            Print Kiali and Jaeger deep-link URLs for this platform
EOF
  exit 1
}

# ── Failure injection ─────────────────────────────────────────────────────────

kill_api() {
  echo "[simulate] Deleting quarkus-api pod..."
  kubectl delete pod -n "$NS_API" -l app=quarkus-api --grace-period=0 --force
  echo "[simulate] Done — watch: kubectl get pods -n $NS_API -w"
}

kill_ui() {
  echo "[simulate] Deleting quarkus-ui pod..."
  kubectl delete pod -n "$NS_UI" -l app=quarkus-ui --grace-period=0 --force
  echo "[simulate] Done — watch: kubectl get pods -n $NS_UI -w"
}

kill_peer() {
  echo "[simulate] Scaling peer0-org1 to 0 replicas..."
  kubectl scale deployment peer0-org1 -n "$NS_FABRIC" --replicas=0
  echo "[simulate] Fabric peer down. Restore with: $(basename "$0") restore-peer"
}

restore_peer() {
  echo "[simulate] Restoring peer0-org1 to 1 replica..."
  kubectl scale deployment peer0-org1 -n "$NS_FABRIC" --replicas=1
  kubectl rollout status deployment/peer0-org1 -n "$NS_FABRIC"
  echo "[simulate] Fabric peer restored."
}

latency_inject() {
  echo "[simulate] Injecting 500ms delay on quarkus-api via Istio VirtualService..."
  kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: quarkus-api-fault
  namespace: ${NS_API}
spec:
  hosts:
    - quarkus-api
  http:
    - fault:
        delay:
          percentage:
            value: 100.0
          fixedDelay: 500ms
      route:
        - destination:
            host: quarkus-api
            port:
              number: 8080
EOF
  echo "[simulate] 500ms delay active. Remove with: $(basename "$0") latency-remove"
}

latency_remove() {
  echo "[simulate] Removing Istio fault injection..."
  kubectl delete virtualservice quarkus-api-fault -n "$NS_API" --ignore-not-found
  echo "[simulate] Latency injection removed."
}

flood() {
  echo "[simulate] Sending 50 concurrent transfer requests to $API_URL..."
  for i in $(seq 1 50); do
    curl -s -o /dev/null -w "%{http_code}\n" \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-B1-001","toAccount":"ACC-B2-001","amount":1}' &
  done
  wait
  echo "[simulate] Flood complete — check Grafana for the request spike."
}

show_status() {
  for ns in "$NS_API" "$NS_UI" "$NS_FABRIC" observability; do
    echo "=== $ns ==="
    kubectl get pods -n "$ns" 2>/dev/null || echo "(empty)"
    echo ""
  done
}

# ── Kiali / Jaeger trace observation ─────────────────────────────────────────

mesh_enable() {
  echo "[mesh] Enabling Istio sidecar injection on $NS_API and $NS_UI..."
  kubectl label namespace "$NS_API" istio-injection=enabled --overwrite
  kubectl label namespace "$NS_UI"  istio-injection=enabled --overwrite
  echo "[mesh] Restarting pods to inject Envoy sidecars..."
  kubectl rollout restart deployment -n "$NS_API"
  kubectl rollout restart deployment -n "$NS_UI"
  kubectl rollout status  deployment -n "$NS_API"
  kubectl rollout status  deployment -n "$NS_UI"
  echo "[mesh] Sidecars injected. All traffic through these pods is now traced."
  echo "[mesh] Kiali: $KIALI_URL"
}

mesh_disable() {
  echo "[mesh] Removing Istio sidecar injection from $NS_API and $NS_UI..."
  kubectl label namespace "$NS_API" istio-injection- 2>/dev/null || true
  kubectl label namespace "$NS_UI"  istio-injection- 2>/dev/null || true
  kubectl rollout restart deployment -n "$NS_API"
  kubectl rollout restart deployment -n "$NS_UI"
  kubectl rollout status  deployment -n "$NS_API"
  kubectl rollout status  deployment -n "$NS_UI"
  echo "[mesh] Sidecar injection removed."
}

trace_steady() {
  local count="${1:-30}"
  local interval="${2:-1}"
  echo "[trace] Sending $count transfers at 1 per ${interval}s — watch Kiali graph for green edges"
  echo "[trace] Kiali: $KIALI_URL/kiali/console/graph/namespaces/?namespaces=$NS_API,$NS_UI,$NS_FABRIC"
  for i in $(seq 1 "$count"); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-B1-001","toAccount":"ACC-B2-001","amount":1}'
    echo "[trace] $i/$count — $(date +%H:%M:%S)"
    sleep "$interval"
  done
  echo "[trace] Done — check Kiali and Jaeger for traces."
}

trace_errors() {
  echo "[trace] Sending 20 invalid transfers — watch for red edges in Kiali..."
  for i in $(seq 1 20); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-INVALID","toAccount":"ACC-B2-001","amount":1}' &
  done
  wait
  echo "[trace] Error burst complete."
  echo "[trace] Jaeger: $JAEGER_URL/search?service=quarkus-api"
}

trace_scenario() {
  echo "[trace] ═══ Full Kiali trace scenario (4 steps) ═══"

  echo ""
  echo "[trace] Step 1/4 — Normal traffic (green in Kiali)"
  for i in $(seq 1 10); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-B1-001","toAccount":"ACC-B2-001","amount":1}'
    sleep 0.5
  done

  echo ""
  echo "[trace] Step 2/4 — Latency injection → slow traffic (yellow in Kiali)"
  latency_inject
  for i in $(seq 1 10); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-B1-001","toAccount":"ACC-B2-001","amount":1}'
    echo "[trace]   slow request $i/10 — $(date +%H:%M:%S)"
    sleep 1
  done

  echo ""
  echo "[trace] Step 3/4 — Error traffic (red in Kiali)"
  latency_remove
  for i in $(seq 1 10); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-INVALID","toAccount":"ACC-B2-001","amount":1}' &
  done
  wait

  echo ""
  echo "[trace] Step 4/4 — Recovery: normal traffic (green in Kiali)"
  for i in $(seq 1 10); do
    curl -s -o /dev/null \
      -X POST "$API_URL/transfer" \
      -H "Content-Type: application/json" \
      -d '{"fromAccount":"ACC-B1-001","toAccount":"ACC-B2-001","amount":1}'
    sleep 0.5
  done

  echo ""
  echo "[trace] ═══ Scenario complete ═══"
  echo "[trace] Kiali graph : $KIALI_URL/kiali/console/graph/namespaces/?namespaces=$NS_API,$NS_UI,$NS_FABRIC"
  echo "[trace] Jaeger traces: $JAEGER_URL/search?service=quarkus-api"
}

trace_open() {
  cat <<EOF

  Kiali service graph:
    $KIALI_URL/kiali/console/graph/namespaces/?namespaces=$NS_API,$NS_UI,$NS_FABRIC&duration=60&refresh=15000

  Kiali workloads:
    $KIALI_URL/kiali/console/namespaces/$NS_API/workloads

  Jaeger — quarkus-api traces:
    $JAEGER_URL/search?service=quarkus-api&limit=20

  Jaeger — all services:
    $JAEGER_URL/search

EOF
}

# ── Dispatch ──────────────────────────────────────────────────────────────────

case "${1:-}" in
  kill-api)         kill_api ;;
  kill-ui)          kill_ui ;;
  kill-peer)        kill_peer ;;
  restore-peer)     restore_peer ;;
  latency-inject)   latency_inject ;;
  latency-remove)   latency_remove ;;
  flood)            flood ;;
  status)           show_status ;;
  mesh-enable)      mesh_enable ;;
  mesh-disable)     mesh_disable ;;
  trace-steady)     trace_steady "${2:-}" "${3:-}" ;;
  trace-errors)     trace_errors ;;
  trace-scenario)   trace_scenario ;;
  trace-open)       trace_open ;;
  *)                usage ;;
esac
