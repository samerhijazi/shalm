# Shalm Platform — Build Runbook & Status

> GitHub: `samerhijazi` | UI: Quarkus (Qute) | Registry: `ghcr.io/samerhijazi`

This is the **single source of truth** for this repository — both the roadmap
for building the platform from an empty repo, and the current status of what
exists. It replaces the earlier `01_plan.md` / `02_task-breakdown.md` /
`03_implementation-status.md` split (those originals are preserved, untracked,
under `00_debug/` for archival reference only — they contain stale paths and
a design that diverged from what was actually built; do not follow them).
This file was previously named `03_implementation-status.md`.

**How to use this file:**
- **Fresh agent, empty repo:** start at "Pre-Phase 0" and execute phases 0→10
  in order. Each phase lists its objective, concrete deliverables (exact
  paths/endpoints matching what a prior run actually produced), and the
  global rules (§5) that apply to it. Do not invent alternate paths, languages,
  or tools not named below — earlier attempts already found the working
  combination the hard way.
- **Continuing this repo:** read "Current State" immediately below, then the
  Fabric incident history before changing its packaging or lifecycle setup.

---

## Current State (read this first in a continuing session)

- **All implemented phases are operational.** Fabric ledger initialization and
  API balance queries were verified live on 2026-09-14.
- **All phases complete (0–5, 8–10). Phases 6 and 7 skipped.**
- Phases 6 (Besu) and 7 (Identity Service) are **skipped** — network config
  kept in `04_blockchain/besu/` for reference
- All nodes are **arm64** — every custom Docker image must be built multi-arch
- `kubectl logs` does NOT work on this cluster (kubelet port unreachable) —
  use debug pods instead (rule 7)
- `kubectl exec` does NOT work either (same cause) — read logs via Loki
  through Grafana's proxy (rule 10c)
- quarkus-api and quarkus-ui both have real test suites (`mvn test`) run as a
  CI gate; results are summarized into a "Tests" tab in the quarkus-ui
  dashboard
- quarkus-ui has a standalone `/architecture` page (separate from the
  dashboard tab bar) with a static platform diagram + service URL table —
  linked from the dashboard header

**Live services:**
| Service            | URL                              | Credentials                |
| ------------------- | --------------------------------- | --------------------------- |
| Quarkus API         | http://192.168.105.3:30800       | —                           |
| Quarkus UI          | http://192.168.105.3:30801       | —                           |
| AI Agent            | http://192.168.105.3:30810       | —                           |
| Grafana             | http://192.168.105.3:30300       | admin / `shalm-admin`       |
| Prometheus          | http://192.168.105.3:30090       | —                           |
| Alertmanager        | http://192.168.105.3:30093       | —                           |
| Jaeger              | http://192.168.105.3:30686       | —                           |
| Kiali               | http://192.168.105.3:30088       | —                           |
| ArgoCD              | http://192.168.105.3:30080       | admin / `GhPtA0-v7iFqnPkX`  |
| Kubernetes Dashboard | https://192.168.105.3:30092 (HTTPS) / :30091 | see `02_gitops/root-app/dashboard-rbac-app.yaml` for the service-account token setup — this component exists in GitOps (`02_gitops/root-app/dashboard-app.yaml`, upstream `kubernetes-dashboard` Helm chart) but was never wired into the phase table below; treat as bonus/unverified rather than a load-bearing platform service |

**Post-deploy checklist (after first CI push of a new image):**
- Go to `https://github.com/samerhijazi?tab=packages` and set the new package
  to **Public**

---

## 1. Objective

Build a **3-node Kubernetes cluster (1 control-plane + 2 workers) demo
platform** that demonstrates:

- Observability-first SRE practices
- GitOps-driven platform management
- A permissioned blockchain system (Hyperledger Fabric) for cross-bank
  token transfer
- A UI-driven interaction layer, backed by a REST API — the UI never talks
  to the blockchain directly
- AI-assisted observability analysis

**Use case:** ClientA (ACC-B1-001, Bank1/Org1) transfers tokens to ClientB
(ACC-B2-001, Bank2/Org2). This is the scenario the UI, API logs, and
chaincode are all built around.

**Scope note (deviates from the original 01_plan.md ambition):** Besu
(a second blockchain runtime) and a custom identity/OIDC service were part
of the original spec but were **intentionally skipped** — Fabric alone
carries the demo. Don't build them unless explicitly asked; if asked, they
are phases 6 and 7 below and `04_blockchain/besu/` already has placeholder
network config.

---

## 2. Target Architecture

```
quarkus-ui (Qute, port 30801)
    └── REST client → quarkus-api (port 30800)
                          ├── in-memory account state (4 accounts, 2 banks)
                          └── /fabric/* → FabricGatewayService → Hyperledger Fabric peer (gRPC)

Observability: Prometheus + Grafana + Loki + Promtail (namespace: observability)
               Alertmanager (routing + inhibit rules, null receiver by default)
               PrometheusRules: latency, error rate, pod health, Fabric health
               Jaeger all-in-one (port 30686) — traces via Envoy/Zipkin protocol
               Kiali (port 30088) — service mesh topology, traffic graph, Jaeger+Grafana integration
Service mesh:  Istio (namespace: istio-system) — sidecar on fabric namespace
               Envoy emits traces to Jaeger:9411 (Zipkin); 100% sampling rate
AI agent:      FastAPI (namespace: ai, port 30810) — GET /summary (Loki), GET /anomalies (Prometheus)
```

**Key constraint:** UI never communicates directly with Fabric or Besu —
always through the API.

**Key architectural rules:**
- All components run on Kubernetes, all deployments managed via ArgoCD
- All services emit logs + metrics
- Fabric + Besu (if built) remain separate systems — no interoperability
  required between them

---

## 3. Platform & Cluster

- **Cluster:** 3 Lima VMs — master `192.168.105.3`, workers
  `192.168.105.4` / `192.168.105.5`
- **All nodes are arm64** — every custom Docker image must be built
  multi-arch (`linux/amd64,linux/arm64`)
- **Registry:** `ghcr.io/samerhijazi` — new packages are private by default;
  make them public after first CI push (see checklist above)
- **Kubeconfig:** saved in `~/.kube/config` — `kubectl` works without extra
  env vars

```bash
limactl shell k8s-master          # SSH into master
kubectl get pods -A               # overview
kubectl get applications -n argocd
```

### Pre-Phase 0 — Run Ansible (K8s Bootstrap)

Run from your local machine:

```bash
cd 01_infrastructure/ansible
ansible-playbook playbooks/site.yml -i inventory/hosts.yml
```

This installs, in order: common config → Kubernetes (kubeadm + Calico +
metrics-server) → MetalLB → ArgoCD. Monitoring is **not** installed by
Ansible — it is managed by GitOps in Phase 1.

---

## 4. Repository Structure (actual, authoritative)

```text
shalm-platform/
│
├── 00_debug/                     # gitignored scratch/archive — not part of the build
│
├── 01_infrastructure/
│   ├── ansible/                  # cluster provisioning (pre-existing)
│   └── base/                     # namespaces.yaml, local-path-provisioner.yaml
│
├── 02_gitops/
│   ├── root-app/                 # ArgoCD app-of-apps — one *-app.yaml per child Application
│   │                              #   (observability split into several: kube-prometheus-stack,
│   │                              #    loki, promtail, jaeger, kiali, observability-alerts,
│   │                              #    observability-dashboards, dashboard, dashboard-rbac)
│   ├── observability/            # Prometheus/Grafana/Loki manifests + alerts/
│   ├── quarkus-app/               # quarkus-api Deployment/Service manifests
│   ├── quarkus-ui/                # quarkus-ui Deployment/Service manifests
│   ├── fabric/                    # peer/orderer/setup-job manifests
│   ├── besu/                      # not created — phase skipped
│   ├── istio/                     # istiod-app, ingressgateway-app, gateway, virtualservice, peerauthentication, telemetry
│   └── ai/                        # AI agent Deployment/Service manifests
│
├── 03_apps/
│   ├── quarkus-api/               # Quarkus backend (transactions)
│   └── quarkus-ui/                # Quarkus UI (Qute templates)
│
├── 04_blockchain/
│   ├── fabric/
│   │   ├── network-config/        # crypto-config, configtx.yaml (crypto-material/ and
│   │   │                          #   channel-artifacts/ subdirs are gitignored, generated)
│   │   └── chaincode/              # Java chaincode (fabric-chaincode-java, CCAAS)
│   └── besu/                      # placeholder network-config only, no K8s manifests
│
├── 05_cicd/
│   └── github-actions/            # documentation copies only — GitHub never reads these
│
└── .github/workflows/             # the REAL CI/CD workflows: quarkus-api.yml, quarkus-ui.yml,
                                    #   chaincode.yml, ai-agent.yml
```

Note: `06_ai/observability-agent/` in the original plan was actually placed
elsewhere — check the live tree for the AI agent's Python source before
assuming a path; do not recreate a `06_ai/` directory speculatively.

---

## 5. Global Rules — Apply at Every Phase

These were discovered the hard way across phases 2–4. Skipping any of them
reproduces a real, already-debugged failure.

### 5.0 Execution discipline

- Use **Git as source of truth**; every deployment = a Kubernetes manifest
  applied via ArgoCD
- Every service must expose `/health` (or Quarkus's built-in) and `/metrics`
- Use one namespace per component
- No manual `kubectl` changes after GitOps bootstrap (see 5.10b)

### 5.1 Node architecture is arm64

All Lima VMs run `arm64`. GitHub Actions `ubuntu-latest` runners are `amd64`.
**Rule:** every Dockerfile build in CI must be multi-arch:

```yaml
- uses: docker/setup-qemu-action@v3.6.0
- uses: docker/setup-buildx-action@v3.10.0
- uses: docker/build-push-action@v5.4.0
  with:
    platforms: linux/amd64,linux/arm64
```

Single-arch images fail silently with exit code 255 and no logs.

### 5.2 GitHub Actions workflows must live in `.github/workflows/`

Files in `05_cicd/github-actions/` are documentation only — GitHub never
reads them. Always create the real workflow at `.github/workflows/<name>.yml`.

### 5.3 GitHub PAT needs `workflow` scope

Pushing any file under `.github/workflows/` requires a PAT with the
`workflow` scope. Update the PAT at `https://github.com/settings/tokens`
before pushing workflow files.

### 5.4 GHCR packages are private by default

New packages created by CI are private; the cluster pulls anonymously and
gets 403. After first CI push, go to
`https://github.com/samerhijazi?tab=packages`, open the package → Settings
→ **Change visibility to Public**.

### 5.5 `git push` will be rejected after CI patches the manifest

The CI workflow commits a manifest update (image SHA) and pushes it back;
your local branch is then behind. Always use:

```bash
git pull --rebase origin main && git push origin main
```

Never just `git push origin main`.

### 5.6 Jakarta REST 3.0 — missing status constants

Quarkus 3.9.5 uses Jakarta REST 3.0, not 3.1.
`Response.Status.UNPROCESSABLE_ENTITY` does **not** exist. Use the numeric
code: `Response.status(422)`. Verify any status constant exists before using
it (same applies to 207, 308, etc.).

### 5.7 `kubectl logs` is broken on this cluster

The kubelet API (port 10250) is not reachable from outside the VMs.
`kubectl logs` always returns "the server could not find the requested
resource". Debug a crashing pod with a one-shot debug pod instead:

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

### 5.8 Calico CNI token expires after long cluster uptime

Symptom: new pods stuck in `ContainerCreating` with "calico failed (add):
error getting ClusterInformation: Unauthorized". Fix immediately with:

```bash
kubectl rollout restart daemonset/calico-node -n kube-system
kubectl rollout status daemonset/calico-node -n kube-system
```

### 5.9 Never use `-q` (quiet) in CI Maven builds

`mvn package -DskipTests -q` hides compilation errors — the workflow just
shows "Process completed with exit code 1" with no useful output. Always
use `mvn package -DskipTests` (no `-q`).

### 5.10a0. No default StorageClass — install local-path-provisioner first

This cluster has no dynamic storage provisioner out of the box (`kubectl get
storageclass` returns nothing). Any workload needing a PVC (e.g. Fabric
peer/orderer ledger storage) stays `Pending` forever without one.
`01_infrastructure/base/local-path-provisioner.yaml` (upstream
rancher/local-path-provisioner v0.0.30, applied once via `kubectl apply`,
**not** ArgoCD-managed) provides the `local-path` StorageClass used by
`02_gitops/fabric/*/pvc.yaml`. Apply this **before** Phase 4.

### 5.10a1. fabric-setup Job needs the FULL MSP directory mounted, not just config.yaml

Mounting only `fabric-org{1,2}-admin-msp` (which contains just
`config.yaml`) is not enough — the `cacerts/`, `signcerts/`, `keystore/`,
and `tlscacerts/` subdirectories must also be mounted, or the peer CLI's
BCCSP tries to `mkdir keystore/` on what it thinks is a normal MSP dir and
fails with "read-only file system" (and `mychannel` never actually gets
created). Mount all four `fabric-org{1,2}-admin-msp-{cacerts,signcerts,
keystore,tlscacerts}` secrets as subdirectories alongside the base
`config.yaml` mount — see `02_gitops/fabric/setup-job.yaml`.

### 5.10a. Fabric identity secret must exist in the consuming namespace too

`quarkus-api`'s Deployment mounts `fabric-org1-admin` from its own namespace
(`quarkus-api`), not from `fabric` — Secrets are namespace-scoped. Creating
it only in `fabric` leaves the mount empty (it's `optional: true`, so the
pod starts anyway with Fabric silently disabled). `create-secrets.sh` must
create `fabric-org1-admin` in **both** `fabric` and `quarkus-api`
namespaces. To backfill after the fact:

```bash
kubectl get secret fabric-org1-admin -n fabric -o json \
  | python3 -c "import json,sys; d=json.load(sys.stdin); d['metadata']={'name':'fabric-org1-admin','namespace':'quarkus-api'}; print(json.dumps(d))" \
  | kubectl apply -f -
```

### 5.10b. ArgoCD reverts un-pushed manual cluster edits

Any resource under `02_gitops/` is reconciled from `origin/main`, not from
what's live in the cluster — `kubectl apply`/`kubectl edit` works for a few
seconds, then auto-sync reverts it, visible as a repeating
Synced→OutOfSync→Synced / Healthy→Degraded cycle in
`kubectl get applications -n argocd`. Always commit + push the manifest
change; never rely on a live patch.

### 5.10c. `kubectl exec` is broken too, not just `kubectl logs`

Same root cause (kubelet port unreachable) — `kubectl exec` fails with
"unable to upgrade connection: pod does not exist" even on a `Running` pod.
Read real container logs via Loki through Grafana's exposed datasource
proxy instead of adding new cluster exposure:

```bash
curl -u admin:shalm-admin \
  'http://192.168.105.3:30300/api/datasources/proxy/uid/<loki-uid>/loki/api/v1/query_range' \
  --data-urlencode 'query={app="quarkus-api", container="quarkus-api"}' \
  --data-urlencode 'start=<start-ns>' --data-urlencode 'end=<end-ns>'
```

Get `<loki-uid>` from `GET /api/datasources`. Labels available: `app`,
`container`, `pod`, `node`, `service_name`, `stream` (no `namespace` label).

### 5.11. Load `versions.env` as the first CI step

All workflows load the central version file before any other step:

```yaml
- name: Load versions
  run: grep -v '^#' versions.env | grep -v '^$' >> $GITHUB_ENV
```

Then use `${{ env.JAVA_VERSION }}`, `${{ env.QUARKUS_VERSION }}`, etc.
`versions.env` at the repo root is the single source of truth for all
version pins.

---

## 6. Build Phases

| #   | Phase                             | Status            | Notes                                                                                                       |
| --- | ---------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------ |
| 0   | Repo Bootstrap + ArgoCD root app   | `[x] done`         | Folder structure, root ArgoCD App-of-Apps                                                                    |
| 1   | Observability Stack                | `[x] done`         | Prometheus, Grafana, Loki+Promtail, dashboards                                                                |
| 2   | Quarkus API                        | `[x] done`         | REST API, in-memory state, metrics, structured logs, GHCR, GitOps                                             |
| 3   | Quarkus UI                         | `[x] done`         | Qute templates, balance/tx views, wired to API, GitOps                                                        |
| 4   | Hyperledger Fabric                 | `[x] done`         | Channel + CCAAS chaincode committed (seq 4), ledger initialized, API queries verified live                       |
| 5   | Istio                               | `[x] done`         | Sidecar injection, ingress gateway, Fabric traffic routing                                                    |
| 6   | Hyperledger Besu                    | `[s] skipped`      | Network config kept in `04_blockchain/besu/`; no K8s manifests                                                |
| 7   | Identity Service                    | `[s] skipped`      | No files generated                                                                                            |
| 8   | CI/CD                                | `[x] done`         | Workflows: quarkus-api, quarkus-ui, chaincode — multi-arch, manifest patch, ArgoCD sync                       |
| 9   | SRE Layer                            | `[x] done`         | PrometheusRules (API/pod/fabric), Alertmanager routing, simulate-failures.sh                                  |
| 10  | AI Observability                     | `[x] done`         | Python FastAPI, Loki+Prometheus queries, /summary, /anomalies, GHCR CI                                        |

**Status key:** `[ ]` pending · `[~]` in-progress · `[x]` done and reviewed ·
`[s]` intentionally skipped

---

### Phase 0 — Repo Bootstrap + ArgoCD

**Objective:** stand up the folder scaffold and ArgoCD so every later phase
is just "add a child Application + push."

**Steps:**
```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# access
kubectl port-forward svc/argocd-server -n argocd 8080:443
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 --decode
```
(In this cluster ArgoCD is instead bootstrapped by the Ansible playbook in
Pre-Phase 0 — the manual `kubectl apply` above is the fallback if doing this
without Ansible.)

```bash
kubectl apply -f 01_infrastructure/base/namespaces.yaml
kubectl apply -f 02_gitops/root-app/root-app.yaml
```

**Deliverables:**
- Full folder scaffold matching §4
- `02_gitops/root-app/root-app.yaml` — the ArgoCD App-of-Apps parent
- One child `*-app.yaml` per component in `02_gitops/root-app/` (this repo
  ended up with one per observability sub-component too — kube-prometheus-stack,
  loki, promtail, jaeger, kiali, observability-alerts,
  observability-dashboards — rather than one monolithic "observability" app;
  either granularity works, but match what's already there if continuing)
- `01_infrastructure/base/namespaces.yaml` — namespace definitions for all
  components

**Rules that apply:** 5.0, 5.10b

---

### Phase 1 — Observability Stack

**Namespace:** `observability`

**Objective:** metrics and logs must exist cluster-wide before any app
layer is built on top, so every later phase can be observed from day one.

**Steps:**
- Deploy Prometheus + Grafana via Helm charts, as ArgoCD Applications
  pointed at `02_gitops/observability/` (kube-prometheus-stack in this repo)
- Deploy Loki + Promtail; ensure all namespaces are scraped and logs are
  labeled by pod + namespace
- Create Grafana dashboards: cluster CPU/memory, pod health, request latency

**Deliverables:**
- `02_gitops/observability/` — ArgoCD Applications for Prometheus, Grafana,
  Loki+Promtail
- Helm values overrides
- Grafana dashboard ConfigMaps: cluster health, pod health, request latency

**Output:** Grafana accessible at `:30300`, logs visible per namespace,
metrics scraped cluster-wide.

**Rules that apply:** 5.0

---

### Phase 2 — Quarkus API

**Namespace:** `quarkus-api`

**Objective:** own account state and process cross-bank transfers behind a
REST API — the only thing the UI and (later) Fabric integration ever talk to.

**Steps:**
1. Create the Quarkus Maven project with endpoints:
   ```
   POST /transfer
   GET  /accounts
   GET  /accounts/{id}
   GET  /balance/{id}          # kept for backward compat
   GET  /health
   GET  /metrics
   ```
2. Implement state as an in-memory `AccountService`, seeded with 4 accounts
3. Add Micrometer metrics: `transfer_request_count`,
   `transfer_request_latency`, `transfer_error_count`
4. Add structured JSON logging via MDC:
   `transaction_id, from_account, from_owner, from_bank, to_account,
   to_owner, to_bank, amount, status`
5. Write a two-stage Dockerfile:
   `maven:3.9.6-eclipse-temurin-21` → `eclipse-temurin:21-jre-jammy`
6. Containerize + push:
   ```bash
   docker build -t ghcr.io/samerhijazi/quarkus-api:latest .
   docker push ghcr.io/samerhijazi/quarkus-api:latest
   ```
   (in practice this is done by CI, not by hand — see Phase 8)
7. Write GitOps manifests: Deployment (NodePort 30800), Service

**Account model** (`Account.java`):
```
id      — e.g. ACC-B1-001
owner   — e.g. ClientA
bank    — e.g. Bank1
balance — integer tokens
```

**Seeded accounts:**
| ID          | Owner   | Bank  | Balance |
| ----------- | ------- | ----- | ------- |
| ACC-B1-001  | ClientA | Bank1 | 1000    |
| ACC-B1-002  | ClientC | Bank1 | 500     |
| ACC-B2-001  | ClientB | Bank2 | 1000    |
| ACC-B2-002  | ClientD | Bank2 | 500     |

**Deliverables:**
- `03_apps/quarkus-api/` — full Quarkus Maven project (as above) + `Dockerfile`
- `02_gitops/quarkus-app/` — Deployment (NodePort 30800), Service
- `.github/workflows/quarkus-api.yml` — build, multi-arch push to GHCR,
  patch manifest
- Test suite under `src/test/java/io/shalm/` (`AccountServiceTest` unit +
  `@QuarkusTest`/REST-assured resource tests) run in CI before every image
  build; results baked into the image and served at `GET /tests`

**Rules that apply:** 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.9, 5.11

---

### Phase 3 — Quarkus UI

**Namespace:** `quarkus-ui`

**Objective:** give a human a way to trigger the cross-bank transfer
scenario and see the result, without ever talking to Fabric directly.

**Steps:**
1. Build the UI as a Quarkus project using Qute templates (single template,
   `dashboard.html`, tab-based — do not split into multiple pages)
2. Wire it to quarkus-api via a MicroProfile REST Client pointed at
   `http://quarkus-api.quarkus-api.svc.cluster.local:8080`
3. Build the dashboard as 6 tabs:
   - **World State** (default): API balance vs Fabric on-chain balance per
     account; Fabric column shows "N/A" only if `FABRIC_ENABLED=false` or
     the peer is unreachable
   - **Blockchain**: Fabric-only ledger view + per-account sync status badge
   - **Transfers**: transfer form (From/To/Amount) + transaction history
     table (dropdowns show `ClientA (Bank1) — ACC-B1-001` labels, post
     account IDs)
   - **Accounts**: bank-grouped balance cards (Bank1/Org1, Bank2/Org2)
   - **Manage**: create/delete account forms
   - **Tests**: pass/fail summary for quarkus-api (fetched live via
     `ApiClient.getApiTestResults()`) and quarkus-ui's own bundled
     `test-results.json`
4. Add a standalone `/architecture` page (`ArchitectureResource.java` +
   `templates/architecture.html`) — hand-built inline SVG diagram + service
   URL/credentials table; link it from the dashboard header only, do not
   add it to the tab bar
5. Hold the last 20 transfers in an in-memory `TransactionStore` (lost on
   pod restart — this is intentional, not a bug to fix)
6. Write GitOps manifests: Deployment (NodePort 30801), Service

**Deliverables:**
- `03_apps/quarkus-ui/` — Quarkus project as above
- `02_gitops/quarkus-ui/` — Deployment (NodePort 30801), Service
- `.github/workflows/quarkus-ui.yml` — multi-arch build, GHCR push,
  manifest patch

**Rules that apply:** 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.9, 5.11

---

### Phase 4 — Hyperledger Fabric

**Namespace:** `fabric`

**Objective:** a real permissioned ledger backing the two banks, invoked by
quarkus-api and surfaced (read-only) through the UI.

**Steps:**
1. Generate Fabric crypto material for OrgA (Bank1), OrgB (Bank2), and one
   SOLO orderer → `04_blockchain/fabric/network-config/` (`crypto-material/`
   and `channel-artifacts/` are generated, gitignored — do not commit them)
2. Write the chaincode in **Java** using `fabric-chaincode-java` (**not**
   Go, **not** Quarkus/Jakarta REST — it's plain Java running inside
   Fabric's CCAAS container runtime):
   - `InitLedger()`
   - `Transfer(ctx, from, to, amount)`
   - `QueryBalance(ctx, account)`
   - `createAccount(ctx, accountId, bankId, owner, initialBalance)`
   - `deleteAccount(ctx, accountId)`
   - `deposit(ctx, accountId, amount)`
   - `getAccount(ctx, accountId)`
   - Accounts: ClientA/ACC-B1-001 (Bank1), ClientB/ACC-B2-001 (Bank2),
     ClientC/ACC-B1-002, ClientD/ACC-B2-002
   - **Critical:** the CCAAS server entrypoint must construct
     `NettyChaincodeServer` manually (needed to actually listen, since
     `ChaincodeBase.start(args)` always dials out to a peer as a client and
     never listens) — but the constructor must replicate `start(args)`'s
     exact real init sequence from fabric-chaincode-java 2.5.0's own
     source: `initializeLogging → processEnvironmentOptions →
     processCommandLineOptions → validateOptions → getChaincodeConfig →
     Metrics.initialize → Traces.initialize`. Skipping any step throws a
     different exception one call deeper, on the first real peer
     connection — don't debug that symptom as if it were something else.
3. Install `local-path-provisioner` **before** deploying peer/orderer PVCs
   (rule 5.10a0) — peer/orderer ledger storage is PVCs on the `local-path`
   StorageClass, not `emptyDir`, so a pod restart doesn't wipe the channel
4. Deploy peer0-org1, peer0-org2, and the orderer to K8s
   (`02_gitops/fabric/`) — use `strategy.type: Recreate`, not
   `RollingUpdate` (RWO local-path PVCs crash-loop the new pod on restart:
   `transientStoreFileLock` held twice)
5. Write the `fabric-setup` Job (`02_gitops/fabric/setup-job.yaml`) to
   create/join `mychannel` and install/approve/commit the chaincode —
   mount the **full** MSP dirs per rule 5.10a1, not just `config.yaml`
6. Create `fabric-org1-admin` / `fabric-org2-admin` secrets in **both**
   `fabric` and `quarkus-api` namespaces (`create-secrets.sh`, rule 5.10a)
7. Wire `FabricGatewayService` into `03_apps/quarkus-api/` — gRPC to the
   peer on port 7051, gated by the `FABRIC_ENABLED` env var (set `true` in
   `02_gitops/quarkus-app/deployment.yaml`)
8. Pin `grpc-netty-shaded` and `grpc-core` to matching versions in the
   Fabric SDK's dependency tree — a version skew between them crashes the
   first RPC with a classpath error, not a clear version-mismatch message
9. Use real `--tls false` pflag syntax carefully — a bare `--tls false`
   parses as `--tls` plus a stray positional arg, silently trying to enable
   TLS with no cert configured

**Deliverables:**
- `04_blockchain/fabric/network-config/` — crypto-config, configtx.yaml
- `04_blockchain/fabric/chaincode/` — Java chaincode as above
- `02_gitops/fabric/` — peer0-org1, peer0-org2, orderer manifests + PVCs +
  setup-job.yaml
- Fabric SDK integration in `03_apps/quarkus-api/` (`/fabric/*` endpoints)

**Rules that apply:** 5.5, 5.8, 5.10a0, 5.10a1, 5.10a, 5.10b — Fabric
images (`hyperledger/fabric-peer`, `hyperledger/fabric-orderer`) are
multi-arch upstream, pull directly, no custom build/CI needed for them.

**Status as of last session:** channel + chaincode committed (sequence 4),
but live invocations are blocked — **see §7 before doing anything here.**

---

### Phase 5 — Istio

**Namespace:** `istio-system`

**Objective:** service mesh for traffic control, observability, and mTLS
simulation — scoped to the Fabric namespace (not cluster-wide).

**Steps:**
- Deploy Istio as ArgoCD Applications rather than a one-shot `istioctl
  install` — this repo uses `02_gitops/istio/istiod-app.yaml` and
  `02_gitops/istio/ingressgateway-app.yaml` (Helm charts, GitOps-managed);
  prefer that pattern over an imperative `istioctl install --set
  profile=demo` so the mesh config survives cluster rebuilds
- Enable sidecar injection: `kubectl label namespace fabric
  istio-injection=enabled` (also written as `02_gitops/istio/namespace.yaml`)
- Configure an ingress gateway + VirtualService for Fabric endpoints
  (`02_gitops/istio/gateway.yaml`, `virtualservice.yaml`)
- Add `PeerAuthentication` and `Telemetry` resources for mTLS and tracing
  (`02_gitops/istio/peerauthentication.yaml`, `telemetry.yaml`) — Envoy
  emits traces to Jaeger:9411 (Zipkin protocol) at 100% sampling
- Kiali (`02_gitops/istio/kiali-nodeport.yaml`) visualizes the resulting
  mesh topology at `:30088`

**Deliverables:**
- `02_gitops/istio/` as listed above
- Sidecar injection label on the `fabric` namespace
- IngressGateway + VirtualService for Fabric endpoints

**Output:** Fabric traffic routed via Istio, telemetry enabled.

**Rules that apply:** 5.0, 5.10b. **Known caveat:** Istio sidecar injection
was removed from `quarkus-ui`'s namespace (see commit `706828d`) after it
caused issues there — if a similar symptom appears elsewhere, that's
precedent for disabling injection on that namespace rather than debugging
the mesh further.

---

### Phase 6 — Hyperledger Besu (skipped)

**Namespace:** `besu` (not created)

If ever picked up: 3-node PoA (IBFT/QBFT) network, minimal config, a simple
token-transfer + balance-query Solidity contract, backend interacts with it
only for comparison (not the primary UI focus), observability via block
production metrics / RPC logs / node health. The `hyperledger/besu`
official image is multi-arch, no custom build needed. Apply rule 5.8
(Calico restart) if pods stay `ContainerCreating` after a gap between
phases.

**Deliverables if built:** `04_blockchain/besu/network-config/` (genesis,
static-nodes — placeholder exists), `04_blockchain/besu/contracts/`,
`02_gitops/besu/` (3-node Deployments/Services), Prometheus scrape config.

---

### Phase 7 — Identity Service (skipped)

**Namespace:** `identity` (not created)

If ever picked up: a custom Quarkus-based OIDC-like provider —
`POST /login`, `GET /token`, `GET /.well-known/openid-configuration`, JWT
issuance with `user_id` + `bank` claims. quarkus-api would validate the JWT
and map identity → account; Fabric would map identity → org/MSP context.
Apply the full Phase-2-style checklist (rules 5.1–5.6, 5.9, 5.11) plus:
use `Response.status(401)` — confirm any status constant exists in Jakarta
REST 3.0 before using it (rule 5.6). Workflow goes in
`.github/workflows/identity-service.yml`, not `05_cicd/`.

---

### Phase 8 — CI/CD

**Objective:** every app change flows build → push → manifest patch →
ArgoCD sync, with no manual image builds.

**Steps:**
1. For each app, create a GitHub Actions workflow at
   `.github/workflows/<app>.yml` (not `05_cicd/` — see rule 5.2) that:
   - loads `versions.env` first (rule 5.11)
   - builds multi-arch (rule 5.1)
   - pushes to `ghcr.io/samerhijazi/<app>:${GITHUB_SHA}`
   - patches the image tag in the corresponding `02_gitops/` manifest,
     commits, and pushes (rule 5.5 applies to your own subsequent local
     pulls after this)
2. Rely on ArgoCD's existing auto-sync (configured in Phase 0) to pick up
   the manifest change and deploy — no separate ArgoCD step needed per app

**Deliverables:**
- `.github/workflows/quarkus-api.yml`
- `.github/workflows/quarkus-ui.yml`
- `.github/workflows/chaincode.yml`
- `.github/workflows/ai-agent.yml`

**Rules that apply:** 5.1, 5.2, 5.3, 5.4, 5.5, 5.9, 5.11

---

### Phase 9 — SRE Layer

**Objective:** know when the platform breaks, before a human notices.

**SLOs:**
- Quarkus: latency < 200ms, error rate < 1%
- Fabric: tx success rate > 98%

**Steps:**
1. Write `PrometheusRule` CRDs under `02_gitops/observability/alerts/`:
   high latency (>200ms), error rate (>1%), pod down, Fabric tx failures
2. Configure Alertmanager routing/inhibit rules (null receiver by default —
   this is intentional, not a misconfiguration to "fix")
3. Write `01_infrastructure/scripts/simulate-failures.sh` to exercise pod
   kill / peer shutdown / latency injection, and confirm: alert fires →
   logs correlate in Loki → system recovers

**Deliverables:**
- `02_gitops/observability/alerts/` — PrometheusRule CRDs (API/pod/fabric)
- Alertmanager config
- `01_infrastructure/scripts/simulate-failures.sh`

**Rules that apply:** 5.10b

---

### Phase 10 — AI Observability

**Namespace:** `ai`

**Objective:** a standalone service that turns raw Loki logs + Prometheus
metrics into a human-readable summary and basic anomaly flags — read-only,
optional Grafana panel integration.

**Steps:**
1. Build a Python FastAPI service with:
   ```
   GET /summary     — summarizes recent Loki logs
   GET /anomalies    — rule-based anomaly detection on Prometheus metrics
   ```
2. Base image `python:3.12-slim` is already multi-arch — the custom image
   built on top of it still needs `platforms: linux/amd64,linux/arm64` in
   the CI build step (rule 5.1 still applies even though the base is fine)
3. Write GitOps manifests: Deployment, Service (NodePort 30810)
4. Write `.github/workflows/ai-agent.yml`

**Deliverables:**
- The Python FastAPI service (verify current path in the live tree — see
  §4 note; original plan said `06_ai/observability-agent/`)
- `02_gitops/ai/` — Deployment, Service manifests
- `.github/workflows/ai-agent.yml`

**Rules that apply:** 5.1, 5.2, 5.3, 5.4, 5.5, 5.11

---

## 7. Fabric Live-Transaction Incident (resolved)

**Session date: 2026-09-13/14.** `FABRIC_ENABLED=true` is live, the channel
`mychannel` genuinely exists, and the `bank-transfer` chaincode is
installed/approved/**committed at sequence 4** on both peers — none of
that had ever actually worked before this session, and 11 distinct real
bugs were found and fixed to get there (all merged to `main`, commits
`b04d549` through `d6481f0` — folded into Phase 4's steps above as items
2–9, in case this needs to be reproduced from zero):

1. `FABRIC_ENABLED` was hardcoded false + `fabric-org1-admin` secret only
   existed in the `fabric` namespace, not `quarkus-api` (rule 5.10a)
2. `grpc-netty-shaded:1.63.0` vs `fabric-gateway`'s transitive
   `grpc-core:1.62.2` — classpath skew crashed the first RPC
3. `fabric-setup` Job only mounted `config.yaml`, never the
   cacerts/signcerts/keystore/tlscacerts MSP subdirectories (rule 5.10a1)
4. `--tls false` is invalid pflag syntax (parses as `--tls` + a stray
   arg) — silently tried to enable TLS with no cert configured
5. No StorageClass existed at all — added `local-path-provisioner`
   (rule 5.10a0) and moved peer/orderer off `emptyDir`
6. `RollingUpdate` strategy + RWO local-path PVCs crash-looped the new
   pod on every restart (`transientStoreFileLock` held twice) — fixed
   with `strategy.type: Recreate`
7. `tar czf` embeds a timestamp, so re-packaging identical chaincode
   content produced a different package ID every run
8. `PACKAGE_ID` was extracted by grepping `queryinstalled`, which
   accumulates one line per historical install — matched all of them
   into one garbled multi-line value that didn't match anything real
9. **Chaincode CCAAS init sequence** (`04_blockchain/fabric/chaincode/.../BankChaincode.java`):
   constructing `NettyChaincodeServer` manually skips everything
   `ChaincodeBase.start(args)` normally does before connecting. Fixed
   by replicating the exact real sequence from fabric-chaincode-java
   2.5.0's own source (see Phase 4 step 2). **Verified**: chaincode
   container logs now show zero exceptions and clean registration with
   both peers.

10. **CCAAS registration ID:** the chaincode pod registered with the label
    `bank-transfer_1.0`, while peers waited for the full installed package ID.
    `02_gitops/fabric/chaincode-config.yaml` is now the shared source for the
    full ID, and the setup Job checks its calculated package hash against it.
11. **Query response encoding:** `ResponseUtils.newSuccessResponse(String)`
    writes to Fabric's message field. Gateway evaluate calls read the payload,
    so `QueryBalance` and `getAccount` now return UTF-8 byte payloads.

The earlier Envoy timeout was a downstream symptom of the peer waiting for a
chaincode registration whose ID could never match. The setup Job also disables
sidecar injection so it exits when its work completes. Live verification showed
`InitLedger` succeeding, all four `/fabric/balance/{id}` calls returning their
expected values, and every Blockchain UI row reporting `sync`.

Chaincode sequence remains **4**. If packaging metadata or `connection.json`
changes, update the ID in `chaincode-config.yaml`; the setup Job will fail fast
and print the newly calculated value if they differ.

---

## Final Expected End State

A running platform where:

- user triggers a transaction via the UI
- quarkus-api processes it: updates local in-memory state and (once §7 is
  resolved) writes a Fabric ledger update
- the observability stack shows metrics, logs, and system behavior for the
  whole flow
- CI/CD deploys any code change automatically via GitOps
- failures can be simulated (`simulate-failures.sh`) and observed recovering
- the AI agent can summarize what happened from Loki/Prometheus data alone
