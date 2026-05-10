#!/usr/bin/env bash
# Run once locally to generate Fabric crypto material and channel artifacts.
# Requires: Docker (uses hyperledger/fabric-tools:2.5 to avoid local tool installs)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "==> Generating crypto material..."
rm -rf crypto-material channel-artifacts
mkdir -p channel-artifacts

docker run --rm \
  -v "$SCRIPT_DIR":/config \
  -w /config \
  hyperledger/fabric-tools:2.5 \
  cryptogen generate --config=crypto-config.yaml --output=crypto-material

echo "==> Generating genesis block..."
docker run --rm \
  -v "$SCRIPT_DIR":/config \
  -w /config \
  -e FABRIC_CFG_PATH=/config \
  hyperledger/fabric-tools:2.5 \
  configtxgen -profile TwoOrgsOrdererGenesis \
    -channelID system-channel \
    -outputBlock ./channel-artifacts/genesis.block

echo "==> Generating channel transaction..."
docker run --rm \
  -v "$SCRIPT_DIR":/config \
  -w /config \
  -e FABRIC_CFG_PATH=/config \
  hyperledger/fabric-tools:2.5 \
  configtxgen -profile TwoOrgsChannel \
    -outputCreateChannelTx ./channel-artifacts/mychannel.tx \
    -channelID mychannel

echo "==> Generating anchor peer updates..."
docker run --rm \
  -v "$SCRIPT_DIR":/config \
  -w /config \
  -e FABRIC_CFG_PATH=/config \
  hyperledger/fabric-tools:2.5 \
  configtxgen -profile TwoOrgsChannel \
    -outputAnchorPeersUpdate ./channel-artifacts/Org1MSPanchors.tx \
    -channelID mychannel -asOrg Org1MSP

docker run --rm \
  -v "$SCRIPT_DIR":/config \
  -w /config \
  -e FABRIC_CFG_PATH=/config \
  hyperledger/fabric-tools:2.5 \
  configtxgen -profile TwoOrgsChannel \
    -outputAnchorPeersUpdate ./channel-artifacts/Org2MSPanchors.tx \
    -channelID mychannel -asOrg Org2MSP

echo ""
echo "Done. Run create-secrets.sh next to push material to the cluster."
