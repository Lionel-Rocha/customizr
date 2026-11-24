package com.vttcaseiro.view;

import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.importer.PdfTemplateReader;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.service.ImageAssetService;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** A ficha de modelo mostra a pagina do PDF com um controle por campo; digitar muda o valor. */
class TemplateSheetViewUiTest {

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

    @Test
    void showsPageWithEditableFields() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        LibraryStorage repo = new LibraryStorage(dir);
        SheetTemplate t = PdfTemplateReader.read(com.vttcaseiro.importer.PdfTemplateTest.fillablePdf(), "Ficha",
                SheetTemplate.CHARACTER, repo.getImageAssetStore());
        ImageAssetService assets = new ImageAssetService(repo);
        Map<String, String> values = new LinkedHashMap<>(Map.of("Nome do Personagem", "Lyra", "PV Atual", "12", "Classe", "Mago"));
        AtomicInteger changes = new AtomicInteger();
        AtomicReference<String> rolled = new AtomicReference<>();
        onFx(() -> {
            TemplateSheetView v = new TemplateSheetView(null, "Lyra", t, values, assets, true, new TemplateSheetView.Actions() {
                @Override public void changed() { changes.incrementAndGet(); }
                @Override public void roll(String label, int modifier) { rolled.set(label + modifier); }
            });
            v.getStage().getScene().getStylesheets().add(TemplateSheetView.class.getResource("/style.css").toExternalForm());
            v.show();
            assertEquals(8, v.controlCount());
            v.typeInto("CA", "17");
            assertEquals("17", values.get("CA"));
            assertTrue(changes.get() > 0);
            v.reload(t, new LinkedHashMap<>(Map.of("Nome do Personagem", "Lyra", "PV Atual", "5")));
            assertEquals("5", values.get("PV Atual"), "valor novo da rede entra no mesmo mapa");
            String snap = System.getProperty("vtt.snap");
            if (snap != null) {
                v.getStage().getScene().getRoot().applyCss();
                v.getStage().getScene().getRoot().layout();
                WritableImage shot = v.getStage().getScene().getRoot().snapshot(null, null);
                BufferedImage o = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < o.getHeight(); y++)
                    for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                try {
                    javax.imageio.ImageIO.write(o, "png", new java.io.File(snap, "ficha-modelo.png"));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            v.getStage().close();
        });
    }
}
