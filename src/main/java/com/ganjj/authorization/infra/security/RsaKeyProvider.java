package com.ganjj.authorization.infra.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Guarda o par de chaves RSA usado para assinar os tokens.
 *
 * Este é o único serviço do GANJJ que conhece a chave privada. Os demais
 * (client, product, cart, order, promotion) recebem apenas a chave pública e
 * conseguem verificar a assinatura, mas não produzir tokens novos.
 */
@Component
@RequiredArgsConstructor
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final JwtProperties properties;

    @Getter
    private RSAPrivateKey privateKey;

    @Getter
    private RSAPublicKey publicKey;

    @PostConstruct
    void load() throws Exception {
        boolean configured = hasText(properties.privateKeyPath()) && hasText(properties.publicKeyPath());

        if (configured) {
            this.privateKey = readPrivateKey(Path.of(properties.privateKeyPath()));
            this.publicKey = readPublicKey(Path.of(properties.publicKeyPath()));
            log.info("Chaves RSA carregadas de {} e {}", properties.privateKeyPath(), properties.publicKeyPath());
            return;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();

        log.warn("JWT_PRIVATE_KEY_PATH/JWT_PUBLIC_KEY_PATH não configurados: "
                + "um par RSA temporário foi gerado para esta execução. "
                + "Os tokens deixam de valer a cada restart e os outros serviços "
                + "não conseguem validá-los. Use apenas em desenvolvimento local.");
    }

    /** Chave pública em PEM, para os demais serviços consumirem. */
    public String publicKeyAsPem() {
        String encoded = Base64.getMimeEncoder(64, System.lineSeparator().getBytes())
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----" + System.lineSeparator()
                + encoded + System.lineSeparator()
                + "-----END PUBLIC KEY-----" + System.lineSeparator();
    }

    private RSAPrivateKey readPrivateKey(Path path) throws Exception {
        byte[] der = decodePem(Files.readString(path));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey readPublicKey(Path path) throws Exception {
        byte[] der = decodePem(Files.readString(path));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Remove cabeçalho, rodapé e quebras de linha do PEM e decodifica o Base64. */
    private byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
