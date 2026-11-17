package com.vttcaseiro.net;

import com.vttcaseiro.library.AudioAssetStore;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.relay.RelayHostLink;
import com.vttcaseiro.relay.RelayServer;
import com.vttcaseiro.service.NetworkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Som pela ponte: o jogador baixa o arquivo pela propria ponte (cookie da mesa) e o hash e conferido. */
class RelayAudioTest {

    static class L implements NetworkService.GameListener {
        volatile SceneModel scene;
        @Override public void onSceneSync(SceneModel s) { scene = s; }
        @Override public void onTokenMoved(String id, double x, double y) { }
        @Override public void onTokenAdded(TokenModel t) { }
        @Override public void onTokenRemoved(String id) { }
        @Override public void onRollResult(String p, String d, int t) { }
        @Override public void onChat(String p, String t) { }
        @Override public void onDisconnected(String r) { }
    }

    /** Porta P livre com P+1 tambem livre (o servidor do navegador usa a porta do jogo + 1). */
    static int freePortPair() throws java.io.IOException {
        for (int i = 0; i < 50; i++) {
            int p;
            try (java.net.ServerSocket a = new java.net.ServerSocket(0)) { p = a.getLocalPort(); }
            try (java.net.ServerSocket a = new java.net.ServerSocket(p); java.net.ServerSocket b = new java.net.ServerSocket(p + 1)) {
                return p;
            } catch (java.io.IOException ignored) {
                // tenta outra
            }
        }
        throw new java.io.IOException("sem par de portas livres");
    }

    @Test
    void jogadorBaixaOSomPelaPonte(@TempDir Path dir) throws Exception {
        ImageAssetStore hostImages = new ImageAssetStore(Files.createDirectories(dir.resolve("mestre")));
        AudioAssetStore hostAudio = AudioAssetStore.besideImages(hostImages);
        Path wav = dir.resolve("tom.wav");
        Files.write(wav, PackageDNetTest.tone());
        String soundId = hostAudio.importFile(wav);

        RelayServer relay = new RelayServer(0);
        relay.start();
        NetworkService gm = new NetworkService();
        NetworkService ana = new NetworkService();
        try {
            gm.host(freePortPair(), new SceneModel(), "Mestre", hostImages, null, null, false, new L());
            assertTrue(gm.getWebPort() > 0, "servidor do navegador ligado no mestre");
            String code = RelayHostLink.newCode();
            gm.openRelay("localhost", relay.getPort(), code, RelayHostLink.newSecret(), m -> { });
            L anaL = new L();
            ana.connectThroughRelay(code);
            ana.connect("localhost", relay.getPort(), "Ana", new ImageAssetStore(Files.createDirectories(dir.resolve("ana"))),
                    false, null, null, false, null, anaL);
            long end = System.currentTimeMillis() + 8000;
            while (anaL.scene == null && System.currentTimeMillis() < end) Thread.sleep(20);
            assertNotNull(anaL.scene, "entrou pela ponte");
            assertTrue(ana.remoteAudioBase().endsWith(":" + relay.getPort()), "o som vem pela ponte, nao pela porta do mestre");

            Path target = dir.resolve("baixado.wav");
            ana.downloadAudio(soundId, target);
            assertArrayEquals(Files.readAllBytes(wav), Files.readAllBytes(target));

            // Um id que nao bate com o conteudo e recusado (ninguem troca o som no caminho).
            String fakeId = "0".repeat(64) + ".wav";
            Files.copy(wav, hostAudio.path(fakeId));
            assertThrows(java.io.IOException.class, () -> ana.downloadAudio(fakeId, dir.resolve("falso.wav")));
            assertFalse(Files.exists(dir.resolve("falso.wav")));
        } finally {
            ana.disconnect();
            gm.disconnect();
            relay.close();
        }
    }
}
