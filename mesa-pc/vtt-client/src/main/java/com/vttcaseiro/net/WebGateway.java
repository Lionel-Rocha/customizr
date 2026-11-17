package com.vttcaseiro.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.EffectModifiers;
import com.vttcaseiro.character.RollAdjustment;
import com.vttcaseiro.character.RollKind;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.library.ImageAssetStore;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jogar pelo NAVEGADOR, sem instalar nada: quem hospeda abre tambem uma
 * porta HTTP (a do jogo + 1) que entrega a pagina do cliente web
 * ({@code /web/index.html}), as imagens ({@code /asset/<id>}) e um
 * WebSocket ({@code /ws}).
 * <p>
 * Cada WebSocket vira uma conexao TCP NORMAL com o {@link GameServer}
 * (linha JSON por mensagem, o mesmo protocolo do programa). Ou seja: senha
 * por desafio, permissoes, camada do mestre... tudo continua sendo decidido
 * pelo servidor de sempre; a ponte so traduz o transporte.
 * <p>
 * A ponte tambem faz duas coisas que o navegador nao sabe fazer sozinho
 * sem duplicar as regras:
 * <ul>
 *   <li>em {@code character_sync} acrescenta {@code derived} (CA, bonus de
 *   pericias e salvaguardas, ataques...) calculado pela {@link CharacterSheet};</li>
 *   <li>entende {@code web_roll} (rolar pela ficha: aplica condicoes, exaustao
 *   e efeitos como no programa) e {@code web_hp} (dano/cura na propria ficha
 *   com as regras de PV temporario, concentracao etc.).</li>
 * </ul>
 */
public class WebGateway {
    private static final Log log = Log.of(WebGateway.class);
    private static final Gson GSON = new Gson();
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME = 8 * 1024 * 1024;
    private static final int MAX_HEADER = 16 * 1024;

    private final int port;
    private final int gamePort;
    private final ImageAssetStore assets;
    private final com.vttcaseiro.library.AudioAssetStore audio;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    /** Tentativas de senha erradas por IP real do navegador (o servidor so ve 127.0.0.1). */
    private final LoginGuard loginGuard = new LoginGuard(System::currentTimeMillis);

    public WebGateway(int port, int gamePort, ImageAssetStore assets) {
        this.port = port;
        this.gamePort = gamePort;
        this.assets = assets;
        com.vttcaseiro.library.AudioAssetStore a = null;
        try {
            if (assets != null) a = com.vttcaseiro.library.AudioAssetStore.besideImages(assets);
        } catch (IOException ignored) {
            // sem pasta de sons: so nao serve audio
        }
        this.audio = a;
    }

    private javax.net.ssl.SSLContext tls;
    private boolean gameTls;

    /** HTTPS/WSS pro navegador e TLS ate o servidor do jogo. Antes de start(). */
    public void useTls(javax.net.ssl.SSLContext context, boolean gameUsesTls) {
        this.tls = context;
        this.gameTls = gameUsesTls;
    }

    public boolean isTls() {
        return tls != null;
    }

    public void start() throws IOException {
        serverSocket = tls == null ? new ServerSocket() : tls.getServerSocketFactory().createServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        Thread t = new Thread(this::acceptLoop, "web-gateway");
        t.setDaemon(true);
        t.start();
        log.info("Cliente web em http://<seu-ip>:" + getPort());
    }

    public int getPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (Socket s : openSockets) closeQuietly(s);
        openSockets.clear();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                openSockets.add(s);
                Thread t = new Thread(() -> {
                    try {
                        handle(s);
                    } catch (IOException | RuntimeException ex) {
                        // conexao caiu / pedido estranho: so fecha
                    } finally {
                        openSockets.remove(s);
                        closeQuietly(s);
                    }
                }, "web-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException ex) {
                if (running) log.warn("Erro aceitando conexão web: " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    record Request(String method, String path, Map<String, String> headers) {
    }

    static Request readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new IOException("conexão fechada");
            buf.write(b);
            if (buf.size() > MAX_HEADER) throw new IOException("cabecalho grande demais");
            state = (b == '\r' && (state == 0 || state == 2)) ? state + 1
                    : (b == '\n' && (state == 1 || state == 3)) ? state + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = buf.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) throw new IOException("pedido inválido");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }
        String path = first[1];
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        return new Request(first[0], path, headers);
    }

    /** Navegador que chegou pela ponte: mesma coisa que uma conexao direta (sempre sem TLS aqui). */
    public void acceptTunneled(Socket s, String realIp) {
        openSockets.add(s);
        Thread t = new Thread(() -> {
            try {
                handle(s, "ponte:" + realIp);
            } catch (IOException | RuntimeException ex) {
                // caiu
            } finally {
                openSockets.remove(s);
                closeQuietly(s);
            }
        }, "web-ponte");
        t.setDaemon(true);
        t.start();
    }

    private void handle(Socket s) throws IOException {
        handle(s, null);
    }

    private void handle(Socket s, String ipOverride) throws IOException {
        s.setSoTimeout(30_000);
        InputStream in = s.getInputStream();
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        Request req = readRequest(in);
        String ip = ipOverride != null ? ipOverride : s.getInetAddress() == null ? "?" : s.getInetAddress().getHostAddress();
        if ("/ws".equals(req.path()) && "websocket".equalsIgnoreCase(req.headers().getOrDefault("upgrade", ""))) {
            if (loginGuard.isBlocked(ip)) {
                respond(out, 429, "text/plain; charset=utf-8", "Muitas senhas erradas. Espere alguns minutos.".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String key = req.headers().get("sec-websocket-key");
            if (key == null) {
                respond(out, 400, "text/plain", "sem chave".getBytes(StandardCharsets.UTF_8));
                return;
            }
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey(key) + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            s.setSoTimeout(0);
            bridge(s, in, out, ip);
            return;
        }
        if (!"GET".equals(req.method())) {
            respond(out, 405, "text/plain", new byte[0]);
            return;
        }
        if ("/".equals(req.path()) || "/index.html".equals(req.path())) {
            try (InputStream page = WebGateway.class.getResourceAsStream("/web/index.html")) {
                if (page == null) {
                    respond(out, 500, "text/plain", "página não encontrada".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(out, 200, "text/html; charset=utf-8", page.readAllBytes());
            }
            return;
        }
        if (req.path().startsWith("/audio/")) {
            String id = req.path().substring(7);
            if (audio != null && com.vttcaseiro.library.AudioAssetStore.isValidId(id) && audio.exists(id)) {
                serveFile(out, audio.path(id), com.vttcaseiro.library.AudioAssetStore.contentType(id),
                        req.headers().get("range"));
            } else {
                respond(out, 404, "text/plain", new byte[0]);
            }
            return;
        }
        if (req.path().startsWith("/asset/")) {
            String id = req.path().substring(7);
            // So o formato de id do ImageAssetStore (hash + extensao): nada de "../".
            if (assets != null && id.matches("[0-9a-f]{64}(\\.[a-z0-9]{1,5})?")) {
                try {
                    respond(out, 200, contentType(id), assets.read(id));
                    return;
                } catch (IOException ignored) {
                    // cai no 404
                }
            }
            respond(out, 404, "text/plain", new byte[0]);
            return;
        }
        respond(out, 404, "text/plain; charset=utf-8", "não encontrado".getBytes(StandardCharsets.UTF_8));
    }

    /** Arquivo inteiro (200) ou so a faixa pedida (206 "bytes=ini-fim"), que o player de audio usa. */
    private static void serveFile(OutputStream out, java.nio.file.Path file, String type, String range) throws IOException {
        long size = java.nio.file.Files.size(file);
        long start = 0;
        long end = size - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            String[] r = range.substring(6).split("-", 2);
            try {
                if (!r[0].isBlank()) start = Long.parseLong(r[0].trim());
                if (r.length > 1 && !r[1].isBlank()) end = Math.min(size - 1, Long.parseLong(r[1].trim()));
                if (r[0].isBlank() && r.length > 1) { start = Math.max(0, size - Long.parseLong(r[1].trim())); end = size - 1; }
                partial = start <= end && start < size;
            } catch (NumberFormatException ignored) {
                start = 0;
                end = size - 1;
            }
            if (!partial) {
                out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size
                        + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                return;
            }
        }
        long length = end - start + 1;
        out.write(((partial ? "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes " + start + "-" + end + "/" + size + "\r\n"
                : "HTTP/1.1 200 OK\r\n") + "Content-Type: " + type + "\r\nAccept-Ranges: bytes\r\nContent-Length: " + length
                + "\r\nCache-Control: max-age=3600\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
            raf.seek(start);
            byte[] buf = new byte[64 * 1024];
            long left = length;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        }
        out.flush();
    }

    private static String contentType(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "application/octet-stream";
    }

    private static void respond(OutputStream out, int code, String type, byte[] body) throws IOException {
        String reason = switch (code) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 429 -> "Too Many Requests";
            default -> "Error";
        };
        out.write(("HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    static String acceptKey(String key) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(sha);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------
    // WebSocket <-> TCP do jogo
    // ------------------------------------------------------------------

    /** Le uma mensagem de texto inteira (junta continuacoes). null = fechou. */
    static String readMessage(InputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        while (true) {
            int b0 = in.read();
            if (b0 < 0) return null;
            int b1 = in.read();
            if (b1 < 0) return null;
            boolean fin = (b0 & 0x80) != 0;
            int op = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) len = ((long) readByte(in) << 8) | readByte(in);
            else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
            }
            if (len > MAX_FRAME || msg.size() + len > MAX_FRAME) throw new IOException("mensagem grande demais");
            byte[] mask = masked ? in.readNBytes(4) : null;
            byte[] payload = in.readNBytes((int) len);
            if (payload.length < len) return null;
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            switch (op) {
                case 0x8 -> {
                    writeFrame(out, 0x8, new byte[0]);
                    return null;
                }
                case 0x9 -> writeFrame(out, 0xA, payload);
                case 0xA -> { }
                case 0x0, 0x1 -> {
                    msg.write(payload);
                    if (fin) return msg.toString(StandardCharsets.UTF_8);
                }
                default -> { } // binario: ignora
            }
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("fim inesperado");
        return b;
    }

    static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        synchronized (out) {
            out.write(0x80 | opcode);
            int len = payload.length;
            if (len < 126) {
                out.write(len);
            } else if (len < 65536) {
                out.write(126);
                out.write(len >>> 8);
                out.write(len & 0xFF);
            } else {
                out.write(127);
                for (int i = 7; i >= 0; i--) out.write((int) ((long) len >>> (8 * i)) & 0xFF);
            }
            out.write(payload);
            out.flush();
        }
    }

    private void bridge(Socket ws, InputStream wsIn, OutputStream wsOut, String ip) throws IOException {
        try (Socket game = gameTls
                // Servidor do jogo e este mesmo processo: o certificado e o nosso.
                ? TlsSupport.trustAllClient().getSocketFactory().createSocket("127.0.0.1", gamePort)
                : new Socket("127.0.0.1", gamePort)) {
            openSockets.add(game);
            PrintWriter gameOut = new PrintWriter(new OutputStreamWriter(game.getOutputStream(), StandardCharsets.UTF_8), true);
            Map<String, CharacterSheet> sheets = new ConcurrentHashMap<>();
            WebSession session = new WebSession(line -> {
                synchronized (gameOut) {
                    gameOut.println(line);
                }
            }, json -> {
                try {
                    writeFrame(wsOut, 0x1, json.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // navegador saiu
                }
            });
            this.lastSession = session;
            Thread down = new Thread(() -> {
                try {
                    BoundedLineReader gameIn = new BoundedLineReader(
                            new java.io.InputStreamReader(game.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = gameIn.readLine()) != null) {
                        try {
                            session.fromServer(NetCodec.decode(line));
                        } catch (RuntimeException ex) {
                            // linha estranha: so repassa
                        }
                        String forBrowser = enrich(line, sheets, ip);
                        writeFrame(wsOut, 0x1, forBrowser.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException | RuntimeException ignored) {
                    // servidor caiu ou navegador saiu
                } finally {
                    closeQuietly(ws);
                }
            }, "web-down");
            down.setDaemon(true);
            down.start();
            String msg;
            try {
                while ((msg = readMessage(wsIn, wsOut)) != null) {
                    String line = translate(msg, sheets, session);
                    if (line == null) continue;
                    synchronized (gameOut) {
                        gameOut.println(line);
                    }
                }
            } finally {
                session.close();
            }
        } finally {
            closeQuietly(ws);
        }
    }

    /** Do servidor pro navegador: ficha ganha os numeros calculados; senha errada conta pro IP real. */
    String enrich(String line, Map<String, CharacterSheet> sheets, String ip) {
        if (line.contains("Senha da mesa incorreta")) loginGuard.failed(ip);
        if (!line.contains("\"character_sync\"")) return line;
        try {
            NetMessage msg = NetCodec.decode(line);
            if (msg.character == null || msg.character.getId() == null) return line;
            sheets.put(msg.character.getId(), msg.character);
            JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            obj.add("derived", derived(msg.character));
            return GSON.toJson(obj);
        } catch (RuntimeException ex) {
            return line;
        }
    }

    /** Ficha de Tormenta20 / Ordem Paranormal pro navegador: tudo ja calculado, com a expressao de cada teste. */
    public static JsonObject systemView(CharacterSheet s, com.vttcaseiro.character.GameSystem gs) {
        JsonObject v = new JsonObject();
        v.addProperty("name", gs.name());
        v.addProperty("id", gs.id());
        com.google.gson.JsonArray attrs = new com.google.gson.JsonArray();
        for (var a : gs.attrs()) {
            JsonObject o = new JsonObject();
            o.addProperty("key", a.key());
            o.addProperty("label", a.label());
            o.addProperty("value", gs.attr(s, a.key()));
            o.addProperty("test", gs.attrTest(s, a.key()));
            attrs.add(o);
        }
        v.add("attrs", attrs);
        com.google.gson.JsonArray res = new com.google.gson.JsonArray();
        for (String r : gs.resources()) {
            s.findResource(r).ifPresent(x -> {
                JsonObject o = new JsonObject();
                o.addProperty("name", r);
                o.addProperty("cur", x.getCurrent());
                o.addProperty("max", x.getMax());
                res.add(o);
            });
        }
        v.add("resources", res);
        com.google.gson.JsonArray skills = new com.google.gson.JsonArray();
        for (var d : gs.skills()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", d.name());
            o.addProperty("attr", d.attr());
            o.addProperty("trained", gs.training(s, d.name()) > 0);
            o.addProperty("test", gs.skillTest(s, d.name()));
            skills.add(o);
        }
        v.add("skills", skills);
        com.google.gson.JsonArray attacks = new com.google.gson.JsonArray();
        for (var a : s.getAttacks()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", a.getName());
            o.addProperty("test", gs.attackTest(s, a));
            a.damageExpression().ifPresent(dmg -> {
                o.addProperty("damage", dmg);
                o.addProperty("crit", com.vttcaseiro.dice.DiceRoller.doubleDice(dmg));
            });
            o.addProperty("notes", a.getNotes());
            attacks.add(o);
        }
        v.add("attacks", attacks);
        JsonObject fields = new JsonObject();
        s.getCustomFields().forEach((k, val) -> { if (!k.equals("Inventário")) fields.addProperty(k, val); });
        v.add("fields", fields);
        v.addProperty("initiative", gs.initiativeTest(s));
        return v;
    }

    static JsonObject derived(CharacterSheet s) {
        JsonObject d = new JsonObject();
        com.vttcaseiro.character.GameSystem gs = com.vttcaseiro.character.GameSystem.of(s);
        if (gs.isCustom()) d.add("system", systemView(s, gs));
        d.addProperty("ac", s.totalArmorClass());
        d.addProperty("initiative", s.initiative());
        d.addProperty("proficiency", s.proficiencyBonus());
        d.addProperty("passivePerception", s.passivePerception());
        d.addProperty("effects", EffectModifiers.describe(s.getEffects()));
        if (s.isSpellcaster()) {
            d.addProperty("spellAttack", s.spellAttackBonus());
            d.addProperty("spellDc", s.spellSaveDc());
        }
        JsonArray abilities = new JsonArray();
        for (Ability a : Ability.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.name());
            o.addProperty("abbr", a.abbreviationPt());
            o.addProperty("score", s.abilityScore(a));
            o.addProperty("mod", CharacterSheet.abilityModifier(s.abilityScore(a)));
            o.addProperty("save", s.savingThrowBonus(a));
            o.addProperty("saveProf", s.isSavingThrowProficient(a));
            abilities.add(o);
        }
        d.add("abilities", abilities);
        JsonArray skills = new JsonArray();
        for (Skill k : Skill.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", k.name());
            o.addProperty("name", k.getDisplayNamePt());
            o.addProperty("bonus", s.skillBonus(k));
            o.addProperty("prof", s.isSkillProficient(k));
            skills.add(o);
        }
        d.add("skills", skills);
        JsonArray attacks = new JsonArray();
        for (int i = 0; i < s.getAttacks().size(); i++) {
            Attack a = s.getAttacks().get(i);
            JsonObject o = new JsonObject();
            o.addProperty("index", i);
            o.addProperty("name", a.getName());
            o.addProperty("text", a.getAttackBonusOrDc());
            o.addProperty("damageText", a.getDamageAndType());
            if (a.attackBonus().isPresent()) o.addProperty("bonus", a.attackBonus().getAsInt());
            a.damageExpression().ifPresent(e -> o.addProperty("damage", e));
            attacks.add(o);
        }
        d.add("attacks", attacks);
        JsonArray resources = new JsonArray();
        for (com.vttcaseiro.character.Resource r : s.getResources()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", r.getName());
            o.addProperty("uses", r.usesText());
            resources.add(o);
        }
        d.add("resources", resources);
        WebSession.addSpells(d, s);
        return d;
    }

    /** Ultima sessao aberta (so pros testes olharem a copia da mesa). */
    volatile WebSession lastSession;

    String translate(String msg, Map<String, CharacterSheet> sheets) {
        return translate(msg, sheets, null);
    }

    /** Do navegador pro servidor: os pedidos "web_*" vao pras regras (WebSession); o resto passa direto. */
    String translate(String msg, Map<String, CharacterSheet> sheets, WebSession session) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(msg);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!parsed.isJsonObject()) return null;
        JsonObject o = parsed.getAsJsonObject();
        String type = str(o, "type");
        if (session != null) {
            switch (String.valueOf(type)) {
                case "hello" -> session.sawHello(o);
                case "web_roll" -> { session.webRoll(o); return null; }
                case "web_cast" -> { session.webCast(o); return null; }
                case "web_resource" -> { session.webResource(o); return null; }
                case "web_hp" -> { session.webHp(o); return null; }
                case "web_import" -> { session.webImport(o); return null; }
                case "web_gm" -> { session.webGm(o); return null; }
                default -> { }
            }
        }
        if ("web_roll".equals(type)) {
            NetMessage roll = webRoll(o, sheets);
            return roll == null ? null : NetCodec.encode(roll);
        }
        if ("web_hp".equals(type)) {
            CharacterSheet s = sheets.get(str(o, "sheetId"));
            if (s == null || !o.has("amount")) return null;
            int amount = Math.max(0, Math.min(10000, o.get("amount").getAsInt()));
            if (amount == 0) return null;
            if (o.has("heal") && o.get("heal").getAsBoolean()) s.heal(amount);
            else s.takeDamage(amount);
            return NetCodec.encode(NetMessage.characterSync(s));
        }
        // Linha unica (sem quebra) com o JSON como veio.
        return GSON.toJson(o);
    }

    static NetMessage webRoll(JsonObject o, Map<String, CharacterSheet> sheets) {
        CharacterSheet s = sheets.get(str(o, "sheetId"));
        if (s == null) return null;
        String kind = str(o, "rollKind");
        RollMode chosen = switch (String.valueOf(str(o, "mode"))) {
            case "advantage" -> RollMode.ADVANTAGE;
            case "disadvantage" -> RollMode.DISADVANTAGE;
            default -> RollMode.NORMAL;
        };
        boolean hidden = o.has("hidden") && o.get("hidden").getAsBoolean();
        Ability ability = null;
        try {
            if (o.has("ability")) ability = Ability.valueOf(str(o, "ability"));
        } catch (IllegalArgumentException ignored) {
        }
        String label;
        int mod;
        RollKind rk;
        Attack attack = null;
        if (o.has("index")) {
            int i = o.get("index").getAsInt();
            if (i >= 0 && i < s.getAttacks().size()) attack = s.getAttacks().get(i);
        }
        switch (String.valueOf(kind)) {
            case "ability" -> {
                if (ability == null) return null;
                rk = RollKind.ABILITY_CHECK;
                mod = CharacterSheet.abilityModifier(s.abilityScore(ability));
                label = "Teste de " + ability.abbreviationPt();
            }
            case "save" -> {
                if (ability == null) return null;
                rk = RollKind.SAVING_THROW;
                mod = s.savingThrowBonus(ability);
                label = "Salvaguarda de " + ability.abbreviationPt();
            }
            case "skill" -> {
                Skill skill;
                try {
                    skill = Skill.valueOf(str(o, "skill"));
                } catch (RuntimeException ex) {
                    return null;
                }
                rk = RollKind.ABILITY_CHECK;
                ability = skill.getAbility();
                mod = s.skillBonus(skill);
                label = skill.getDisplayNamePt();
            }
            case "initiative" -> {
                rk = RollKind.INITIATIVE;
                ability = Ability.DEXTERITY;
                mod = s.initiative();
                label = "Iniciativa";
            }
            case "death" -> {
                rk = RollKind.DEATH_SAVE;
                mod = 0;
                label = "Teste contra a morte";
            }
            case "spellAttack" -> {
                if (!s.isSpellcaster()) return null;
                rk = RollKind.ATTACK;
                mod = s.spellAttackBonus();
                label = "Ataque de magia";
            }
            case "attack" -> {
                if (attack == null || attack.attackBonus().isEmpty()) return null;
                rk = RollKind.ATTACK;
                mod = attack.attackBonus().getAsInt();
                label = "Ataque: " + attack.getName();
            }
            case "damage", "crit" -> {
                if (attack == null || attack.damageExpression().isEmpty()) return null;
                String expr = attack.damageExpression().get();
                if ("crit".equals(kind)) expr = com.vttcaseiro.dice.DiceRoller.doubleDice(expr);
                expr += EffectModifiers.damageSuffix(s.getEffects());
                NetMessage m = NetMessage.roll(s.getName(), expr);
                m.label = s.getName() + " - " + ("crit".equals(kind) ? "Dano CRITICO: " : "Dano: ") + attack.getName()
                        + " (" + attack.getDamageAndType() + ")";
                if (hidden) m.hidden = true;
                return m;
            }
            default -> {
                return null;
            }
        }
        RollAdjustment adj = s.rollAdjustment(rk, ability, chosen);
        int finalMod = mod + adj.modifierDelta();
        String modeText = adj.mode() == RollMode.NORMAL ? "" : " (" + adj.mode().labelPt() + ")";
        String title = s.getName() + " - " + label + modeText + adj.notesText() + (adj.autoFail() ? " -- FALHA AUTOMÁTICA" : "");
        String expr = finalMod == 0 ? "1d20" : "1d20" + (finalMod > 0 ? "+" + finalMod : String.valueOf(finalMod));
        NetMessage m = NetMessage.roll(s.getName(), expr);
        m.label = title;
        if (adj.mode() != RollMode.NORMAL) {
            m.rollMode = adj.mode() == RollMode.ADVANTAGE ? "advantage" : "disadvantage";
            m.modifier = finalMod;
        }
        if (hidden) m.hidden = true;
        return m;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
