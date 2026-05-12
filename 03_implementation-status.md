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

## Current State (read this first in a new session)

- **All phases complete (0–5, 8–10). Phases 6 and 7 skipped.**
- Phases 6 (Besu) and 7 (Identity Service) are **skipped** — network config kept in `04_blockchain/besu/` for reference
- All nodes are **arm64** — every custom Docker image must be built multi-arch (`linux/amd64,linux/arm64`)
- `kubectl logs` does NOT work on this cluster (kubelet port unreachable) — use debug pods instead (see rule 7)

**Live services:**
| Service     | URL                              |
| ----------- | -------------------------------- |
| Quarkus API | http://192.168.105.3:30800       |
| Quarkus UI  | http://192.168.105.3:30801       |
| AI Agent    | http://192.168.105.3:30810       |
| Grafana     | http://192.168.105.3:30300       |
| Prometheus  | http://192.168.105.3:30090       |
| Alertmanager| http://192.168.105.3:30093       |

**Post-deploy checklist (after first CI push of a new image):**
- Go to `https://github.com/samerhijazi?tab=packages` and set the new package to **Public**

---

## Known Issues & Rules — Apply to Every Phase

These were discovered during Phases 2–3. Violating them causes hard-to-debug failures.

### 1. Node architecture is arm64

All Lima VMs run `arm64` (confirmed via `kubectl get nodes -o jsonpath`).
GitHub Actions `ubuntu-latest` is `amd64`.
**Rule:** Every Dockerfile build in CI must use multi-arch:

```yaml
- uses: docker/setup-qemu-action@v3.6.0
- uses: docker/setup-buildx-action@v3.10.0
- uses: docker/build-push-action@v5.4.0
  with:
    platforms: linux/amd64,linux/arm64
```

Single-arch images fail silently with exit code 255 and no logs.

### 2. GitHub Actions workflows must live in `.github/workflows/`

Files in `05_cicd/github-actions/` are documentation only — GitHub never reads them.
**Rule:** Always create the real workflow at `.github/workflows/<name>.yml`.
The `05_cicd/` copy is optional reference.

### 3. GitHub PAT needs `workflow` scope

Pushing any file under `.github/workflows/` requires a PAT with the `workflow` scope.
**Rule:** Update the PAT at `https://github.com/settings/tokens` before pushing workflow files.

### 4. GHCR packages are private by default

New packages created by CI are private. The cluster pulls anonymously and gets 403.
**Rule:** After first CI push, go to `https://github.com/samerhijazi?tab=packages`,
open the package → Settings → **Change visibility to Public**.

### 5. `git push` will be rejected after CI patches the manifest

The CI workflow commits a manifest update (image SHA) and pushes it back.
Your local branch is then behind.
**Rule:** Always use:

```bash
git pull --rebase origin main && git push origin main
```

Never just `git push origin main`.

### 6. Jakarta REST 3.0 — missing status constants

Quarkus 3.9.5 uses Jakarta REST 3.0, not 3.1.
`Response.Status.UNPROCESSABLE_ENTITY` does **not** exist.
**Rule:** Use the numeric code: `Response.status(422)`.
Same applies to any status added after REST 3.0 (e.g., 207, 308).

### 7. `kubectl logs` is broken on this cluster

The kubelet API (port 10250) is not reachable from outside the VMs.
`kubectl logs` always returns `the server could not find the requested resource`.
**Rule:** To debug a crashing pod, use a one-shot debug pod:

```bash
kubectl run debug \
  --image=<same-image> \
  --restart=Never \
  -n <namespace> \
  --env="QUARKUS_LOG_CONSOLE_JSON=false" \
  -- java -jar /app/quarkus-run.jar
kubectl describe pod debug -n <namespace>
kubectl delete pod debug -n <namespace>
```

### 8. Calico CNI token expires after long cluster uptime

Symptom: new pods stuck in `ContainerCreating` with
`calico failed (add): error getting ClusterInformation: Unauthorized`.
**Rule:** Fix immediately with:

```bash
kubectl rollout restart daemonset/calico-node -n kube-system
kubectl rollout status daemonset/calico-node -n kube-system
```

### 9. Never use `-q` (quiet) in CI Maven builds

`mvn package -DskipTests -q` hides compilation errors. The workflow shows
"Process completed with exit code 1" with no useful output.
**Rule:** Use `mvn package -DskipTests` (no `-q`) in all CI workflows.

### 10. Load `versions.env` as the first CI step

All future workflows must load the central version file before any other step:

```yaml
- name: Load versions
  run: grep -v '^#' versions.env | grep -v '^$' >> $GITHUB_ENV
```

Then use `${{ env.JAVA_VERSION }}`, `${{ env.QUARKUS_VERSION }}`, etc.

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
| 4   | Hyperledger Fabric               | `[x] done`    | 2 orgs, SOLO orderer, CCAAS Java chaincode, /fabric/* endpoints   |
| 5   | Istio                            | `[x] done`    | Sidecar injection, ingress gateway, Fabric traffic routing        |
| 6   | Hyperledger Besu                 | `[s] skipped` | Network config kept in `04_blockchain/besu/`; no K8s manifests   |
| 7   | Identity Service                 | `[s] skipped` | No files generated                                                |
| 8   | CI/CD                            | `[x] done`    | Workflows: quarkus-api, quarkus-ui, chaincode — multi-arch, manifest patch, ArgoCD sync |
| 9   | SRE Layer                        | `[x] done`    | PrometheusRules (API/pod/fabric), Alertmanager routing, simulate-failures.sh |
| 10  | AI Observability                 | `[x] done`    | Python FastAPI, Loki+Prometheus queries, /summary, /anomalies, GHCR CI |

---

## Status Key

- `[ ] pending` — not started
- `[~] in-progress` — currently being generated
- `[x] done` — generated and reviewed
- `[s] skipped` — intentionally omitted from the platform

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
  - `POST /transfer` — cross-bank transfer (from: account ID, to: account ID, amount)
  - `GET /accounts` — list all accounts with owner, bank, balance
  - `GET /accounts/{id}` — single account detail
  - `GET /balance/{id}` — balance only (kept for backward compat)
  - `GET /health`, `GET /metrics`
  - Micrometer: `transfer_request_count`, `transfer_request_latency`, `transfer_error_count`
  - Structured JSON logs via MDC: transaction_id, from_account, from_owner, from_bank, to_account, to_owner, to_bank, amount, status
  - `Dockerfile`
- `02_gitops/quarkus-app/` — Deployment (NodePort 30800), Service
- `.github/workflows/quarkus-api.yml` — build, multi-arch push to GHCR, patch manifest

**Account model** (`Account.java`):

```
id      — e.g. ACC-B1-001
owner   — e.g. ClientA
bank    — e.g. Bank1
balance — integer tokens
```

**Seeded accounts:**
| ID | Owner | Bank | Balance |
|-------------|---------|-------|---------|
| ACC-B1-001 | ClientA | Bank1 | 1000 |
| ACC-B1-002 | ClientC | Bank1 | 500 |
| ACC-B2-001 | ClientB | Bank2 | 1000 |
| ACC-B2-002 | ClientD | Bank2 | 500 |

---

### Phase 3 — Quarkus UI

**Namespace:** `quarkus-ui`
**Deliverables:**

- `03_apps/quarkus-ui/` — Quarkus project with Qute templates
  - Dashboard grouped by bank: **Bank1 | Bank2** side-by-side, each showing clients + account IDs + balances
  - Transfer form: dropdowns show `ClientA (Bank1) — ACC-B1-001` labels, posts account IDs
  - Transaction history: shows `ClientA (Bank1)` → `ClientB (Bank2)` with account IDs, status badge, timestamp
  - Calls `GET /accounts` and `POST /transfer` on quarkus-api via MicroProfile REST client
  - In-memory transaction store (last 20, lost on pod restart)
- `02_gitops/quarkus-ui/` — Deployment (NodePort 30801), Service
- `.github/workflows/quarkus-ui.yml` — multi-arch build, GHCR push, manifest patch

**Use case:** ClientA (ACC-B1-001, Bank1/Org1) transfers tokens to ClientB (ACC-B2-001, Bank2/Org2).
This is the primary cross-bank transfer scenario visible in the UI and traced through the API logs.

---

### Phase 4 — Hyperledger Fabric

**Namespace:** `fabric`
**Deliverables:**

- `04_blockchain/fabric/network-config/` — crypto-config, configtx.yaml
- `04_blockchain/fabric/chaincode/` — **Java** chaincode (`fabric-chaincode-java`):
  - `Transfer(ctx, from, to, amount)`, `QueryBalance(ctx, account)`, `InitLedger()`, `createAccount(ctx, accountId, bankId, owner, initialBalance), deleteAccount(ctx, accountId)`, `deposit(ctx, accountId, amount)`, `getAccount(ctx, accountId)`
  - NOT Quarkus — chaincode is plain Java running inside Fabric's container runtime
  - Accounts: ClientA/ACC-B1-001 (Bank1), ClientB/ACC-B2-001 (Bank2), ClientC/ACC-B1-002, ClientD/ACC-B2-002
- `02_gitops/fabric/` — K8s manifests: peer0-org1, peer0-org2, orderer
- Fabric SDK integration in `03_apps/quarkus-api/` (new endpoints)

**Rules for this phase:**

- Fabric images (`hyperledger/fabric-peer`, `hyperledger/fabric-orderer`) are multi-arch — pull directly, no build needed
- No CI/CD workflow for Fabric itself (no custom image); apply rules 5 and 8 for manifest pushes
- Chaincode is Java using fabric-chaincode-java (JVM-based, no Quarkus/Jakarta REST)

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

**Rules for this phase:**

- `hyperledger/besu` official image is multi-arch — no custom build needed
- Apply rule 8 (Calico restart) if pods stay in ContainerCreating after a gap between phases

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

**Rules for this phase:**

- Apply rules 1, 2, 3, 4, 5, 6, 9, 10 (Quarkus app with CI — full checklist)
- Use `Response.status(401)` not `Response.Status.UNAUTHORIZED` — confirm it exists in Jakarta REST 3.0 first
- Workflow goes in `.github/workflows/identity-service.yml`, not `05_cicd/`

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

**Rules for this phase:**

- Apply rules 1, 2, 3, 4, 5, 10 (Python app with CI)
- Python `python:3.12-slim` is multi-arch — base image is fine
- Custom image still needs `platforms: linux/amd64,linux/arm64` in the build step (rule 1)
- Workflow goes in `.github/workflows/ai-agent.yml`
