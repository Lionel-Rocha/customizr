package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Servidor nao trava por causa de um jogador lento nem de uma mensagem gigante. */
class RobustnessTest {

    private static void waitFor(BooleanSupplier cond, long ms, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timeout esperando: " + what);
    }

    @Test
    void leitorComLimiteSeparaLinhasEBarraLinhaGigante() throws Exception {
        BoundedLineReader r = new BoundedLineReader(new StringReader("a\r\nbb\nccc"), 10);
        assertEquals("a", r.readLine());
        assertEquals("bb", r.readLine());
        assertEquals("ccc", r.readLine());
        assertNull(r.readLine());
        BoundedLineReader big = new BoundedLineReader(new StringReader("x".repeat(50) + "\n"), 10);
        assertThrows(BoundedLineReader.LineTooLongException.class, big::readLine);
    }

    @Test
    void jogadorQueNaoLeNaoTravaOsOutros() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GameServerSecurityTest.Recorder gm = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder ana = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        try (Socket stuck = new Socket("localhost", server.getPort())) {
            // Jogador "travado": manda hello e nunca mais le nada.
            stuck.setReceiveBufferSize(4096);
            PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(stuck.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(NetCodec.encode(NetMessage.hello("Travado", "stuck", false)));
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, 5000, "conectados");

            String payload = "y".repeat(2000);
            int n = 6000; // ~12 MB: muito mais que o buffer de socket do travado
            long start = System.currentTimeMillis();
            for (int i = 0; i < n; i++) gmC.sendChat("Mestre", i + payload);
            waitFor(() -> ana.chats.stream().filter(c -> c.contains(payload)).count() == n, 20000, "Ana recebe tudo");
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 20000, "levou " + elapsed + " ms");

            // O travado acaba desconectado (fila passou do limite) em vez de
            // acumular memoria pra sempre.
            InputStream in = stuck.getInputStream();
            stuck.setSoTimeout(10000);
            byte[] buf = new byte[65536];
            long total = 0;
            int read;
            while ((read = in.read(buf)) != -1) total += read;
            assertTrue(total < (long) n * payload.length(), "conexao do travado foi encerrada antes do fim");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }

    @Test
    void mensagemGiganteDerrubaSoQuemMandou() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GameServerSecurityTest.Recorder gm = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        try (Socket evil = new Socket("localhost", server.getPort())) {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            waitFor(() -> gm.scene != null, 5000, "mestre conectado");
            OutputStream raw = evil.getOutputStream();
            raw.write((NetCodec.encode(NetMessage.hello("Mau", "evil", false)) + "\n").getBytes(StandardCharsets.UTF_8));
            byte[] chunk = "z".repeat(1 << 20).getBytes(StandardCharsets.UTF_8);
            boolean closedByServer = false;
            try {
                for (int i = 0; i < 12; i++) raw.write(chunk); // 12 MB sem '\n'
                raw.flush();
                evil.setSoTimeout(10000);
                InputStream in = evil.getInputStream();
                byte[] buf = new byte[8192];
                while (in.read(buf) != -1) { /* drena ate o servidor fechar */ }
                closedByServer = true;
            } catch (java.io.IOException ex) {
                closedByServer = true; // servidor fechou no meio da escrita
            }
            assertTrue(closedByServer);
            // E a mesa continua viva pro resto.
            gmC.sendChat("Mestre", "ainda aqui");
            waitFor(() -> gm.chats.stream().anyMatch(c -> c.contains("ainda aqui")), 5000, "servidor vivo");
        } finally {
            gmC.disconnect();
            server.stop();
        }
    }
}
