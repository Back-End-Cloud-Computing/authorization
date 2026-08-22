# Gera o par de chaves RSA usado para assinar os tokens JWT.
#
#   private.pem -> fica SO neste servico; e o que assina os tokens
#   public.pem  -> vai para os outros microsservicos; so permite verificar
#
# As chaves nao entram no Git (ver .gitignore). Cada ambiente gera as suas.
#
# Versao PowerShell do generate-keys.sh, para quem usa Windows.
# Uso:  .\scripts\generate-keys.ps1

$ErrorActionPreference = 'Stop'

$dest = Join-Path (Split-Path -Parent $PSScriptRoot) 'keys'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$privada = Join-Path $dest 'private.pem'
$publica = Join-Path $dest 'public.pem'

if (Test-Path $privada) {
    Write-Host "Ja existe $privada - apague antes de gerar de novo."
    Write-Host "Atencao: trocar a chave invalida todos os tokens em circulacao."
    exit 1
}

# O Windows nao traz openssl. O Git para Windows traz, e quem esta neste projeto
# ja precisa de Docker - entao usamos o openssl do Docker quando nao houver um
# local, e ninguem precisa instalar nada a mais.
$temOpenssl = $null -ne (Get-Command openssl -ErrorAction SilentlyContinue)

if ($temOpenssl) {
    & openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privada 2>$null
    & openssl rsa -in $privada -pubout -out $publica 2>$null
}
else {
    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error @"
Nao encontrei nem openssl nem docker.

Instale uma das opcoes:
  - Git para Windows (traz o openssl): https://git-scm.com/download/win
  - Docker Desktop: https://www.docker.com/products/docker-desktop
"@
        exit 1
    }

    Write-Host 'openssl nao encontrado; usando o do Docker...'
    $montagem = "${dest}:/keys"
    & docker run --rm -v $montagem alpine/openssl `
        genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /keys/private.pem
    if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao gerar a chave privada.'; exit 1 }

    & docker run --rm -v $montagem alpine/openssl `
        rsa -in /keys/private.pem -pubout -out /keys/public.pem
    if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao gerar a chave publica.'; exit 1 }
}

if (-not (Test-Path $publica)) {
    Write-Error 'As chaves nao foram geradas.'
    exit 1
}

Write-Host "Chaves geradas em ${dest}:"
Write-Host '  private.pem  (fica aqui, nunca compartilhe)'
Write-Host '  public.pem   (distribua para os outros servicos do GANJJ)'
