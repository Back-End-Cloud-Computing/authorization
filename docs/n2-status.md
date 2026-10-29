# Validação N2 — authorization

Atualizado em 29/10/2026; as validações técnicas abaixo foram registradas em 25/10/2026. Evidências sanitizadas em `docs/evidence/2026-10-25/`.

## Executado

- Build, análise e testes locais; CI do PR anterior verde (nova revisão será validada novamente).
- Imagem pública Docker Hub `eduardofabri/authorization:1.2.1`, com linux/amd64 e linux/arm64. Manifest inspecionado e acesso anônimo validado.
- Instalação real por Helm em Minikube limpo (perfil `ganjj-n2`, Kubernetes 1.37.0), credenciais externas ao Git.
- Prometheus coletando o serviço e dashboard Grafana importado por provisionamento. Captura versionada.
- Três réplicas validadas; login ADMIN/CLIENTE e assinatura RS256 conferidos no cluster. Chave pública consumida por client, gateway e promotion reais.
- Ajuste de inicialização: Hikari aguarda Oracle; startupProbe tolera boot frio; probes com timeout de 5s.
- Gateway validou login/perfil e rejeição de refresh. Promotion retornou ADMIN 200, CLIENTE 403 e 401 para ausência, refresh, formato inválido, expiração, issuer errado e assinatura inválida.
- Comparação com o material indicado por Eduardo registrada em `docs/jwt-n2.md`, incluindo diferenças para Keycloak/Istio/aud.
- Ingress aceita `/auth/public-key` e não encaminha `/metrics` (404).

## Pendências humanas / externas

- Ensaio da apresentação com o grupo, com tempo por pessoa; roteiro pronto, mas não foi declarado um ensaio humano que não ocorreu.
- Compartilhamento privado ADMIN confirmado por Eduardo em 29/10 com promotion/gateway; [issue #8 encerrada](https://github.com/Back-End-Cloud-Computing/authorization/issues/8) após registrar a confirmação. Nenhuma senha/token/chave privada deve constar em issue ou PR.

Guia operacional próprio: [entrega-local.md](entrega-local.md). O laboratório de desenvolvimento permanece local; a implantação compartilhada da N2 segue o kit EKS do grupo; DNS .svc não é hospedagem pública. A revisão documental não reexecutou a suíte técnica.
