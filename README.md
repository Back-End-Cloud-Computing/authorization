# Authorization Service — GANJJ

Microsserviço de autenticação e autorização do e-commerce **GANJJ**. Emite e valida os
tokens JWT usados por todos os outros serviços do sistema.

## Stack

- **Java 21 + Spring Boot** (Spring Security)
- **Oracle Database** (via imagem Docker `gvenzl/oracle-free` em desenvolvimento)

## Escopo

- `POST /auth/register` — cadastro público de usuário
- `POST /auth/login` — autenticação, retorna o token
- `POST /auth/refresh` — renovação de token
- Dono exclusivo dos dados de credencial (login, hash de senha, papel). Dados de perfil
  do cliente (nome, endereço, contato) ficam no serviço [client](https://github.com/Back-End-Cloud-Computing/client) — os dois se
  relacionam pelo mesmo ID de usuário, sem um chamar o outro no caminho de login.

## Endpoints

| Método | Rota | Autenticação | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | pública | Cria conta de cliente |
| `POST` | `/auth/login` | pública | Autentica e devolve os tokens |
| `POST` | `/auth/refresh` | pública | Renova o token de acesso |
| `GET` | `/auth/me` | Bearer | Dados da conta autenticada |
| `GET` | `/auth/public-key` | pública | Chave pública em PEM, para os demais serviços |

Documentação interativa: `http://localhost:8081/swagger-ui.html`

### O que vai dentro do token

Os tokens são assinados em RS256. O conteúdo:

```json
{
  "iss": "ganjj-authorization",
  "sub": "d3f1a2b4-...",         // id da conta — a referência usada pelo client
  "email": "cliente@ganjj.com",
  "role": "CLIENTE",             // ou ADMIN
  "typ": "access",               // "refresh" não abre rota protegida
  "exp": 1770000000
}
```

Os demais serviços leem `sub` e `role` direto do token, sem consultar banco nem
chamar este serviço.

### Integrando os outros serviços

Cada serviço precisa apenas da chave pública — por variável de ambiente, ou buscando
uma vez na inicialização:

```bash
curl -s http://localhost:8081/auth/public-key > public.pem
```

`examples/verify_token.py` mostra a verificação passo a passo. Na prática, use a
biblioteca JWT da sua linguagem — `PyJWT`, `jsonwebtoken`,
`System.IdentityModel.Tokens.Jwt`, `firebase/php-jwt`, `golang-jwt` — apontando para
essa chave e exigindo algoritmo `RS256`, emissor `ganjj-authorization` e `typ` igual
a `access`.

## Rodando com Docker

```bash
docker network create ganjj-net
./scripts/generate-keys.sh
docker compose up --build
```

O serviço sobe em `http://localhost:8081`. O `docker-compose.yml` já espera o Oracle
ficar saudável antes de iniciar a aplicação.

A rede `ganjj-net` é externa de propósito: os outros microsserviços do GANJJ entram
nela e passam a enxergar este serviço pelo nome `authorization`.

### Testando na prática

```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ganjj.com","password":"senhaSegura123"}'
```

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@ganjj.com","password":"senhaSegura123"}'
```

```bash
curl http://localhost:8081/auth/me -H "Authorization: Bearer <accessToken>"
```

## Rodando local, sem Docker

```bash
./mvnw spring-boot:run
```

Sem `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` configurados, o serviço gera um par
RSA temporário e avisa no log. É prático para desenvolvimento, mas os tokens deixam de
valer a cada restart e os outros serviços não conseguem validá-los — em qualquer
ambiente compartilhado, use as chaves geradas pelo script.

## Testes

```bash
./mvnw test
```

Os testes usam H2 em memória no lugar do Oracle. Como toda a configuração de banco vem
de variável de ambiente, nenhuma linha de código muda entre um e outro.

## Configuração

Tudo por variável de ambiente — a mesma imagem roda em desenvolvimento, teste e
produção trocando só os valores.

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8081` | Porta HTTP |
| `DB_HOST` | `localhost` | Host do Oracle |
| `DB_PORT` | `1521` | Porta do Oracle |
| `DB_NAME` | `FREEPDB1` | Nome do serviço Oracle |
| `DB_USER` | `ganjj_auth` | Usuário do banco |
| `DB_PASSWORD` | `ganjj_auth` | Senha do banco |
| `JPA_DDL_AUTO` | `update` | Estratégia do Hibernate |
| `JWT_ISSUER` | `ganjj-authorization` | Emissor gravado no token |
| `JWT_ACCESS_TOKEN_MINUTES` | `15` | Validade do token de acesso |
| `JWT_REFRESH_TOKEN_DAYS` | `7` | Validade do refresh token |
| `JWT_PRIVATE_KEY_PATH` | — | Caminho da chave privada (PEM) |
| `JWT_PUBLIC_KEY_PATH` | — | Caminho da chave pública (PEM) |

As chaves em `keys/` não são versionadas: cada ambiente gera as suas.

## Time

Parte do projeto GANJJ (5 microsserviços poliglotas). Mantido por Eduardo Fabri, que
também mantém o serviço `client`.
