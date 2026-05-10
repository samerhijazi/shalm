#!/usr/bin/env bash
# Push generated crypto material to K8s as Secrets.
# Run AFTER generate.sh. Requires kubectl pointing at the cluster.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS=fabric

echo "==> Creating namespace..."
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

# ── helpers ────────────────────────────────────────────────────────────────────

create_secret_from_dir() {
  local name="$1"
  local dir="$2"
  echo "  Creating secret: $name (from $dir)"
  kubectl create secret generic "$name" \
    --from-file="$dir" \
    -n "$NS" \
    --dry-run=client -o yaml | kubectl apply -f -
}

create_secret_from_file() {
  local name="$1"
  shift
  echo "  Creating secret: $name"
  kubectl create secret generic "$name" \
    "$@" \
    -n "$NS" \
    --dry-run=client -o yaml | kubectl apply -f -
}

# ── orderer ────────────────────────────────────────────────────────────────────

ORDERER_BASE="$SCRIPT_DIR/crypto-material/ordererOrganizations/shalm.local"
create_secret_from_dir "fabric-orderer-msp"  "$ORDERER_BASE/orderers/orderer.shalm.local/msp"
create_secret_from_dir "fabric-orderer-tls"  "$ORDERER_BASE/orderers/orderer.shalm.local/tls"

# ── org1 peer ─────────────────────────────────────────────────────────────────

ORG1_BASE="$SCRIPT_DIR/crypto-material/peerOrganizations/org1.shalm.local"
create_secret_from_dir "fabric-org1-peer-msp" "$ORG1_BASE/peers/peer0.org1.shalm.local/msp"
create_secret_from_dir "fabric-org1-peer-tls" "$ORG1_BASE/peers/peer0.org1.shalm.local/tls"

# ── org2 peer ─────────────────────────────────────────────────────────────────

ORG2_BASE="$SCRIPT_DIR/crypto-material/peerOrganizations/org2.shalm.local"
create_secret_from_dir "fabric-org2-peer-msp" "$ORG2_BASE/peers/peer0.org2.shalm.local/msp"
create_secret_from_dir "fabric-org2-peer-tls" "$ORG2_BASE/peers/peer0.org2.shalm.local/tls"

# ── admin identities (used by setup-job and quarkus-api) ─────────────────────

ORG1_ADMIN="$ORG1_BASE/users/Admin@org1.shalm.local/msp"
create_secret_from_dir "fabric-org1-admin-msp" "$ORG1_ADMIN"
create_secret_from_file "fabric-org1-admin" \
  --from-file=admin-cert.pem="$(ls "$ORG1_ADMIN/signcerts/"*.pem | head -1)" \
  --from-file=admin-key.pem="$(ls "$ORG1_ADMIN/keystore/"*_sk 2>/dev/null || ls "$ORG1_ADMIN/keystore/"*.pem | head -1)"

ORG2_ADMIN="$ORG2_BASE/users/Admin@org2.shalm.local/msp"
create_secret_from_dir "fabric-org2-admin-msp" "$ORG2_ADMIN"

# ── orderer org MSP (for clients to verify orderer) ──────────────────────────

create_secret_from_dir "fabric-orderer-org-msp" "$ORDERER_BASE/msp"

# ── channel artifacts ─────────────────────────────────────────────────────────

echo "  Creating secret: fabric-artifacts"
kubectl create secret generic fabric-artifacts \
  --from-file="$SCRIPT_DIR/channel-artifacts/genesis.block" \
  --from-file="$SCRIPT_DIR/channel-artifacts/mychannel.tx" \
  -n "$NS" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "All secrets created in namespace '$NS'."
echo "Now apply the fabric manifests:"
echo "  kubectl apply -R -f 02_gitops/fabric/"
echo "  (Or let ArgoCD sync if fabric-app.yaml is applied to argocd namespace)"
