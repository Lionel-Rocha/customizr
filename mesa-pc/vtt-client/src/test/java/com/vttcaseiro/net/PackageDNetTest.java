package com.vttcaseiro.net;

import com.vttcaseiro.library.AudioAssetStore;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.model.AudioCommand;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.view.DiceAnimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Som da mesa: biblioteca, ordem do mestre pela rede, arquivo servido por HTTP; e os dados animados. */
public class PackageDNetTest {

    static class Rec extends Priority3NetTest.Rec {
        final List<AudioCommand> audio = new CopyOnWriteArrayList<>();

        @Override public void onAudio(AudioCommand c) { audio.add(c); }
    }

    public static byte[] tone() {
        int n = 2205;
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(22050).putInt(44100).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(n * 2);
        for (int i = 0; i < n; i++) b.putShort((short) (Math.sin(i / 8.0) * 3000));
        return b.array();
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    private static String http(int port, String path, String range) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\n" + (range == null ? "" : "Range: " + range + "\r\n")
                    + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            String all = new String(s.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            return all.substring(0, all.indexOf("\r\n\r\n"));
        }
    }

    @Test
    void bibliotecaDeSons(@TempDir Path dir) throws Exception {
        AudioAssetStore store = new AudioAssetStore(dir);
        Path wav = dir.resolve("chuva.wav");
        Files.write(wav, tone());
        String id = store.importFile(wav);
        assertTrue(AudioAssetStore.isValidId(id));
        assertTrue(id.endsWith(".wav"));
        assertEquals(id, store.importFile(wav), "mesmo arquivo = mesmo id");
        assertTrue(store.exists(id));
        assertEquals("audio/wav", AudioAssetStore.contentType(id));
        assertFalse(AudioAssetStore.isValidId("../../segredo.wav"));
        Path txt = dir.resolve("nao.txt");
        Files.writeString(txt, "x");
        assertThrows(java.io.IOException.class, () -> store.importFile(txt));
    }

    @Test
    void mestreTocaTodosOuvemEQuemEntraDepoisTambem(@TempDir Path dir) throws Exception {
        ImageAssetStore images = new ImageAssetStore(dir);
        AudioAssetStore sounds = AudioAssetStore.besideImages(images);
        Path wav = dir.resolve("taverna.wav");
        Files.write(wav, tone());
        String id = sounds.importFile(wav);
        GameServer server = new GameServer(0, new SceneModel(), images, null, null);
        server.start();
        WebGateway gateway = new WebGateway(0, server.getPort(), images);
        gateway.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            anaC.sendAudio(AudioCommand.play(AudioCommand.MUSIC, id, "hack"));
            gmC.sendAudio(AudioCommand.play(AudioCommand.MUSIC, id, "Taverna"));
            gmC.sendAudio(AudioCommand.play(AudioCommand.SFX, id, "Porta rangendo"));
            waitFor(() -> ana.audio.size() == 2, "som chegou");
            assertEquals("Taverna", ana.audio.get(0).getName(), "jogador nao toca som pra mesa");
            assertTrue(ana.audio.get(0).loops());
            assertFalse(ana.audio.get(1).loops(), "efeito toca uma vez");

            lateC.connect("localhost", server.getPort(), "Atrasado", false);
            waitFor(() -> late.audio.size() == 1, "quem entra depois ouve a musica atual");
            assertEquals("Taverna", late.audio.get(0).getName());

            // O arquivo vem do host por HTTP, inteiro ou em faixas.
            assertTrue(http(gateway.getPort(), "/audio/" + id, null).startsWith("HTTP/1.1 200"));
            String part = http(gateway.getPort(), "/audio/" + id, "bytes=0-99");
            assertTrue(part.startsWith("HTTP/1.1 206"), part);
            assertTrue(part.contains("Content-Length: 100"));
            assertTrue(http(gateway.getPort(), "/audio/" + "0".repeat(64) + ".wav", null).startsWith("HTTP/1.1 404"));

            gmC.sendAudio(AudioCommand.stop(AudioCommand.MUSIC));
            waitFor(() -> ana.audio.stream().anyMatch(AudioCommand::isStop), "parar chegou");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            gateway.stop();
            server.stop();
        }
    }

    @Test
    void dadosDaRolagemPraAnimacao() {
        List<DiceAnimator.Die> adv = DiceAnimator.parse("1d20 [14, 9 descartado] + 2");
        assertEquals(2, adv.size());
        assertTrue(adv.get(0).kept());
        assertFalse(adv.get(1).kept());
        List<DiceAnimator.Die> fireball = DiceAnimator.parse("8d6[2,5,3,6,4,4,5,3] = 32");
        assertEquals(8, fireball.size());
        assertEquals(6, fireball.get(0).sides());
        assertEquals(8, DiceAnimator.parse("20d6[" + "1,".repeat(19) + "1]").size(), "no maximo 8 na tela");
        assertTrue(DiceAnimator.parse("sem dados").isEmpty());
    }
}
