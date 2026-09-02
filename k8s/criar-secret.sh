#!/usr/bin/env bash
# Cria o Secret com o par de chaves RSA que o Authorization usa para assinar
# os tokens.
#
# O Secret nao e um arquivo YAML versionado de proposito: a chave privada nao
# entra no Git. Ele e criado a partir dos arquivos gerados por
# scripts/generate-keys.sh.
#
# As tres replicas montam o MESMO Secret. Sem isso, cada Pod geraria o proprio
# par e um token emitido por um seria recusado pelos outros.
#
# Uso: ./k8s/criar-secret.sh
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHAVES="$RAIZ/keys"

if [[ ! -f "$CHAVES/private.pem" ]]; then
  echo "Chaves nao encontradas em $CHAVES."
  echo "Rode antes: ./scripts/generate-keys.sh"
  exit 1
fi

# --dry-run + apply torna o comando repetivel: roda de novo sem dar erro de
# "ja existe" e atualiza o conteudo se as chaves mudarem.
kubectl create secret generic jwt-keys \
  --from-file=private.pem="$CHAVES/private.pem" \
  --from-file=public.pem="$CHAVES/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

echo
echo "Secret 'jwt-keys' pronto. Confira com:"
echo "  kubectl describe secret jwt-keys"
