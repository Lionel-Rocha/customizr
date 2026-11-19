package com.vttcaseiro.app;

import com.vttcaseiro.view.ChatListCell;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Imagem no chat aparece como miniatura (clicavel); enquanto nao chega, um aviso. */
class ChatImageUiTest {

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

    private static <T extends Node> List<T> all(Node root, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) out.addAll(all(c, type));
        return out;
    }

    @Test
    void thumbnailAndPlaceholder() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        WritableImage big = new WritableImage(800, 400);
        for (int y = 0; y < 400; y++) for (int x = 0; x < 800; x++) big.getPixelWriter().setColor(x, y, Color.CORAL);
        List<String> opened = new ArrayList<>();
        onFx(() -> {
            ListView<LogEntry> list = new ListView<>();
            list.setCellFactory(lv -> new ChatListCell(new ChatListCell.ChatActions() {
                @Override public void applyToSelectedToken(int total, double multiplier, boolean heal) { }
                @Override public javafx.scene.image.Image chatImage(String assetId) {
                    return assetId.startsWith("a") ? big : null;
                }
                @Override public void openImage(String assetId) { opened.add(assetId); }
            }));
            list.getItems().addAll(LogEntry.image("Ana", "o mapa", "a".repeat(64) + ".png", null),
                    LogEntry.image("Bia", "", "b".repeat(64) + ".gif", null));
            Stage stage = new Stage();
            stage.setScene(new Scene(list, 400, 500));
            stage.show();
            list.applyCss();
            list.layout();
            List<ImageView> views = all(list, ImageView.class).stream().filter(v -> v.getImage() == big).toList();
            assertEquals(1, views.size(), "uma miniatura");
            assertTrue(views.get(0).getFitWidth() <= 240 && views.get(0).getFitHeight() <= 200, "cabe no chat");
            assertTrue(all(list, javafx.scene.control.Label.class).stream()
                    .anyMatch(l -> "(recebendo imagem...)".equals(l.getText())), "a que nao chegou mostra aviso");
            views.get(0).getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, false, null));
            assertEquals(List.of("a".repeat(64) + ".png"), opened, "clique abre grande");
            stage.close();
        });
    }
}
