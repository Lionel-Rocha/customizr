package com.vttcaseiro.view;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.SceneModel;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Tela de escolher mesa: cartoes com miniatura e resumo; apagar manda pra lixeira das mesas. */
class MesaSelectorUiTest {

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

    private static void all(Node n, List<Node> out) {
        if (out.stream().anyMatch(x -> x == n)) return;
        out.add(n);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) all(c, out);
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) all(sp.getContent(), out);
    }

    @Test
    void cardsShowSummaryAndTrashKeepsTheMesa() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        LibraryStorage camp = new LibraryStorage(dir.resolve("Minha campanha"));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(400, 225, BufferedImage.TYPE_INT_RGB), "png", png);
        SceneModel scene = new SceneModel();
        scene.setBackgroundImageAssetId(camp.getImageAssetStore().store(png.toByteArray(), "mapa.png"));
        camp.saveMap(scene, "Taverna");
        camp.saveMap(new SceneModel(), "Estrada");
        camp.saveCharacter(new com.vttcaseiro.character.CharacterSheet("Lyra"), "Lyra");
        new LibraryStorage(dir.resolve("Teste"));

        assertTrue(MesaSelectorView.mesaSummary(dir.resolve("Minha campanha")).contains("2 cenas  ·  1 ficha"));
        assertNotNull(MesaSelectorView.lastSceneImage(dir.resolve("Minha campanha")), "miniatura da última cena");
        assertNull(MesaSelectorView.lastSceneImage(dir.resolve("Teste")), "sem cena: fica o ícone");

        // capa: vale mais que a ultima cena; arquivo que nao e imagem e recusado
        Path cover = dir.resolve("capa-escolhida.png");
        Files.write(cover, png.toByteArray());
        Path teste = dir.resolve("Teste");
        assertNull(MesaSelectorView.cardImage(teste));
        MesaSelectorView.setCover(teste, cover);
        assertEquals("capa.png", MesaSelectorView.coverFile(teste).getFileName().toString());
        assertNotNull(MesaSelectorView.cardImage(teste), "capa aparece no cartão");
        Path notImage = dir.resolve("nota.png");
        Files.writeString(notImage, "isso nao e imagem");
        assertThrows(java.io.IOException.class, () -> MesaSelectorView.setCover(teste, notImage));
        assertNotNull(MesaSelectorView.coverFile(teste), "capa antiga continua");
        MesaSelectorView.clearCover(teste);
        assertNull(MesaSelectorView.coverFile(teste));
        MesaSelectorView.setCover(teste, cover);

        AtomicReference<Stage> st = new AtomicReference<>();
        onFx(() -> {
            try {
                Stage stage = new Stage();
                st.set(stage);
                MesaSelectorView v = new MesaSelectorView(stage, m -> { }, (m, r) -> { });
                Method show = MesaSelectorView.class.getDeclaredMethod("showMesaList", Path.class);
                show.setAccessible(true);
                show.invoke(v, dir);
                List<Node> nodes = new ArrayList<>();
                all(stage.getScene().getRoot(), nodes);
                long cards = nodes.stream().filter(n -> n.getStyleClass().contains("mesa-card")).count();
                assertEquals(2, cards);
                assertTrue(nodes.stream().anyMatch(n -> n.getStyleClass().contains("mesa-card-selected")), "a primeira vem escolhida");
                long primaries = nodes.stream().filter(n -> n.getStyleClass().contains("action-card-primary")).count();
                assertEquals(1, primaries, "um destaque só (Abrir mesa)");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(600); // miniatura carrega fora da tela
        onFx(() -> {
            Stage stage = st.get();
            String snap = System.getProperty("vtt.snap");
            if (snap != null) {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                WritableImage img = stage.getScene().getRoot().snapshot(null, null);
                BufferedImage o = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < o.getHeight(); y++)
                    for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                try {
                    javax.imageio.ImageIO.write(o, "png", new java.io.File(snap, "escolher-mesa.png"));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            stage.close();
        });

        Path moved = MesaSelectorView.moveToTrash(dir, "Teste");
        assertFalse(Files.exists(dir.resolve("Teste")), "some da lista");
        assertTrue(Files.isDirectory(moved) && moved.startsWith(dir.resolve(MesaSelectorView.TRASH_DIR)), "vai pra lixeira das mesas");
    }

    @SuppressWarnings("unused")
    private static Label unused() {
        return null;
    }
}
