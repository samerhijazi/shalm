# Shalm Demo Platform — Implementation Specification

Produce a **clean system spec for implementation**.
**AI-agent executable specification** (what to build, structure, responsibilities, constraints).

## 1. Objective

Build a **3-node Kubernetes cluster (1 control-plane + 2 workers) demo platform** that demonstrates:

- Observability-first SRE practices
- GitOps-driven platform management
- Multi-runtime blockchain systems (Fabric + Besu)
- Identity-aware system (OIDC-style)
- UI-driven interaction layer
- AI-assisted observability analysis

---

## 2. Repository Structure (Mono-Repo)

```
shalm-platform/
│
├── infra/
│   ├── ansible/                  # cluster provisioning (already exists)
│   └── base/                     # base k8s configs (calico, metrics-server)
│
├── gitops/
│   ├── root-app/                 # ArgoCD app-of-apps
│   ├── observability/
│   ├── quarkus-app/
│   ├── fabric/
│   ├── besu/
│   ├── identity/
│   └── istio/
│
├── apps/
│   ├── quarkus-api/              # backend service (transactions)
│   ├── quarkus-ui/               # UI (management dashboard)
│   └── identity-service/         # OIDC-style mock
│
├── blockchain/
│   ├── fabric/
│   │   ├── network-config/
│   │   └── chaincode/
│   └── besu/
│       └── network-config/
│
├── ci/
│   └── github-actions/           # pipelines
│
└── ai/
    └── observability-agent/      # log analysis service
```

---

## 3. Platform Core

### Kubernetes

- 1 master, 2 workers (Lima)
- containerd runtime
- Calico networking

---

### GitOps (ArgoCD)

- App-of-Apps pattern
- root app deploys:
  - observability
  - apps
  - blockchain
  - identity
  - istio

---

### Container Registry

- GitHub Container Registry (GHCR)
- all images pushed via GitHub Actions

---

## 4. Observability Layer (Phase 1)

### Stack

- Prometheus
- Grafana
- Loki

### Requirements

#### Metrics

- cluster metrics
- workload metrics (all namespaces)

#### Logs

- centralized via Loki
- namespace + workload filtering

#### Dashboards

- cluster health
- workload performance
- blockchain-specific dashboards (later phases)

---

## 5. Application Layer (Phase 2)

## Quarkus API (Stateful)

### Responsibilities

- manage client accounts
- process transfers (A → B)
- expose REST API

### State Model

- account balance per client
- in-memory (initial), optional persistence later

---

## Quarkus UI (NEW requirement)

### Responsibilities

- trigger transactions
- display:
  - account balances
  - transaction history
  - Fabric ledger data (via backend)

- visualize system status (basic)

### Constraint

- UI communicates only with backend API (not directly with Fabric/Besu)

---

## Observability Requirements (App Layer)

### Metrics

- request count
- latency
- error rate

### Logs

- structured JSON
- include:
  - transaction_id
  - user_id
  - status

---

## 6. Hyperledger Fabric Layer (Phase 3)

### Deployment

- Kubernetes-native (no operator)
- exposed via MetalLB
- integrated with Istio (see section below)

---

### Network Topology

- OrgA (BankA)
- OrgB (BankB)
- 1 peer per org
- 1 ordering service

---

### Chaincode

- asset transfer:
  - accounts mapped to users
  - transfer operation
  - query balances

---

### Integration

#### Backend (Quarkus API)

- invokes Fabric transactions
- retrieves ledger state

#### UI

- displays ledger data via backend

---

### Observability

- peer logs (Loki)
- orderer logs
- transaction flow visibility

---

## 7. Istio Integration (Fabric requirement)

### Purpose

- service mesh for:
  - traffic control
  - observability
  - mTLS simulation

---

### Scope

- applied primarily to Fabric namespace

### Requirements

- ingress gateway for Fabric endpoints
- traffic routing between:
  - peers
  - orderer

- basic telemetry via Istio

---

## 8. Hyperledger Besu Layer (Phase 4)

### Deployment

- Kubernetes-native
- separate namespace

---

### Network

- 3-node PoA (IBFT/QBFT)
- minimal config

---

### Smart Contract

- simple token transfer
- balance query

---

### Integration

- backend optionally interacts with Besu
- used for comparison (not primary UI focus)

---

### Observability

- block production metrics
- RPC logs
- node health

---

## 9. Identity Layer (Phase 5)

### Service

- custom Quarkus-based OIDC-like provider

---

### Features

- user authentication
- token issuance (JWT)
- role/attribute claims:
  - bank
  - user_id

---

### Integration

#### Quarkus API

- validates JWT
- maps identity → account

#### Fabric

- identity mapped to org/MSP context

#### Besu

- transaction authorization (off-chain check)

---

### Observability

- login events
- token issuance logs
- auth failures

---

## 10. CI/CD Layer (Phase 6)

### Tooling

- GitHub Actions

---

### Pipelines

#### For each app:

- build container
- push to GHCR
- update GitOps manifests (image tag)

---

### ArgoCD

- detects changes
- deploys automatically

---

### Requirements

- versioned deployments
- rollback via Git revert

---

## 11. SRE Layer (Phase 7)

### SLO Definitions

#### Quarkus

- latency < 200ms
- error rate < 1%

#### Fabric

- tx success rate > 98%

#### Besu

- block production stability

---

### Alerting

- Prometheus alerts:
  - high latency
  - node down
  - tx failures

---

### Failure Scenarios

- pod kill
- peer shutdown
- orderer disruption
- latency injection (app level)

---

### Observability Flow

- detect via metrics
- investigate via logs
- validate recovery

---

## 12. AI Layer (Phase 8)

### Service

- standalone analysis service

---

### Input

- Loki logs
- Prometheus metrics snapshots

---

### Functions

- log summarization
- anomaly detection (basic)
- incident correlation

---

### Output

- summaries via API
- optional Grafana panel integration

---

## 13. System Constraints

- Single environment (dev)
- Minimal viable configurations for Fabric and Besu
- No production-grade scaling required
- Observability must work across all components
- GitOps is mandatory for all deployments

---

## 14. Key Architectural Rules

- All components run on Kubernetes
- All deployments managed via ArgoCD
- All services emit logs + metrics
- UI never directly accesses blockchain
- Identity layer is mandatory for all requests
- Fabric + Besu remain separate systems (no interoperability required)

---

## 15. Expected End State

A running platform where:

- user logs in via identity service
- triggers transaction via UI
- backend processes:
  - local state (Quarkus)
  - Fabric ledger update

- observability stack shows:
  - metrics
  - logs
  - system behavior

- CI/CD updates system via GitOps
- failures can be simulated and observed
- AI service summarizes incidents
