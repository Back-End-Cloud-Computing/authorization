package com.ganjj.authorization.response;

/**
 * Resposta do login e da renovação. O cliente guarda o accessToken e o envia
 * no header Authorization: Bearer &lt;token&gt; para os demais serviços.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
