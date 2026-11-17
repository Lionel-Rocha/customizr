package com.vttcaseiro.net;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Senha sem viajar pela rede (desafio-resposta). O servidor manda um
 * desafio aleatorio novo a cada conexao; o cliente responde
 * HMAC-SHA256(chave = senha, dado = desafio). Quem escuta a rede ve so o
 * desafio e a prova -- nao a senha -- e a prova nao serve numa conexao
 * seguinte (o desafio muda).
 * <p>
 * Limite honesto: isto protege a SENHA, nao o resto do jogo (chat, fichas
 * continuam sem criptografia). Pra jogar pela internet aberta, use uma rede
 * privada tipo Tailscale/ZeroTier.
 */
public final class PasswordProof {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordProof() {
    }

    public static String newChallenge() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** null/vazio = sem senha -> sem prova. */
    public static String prove(String password, String challenge) {
        if (password == null || password.isEmpty() || challenge == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 indisponivel nesta JVM", ex);
        }
    }

    /** Senha nao configurada sempre bate; senao compara em tempo constante. */
    public static boolean matches(String expectedPassword, String challenge, String givenProof) {
        if (expectedPassword == null || expectedPassword.isBlank()) return true;
        if (givenProof == null) return false;
        String expected = prove(expectedPassword, challenge);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), givenProof.getBytes(StandardCharsets.UTF_8));
    }
}
