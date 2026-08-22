package com.ganjj.authorization.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cobre a leitura das chaves em PEM — o caminho que realmente roda em Docker.
 * Sem este teste, uma falha de parsing ficaria escondida atrás do par temporário
 * que o serviço gera quando as variáveis de ambiente não estão configuradas.
 */
class RsaKeyProviderTest {

    @Test
    void carregaOParDeChavesAPartirDeArquivosPem(@TempDir Path dir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        Path privatePem = dir.resolve("private.pem");
        Path publicPem = dir.resolve("public.pem");
        Files.writeString(privatePem, toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicPem, toPem("PUBLIC KEY", pair.getPublic().getEncoded()));

        RsaKeyProvider provider = new RsaKeyProvider(new JwtProperties(
                "ganjj-authorization", 15, 7, privatePem.toString(), publicPem.toString()));
        provider.load();

        assertThat(provider.getPrivateKey().getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
        assertThat(provider.getPublicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void semChavesConfiguradasGeraUmParTemporario() throws Exception {
        RsaKeyProvider provider = new RsaKeyProvider(
                new JwtProperties("ganjj-authorization", 15, 7, "", ""));
        provider.load();

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
    }

    /** O PEM exportado precisa ser relido sem perda — é o que os outros serviços consomem. */
    @Test
    void pemExportadoPodeSerLidoDeVolta(@TempDir Path dir) throws Exception {
        RsaKeyProvider origem = new RsaKeyProvider(
                new JwtProperties("ganjj-authorization", 15, 7, "", ""));
        origem.load();

        Path publicPem = dir.resolve("public.pem");
        Files.writeString(publicPem, origem.publicKeyAsPem());

        // Reaproveita a chave privada temporária só para satisfazer o par de caminhos.
        Path privatePem = dir.resolve("private.pem");
        Files.writeString(privatePem, toPem("PRIVATE KEY", origem.getPrivateKey().getEncoded()));

        RsaKeyProvider relido = new RsaKeyProvider(new JwtProperties(
                "ganjj-authorization", 15, 7, privatePem.toString(), publicPem.toString()));
        relido.load();

        assertThat(relido.getPublicKey().getEncoded()).isEqualTo(origem.getPublicKey().getEncoded());
    }

    private String toPem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
