package com.vttcaseiro.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Ponte do navegador: HTTP, WebSocket e a traducao das rolagens da ficha. */
class WebGatewayTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 6000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    /** GET simples: devolve "codigo corpo". */
    private static String httpGet(int port, String path) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            String all = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return all.split(" ")[1] + " " + all.substring(all.indexOf("\r\n\r\n") + 4);
        }
    }

    /** Cliente WebSocket minimo (o que o navegador faz por baixo). */
    static final class WsClient implements AutoCloseable {
        final Socket socket;
        final OutputStream out;
        final List<String> got = new CopyOnWriteArrayList<>();

        WsClient(int port) throws Exception {
            this(new Socket("localhost", port));
        }

        /** Sobre um socket ja aberto (ex.: TLS, pra testar o wss:// do navegador). */
        WsClient(Socket s) throws Exception {
            socket = s;
            out = socket.getOutputStream();
            out.write(("GET /ws HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            InputStream in = socket.getInputStream();
            WebGateway.Request resp = WebGateway.readRequest(in); // a resposta tem o mesmo formato de cabecalho
            assertEquals("101", resp.path());
            assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", resp.headers().get("sec-websocket-accept"));
            Thread t = new Thread(() -> {
                try {
                    String m;
                    while ((m = WebGateway.readMessage(in, new ByteArrayOutputStream())) != null) got.add(m);
                } catch (Exception ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void send(String text) throws Exception {
            byte[] p = text.getBytes(StandardCharsets.UTF_8);
            byte[] mask = {9, 8, 7, 6};
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(0x81);
            if (p.length < 126) {
                f.write(0x80 | p.length);
            } else {
                f.write(0x80 | 126);
                f.write(p.length >>> 8);
                f.write(p.length & 0xFF);
            }
            f.write(mask);
            for (int i = 0; i < p.length; i++) f.write(p[i] ^ mask[i % 4]);
            synchronized (out) {
                out.write(f.toByteArray());
                out.flush();
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    void chaveDoHandshakeEQuadrosDoWebSocket() throws Exception {
        // Exemplo da RFC 6455.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebGateway.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
        // Quadro mascarado (como o navegador manda), em dois pedacos (continuacao).
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        byte[] mask = {1, 2, 3, 4};
        for (String part : new String[]{"ola, ", "mesa"}) {
            boolean last = part.equals("mesa");
            in.write((last ? 0x80 : 0) | (part.startsWith("olá") ? 0x1 : 0x0));
            byte[] p = part.getBytes(StandardCharsets.UTF_8);
            in.write(0x80 | p.length);
            in.write(mask);
            for (int i = 0; i < p.length; i++) in.write(p[i] ^ mask[i % 4]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals("ola, mesa", WebGateway.readMessage(new ByteArrayInputStream(in.toByteArray()), out));
        // Resposta grande (> 125 bytes) usa o tamanho estendido.
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        WebGateway.writeFrame(frame, 0x1, "x".repeat(300).getBytes(StandardCharsets.UTF_8));
        byte[] f = frame.toByteArray();
        assertEquals(0x81, f[0] & 0xFF);
        assertEquals(126, f[1] & 0xFF);
        assertEquals(300, ((f[2] & 0xFF) << 8) | (f[3] & 0xFF));
    }

    @Test
    void navegadorEntraComSenhaRolaPelaFichaEMudaOPv() throws Exception {
        SceneModel scene = new SceneModel();
        TokenModel elora = new TokenModel("Elora", null);
        elora.setCharacterSheetId("ficha-elora");
        scene.addToken(elora);
        GameServer server = new GameServer(0, scene, null, "segredo", null);
        server.start();
        WebGateway gateway = new WebGateway(0, server.getPort(), null);
        gateway.start();
        Priority3NetTest.Rec gm = new Priority3NetTest.Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        WsClient ws = null;
        try {
            String page = httpGet(gateway.getPort(), "/");
            assertTrue(page.startsWith("200 "), page.substring(0, Math.min(40, page.length())));
            assertTrue(page.contains("Customizr"));
            assertTrue(httpGet(gateway.getPort(), "/asset/..%2F..%2Fsegredo").startsWith("404"),
                    "id fora do formato nao serve arquivo");

            gmC.connect("localhost", server.getPort(), "Mestre", true, "segredo", null);
            waitFor(() -> gm.scene != null, "mestre entrou");

            ws = new WsClient(gateway.getPort());
            List<String> got = ws.got;
            waitFor(() -> got.stream().anyMatch(s -> s.contains("\"challenge\"")), "desafio");
            String challenge = JsonParser.parseString(got.stream().filter(s -> s.contains("\"challenge\"")).findFirst()
                    .orElseThrow()).getAsJsonObject().get("challenge").getAsString();
            ws.send("{\"type\":\"hello\",\"player\":\"Ana\",\"playerId\":\"web-ana\",\"isGm\":false,"
                    + "\"protocolVersion\":3,\"passwordProof\":\"" + PasswordProof.prove("segredo", challenge) + "\"}");
            waitFor(() -> got.stream().anyMatch(s -> s.contains("\"scene_sync\"")), "entrou pelo navegador");

            // Mestre da a ficha pra Ana: chega no navegador com os numeros calculados.
            CharacterSheet sheet = new CharacterSheet("Elora");
            java.lang.reflect.Field id = CharacterSheet.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(sheet, "ficha-elora");
            sheet.setOwnerPlayerId("web-ana");
            sheet.setDexterity(16);
            sheet.setHpMax(20);
            sheet.setHpCurrent(20);
            sheet.setArmorClass(15);
            sheet.setEffects(List.of("Bencao", "Escudo"));
            Attack rapier = new Attack();
            rapier.setName("Rapieira");
            rapier.setAttackBonusOrDc("+5");
            rapier.setDamageAndType("1d8+3 perfurante");
            sheet.getAttacks().add(rapier);
            gmC.sendCharacterSync(sheet);
            waitFor(() -> got.stream().anyMatch(s -> s.contains("\"derived\"")), "ficha com numeros");
            JsonObject derived = JsonParser.parseString(got.stream().filter(s -> s.contains("\"derived\"")).findFirst()
                    .orElseThrow()).getAsJsonObject().getAsJsonObject("derived");
            assertEquals(20, derived.get("ac").getAsInt(), "CA 15 + Escudo 5");
            assertEquals(3, derived.get("initiative").getAsInt());
            assertEquals(5, derived.getAsJsonArray("attacks").get(0).getAsJsonObject().get("bonus").getAsInt());

            ws.send("{\"type\":\"web_roll\",\"sheetId\":\"ficha-elora\",\"rollKind\":\"attack\",\"index\":0,\"mode\":\"normal\"}");
            waitFor(() -> gm.rolls.stream().anyMatch(r -> r.contains("Ataque: Rapieira")), "rolagem chegou no mestre");
            assertTrue(gm.rolls.stream().anyMatch(r -> r.startsWith("Ana|") && r.contains("Bencao")),
                    "efeito da ficha entra na rolagem: " + gm.rolls);

            ws.send("{\"type\":\"web_hp\",\"sheetId\":\"ficha-elora\",\"amount\":7,\"heal\":false}");
            waitFor(() -> gm.sheets.stream().anyMatch(s -> Integer.valueOf(13).equals(s.getHpCurrent())), "PV chegou no mestre");

            // Dona da ficha ligada move o token pelo navegador.
            ws.send("{\"type\":\"move_token\",\"tokenId\":\"" + elora.getId() + "\",\"x\":140,\"y\":70}");
            waitFor(() -> got.stream().anyMatch(s -> s.contains("\"move_token\"") && s.contains("140")), "movimento confirmado");
        } finally {
            if (ws != null) ws.close();
            gmC.disconnect();
            gateway.stop();
            server.stop();
        }
    }

    @Test
    void rolagemDaFichaTraduzidaComCondicoes() {
        CharacterSheet s = new CharacterSheet("Brok");
        s.setStrength(18);
        s.setConditions(List.of("POISONED"));
        java.util.Map<String, CharacterSheet> sheets = java.util.Map.of(s.getId(), s);
        JsonObject o = JsonParser.parseString("{\"type\":\"web_roll\",\"sheetId\":\"" + s.getId()
                + "\",\"rollKind\":\"ability\",\"ability\":\"STRENGTH\",\"mode\":\"normal\",\"hidden\":true}").getAsJsonObject();
        NetMessage m = WebGateway.webRoll(o, sheets);
        assertNotNull(m);
        assertEquals("disadvantage", m.rollMode, "envenenado = desvantagem em teste");
        assertEquals(4, m.modifier);
        assertTrue(m.label.contains("Teste de FOR"));
        assertTrue(Boolean.TRUE.equals(m.hidden));
        assertNull(WebGateway.webRoll(JsonParser.parseString("{\"sheetId\":\"outra\",\"rollKind\":\"ability\"}")
                .getAsJsonObject(), sheets), "ficha desconhecida nao rola");
    }
}
