package com.vttcaseiro.app;

import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

/** Mestre arrasta o pin pra outro lugar e apaga pelo menu / tecla Delete. */
class PinDragUiTest {

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

    static Node pinNode(MapView view) {
        try {
            java.lang.reflect.Field f = MapView.class.getDeclaredField("pinLayer");
            f.setAccessible(true);
            return ((Group) f.get(view)).getChildren().get(0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void mouse(Node target, javafx.event.EventType<MouseEvent> type, Point2D scene, MouseButton b) {
        Point2D screen = target.getScene().getWindow() == null ? scene
                : new Point2D(scene.getX() + target.getScene().getWindow().getX(), scene.getY() + target.getScene().getWindow().getY());
        Event.fireEvent(target, new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(), b, 1,
                false, false, false, false, b == MouseButton.PRIMARY, false, b == MouseButton.SECONDARY,
                true, false, true, null));
    }

    @Test
    void masterDragsPinAndDeletesIt() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        SceneModel scene = new SceneModel();
        MapPin pin = new MapPin(200, 150, "Taverna", "taverna");
        scene.getPins().add(pin);
        List<String> calls = new ArrayList<>();
        onFx(() -> {
            MapView view = new MapView(scene);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 900, 600));
            stage.show();
            view.setPinHandler(new MapView.PinHandler() {
                @Override public void create(double x, double y) { calls.add("create"); }
                @Override public void open(MapPin p) { calls.add("open"); }
                @Override public void moved(MapPin p, double x, double y) {
                    calls.add("moved " + Math.round(x) + "," + Math.round(y));
                    p.setPosition(x, y);
                }
                @Override public void edit(MapPin p) { calls.add("edit"); }
                @Override public void remove(MapPin p) { calls.add("remove"); }
            });
            view.redrawPins();
            Node node = pinNode(view);
            Point2D start = node.localToScene(0, 0);
            mouse(node, MouseEvent.MOUSE_PRESSED, start, MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_DRAGGED, start.add(40, 0), MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_DRAGGED, start.add(100, 50), MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_RELEASED, start.add(100, 50), MouseButton.PRIMARY);
            assertEquals(List.of("moved 300,200"), calls, "arrastar move o pin");

            calls.clear();
            view.redrawPins();
            node = pinNode(view);
            Point2D at = node.localToScene(0, 0);
            mouse(node, MouseEvent.MOUSE_PRESSED, at, MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_RELEASED, at, MouseButton.PRIMARY);
            assertEquals(List.of("open"), calls, "clique simples abre o cartao");

            // com a ferramenta de pin ligada tambem da pra arrastar
            calls.clear();
            view.setPinTool(true);
            node = pinNode(view);
            at = node.localToScene(0, 0);
            mouse(node, MouseEvent.MOUSE_PRESSED, at, MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_DRAGGED, at.add(-60, 0), MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_RELEASED, at.add(-60, 0), MouseButton.PRIMARY);
            assertEquals(List.of("moved 240,200"), calls);

            // com outra ferramenta (ex.: regua) ligada, o pin ainda arrasta
            calls.clear();
            view.setPinTool(false);
            view.setMeasuring(true);
            node = pinNode(view);
            at = node.localToScene(0, 0);
            mouse(node, MouseEvent.MOUSE_PRESSED, at, MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_DRAGGED, at.add(0, 60), MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_RELEASED, at.add(0, 60), MouseButton.PRIMARY);
            assertEquals(List.of("moved 240,260"), calls, "regua ligada nao trava o pin");
            view.setMeasuring(false);

            // tecla Delete com o pin em foco apaga
            calls.clear();
            node = pinNode(view);
            at = node.localToScene(0, 0);
            mouse(node, MouseEvent.MOUSE_PRESSED, at, MouseButton.PRIMARY);
            mouse(node, MouseEvent.MOUSE_RELEASED, at, MouseButton.PRIMARY);
            calls.clear();
            Event.fireEvent(node, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.DELETE, false, false, false, false));
            assertEquals(List.of("remove"), calls, "Delete apaga o pin selecionado");
            stage.close();
        });
    }
}
