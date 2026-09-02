# Authorization no Kubernetes

Manifests do Lab 05. Sobem o Oracle e três réplicas do Authorization, com
Service Discovery entre eles.

```
Service authorization :8081
          |
   Service Discovery
          |
   +------+------+------+
   |             |      |
  Pod           Pod    Pod
   |             |      |
   +------+------+------+
          |
          | oracle-auth:1521
          v
   Service oracle-auth
          |
        Pod Oracle
```

## Subir

```bash
./scripts/generate-keys.sh     # se ainda não houver keys/
./k8s/criar-secret.sh
kubectl apply -f k8s/oracle.yaml
kubectl apply -f k8s/authorization.yaml
```

O Oracle demora cerca de dois minutos na primeira subida. Enquanto ele não
responde, os Pods da aplicação reiniciam — é esperado, e param assim que o banco
fica pronto.

```bash
kubectl wait --for=condition=ready pod -l app=oracle-auth --timeout=300s
kubectl wait --for=condition=ready pod -l app=authorization --timeout=240s
kubectl get pods
```

## Por que as chaves ficam num Secret

Este é o ponto que diferencia o serviço do `ms-task` do laboratório.

O `ms-task` não guarda estado, então três réplicas são intercambiáveis. O
Authorization assina tokens com um par RSA. Se cada Pod gerasse o seu, um token
emitido pelo Pod A seria recusado pelo Pod B, e o login ficaria intermitente
sem erro aparente.

As três réplicas montam o **mesmo Secret**, então todas assinam e verificam com
a mesma chave. O `criar-secret.sh` cria esse Secret a partir de `keys/`, que não
é versionado.

Verificando na prática, de dentro do cluster:

```bash
kubectl run teste --image=curlimages/curl:latest -it --rm -- sh
```

```sh
# o token é emitido por um Pod qualquer
T=$(curl -s -X POST http://authorization:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"k8s@ganjj.com","password":"senhaSegura123"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

# e é aceito por todos, porque a chave é a mesma
for i in 1 2 3 4 5 6; do
  curl -s http://authorization:8081/instance
  curl -s -o /dev/null -w " -> %{http_code}\n" \
    http://authorization:8081/auth/me -H "Authorization: Bearer $T"
done
```

## Ver a distribuição entre as réplicas

```sh
for i in 1 2 3 4 5 6 7 8 9 10; do curl -s http://authorization:8081/instance; echo; done
```

O endpoint `/instance` devolve o nome do Pod que respondeu, lido da variável
`HOSTNAME`. Fora do cluster devolve `local`.

## Autorrecuperação

```bash
kubectl get pods -l app=authorization
kubectl delete pod <nome-de-um-pod>
kubectl get pods -l app=authorization
```

O manifesto declara `replicas: 3`. Ao perder um Pod, o Deployment cria outro
para voltar ao estado declarado. O Service atualiza os endpoints sozinho, e quem
consome continua usando o mesmo endereço `authorization:8081`.

## Escalar

Altere `replicas` em `authorization.yaml` e reaplique. Não use `kubectl scale`:
o arquivo no Git deve representar o estado desejado.

```bash
kubectl apply -f k8s/authorization.yaml
```

## Acessar do host

O Service é ClusterIP, então não publica porta na máquina. Para testar o Swagger:

```bash
kubectl port-forward service/authorization 8081:8081
```

## Sobre a imagem

Os manifests apontam para `ganjj/authorization:1.1.0` com
`imagePullPolicy: IfNotPresent`, que usa a imagem local quando o cluster
compartilha o daemon do Docker (caso do OrbStack). Para publicar:

```bash
docker tag ganjj/authorization:1.1.0 seu-usuario/authorization:1.1.0
docker push seu-usuario/authorization:1.1.0
```

E troque o campo `image` no `authorization.yaml`.

## Limitação conhecida

O Oracle roda como Deployment sem volume persistente: se o Pod for recriado, o
banco começa vazio e a aplicação recria o schema e a conta ADMIN. Para o
laboratório basta. Um banco de verdade pediria StatefulSet com
PersistentVolumeClaim.
