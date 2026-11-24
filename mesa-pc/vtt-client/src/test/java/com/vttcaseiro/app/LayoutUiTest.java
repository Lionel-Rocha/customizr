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

    /** Foto da janela inteira (so com -Dvtt.snap=pasta). */
    private static void snapStage(Stage stage, String file) {
        String dir = System.getProperty("vtt.snap");
        if (dir == null) return;
        javafx.scene.Parent root = stage.getScene().getRoot();
        root.applyCss();
        root.layout();
        javafx.scene.image.WritableImage img = root.snapshot(null, null);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(), (int) img.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        try {
            javax.imageio.ImageIO.write(out, "png", new java.io.File(dir, file));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
                stage.setWidth(1440);
                stage.setHeight(880);
                stage.show();
                snapStage(stage, "tela-mapa.png");

                @SuppressWarnings("unchecked")
                Map<String, ToggleButton> rail = (Map<String, ToggleButton>) field(main, "railButtons");
                assertEquals(List.of("Mesa", "Combate", "Personagens", "Mundo", "Som", "Rede"), new ArrayList<>(rail.keySet()),
                        "uma barra so, com abas agrupadas");
                SplitPane split = (SplitPane) field(main, "mainSplit");
                Node panel = (Node) field(main, "sidePanel");
                assertEquals(2, split.getItems().size(), "mapa e a barra da direita (sem painel separado do chat)");
                assertTrue(split.getItems().contains(panel) && rail.get("Mesa").isSelected(), "abre no chat");

                rail.get("Combate").fire();
                snapStage(stage, "tela-combate.png");
                @SuppressWarnings("unchecked")
                Map<String, Node> contents = (Map<String, Node>) field(main, "panelContents");
                assertTrue(contents.get("Combate").isVisible() && !contents.get("Chat").isVisible(), "so uma secao por vez");
                assertEquals("Combate", ((Label) field(main, "sidePanelTitle")).getText());

                rail.get("Combate").fire();
                assertFalse(split.getItems().contains(panel), "clicar de novo recolhe: mapa maior");
                assertEquals(2, split.getItems().size(), "mapa e a faixa fina de icones");
                snapStage(stage, "tela-recolhida.png");
                rail.get("Rede").fire();
                assertTrue(split.getItems().contains(panel) && contents.get("Rede").isVisible(), "reabre");

                // aba com duas secoes: seletor pequeno
                rail.get("Personagens").fire();
                assertTrue(contents.get("Fichas").isVisible());
                Method open = Main.class.getDeclaredMethod("openPanel", String.class);
                open.setAccessible(true);
                open.invoke(main, "Party");
                assertTrue(contents.get("Party").isVisible() && rail.get("Personagens").isSelected());
                open.invoke(main, "Mapa");
                assertTrue(rail.get("Mundo").isSelected(), "cenas ficam na aba Mundo");
                snapStage(stage, "tela-mundo.png");

                // modo Preparar (mestre): o token colocado nasce escondido dos jogadores
                Field mode = Main.class.getDeclaredField("gmMode");
                mode.setAccessible(true);
                mode.set(main, "preparar");
                Method add = Main.class.getDeclaredMethod("addTokenToScene", com.vttcaseiro.model.TokenModel.class);
                add.setAccessible(true);
                com.vttcaseiro.model.TokenModel prepared = new com.vttcaseiro.model.TokenModel("Emboscada", null);
                add.invoke(main, prepared);
                assertTrue(prepared.isHiddenFromPlayers(), "preparando: nasce escondido");
                mode.set(main, "jogar");
                com.vttcaseiro.model.TokenModel normal = new com.vttcaseiro.model.TokenModel("Lyra", null);
                add.invoke(main, normal);
                assertFalse(normal.isHiddenFromPlayers(), "jogando: aparece normal");

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
