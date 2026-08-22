# Como funciona a autenticação no GANJJ

Documento pra tirar dúvida de quem vai integrar. Comece pelo básico e desça até a
parte que te interessa.

---

## Índice

1. [O básico](#1-o-básico)
2. [Login, token e o que fazer com ele](#2-login-token-e-o-que-fazer-com-ele)
3. [Cookie ou não? Onde guardar o token](#3-cookie-ou-não-onde-guardar-o-token)
4. [Validando o token no seu serviço](#4-validando-o-token-no-seu-serviço)
5. [Auth e client: quem guarda o quê](#5-auth-e-client-quem-guarda-o-quê)
6. [Segurança](#6-segurança)
7. [Deu erro, e agora](#7-deu-erro-e-agora)
8. [Perguntas soltas](#8-perguntas-soltas)

---

## 1. O básico

### O que são esses dois serviços?

São dois microsserviços separados, cada um com seu banco:

| | authorization | client |
|---|---|---|
| Cuida de | login e senha | perfil do cliente |
| Guarda | email, senha, role | nome, telefone, endereço |
| Banco | Oracle | PostgreSQL |
| Porta | 8081 | 8082 |

### Por que separar? Não daria pra ser um só?

Daria, mas separado é melhor por dois motivos.

O primeiro é que são responsabilidades diferentes. "Quem é você" (credencial) e "quais
seus dados" (perfil) mudam por razões diferentes e são usados por partes diferentes do
sistema. Todo mundo precisa saber quem é o usuário; só algumas telas precisam do
endereço dele.

O segundo é que credencial é dado sensível. Isolar o serviço que guarda senha reduz a
superfície: se um bug aparecer no serviço de perfil, ele não chega perto da tabela de
senhas.

### Como os dois se conectam então?

Pelo **id da conta**. Quando você cria uma conta no auth, ele devolve um id (um UUID).
Esse mesmo id vai dentro do token, no campo `sub`. O client usa esse id pra saber de
quem é cada perfil.

Os dois serviços **não conversam entre si**. Nenhum chama o outro. O elo é só esse id
que viaja no token.

---

## 2. Login, token e o que fazer com ele

### Qual o passo a passo, do zero?

**1. Criar a conta** (no auth, porta 8081)

```
POST /auth/register
{ "email": "cliente@ganjj.com", "password": "senhaSegura123" }
```

Devolve o id da conta. Toda conta criada assim nasce como `CLIENTE`.

**2. Fazer login** (no auth)

```
POST /auth/login
{ "email": "cliente@ganjj.com", "password": "senhaSegura123" }
```

Devolve:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 7200
}
```

**3. Criar o perfil** (no client, porta 8082), mandando o token no cabeçalho

```
POST /clients
Authorization: Bearer eyJhbGciOi...
{ "nome": "...", "telefone": "...", "endereco": { ... } }
```

Repare que você **não manda o id do usuário**. Ele vem de dentro do token.

**4. Usar normalmente.** A partir daí é só mandar o mesmo cabeçalho `Authorization` em
toda requisição, pra qualquer serviço.

### Por que preciso criar conta E perfil? Não é redundante?

Não, são coisas diferentes. A conta é o acesso (email e senha). O perfil é o cadastro
(nome, endereço). Dá pra ter conta sem perfil, e é o que acontece logo depois do
cadastro: a pessoa entrou no sistema mas ainda não preencheu os dados dela.

Se o front quiser, pode pedir tudo numa tela só e fazer as duas chamadas em sequência.

### O que é access token e o que é refresh token?

O **access token** é o que abre as portas. Você manda ele em toda requisição. Vale
**2 horas**.

O **refresh token** só serve pra pedir um access token novo quando o antigo expira.
Vale **7 dias**. Ele não abre rota nenhuma.

A ideia é que o access token circula muito (vai em toda requisição), então é bom que
ele valha pouco tempo. O refresh circula pouco (só na renovação), então pode durar mais.

### O access token expirou. E agora?

O front chama `POST /auth/refresh` mandando o refresh token no corpo, e recebe um par
novo.

**Isso é problema do front, não do seu serviço.** Seu serviço só responde `401` quando
o token estiver vencido. O front vê o 401, renova e repete a chamada.

### E o logout?

```
POST /auth/logout
{ "refreshToken": "..." }
```

Isso invalida o refresh token: ele não renova mais nada.

### Por que o logout não derruba o access token na hora?

Porque nenhum serviço pergunta pro auth se um token ainda vale. Cada um confere sozinho,
olhando só a assinatura. É isso que faz o sistema ser rápido e continuar funcionando se
o auth cair. O preço é que, depois do logout, o access token que já estava na mão
continua funcionando até expirar.

Na prática o front descarta o token ao sair, então ninguém usa. A janela só importaria
se alguém tivesse copiado o token antes.

---

## 3. Cookie ou não? Onde guardar o token

### O auth usa cookie?

**Não.** O auth devolve os tokens no corpo JSON da resposta do login. Ele não seta
cookie nenhum. Quem decide onde guardar é o front.

### Então onde o front guarda?

Três opções, com trocas diferentes:

**`localStorage`** é o mais simples e o mais comum em projeto de faculdade. O token
sobrevive a fechar e reabrir a aba. O risco é que qualquer JavaScript rodando na página
consegue ler, então um XSS rouba o token.

**`sessionStorage`** é igual, mas o token some ao fechar a aba. Um pouco mais seguro,
menos cômodo.

**Cookie `httpOnly`** é o mais seguro contra XSS, porque o JavaScript não consegue ler.
Mas exige que o backend seja quem seta o cookie, e o nosso não faz isso hoje. Também
abre a porta pra CSRF, que aí precisa de proteção própria.

Pro escopo do trabalho, `localStorage` está de bom tamanho. Se o professor perguntar,
a resposta honesta é: escolhemos simplicidade, sabendo do risco de XSS.

### Como o front manda o token depois?

No cabeçalho, sempre assim:

```
Authorization: Bearer eyJhbGciOi...
```

A palavra `Bearer`, um espaço, e o token.

### Por que token e não sessão no servidor?

Sessão exigiria que todos os serviços consultassem o mesmo lugar pra saber quem é o
usuário, e aí esse lugar vira gargalo e ponto único de falha. Com token, a informação
viaja junto da requisição e cada serviço se vira sozinho. É o que faz sentido quando são
cinco serviços em cinco linguagens.

---

## 4. Validando o token no seu serviço

### Preciso chamar o auth pra validar?

**Não.** Essa é a parte que costuma confundir. Você confere o token sozinho, usando a
chave pública. Nenhuma chamada de rede.

### Como assim? Como confiro sem perguntar pra ninguém?

O auth assina o token com uma **chave privada** que só ele tem. Essa assinatura pode ser
conferida por qualquer um que tenha a **chave pública** correspondente.

É como reconhecer a letra de alguém: você confere que foi ela quem escreveu, sem
precisar ligar e perguntar. E não conseguir imitar a letra é o que impede falsificação.

Chave pública **confere** assinatura. Chave privada **cria** assinatura. Vocês recebem
só a pública.

### De onde tiro a chave pública?

Do próprio auth:

```
GET /auth/public-key
```

Pega uma vez e guarda no seu projeto.

### Posso commitar a chave pública no Git?

**Pode.** Ela é pública de verdade, esse é o nome dela. Com ela ninguém consegue criar
token, só conferir. A que não pode sair de perto do auth é a privada, e essa está no
`.gitignore`.

### O que exatamente preciso configurar na biblioteca?

Quatro coisas:

1. **Algoritmo `RS256`**, fixo. Esse é o mais importante, explico no item 6.
2. **Emissor** igual a `ganjj-authorization`.
3. **Campo `typ`** igual a `access`, senão um refresh token passaria como token de acesso.
4. **Expiração**, que a maioria das bibliotecas já confere sozinha.

### E se o auth estiver fora do ar?

Seu serviço continua autenticando normalmente, porque ele não depende do auth pra
validar. O que para de funcionar é só criar conta, fazer login e renovar token.

Isso foi testado: derrubamos o auth e o client continuou aceitando tokens válidos e
recusando adulterados.

### O que vem dentro do token?

```json
{
  "iss": "ganjj-authorization",
  "sub": "d3f1a2b4-0000-...",
  "email": "cliente@ganjj.com",
  "role": "CLIENTE",
  "typ": "access",
  "exp": 1770000000
}
```

- `sub` é o **id da conta**. É por ele que você amarra seus dados ao usuário.
- `role` é `CLIENTE` ou `ADMIN`. É por ele que você libera rota de admin.
- `typ` distingue access de refresh.
- `exp` é quando expira.

---

## 5. Auth e client: quem guarda o quê

### Onde fica a senha?

Só no auth, e nunca em texto puro. Guardamos um hash BCrypt. Nem quem tem acesso ao
banco consegue ler a senha original.

### Por que o perfil tem id próprio E account_id?

O `id` identifica o **perfil**. O `account_id` diz de **qual conta** aquele perfil é.

São coisas diferentes porque são de serviços diferentes: o auth não sabe que perfis
existem, e o client não manda na conta. Se um dia um usuário pudesse ter dois perfis
(por exemplo, pessoa física e jurídica), a estrutura já aguenta.

### Se eu apagar o perfil, a conta some?

Não. São independentes. Apagar o perfil no client não mexe na conta do auth, e a pessoa
continua conseguindo fazer login. Ela só fica sem perfil, como estava logo depois do
cadastro.

### Posso guardar o email do usuário no meu serviço?

Pode, mas pense se precisa. O email já vem dentro do token a cada requisição, então
copiar pro seu banco cria uma segunda cópia que pode ficar desatualizada se a pessoa
trocar de email. Se for só pra exibir na tela, use o do token.

### Como crio um admin?

Não dá pelo cadastro, que sempre cria `CLIENTE`. O admin inicial vem de variável de
ambiente no auth (`ADMIN_EMAIL` e `ADMIN_PASSWORD`), criado na primeira subida.

Pra testar rota de admin, faça login com essa conta.

---

## 6. Segurança

### Alguém consegue ler meu token?

**Sim.** JWT é assinado, não criptografado. Qualquer um que tenha o token consegue ler
o conteúdo, sem chave nenhuma. É só base64, dá pra colar no site jwt.io e ver.

Consequência prática: **nunca coloque nada sensível dentro do token**. O que está lá
hoje (id, email, role) é inofensivo.

### Alguém consegue mudar a role pra ADMIN?

**Não.** Isso foi testado: trocando `CLIENTE` por `ADMIN` e reempacotando, a validação
recusa com erro de assinatura inválida. A assinatura cobre o conteúdo inteiro, e pra
gerar uma nova válida seria preciso a chave privada, que só o auth tem.

### Então por que tanta insistência com o RS256?

Existe um ataque conhecido chamado confusão de algoritmo. A chave pública é conhecida
por todos. Se um serviço aceitar qualquer algoritmo, alguém pode forjar um token
assinado com HMAC usando **a chave pública como se fosse senha**. Como a biblioteca
aceitaria os dois algoritmos, ela usaria a chave pública pra conferir e o token passaria.

Fixando `RS256`, o token forjado é recusado antes mesmo de olhar a assinatura.

Vale dizer: algumas bibliotecas já se protegem sozinhas (a PyJWT bloqueia esse caso mesmo
sem você fixar). Mas isso varia entre linguagens, e é barato não depender da sorte.

### Por que RS256 e não uma senha compartilhada (HS256)?

Com senha compartilhada, todos os cinco serviços precisariam ter a mesma senha, e essa
senha **cria** tokens. Ou seja: cinco lugares com poder de emitir token, e vazar de
qualquer um deles compromete tudo.

Com par de chaves, só o auth cria. Os outros só conferem. É a mesma diferença entre dar
a chave da casa e dar uma foto da fechadura.

### E se roubarem meu token?

Esse é o risco de verdade, e é maior que o de adulteração. Quem tem um token válido não
precisa modificar nada, é só usar. Vale até expirar (2 horas), e o logout não ajuda,
porque ele só invalida a renovação.

Por onde vaza na prática: token trafegando sem HTTPS, token indo parar em log, XSS
lendo o `localStorage`, ou alguém colando o token num print ou no chat do grupo.

O que reduz: HTTPS na entrega final, não logar token em lugar nenhum, e não deixar
token em mensagem.

### É seguro o suficiente pro trabalho?

Sim. As decisões que tomamos (RS256, senha com BCrypt, token de curta duração, refresh
de uso único) são as mesmas de sistema de verdade. O que ficou de fora é coisa que só
faz sentido com escala e time de operação, tipo rotação automática de chave e
invalidação global de sessão.

---

## 7. Deu erro, e agora

### 401 no meu serviço

Em ordem de probabilidade:

1. Esqueceu o `Bearer ` antes do token (com espaço).
2. Token expirou. Passaram 2 horas? O front precisa renovar.
3. Mandou o **refresh** token no lugar do **access**. Confira o campo `typ`.
4. A chave pública que você baixou é de outra execução do auth. Se o auth rodou sem as
   chaves configuradas, ele gera um par temporário a cada subida. Baixe de novo.
5. Emissor configurado errado. Tem que ser exatamente `ganjj-authorization`.

### 403 em vez de 401

401 é "não sei quem você é". 403 é "sei quem você é, mas você não pode". Se está tomando
403, o token é válido mas a conta não é `ADMIN`.

### Erro de CORS no navegador

O navegador está bloqueando porque o serviço não liberou a origem do front. Cada serviço
precisa configurar isso por conta. No auth e no client é a variável
`CORS_ALLOWED_ORIGINS`, com as origens separadas por vírgula.

Detalhe: tem que ser a origem exata, incluindo a porta. `http://localhost:4200` e
`http://localhost:3000` são origens diferentes.

### Não consigo conectar no auth de dentro do meu container

Clássico. Dentro do container, `localhost` é **o próprio container**, não o auth.

Use o nome do serviço: `http://authorization:8081`. E pra isso funcionar, seu serviço
precisa estar na rede `ganjj-net`:

```yaml
networks:
  - ganjj-net

# no fim do arquivo
networks:
  ganjj-net:
    external: true
```

E rode uma vez, antes de tudo: `docker network create ganjj-net`.

### "Bind for 0.0.0.0:5432 failed: port is already allocated"

Você já tem um PostgreSQL rodando na máquina. Suba numa porta diferente:
`DB_HOST_PORT=5433 docker compose up`.

### Chave não encontrada ao subir o serviço

Faltou rodar o script que gera (no auth) ou busca (no client) as chaves. A pasta `keys/`
não é versionada de propósito, então em clone novo ela não existe.

---

## 8. Perguntas soltas

### Posso testar sem front?

Pode. Os dois serviços têm Swagger:

- auth: `http://localhost:8081/swagger-ui.html`
- client: `http://localhost:8082/swagger/index.html`

Faça login pelo Swagger do auth, copie o `accessToken`, e cole no botão **Authorize**
do outro no formato `Bearer {token}`.

### Preciso subir o auth pra desenvolver meu serviço?

Pra rodar de verdade, sim, porque você precisa de um token. Pros seus testes
automatizados, não: gere um par de chaves no próprio teste e assine um token de mentira
com ele. É o que o client faz.

### Quantas vezes preciso baixar a chave pública?

Uma. Ela só muda se alguém gerar chaves novas no auth, e nesse caso todos os tokens
antigos param de valer de qualquer forma.

### O token muda a cada login?

Sim, sempre. Cada login gera um par novo. Tokens antigos continuam valendo até expirar,
o que é normal (é o que permite estar logado no celular e no computador ao mesmo tempo).

### Se eu usar o refresh token duas vezes, funciona?

Não. Ao renovar, o refresh token usado é queimado e um novo vem no lugar. Se o mesmo
for usado de novo, dá 401.

Isso é de propósito: se alguém roubar um refresh token, ele para de funcionar assim que
o dono legítimo renovar.

### Onde vejo isso tudo funcionando?

O `examples/verify_token.py` neste repositório mostra a validação passo a passo, com
comentários explicando cada conferência.
