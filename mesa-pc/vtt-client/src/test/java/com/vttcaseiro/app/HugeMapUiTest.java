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

/**
 * Mapa enorme (500 x 500 casas de casa grande) derrubava a tela: a grade e a neblina eram Canvas do tamanho do
 * mapa inteiro (dezenas de milhares de pixels), maiores que a textura que a placa de video aceita
 * ("Texture ... is null" no log). Agora os Canvas ficam no maximo em CANVAS_MAX_SIDE e esticam na tela.
 */
class HugeMapUiTest {

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

    @Test
    void cellSizeAndTokenSpanAreClamped() {
        GridSettings g = new GridSettings();
        g.setCellSize(1e9);
        assertEquals(GridSettings.MAX_CELL_SIZE, g.getCellSize());
        g.setCellSize(Double.NaN);
        assertEquals(70, g.getCellSize());
        g.setMapSizeCells(99999, 99999);
        assertEquals(GridSettings.MAX_MAP_CELLS, g.getMapWidthCells());
        TokenModel t = new TokenModel("Colosso", null);
        t.setWidthCells(5000);
        assertEquals(TokenModel.MAX_CELLS, t.getWidthCells());
    }

    @Test
    void bigImageIsDecodedSmallButKeepsItsSize() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        BufferedImage big = new BufferedImage(12000, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(big, "png", out);
        SafeImage.Loaded l = SafeImage.read(out.toByteArray());
        assertNotNull(l);
        assertEquals(12000, l.width(), "tamanho original (o mapa mede por ele)");
        assertTrue(l.image().getWidth() <= SafeImage.MAX_SIDE, "textura reduzida");
        assertTrue(l.reduced());
    }

    @Test
    void hugeMapKeepsCanvasesSmallAndStillRenders() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        SceneModel scene = new SceneModel();
        scene.getGrid().setCellSize(120);
        scene.getGrid().setMapSizeCells(500, 500);
        scene.getFog().setEnabled(true);
        scene.setVisionEnabled(true);
        TokenModel hero = new TokenModel("Heroi", null);
        hero.setX(1200);
        hero.setY(1200);
        scene.addToken(hero);
        onFx(() -> {
            MapView view = new MapView(scene);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 900, 600));
            stage.show();
            view.loadBackgroundImage();
            view.redrawFog();
            assertEquals(60000, view.mapWidth(), 0.01, "o mapa continua com o tamanho escolhido");
            assertTrue(view.gridCanvasSide() <= MapView.CANVAS_MAX_SIDE, "Canvas da grade: " + view.gridCanvasSide());
            assertTrue(view.canvasScale() < 1);
            assertEquals(500, view.gridCols());
            view.fitToView();
            WritableImage img = view.snapshot(screen(), null); // desenha de verdade (antes: textura nula)
            assertNotNull(img);
            String snap = System.getProperty("vtt.snap");
            if (snap != null) {
                view.resetView();
                WritableImage shot = view.snapshot(screen(), null);
                BufferedImage o = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < o.getHeight(); y++)
                    for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                try {
                    javax.imageio.ImageIO.write(o, "png", new java.io.File(snap));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            // casa absurda vinda de um arquivo salvo: limita, nao quebra
            scene.getGrid().setCellSize(1e7);
            view.redrawGrid();
            assertTrue(view.mapWidth() <= MapView.MAP_MAX_SIDE);
            assertTrue(view.gridCanvasSide() <= MapView.CANVAS_MAX_SIDE);
            view.snapshot(screen(), null);
            stage.close();
        });
    }
}
