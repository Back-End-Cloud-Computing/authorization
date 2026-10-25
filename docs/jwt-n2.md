# Contrato JWT — verificação de implementação

## Evidências locais

`TokenService` emite RS256 com `iss=ganjj-authorization` por padrão; access tokens contêm `sub`, `email`, `role` e `typ=access`. Refresh tokens contêm `jti` e `typ=refresh`. `SecurityFilter` exige access token para recursos protegidos; rotas administrativas exigem ADMIN. A suíte cobre assinatura com chave pública, fluxo de autenticação, refresh concorrente, revogação e autorização.

`GET /auth/public-key` é público e continua acessível pelo Ingress. O par RSA precisa ser compartilhado pelas réplicas via `jwt-keys`. A conta inicial ADMIN é provisionada por `ADMIN_EMAIL` e `ADMIN_PASSWORD`, esta última no Secret `authorization-credentials`.

## Aceites externos pendentes

A descrição da issue #9 menciona RS256, refresh e roles: esses pontos estão implementados. O material indicado por Eduardo foi consultado em 25/10/2026: [Arquitetura Cloud Native, etapa 3 — Keycloak + JWT](https://devlabs.escdodev.com.br/labs/cloudnative). A página não identifica a data de 20/10, então esta comparação se refere ao laboratório publicado.

| Laboratório | GANJJ atual | Decisão registrada |
|---|---|---|
| Keycloak/OIDC como emissor | Authorization próprio com Spring Boot e Oracle | Manter o emissor já integrado ao GANJJ; não declarar suporte OIDC/SSO. |
| Istio RequestAuthentication + AuthorizationPolicy | Verificação RS256 no gateway e nos serviços | Manter os middlewares atuais; equivalência de respostas 200/401/403 testada, sem afirmar que há service mesh. |
| JWKS e issuer do realm | PEM em `/auth/public-key` e `iss=ganjj-authorization` | Manter contrato PEM compartilhado com os serviços; JWKS não é implementado. |
| Claim `aud=lab-api` na política de demonstração | Validação de issuer, assinatura, expiração, `typ` e papel | `aud` não faz parte do contrato atual; sua adoção deve ser coordenada entre os serviços antes de exigir a claim. |
| Bloquear sem JWT e liberar com JWT correto | Testes no gateway, client e promotion | Validado no Minikube com emissor real e contas fictícias. |

As diferenças ficam registradas como decisão de arquitetura, conforme o aceite da issue #9. Se o professor exigir a infraestrutura literal de Keycloak/Istio, a equipe precisará planejar essa mudança conjunta; não está sendo apresentada como já implementada.

Para #8, validar a chave no cluster e combinar as credenciais de uma conta ADMIN de teste com os responsáveis por `promotion` #49 e `api-gateway` #13 por canal privado. Não publicar senhas, tokens ou chave privada em issues, PRs ou no repositório.

```bash
kubectl port-forward svc/authorization 8081:8081
curl --fail --silent http://localhost:8081/auth/public-key | openssl pkey -pubin -noout
```

Depois, usar a conta de teste para obter um access token e executar os testes de integração do promotion/gateway. Em 25/10/2026, o endpoint e uma conta ADMIN de teste foram validados no Minikube; gateway, client e promotion aceitaram os tokens reais e rejeitaram refresh/assinatura inválida. Evidência sanitizada em `docs/evidence/2026-10-25/`. As credenciais estão apenas em arquivos locais privados de teste e Secrets do namespace `ganjj-n2`. O compartilhamento humano de credenciais com o grupo deve ocorrer por canal privado, nunca por issue ou PR.
