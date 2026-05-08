#!/usr/bin/env bash
set -euo pipefail

VMS=(k8s-master k8s-worker-01 k8s-worker-02)
CMD="${1:-}"

usage() {
  echo "Usage: $0 {start|stop|delete|prune|restart}"
  exit 1
}

case "$CMD" in
  start)
    echo "==> Starting VMs..."
    limactl start --tty=false k8s-master.yaml
    limactl start --tty=false k8s-worker-01.yaml
    limactl start --tty=false k8s-worker-02.yaml
    ;;
  stop)
    echo "==> Stopping VMs..."
    for vm in "${VMS[@]}"; do limactl stop -f "$vm"; done
    ;;
  delete)
    echo "==> Stopping VMs..."
    for vm in "${VMS[@]}"; do limactl stop "$vm" 2>/dev/null || true; done
    echo "==> Deleting VMs..."
    for vm in "${VMS[@]}"; do limactl delete "$vm"; done
    ;;
  prune)
    echo "==> Pruning Lima cache..."
    limactl prune
    ;;
  restart)
    echo "==> Stopping VMs..."
    for vm in "${VMS[@]}"; do limactl stop "$vm" 2>/dev/null || true; done
    echo "==> Starting VMs..."
    for vm in "${VMS[@]}"; do limactl start "$vm"; done
    ;;
  *)
    usage
    ;;
esac

limactl list

echo "==> Done"
