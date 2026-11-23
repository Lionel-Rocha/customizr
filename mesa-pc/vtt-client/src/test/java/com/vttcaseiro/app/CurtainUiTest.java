package com.vttcaseiro.app;

import com.vttcaseiro.model.GridSettings;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.view.MapView;
import com.vttcaseiro.view.SafeImage;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Cena escondida: o jogador ve o aviso "o mestre esta preparando"; o mestre nao. */
class CurtainUiTest {

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
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX travou");
        if (err.get() != null) throw new AssertionError(err.get());
    }

    /** So a area da janela (como a tela desenha); a foto do no inteiro teria o tamanho do mapa. */
    private static javafx.scene.SnapshotParameters screen() {
        javafx.scene.SnapshotParameters p = new javafx.scene.SnapshotParameters();
        p.setViewport(new javafx.geometry.Rectangle2D(0, 0, 900, 600));
        return p;
    }

    private static MapView.TokenActions player() {
        return (MapView.TokenActions) java.lang.reflect.Proxy.newProxyInstance(MapView.class.getClassLoader(),
                new Class<?>[]{MapView.TokenActions.class}, (p, m, a) -> {
                    Class<?> t = m.getReturnType();
                    if (t == boolean.class) return false;
                    if (t == int.class) return 0;
                    if (t == double.class) return 0.0;
                    if (t == long.class) return 0L;
                    return null;
                });
    }

    private static boolean noticeShown(MapView view) {
        try {
            java.lang.reflect.Field f = MapView.class.getDeclaredField("emptyStateLabel");
            f.setAccessible(true);
            javafx.scene.control.Label l = (javafx.scene.control.Label) f.get(view);
            return l.isVisible() && l.getText().contains("preparando");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void playerSeesPreparingNoticeMasterDoesNot() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        SceneModel scene = new SceneModel();
        scene.setHiddenFromPlayers(true);
        onFx(() -> {
            MapView view = new MapView(scene);
            Stage stage = new Stage();
            Scene sc = new Scene(view, 900, 600);
            sc.getStylesheets().add(MapView.class.getResource("/style.css").toExternalForm());
            stage.setScene(sc);
            stage.show();
            assertFalse(noticeShown(view), "mestre (sem TokenActions) nao ve o aviso");
            view.setTokenActions(player());
            assertTrue(noticeShown(view), "jogador ve o aviso");
            view.applyCss();
            view.layout();
            view.refreshCurtain();
            String snap = System.getProperty("vtt.snap");
            if (snap != null) {
                WritableImage shot = view.snapshot(null, null);
                BufferedImage o = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < o.getHeight(); y++)
                    for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                try {
                    javax.imageio.ImageIO.write(o, "png", new java.io.File(snap));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            scene.setHiddenFromPlayers(false);
            view.refreshCurtain();
            assertFalse(noticeShown(view), "liberada: some");
            stage.close();
        });
    }
}
