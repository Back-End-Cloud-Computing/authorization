# Entrega local — authorization

Atualizado em 29/10/2026. Este guia pertence ao authorization; rotas, payloads e variáveis completas estão no [README](../README.md).

## Desenvolvimento local e apresentação N2

Este guia descreve o laboratório de desenvolvimento e validação local usado nesta etapa. A decisão temporária de trabalhar localmente não define o destino final da apresentação. A implantação compartilhada da N2 segue o [kit Amazon EKS do api-gateway](https://github.com/Back-End-Cloud-Computing/api-gateway/tree/main/eks), coordenado pelo grupo. O Gateway/Ingress é a entrada externa única; client, authorization e support continuam serviços internos, sem necessidade de uma URL pública por serviço.

O kit aplica os manifests no namespace do contexto kubectl, default no fluxo padrão. DNS curto como authorization:8081 funciona para consumidores no mesmo namespace; authorization.default.svc:8081 identifica explicitamente o namespace default. O DNS com ganjj-n2 só vale no laboratório onde esse namespace foi criado/aplicado. Esta revisão é documental: não cria nem modifica infraestrutura AWS.

## Responsabilidade e dependências

Authorization mantém e-mail, senha com BCrypt e papel da conta no Oracle. Emite access/refresh tokens assinados com RSA/RS256 e publica a chave pública para os serviços consumidores. Nome, endereço e telefone são responsabilidade do client, ligados pelo mesmo ID de conta.

Requisitos: Java 21, Oracle e par RSA persistente para integração compartilhada. Maven usa o wrapper do repositório; Docker Compose inicia Oracle e aplicação na rede ganjj-net.

## Endereços e integração entre computadores

| Contexto | URL |
|---|---|
| Computador com Compose padrão | http://localhost:8081 |
| Dentro da rede Docker ganjj-net | http://authorization:8081 |
| Laboratório Minikube, namespace ganjj-n2 explicitamente criado | http://authorization.ganjj-n2.svc:8081 |
| EKS, consumidores no mesmo namespace | http://authorization:8081 |
| EKS, namespace default do fluxo padrão | http://authorization.default.svc:8081 |
| Swagger no computador | http://localhost:8081/swagger-ui.html |
| Chave pública no computador | http://localhost:8081/auth/public-key |
| Saúde no computador | http://localhost:8081/actuator/health |

O endereço .svc é interno do cluster; localhost aponta para quem faz a requisição. Esses endereços não são URLs públicas para colegas em outra máquina. Para desenvolver localmente, cada ambiente executa os serviços e distribui a chave pública correspondente ou combina acesso à rede do laboratório. Isso não substitui a implantação compartilhada no EKS para a apresentação; externamente, a entrada continua sendo o Gateway/Ingress.

## Iniciar

```bash
./scripts/generate-keys.sh
docker compose up --build
```

A rede ganjj-net deve existir (docker network create ganjj-net quando ainda não existir). O script de chaves recusa substituir um par existente; não gerar outro par apenas para reiniciar o serviço. AUTH_HOST_PORT altera a porta externa quando a 8081 estiver ocupada. Na rede Docker, a porta interna continua 8081.

Sem Docker, disponibilizar Oracle, exportar variáveis DB_* e JWT_PRIVATE_KEY_PATH/JWT_PUBLIC_KEY_PATH e executar ./mvnw spring-boot:run. Sem os caminhos de chaves, o serviço gera um par temporário: tokens antigos e consumidores com outro PEM deixam de validar após reinício. Não compartilhar a chave privada.

## Contrato para client, promotion e gateway

- POST /auth/register cria CLIENTE; POST /auth/login retorna accessToken e refreshToken.
- POST /auth/refresh gira o refresh token; POST /auth/logout revoga o refresh apresentado. Ambos usam o refresh no corpo.
- GET /auth/me exige Bearer access token; GET /auth/accounts exige ADMIN.
- GET /auth/public-key retorna PEM público. Cada consumidor valida assinatura RS256, issuer ganjj-authorization, typ=access e expiração; sub identifica a conta e role controla permissão.
- Padrões: access por 120 minutos, refresh por 7 dias. Logout não revoga imediatamente o access token já emitido.

Guia detalhado: [COMO-FUNCIONA](COMO-FUNCIONA.md). Comparação com o material indicado pelo usuário e diferenças Keycloak/Istio/aud: [JWT N2](jwt-n2.md).

## Conta ADMIN e compartilhamento

ADMIN_EMAIL e ADMIN_PASSWORD configuram a conta inicial de teste na inicialização. O cadastro público não concede ADMIN. Usar o mesmo par de chaves para emitir e verificar os tokens nesse ambiente.

Eduardo confirmou em 29/10 o compartilhamento das credenciais ADMIN de teste por canal privado com os responsáveis por promotion e gateway. Isso atende à pendência humana de compartilhamento da issue #8 registrada na auditoria. Senha, tokens e chave privada não pertencem ao README nem à issue. A confirmação foi registrada no GitHub e a [issue #8 foi encerrada](https://github.com/Back-End-Cloud-Computing/authorization/issues/8).

Credenciais de um banco local não criam automaticamente a mesma conta em outro banco: os colegas devem usar o ambiente combinado ou configurar a conta de teste na própria instância. O endpoint e a chave pública precisam pertencer à instância que emitiu o token.

## Testes e evidências

```bash
./mvnw verify
```

Os testes da aplicação usam H2 no lugar de Oracle. Para o fluxo HTTP real entre authorization e client, com ambos iniciados, existe ./scripts/smoke-test.sh; ele cria contas de teste novas.

Validações anteriores registradas em 25/10: três réplicas, ADMIN/CLIENTE, RS256, gateway e promotion reais, rejeição de refresh/token inválido, Helm, Prometheus/Grafana e imagem pública eduardofabri/authorization:1.2.1 multiarch. Não foram repetidas nesta revisão documental.

- [JWT/client](evidence/2026-10-25/auth-client.json) e [promotion/gateway](evidence/2026-10-25/promotion-jwt.json)
- [Imagem publicada](evidence/2026-10-25/authorization-image.json)
- [Helm](../helm/authorization/README.md), [Secrets](../k8s/README.md) e [Grafana](../k8s/observability/README.md)

## Pendências

Realizar o ensaio em grupo (#7), por último. A issue #8 está encerrada; a revisão documental foi enviada à branch própria codex/documentacao-authorization, aguardando revisão para integração. O laboratório desta etapa permanece local; o destino compartilhado da N2 é tratado pelo kit EKS do grupo.

Matriz completa de aceite das issues deste serviço: [aceite-final.md](aceite-final.md).
