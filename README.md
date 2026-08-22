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
| `POST` | `/auth/logout` | pública | Invalida o refresh token |
| `GET` | `/auth/me` | Bearer | Dados da conta autenticada |
| `GET` | `/auth/accounts` | Bearer + ADMIN | Lista as contas cadastradas |
| `GET` | `/auth/public-key` | pública | Chave pública em PEM, para os demais serviços |

`/auth/refresh` e `/auth/logout` se autenticam pelo próprio refresh token, enviado no
corpo — por isso não pedem header `Authorization`.

Documentação interativa: `http://localhost:8081/swagger-ui.html`

### Logout

Cada refresh token carrega um identificador (`jti`). O logout grava esse
identificador numa lista de revogados, e o refresh passa a recusá-lo. O mesmo vale ao
renovar: o token usado é queimado e um novo é emitido no lugar, então um refresh token
vazado deixa de funcionar assim que o dono legítimo renovar.

O **token de acesso continua válido até expirar** (por padrão, 2 horas). Isso é a
contrapartida de os outros serviços validarem sem consultar ninguém — o cliente deve
descartar o token de acesso ao sair.

Uma tarefa diária remove da lista os tokens que já expiraram sozinhos.

### Conta ADMIN

O cadastro público sempre cria contas `CLIENTE`. Para ter a primeira conta `ADMIN`,
preencha `ADMIN_EMAIL` e `ADMIN_PASSWORD` — ela é criada na inicialização, uma única
vez. `GET /auth/accounts` existe para conferir que a autorização por papel está
valendo: com token de `CLIENTE` responde `403`.

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

## Integrando os outros serviços

Quem cuida do login é este serviço, que devolve um token pro usuário. Os outros
serviços não precisam chamar ele pra nada: cada um confere o token sozinho.

Pra isso você só precisa de um arquivo, a chave pública. Baixe uma vez e deixe no seu
projeto. Pode commitar, ela é pública mesmo.

```bash
curl -s http://localhost:8081/auth/public-key > public.pem
```

O token chega no cabeçalho `Authorization`. Use a biblioteca de JWT da sua linguagem,
tem pronta pra todas: `PyJWT` no Python, `jsonwebtoken` no Node,
`System.IdentityModel.Tokens.Jwt` no C#, `firebase/php-jwt` no PHP, `golang-jwt` no Go.

Na configuração dela, marque o algoritmo como **RS256**. Esse é o ponto mais
importante: se esquecer, tudo continua funcionando normal, mas fica possível alguém
forjar um token e entrar como quiser. Vale conferir também o emissor
(`ganjj-authorization`) e que o campo `typ` seja `access`, pra um refresh token não
passar como token de acesso.

Token válido traz o **id do usuário** no campo `sub` e a **role** (`CLIENTE` ou
`ADMIN`). Use o id pra saber de quem são os dados e a role pra liberar rota de admin.
E nunca pegue o id do corpo da requisição, só de dentro do token, senão qualquer um
edita os dados de qualquer um.

O token vale 2 horas. Depois disso é só responder `401`. Quem renova é o front,
chamando `POST /auth/refresh`.

Uma pegadinha do Docker: lá dentro do container, `localhost` é o próprio container e
não este serviço. O endereço é `authorization` na porta 8081, e o seu serviço precisa
estar na rede `ganjj-net`.

Pra testar, entre no Swagger em `http://localhost:8081/swagger-ui.html`, crie uma conta
e faça login. Ele já devolve o token pra você usar.

O arquivo `examples/verify_token.py` mostra a verificação passo a passo, se quiser ver
o que acontece por baixo.

## Rodando com Docker

```bash
docker network create ganjj-net
./scripts/generate-keys.sh
docker compose up --build
```

No Windows (PowerShell), troque o script do meio:

```powershell
.\scripts\generate-keys.ps1
```

Ele usa o `openssl` se você tiver (o Git para Windows traz um) e, se não tiver, gera as
chaves pelo Docker — que este projeto já exige de qualquer forma.

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
| `JWT_ACCESS_TOKEN_MINUTES` | `120` | Validade do token de acesso (em minutos) |
| `JWT_REFRESH_TOKEN_DAYS` | `7` | Validade do refresh token |
| `JWT_PRIVATE_KEY_PATH` | — | Caminho da chave privada (PEM) |
| `JWT_PUBLIC_KEY_PATH` | — | Caminho da chave pública (PEM) |
| `JWT_REVOKED_CLEANUP_CRON` | `0 0 3 * * *` | Faxina da lista de tokens revogados |
| `CORS_ALLOWED_ORIGINS` | portas de dev | Origens do frontend, separadas por vírgula |
| `ADMIN_EMAIL` | — | E-mail da conta ADMIN inicial |
| `ADMIN_PASSWORD` | — | Senha da conta ADMIN inicial |

As chaves em `keys/` não são versionadas: cada ambiente gera as suas.

## Time

Parte do projeto GANJJ (5 microsserviços poliglotas). Mantido por Eduardo Fabri, que
também mantém o serviço `client`.
