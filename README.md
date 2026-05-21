# Shalm Platform

> AI-generated cross-bank blockchain platform, built end-to-end with [Claude Code](https://claude.ai/code)

![Built with Claude Code](https://img.shields.io/badge/Built%20with-Claude%20Code-6B48FF?style=flat-square)
![Hyperledger Fabric](https://img.shields.io/badge/Blockchain-Hyperledger%20Fabric%202.5-2F4F4F?style=flat-square)
![Quarkus](https://img.shields.io/badge/Backend-Quarkus%203.9.5-4695EB?style=flat-square)
![Kubernetes](https://img.shields.io/badge/Platform-Kubernetes-326CE5?style=flat-square)

---

## What Is This?

Shalm is a **cross-bank token transfer platform** running on Hyperledger Fabric, deployed on a 3-node Kubernetes cluster (Lima VMs on arm64). It demonstrates a full production-grade platform — from blockchain chaincode to GitOps CI/CD and AI-powered observability — **generated entirely by Claude Code**.

Every line of infrastructure config, application code, GitOps manifest, and CI/CD workflow was produced through AI-assisted development with Claude Code (Anthropic). No manual scaffolding, no boilerplate — the AI designed and implemented the full stack.

**Use case:** ClientA (Bank1/Org1) transfers tokens to ClientB (Bank2/Org2), recorded on Hyperledger Fabric and visible through the Quarkus UI.

---

## Architecture

```
quarkus-ui  (Qute templates, port 30801)
    └── REST client → quarkus-api  (port 30800)
                          ├── in-memory account state (4 accounts, 2 banks)
                          └── /fabric/* → FabricGatewayService → Hyperledger Fabric peer (gRPC)

Observability:  Prometheus · Grafana · Loki · Promtail  (namespace: observability)
                Alertmanager  (routing + inhibit rules)
                PrometheusRules: latency, error rate, pod health, Fabric health
                Jaeger all-in-one  (port 30686) — traces via Envoy/Zipkin
                Kiali  (port 30088) — service mesh topology + Jaeger/Grafana integration

Service mesh:   Istio  (namespace: istio-system)
                Envoy sidecars on fabric namespace — 100% trace sampling → Jaeger

AI agent:       FastAPI  (namespace: ai, port 30810)
                GET /summary  — recent Loki log digest
                GET /anomalies — rule-based anomaly detection on Prometheus metrics

GitOps:         ArgoCD App-of-Apps → child Applications → manifests in 02_gitops/
CI/CD:          GitHub Actions — multi-arch build (linux/amd64,linux/arm64) → GHCR → manifest patch → ArgoCD sync
```

**Key constraint:** UI never talks directly to Fabric — always through the API.

---

## Live Services

| Service      | URL                        | Credentials           |
|--------------|----------------------------|-----------------------|
| Quarkus UI   | http://192.168.105.3:30801 | —                     |
| Quarkus API  | http://192.168.105.3:30800 | —                     |
| AI Agent     | http://192.168.105.3:30810 | —                     |
| Grafana      | http://192.168.105.3:30300 | admin / `shalm-admin` |
| Prometheus   | http://192.168.105.3:30090 | —                     |
| Alertmanager | http://192.168.105.3:30093 | —                     |
| Jaeger       | http://192.168.105.3:30686 | —                     |
| Kiali        | http://192.168.105.3:30088 | —                     |
| ArgoCD       | http://192.168.105.3:30080 | admin / `GhPtA0-v7iFqnPkX` |

---

## Tech Stack

| Layer         | Technology                                          |
|---------------|-----------------------------------------------------|
| Backend       | Quarkus 3.9.5 · Jakarta REST 3.0 · Micrometer       |
| Frontend      | Quarkus Qute templates · MicroProfile REST Client   |
| Blockchain    | Hyperledger Fabric 2.5 · Java chaincode (CCAAS)     |
| Service Mesh  | Istio · Envoy · Kiali                               |
| Observability | Prometheus · Grafana · Loki · Promtail · Jaeger      |
| AI Agent      | Python 3.12 · FastAPI                               |
| GitOps        | ArgoCD (App-of-Apps pattern)                        |
| CI/CD         | GitHub Actions · GHCR · multi-arch (amd64 + arm64) |
| Platform      | Kubernetes · 3 Lima VMs (arm64) · Calico CNI        |
| Registry      | ghcr.io/samerhijazi                                 |

---

## Phase Status

| # | Phase                         | Status    |
|---|-------------------------------|-----------|
| 0 | Repo Bootstrap + ArgoCD       | Done      |
| 1 | Observability Stack           | Done      |
| 2 | Quarkus API                   | Done      |
| 3 | Quarkus UI                    | Done      |
| 4 | Hyperledger Fabric            | Done      |
| 5 | Istio Service Mesh            | Done      |
| 6 | Hyperledger Besu              | Skipped   |
| 7 | Identity Service              | Skipped   |
| 8 | CI/CD                         | Done      |
| 9 | SRE Layer                     | Done      |
| 10| AI Observability Agent        | Done      |

All active phases complete. Phases 6 (Besu) and 7 (Identity) intentionally skipped.

---

## Quick Start

All versions are pinned in `versions.env` at the repo root.

```bash
# Build Quarkus API
cd 03_apps/quarkus-api && mvn package -DskipTests

# Build Quarkus UI
cd 03_apps/quarkus-ui && mvn package -DskipTests

# Run locally in dev mode
mvn quarkus:dev

# Check cluster state
kubectl get pods -A
kubectl get applications -n argocd

# SSH into master node
limactl shell k8s-master
```

---

## Repo Structure

```
shalm-platform/
├── 01_infrastructure/    # Ansible provisioning + base K8s configs
├── 02_gitops/            # ArgoCD app-of-apps + all manifests
│   ├── root-app/         # ArgoCD root application
│   ├── observability/    # Prometheus, Grafana, Loki
│   ├── quarkus-app/      # quarkus-api manifests
│   ├── quarkus-ui/       # quarkus-ui manifests
│   ├── fabric/           # Fabric peer/orderer manifests
│   ├── istio/            # Istio install + routing
│   └── ai/               # AI agent manifests
├── 03_apps/
│   ├── quarkus-api/      # Quarkus backend (REST + Fabric gateway)
│   └── quarkus-ui/       # Quarkus frontend (Qute)
├── 04_blockchain/
│   └── fabric/           # Crypto material, configtx, Java chaincode
├── 05_cicd/              # GitHub Actions workflow documentation
├── 06_ai/                # Python FastAPI observability agent
└── .github/workflows/    # Active CI/CD workflows (quarkus-api, quarkus-ui, chaincode, ai-agent)
```

---

## Known Issues

**`kubectl logs` is broken** — kubelet port 10250 unreachable on this cluster. Debug crashing pods with:
```bash
kubectl run debug --image=<same-image> --restart=Never -n <namespace> \
  --env="QUARKUS_LOG_CONSOLE_JSON=false" -- java -jar /app/quarkus-run.jar
kubectl describe pod debug -n <namespace>
kubectl delete pod debug -n <namespace>
```

**Calico token expiry** — pods stuck in `ContainerCreating` with "Unauthorized":
```bash
kubectl rollout restart daemonset/calico-node -n kube-system
```

**Jakarta REST 3.0** — `Response.Status.UNPROCESSABLE_ENTITY` does not exist. Use `Response.status(422)`.

---

*Built with [Claude Code](https://claude.ai/code) by Anthropic — AI-assisted development from infrastructure to application layer.*
