# Contrato JWT — verificação de implementação

## Evidências locais

`TokenService` emite RS256 com `iss=ganjj-authorization` por padrão; access tokens contêm `sub`, `email`, `role` e `typ=access`. Refresh tokens contêm `jti` e `typ=refresh`. `SecurityFilter` exige access token para recursos protegidos; rotas administrativas exigem ADMIN. A suíte cobre assinatura com chave pública, fluxo de autenticação, refresh concorrente, revogação e autorização.

`GET /auth/public-key` é público e continua acessível pelo Ingress. O par RSA precisa ser compartilhado pelas réplicas via `jwt-keys`. A conta inicial ADMIN é provisionada por `ADMIN_EMAIL` e `ADMIN_PASSWORD`, esta última no Secret `authorization-credentials`.

## Aceites externos pendentes

A descrição da issue #9 menciona RS256, refresh e roles: esses pontos estão implementados. O material efetivo da aula de 20/10 não foi fornecido; comparar este contrato com a aula e registrar diferenças antes de fechar #9.

Para #8, validar a chave no cluster e combinar as credenciais de uma conta ADMIN de teste com os responsáveis por `promotion` #49 e `api-gateway` #13 por canal privado. Não publicar senhas, tokens ou chave privada em issues, PRs ou no repositório.

```bash
kubectl port-forward svc/authorization 8081:8081
curl --fail --silent http://localhost:8081/auth/public-key | openssl pkey -pubin -noout
```

Depois, usar a conta de teste para obter um access token e executar os testes de integração do promotion/gateway. Não há evidência de cluster ou de acordo de credenciais nesta entrega; #8 permanece pendente.
