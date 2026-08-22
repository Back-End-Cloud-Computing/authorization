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

## Time

Parte do projeto GANJJ (5 microsserviços poliglotas). Mantido por Eduardo Fabri, que
também mantém o serviço `client`.
