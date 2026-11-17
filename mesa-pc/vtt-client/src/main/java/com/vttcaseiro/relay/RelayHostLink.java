package com.vttcaseiro.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lado do MESTRE: mantem a mesa registrada na ponte e, a cada jogador que chega
 * ({@code OPEN}), abre uma conexao nova com a ponte e entrega ela pro servidor do jogo
 * (ou pro servidor do navegador), como se o jogador tivesse conectado direto.
 * Se a ligacao cair, tenta de novo sozinho.
 */
public final class RelayHostLink {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final String relayHost;
    private final int relayPort;
    private final String code;
    private final String secret;
    private final BiConsumer<Socket, String> gameAcceptor;
    private final BiConsumer<Socket, String> webAcceptor;
    private final Consumer<String> status;
    private volatile boolean running;
    private volatile Socket control;

    /**
     * @param gameAcceptor recebe (conexao ja ligada ao jogador, IP real dele) pro servidor do jogo
     * @param webAcceptor  idem pro navegador (null = sem jogadores pelo navegador)
     * @param status       mensagens pra tela ("Conectado a ponte", "Reconectando...")
     */
    public RelayHostLink(String relayHost, int relayPort, String code, String secret,
                         BiConsumer<Socket, String> gameAcceptor, BiConsumer<Socket, String> webAcceptor,
                         Consumer<String> status) {
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.code = code;
        this.secret = secret;
        this.gameAcceptor = gameAcceptor;
        this.webAcceptor = webAcceptor;
        this.status = status == null ? s -> { } : status;
    }

    /** Codigo curto pra passar aos jogadores, ex. "K7P-Q2X" (sem letras que confundem). */
    public static String newCode() {
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i == 3) b.append('-');
            b.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }

    public static String newSecret() {
        SecureRandom r = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 32; i++) b.append(chars.charAt(r.nextInt(chars.length())));
        return b.toString();
    }

    public String getCode() {
        return code;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Registra a mesa (espera a resposta da ponte) e segue escutando em segundo plano.
     * @throws IOException se nao conseguiu falar com a ponte ou ela recusou (ex.: codigo de outro mestre)
     */
    public void start() throws IOException {
        running = true;
        Socket first = register();
        Thread.ofPlatform().daemon().name("ponte-mestre").start(() -> loop(first));
    }

    public void stop() {
        running = false;
        RelayServer.closeQuietly(control);
    }

    private Socket register() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(relayHost, relayPort), 8000);
        s.setSoTimeout(RelayServer.HANDSHAKE_TIMEOUT_MS);
        write(s, "CUSTOMIZR-HOST " + code + " " + secret);
        String answer = RelayServer.readLineUtf8(s.getInputStream(), 512);
        if (answer == null || !answer.equals("OK")) {
            RelayServer.closeQuietly(s);
            String why = answer != null && answer.startsWith("ERR ") ? answer.substring(4) : "a ponte nao respondeu";
            throw new IOException(why);
        }
        s.setSoTimeout(RelayServer.PING_MS * 3);
        control = s;
        status.accept("Mesa aberta na ponte " + relayHost + ":" + relayPort + " (código " + code + ").");
        return s;
    }

    private void loop(Socket s) {
        long backoff = 2000;
        while (running) {
            try {
                listen(s);
            } catch (IOException ex) {
                // caiu: tenta de novo abaixo
            }
            RelayServer.closeQuietly(s);
            if (!running) return;
            status.accept("Ligação com a ponte caiu; tentando de novo...");
            while (running) {
                try {
                    Thread.sleep(backoff);
                    s = register();
                    backoff = 2000;
                    break;
                } catch (IOException ex) {
                    backoff = Math.min(30_000, backoff * 2);
                    if (ex.getMessage() != null && ex.getMessage().contains("outro mestre")) {
                        status.accept("A ponte recusou: " + ex.getMessage());
                        running = false;
                        return;
                    }
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
    }

    private void listen(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        String line;
        while (running && (line = RelayServer.readLine(in, 512)) != null) {
            if (line.equals("PING")) {
                write(s, "PONG");
            } else if (line.startsWith("OPEN ")) {
                String[] p = line.split(" ");
                if (p.length >= 3) {
                    String id = p[1], kind = p[2], ip = p.length > 3 ? p[3] : "?";
                    Thread.ofVirtual().start(() -> openTunnel(id, kind, ip));
                }
            }
        }
    }

    private void openTunnel(String id, String kind, String ip) {
        BiConsumer<Socket, String> target = "WEB".equals(kind) ? webAcceptor : gameAcceptor;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(relayHost, relayPort), 8000);
            s.setSoTimeout(RelayServer.HANDSHAKE_TIMEOUT_MS);
            if (target == null) {
                // sem servidor do navegador aqui: responde na hora (em vez da ponte esperar 15 s)
                write(s, "CUSTOMIZR-ACCEPT " + code + " " + id + " " + secret);
                String ok = RelayServer.readLine(s.getInputStream(), 64);
                if ("OK".equals(ok)) {
                    s.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain; charset=utf-8\r\n"
                            + "Connection: close\r\n\r\nO mestre está sem a parte do navegador ligada.").getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                }
                RelayServer.closeQuietly(s);
                return;
            }
            write(s, "CUSTOMIZR-ACCEPT " + code + " " + id + " " + secret);
            String ok = RelayServer.readLine(s.getInputStream(), 64); // byte a byte: nada do jogo e lido aqui
            if (!"OK".equals(ok)) {
                RelayServer.closeQuietly(s);
                return;
            }
            s.setSoTimeout(0);
            s.setTcpNoDelay(true);
            target.accept(s, ip);
        } catch (IOException | RuntimeException ex) {
            RelayServer.closeQuietly(s);
        }
    }

    private static void write(Socket s, String line) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    // ------------------------------------------------------------------ lado do jogador

    /**
     * Lado do JOGADOR: conecta na ponte e pede a mesa pelo codigo. Devolve a conexao ja ligada
     * ao mestre (dai o jogo segue normal, inclusive o TLS de ponta a ponta).
     */
    public static Socket join(String relayHost, int relayPort, String code) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(relayHost, relayPort), 8000);
        s.setSoTimeout(RelayServer.PAIR_TIMEOUT_MS + 5000);
        write(s, "CUSTOMIZR-JOIN " + code.trim().toUpperCase());
        String answer = RelayServer.readLineUtf8(s.getInputStream(), 512);
        if (!"OK".equals(answer)) {
            RelayServer.closeQuietly(s);
            throw new IOException(answer != null && answer.startsWith("ERR ") ? answer.substring(4)
                    : "a ponte não respondeu");
        }
        s.setSoTimeout(0);
        return s;
    }
}
