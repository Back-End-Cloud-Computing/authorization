# Instalar authorization

Crie as credenciais e as chaves com `scripts/generate-keys.sh` e `k8s/criar-secret.sh` antes da instalação. Todos os recursos devem estar no mesmo namespace.

```bash
./k8s/criar-credentials.sh
helm lint helm/authorization
helm upgrade --install authorization helm/authorization --wait --timeout 10m
```

A imagem `1.2.0` precisa ser publicada pelo workflow de release antes de instalar. Para teste local, use `--set image.repository=... --set image.tag=...`.

`existingSecret` referencia as credenciais; `jwtExistingName` referencia o Secret com o par RSA. Nenhuma chave ou senha integra o chart. Os nomes dos Services seguem o contrato entre serviços; instale uma release por namespace. `database.enabled=false` reutiliza um banco acessível no Service oracle-auth. O banco deste laboratório não tem persistência: não use este chart para dados de produção.
