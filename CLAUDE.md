# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Role

You are performing a multi-disciplinary role: Developer, Tester, DevOps, GitOps, SRE, and Operations.

For this project:
- **Developer**: Write production-ready code with types, error handling, and tests.
- **Tester**: Include unit tests, integration tests, and edge case coverage.
- **DevOps/GitOps**: Manage infrastructure as code (Terraform, Ansible, Helm), CI/CD pipelines, and deployment automation.
- **SRE/Operations**: Monitor reliability, document runbooks, define SLOs, and plan for failure modes.

**Documentation**: Maintain project context across all artifacts:
- Update README, architecture docs, and runbooks after each change.
- Keep a CHANGELOG for decision log.
- Track context in ADRs (Architecture Decision Records) for design choices.
- Link related files in comments to preserve cross-cutting concerns.

**Scope**: Complete solutions—code, tests, deployment configs, docs, and operations playbooks in one session.

## Where to Start

Read `00_roadmap_runbook.md` first — it has the current phase, what's done, and the live rules that apply to every phase going forward.

## Documentation Rule (mandatory)

After **any** change to the platform — new feature, bug fix, config change, new service, phase completion, or infrastructure update — you **must** update all three:

1. **`CLAUDE.md`** — keep Platform URLs, Architecture, Known Issues, and component descriptions current
2. **`00_roadmap_runbook.md`** — update the phase table, Current State section, and Live Services table
3. **`README.md`** — keep Live Services table, Phase Status table, and Architecture summary in sync with the above two files

Do not commit without updating these three files. They are the single source of truth for future sessions.

## Cluster & Platform

- **Cluster:** 3 Lima VMs — master `192.168.105.3`, workers `192.168.105.4 / .5`
- **All nodes are arm64** — every custom Docker image must be built multi-arch (`linux/amd64,linux/arm64`)
- **Registry:** `ghcr.io/samerhijazi` — new packages are private by default; make them public after first CI push
- **Kubeconfig:** Cluster config is saved in `~/.kube/config` — `kubectl` works without any extra env vars.

```bash
limactl shell k8s-master          # SSH into master
kubectl get pods -A               # overview
kubectl get applications -n argocd
```

## Platform URLs

| Service          | URL                        | Credentials                |
| ---------------- | -------------------------- | -------------------------- |
| ArgoCD           | http://192.168.105.3:30080 | admin / `GhPtA0-v7iFqnPkX` |
| Grafana          | http://192.168.105.3:30300 | admin / `shalm-admin`      |
| Prometheus       | http://192.168.105.3:30090 | —                          |
| Alertmanager     | http://192.168.105.3:30093 | —                          |
| Quarkus API      | http://192.168.105.3:30800 | —                          |
| Quarkus UI       | http://192.168.105.3:30801 | —                          |
| AI Agent         | http://192.168.105.3:30810 | —                          |

## Build & Run

All versions are in `versions.env` at repo root — single source of truth.

```bash
# Build Quarkus app (no -q — hides errors)
cd 03_apps/quarkus-api
mvn package -DskipTests

cd 03_apps/quarkus-ui
mvn package -DskipTests

# Run locally in dev mode
mvn quarkus:dev

# Run tests
mvn test

# Fabric chaincode regression tests
mvn -f 04_blockchain/fabric/chaincode/pom.xml test

# Validate Fabric package ID or verify a live deployment
./04_blockchain/fabric/chaincode/validate-release.sh
./01_infrastructure/scripts/verify-fabric.sh
```

Dockerfiles use a two-stage build: `maven:3.9.6-eclipse-temurin-21` → `eclipse-temurin:21-jre-jammy`.

## CI/CD Rules

Every workflow must:

1. Load versions as the first step: `grep -v '^#' versions.env | grep -v '^$' >> $GITHUB_ENV`
2. Use multi-arch build (QEMU + Buildx + `platforms: linux/amd64,linux/arm64`)
3. Live in `.github/workflows/<name>.yml` — files in `05_cicd/github-actions/` are documentation only
4. Use `mvn package -DskipTests` (never `-q`)

After CI pushes a manifest update, always pull before pushing locally:

```bash
git pull --rebase origin main && git push origin main
```

## GitOps (ArgoCD)

App-of-Apps pattern: `02_gitops/root-app/root-app.yaml` → child Applications in `02_gitops/root-app/` → manifests in `02_gitops/<component>/`.

```bash
# Bootstrap (once)
kubectl apply -f 01_infrastructure/base/namespaces.yaml
kubectl apply -f 02_gitops/root-app/root-app.yaml

# Force sync
argocd app sync <app-name>
kubectl annotate app <app-name> -n argocd argocd.argoproj.io/refresh=hard --overwrite
```

## Architecture

```
quarkus-ui (Qute, port 30801)
    └── REST client → quarkus-api (port 30800)
                          ├── in-memory account state (4 accounts, 2 banks)
                          └── /fabric/* → FabricGatewayService → Hyperledger Fabric peer (gRPC)

Observability: Prometheus + Grafana + Loki + Promtail (namespace: observability)
               Alertmanager (routing + inhibit rules, null receiver by default)
               PrometheusRules: latency, error rate, pod health, Fabric health
AI agent:      FastAPI (namespace: ai, port 30810) — GET /summary (Loki), GET /anomalies (Prometheus)
```

**Key constraint:** UI never communicates directly with Fabric or Besu — always through the API.

### Quarkus API (`03_apps/quarkus-api/`, namespace `quarkus-api`)

- Jakarta REST 3.0 resources: `AccountsResource`, `BalanceResource`, `TransferResource`, `FabricResource`, `FabricLedgerResource`, `NetworkStatusResource`, `ConsistencyResource`
- `AccountService` holds in-memory state; seeded with 4 accounts (ACC-B1-001/002, ACC-B2-001/002); `Account.updatedAt` is stamped on creation and on every transfer
- `FabricGatewayService` wraps Fabric Gateway SDK — enabled via `FABRIC_ENABLED` env var (`true` in `02_gitops/quarkus-app/deployment.yaml`); requires the `fabric-org1-admin` secret to exist in the `quarkus-api` namespace (see Known Issues). Also holds a second `Contract` handle for the built-in `qscc` system chaincode (no chaincode redeploy needed) and exposes `evaluateQscc(...)` for block/ledger queries.
- `POST /transfer` is a best-effort dual-write: it always updates the in-memory `AccountService` state (authoritative for success/failure), then — only when `FABRIC_ENABLED` and the gateway is connected — also submits the same transfer to Fabric via `contract.newProposal(...).endorse().submitAsync().getStatus()` and attaches the real `fabricTransactionId`/`fabricBlockNumber`/`fabricValidationCode`/`fabricStatus` (`committed`/`failed`/`unavailable`) to the response. A Fabric failure never rolls back or fails the in-memory transfer. `POST /fabric/transfer` uses the same real Fabric transaction id (previously fabricated a random UUID — fixed).
- `FabricLedgerService` parses real Fabric blocks via `qscc`'s `GetChainInfo`/`GetBlockByNumber` (protobuf classes come from `fabric-protos`, already a transitive dependency of `fabric-gateway` — no new Maven dependency). `GET /fabric/blocks?limit=`, `GET /fabric/blocks/{number}` return 503 (matching `FabricResource`'s convention) when Fabric is unavailable.
- `GET /fabric/network-status` and `GET /consistency` always return 200 (never 503) — they report Fabric-down as data (`fabricAvailable: false`), not as an HTTP error, since the UI needs a renderable "not connected" state rather than an exception.
- `GET /tests` — serves the JSON test-result summary baked into the image at CI build time (read by the quarkus-ui "Tests" dashboard tab)
- Micrometer metrics: `transfer_request_count`, `transfer_request_latency`, `transfer_error_count`
- Structured JSON logs with MDC fields: `transaction_id`, `from_account`, `to_account`, `amount`, `status`
- Tests: `src/test/java/io/shalm/` — `AccountServiceTest` (unit) + `@QuarkusTest`/REST-assured resource tests; run in CI before every image build

### Quarkus UI (`03_apps/quarkus-ui/`, namespace `quarkus-ui`)

- Standalone `/architecture` page (`ArchitectureResource.java` + `templates/architecture.html`) — static platform architecture diagram (hand-built inline SVG) and a service URLs/credentials table; linked from the dashboard header, not part of the tab bar
- Qute templates: `dashboard.html` (shell — head/style/header/5-tab nav/JS) `{#include}`s five partials under `templates/tabs/`, one per top-level tab. Five top-level tabs, default landing tab is **Dashboard**:
  - **Dashboard**: summary cards (total token supply, account count, org count, latest Fabric block, network health, API/Fabric consistency status), accounts-by-bank, 5 most recent transactions, Fabric network/component status, "New Transfer" CTA
  - **Accounts**: merged Accounts + Manage Accounts — searchable/sortable table (client-side JS filter/sort, no server round trip), Create Account modal, per-account View/Transfer/Close actions with a same-page detail panel and a Close-Account confirmation modal whose wording is conditional on `networkStatus.fabricAvailable` (blockchain-history wording only when Fabric is actually connected)
  - **Transfer**: form with client + server validation (same-account, non-positive amount, amount > balance), honest lifecycle (`Submitting` → `Committed`/`Failed`, no fabricated intermediate states), transaction history with an expandable row showing Fabric metadata only when the dual-write actually ran
  - **Ledger**: **World State** subtab (authoritative API balance + compact verified/diverged badge, sourced from `/consistency`) and **Blockchain** subtab (real blocks/transactions from `/fabric/blocks`, or an honest "unavailable" state — never account balances as a substitute)
  - **Operations**: **Network** subtab (channel, chaincode name+version, latest block, badges — Peer0 Org2/Orderer/State DB are honestly "Unknown", not fabricated, since only one Fabric identity is connected), **Consistency** subtab (the detailed API-vs-Fabric comparison, moved here from the old World State tab, plus a "Run Consistency Check" action), **Tests** subtab (same test-result cards as before)
- `ApiClient` (MicroProfile REST Client) calls quarkus-api at `http://quarkus-api.quarkus-api.svc.cluster.local:8080`
- `TransactionStore` holds last 20 transfers in memory (lost on pod restart); `getForAccount(id)`/`getRecent(n)` support the Accounts detail panel and Dashboard's recent-transactions card
- `DashboardResource` computes `totalSupply`/`orgCount`/`txByAccount`/`closeAccountWarning` and fetches `NetworkStatus`/`ConsistencyReport`/`BlocksResponse` on every render, each with a defensive try/catch falling back to an honest "unavailable" object (same pattern as the pre-existing `fetchAccounts()`)

### Fabric (`04_blockchain/fabric/`, namespace `fabric`)

- 2 orgs (Org1/Bank1, Org2/Bank2), 1 peer each, 1 SOLO orderer
- Chaincode server entrypoint constructs `NettyChaincodeServer` manually (needed for real CCAAS server mode — `ChaincodeBase.start(args)` always dials out to a peer as a client, it never listens); the constructor must replicate `start(args)`'s exact real sequence — `initializeLogging → processEnvironmentOptions → processCommandLineOptions → validateOptions → getChaincodeConfig → Metrics.initialize → Traces.initialize` — or skipping any step throws a different exception one call deeper on the first real peer connection
- Live Fabric queries are operational. CCAAS must register with the complete package ID from `02_gitops/fabric/chaincode-config.yaml`, and query methods must return values in the Fabric response payload rather than its message field.
- Lifecycle name, version, sequence, label, and CCAAS address are sourced from `04_blockchain/fabric/chaincode/release.env`. For a behavior change, bump version and sequence, run `update-release-config.sh`, and confirm with `validate-release.sh`; Fabric's `calculatepackageid` command is authoritative.
- Peer/orderer ledger storage is PersistentVolumeClaims (`local-path` StorageClass, `01_infrastructure/base/local-path-provisioner.yaml`) — not `emptyDir`, so a pod restart doesn't wipe the channel
- `fabric-setup` is an Argo CD Sync hook (`02_gitops/fabric/setup-job.yaml`): it creates/joins `mychannel`, checks the canonical package ID, and installs/approves/commits the chaincode. It mounts full MSP dirs (cacerts/signcerts/keystore/tlscacerts) for both admin identities, not just `config.yaml`.
- Chaincode CI runs unit tests, release metadata validation, and a disposable two-organization CCAAS integration test before publishing an image. Argo CD follows with a PostSync API/UI smoke test (`02_gitops/fabric/smoke-test-job.yaml`).
- `/fabric/health` performs a real ledger query; the API readiness check includes Fabric when `FABRIC_ENABLED=true`. Run `01_infrastructure/scripts/verify-fabric.sh` after a rollout to check deployments, all four seeded balances, and the rendered Ledger → Blockchain tab.
- **Java chaincode** (`fabric-chaincode-java` SDK — not Quarkus): `InitLedger`, `Transfer`, `QueryBalance`, `createAccount`, `getAccount`, `deposit`, `deleteAccount`
- API calls Fabric via `FabricGatewayService` using gRPC (port 7051)

## Known Issues (apply to every phase)

**`kubectl logs` is broken** — kubelet port 10250 unreachable. Debug crashing pods with:

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

**Jakarta REST 3.0** — `Response.Status.UNPROCESSABLE_ENTITY` does not exist. Use `Response.status(422)`. Verify any status constant exists before using it.

**PAT scope** — pushing `.github/workflows/` files requires a PAT with `workflow` scope.

**`kubectl exec`/`kubectl logs` both go through the same broken kubelet path** — `kubectl exec` fails with `unable to upgrade connection: pod does not exist` even when the pod is `Running`. To read a live pod's actual logs, query Loki through Grafana's already-exposed datasource proxy instead of adding new cluster exposure:

```bash
curl -u admin:shalm-admin \
  'http://192.168.105.3:30300/api/datasources/proxy/uid/P8E80F9AEF21F6940/loki/api/v1/query_range' \
  --data-urlencode 'query={app="quarkus-api", container="quarkus-api"}' \
  --data-urlencode 'start=<start-ns>' --data-urlencode 'end=<end-ns>'
```

Find the Loki datasource UID via `GET /api/datasources`. Valid Loki labels here are `app`, `container`, `pod`, `node`, `service_name`, `stream` (no `namespace` label).

**Fabric client secret is namespace-scoped** — `create-secrets.sh` originally only created `fabric-org1-admin` in the `fabric` namespace, but `quarkus-api`'s Deployment (namespace `quarkus-api`) mounts it too, and Secrets can't be shared across namespaces. The script now creates it in both; if Fabric was set up before this fix, copy it manually:

```bash
kubectl get secret fabric-org1-admin -n fabric -o json \
  | python3 -c "import json,sys; d=json.load(sys.stdin); d['metadata']={'name':'fabric-org1-admin','namespace':'quarkus-api'}; print(json.dumps(d))" \
  | kubectl apply -f -
```

**ConfigMaps are namespace-scoped too, same as Secrets** — `02_gitops/fabric/chaincode-config.yaml`'s `fabric-chaincode-config` ConfigMap lives in the `fabric` namespace, but `quarkus-api`'s Deployment runs in the `quarkus-api` namespace and can't mount it directly. Rather than duplicating the ConfigMap across namespaces, `FABRIC_CHAINCODE_VERSION` in `02_gitops/quarkus-app/deployment.yaml` is a plain literal value kept manually in sync with `chaincode-version` in `chaincode-config.yaml` — update both together when the chaincode version changes; it's used only to display the version on the Operations → Network page.

**ArgoCD auto-sync reverts manual `kubectl apply` / `kubectl edit` on any GitOps-managed resource** — a live edit that isn't pushed to `origin/main` gets silently reverted on the next reconcile (visible as a Synced→OutOfSync→Synced / Healthy→Degraded cycle in `kubectl get applications -n argocd`). Always commit + push the manifest change instead of patching the live object directly.
