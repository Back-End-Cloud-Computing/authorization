#!/usr/bin/env bash
# Gera o par de chaves RSA usado para assinar os tokens JWT.
#
#   private.pem -> fica SO neste servico; e o que assina os tokens
#   public.pem  -> vai para os outros microsservicos; so permite verificar
#
# As chaves nao entram no Git (ver .gitignore). Cada ambiente gera as suas.
set -euo pipefail

DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/keys"
mkdir -p "$DEST"

if [[ -f "$DEST/private.pem" ]]; then
  echo "Ja existe $DEST/private.pem — apague antes de gerar de novo."
  echo "Atencao: trocar a chave invalida todos os tokens em circulacao."
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DEST/private.pem" 2>/dev/null
openssl rsa -in "$DEST/private.pem" -pubout -out "$DEST/public.pem" 2>/dev/null
chmod 600 "$DEST/private.pem"

echo "Chaves geradas em $DEST:"
echo "  private.pem  (fica aqui, nunca compartilhe)"
echo "  public.pem   (distribua para os outros servicos do GANJJ)"
