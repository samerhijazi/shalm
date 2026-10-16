# Readme

## Deployment order

1. Run `generate.sh` (needs Docker locally)
2. Run `create-secrets.sh` (needs kubectl pointing at cluster)
3. Push to GitHub → CI builds chaincode image → make it public in GHCR
4. ArgoCD syncs fabric-app → all pods start
5. The `fabric-setup` Job runs automatically and initializes the channel + chaincode
6. Rebuild `quarkus-api`; it connects to both peers with the Org1 and Org2
   identities created by `create-secrets.sh`.

## Apply

Now apply the fabric manifests:
`kubectl apply -R -f 02_gitops/fabric/`
(Or let ArgoCD sync if fabric-app.yaml is applied to argocd namespace)

## Bidirectional ownership demo

The upgraded `bank-transfer` chaincode seeds two unique assets:

- `ASSET-001`, initially owned by `Org1MSP`
- `ASSET-002`, initially owned by `Org2MSP`

The current owner organization must sign `TransferAsset`. The chaincode checks the
invoker MSP, both organizations endorse the state change, and the API selects the
gateway identity that matches the owner recorded on the ledger.

```bash
# Inspect Fabric-owned assets
curl http://quarkus-api.quarkus-api.svc.cluster.local:8080/fabric/assets

# Org1MSP -> Org2MSP (the API automatically signs with the current Org1 owner)
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"newOwner":"Bank2 Trade Desk","newOwnerMsp":"Org2MSP"}' \
  http://quarkus-api.quarkus-api.svc.cluster.local:8080/fabric/assets/ASSET-001/transfer

# Org2MSP -> Org1MSP (now automatically signed with Org2)
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"newOwner":"Bank1 Trade Desk","newOwnerMsp":"Org1MSP"}' \
  http://quarkus-api.quarkus-api.svc.cluster.local:8080/fabric/assets/ASSET-001/transfer
```

The same flow is available in the `Assets` tab of `quarkus-ui`.
