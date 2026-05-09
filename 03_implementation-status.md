# Shalm Platform — Implementation Status

> GitHub: `samerhijazi` | UI: Quarkus (Qute) | Registry: `ghcr.io/samerhijazi`
> Generate one phase at a time. Update status after each phase is complete.

---

## How to start this file

"Read 03_implementation-status.md and continue from where we left off."

## VM with lima

k8s-master (192.168.105.3)
k8s-worker-01 (192.168.105.4)
k8s-worker-02 (192.168.105.5)

---

## Repository Structure

```text
shalm-platform/
│
├── 00_docs/
│
├── 01_infrastructure/
│   ├── ansible/                  # cluster provisioning (pre-existing)
│   └── base/                     # base k8s configs (calico, metrics-server)
│
├── 02_gitops/
│   ├── root-app/                 # ArgoCD app-of-apps
│   ├── observability/            # Prometheus, Grafana, Loki
│   ├── quarkus-app/              # quarkus-api manifests
│   ├── quarkus-ui/               # quarkus-ui manifests
│   ├── fabric/                   # Fabric manifests
│   ├── besu/                     # Besu manifests
│   ├── identity/                 # identity-service manifests
│   ├── istio/                    # Istio install + routing
│   └── ai/                       # AI observability service manifests
│
├── 03_apps/
│   ├── quarkus-api/              # Quarkus backend (transactions)
│   ├── quarkus-ui/               # Quarkus UI (Qute templates)
│   └── identity-service/         # OIDC-style mock (Quarkus)
│
├── 04_blockchain/
│   ├── fabric/
│   │   ├── network-config/       # crypto material, configtx
│   │   └── chaincode/            # Go chaincode (transfer, queryBalance)
│   └── besu/
│       ├── network-config/       # genesis, static-nodes
│       └── contracts/            # Solidity token contract
│
├── 05_cicd/
│   └── github-actions/           # GitHub Actions workflows
│
└── 06_ai/
    └── observability-agent/      # Python FastAPI log/metrics analysis
```

> **Note:** Update this structure before Phase 0 starts if any paths need changing.

---

## Phases

| #   | Phase                            | Status        | Notes                                                             |
| --- | -------------------------------- | ------------- | ----------------------------------------------------------------- |
| 0   | Repo Bootstrap + ArgoCD root app | `[x] done`    | Folder structure, root ArgoCD App-of-Apps                         |
| 1   | Observability Stack              | `[x] done`    | Prometheus, Grafana, Loki+Promtail, dashboards                    |
| 2   | Quarkus API                      | `[x] done`    | REST API, in-memory state, metrics, structured logs, GHCR, GitOps |
| 3   | Quarkus UI                       | `[x] done`    | Qute templates, balance/tx views, wired to API, GitOps            |
| 4   | Hyperledger Fabric               | `[ ] pending` | 2 orgs, 1 orderer, peers, Go chaincode, API integration           |
| 5   | Istio                            | `[ ] pending` | Sidecar injection, ingress gateway, Fabric traffic routing        |
| 6   | Hyperledger Besu                 | `[ ] pending` | 3-node QBFT, Solidity contract, observability                     |
| 7   | Identity Service                 | `[ ] pending` | OIDC-mock (Quarkus), JWT, API validation                          |
| 8   | CI/CD                            | `[ ] pending` | GitHub Actions per app, GHCR push, manifest patch, ArgoCD sync    |
| 9   | SRE Layer                        | `[ ] pending` | PrometheusRules, Alertmanager, failure scripts                    |
| 10  | AI Observability                 | `[ ] pending` | Python FastAPI, Loki+Prometheus queries, /summary, /anomalies     |

---

## Status Key

- `[ ] pending` — not started
- `[~] in-progress` — currently being generated
- `[x] done` — generated and reviewed

---

## Phase Details

### Pre-Phase 0 — Run Ansible (K8s Bootstrap)

**Run from your local machine:**

```bash
cd 01_infrastructure/ansible
ansible-playbook playbooks/site.yml -i inventory/hosts.yml
```

This installs: common config → Kubernetes (kubeadm + Calico + metrics-server) → MetalLB → ArgoCD.
Monitoring is NOT installed by ansible — it is managed by GitOps in Phase 1.

---

### Phase 0 — Repo Bootstrap

**Deliverables:**

- Full folder scaffold (`shalm-platform/` tree matching structure above)
- `02_gitops/root-app/root-app.yaml` — ArgoCD App-of-Apps
- Child `Application` stubs for each phase (synced to their `02_gitops/` paths)
- `01_infrastructure/base/` — namespace definitions for all components

---

### Phase 1 — Observability Stack

**Namespace:** `observability`
**Deliverables:**

- `02_gitops/observability/` — ArgoCD Applications for Prometheus, Grafana, Loki+Promtail
- Helm values overrides
- Grafana dashboard ConfigMaps: cluster health, pod health, request latency

---

### Phase 2 — Quarkus API

**Namespace:** `quarkus-api`
**Deliverables:**

- `03_apps/quarkus-api/` — full Quarkus Maven project
  - `POST /transfer`, `GET /balance/{id}`, `GET /health`, `GET /metrics`
  - In-memory `Map<String, Integer>` balances
  - Micrometer: request_count, request_latency, error_count
  - Structured JSON logs: transaction_id, from, to, amount, status
  - `Dockerfile`
- `02_gitops/quarkus-app/` — Deployment, Service, Ingress manifests
- `05_cicd/github-actions/quarkus-api.yml` — build, push GHCR, patch manifest

---

### Phase 3 — Quarkus UI

**Namespace:** `quarkus-ui`
**Deliverables:**

- `03_apps/quarkus-ui/` — Quarkus project with Qute templates
  - Transfer form
  - Balance display
  - Transaction history
  - Calls backend API only (no direct blockchain access)
- `02_gitops/quarkus-ui/` — Deployment, Service manifests
- `05_cicd/github-actions/quarkus-ui.yml`

---

### Phase 4 — Hyperledger Fabric

**Namespace:** `fabric`
**Deliverables:**

- `04_blockchain/fabric/network-config/` — crypto-config, configtx.yaml
- `04_blockchain/fabric/chaincode/` — Go chaincode: `transfer()`, `queryBalance()`
- `02_gitops/fabric/` — K8s manifests: peer0-orgA, peer0-orgB, orderer
- Fabric SDK integration in `03_apps/quarkus-api/` (new endpoints)

---

### Phase 5 — Istio

**Namespace:** `istio-system`
**Deliverables:**

- `02_gitops/istio/` — IstioOperator or ArgoCD Application
- Sidecar injection label on `fabric` namespace
- IngressGateway + VirtualService for Fabric endpoints

---

### Phase 6 — Hyperledger Besu

**Namespace:** `besu`
**Deliverables:**

- `04_blockchain/besu/network-config/` — genesis.json (QBFT), static-nodes.json
- `04_blockchain/besu/contracts/` — Solidity token contract
- `02_gitops/besu/` — 3-node K8s Deployments, Services
- Prometheus scrape config, Loki log shipping

---

### Phase 7 — Identity Service

**Namespace:** `identity`
**Deliverables:**

- `03_apps/identity-service/` — Quarkus project
  - `POST /login`, `GET /token`, `GET /.well-known/openid-configuration`
  - JWT with `user_id` + `bank` claims
- JWT validation integrated into `03_apps/quarkus-api/`
- `02_gitops/identity/` — manifests
- `05_cicd/github-actions/identity-service.yml`

---

### Phase 8 — CI/CD

**Deliverables:**

- `05_cicd/github-actions/quarkus-api.yml`
- `05_cicd/github-actions/quarkus-ui.yml`
- `05_cicd/github-actions/identity-service.yml`
- Each: build → push `ghcr.io/samerhijazi/<app>:${GITHUB_SHA}` → patch manifest → commit → push

---

### Phase 9 — SRE Layer

**Deliverables:**

- `02_gitops/observability/alerts/` — PrometheusRule CRDs
  - High latency (>200ms), error rate (>1%), pod down, Fabric tx failures
- Alertmanager config
- `01_infrastructure/scripts/simulate-failures.sh` — pod kill, peer shutdown, latency injection

---

### Phase 10 — AI Observability

**Namespace:** `ai`
**Deliverables:**

- `06_ai/observability-agent/` — Python FastAPI service
  - `GET /summary` — summarizes recent Loki logs
  - `GET /anomalies` — basic rule-based anomaly detection on Prometheus metrics
- `02_gitops/ai/` — Deployment, Service manifests
- `05_cicd/github-actions/ai-agent.yml`
