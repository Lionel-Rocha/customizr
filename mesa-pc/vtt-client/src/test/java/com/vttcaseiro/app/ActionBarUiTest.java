package com.vttcaseiro.app;

import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.CharacterService;
import com.vttcaseiro.service.SceneService;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Barra de acoes: escolher o token mostra os ataques da ficha; clicar rola. */
class ActionBarUiTest {

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
    void selectingMyTokenShowsItsAttacks(@TempDir Path dir) throws Exception {
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

                CharacterSheet lyra = new CharacterSheet("Lyra");
                lyra.getAttacks().add(new Attack("Espada longa", "+5", "1d8+3 cortante", ""));
                lyra.getAttacks().add(new Attack("Sem dano nem acerto", "", "", "so texto"));
                ((CharacterService) field(main, "characterService")).save(lyra);
                TokenModel t = new TokenModel("Lyra", null);
                t.setCharacterSheetId(lyra.getId());
                ((SceneService) field(main, "sceneService")).addToken(t);
                MapView mv = (MapView) field(main, "mapView");
                HBox bar = (HBox) field(main, "actionBar");
                assertFalse(bar.isVisible(), "sem token escolhido, sem barra");

                mv.selectTokens(List.of(t));
                assertTrue(bar.isVisible(), "escolheu o token: aparece a barra");
                List<String> texts = bar.getChildren().stream().filter(n -> n instanceof Button)
                        .map(n -> ((Button) n).getText()).filter(java.util.Objects::nonNull).toList();
                assertEquals(List.of("Espada longa +5"), texts, "so o ataque que rola");

                String snap = System.getProperty("vtt.snap");
                if (snap != null) {
                    stage.getScene().getRoot().applyCss();
                    stage.getScene().getRoot().layout();
                    javafx.scene.image.WritableImage img = stage.getScene().getRoot().snapshot(null, null);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(), (int) img.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < out.getHeight(); y++)
                        for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(out, "png", new java.io.File(snap, "tela-barra-acoes.png"));
                }

                // clicar no ataque rola (acerto aparece no chat)
                @SuppressWarnings("unchecked")
                javafx.collections.ObservableList<Object> log = (javafx.collections.ObservableList<Object>) field(main, "logEntries");
                int before = log.size();
                Button attack = (Button) bar.getChildren().stream()
                        .filter(n -> n instanceof Button b && "Espada longa +5".equals(b.getText())).findFirst().orElseThrow();
                Node.class.cast(attack).fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                        true, false, false, true, false, true, null));
                assertTrue(log.size() > before, "rolou");
                assertTrue(log.stream().anyMatch(e -> String.valueOf(e).contains("Espada longa")), "com o nome do ataque");

                mv.clearSelection();
                assertFalse(bar.isVisible(), "tirou a seleção: some");
                stage.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
