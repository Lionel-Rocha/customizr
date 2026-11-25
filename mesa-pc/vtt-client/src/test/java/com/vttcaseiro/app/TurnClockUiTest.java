package com.vttcaseiro.app;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.AudioCommand;
import com.vttcaseiro.model.MapZone;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.service.SceneService;
import com.vttcaseiro.service.TableService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Relogio do turno na fila sobre o mapa e no painel de combate; o que a zona de som toca. */
class TurnClockUiTest {

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

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = Main.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    void zoneSoundPlaysStopsOrDoesNothing() {
        MapZone z = new MapZone(0, 0, 10, 10, MapZone.SOUND);
        assertNull(Main.zoneSound(z), "efeito sem som: nada");
        z.setSoundChannel(AudioCommand.MUSIC);
        assertTrue(Main.zoneSound(z).isStop(), "musica sem som: para a musica");
        z.setSoundId("abc");
        z.setSoundName("Taverna");
        AudioCommand c = Main.zoneSound(z);
        assertEquals(AudioCommand.MUSIC, c.getChannel());
        assertEquals("abc", c.getAssetId());
        assertTrue(c.loops());
    }

    @Test
    void clockShowsOnTheTurnQueueAndInTheCombatPanel(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        Path mesa = Files.createDirectories(dir.resolve("Campanha"));
        onFx(() -> {
            try {
                Main main = new Main();
                Stage stage = new Stage();
                Field sf = Main.class.getDeclaredField("stage");
                sf.setAccessible(true);
                sf.set(main, stage);
                Method m = Main.class.getDeclaredMethod("onMesaChosen", MesaRepository.class);
                m.setAccessible(true);
                m.invoke(main, new LibraryStorage(mesa));
                stage.setWidth(1440);
                stage.setHeight(880);
                stage.show();

                SceneService scenes = (SceneService) field(main, "sceneService");
                TokenModel a = new TokenModel("Lyra", null);
                a.setInitiative(18);
                TokenModel b = new TokenModel("Goblin", null);
                b.setInitiative(9);
                scenes.addToken(a);
                scenes.addToken(b);
                TableService table = (TableService) field(main, "table");
                table.startEncounter();
                HBox queue = (HBox) field(main, "turnQueue");
                Label clock = (Label) field(main, "turnClock");
                assertFalse(queue.getChildren().contains(clock), "sem tempo escolhido, sem relogio");

                table.setTurnTimer(60);
                assertTrue(queue.getChildren().contains(clock), "relogio na fila de turnos");
                assertEquals("1:00", clock.getText());
                Label panel = (Label) field(main, "turnClockPanel");
                assertTrue(panel.isVisible());
                assertEquals("1:00", panel.getText());

                table.setTurnTimerPaused(true);
                assertTrue(clock.getText().endsWith("(pausado)"));
                table.nextTurn();
                assertEquals("1:00", clock.getText().replace(" (pausado)", ""), "vez de outro: tempo cheio");

                String snap = System.getProperty("vtt.snap");
                if (snap != null) {
                    table.setTurnTimerPaused(false);
                    stage.getScene().getRoot().applyCss();
                    stage.getScene().getRoot().layout();
                    javafx.scene.image.WritableImage img = stage.getScene().getRoot().snapshot(null, null);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(), (int) img.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < out.getHeight(); y++)
                        for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(out, "png", new java.io.File(snap, "tela-relogio.png"));
                }

                table.setTurnTimer(0);
                assertFalse(queue.getChildren().contains(clock), "desligou: some");
                assertFalse(panel.isVisible());
                assertTrue(main.handoutRecipientChoices(null).isEmpty(), "offline: ninguem pra escolher");
                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
