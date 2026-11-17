package com.vttcaseiro.net;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Conexao criptografada (TLS) sem depender de ninguem: quem hospeda gera um
 * certificado proprio (com o {@code keytool} do JDK) e passa pros jogadores o
 * "codigo da mesa" (impressao digital do certificado). Na primeira conexao o
 * programa mostra o codigo pra conferir e lembra dele; se um dia o codigo
 * mudar (alguem no meio do caminho), a conexao e recusada com aviso.
 * <p>
 * O aperto de mao aceita qualquer certificado e a conferencia e feita logo
 * depois, ANTES de mandar qualquer coisa (senha inclusive) -- e o mesmo
 * modelo do SSH.
 */
public final class TlsSupport {

    private static volatile Path baseDir = Path.of(System.getProperty("user.home"), ".vttcaseiro", "tls");
    private static final String ALIAS = "vtt";

    private TlsSupport() {
    }

    /** Pasta do certificado e das mesas conhecidas (os testes usam uma temporaria). */
    public static void setBaseDir(Path dir) {
        baseDir = dir;
    }

    /** Pergunta ao usuario se confia neste codigo. previous != null = o codigo MUDOU. */
    public interface TrustPrompt {
        boolean accept(String hostPort, String fingerprint, String previous);
    }

    // ------------------------------------------------------------------
    // Lado de quem hospeda
    // ------------------------------------------------------------------

    private static synchronized KeyStore keyStore(char[][] passOut) throws IOException {
        Files.createDirectories(baseDir);
        Path ks = baseDir.resolve("host.p12");
        Path passFile = baseDir.resolve("host.pass");
        if (!Files.exists(ks) || !Files.exists(passFile)) {
            byte[] raw = new byte[18];
            new SecureRandom().nextBytes(raw);
            String pass = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            Files.writeString(passFile, pass, StandardCharsets.UTF_8);
            Files.deleteIfExists(ks);
            generate(ks, pass);
        }
        char[] pass = Files.readString(passFile, StandardCharsets.UTF_8).trim().toCharArray();
        passOut[0] = pass;
        try (InputStream in = Files.newInputStream(ks)) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, pass);
            return store;
        } catch (GeneralSecurityException ex) {
            throw new IOException("Certificado da mesa estragado (apague " + baseDir + " pra gerar outro): " + ex.getMessage());
        }
    }

    private static void generate(Path ks, String pass) throws IOException {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", win ? "keytool.exe" : "keytool");
        if (!Files.exists(keytool)) throw new IOException("keytool não encontrado no Java (" + keytool + ").");
        ProcessBuilder pb = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", ALIAS,
                "-keyalg", "EC", "-groupname", "secp256r1", "-sigalg", "SHA256withECDSA", "-validity", "3650",
                "-dname", "CN=Customizr", "-keystore", ks.toString(), "-storetype", "PKCS12",
                "-storepass", pass, "-keypass", pass, "-noprompt");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0 || !Files.exists(ks)) {
                throw new IOException("Não foi possível gerar o certificado: " + output.strip());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Geracao do certificado interrompida.");
        }
    }

    /** Contexto TLS do servidor (gera o certificado na primeira vez). */
    public static SSLContext hostContext() throws IOException {
        char[][] pass = new char[1][];
        KeyStore store = keyStore(pass);
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(store, pass[0]);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
            return ctx;
        } catch (GeneralSecurityException ex) {
            throw new IOException("TLS indisponivel: " + ex.getMessage());
        }
    }

    /** Codigo desta mesa (impressao digital SHA-256 do certificado). */
    public static String hostFingerprint() throws IOException {
        char[][] pass = new char[1][];
        KeyStore store = keyStore(pass);
        try {
            return fingerprint(store.getCertificate(ALIAS));
        } catch (GeneralSecurityException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Lado de quem conecta
    // ------------------------------------------------------------------

    /** Aceita qualquer certificado no aperto de mao; quem confere e {@link #verify}. */
    public static SSLContext trustAllClient() throws IOException {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (GeneralSecurityException ex) {
            throw new IOException("TLS indisponivel: " + ex.getMessage());
        }
    }

    public static String fingerprint(Certificate cert) throws IOException {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(d);
        } catch (GeneralSecurityException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    /** "AB12 CD34 EF56 7890": o comeco do codigo, facil de ler em voz alta pra conferir. */
    public static String shortCode(String fingerprint) {
        String hex = fingerprint == null ? "" : fingerprint.replace(":", "");
        if (hex.length() < 16) return hex;
        return hex.substring(0, 4) + " " + hex.substring(4, 8) + " " + hex.substring(8, 12) + " " + hex.substring(12, 16);
    }

    private static Path knownFile() {
        return baseDir.resolve("mesas-conhecidas.properties");
    }

    public static synchronized String known(String hostPort) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(knownFile())) {
            p.load(in);
        } catch (IOException ignored) {
            return null;
        }
        return p.getProperty(hostPort);
    }

    public static synchronized void remember(String hostPort, String fingerprint) throws IOException {
        Files.createDirectories(baseDir);
        Properties p = new Properties();
        if (Files.exists(knownFile())) {
            try (InputStream in = Files.newInputStream(knownFile())) {
                p.load(in);
            }
        }
        p.setProperty(hostPort, fingerprint);
        try (OutputStream out = Files.newOutputStream(knownFile())) {
            p.store(out, "Mesas TLS confiaveis (host:porta = código)");
        }
    }

    /**
     * Faz o aperto de mao e confere o codigo: igual ao lembrado = ok; novo =
     * pergunta (prompt null = aceita e lembra, usado pra conexao local);
     * diferente do lembrado = so com aceite explicito.
     */
    public static void verify(SSLSocket socket, String hostPort, TrustPrompt prompt) throws IOException {
        try {
            socket.startHandshake();
        } catch (javax.net.ssl.SSLException ex) {
            throw new IOException("A mesa não respondeu com conexão criptografada (o mestre marcou TLS?): " + ex.getMessage());
        }
        String fp = fingerprint(socket.getSession().getPeerCertificates()[0]);
        String previous = known(hostPort);
        if (fp.equals(previous)) return;
        boolean ok = prompt == null ? previous == null : prompt.accept(hostPort, fp, previous);
        if (!ok) {
            socket.close();
            throw new IOException(previous == null ? "Conexão cancelada: código da mesa não confirmado."
                    : "O CÓDIGO DA MESA MUDOU desde a última vez -- conexão recusada (pode ser alguém no meio do caminho).");
        }
        remember(hostPort, fp);
    }
}
