# Repository Guidelines

## Project Structure & Module Organization

This repository is organized by platform layer. `01_infrastructure/` contains Lima, Ansible, and base Kubernetes provisioning. `02_gitops/` holds ArgoCD applications and deployable manifests. Java services live in `03_apps/quarkus-api/` and `03_apps/quarkus-ui/`; each follows the Maven `src/main` and `src/test` layout. Hyperledger Fabric configuration and Java chaincode are under `04_blockchain/fabric/`. Active CI workflows are in `.github/workflows/` (`05_cicd/github-actions/` is reference documentation), and the FastAPI observability agent is in `06_ai/`. Keep pinned versions centralized in `versions.env`.

## Build, Test, and Development Commands

Run Maven commands inside the component being changed:

```bash
cd 03_apps/quarkus-api && mvn test
cd 03_apps/quarkus-ui && mvn package -DskipTests
cd 04_blockchain/fabric/chaincode && mvn test
./04_blockchain/fabric/chaincode/validate-release.sh
./01_infrastructure/scripts/verify-fabric.sh
```

`mvn test` runs the JUnit/Quarkus suite; the scripts validate Fabric release metadata and the live deployment. Use `mvn quarkus:dev` from either Quarkus module for local hot reload. Provision the cluster from `01_infrastructure/ansible/` with `ansible-playbook playbooks/site.yml -i inventory/hosts.yml`. Inspect deployments with `kubectl get pods -A` and ArgoCD state with `kubectl get applications -n argocd`.

## Coding Style & Naming Conventions

Use four-space indentation in Java and Python and two spaces in YAML. Follow existing Java conventions: `PascalCase` classes, `camelCase` methods and fields, and packages beneath `io.shalm`. Python uses `snake_case`, type hints, and grouped standard-library/third-party imports. Name Kubernetes resources and files with lowercase kebab-case. Keep configuration declarative and avoid duplicating version values outside `versions.env`.

## Testing Guidelines

Java tests use JUnit 5, Quarkus Test, and REST Assured. Place them in the matching `src/test/java` package and name classes `*Test`; use behavior-focused method names such as `transfer_insufficientFundsFails`. Run `mvn test` for every affected Java module. No numeric coverage threshold is enforced, but changes to endpoints, account logic, or chaincode should include focused regression tests. CI publishes Quarkus test summaries through `.github/scripts/generate-test-summary.py`.

## Commit & Pull Request Guidelines

Follow the repository’s Conventional Commit pattern: `fix(fabric): ...`, `test: ...`, `docs: ...`, or `ci: ...`. Keep commits scoped to one logical change. Pull requests should explain the behavior changed, identify affected modules/manifests, link relevant issues, and report commands run. Include screenshots for UI changes and rollout evidence for GitOps changes. Commit manifest updates instead of relying on manual `kubectl apply` or `kubectl edit`, because ArgoCD reconciliation will revert live-only edits.
