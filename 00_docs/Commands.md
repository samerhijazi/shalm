# Shalm Platform — Commands & Reference

> Master node: `192.168.105.3` | Workers: `192.168.105.4`, `192.168.105.5`
> GitHub: `https://github.com/samerhijazi/shalm`
> Registry: `ghcr.io/samerhijazi`

---

## Platform URLs

| Service        | URL                              | User    | Password            |
| -------------- | -------------------------------- | ------- | ------------------- |
| ArgoCD         | http://192.168.105.3:30080       | admin   | `GhPtA0-v7iFqnPkX` |
| Grafana        | http://192.168.105.3:30300       | admin   | `shalm-admin`       |
| Prometheus     | http://192.168.105.3:30090       | —       | —                   |
| AlertManager   | http://192.168.105.3:30093       | —       | —                   |
| Quarkus API    | http://192.168.105.3:30800       | —       | —                   |
| Quarkus UI     | http://192.168.105.3:30801       | —       | — (Phase 3)         |

### Quarkus API Endpoints (Phase 2+)

```
POST http://192.168.105.3:30800/transfer
GET  http://192.168.105.3:30800/balance/{id}   # id = org1 | org2 | org3
GET  http://192.168.105.3:30800/health/ready
GET  http://192.168.105.3:30800/health/live
GET  http://192.168.105.3:30800/metrics
```

---

## Git — Push to GitHub

Run these from the repo root after each phase:

```bash
# Stage everything (review with git status first)
git add .
git status

# Commit — replace <phase> and <description>
git commit -m "Phase <N>: <description>"

# Push
git push origin main
```

### Phase-by-phase commit commands

```bash
# Phase 0+1 (already pushed)
# git commit -m "Phase 0+1: repo bootstrap, observability stack"

# Phase 2 — Quarkus API
git add 03_apps/quarkus-api/ 02_gitops/quarkus-app/ 02_gitops/root-app/quarkus-api-app.yaml 05_cicd/github-actions/quarkus-api.yml 03_implementation-status.md 00_docs/Commands.md
git commit -m "Phase 2: Quarkus API — REST, metrics, structured logs, GitOps, CI"
git push origin main

# Phase 3 — Quarkus UI
git add 03_apps/quarkus-ui/ 02_gitops/quarkus-ui/ 02_gitops/root-app/quarkus-ui-app.yaml 05_cicd/github-actions/quarkus-ui.yml 03_implementation-status.md
git commit -m "Phase 3: Quarkus UI — Qute templates, transfer form, balance view"
git push origin main

# Phase 4 — Hyperledger Fabric
git add 04_blockchain/fabric/ 02_gitops/fabric/ 02_gitops/root-app/fabric-app.yaml 03_implementation-status.md
git commit -m "Phase 4: Hyperledger Fabric — 2 orgs, Go chaincode, K8s manifests"
git push origin main

# Phase 5 — Istio
git add 02_gitops/istio/ 02_gitops/root-app/istio-app.yaml 03_implementation-status.md
git commit -m "Phase 5: Istio — sidecar injection, ingress gateway, virtual services"
git push origin main

# Phase 6 — Hyperledger Besu
git add 04_blockchain/besu/ 02_gitops/besu/ 02_gitops/root-app/besu-app.yaml 03_implementation-status.md
git commit -m "Phase 6: Hyperledger Besu — 3-node QBFT, Solidity contract"
git push origin main

# Phase 7 — Identity Service
git add 03_apps/identity-service/ 02_gitops/identity/ 02_gitops/root-app/identity-app.yaml 05_cicd/github-actions/identity-service.yml 03_implementation-status.md
git commit -m "Phase 7: Identity Service — OIDC mock, JWT, API validation"
git push origin main

# Phase 8 — CI/CD
git add 05_cicd/ 03_implementation-status.md
git commit -m "Phase 8: CI/CD — GitHub Actions for all apps, GHCR push, manifest patch"
git push origin main

# Phase 9 — SRE Layer
git add 02_gitops/observability/alerts/ 01_infrastructure/scripts/ 03_implementation-status.md
git commit -m "Phase 9: SRE — PrometheusRules, Alertmanager, failure scripts"
git push origin main

# Phase 10 — AI Observability
git add 06_ai/ 02_gitops/ai/ 02_gitops/root-app/ai-app.yaml 05_cicd/github-actions/ai-agent.yml 03_implementation-status.md
git commit -m "Phase 10: AI Observability — FastAPI summary + anomaly detection"
git push origin main
```

---

## ArgoCD

```bash
# Bootstrap (run once after first push)
kubectl apply -f 01_infrastructure/base/namespaces.yaml
kubectl apply -f 02_gitops/root-app/root-app.yaml

# Check all apps
kubectl get applications -n argocd

# Force sync a specific app
kubectl annotate app <app-name> -n argocd argocd.argoproj.io/refresh=hard --overwrite
argocd app sync <app-name>

# Watch sync status
kubectl get applications -n argocd -w

# Get ArgoCD admin password (if lost)
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

---

## Observability

```bash
# Check all pods
kubectl get pods -n observability

# Prometheus targets
open http://192.168.105.3:30090/targets

# Grafana — verify Loki datasource
open http://192.168.105.3:30300   # Connections → Data sources → Loki → Test

# Loki logs
kubectl logs loki-0 -n observability
kubectl logs loki-0 -n observability --previous

# Promtail
kubectl logs -l app.kubernetes.io/name=promtail -n observability
```

---

## Quarkus API (Phase 2)

```bash
# Check deployment
kubectl get pods -n quarkus-api
kubectl logs -l app=quarkus-api -n quarkus-api -f

# Test endpoints
curl -s http://192.168.105.3:30800/balance/org1 | jq
curl -s http://192.168.105.3:30800/balance/org2 | jq
curl -s http://192.168.105.3:30800/balance/org3 | jq

curl -s -X POST http://192.168.105.3:30800/transfer \
  -H "Content-Type: application/json" \
  -d '{"from":"org1","to":"org2","amount":100}' | jq

curl -s http://192.168.105.3:30800/health/ready
curl -s http://192.168.105.3:30800/metrics | grep transfer_
```

---

## Quarkus UI (Phase 3)

```bash
kubectl get pods -n quarkus-ui
kubectl logs -l app=quarkus-ui -n quarkus-ui -f
open http://192.168.105.3:30801
```

---

## Hyperledger Fabric (Phase 4)

```bash
kubectl get pods -n fabric
kubectl logs -l app=orderer -n fabric
kubectl logs -l app=peer0-orga -n fabric
kubectl logs -l app=peer0-orgb -n fabric
```

---

## Hyperledger Besu (Phase 6)

```bash
kubectl get pods -n besu
kubectl logs -l app=besu-node-0 -n besu
# RPC endpoint (after deploy)
curl -X POST http://192.168.105.3:308545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

---

## Identity Service (Phase 7)

```bash
kubectl get pods -n identity
curl -s http://192.168.105.3:30900/health/ready
curl -s -X POST http://192.168.105.3:30900/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass"}' | jq
```

---

## AI Observability (Phase 10)

```bash
kubectl get pods -n ai
curl -s http://192.168.105.3:30950/summary | jq
curl -s http://192.168.105.3:30950/anomalies | jq
```

---

## GHCR — Container Registry

```bash
# Login
echo $GITHUB_TOKEN | docker login ghcr.io -u samerhijazi --password-stdin

# Images
ghcr.io/samerhijazi/quarkus-api:<sha>
ghcr.io/samerhijazi/quarkus-ui:<sha>
ghcr.io/samerhijazi/identity-service:<sha>
ghcr.io/samerhijazi/ai-agent:<sha>

# Pull manually
docker pull ghcr.io/samerhijazi/quarkus-api:latest
```

---

## VM Access (Lima)

```bash
limactl list
limactl shell k8s-master
limactl shell k8s-worker-01
limactl shell k8s-worker-02

# kubeconfig
export KUBECONFIG=~/.lima/k8s-master/conf/kubeconfig.yaml
kubectl get nodes
```

---

## Kubernetes — General

```bash
# All namespaces overview
kubectl get pods -A

# Events (useful for debugging)
kubectl get events -n <namespace> --sort-by='.lastTimestamp'

# Describe a failing pod
kubectl describe pod <pod-name> -n <namespace> | tail -30

# Force delete a stuck pod
kubectl delete pod <pod-name> -n <namespace> --force --grace-period=0

# Check resource usage
kubectl top pods -A
kubectl top nodes
```
