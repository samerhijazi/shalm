#!/usr/bin/env bash
# Push generated crypto material to K8s as Secrets.
# Run AFTER generate.sh. Requires kubectl pointing at the cluster.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS=fabric

echo "==> Creating namespace..."
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

# ── helpers ────────────────────────────────────────────────────────────────────

apply_secret() {
  local name="$1"
  shift
  echo "  Creating secret: $name"
  kubectl create secret generic "$name" \
    "$@" \
    -n "$NS" \
    --dry-run=client -o yaml | kubectl apply -f -
}

# Creates one secret per MSP subdirectory (cacerts, signcerts, keystore, tlscacerts).
# Admincerts files may contain @ which is invalid in K8s key names — renamed to admin-cert.pem.
create_msp_subdir_secrets() {
  local prefix="$1"   # e.g. fabric-orderer-msp  or  fabric-org1-peer-msp
  local msp_dir="$2"  # path to the msp/ directory

  apply_secret "${prefix}-cacerts"    --from-file=ca.pem="$(ls "$msp_dir/cacerts/"*.pem | head -1)"
  apply_secret "${prefix}-signcerts"  --from-file=cert.pem="$(ls "$msp_dir/signcerts/"*.pem | head -1)"
  apply_secret "${prefix}-keystore"   --from-file=priv_sk="$msp_dir/keystore/priv_sk"
  apply_secret "${prefix}-tlscacerts" --from-file=tlsca.pem="$(ls "$msp_dir/tlscacerts/"*.pem | head -1)"

  # admincerts is optional in Fabric 2.x (NodeOUs via config.yaml), but include if present
  if [ -d "$msp_dir/admincerts" ]; then
    local admin_cert
    admin_cert=$(ls "$msp_dir/admincerts/"*.pem 2>/dev/null | head -1 || true)
    if [ -n "$admin_cert" ]; then
      apply_secret "${prefix}-admincerts" --from-file=admin-cert.pem="$admin_cert"
    fi
  fi
}

# ── orderer ────────────────────────────────────────────────────────────────────

ORDERER_BASE="$SCRIPT_DIR/crypto-material/ordererOrganizations/shalm.local"
ORDERER_MSP="$ORDERER_BASE/orderers/orderer.shalm.local/msp"

create_msp_subdir_secrets "fabric-orderer-msp" "$ORDERER_MSP"
apply_secret "fabric-orderer-tls" \
  --from-file=ca.crt="$ORDERER_BASE/orderers/orderer.shalm.local/tls/ca.crt" \
  --from-file=server.crt="$ORDERER_BASE/orderers/orderer.shalm.local/tls/server.crt" \
  --from-file=server.key="$ORDERER_BASE/orderers/orderer.shalm.local/tls/server.key"

# ── org1 peer ─────────────────────────────────────────────────────────────────

ORG1_BASE="$SCRIPT_DIR/crypto-material/peerOrganizations/org1.shalm.local"
ORG1_PEER_MSP="$ORG1_BASE/peers/peer0.org1.shalm.local/msp"

apply_secret "fabric-org1-peer-msp" --from-file=config.yaml="$ORG1_PEER_MSP/config.yaml"
create_msp_subdir_secrets "fabric-org1-peer-msp" "$ORG1_PEER_MSP"
apply_secret "fabric-org1-peer-tls" \
  --from-file=ca.crt="$ORG1_BASE/peers/peer0.org1.shalm.local/tls/ca.crt" \
  --from-file=server.crt="$ORG1_BASE/peers/peer0.org1.shalm.local/tls/server.crt" \
  --from-file=server.key="$ORG1_BASE/peers/peer0.org1.shalm.local/tls/server.key"

# ── org2 peer ─────────────────────────────────────────────────────────────────

ORG2_BASE="$SCRIPT_DIR/crypto-material/peerOrganizations/org2.shalm.local"
ORG2_PEER_MSP="$ORG2_BASE/peers/peer0.org2.shalm.local/msp"

apply_secret "fabric-org2-peer-msp" --from-file=config.yaml="$ORG2_PEER_MSP/config.yaml"
create_msp_subdir_secrets "fabric-org2-peer-msp" "$ORG2_PEER_MSP"
apply_secret "fabric-org2-peer-tls" \
  --from-file=ca.crt="$ORG2_BASE/peers/peer0.org2.shalm.local/tls/ca.crt" \
  --from-file=server.crt="$ORG2_BASE/peers/peer0.org2.shalm.local/tls/server.crt" \
  --from-file=server.key="$ORG2_BASE/peers/peer0.org2.shalm.local/tls/server.key"

# ── admin identities (used by setup-job and quarkus-api) ─────────────────────

ORG1_ADMIN="$ORG1_BASE/users/Admin@org1.shalm.local/msp"
apply_secret "fabric-org1-admin-msp" --from-file=config.yaml="$ORG1_ADMIN/config.yaml"
create_msp_subdir_secrets "fabric-org1-admin-msp" "$ORG1_ADMIN"
apply_secret "fabric-org1-admin" \
  --from-file=admin-cert.pem="$(ls "$ORG1_ADMIN/signcerts/"*.pem | head -1)" \
  --from-file=admin-key.pem="$ORG1_ADMIN/keystore/priv_sk"

ORG2_ADMIN="$ORG2_BASE/users/Admin@org2.shalm.local/msp"
apply_secret "fabric-org2-admin-msp" --from-file=config.yaml="$ORG2_ADMIN/config.yaml"
create_msp_subdir_secrets "fabric-org2-admin-msp" "$ORG2_ADMIN"

# ── orderer org MSP (for clients to verify orderer) ──────────────────────────

ORDERER_ORG_MSP="$ORDERER_BASE/msp"
apply_secret "fabric-orderer-org-msp" \
  --from-file=admin-cert.pem="$(ls "$ORDERER_ORG_MSP/admincerts/"*.pem | head -1)" \
  --from-file="$(ls "$ORDERER_ORG_MSP/cacerts/"*.pem | head -1)" \
  --from-file="$(ls "$ORDERER_ORG_MSP/tlscacerts/"*.pem | head -1)"

# ── channel artifacts ─────────────────────────────────────────────────────────

apply_secret "fabric-artifacts" \
  --from-file="$SCRIPT_DIR/channel-artifacts/genesis.block" \
  --from-file="$SCRIPT_DIR/channel-artifacts/mychannel.tx"

echo ""
echo "All secrets created in namespace '$NS'."
echo "Now apply the fabric manifests:"
echo "  kubectl apply -R -f 02_gitops/fabric/"
echo "  (Or let ArgoCD sync if fabric-app.yaml is applied to argocd namespace)"
