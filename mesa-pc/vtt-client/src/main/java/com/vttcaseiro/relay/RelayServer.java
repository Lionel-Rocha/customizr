package com.vttcaseiro.relay;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ponte do Customizr: roda num computador com uma porta aberta pra internet (um VPS barato,
 * por exemplo) e liga jogadores a mesa do mestre sem ninguem precisar abrir porta no roteador.
 * O mestre continua rodando a mesa no PC dele (os dados ficam la); a ponte so repassa os bytes.
 *
 * <p>Protocolo (uma linha de texto, depois bytes crus):</p>
 * <ul>
 *   <li>{@code CUSTOMIZR-HOST <codigo> <segredo>}: o programa do mestre registra a mesa e deixa
 *       essa conexao aberta. A ponte manda {@code OPEN <id> GAME|WEB <ip>} quando chega alguem
 *       e {@code PING} de tempos em tempos.</li>
 *   <li>{@code CUSTOMIZR-ACCEPT <codigo> <id> <segredo>}: o mestre abre uma conexao nova pra
 *       cada jogador; a ponte responde {@code OK} e liga as duas pontas.</li>
 *   <li>{@code CUSTOMIZR-JOIN <codigo>}: o programa do jogador pede a mesa; recebe {@code OK}
 *       (e dai fala o protocolo do jogo normalmente, inclusive TLS de ponta a ponta) ou {@code ERR ...}.</li>
 *   <li>Navegador: {@code http://ponte:porta/?mesa=CODIGO} vira um cookie e cada pedido HTTP/WebSocket
 *       e repassado pro mestre.</li>
 * </ul>
 * So usa o Java padrao (sem JavaFX), entao roda em qualquer servidor com Java 21+:
 * {@code java -cp customizr.jar com.vttcaseiro.relay.RelayServer 55070}.
 */
public final class RelayServer implements Closeable {

    public static final int DEFAULT_PORT = 55070;
    static final int MAX_MESAS = 300;
    static final int MAX_PENDING_PER_MESA = 64;
    static final int MAX_TUNNELS_PER_MESA = 200;
    static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    static final int PAIR_TIMEOUT_MS = 15_000;
    static final int PING_MS = 25_000;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9-]{4,16}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9]{16,64}");

    private final ServerSocket server;
    private final Map<String, Mesa> mesas = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running = true;

    private static final class Mesa {
        final String code;
        final String secret;
        final Socket control;
        final OutputStream controlOut;
        final Map<String, Pending> pending = new ConcurrentHashMap<>();
        final AtomicInteger tunnels = new AtomicInteger();

        Mesa(String code, String secret, Socket control) throws IOException {
            this.code = code;
            this.secret = secret;
            this.control = control;
            this.controlOut = control.getOutputStream();
        }

        synchronized boolean sendLine(String line) {
            try {
                controlOut.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
                controlOut.flush();
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private record Pending(CompletableFuture<Socket> host) {
    }

    public RelayServer(int port) throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
    }

    public int getPort() {
        return server.getLocalPort();
    }

    public int mesaCount() {
        return mesas.size();
    }

    public void start() {
        Thread accept = Thread.ofPlatform().daemon().name("ponte-accept").start(this::acceptLoop);
        Thread.ofPlatform().daemon().name("ponte-ping").start(() -> {
            while (running) {
                try {
                    Thread.sleep(PING_MS);
                } catch (InterruptedException ex) {
                    return;
                }
                for (Mesa m : mesas.values()) {
                    if (!m.sendLine("PING")) dropMesa(m);
                }
            }
        });
        if (accept == null) throw new IllegalStateException();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                Thread.ofVirtual().start(() -> handle(s));
            } catch (IOException ex) {
                if (!running) return;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        closeQuietly(server);
        for (Mesa m : mesas.values()) closeQuietly(m.control);
        mesas.clear();
    }

    // ------------------------------------------------------------------ conexoes

    private void handle(Socket s) {
        try {
            s.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            InputStream in = s.getInputStream();
            String first = readLine(in, 2048);
            if (first == null) {
                closeQuietly(s);
                return;
            }
            if (first.startsWith("CUSTOMIZR-HOST ")) {
                host(s, in, first.split(" "));
            } else if (first.startsWith("CUSTOMIZR-ACCEPT ")) {
                accept(s, first.split(" "));
            } else if (first.startsWith("CUSTOMIZR-JOIN ")) {
                join(s, first.split(" "));
            } else if (first.matches("^(GET|POST|HEAD|PUT|OPTIONS) \\S+ HTTP/1\\.[01]$")) {
                web(s, in, first);
            } else {
                closeQuietly(s);
            }
        } catch (IOException | RuntimeException ex) {
            closeQuietly(s);
        }
    }

    private void host(Socket s, InputStream in, String[] parts) throws IOException {
        if (parts.length != 3 || !CODE.matcher(parts[1]).matches() || !SECRET.matcher(parts[2]).matches()) {
            reply(s, "ERR pedido inválido");
            closeQuietly(s);
            return;
        }
        String code = parts[1];
        Mesa old = mesas.get(code);
        if (old != null && !old.secret.equals(parts[2])) {
            reply(s, "ERR esse código de mesa já está em uso por outro mestre");
            closeQuietly(s);
            return;
        }
        if (old == null && mesas.size() >= MAX_MESAS) {
            reply(s, "ERR a ponte está cheia");
            closeQuietly(s);
            return;
        }
        if (old != null) dropMesa(old); // o mesmo mestre reconectando
        Mesa m = new Mesa(code, parts[2], s);
        mesas.put(code, m);
        m.sendLine("OK");
        s.setSoTimeout(PING_MS * 3);
        try {
            // Linhas do mestre (PONG) so mantem a conexao viva; quando ela cai, a mesa sai da ponte.
            while (running && readLine(in, 256) != null) {
                // nada
            }
        } catch (SocketTimeoutException ex) {
            // mestre sumiu
        } finally {
            if (mesas.get(code) == m) dropMesa(m);
        }
    }

    private void dropMesa(Mesa m) {
        mesas.remove(m.code, m);
        closeQuietly(m.control);
        for (Pending p : m.pending.values()) p.host().completeExceptionally(new IOException("mesa fechou"));
        m.pending.clear();
    }

    private void accept(Socket s, String[] parts) throws IOException {
        Mesa m = parts.length == 4 ? mesas.get(parts[1]) : null;
        if (m == null || !m.secret.equals(parts[3])) {
            closeQuietly(s);
            return;
        }
        Pending p = m.pending.remove(parts[2]);
        if (p == null) {
            closeQuietly(s);
            return;
        }
        s.setSoTimeout(0);
        if (!p.host().complete(s)) closeQuietly(s);
    }

    /** Pede ao mestre uma conexao pra este jogador e espera ela chegar. */
    private Socket openTunnel(Mesa m, String kind, Socket player) throws IOException {
        if (m.pending.size() >= MAX_PENDING_PER_MESA || m.tunnels.get() >= MAX_TUNNELS_PER_MESA) {
            throw new IOException("mesa cheia");
        }
        String id = Long.toHexString(random.nextLong() & Long.MAX_VALUE);
        Pending p = new Pending(new CompletableFuture<>());
        m.pending.put(id, p);
        String ip = player.getInetAddress() == null ? "?" : player.getInetAddress().getHostAddress();
        if (!m.sendLine("OPEN " + id + " " + kind + " " + ip)) {
            m.pending.remove(id);
            dropMesa(m);
            throw new IOException("o mestre desconectou");
        }
        try {
            return p.host().get(PAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            m.pending.remove(id);
            throw new IOException("o mestre não respondeu");
        }
    }

    private void join(Socket s, String[] parts) throws IOException {
        Mesa m = parts.length == 2 ? mesas.get(parts[1].trim().toUpperCase()) : null;
        if (m == null) {
            reply(s, "ERR mesa não encontrada nesta ponte (o mestre abriu a mesa? o código está certo?)");
            closeQuietly(s);
            return;
        }
        Socket hostSide;
        try {
            hostSide = openTunnel(m, "GAME", s);
        } catch (IOException ex) {
            reply(s, "ERR " + ex.getMessage());
            closeQuietly(s);
            return;
        }
        reply(hostSide, "OK");
        reply(s, "OK");
        s.setSoTimeout(0);
        pipe(m, s, hostSide, null);
    }

    // ------------------------------------------------------------------ navegador

    private static final Pattern QUERY_MESA = Pattern.compile("[?&]mesa=([A-Za-z0-9-]{4,16})");
    private static final Pattern COOKIE_MESA = Pattern.compile("(?i)customizr_mesa=([A-Z0-9-]{4,16})");

    private void web(Socket s, InputStream in, String requestLine) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write((requestLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        String cookieMesa = null;
        while (true) {
            String line = readLine(in, 8192);
            if (line == null) {
                closeQuietly(s);
                return;
            }
            head.write((line + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            if (head.size() > 32 * 1024) {
                closeQuietly(s);
                return;
            }
            if (line.isEmpty()) break;
            if (line.regionMatches(true, 0, "Cookie:", 0, 7)) {
                Matcher c = COOKIE_MESA.matcher(line);
                if (c.find()) cookieMesa = c.group(1).toUpperCase();
            }
        }
        String target = requestLine.split(" ")[1];
        Matcher q = QUERY_MESA.matcher(target);
        if (q.find()) {
            // /?mesa=CODIGO: guarda num cookie e manda pra pagina da mesa (os pedidos seguintes levam o cookie)
            String code = q.group(1).toUpperCase();
            raw(s, "HTTP/1.1 302 Found\r\nSet-Cookie: customizr_mesa=" + code + "; Path=/; SameSite=Lax\r\n"
                    + "Location: /\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            closeQuietly(s);
            return;
        }
        Mesa m = cookieMesa == null ? null : mesas.get(cookieMesa);
        if (m == null) {
            String msg = cookieMesa == null ? "" : "<p style='color:#E98D87'>Mesa " + cookieMesa
                    + " não está aberta agora nesta ponte.</p>";
            String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
                    + "<title>Customizr - ponte</title><body style='background:#1B1411;color:#F6E9D3;font:16px Segoe UI,"
                    + "sans-serif;display:grid;place-items:center;height:90vh'><form style='background:#241B16;padding:24px;"
                    + "border-radius:12px;border:1px solid #45342A'><h2 style='color:#FFCF7F;font-family:Georgia'>Customizr</h2>"
                    + msg + "<p>Código da mesa (o mestre te passa):</p><input name=mesa autofocus required "
                    + "style='font-size:20px;text-transform:uppercase;padding:6px'> <button>Entrar</button></form>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            raw(s, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n");
            s.getOutputStream().write(body);
            s.getOutputStream().flush();
            closeQuietly(s);
            return;
        }
        Socket hostSide;
        try {
            hostSide = openTunnel(m, "WEB", s);
        } catch (IOException ex) {
            raw(s, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            closeQuietly(s);
            return;
        }
        reply(hostSide, "OK");
        s.setSoTimeout(0);
        pipe(m, s, hostSide, head.toByteArray());
    }

    // ------------------------------------------------------------------ util

    /** Liga as duas pontas ate uma fechar. {@code firstToHost} = bytes ja lidos do jogador. */
    private void pipe(Mesa m, Socket player, Socket host, byte[] firstToHost) {
        m.tunnels.incrementAndGet();
        try {
            if (firstToHost != null) {
                host.getOutputStream().write(firstToHost);
                host.getOutputStream().flush();
            }
            Thread up = Thread.ofVirtual().start(() -> copy(player, host));
            copy(host, player);
            up.join(2000);
        } catch (IOException | InterruptedException ex) {
            // fecha abaixo
        } finally {
            m.tunnels.decrementAndGet();
            closeQuietly(player);
            closeQuietly(host);
        }
    }

    private static void copy(Socket from, Socket to) {
        byte[] buf = new byte[16 * 1024];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
            // uma ponta caiu
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    /** Le uma linha (ate \n) byte a byte, sem ler nada alem dela. null = fim da conexao. */
    static String readLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (b.size() >= max) throw new IOException("linha grande demais");
            b.write(c);
        }
        if (c < 0 && b.size() == 0) return null;
        String line = b.toString(StandardCharsets.ISO_8859_1);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** Igual readLine, decodificando UTF-8 (respostas da ponte, com acentos). */
    static String readLineUtf8(InputStream in, int max) throws IOException {
        String raw = readLine(in, max);
        return raw == null ? null : new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private static void reply(Socket s, String line) throws IOException {
        raw(s, line + "\n");
    }

    private static void raw(Socket s, String text) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static void closeQuietly(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
            // nada
        }
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        RelayServer relay = new RelayServer(port);
        relay.start();
        System.out.println("Ponte do Customizr escutando na porta " + relay.getPort()
                + ". Os mestres usam \"Abrir mesa na ponte\" (aba Rede) com o endereço deste servidor.");
        Runtime.getRuntime().addShutdownHook(new Thread(relay::close));
        while (true) {
            Thread.sleep(60_000);
            System.out.println("mesas abertas: " + relay.mesaCount());
        }
    }
}
