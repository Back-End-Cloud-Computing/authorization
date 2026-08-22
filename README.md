# Authorization Service — GANJJ

Microsserviço de autenticação e autorização do e-commerce **GANJJ**. Emite e valida os
tokens JWT usados por todos os outros serviços do sistema.

## Stack

- **Java 21 + Spring Boot** (Spring Security)
- **Oracle Database** (via imagem Docker `gvenzl/oracle-free` em desenvolvimento)
- **JWT RS256** — este serviço é o único que guarda a chave privada e assina tokens; os
  demais serviços recebem só a chave pública e validam localmente, sem chamada de rede
  a cada requisição

## Escopo

- `POST /auth/register` — cadastro público de usuário
- `POST /auth/login` — autenticação, retorna o token
- `POST /auth/refresh` — renovação de token
- Dono exclusivo dos dados de credencial (login, hash de senha, papel). Dados de perfil
  do cliente (nome, endereço, contato) ficam no serviço [client](https://github.com/Back-End-Cloud-Computing/client) — os dois se
  relacionam pelo mesmo ID de usuário, sem um chamar o outro no caminho de login.

## Referência de design

A estrutura de segurança (filtro JWT, serviço de token, `UserDetailsService`) usa como
referência o módulo de segurança do [chrono-api](https://github.com/eduardofabrii/chrono-api)
— não é um fork; é um projeto novo, adaptado para RS256 (em vez de HMAC256, já que aqui
múltiplos serviços em linguagens diferentes precisam verificar o token) e Oracle (em vez
de MySQL, para não colidir com o banco do serviço de Promoção).

## Time

Parte do projeto GANJJ (5 microsserviços poliglotas). Mantido por Eduardo Fabri, que
também mantém o serviço `client`.
