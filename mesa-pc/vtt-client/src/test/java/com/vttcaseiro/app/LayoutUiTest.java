package com.vttcaseiro.app;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.view.MesaSelectorView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Layout novo: barra de navegacao abre/fecha o painel, ajuda longa recolhida, mesa online numa pasta escondida. */
class LayoutUiTest {

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

    private static <T extends Node> List<T> all(Node root, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) out.addAll(all(c, type));
        if (root instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) out.addAll(all(sp.getContent(), type));
        return out;
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = Main.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    void railOpensAndClosesPanels(@TempDir Path dir) throws Exception {
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
                stage.show();

                @SuppressWarnings("unchecked")
                Map<String, ToggleButton> rail = (Map<String, ToggleButton>) field(main, "railButtons");
                assertEquals(List.of("Mapa", "Fichas", "Party", "Combate", "Diário", "Som", "Rede"), new ArrayList<>(rail.keySet()));
                assertEquals("Grupo", rail.get("Party").getText(), "nome em portugues na barra");
                SplitPane split = (SplitPane) field(main, "mainSplit");
                Node panel = (Node) field(main, "sidePanel");
                assertTrue(split.getItems().contains(panel) && rail.get("Mapa").isSelected(), "abre no Mapa");

                rail.get("Combate").fire();
                @SuppressWarnings("unchecked")
                Map<String, Node> contents = (Map<String, Node>) field(main, "panelContents");
                assertTrue(contents.get("Combate").isVisible() && !contents.get("Mapa").isVisible(), "so um painel por vez");
                assertEquals("Combate", ((Label) field(main, "sidePanelTitle")).getText());

                rail.get("Combate").fire();
                assertFalse(split.getItems().contains(panel), "clicar de novo fecha: mapa maior");
                assertEquals(2, split.getItems().size(), "mapa e chat");
                rail.get("Rede").fire();
                assertTrue(split.getItems().contains(panel) && contents.get("Rede").isVisible(), "reabre");

                List<Label> toggles = all(contents.get("Combate"), Label.class).stream()
                        .filter(l -> l.getStyleClass().contains("hint-toggle")).toList();
                assertFalse(toggles.isEmpty(), "ajuda longa vira 'Como funciona'");
                Label round = (Label) field(main, "roundLabel");
                assertTrue(round.isManaged(), "estado do combate continua a vista");

                // pastas de cenas: a cena aparece dentro da pasta na arvore
                com.vttcaseiro.service.MesaService mesas = (com.vttcaseiro.service.MesaService) field(main, "mesaService");
                mesas.saveMap(new com.vttcaseiro.model.SceneModel(), "Mapa da cidade");
                mesas.saveMap(new com.vttcaseiro.model.SceneModel(), "Arena");
                com.vttcaseiro.library.SceneFolders folders = (com.vttcaseiro.library.SceneFolders) field(main, "sceneFolders");
                folders.addFolder("", "Cidade");
                folders.moveScene("Mapa da cidade", "Cidade");
                Method save = Main.class.getDeclaredMethod("saveSceneFolders");
                save.setAccessible(true);
                save.invoke(main);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TreeView<Object> tree = (javafx.scene.control.TreeView<Object>) field(main, "mapListView");
                var top = tree.getRoot().getChildren();
                assertEquals("Cidade", top.get(0).getValue().toString(), "pasta primeiro");
                assertEquals("Mapa da cidade", top.get(0).getChildren().get(0).getValue().toString(), "cena dentro da pasta");
                assertTrue(top.stream().anyMatch(i -> "Arena".equals(i.getValue().toString())), "cena sem pasta fica na raiz");
                assertTrue(Files.exists(mesa.resolve(com.vttcaseiro.library.SceneFolders.FILE)), "pastas salvas na mesa");
                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void onlineTablesLiveInAHiddenFolder(@TempDir Path base) {
        MesaSelectorView.JoinRequest byAddress = new MesaSelectorView.JoinRequest("Ana", "100.64.0.5", "55055", "", "", false, false, "");
        MesaSelectorView.JoinRequest byRelay = new MesaSelectorView.JoinRequest("Ana", "ponte.com", "55070", "K7P-Q2X", "", false, false, "");
        Path a = MesaSelectorView.onlineFolder(base, byAddress);
        Path r = MesaSelectorView.onlineFolder(base, byRelay);
        assertEquals(base.resolve(".online"), a.getParent(), "fora da lista de mesas");
        assertEquals("Online - 100.64.0.5 55055", a.getFileName().toString());
        assertEquals("Online - ponte K7P-Q2X", r.getFileName().toString());
        MesaSelectorView.JoinRequest evil = new MesaSelectorView.JoinRequest("Ana", "..\\..\\x", "1", "", "", false, false, "");
        assertTrue(MesaSelectorView.onlineFolder(base, evil).normalize().startsWith(base.resolve(".online")), "nome seguro");
    }
}
