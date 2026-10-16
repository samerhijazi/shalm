#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
# shellcheck source=release.env
source "$SCRIPT_DIR/release.env"

output="${1:-$REPO_ROOT/02_gitops/fabric/chaincode-config.yaml}"
package_file="$(mktemp "$REPO_ROOT/.fabric-chaincode-package.XXXXXX")"
package_name="$(basename "$package_file")"
trap 'rm -f "$package_file"' EXIT

docker run --rm \
  -v "$REPO_ROOT:/workspace" \
  -w /workspace \
  hyperledger/fabric-tools:2.5 \
  bash 04_blockchain/fabric/chaincode/package-ccaas.sh "/workspace/$package_name"

package_id="$(docker run --rm \
  -v "$REPO_ROOT:/workspace" \
  hyperledger/fabric-tools:2.5 \
  peer lifecycle chaincode calculatepackageid "/workspace/$package_name")"

cat > "$output" <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: fabric-chaincode-config
  namespace: fabric
data:
  chaincode-name: "$CHAINCODE_NAME"
  chaincode-version: "$CHAINCODE_VERSION"
  chaincode-sequence: "$CHAINCODE_SEQUENCE"
  chaincode-label: "$CHAINCODE_LABEL"
  chaincode-address: "$CHAINCODE_ADDRESS"
  chaincode-endorsement-policy: "$CHAINCODE_ENDORSEMENT_POLICY"
  # Full ID of the deterministic CCAAS package built in setup-job.yaml.
  chaincode-id: "$package_id"
EOF

# Stamp the ID into the chaincode pod template so a new release rolls the CCAAS pod.
# Skipped when rendering elsewhere (validate-release.sh renders to a temp file).
deployment="$REPO_ROOT/02_gitops/fabric/chaincode/deployment.yaml"
if [ "$output" = "$REPO_ROOT/02_gitops/fabric/chaincode-config.yaml" ]; then
  sed -i.bak "s|shalm.io/chaincode-id: \".*\"|shalm.io/chaincode-id: \"$package_id\"|" "$deployment"
  rm -f "$deployment.bak"
fi

echo "Rendered chaincode release config: $output"
echo "Package ID: $package_id"
