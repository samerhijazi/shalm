# Notes

┌──────────────┬────────────────────────────┬──────────┬─────────────┐
│ Service │ URL. │ Username │ Password │
├──────────────┼────────────────────────────┼──────────┼─────────────┤
│ Grafana │ http://192.168.105.3:30300 │ admin │ shalm-admin │
├──────────────┼────────────────────────────┼──────────┼─────────────┤
│ Prometheus │ http://192.168.105.3:30090 │ — │ — │
├──────────────┼────────────────────────────┼──────────┼─────────────┤
│ AlertManager │ http://192.168.105.3:30093 │ — │ — │
└──────────────┴────────────────────────────┴──────────┴─────────────┘

"msg": "ArgoCD UI: http://192.168.105.3:30080 Username: admin Password: GhPtA0-v7iFqnPkX"
"msg": "GitHub repo 'https://github.com/samerhijazi/shalm' registered in ArgoCD as secret 'repo-shalm'"

- Grafana: NodePort 30300
- Prometheus: NodePort 30090, emptyDir (no PVC needed), scrapes all namespaces
- AlertManager: NodePort 30093

To activate: push to GitHub, then apply the root-app once:
kubectl apply -f 02_gitops/root-app/root-app.yaml
ArgoCD will pick up all 4 child apps automatically.

▎ Note on Helm chart versions (65.1.1 / 6.6.2 / 6.16.6): these were current as of my training data. If ArgoCD reports a version not found, check helm search repo for the latest and update the ▎ targetRevision in the Application manifests.
