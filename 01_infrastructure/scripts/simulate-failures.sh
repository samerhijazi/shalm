#!/usr/bin/env bash
# simulate-failures.sh — inject failures for demo/testing purposes

set -euo pipefail

NS_API="quarkus-api"
NS_UI="quarkus-ui"
NS_FABRIC="fabric"
API_URL="http://192.168.105.3:30800"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Failure injection:
  kill-api              Delete the quarkus-api pod (ArgoCD respawns it)
  kill-ui               Delete the quarkus-ui pod  (ArgoCD respawns it)
  kill-peer             Scale fabric peer0-org1 to 0 (simulates peer outage)
  restore-peer          Scale fabric peer0-org1 back to 1
  flood                 Send 50 concurrent transfer requests to stress the API
  status                Show pod readiness across all shalm namespaces
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

# ── Dispatch ──────────────────────────────────────────────────────────────────

case "${1:-}" in
  kill-api)         kill_api ;;
  kill-ui)          kill_ui ;;
  kill-peer)        kill_peer ;;
  restore-peer)     restore_peer ;;
  flood)            flood ;;
  status)           show_status ;;
  *)                usage ;;
esac
