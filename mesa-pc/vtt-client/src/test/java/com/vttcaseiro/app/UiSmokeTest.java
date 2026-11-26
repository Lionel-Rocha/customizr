package com.vttcaseiro.app;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.service.CharacterService;
import com.vttcaseiro.service.SceneService;
import com.vttcaseiro.service.TableService;
import com.vttcaseiro.service.TokenLink;
import com.vttcaseiro.view.CharacterSheetView;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.Window;
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

/**
 * Abre o programa DE VERDADE (JavaFX) numa mesa temporaria e usa as mesmas
 * acoes que os botoes/menus usam. Pega o tipo de erro que so aparece com a
 * tela montada (NullPointer na construcao de uma aba, menu sem item, etc.).
 * <p>
 * Pula sozinho onde nao ha tela (CI sem display) ou com -Dvtt.skipUi=true.
 */
class UiSmokeTest {

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
            fxReady = false; // sem tela: o teste pula
        }
        if (fxReady) Platform.setImplicitExit(false);
    }

    /** Roda no thread do JavaFX e devolve/relanca o que acontecer. */
    private static void onFx(ThrowingRunnable r) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "acao na tela demorou demais");
        if (error.get() instanceof Exception e) throw e;
        if (error.get() instanceof Error e) throw e;
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o, args);
    }

    private static boolean hasLabel(Node root, String text) {
        if (root instanceof Label l && text.equals(l.getText())) return true;
        if (root instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) if (hasLabel(child, text)) return true;
        }
        return false;
    }

    @Test
    void mesaInteiraFunciona() throws Exception {
        // Pasta propria (em vez de @TempDir): o salvamento automatico ainda pode gravar um arquivo
        // logo depois das janelas fecharem; a limpeza abaixo espera e tenta de novo.
        Path dir = Files.createTempDirectory("ui-smoke");
        try {
            mesaInteira(dir);
        } finally {
            deleteWithRetry(dir);
        }
    }

    private static void deleteWithRetry(Path dir) throws InterruptedException {
        for (int attempt = 0; attempt < 15; attempt++) {
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                return;
            } catch (java.io.IOException | java.io.UncheckedIOException ex) {
                Thread.sleep(300);
            }
        }
    }

    private volatile Object mainRef;

    private void mesaInteira(Path dir) throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel -- teste de interface pulado");
        Main main = new Main();
        mainRef = main;
        try {
            onFx(() -> {
                Field stageField = Main.class.getDeclaredField("stage");
                stageField.setAccessible(true);
                stageField.set(main, new Stage());
                call(main, "onMesaChosen", new Class<?>[]{MesaRepository.class}, new LibraryStorage(dir));
                // conteudo dos livros: so existe na maquina local (sem ele, o aviso esperaria um clique)
                if (UiSmokeTest.class.getResource("/seed/bestiary-xmm.json") != null) {
                    call(main, "onLoadSeedContent", new Class<?>[]{});
                }
            });

            AtomicReference<CharacterSheet> sheetRef = new AtomicReference<>();
            onFx(() -> {
                CharacterService chars = (CharacterService) field(main, "characterService");
                CharacterSheet sheet = chars.create("Elora");
                sheet.setHpMax(20);
                sheet.setHpCurrent(20);
                chars.save(sheet);
                sheetRef.set(sheet);
                call(main, "openSheetView", new Class<?>[]{CharacterSheet.class}, sheet);
                TokenModel t = TokenLink.tokenFromSheet(sheet);
                call(main, "addTokenToScene", new Class<?>[]{TokenModel.class}, t);
                @SuppressWarnings("unchecked")
                javafx.scene.control.ListView<String> mobs = (javafx.scene.control.ListView<String>) field(main, "mobListView");
                if (UiSmokeTest.class.getResource("/seed/bestiary-xmm.json") != null) {
                    assertTrue(mobs.getItems().size() > 400, "bestiario carregado");
                } else {
                    // sem o conteudo dos livros (ex.: no GitHub): um goblin feito na hora faz o mesmo papel
                    com.vttcaseiro.mob.MobSheet goblinSheet = new com.vttcaseiro.mob.MobSheet("Goblin");
                    goblinSheet.setArmorClass(15);
                    goblinSheet.setHitPointsAverage(10); // como o do bestiario
                    ((com.vttcaseiro.service.MobService) field(main, "mobService")).save(goblinSheet);
                    call(main, "refreshMobList", new Class<?>[]{});
                }
                mobs.getSelectionModel().select(mobs.getItems().stream().filter(n -> n.startsWith("Goblin")).findFirst().orElseThrow());
                call(main, "onPlaceSelectedMob", new Class<?>[]{});
            });

            onFx(() -> {
                SceneService scenes = (SceneService) field(main, "sceneService");
                MapView.TokenActions actions = (MapView.TokenActions) field(main, "tokenActions");
                TableService table = (TableService) field(main, "table");
                MapView map = (MapView) field(main, "mapView");
                CharacterSheet sheet = sheetRef.get();
                TokenModel elora = scenes.getScene().getTokens().get(0);
                TokenModel goblin = scenes.getScene().getTokens().get(1);
                assertNotNull(goblin.getMobSheetId(), "token de monstro ligado a ficha do monstro");

                // Dano pelo menu do token passa pela ficha; Ctrl+Z volta.
                actions.damage(elora, 6);
                assertEquals(14, sheet.getHpCurrent());
                assertEquals(14, elora.getCurrentHp());
                call(main, "onUndo", new Class<?>[]{});
                assertEquals(20, sheet.getHpCurrent(), "desfazer volta o PV pela ficha");
                assertEquals(20, elora.getCurrentHp());

                // Mover e desfazer.
                double oldX = goblin.getX();
                goblin.setX(oldX + 70);
                ((com.vttcaseiro.service.UndoService) field(main, "undo")).record("Mover", tokenAt(goblin, oldX), goblin);
                call(main, "onUndo", new Class<?>[]{});
                assertEquals(oldX, goblin.getX());

                // Camada do mestre: badge "OCULTO" e item de menu certo.
                actions.setHidden(goblin, true);
                assertTrue(goblin.isHiddenFromPlayers());
                assertTrue(hasLabel(map, "OCULTO"), "token oculto mostra o selo pro mestre");
                Method menu = MapView.class.getDeclaredMethod("buildTokenMenu", TokenModel.class, boolean.class);
                menu.setAccessible(true);
                List<String> items = ((ContextMenu) menu.invoke(map, goblin, true)).getItems().stream()
                        .map(MenuItem::getText).filter(java.util.Objects::nonNull).toList();
                assertTrue(items.contains("Revelar aos jogadores"), items.toString());
                assertTrue(items.contains("Abrir ficha"), "mestre abre a ficha do monstro: " + items);
                assertTrue(items.contains("Efeito com duração (rodadas)..."), items.toString());

                // Neblina.
                table.editFog(0, 0, 3, 3, true);
                assertTrue(scenes.getScene().getFog().isEnabled());
                assertEquals(16, scenes.getScene().getFog().revealedCount());
                map.redrawFog();

                // Combate com efeito com duracao.
                table.rollInitiativeForAllAndStart();
                table.addTimedEffect(elora, "Bencao", 1);
                table.nextTurn();
                table.nextTurn();
                assertFalse(sheet.getEffects().contains("Bencao"), "efeito venceu e saiu da ficha");

                // Tamanho pelo menu (com desfazer), ping e itens novos do menu.
                actions.setSize(goblin, 3);
                assertEquals(3, goblin.getWidthCells());
                call(main, "onUndo", new Class<?>[]{});
                assertEquals(1, goblin.getWidthCells(), "desfazer volta o tamanho");
                List<String> items2 = ((ContextMenu) menu.invoke(map, goblin, true)).getItems().stream()
                        .map(MenuItem::getText).filter(java.util.Objects::nonNull).toList();
                assertTrue(items2.contains("Tamanho"), items2.toString());
                map.showPing(100, 100, "Ana");

                // Salvaguarda em grupo e descanso longo pela mesa.
                table.groupSave(com.vttcaseiro.character.Ability.DEXTERITY, 100, List.of(elora), "3", true);
                assertEquals(17, sheet.getHpCurrent());
                table.groupLongRest();
                assertEquals(20, sheet.getHpCurrent());

                // Macro: cria, aparece na linha do chat e roda.
                com.vttcaseiro.library.MacroStore macros = (com.vttcaseiro.library.MacroStore) field(main, "macroStore");
                macros.save(List.of(new com.vttcaseiro.library.MacroStore.Macro("Furtivo", "/r 1d20+7")));
                call(main, "refreshMacroRow", new Class<?>[]{});
                javafx.scene.layout.FlowPane macroRow = (javafx.scene.layout.FlowPane) field(main, "macroRow");
                assertTrue(macroRow.getChildren().stream().anyMatch(n -> n instanceof javafx.scene.control.Button b
                        && "Furtivo".equals(b.getText())), "botao da macro aparece");
                call(main, "runMacro", new Class<?>[]{com.vttcaseiro.library.MacroStore.Macro.class}, macros.load().get(0));

                // Alvos: mira no goblin, ataque acerta, o dano da ficha cai nele sozinho.
                map.toggleTarget(goblin);
                assertEquals(List.of(goblin), map.getTargets());
                assertTrue(((ContextMenu) menu.invoke(map, goblin, true)).getItems().stream()
                        .anyMatch(i -> "Tirar a mira".equals(i.getText())));
                int goblinHp = goblin.getCurrentHp();
                table.resolveAttack("sheet:" + sheet.getId(), "Elora", 30, 10, map.getTargets());
                CharacterSheetView.Actions sheetActions = (CharacterSheetView.Actions) field(main, "sheetActions");
                sheet.setEffects(List.of("Furia"));
                sheetActions.roll(sheet, "Dano: Espada (cortante)", "2d2", false);
                assertTrue(goblinHp - goblin.getCurrentHp() >= 4, "dano caiu no alvo acertado, com +2 da Furia");
                sheet.setEffects(List.of());
                assertFalse(table.hasPendingAttack("sheet:" + sheet.getId()));
                map.clearTargets();
                assertTrue(map.getTargets().isEmpty());

                // Desenho: entra na cena e na camada do mapa; a borracha acha o mais perto.
                table.addDrawing(com.vttcaseiro.model.Drawing.path(List.of(10.0, 10.0, 200.0, 10.0), "#ff0000", 3));
                table.addDrawing(com.vttcaseiro.model.Drawing.text(50, 80, "Armadilha", "#ffffff"));
                assertEquals(2, scenes.getScene().getDrawings().size());
                assertTrue(map.drawingNear(100, 12, 12).isPresent());
                map.setDrawTool(MapView.TOOL_PEN, null);
                map.setDrawTool(null, null);
                assertEquals(2, table.clearDrawings());

                // Diario: mestre cria, revela, a aba mostra.
                com.vttcaseiro.library.HandoutStore journal =
                        (com.vttcaseiro.library.HandoutStore) field(main, "handoutStore");
                journal.save(List.of(new com.vttcaseiro.model.Handout("Carta do Barao", "Venham sozinhos.")));
                call(main, "refreshJournal", new Class<?>[]{});
                @SuppressWarnings("unchecked")
                javafx.collections.ObservableList<com.vttcaseiro.model.Handout> entries =
                        (javafx.collections.ObservableList<com.vttcaseiro.model.Handout>) field(main, "journalItems");
                assertEquals(1, entries.size());
                call(main, "setHandoutRevealed", new Class<?>[]{com.vttcaseiro.model.Handout.class, boolean.class},
                        entries.get(0), true);
                assertTrue(journal.load().get(0).isRevealed());
                call(main, "showHandout", new Class<?>[]{com.vttcaseiro.model.Handout.class}, entries.get(0));

                // Paredes e visao: mestre desenha, liga a visao, a previa mostra a mascara.
                table.addWall(new com.vttcaseiro.model.Wall(0, 300, 700, 300, false));
                com.vttcaseiro.model.Wall door = new com.vttcaseiro.model.Wall(700, 300, 770, 300, true);
                table.addWall(door);
                table.setVisionSettings(true, false);
                map.setGmPreview(true);
                map.setGmPreview(false);
                table.toggleDoor(door);
                assertTrue(door.isOpen());
                assertTrue(map.isVisibleToMe(10, 10), "mestre sem previa ve tudo");
                Method allow = MapView.class.getDeclaredMethod("allowMove", TokenModel.class, double[].class);
                allow.setAccessible(true);
                assertTrue((boolean) allow.invoke(map, elora, new double[]{1000, 1000}), "mestre atravessa parede");
                // Memoria do explorado: so pra jogador (o mestre ve tudo).
                assertEquals(0, map.exploredCount());

                // Ctrl+Z desfaz parede desenhada pelo handler (mesma pilha dos tokens).
                MapView.WallHandler walls = (MapView.WallHandler) field(map, "wallHandler");
                com.vttcaseiro.model.Wall extra = new com.vttcaseiro.model.Wall(0, 500, 300, 500, false);
                walls.add(extra);
                assertTrue(scenes.getScene().findWall(extra.getId()).isPresent());
                call(main, "onUndo", new Class<?>[]{});
                assertTrue(scenes.getScene().findWall(extra.getId()).isEmpty(), "desfazer tira a parede");
                walls.remove(door);
                call(main, "onUndo", new Class<?>[]{});
                assertTrue(scenes.getScene().findWall(door.getId()).isPresent(), "desfazer volta a porta apagada");

                table.setVisionSettings(false, false);
                table.clearWalls();

                // Selecao de varios + setas + atalhos de teclado.
                actions.setHidden(goblin, false);
                map.selectTokens(List.of(elora, goblin));
                assertEquals(2, map.getSelectedTokens().size());
                double ex = elora.getX();
                double gx = goblin.getX();
                assertEquals(2, map.nudgeSelection(1, 0), "mestre anda os dois");
                assertEquals(ex + 70, elora.getX());
                assertEquals(gx + 70, goblin.getX());
                Method key = Main.class.getDeclaredMethod("onMapKey", javafx.scene.input.KeyEvent.class);
                key.setAccessible(true);
                java.util.function.Function<javafx.scene.input.KeyCode, javafx.scene.input.KeyEvent> press = code ->
                        new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code,
                                false, false, false, false);
                key.invoke(main, press.apply(javafx.scene.input.KeyCode.T));
                assertEquals(2, map.getTargets().size(), "T mira a selecao");
                key.invoke(main, press.apply(javafx.scene.input.KeyCode.DIGIT3));
                assertEquals(MapView.TOOL_PEN, field(map, "drawTool"), "3 = lapis");
                key.invoke(main, press.apply(javafx.scene.input.KeyCode.ESCAPE));
                assertNull(field(map, "drawTool"), "Esc desliga a ferramenta");
                assertTrue(map.getSelectedTokens().isEmpty(), "Esc limpa a selecao");
                map.clearTargets();

                // Tabela aleatoria pelo chat e a calculadora por tras.
                call(main, "onChatInput", new Class<?>[]{String.class}, "/tabela clima");
                assertTrue(((javafx.collections.ObservableList<?>) field(main, "logEntries")).stream()
                        .anyMatch(o -> String.valueOf(o).contains("Tabela Clima")), "tabela de exemplo rolada");

                // Som: o mestre toca um som da biblioteca e ele toca aqui (arquivo local).
                com.vttcaseiro.library.AudioAssetStore audio =
                        (com.vttcaseiro.library.AudioAssetStore) field(main, "audioStore");
                java.nio.file.Path wav = dir.resolve("teste.wav");
                Files.write(wav, com.vttcaseiro.net.PackageDNetTest.tone());
                String soundId = audio.importFile(wav);
                call(main, "gmAudio", new Class<?>[]{com.vttcaseiro.model.AudioCommand.class},
                        com.vttcaseiro.model.AudioCommand.play("music", soundId, "Teste"));
                SoundPlayer sp = (SoundPlayer) field(main, "soundPlayer");
                assertEquals("Teste", sp.nowPlaying("music"));
                sp.stopAll();
                assertNull(sp.nowPlaying("music"));
                // Dados animados: aparecem no mapa numa rolagem local.
                call(main, "onRollDice", new Class<?>[]{String.class}, "3d6");
                com.vttcaseiro.view.DiceAnimator anim = (com.vttcaseiro.view.DiceAnimator) field(main, "diceAnimator");
                assertEquals(3, anim.getChildren().size(), "3 dados animando");

                // Ficha de monstro abre pelo token.
                actions.openSheet(goblin);
                assertTrue(Window.getWindows().stream().anyMatch(w -> w instanceof Stage s
                        && s.getTitle() != null && s.getTitle().startsWith("Goblin")), "janela do monstro abriu");
            });

            // Historico do chat foi gravado.
            assertTrue(Files.readString(dir.resolve("chat-log.jsonl")).contains("Bencao"));
        } finally {
            onFx(() -> {
                for (Window w : List.copyOf(Window.getWindows())) {
                    if (w instanceof Stage s) s.close();
                }
            });
            // grava o pendente agora e para o timer do salvamento automatico
            onFx(() -> {
                try {
                    Object mref = mainRef;
                    if (mref != null) {
                        java.lang.reflect.Method flush = mref.getClass().getDeclaredMethod("flushSceneAutosave");
                        flush.setAccessible(true);
                        flush.invoke(mref);
                        Object timer = field(mref, "sceneAutosave");
                        if (timer instanceof javafx.animation.Animation a) a.stop();
                    }
                } catch (Exception ignored) {
                    // so limpeza
                }
            });
        }
    }

    private static TokenModel tokenAt(TokenModel t, double x) {
        TokenModel copy = com.vttcaseiro.service.UndoService.copy(t);
        copy.setX(x);
        return copy;
    }
}
