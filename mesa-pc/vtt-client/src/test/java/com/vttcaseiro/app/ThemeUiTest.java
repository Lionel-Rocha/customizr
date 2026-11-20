package com.vttcaseiro.app;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Toda caixa de dialogo pronta do JavaFX sai no tema escuro, sem cada uma ter que pedir. */
class ThemeUiTest {

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
    void everyDialogGetsTheDarkTheme() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        onFx(() -> {
            Theme.install();
            TextInputDialog text = new TextInputDialog("Cena 1");
            text.setTitle("Nome da cena");
            text.setHeaderText("Como chama a cena nova?");
            text.setContentText("Nome:");
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Descanso longo pra todos os personagens com token no mapa?");
            confirm.setHeaderText(null);
            ChoiceDialog<String> choice = new ChoiceDialog<>("Arquivo do computador", "Arquivo do computador", "Galeria da mesa");
            choice.setHeaderText("De onde vem a imagem?");
            List<Dialog<?>> dialogs = List.of(text, confirm, choice);
            String snap = System.getProperty("vtt.snapdir");
            int i = 0;
            for (Dialog<?> d : dialogs) {
                d.show();
                assertTrue(d.getDialogPane().getScene().getStylesheets().contains(Theme.css()), "tema na caixa " + i);
                if (snap != null) {
                    d.getDialogPane().applyCss();
                    d.getDialogPane().layout();
                    WritableImage img = d.getDialogPane().snapshot(null, null);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(),
                            (int) img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < out.getHeight(); y++)
                        for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                    try {
                        javax.imageio.ImageIO.write(out, "png", new java.io.File(snap, "dialog-" + i + ".png"));
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
                d.close();
                i++;
            }
            Theme.install(); // chamar de novo nao duplica
            javafx.stage.Stage extra = new javafx.stage.Stage();
            extra.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(), 10, 10));
            extra.show();
            assertEquals(1, extra.getScene().getStylesheets().stream().filter(Theme.css()::equals).count(), "uma vez so");
            extra.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(), 10, 10));
            assertTrue(extra.getScene().getStylesheets().contains(Theme.css()), "cena trocada tambem ganha o tema");
            extra.close();
        });
    }
}
