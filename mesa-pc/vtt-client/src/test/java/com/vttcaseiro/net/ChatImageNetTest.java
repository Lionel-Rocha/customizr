package com.vttcaseiro.net;

import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Imagem no chat: os bytes vao uma vez (image_data), a mensagem leva so o id; id falso nao passa. */
class ChatImageNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String[]> images = new CopyOnWriteArrayList<>();
        final List<String> stored = new CopyOnWriteArrayList<>();

        @Override public void onChatImage(String player, String assetId, String caption, String whisperTo) {
            images.add(new String[]{player, assetId, caption});
        }

        @Override public void onImageStored(String assetId) {
            stored.add(assetId);
        }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    /** GIF 1x1 de verdade. */
    private static final byte[] GIF = java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACwAAAAAAQABAAACAkQBADs=");

    @Test
    void imageReachesEveryoneAndFakeIdsAreDropped(@TempDir Path dir) throws Exception {
        ImageAssetStore hostStore = new ImageAssetStore(Files.createDirectories(dir.resolve("host")));
        ImageAssetStore anaStore = new ImageAssetStore(Files.createDirectories(dir.resolve("ana")));
        ImageAssetStore biaStore = new ImageAssetStore(Files.createDirectories(dir.resolve("bia")));
        GameServer server = new GameServer(0, new SceneModel(), hostStore);
        server.start();
        Rec ana = new Rec();
        Rec bia = new Rec();
        GameClient anaC = new GameClient(ana, anaStore, "ana");
        GameClient biaC = new GameClient(bia, biaStore, "bia");
        try {
            anaC.connect("localhost", server.getPort(), "Ana", false);
            biaC.connect("localhost", server.getPort(), "Bia", false);
            waitFor(() -> ana.scene != null && bia.scene != null, "conectados");

            String id = anaStore.store(GIF, "mapa-do-tesouro.gif");
            assertTrue(id.endsWith(".gif"), "GIF continua GIF");
            anaC.sendChatImage("Ana", id, "olha o que achei", null);
            waitFor(() -> bia.images.size() == 1 && biaStore.exists(id), "imagem chegou na Bia");
            assertArrayEquals(new String[]{"Ana", id, "olha o que achei"}, bia.images.get(0));
            assertArrayEquals(GIF, biaStore.read(id), "bytes intactos");
            waitFor(() -> ana.images.size() == 1, "quem mandou tambem ve");

            // id inventado / caminho: vira chat comum, sem imagem
            biaC.sendChatImage("Bia", "../../segredo.png", "hack", null);
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("hack")), "texto chegou sem imagem");
            assertEquals(1, ana.images.size());
        } finally {
            anaC.disconnect();
            biaC.disconnect();
            server.stop();
        }
    }
}
