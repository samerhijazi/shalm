#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release.env
source "$SCRIPT_DIR/release.env"

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 OUTPUT_PACKAGE" >&2
  exit 2
fi

output="$1"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/package"

cat > "$work_dir/connection.json" <<EOF
{
  "address": "$CHAINCODE_ADDRESS",
  "dial_timeout": "10s",
  "tls_required": false
}
EOF

cat > "$work_dir/package/metadata.json" <<EOF
{
  "type": "ccaas",
  "label": "$CHAINCODE_LABEL"
}
EOF

# Normalized mtimes make the package ID reproducible on every release runner.
tar --mtime='@0' -czf "$work_dir/package/code.tar.gz" -C "$work_dir" connection.json
tar --mtime='@0' -czf "$output" -C "$work_dir/package" metadata.json code.tar.gz
