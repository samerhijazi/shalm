# Readme

## Deployment order

1. Run `generate.sh` (needs Docker locally)
2. Run `create-secrets.sh` (needs kubectl pointing at cluster)
3. Push to GitHub → CI builds chaincode image → make it public in GHCR
4. ArgoCD syncs fabric-app → all pods start
5. The `fabric-setup` Job runs automatically and initializes the channel + chaincode
6. Rebuild quarkus-api with Phase 4 changes, then set FABRIC_ENABLED=true in the deployment + mount the fabric-org1-admin secret

## Apply

Now apply the fabric manifests:
`kubectl apply -R -f 02_gitops/fabric/`
(Or let ArgoCD sync if fabric-app.yaml is applied to argocd namespace)
