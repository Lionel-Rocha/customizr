package com.vttcaseiro.net;

import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.service.ImageAssetService;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jogador entrando numa mesa com mapa: a cena chega antes da imagem do mapa. Antes o mapa ficava vazio pra sempre
 * (nada redesenhava quando a imagem chegava); agora a imagem aparece assim que chega.
 */
class JoinWithMapUiTest {

    @TempDir
    Path dir;

    private static boolean fxReady;

    @BeforeAll
    static void startFx() {
        if (Boolean.getBoolean("vtt.skipUi") || java.awt.GraphicsEnvironment.isHeadless()) return;
        try {
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            fxReady = started.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            fxReady = true;
        } catch (Throwable ex) {
            fxReady = false;
        }
        if (fxReady) Platform.setImplicitExit(false);
    }

    private static void onFx(Runnable r) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "FX travou");
        if (err.get() != null) throw new AssertionError(err.get());
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 8000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> stored = new CopyOnWriteArrayList<>();
        final List<String> order = new CopyOnWriteArrayList<>();
        @Override public void onSceneSync(SceneModel s) {
            super.onSceneSync(s);
            order.add("scene");
        }
        @Override public void onImageStored(String id) {
            stored.add(id);
            order.add("image");
        }
    }

    @Test
    void mapAppearsWhenTheImageArrivesAfterTheScene() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB), "png", png);
        LibraryStorage host = new LibraryStorage(dir.resolve("host"));
        String assetId = host.getImageAssetStore().store(png.toByteArray(), "mapa.png");
        SceneModel scene = new SceneModel();
        scene.setName("Masmorra");
        scene.setBackgroundImageAssetId(assetId);

        GameServer server = new GameServer(0, scene, host.getImageAssetStore());
        server.start();
        LibraryStorage player = new LibraryStorage(dir.resolve("jogador"));
        Rec ana = new Rec();
        GameClient anaC = new GameClient(ana, player.getImageAssetStore(), "ana");
        try {
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> ana.scene != null && ana.stored.contains(assetId), "cena e imagem chegaram");
            assertEquals("scene", ana.order.get(0), "a cena chega primeiro (por isso a imagem precisa redesenhar)");

            // O que o programa do jogador faz: monta o mapa com a cena (imagem ainda nao estava no disco)...
            ImageAssetService assets = new ImageAssetService(player);
            SceneModel received = ana.scene;
            AtomicReference<MapView> ref = new AtomicReference<>();
            onFx(() -> {
                MapView view = new MapView(received, null); // sem acesso a imagem = como estava antes de chegar
                ref.set(view);
                Stage stage = new Stage();
                stage.setScene(new Scene(view, 800, 500));
                stage.show();
                assertNull(view.backgroundImage(), "sem imagem ainda");
                stage.close();
            });
            // ...e quando a imagem chega, o MapView com acesso ao acervo redesenha
            onFx(() -> {
                MapView real = new MapView(received, assets);
                real.backgroundArrived(assetId);
                assertNotNull(real.backgroundImage(), "mapa apareceu");
                assertEquals(1400, real.mapWidth(), 0.01);
                real.release();
                assertNull(real.backgroundImage(), "release solta a imagem (memoria de video)");
            });
        } finally {
            anaC.disconnect();
            server.stop();
        }
    }
}
