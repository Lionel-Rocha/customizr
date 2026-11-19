package com.vttcaseiro.app;

import com.vttcaseiro.model.MapLight;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Portas trancadas/secretas, janelas e luzes fixas desenhadas no mapa: o mestre ve tudo, o jogador nao. */
class DoorsLightsUiTest {

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

    private static int count(MapView view, String field) {
        try {
            Field f = MapView.class.getDeclaredField(field);
            f.setAccessible(true);
            return ((Group) f.get(view)).getChildren().size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** TokenActions de mentira: so responde isGm. */
    private static MapView.TokenActions player() {
        return (MapView.TokenActions) Proxy.newProxyInstance(MapView.class.getClassLoader(),
                new Class<?>[]{MapView.TokenActions.class}, (p, m, a) -> {
                    if (m.getName().equals("isGm")) return false;
                    Class<?> t = m.getReturnType();
                    if (t == boolean.class) return false;
                    if (t == int.class) return 0;
                    if (t == double.class) return 0.0;
                    if (t == long.class) return 0L;
                    return null;
                });
    }

    @Test
    void masterSeesEverythingPlayerSeesOnlyPlainDoors() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        SceneModel scene = new SceneModel();
        scene.getGrid().setCellSize(70);
        scene.getGrid().setFeetPerCell(5);
        scene.setVisionEnabled(true);
        scene.setDark(true);
        scene.getWalls().add(new Wall(70, 70, 700, 70, false));
        Wall locked = new Wall(70, 70, 70, 280, true);
        locked.setLocked(true);
        Wall secret = new Wall(700, 70, 700, 280, true);
        secret.setSecret(true);
        Wall window = new Wall(70, 420, 700, 420, false);
        window.setKind(false, true);
        Wall plain = new Wall(280, 280, 420, 280, true);
        scene.getWalls().addAll(java.util.List.of(locked, secret, window, plain));
        scene.getLights().add(new MapLight("Fogueira", 385, 210, 20, 20));
        MapLight off = new MapLight("Vela", 600, 350, 5, 5);
        off.setOn(false);
        scene.getLights().add(off);
        TokenModel hero = new TokenModel("Heroi", null);
        hero.setX(140);
        hero.setY(140);
        hero.setOwnerId("p");
        scene.addToken(hero);

        AtomicReference<MapView> ref = new AtomicReference<>();
        onFx(() -> {
            MapView view = new MapView(scene);
            ref.set(view);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 900, 600));
            view.redrawWalls();
            assertEquals(5, count(view, "wallLayer"), "mestre ve as 5 linhas");
            assertEquals(2, count(view, "lightLayer"), "mestre ve as 2 luzes (acesa e apagada)");
            String snap = System.getProperty("vtt.snap");
            if (snap != null) {
                view.setGmPreview(true);
                WritableImage img = view.snapshot(null, null);
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(), (int) img.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < out.getHeight(); y++)
                    for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                try {
                    javax.imageio.ImageIO.write(out, "png", new java.io.File(snap));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }

            view.setTokenActions(player());
            view.redrawWalls();
            assertEquals(2, count(view, "wallLayer"), "jogador: so a porta comum e a trancada (secreta fechada = parede)");
            assertEquals(0, count(view, "lightLayer"), "jogador nao ve o icone das luzes");

            secret.setOpen(true);
            view.redrawWalls();
            assertEquals(3, count(view, "wallLayer"), "secreta aberta aparece pro jogador");
        });
    }
}
