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

                // desfazer dano: os PV voltam
                com.vttcaseiro.service.TableService table = (com.vttcaseiro.service.TableService) field(main, "table");
                TokenModel goblin = new TokenModel("Goblin", null);
                goblin.setMaxHp(20);
                goblin.setCurrentHp(20);
                ((SceneService) field(main, "sceneService")).addToken(goblin);
                java.util.Map<TokenModel, Integer[]> hpBefore = new java.util.LinkedHashMap<>();
                hpBefore.put(goblin, new Integer[]{20, 20, null});
                table.setHp(goblin, 8, 20, null);
                Method reg = Main.class.getDeclaredMethod("registerHpUndo", java.util.Map.class, String.class);
                reg.setAccessible(true);
                reg.invoke(main, hpBefore, "Dano");
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Runnable> undos = (java.util.Map<Object, Runnable>) field(main, "hpUndos");
                assertEquals(1, undos.size(), "a linha do chat ganha o Desfazer");
                undos.values().iterator().next().run();
                assertEquals(20, goblin.getCurrentHp(), "voltou aos PV de antes");
                assertTrue(undos.isEmpty(), "desfazer de novo nao faz nada");

                // ver como: o oculto some da tela do mestre ate voltar
                TokenModel spy = new TokenModel("Espiao", null);
                spy.setHiddenFromPlayers(true);
                ((SceneService) field(main, "sceneService")).addToken(spy);
                Method viewAs = Main.class.getDeclaredMethod("viewAs", java.util.Set.class, String.class);
                viewAs.setAccessible(true);
                viewAs.invoke(main, java.util.Set.of(t.getId()), "Lyra");
                Field tl = MapView.class.getDeclaredField("tokenLayer");
                tl.setAccessible(true);
                javafx.scene.Parent layer = (javafx.scene.Parent) tl.get(mv);
                java.util.function.Supplier<Boolean> spyVisible = () -> layer.getChildrenUnmodifiable().stream()
                        .filter(n -> n instanceof com.vttcaseiro.view.TokenNode tn && tn.getModel().getId().equals(spy.getId()))
                        .anyMatch(Node::isVisible);
                assertFalse(spyVisible.get(), "vendo como a Lyra: o espiao oculto some");
                assertTrue(((HBox) field(main, "perspectiveBar")).isVisible(), "aviso com o botao de voltar");
                Method end = Main.class.getDeclaredMethod("endViewAs");
                end.setAccessible(true);
                end.invoke(main);
                assertTrue(spyVisible.get(), "de volta a visao do mestre");

                // area de magia presa no cursor: clicar marca quem esta dentro
                goblin.setX(500);
                goblin.setY(500);
                java.util.concurrent.atomic.AtomicReference<java.util.List<TokenModel>> got = new java.util.concurrent.atomic.AtomicReference<>();
                mv.armArea("Circulo", 150, null, got::set, null);
                assertTrue(mv.isAreaArmed());
                Field cf = MapView.class.getDeclaredField("content");
                cf.setAccessible(true);
                Node content = (Node) cf.get(mv);
                double cell = ((SceneService) field(main, "sceneService")).getScene().getGrid().getCellSize();
                javafx.geometry.Point2D at = content.localToScene(500 + cell / 2, 500 + cell / 2);
                for (javafx.event.EventType<javafx.scene.input.MouseEvent> type : List.of(
                        javafx.scene.input.MouseEvent.MOUSE_MOVED, javafx.scene.input.MouseEvent.MOUSE_PRESSED)) {
                    mv.fireEvent(new javafx.scene.input.MouseEvent(type, at.getX(), at.getY(), 0, 0,
                            javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true,
                            false, true, null));
                }
                assertFalse(mv.isAreaArmed(), "o clique marcou a area");
                assertNotNull(got.get());
                assertTrue(got.get().stream().anyMatch(x -> x.getId().equals(goblin.getId())), "goblin dentro da esfera");
                assertFalse(got.get().stream().anyMatch(x -> x.getId().equals(t.getId())), "Lyra longe, fora");
                // --- copiar e colar: id novo, numero no nome, uma casa ao lado
                mv.selectTokens(List.of(goblin));
                Method copy = Main.class.getDeclaredMethod("copyTokens");
                copy.setAccessible(true);
                copy.invoke(main);
                Method paste = Main.class.getDeclaredMethod("pasteTokens");
                paste.setAccessible(true);
                paste.invoke(main);
                paste.invoke(main);
                SceneService ssv = (SceneService) field(main, "sceneService");
                List<TokenModel> gobs = ssv.getScene().getTokens().stream().filter(x -> x.getName().startsWith("Goblin")).toList();
                assertEquals(3, gobs.size(), "colou duas vezes");
                assertTrue(gobs.stream().anyMatch(x -> x.getName().equals("Goblin 2")));
                assertTrue(gobs.stream().anyMatch(x -> x.getName().equals("Goblin 3")));
                assertEquals(3, gobs.stream().map(TokenModel::getId).distinct().count(), "cada copia com id novo");

                // --- quem fica por cima: a camada maior e o ultimo desenhado
                TokenModel g2 = gobs.stream().filter(x -> x.getName().equals("Goblin 2")).findFirst().orElseThrow();
                g2.setLayer(-5);
                goblin.setLayer(9);
                mv.rebuildTokens();
                java.util.List<String> order = new java.util.ArrayList<>();
                for (Node n : layer.getChildrenUnmodifiable()) {
                    if (n instanceof com.vttcaseiro.view.TokenNode tn) order.add(tn.getModel().getName());
                }
                assertEquals("Goblin 2", order.get(0), "camada menor: por baixo");
                assertEquals("Goblin", order.get(order.size() - 1), "camada maior: por cima");

                // --- regua com paradas: Espaco no meio da medicao soma os trechos
                mv.setMeasuring(true);
                double cl = ssv.getScene().getGrid().getCellSize();
                java.util.function.BiConsumer<javafx.event.EventType<javafx.scene.input.MouseEvent>, double[]> fire = (type, w) -> {
                    javafx.geometry.Point2D sp = content.localToScene(w[0], w[1]);
                    mv.fireEvent(new javafx.scene.input.MouseEvent(type, sp.getX(), sp.getY(), 0, 0,
                            javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true,
                            false, true, null));
                };
                fire.accept(javafx.scene.input.MouseEvent.MOUSE_PRESSED, new double[]{cl * 0.5, cl * 0.5});
                fire.accept(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, new double[]{cl * 4.5, cl * 0.5});
                assertTrue(mv.addRulerWaypoint(), "parada marcada");
                fire.accept(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, new double[]{cl * 4.5, cl * 3.5});
                Field ml = MapView.class.getDeclaredField("measureLabel");
                ml.setAccessible(true);
                String measured = ((javafx.scene.control.Label) ml.get(mv)).getText();
                assertTrue(measured.contains("7 casas"), "4 pra direita + 3 pra baixo: " + measured);
                fire.accept(javafx.scene.input.MouseEvent.MOUSE_RELEASED, new double[]{cl * 4.5, cl * 3.5});
                mv.setMeasuring(false);

                // --- clima liga e desliga
                Method weather = Main.class.getDeclaredMethod("setSceneWeather", String.class);
                weather.setAccessible(true);
                weather.invoke(main, "neve");
                assertEquals("neve", mv.getWeather());
                assertEquals("neve", ssv.getScene().getWeather(), "vai salvo com a cena");

                // --- tela da TV: o que os jogadores veem (sem o oculto), com o clima
                Method tv = Main.class.getDeclaredMethod("openTvWindow");
                tv.setAccessible(true);
                tv.invoke(main);
                MapView tvView = (MapView) field(main, "tvView");
                assertNotNull(tvView, "abriu a tela da mesa");
                assertEquals("neve", tvView.getWeather());
                javafx.scene.Parent tvLayer = (javafx.scene.Parent) tl.get(tvView);
                assertFalse(tvLayer.getChildrenUnmodifiable().stream().anyMatch(n -> n instanceof com.vttcaseiro.view.TokenNode tn
                        && tn.getModel().getId().equals(spy.getId())), "token oculto nem aparece na TV");
                assertTrue(tvLayer.getChildrenUnmodifiable().stream().anyMatch(n -> n instanceof com.vttcaseiro.view.TokenNode tn
                        && tn.getModel().getId().equals(goblin.getId())), "os outros aparecem");
                weather.invoke(main, "");
                ((Stage) field(main, "tvStage")).close();
                // --- escudo do mestre: a Lyra (token na cena) aparece com CA e passivas
                Method gmScreen = Main.class.getDeclaredMethod("showGmScreen");
                gmScreen.setAccessible(true);
                gmScreen.invoke(main);
                Stage gs = (Stage) field(main, "gmScreenStage");
                assertTrue(gs.isShowing(), "abriu o escudo do mestre");
                java.util.List<String> labels = new java.util.ArrayList<>();
                java.util.ArrayDeque<javafx.scene.Parent> todo = new java.util.ArrayDeque<>(List.of(gs.getScene().getRoot()));
                while (!todo.isEmpty()) {
                    javafx.scene.Parent par = todo.pop();
                    if (par instanceof javafx.scene.control.ScrollPane sp && sp.getContent() instanceof javafx.scene.Parent cp) todo.push(cp);
                    for (Node n : par.getChildrenUnmodifiable()) {
                        if (n instanceof javafx.scene.control.Label lb) labels.add(lb.getText());
                        if (n instanceof javafx.scene.Parent pp) todo.push(pp);
                    }
                }
                assertTrue(labels.stream().anyMatch(x -> x.startsWith("Lyra")), "Lyra na tabela: " + labels);
                gs.close();

                // --- loja (jogando sozinho = mestre): compra tira o dinheiro e poe o item na ficha
                Method working = Main.class.getDeclaredMethod("workingShops");
                working.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<com.vttcaseiro.model.Shop> shopsNow = (java.util.List<com.vttcaseiro.model.Shop>) working.invoke(main);
                com.vttcaseiro.model.Shop smith = new com.vttcaseiro.model.Shop("Ferreiro");
                smith.getItems().add(new com.vttcaseiro.model.Shop.Item("Dagger", 200, -1, ""));
                shopsNow.add(smith);
                lyra.getInventory().getMoney().add(com.vttcaseiro.inventory.Currency.GOLD, 5);
                Method trade = Main.class.getDeclaredMethod("shopTrade", String.class, String.class, int.class,
                        CharacterSheet.class, boolean.class, String.class);
                trade.setAccessible(true);
                trade.invoke(main, smith.getId(), "Dagger", 1, lyra, false, "eu");
                assertEquals(300, lyra.getInventory().getMoney().totalInCopper(), "5 po - 2 po");
                assertTrue(lyra.getInventory().findByName("Dagger").isPresent(), "a adaga entrou no inventario");
                Method openShops = Main.class.getDeclaredMethod("openShops");
                openShops.setAccessible(true);
                openShops.invoke(main);
                com.vttcaseiro.view.ShopWindow sw = (com.vttcaseiro.view.ShopWindow) field(main, "shopWindow");
                assertTrue(sw.isShowing(), "a janela das lojas abre");

                // --- enquete jogando sozinho: vira uma linha do chat e conta o voto
                Method poll = Main.class.getDeclaredMethod("startPoll", String.class);
                poll.setAccessible(true);
                poll.invoke(main, "Por onde? | Floresta | Estrada");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> polls = (java.util.Map<String, Object>) field(main, "pollsById");
                assertEquals(1, polls.size(), "enquete criada");
                stage.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
