package com.vttcaseiro.net;

import com.vttcaseiro.relay.RelayHostLink;
import com.vttcaseiro.relay.RelayServer;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Ponte: jogador entra na mesa do mestre sem conexao direta (os dois conectam na ponte). */
class RelayNetTest {

    @AfterEach
    void restore() {
        TlsSupport.setBaseDir(Path.of(System.getProperty("user.home"), ".vttcaseiro", "tls"));
    }

    static class Rec extends GameServerSecurityTest.Recorder {
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 8000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void jogadorEntraPelaPonteComOCodigo() throws Exception {
        SceneModel scene = new SceneModel();
        scene.setName("Cripta pela ponte");
        GameServer server = new GameServer(0, scene, null, "segredo", null);
        server.start();
        RelayServer relay = new RelayServer(0);
        relay.start();
        String code = RelayHostLink.newCode();
        String secret = RelayHostLink.newSecret();
        RelayHostLink link = new RelayHostLink("localhost", relay.getPort(), code, secret,
                server::acceptTunneled, null, s -> { });
        Rec ana = new Rec();
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            link.start();
            assertEquals(1, relay.mesaCount());
            anaC.useRelay(code.toLowerCase()); // codigo em minuscula tambem vale
            anaC.connect("localhost", relay.getPort(), "Ana", false, "segredo", null);
            waitFor(() -> ana.scene != null, "Ana recebeu a mesa pela ponte");
            assertEquals("Cripta pela ponte", ana.scene.getName());

            // Outro "mestre" tentando pegar o mesmo codigo e recusado.
            RelayHostLink thief = new RelayHostLink("localhost", relay.getPort(), code, RelayHostLink.newSecret(),
                    server::acceptTunneled, null, s -> { });
            IOException ex = assertThrows(IOException.class, thief::start);
            assertTrue(ex.getMessage().contains("outro mestre"), ex.getMessage());

            IOException none = assertThrows(IOException.class,
                    () -> RelayHostLink.join("localhost", relay.getPort(), "ZZZ-999"));
            assertTrue(none.getMessage().contains("não encontrada"), none.getMessage());

            // Senha errada pela ponte: o servidor conta pelo IP real (nao trata como conexao local).
            Rec beto = new Rec();
            GameClient betoC = new GameClient(beto, null, "beto");
            betoC.useRelay(code);
            try {
                betoC.connect("localhost", relay.getPort(), "Beto", false, "errada", null);
            } catch (IOException ignored) {
                // tambem serve
            }
            Thread.sleep(400);
            assertNull(beto.scene, "senha errada nao entra");
            betoC.disconnect();
        } finally {
            anaC.disconnect();
            link.stop();
            relay.close();
            server.stop();
        }
    }

    @Test
    void tlsDePontaAPontaPelaPonte(@TempDir Path dir) throws Exception {
        TlsSupport.setBaseDir(dir);
        GameServer server = new GameServer(0, new SceneModel());
        server.useTls(TlsSupport.hostContext());
        server.start();
        RelayServer relay = new RelayServer(0);
        relay.start();
        String code = RelayHostLink.newCode();
        RelayHostLink link = new RelayHostLink("localhost", relay.getPort(), code, RelayHostLink.newSecret(),
                server::acceptTunneled, null, s -> { });
        Rec ana = new Rec();
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            link.start();
            java.util.List<String> fps = new java.util.ArrayList<>();
            anaC.useTls((hp, fp, prev) -> { fps.add(fp); return true; });
            anaC.useRelay(code);
            anaC.connect("localhost", relay.getPort(), "Ana", false);
            waitFor(() -> ana.scene != null, "Ana entrou com TLS pela ponte");
            assertEquals(java.util.List.of(TlsSupport.hostFingerprint()), fps, "o certificado e o do MESTRE, nao o da ponte");
        } finally {
            anaC.disconnect();
            link.stop();
            relay.close();
            server.stop();
        }
    }

    @Test
    void navegadorPelaPonte() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        WebGateway web = new WebGateway(0, server.getPort(), null);
        web.start();
        RelayServer relay = new RelayServer(0);
        relay.start();
        String code = RelayHostLink.newCode();
        RelayHostLink link = new RelayHostLink("localhost", relay.getPort(), code, RelayHostLink.newSecret(),
                server::acceptTunneled, web::acceptTunneled, s -> { });
        try {
            link.start();
            String r1 = http(relay.getPort(), "GET /?mesa=" + code + " HTTP/1.1\r\nHost: x\r\n\r\n");
            assertTrue(r1.startsWith("HTTP/1.1 302"), r1);
            assertTrue(r1.contains("Set-Cookie: customizr_mesa=" + code));
            String r2 = http(relay.getPort(), "GET / HTTP/1.1\r\nHost: x\r\nCookie: customizr_mesa=" + code
                    + "\r\nConnection: close\r\n\r\n");
            assertTrue(r2.startsWith("HTTP/1.1 200"), r2.substring(0, Math.min(80, r2.length())));
            assertTrue(r2.contains("Customizr"));
            String r3 = http(relay.getPort(), "GET / HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");
            assertTrue(r3.contains("Código da mesa"), "sem cookie: a ponte pede o codigo");
        } finally {
            link.stop();
            relay.close();
            web.stop();
            server.stop();
        }
    }

    private static String http(int port, String request) throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(8000);
            s.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            s.getOutputStream().flush();
            InputStream in = s.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
