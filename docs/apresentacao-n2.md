# Roteiro de apresentação N2 — Authorization

Responsável: Eduardo. Apresentação: **10/11/2026**. Ensaio do grupo até **07/11**, ligado à issue #7.

Este é um roteiro para estudar e executar, ainda sem registro de ensaio humano. A demonstração compartilhada deve seguir o kit EKS do grupo. O laboratório local serve para treinar os passos e conferir evidências.

## 1. O que falar e em quanto tempo

Sugestão: **3 minutos**, ajustar ao tempo combinado pelo grupo.

| Janela planejada | Fala sugerida | O que mostrar |
|---|---|---|
| 0:00–0:30 | “O Authorization cuida da identidade: e-mail, senha com BCrypt e papel da conta. Eu usei Java/Spring Boot e um banco Oracle próprio.” | Responsabilidade e banco separado do Client |
| 0:30–1:00 | “No EKS, a aplicação e o banco são acessados por Services. A entrada externa é o Gateway; os consumidores falam com authorization pelo DNS interno.” | Deployments/Services e namespace correto |
| 1:00–2:15 | “O login emite um access token assinado em RS256. A conta CLIENTE pode consultar a própria identidade; só ADMIN pode listar contas.” | Login preparado sem exibir token; respostas 200, 401 e 403 |
| 2:15–2:45 | “Só o Authorization tem a chave privada. Client, Gateway e outros consumidores usam a chave pública e validam assinatura, issuer, expiração e tipo do token.” | GET /auth/public-key, PEM público |
| 2:45–3:00 | “O ID da conta segue no sub. Agora o Client usa essa identidade para guardar o perfil em seu próprio banco.” | Passagem para a parte do Client |

## 2. Antes de compartilhar a tela

Combinar com o responsável pelo EKS a sessão do lab, cluster, namespace e ELB. A criação/remoção do cluster fica com o responsável; este roteiro não provisiona AWS.

Os comandos desta seção usam os nomes dos manifests k8s/ do kit EKS; o chart do laboratório local pode usar nomes diferentes. Confirmar o contexto e ajustar o namespace antes de executar. Variáveis exportadas valem somente na aba de Terminal em que foram definidas.

```bash
export GANJJ_NAMESPACE=default
export GANJJ_BASE_URL="http://ELB_DO_GATEWAY"
kubectl config current-context
kubectl get deployment authorization -n "$GANJJ_NAMESPACE"
kubectl get service authorization oracle-auth -n "$GANJJ_NAMESPACE"
```

Substituir o ELB e confirmar o contexto antes de continuar. No fluxo padrão do kit, o namespace é default. No mesmo namespace o DNS é authorization:8081; o nome explícito é authorization.default.svc:8081. authorization.ganjj-n2.svc é apenas do laboratório que criou ganjj-n2.

Preparar duas contas no Authorization desse mesmo ambiente: CLIENTE e ADMIN. As credenciais de um banco local não criam a conta no banco do EKS; conferir com o grupo. A chave de um token do Minikube pode ser diferente da chave do EKS.

### Carregar os tokens fora da tela da apresentação

Guardar dois arquivos JSON **privados, fora do Git**, cada um com os campos email e password da respectiva conta. Os caminhos abaixo são exemplos a substituir. Não mostrar o conteúdo dos arquivos, a senha ADMIN ou o JWT na tela.

```bash
export GANJJ_LOGIN_CLIENTE="/caminho/privado/login-cliente.json"
export GANJJ_LOGIN_ADMIN="/caminho/privado/login-admin.json"
export GANJJ_TOKEN_CLIENTE="$(curl -fsS -X POST "$GANJJ_BASE_URL/auth/login" -H 'Content-Type: application/json' --data-binary "@$GANJJ_LOGIN_CLIENTE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
export GANJJ_TOKEN_ADMIN="$(curl -fsS -X POST "$GANJJ_BASE_URL/auth/login" -H 'Content-Type: application/json' --data-binary "@$GANJJ_LOGIN_ADMIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
test -n "$GANJJ_TOKEN_CLIENTE" && printf 'Token CLIENTE carregado\n'
test -n "$GANJJ_TOKEN_ADMIN" && printf 'Token ADMIN carregado\n'
```

Se o login falhar, resolver antes da demonstração. Esses comandos carregam o token na variável; não executar echo da variável. Por padrão o access token vence em 120 minutos; fazer login novamente perto da apresentação, se necessário.

## 3. Demonstração reproduzível

Cada comando abaixo mostra apenas o código HTTP, sem despejar contas ou tokens. Resultado esperado está na tabela.

| Chamada | Esperado | Explicação |
|---|---|---|
| /auth/me sem token | 401 | Sem identidade válida |
| /auth/me com CLIENTE | 200 | Identidade autenticada |
| /auth/accounts com CLIENTE | 403 | Autenticado, sem papel ADMIN |
| /auth/accounts com ADMIN | 200 | Autorização por papel funciona |

```bash
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' "$GANJJ_BASE_URL/auth/me"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -H "Authorization: Bearer $GANJJ_TOKEN_CLIENTE" "$GANJJ_BASE_URL/auth/me"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -H "Authorization: Bearer $GANJJ_TOKEN_CLIENTE" "$GANJJ_BASE_URL/auth/accounts"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -H "Authorization: Bearer $GANJJ_TOKEN_ADMIN" "$GANJJ_BASE_URL/auth/accounts"
curl -fsS "$GANJJ_BASE_URL/auth/public-key"
```

A última resposta é a chave **pública**, nunca a privada. O Gateway permite essas rotas e encaminha o papel ao serviço; a regra ADMIN fica no Authorization.

## 4. Perguntas para treinar

| Pergunta | Resposta curta |
|---|---|
| Por que o Client não guarda a senha? | Cada serviço tem uma responsabilidade e persistência próprias; ambos relacionam a conta pelo sub. |
| Por que RS256? | Emissão usa chave privada; consumidores verificam com a pública sem poder emitir tokens. |
| JWT é uma senha criptografada? | Não. Aqui a assinatura protege integridade/autenticidade; a senha fica em hash BCrypt no banco. |
| Refresh abre uma rota protegida? | Não; as rotas exigem access token. Refresh é usado no endpoint próprio e tem rotação. |
| Logout invalida imediatamente o access token? | Não; revoga o refresh. O access já emitido permanece válido até expirar. |
| Se o Authorization cair? | Login/refresh ficam indisponíveis; consumidores com chave carregada ainda verificam access tokens válidos localmente. |

## 5. O que priorizar e plano B

A rubrica registrada pelo grupo prioriza responsabilidade individual, diversidade de linguagens/bancos, EKS e integração REST/RabbitMQ. JWT demonstra o funcionamento do seu serviço; Grafana/Helm são complementos se houver tempo. Não usar a antiga divisão de pontos 4/3/3 como se fosse a transcrição atual do Canvas.

Se o cluster falhar, identificar a falha, mostrar as evidências históricas como **históricas** e reagendar a execução no EKS com o grupo. Prints/Minikube não devem ser apresentados como execução nova no EKS.

- [Integração JWT validada no laboratório](evidence/2026-10-25/auth-client.json).
- [Promotion/Gateway com JWT real](evidence/2026-10-25/promotion-jwt.json).
- [Grafana histórico, extra](evidence/2026-10-25/grafana-authorization.jpg).
- [Guia operacional](entrega-local.md) e [aceite das issues](aceite-final.md).

## 6. Checklist e registro

- [ ] Confirmar cluster/contexto/namespace e ELB com o grupo.
- [ ] Preparar contas e tokens do mesmo Authorization implantado.
- [ ] Testar os quatro resultados HTTP antes de apresentar.
- [ ] Combinar a passagem de fala para Client e o tempo disponível.
- [ ] Fazer o ensaio humano, cronometrar e preencher [registro-ensaio.md](registro-ensaio.md).

Não fechar a issue #7 apenas por ter o roteiro pronto.

## Modelos usados

A estrutura segue os roteiros dos colegas: demonstração com comando/resultado, decisões para defender, perguntas e checklist.

- [Runbook do Rodrigo — Order](https://github.com/Back-End-Cloud-Computing/order/blob/main/docs/PRESENTATION-RUNBOOK.md).
- [Roteiro do Promotion](https://github.com/Back-End-Cloud-Computing/promotion/blob/main/docs/apresentacao-n2.md).
- [Rubrica atual transcrita pelo grupo](https://github.com/Back-End-Cloud-Computing/promotion/blob/main/docs/rubrica-n2.md). O documento registra a fonte Canvas e a dúvida sobre a soma dos pontos; este guia não redefine a avaliação.
- [Kit compartilhado EKS](https://github.com/Back-End-Cloud-Computing/api-gateway/tree/main/eks).

Preparação escrita em 29/10/2026. Tempos abaixo são sugestão; tempos reais só entram no [registro de ensaio](registro-ensaio.md) depois da atividade com o grupo.
