package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Mesa criptografada: certificado proprio, codigo da mesa conferido e lembrado. */
class TlsNetTest {

    @AfterEach
    void restore() {
        TlsSupport.setBaseDir(Path.of(System.getProperty("user.home"), ".vttcaseiro", "tls"));
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 8000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void conexaoCriptografadaComCodigoDaMesa(@TempDir Path dir) throws Exception {
        TlsSupport.setBaseDir(dir);
        javax.net.ssl.SSLContext ctx = TlsSupport.hostContext();
        String fp = TlsSupport.hostFingerprint();
        assertEquals(fp, TlsSupport.hostFingerprint(), "o certificado e gerado uma vez e reaproveitado");
        assertEquals(19, TlsSupport.shortCode(fp).length(), "AB12 CD34 EF56 7890");

        GameServer server = new GameServer(0, new SceneModel(), null, "segredo", null);
        server.useTls(ctx);
        server.start();
        WebGateway gateway = new WebGateway(0, server.getPort(), null);
        gateway.useTls(ctx, true);
        gateway.start();
        String hostPort = "localhost:" + server.getPort();
        List<String> prompts = new CopyOnWriteArrayList<>();
        Priority3NetTest.Rec ana = new Priority3NetTest.Rec();
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            // Primeira vez: pergunta com o codigo; aceita e lembra.
            anaC.useTls((hp, f, prev) -> { prompts.add(f + "|" + prev); return true; });
            anaC.connect("localhost", server.getPort(), "Ana", false, "segredo", null);
            waitFor(() -> ana.scene != null, "Ana entrou pela conexao criptografada");
            assertEquals(List.of(fp + "|null"), prompts);
            assertEquals(fp, TlsSupport.known(hostPort));
            anaC.disconnect();

            // Segunda vez: codigo igual, nao pergunta de novo.
            Priority3NetTest.Rec again = new Priority3NetTest.Rec();
            GameClient againC = new GameClient(again, null, "ana");
            againC.useTls((hp, f, prev) -> { prompts.add("de novo"); return true; });
            againC.connect("localhost", server.getPort(), "Ana", false, "segredo", null);
            waitFor(() -> again.scene != null, "reconectou");
            assertEquals(1, prompts.size());
            againC.disconnect();

            // Codigo lembrado diferente (alguem no meio): recusa se nao aceitar.
            TlsSupport.remember(hostPort, "AA:BB");
            GameClient mitm = new GameClient(new Priority3NetTest.Rec(), null, "ana");
            mitm.useTls((hp, f, prev) -> { prompts.add("mudou:" + prev); return false; });
            IOException ex = assertThrows(IOException.class,
                    () -> mitm.connect("localhost", server.getPort(), "Ana", false, "segredo", null));
            assertTrue(ex.getMessage().contains("MUDOU"), ex.getMessage());
            assertTrue(prompts.contains("mudou:AA:BB"));

            // Cliente sem TLS numa mesa com TLS nao entra.
            Priority3NetTest.Rec plain = new Priority3NetTest.Rec();
            GameClient plainC = new GameClient(plain, null, "beto");
            try {
                plainC.connect("localhost", server.getPort(), "Beto", false, "segredo", null);
            } catch (IOException ignored) {
                // tambem serve
            }
            Thread.sleep(500);
            assertNull(plain.scene, "sem criptografia nao recebe a mesa");
            plainC.disconnect();

            // Navegador: a pagina vem por HTTPS.
            try (SSLSocket s = (SSLSocket) TlsSupport.trustAllClient().getSocketFactory()
                    .createSocket("localhost", gateway.getPort())) {
                s.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                String resp = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(resp.startsWith("HTTP/1.1 200"), resp.substring(0, Math.min(40, resp.length())));
                assertEquals(fp, TlsSupport.fingerprint(s.getSession().getPeerCertificates()[0]));
            }
        } finally {
            gateway.stop();
            server.stop();
        }
    }

    @Test
    void navegadorPorHttpsEntraEImportaFicha(@TempDir Path dir) throws Exception {
        TlsSupport.setBaseDir(dir);
        javax.net.ssl.SSLContext ctx = TlsSupport.hostContext();
        GameServer server = new GameServer(0, new SceneModel());
        server.useTls(ctx);
        server.start();
        WebGateway gateway = new WebGateway(0, server.getPort(), null);
        gateway.useTls(ctx, true);
        gateway.start();
        try (WebGatewayTest.WsClient ws = new WebGatewayTest.WsClient(
                TlsSupport.trustAllClient().getSocketFactory().createSocket("localhost", gateway.getPort()))) {
            List<String> got = ws.got;
            waitFor(() -> got.stream().anyMatch(m -> m.contains("\"challenge\"")), "desafio pelo wss");
            ws.send("{\"type\":\"hello\",\"player\":\"Rin\",\"playerId\":\"web-rin\",\"isGm\":false,\"protocolVersion\":3}");
            waitFor(() -> got.stream().anyMatch(m -> m.contains("\"scene_sync\"")), "entrou na mesa por https/wss");

            // Importa uma ficha de Tormenta20 pelo navegador: volta como ficha DELA, com os testes calculados.
            com.vttcaseiro.character.CharacterSheet t20 = new com.vttcaseiro.character.CharacterSheet("Aldric");
            com.vttcaseiro.character.GameSystem.TORMENTA20.initialize(t20);
            t20.getCustomAttributes().put("FOR", 16);
            t20.getCustomSkills().put("Luta", 1);
            String b64 = java.util.Base64.getEncoder().encodeToString(
                    com.vttcaseiro.library.LibraryStorage.characterToJson(t20).getBytes(StandardCharsets.UTF_8));
            ws.send("{\"type\":\"web_import\",\"name\":\"aldric.json\",\"data\":\"" + b64 + "\"}");
            waitFor(() -> got.stream().anyMatch(m -> m.contains("\"character_sync\"") && m.contains("Aldric")), "ficha voltou");
            String sync = got.stream().filter(m -> m.contains("\"character_sync\"") && m.contains("Aldric")).findFirst().orElseThrow();
            assertTrue(sync.contains("\"ownerPlayerId\":\"web-rin\""), "a ficha e da jogadora que importou");
            assertTrue(sync.contains("\"system\":{\"name\":\"Tormenta20\""), "o navegador recebe a ficha de T20 pronta");
            assertTrue(sync.contains("1d20+5"), "Luta: metade do nivel 0 + FOR 3 + treino 2");
        } finally {
            gateway.stop();
            server.stop();
        }
    }

    @Test
    void clienteComTlsNumaMesaSemTlsAvisa(@TempDir Path dir) throws Exception {
        TlsSupport.setBaseDir(dir);
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        try {
            GameClient c = new GameClient(new Priority3NetTest.Rec(), null, "x");
            c.useTls(null);
            IOException ex = assertThrows(IOException.class, () -> c.connect("localhost", server.getPort(), "X", false));
            assertTrue(ex.getMessage().contains("criptografada"), ex.getMessage());
        } finally {
            server.stop();
        }
        assertFalse(Files.exists(dir.resolve("host.p12")), "quem so conecta nao gera certificado");
    }
}
