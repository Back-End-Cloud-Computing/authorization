"""
Como os outros microsserviços do GANJJ validam um token.

Este exemplo está em Python (product-service), mas a ideia é a mesma em
Node, C#, PHP e Go: carregar a chave pública uma vez e verificar a assinatura
localmente. Nenhuma chamada ao Authorization Service acontece aqui — nem na
inicialização, se a chave vier por variável de ambiente.

Rodar:
    curl -s http://localhost:8081/auth/public-key > public.pem
    python examples/verify_token.py "<accessToken>"

Em produção, prefira uma biblioteca JWT da sua linguagem (PyJWT, jsonwebtoken,
System.IdentityModel.Tokens.Jwt, firebase/php-jwt, golang-jwt) em vez de
verificar a assinatura na mão como abaixo. O código explícito está aqui só para
deixar claro o que acontece por baixo.
"""

import base64
import json
import sys
import time

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ISSUER = "ganjj-authorization"


def _b64url(segment: str) -> bytes:
    """Base64 URL-safe do JWT vem sem o padding '='."""
    return base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))


def verify(token: str, public_key_path: str = "public.pem") -> dict:
    """Devolve as claims do token, ou levanta ValueError se ele não servir."""
    with open(public_key_path, "rb") as file:
        public_key = serialization.load_pem_public_key(file.read())

    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
    except ValueError:
        raise ValueError("Token malformado.")

    try:
        public_key.verify(
            _b64url(signature_b64),
            f"{header_b64}.{payload_b64}".encode(),
            padding.PKCS1v15(),
            hashes.SHA256(),
        )
    except InvalidSignature:
        raise ValueError("Assinatura inválida — o token não foi emitido pelo Authorization.")

    claims = json.loads(_b64url(payload_b64))

    if claims.get("iss") != ISSUER:
        raise ValueError("Emissor inesperado.")
    if claims.get("typ") != "access":
        raise ValueError("Este não é um token de acesso.")
    if claims.get("exp", 0) < time.time():
        raise ValueError("Token expirado.")

    return claims


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    try:
        claims = verify(sys.argv[1])
    except ValueError as error:
        print(f"Recusado: {error}")
        sys.exit(1)

    # "sub" é o id da conta — a mesma referência usada pelo serviço client.
    print(f"conta : {claims['sub']}")
    print(f"email : {claims['email']}")
    print(f"papel : {claims['role']}")
