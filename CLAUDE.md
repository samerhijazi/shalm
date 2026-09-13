# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Where to Start

Read `03_implementation-status.md` first — it has the current phase, what's done, and the live rules that apply to every phase going forward.

## Documentation Rule (mandatory)

After **any** change to the platform — new feature, bug fix, config change, new service, phase completion, or infrastructure update — you **must** update all three:

1. **`CLAUDE.md`** — keep Platform URLs, Architecture, Known Issues, and component descriptions current
2. **`03_implementation-status.md`** — update the phase table, Current State section, and Live Services table
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
| Jaeger           | http://192.168.105.3:30686 | —                          |
| Kiali            | http://192.168.105.3:30088 | —                          |

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
               Jaeger all-in-one (port 30686) — traces via Envoy/Zipkin protocol
               Kiali (port 30088) — service mesh topology, traffic graph, Jaeger+Grafana integration
Service mesh:  Istio (namespace: istio-system) — sidecar on fabric namespace
               Envoy emits traces to Jaeger:9411 (Zipkin); 100% sampling rate
AI agent:      FastAPI (namespace: ai, port 30810) — GET /summary (Loki), GET /anomalies (Prometheus)
```

**Key constraint:** UI never communicates directly with Fabric or Besu — always through the API.

### Quarkus API (`03_apps/quarkus-api/`, namespace `quarkus-api`)

- Jakarta REST 3.0 resources: `AccountsResource`, `BalanceResource`, `TransferResource`, `FabricResource`
- `AccountService` holds in-memory state; seeded with 4 accounts (ACC-B1-001/002, ACC-B2-001/002)
- `FabricGatewayService` wraps Fabric Gateway SDK — enabled via `FABRIC_ENABLED` env var (`true` in `02_gitops/quarkus-app/deployment.yaml`); requires the `fabric-org1-admin` secret to exist in the `quarkus-api` namespace (see Known Issues)
- `GET /tests` — serves the JSON test-result summary baked into the image at CI build time (read by the quarkus-ui "Tests" dashboard tab)
- Micrometer metrics: `transfer_request_count`, `transfer_request_latency`, `transfer_error_count`
- Structured JSON logs with MDC fields: `transaction_id`, `from_account`, `to_account`, `amount`, `status`
- Tests: `src/test/java/io/shalm/` — `AccountServiceTest` (unit) + `@QuarkusTest`/REST-assured resource tests; run in CI before every image build

### Quarkus UI (`03_apps/quarkus-ui/`, namespace `quarkus-ui`)

- Single Qute template: `dashboard.html` — 6 tabs:
  - **World State** (default): table comparing API balance vs Fabric on-chain balance per account; Fabric column shows "N/A" only if `FABRIC_ENABLED=false` or the peer is unreachable
  - **Blockchain**: Fabric-only ledger view + per-account sync status badge
  - **Transfers**: transfer form (From / To / Amount) at top; transaction history table below
  - **Accounts**: bank-grouped balance cards (Bank1/Org1, Bank2/Org2)
  - **Manage**: create account + delete account forms
  - **Tests**: pass/fail summary cards for quarkus-api (fetched live via `ApiClient.getApiTestResults()`) and quarkus-ui (its own bundled `test-results.json`)
- `ApiClient` (MicroProfile REST Client) calls quarkus-api at `http://quarkus-api.quarkus-api.svc.cluster.local:8080`
- `TransactionStore` holds last 20 transfers in memory (lost on pod restart)
- `LedgerEntry` DTO combines `AccountInfo` (API) + Fabric balance string per account

### Fabric (`04_blockchain/fabric/`, namespace `fabric`)

- 2 orgs (Org1/Bank1, Org2/Bank2), 1 peer each, 1 SOLO orderer
- Chaincode server entrypoint constructs `NettyChaincodeServer` manually (needed for real CCAAS server mode — `ChaincodeBase.start(args)` always dials out to a peer as a client, it never listens); the constructor must call `processEnvironmentOptions()` **and** `processCommandLineOptions(args)` before that, since `chaincodeConfig` isn't populated otherwise
- Peer/orderer ledger storage is PersistentVolumeClaims (`local-path` StorageClass, `01_infrastructure/base/local-path-provisioner.yaml`) — not `emptyDir`, so a pod restart doesn't wipe the channel
- `fabric-setup` Job (`02_gitops/fabric/setup-job.yaml`) creates/joins `mychannel` and installs/approves/commits the chaincode — mounts full MSP dirs (cacerts/signcerts/keystore/tlscacerts) for the org1/org2 admin identities, not just `config.yaml`
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

**ArgoCD auto-sync reverts manual `kubectl apply` / `kubectl edit` on any GitOps-managed resource** — a live edit that isn't pushed to `origin/main` gets silently reverted on the next reconcile (visible as a Synced→OutOfSync→Synced / Healthy→Degraded cycle in `kubectl get applications -n argocd`). Always commit + push the manifest change instead of patching the live object directly.
