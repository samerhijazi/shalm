#!/usr/bin/env bash
set -euo pipefail

API_URL="${SHALM_API_URL:-http://192.168.105.3:30800}"
UI_URL="${SHALM_UI_URL:-http://192.168.105.3:30801}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

echo "Checking Kubernetes rollouts..."
kubectl rollout status deployment/fabric-chaincode -n fabric --timeout=120s
kubectl rollout status deployment/quarkus-api -n quarkus-api --timeout=120s
kubectl rollout status deployment/quarkus-ui -n quarkus-ui --timeout=120s

echo "Checking Fabric readiness through the API..."
curl --fail --silent --show-error --max-time 15 \
  "$API_URL/fabric/health" -o "$work_dir/health.json"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' "$work_dir/health.json"

echo "Checking all ledger balance queries..."
for account in ACC-B1-001 ACC-B1-002 ACC-B2-001 ACC-B2-002; do
  curl --fail --silent --show-error --max-time 15 \
    "$API_URL/fabric/balance/$account" -o "$work_dir/$account.json"
  grep -Eq '"balance"[[:space:]]*:[[:space:]]*[0-9]+' "$work_dir/$account.json"
  echo "  $account: passed"
done

echo "Checking the rendered Ledger > Blockchain tab..."
curl --fail --silent --show-error --max-time 20 \
  "$UI_URL/" -o "$work_dir/dashboard.html"
if grep -q 'Blockchain data is unavailable' "$work_dir/dashboard.html"; then
  echo "Ledger > Blockchain tab reports that Fabric block data is unavailable" >&2
  exit 1
fi

echo "Fabric deployment verification passed."
