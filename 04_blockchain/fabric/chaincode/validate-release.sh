#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
config="$REPO_ROOT/02_gitops/fabric/chaincode-config.yaml"
generated_config="$(mktemp)"
trap 'rm -f "$generated_config"' EXIT

"$SCRIPT_DIR/update-release-config.sh" "$generated_config"

if ! diff -u "$config" "$generated_config"; then
  echo "Chaincode release metadata is stale." >&2
  echo "Run: 04_blockchain/fabric/chaincode/update-release-config.sh" >&2
  exit 1
fi

package_id="$(sed -n 's/^  chaincode-id: "\(.*\)"$/\1/p' "$config")"
if ! grep -q "shalm.io/chaincode-id: \"$package_id\"" "$REPO_ROOT/02_gitops/fabric/chaincode/deployment.yaml"; then
  echo "Chaincode deployment annotation shalm.io/chaincode-id does not match $package_id." >&2
  echo "Run: 04_blockchain/fabric/chaincode/update-release-config.sh" >&2
  exit 1
fi

echo "Chaincode release metadata and canonical package ID are valid."
