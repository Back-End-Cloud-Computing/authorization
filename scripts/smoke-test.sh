#!/usr/bin/env bash
# Confere se o GANJJ esta funcionando de ponta a ponta.
#
# Sobe os dois servicos antes de rodar:
#   authorization:  docker compose up -d
#   client:         docker compose up -d
#
# Uso:
#   ./scripts/smoke-test.sh
#   AUTH=http://localhost:8081 CLIENT=http://localhost:8082 ./scripts/smoke-test.sh
#
# Nao precisa limpar nada antes: cada execucao usa emails novos.

set -uo pipefail

AUTH="${AUTH:-http://localhost:8081}"
CLIENT="${CLIENT:-http://localhost:8082}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ganjj.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-adminSegura123}"

# Emails unicos por execucao, pra poder rodar quantas vezes quiser.
MARCA="$(date +%s)$RANDOM"
EMAIL="teste$MARCA@ganjj.com"
SENHA='senhaSegura123'

# JSON sempre em variavel, nunca escrito direto na chamada: dentro de
# "$( ... )" o bash trataria {"a":1,"b":2} como expansao de chaves e quebraria
# o corpo em duas partes.
CREDENCIAL="{\"email\":\"$EMAIL\",\"password\":\"$SENHA\"}"
CREDENCIAL_ERRADA="{\"email\":\"$EMAIL\",\"password\":\"errada\"}"
EMAIL_INVALIDO='{"email":"nao-e-email","password":"senhaSegura123"}'
SENHA_CURTA="{\"email\":\"curta$MARCA@ganjj.com\",\"password\":\"123\"}"
ADMIN_CREDENCIAL="{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
PERFIL='{"nome":"Teste Fumaca","telefone":"(11) 98765-4321","endereco":{"logradouro":"Rua A","numero":"1","bairro":"Centro","cidade":"Sao Paulo","estado":"SP","cep":"01310-100"}}'
PERFIL_INVALIDO='{"nome":"x","telefone":"y","endereco":{"logradouro":"","numero":"","cidade":"","estado":"z","cep":"1"}}'

verde=$'\033[32m'; vermelho=$'\033[31m'; cinza=$'\033[90m'; normal=$'\033[0m'
passou=0; falhou=0

# checa <descricao> <esperado> <obtido>
checa() {
  if [[ "$2" == "$3" ]]; then
    printf '  %s✓%s %s\n' "$verde" "$normal" "$1"
    passou=$((passou + 1))
  else
    printf '  %s✗%s %s %s(esperado %s, veio %s)%s\n' \
      "$vermelho" "$normal" "$1" "$cinza" "$2" "$3" "$normal"
    falhou=$((falhou + 1))
  fi
}

# status <metodo> <url> [corpo] [token]
status() {
  local metodo="$1" url="$2" corpo="${3:-}" token="${4:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "$metodo" "$url")
  [[ -n "$corpo" ]] && args+=(-H 'Content-Type: application/json' -d "$corpo")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  curl "${args[@]}"
}

# corpo <metodo> <url> [corpo] [token]
corpo() {
  local metodo="$1" url="$2" dados="${3:-}" token="${4:-}"
  local args=(-s -X "$metodo" "$url")
  [[ -n "$dados" ]] && args+=(-H 'Content-Type: application/json' -d "$dados")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  curl "${args[@]}"
}

campo() { python3 -c "import json,sys; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

echo
echo "GANJJ - teste de fumaca"
echo "auth:   $AUTH"
echo "client: $CLIENT"
echo

# ---------------------------------------------------------------- servicos no ar
echo "Servicos no ar"
checa "auth responde"   "200" "$(status GET "$AUTH/auth/public-key")"
checa "client responde" "200" "$(status GET "$CLIENT/health")"

if [[ "$(status GET "$AUTH/auth/public-key")" != "200" ]]; then
  echo
  echo "O auth nao respondeu. Suba os servicos antes de rodar este teste."
  exit 1
fi

# ---------------------------------------------------------------- cadastro e login
echo
echo "Cadastro e login"
checa "cria conta" "201" "$(status POST "$AUTH/auth/register" "$CREDENCIAL")"
checa "email repetido e recusado" "409" "$(status POST "$AUTH/auth/register" "$CREDENCIAL")"
checa "email invalido e recusado" "400" "$(status POST "$AUTH/auth/register" "$EMAIL_INVALIDO")"
checa "senha curta e recusada"    "400" "$(status POST "$AUTH/auth/register" "$SENHA_CURTA")"

LOGIN=$(corpo POST "$AUTH/auth/login" "$CREDENCIAL")
TOKEN=$(echo "$LOGIN" | campo accessToken)
REFRESH=$(echo "$LOGIN" | campo refreshToken)
VALIDADE=$(echo "$LOGIN" | campo expiresIn)

checa "login devolve token"      "sim" "$([[ -n "$TOKEN" ]] && echo sim || echo nao)"
checa "token dura 2 horas"       "7200" "$VALIDADE"
checa "senha errada e recusada"  "401" "$(status POST "$AUTH/auth/login" "$CREDENCIAL_ERRADA")"

# ---------------------------------------------------------------- rota autenticada
echo
echo "Rota autenticada"
checa "com token"              "200" "$(status GET "$AUTH/auth/me" "" "$TOKEN")"
checa "sem token"              "401" "$(status GET "$AUTH/auth/me")"
checa "token adulterado"       "401" "$(status GET "$AUTH/auth/me" "" "${TOKEN%????}AAAA")"
checa "refresh no lugar do access" "401" "$(status GET "$AUTH/auth/me" "" "$REFRESH")"

# ---------------------------------------------------------------- client
echo
echo "Perfil no client (token vindo do auth)"
checa "cria perfil"            "201" "$(status POST "$CLIENT/clients" "$PERFIL" "$TOKEN")"
checa "perfil duplicado"       "409" "$(status POST "$CLIENT/clients" "$PERFIL" "$TOKEN")"
checa "le o proprio perfil"    "200" "$(status GET "$CLIENT/clients/me" "" "$TOKEN")"
checa "atualiza o perfil"      "200" "$(status PUT "$CLIENT/clients/me" "$PERFIL" "$TOKEN")"
checa "sem token"              "401" "$(status GET "$CLIENT/clients/me")"
checa "dados invalidos"        "400" "$(status POST "$CLIENT/clients" "$PERFIL_INVALIDO" "$TOKEN")"

# o id no client tem que ser o mesmo id da conta no auth
CONTA=$(corpo GET "$AUTH/auth/me" "" "$TOKEN" | campo id)
PERFIL_CONTA=$(corpo GET "$CLIENT/clients/me" "" "$TOKEN" | campo accountId)
checa "id da conta bate nos dois servicos" "$CONTA" "$PERFIL_CONTA"

# ---------------------------------------------------------------- papeis
echo
echo "Permissao por papel"
checa "cliente na rota de admin do auth"   "403" "$(status GET "$AUTH/auth/accounts" "" "$TOKEN")"
checa "cliente na rota de admin do client" "403" "$(status GET "$CLIENT/clients" "" "$TOKEN")"

ADMIN=$(corpo POST "$AUTH/auth/login" "$ADMIN_CREDENCIAL" | campo accessToken)
if [[ -n "$ADMIN" ]]; then
  checa "admin na rota de admin do auth"   "200" "$(status GET "$AUTH/auth/accounts" "" "$ADMIN")"
  checa "admin na rota de admin do client" "200" "$(status GET "$CLIENT/clients" "" "$ADMIN")"
else
  echo "  ${cinza}admin nao configurado (ADMIN_EMAIL/ADMIN_PASSWORD), pulando${normal}"
fi

# ---------------------------------------------------------------- refresh e logout
echo
echo "Renovacao e logout"
CORPO_REFRESH="{\"refreshToken\":\"$REFRESH\"}"
NOVO=$(corpo POST "$AUTH/auth/refresh" "$CORPO_REFRESH")
checa "renova o token"            "sim" "$([[ -n "$(echo "$NOVO" | campo accessToken)" ]] && echo sim || echo nao)"
checa "refresh usado nao repete"  "401" "$(status POST "$AUTH/auth/refresh" "$CORPO_REFRESH")"

REFRESH2=$(echo "$NOVO" | campo refreshToken)
CORPO_REFRESH2="{\"refreshToken\":\"$REFRESH2\"}"
checa "logout"                    "204" "$(status POST "$AUTH/auth/logout" "$CORPO_REFRESH2")"
checa "logout repetido"           "204" "$(status POST "$AUTH/auth/logout" "$CORPO_REFRESH2")"
checa "refresh apos logout"       "401" "$(status POST "$AUTH/auth/refresh" "$CORPO_REFRESH2")"

# ---------------------------------------------------------------- documentacao
echo
echo "Documentacao"
checa "swagger do auth"   "200" "$(curl -sL -o /dev/null -w '%{http_code}' "$AUTH/swagger-ui.html")"
checa "swagger do client" "200" "$(status GET "$CLIENT/swagger/index.html")"

# ---------------------------------------------------------------- resultado
echo
if [[ $falhou -eq 0 ]]; then
  printf '%s%d verificacoes, todas passaram.%s\n\n' "$verde" "$passou" "$normal"
  exit 0
else
  printf '%s%d de %d falharam.%s\n\n' "$vermelho" "$falhou" "$((passou + falhou))" "$normal"
  exit 1
fi
