#!/usr/bin/env bash
# Create K8s secrets for Besu node private keys.
# These are well-known test keys — do NOT use in production.
set -euo pipefail
NS=besu

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

apply_key_secret() {
  local name="$1"
  local key="$2"
  echo "  Creating secret: $name"
  kubectl create secret generic "$name" \
    --from-literal=key="$key" \
    -n "$NS" \
    --dry-run=client -o yaml | kubectl apply -f -
}

apply_key_secret besu-node-0-key 8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63
apply_key_secret besu-node-1-key c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3
apply_key_secret besu-node-2-key ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f

echo "Besu secrets created in namespace '$NS'."
