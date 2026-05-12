#!/usr/bin/env bash
# simulate-failures.sh — inject and restore failures for SRE testing

set -euo pipefail

NS_API="quarkus-api"
NS_UI="quarkus-ui"
NS_FABRIC="fabric"
API_URL="http://192.168.105.3:30800"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command>

Commands:
  kill-api         Delete the quarkus-api pod (ArgoCD respawns it)
  kill-ui          Delete the quarkus-ui pod  (ArgoCD respawns it)
  kill-peer        Scale fabric peer0-org1 to 0 (simulates peer outage)
  restore-peer     Scale fabric peer0-org1 back to 1
  latency-inject   Inject 500ms delay on quarkus-api via Istio fault injection
  latency-remove   Remove Istio latency fault injection
  flood            Send 50 concurrent transfer requests to stress the API
  status           Show pod readiness across all shalm namespaces
EOF
  exit 1
}

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
  echo "[simulate] 500ms delay active — check Grafana latency dashboard."
  echo "[simulate] Remove with: $(basename "$0") latency-remove"
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

case "${1:-}" in
  kill-api)         kill_api ;;
  kill-ui)          kill_ui ;;
  kill-peer)        kill_peer ;;
  restore-peer)     restore_peer ;;
  latency-inject)   latency_inject ;;
  latency-remove)   latency_remove ;;
  flood)            flood ;;
  status)           show_status ;;
  *)                usage ;;
esac
