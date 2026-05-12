# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Where to Start

Read `03_implementation-status.md` first — it has the current phase, what's done, and the live rules that apply to every phase going forward.

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
Service mesh:  Istio (namespace: istio-system) — sidecar on fabric namespace
AI agent:      FastAPI (namespace: ai, port 30810) — GET /summary (Loki), GET /anomalies (Prometheus)
```

**Key constraint:** UI never communicates directly with Fabric or Besu — always through the API.

### Quarkus API (`03_apps/quarkus-api/`, namespace `quarkus-api`)

- Jakarta REST 3.0 resources: `AccountsResource`, `BalanceResource`, `TransferResource`, `FabricResource`
- `AccountService` holds in-memory state; seeded with 4 accounts (ACC-B1-001/002, ACC-B2-001/002)
- `FabricGatewayService` wraps Fabric Gateway SDK — enabled via `FABRIC_ENABLED=true` env var (off by default)
- Micrometer metrics: `transfer_request_count`, `transfer_request_latency`, `transfer_error_count`
- Structured JSON logs with MDC fields: `transaction_id`, `from_account`, `to_account`, `amount`, `status`

### Quarkus UI (`03_apps/quarkus-ui/`, namespace `quarkus-ui`)

- Single Qute template: `dashboard.html` — bank-grouped balance view + transfer form + transaction history
- `ApiClient` (MicroProfile REST Client) calls quarkus-api at `http://quarkus-api.quarkus-api.svc.cluster.local:8080`
- `TransactionStore` holds last 20 transfers in memory (lost on pod restart)

### Fabric (`04_blockchain/fabric/`, namespace `fabric`)

- 2 orgs (Org1/Bank1, Org2/Bank2), 1 peer each, 1 SOLO orderer
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
