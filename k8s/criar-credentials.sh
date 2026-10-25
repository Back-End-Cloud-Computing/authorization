#!/usr/bin/env bash
# Senhas entram pelo ambiente e por arquivos temporários privados, nunca pelo Git.
set -euo pipefail
umask 077
: "${DB_PASSWORD:?Defina DB_PASSWORD no ambiente antes de executar}"
: "${ADMIN_PASSWORD:?Defina ADMIN_PASSWORD no ambiente antes de executar}"
: "${ORACLE_PASSWORD:?Defina ORACLE_PASSWORD no ambiente antes de executar}"
secret_dir="$(mktemp -d)"
trap 'rm -rf "$secret_dir"' EXIT
printf '%s' "$DB_PASSWORD" > "$secret_dir/db-password"
printf '%s' "$ADMIN_PASSWORD" > "$secret_dir/admin-password"
printf '%s' "$ORACLE_PASSWORD" > "$secret_dir/oracle-password"
kubectl create secret generic authorization-credentials \
  --from-file=db-password="$secret_dir/db-password" \
  --from-file=admin-password="$secret_dir/admin-password" \
  --from-file=oracle-password="$secret_dir/oracle-password" \
  --dry-run=client -o yaml | kubectl apply -f -
