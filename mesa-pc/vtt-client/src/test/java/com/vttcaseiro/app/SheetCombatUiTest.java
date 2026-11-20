package com.vttcaseiro.app;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.service.CharacterService;
import com.vttcaseiro.view.CharacterSheetView;
import com.vttcaseiro.view.MapView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aba Combate da ficha, com a tela montada de verdade: clica nos botoes como o jogador clicaria.
 * Pula sozinho onde nao ha tela.
 */
class SheetCombatUiTest {

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

    interface ThrowingRunnable {
        void run() throws Exception;
    }

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

    static List<Node> find(Node root, Predicate<Node> test) {
        List<Node> out = new ArrayList<>();
        if (test.test(root)) out.add(root);
        if (root instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) out.addAll(find(c, test));
        // abas: o conteudo de cada Tab nao e filho direto do TabPane
        if (root instanceof javafx.scene.control.TabPane tp) {
            for (var tab : tp.getTabs()) if (tab.getContent() != null) out.addAll(find(tab.getContent(), test));
        }
        if (root instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) out.addAll(find(sp.getContent(), test));
        return out;
    }

    /** Linha (HBox) que tem um Label com esse texto. */
    static Parent rowWithLabel(Node root, String label) {
        return (Parent) find(root, n -> n instanceof javafx.scene.layout.HBox h && h.getChildrenUnmodifiable().stream()
                .anyMatch(c -> c instanceof Label l && label.equals(l.getText()))).get(0);
    }

    static Button button(Node root, String text) {
        return (Button) find(root, n -> n instanceof Button b && text.equals(b.getText())).get(0);
    }

    record Opened(Main main, CharacterSheet sheet, CharacterSheetView view, Node root) {}

    private Opened open(Path dir) throws Exception {
        Main main = new Main();
        AtomicReference<Opened> out = new AtomicReference<>();
        onFx(() -> {
            Field stageField = Main.class.getDeclaredField("stage");
            stageField.setAccessible(true);
            stageField.set(main, new Stage());
            call(main, "onMesaChosen", new Class<?>[]{MesaRepository.class}, new LibraryStorage(dir));
            CharacterService chars = (CharacterService) field(main, "characterService");
            CharacterSheet sheet = chars.create("Elora");
            sheet.setHpMax(20);
            sheet.setHpCurrent(20);
            chars.save(sheet);
            call(main, "openSheetView", new Class<?>[]{CharacterSheet.class}, sheet);
            @SuppressWarnings("unchecked")
            Map<String, CharacterSheetView> views = (Map<String, CharacterSheetView>) field(main, "openSheetViews");
            CharacterSheetView view = views.get(sheet.getId());
            Stage stage = (Stage) field(view, "stage");
            out.set(new Opened(main, sheet, view, stage.getScene().getRoot()));
        });
        return out.get();
    }

    private void close(Opened o) throws Exception {
        onFx(() -> {
            o.view().close();
            for (javafx.stage.Window w : new ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w instanceof Stage s) s.close();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> chat(Main main) throws Exception {
        return ((javafx.collections.ObservableList<LogEntry>) field(main, "logEntries")).stream()
                .map(LogEntry::text).filter(java.util.Objects::nonNull).toList();
    }

    @Test
    void attackButtonRollsHitAndDamageTogether() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                o.sheet().addAttack(new com.vttcaseiro.character.Attack("Espada", "+5", "1d8+3 cortante", ""));
                o.view().reload(o.sheet());
            });
            // 40 ataques: cada um tem o acerto e, menos na falha critica, o dano logo em seguida
            for (int i = 0; i < 40; i++) {
                onFx(() -> {
                    Stage stage = (Stage) field(o.view(), "stage");
                    Node root = stage.getScene().getRoot();
                    @SuppressWarnings("unchecked")
                    javafx.scene.control.TableView<com.vttcaseiro.character.Attack> table =
                            (javafx.scene.control.TableView<com.vttcaseiro.character.Attack>) find(root,
                                    n -> n instanceof javafx.scene.control.TableView<?> tv && !tv.getItems().isEmpty()
                                            && tv.getItems().get(0) instanceof com.vttcaseiro.character.Attack).get(0);
                    table.getSelectionModel().select(0);
                    button(root, "Atacar").fire();
                });
            }
            onFx(() -> {
                List<String> log = chat(o.main());
                List<String> attacks = log.stream().filter(t -> t.contains("Ataque: Espada")).toList();
                assertEquals(40, attacks.size(), log.toString());
                long damage = log.stream().filter(t -> t.contains("Dano: Espada") || t.contains("Dano CRÍTICO: Espada")).count();
                long fumbles = log.stream().filter(t -> t.contains("falha crítica")).count();
                assertEquals(40, damage + fumbles, "cada ataque tem dano ou o aviso de falha critica");
                for (String crit : log.stream().filter(t -> t.contains("Dano CRÍTICO: Espada")).toList()) {
                    assertTrue(crit.contains("2d8"), "critico dobra os dados: " + crit);
                }
                assertTrue(find(((Stage) field(o.view(), "stage")).getScene().getRoot(),
                        n -> n instanceof Button b && ("Rolar dano".equals(b.getText()) || "Dano crítico".equals(b.getText()))).isEmpty(),
                        "os botoes separados sairam");
            });
        } finally {
            close(o);
        }
    }

    @Test
    void deathSaveRollMarksTheBoxesAndHitDiceArePerType() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                o.sheet().setHpCurrent(0);
                o.sheet().setLevel(5);
                o.view().reload(o.sheet());
            });
            onFx(() -> {
                Node root = ((Stage) field(o.view(), "stage")).getScene().getRoot();
                button(root, "Rolar teste contra a morte").fire();
                CharacterSheet s = o.view().getSheet();
                boolean marked = s.getDeathSaveSuccesses() + s.getDeathSaveFailures() > 0 || s.getHpCurrent() == 1;
                assertTrue(marked, "a rolagem marcou uma caixa (ou o 20 trouxe de volta com 1 PV)");
                assertTrue(chat(o.main()).stream().anyMatch(t -> t.contains("teste contra a morte")), "resultado no chat");
                // ficha sem a caixa de escudo
                assertTrue(find(root, n -> n instanceof javafx.scene.control.CheckBox c && c.getText() != null
                        && c.getText().equals("Escudo (+2)")).isEmpty(), "caixa de escudo saiu da CA");
                assertFalse(find(root, n -> n instanceof Button b && "Rolar iniciativa".equals(b.getText())).isEmpty());
            });
            onFx(() -> {
                CharacterSheet s = o.view().getSheet();
                s.setHitDiceCount("d10", 3);
                s.setHitDiceCount("d8", 2);
                o.view().reload(s);
                Node root = ((Stage) field(o.view(), "stage")).getScene().getRoot();
                long spendButtons = find(root, n -> n instanceof Button b && "Gastar".equals(b.getText())).size();
                assertEquals(4, spendButtons, "um Gastar por tipo de dado");
                assertEquals(5, s.hitDiceMax());
            });
        } finally {
            close(o);
        }
    }

    @Test
    void portraitGoesToTheTokenAndTheMobSheetLooksLikeTheCharacterOne() throws Exception {
        // retrato da ficha -> imagem do token (sem tela)
        CharacterSheet sheet = new CharacterSheet("Aria");
        com.vttcaseiro.model.TokenModel token = com.vttcaseiro.service.TokenLink.tokenFromSheet(sheet);
        sheet.setPortraitImageAssetId("asset-novo");
        assertTrue(com.vttcaseiro.service.TokenLink.pushSheetToToken(sheet, token));
        assertEquals("asset-novo", token.getImageAssetId());

        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        com.vttcaseiro.mob.MobSheet mob = new com.vttcaseiro.mob.MobSheet("Goblin");
        mob.setDexterity(14);
        mob.getSkillBonuses().put("stealth", 6);
        mob.getSavingThrowBonuses().put("dex", 4);
        mob.getActions().add(new com.vttcaseiro.character.CharacterTrait("Cimitarra",
                "Melee Weapon Attack: +4 to hit, reach 5 ft. Hit: 5 (1d6 + 2) slashing damage.", "action"));
        java.util.List<String> rolled = new ArrayList<>();
        com.vttcaseiro.view.MobSheetView.Actions actions = new com.vttcaseiro.view.MobSheetView.Actions() {
            @Override public void rollD20(String label, int modifier, com.vttcaseiro.character.RollKind kind,
                                          com.vttcaseiro.character.Ability ability, com.vttcaseiro.character.RollMode mode,
                                          boolean hidden) { rolled.add(label + " " + modifier); }
            @Override public void roll(String label, String expression, boolean hidden) { rolled.add(label + " " + expression); }
            @Override public void chat(String text) { rolled.add(text); }
            @Override public void attack(String attackLabel, int bonus, String damageLabel, String damageExpression,
                                         com.vttcaseiro.character.RollMode mode, boolean hidden) {
                rolled.add("ATAQUE " + attackLabel + " " + bonus + " " + damageExpression);
            }
            @Override public String hpText() { return "7 / 7"; }
        };
        onFx(() -> {
            com.vttcaseiro.view.MobSheetView view = new com.vttcaseiro.view.MobSheetView(mob, "Goblin 2", actions, t -> { });
            view.show();
            Node root = javafx.stage.Window.getWindows().stream().filter(w -> w instanceof Stage s && "Goblin 2".equals(s.getTitle()))
                    .map(w -> ((Stage) w).getScene().getRoot()).findFirst().orElseThrow();
            assertFalse(find(root, n -> n instanceof Label l && "DESTREZA".equals(l.getText())).isEmpty(), "caixas de atributo");
            assertFalse(find(root, n -> n instanceof Label l && "7 / 7".equals(l.getText())).isEmpty(), "PV do token");
            Button stealth = (Button) find(root, n -> n instanceof Button b && b.getText() != null
                    && b.getText().contains("Furtividade")).get(0);
            stealth.fire();
            Button save = (Button) find(root, n -> n instanceof Button b && b.getText() != null
                    && b.getText().startsWith("◆ Salvaguarda  +4")).get(0);
            save.fire();
            button(root, "Atacar").fire();
            for (javafx.stage.Window w : new ArrayList<>(javafx.stage.Window.getWindows())) if (w instanceof Stage s) s.close();
        });
        assertTrue(rolled.stream().anyMatch(r -> r.contains("Furtividade") && r.endsWith(" 6")), rolled.toString());
        assertTrue(rolled.stream().anyMatch(r -> r.contains("Salvaguarda de DES") && r.endsWith(" 4")), rolled.toString());
        assertTrue(rolled.stream().anyMatch(r -> r.startsWith("ATAQUE Goblin 2 - Ataque: Cimitarra 4 1d6+2")), rolled.toString());
    }

    @Test
    void skillMarkCyclesAndNamesRollWithTheShownValue() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                CharacterSheet s = o.sheet();
                s.setLevel(5);
                s.setDexterity(14);
                o.view().reload(s);
                Node root = ((Stage) field(o.view(), "stage")).getScene().getRoot();
                Parent stealthRow = (Parent) find(root, n -> n instanceof javafx.scene.layout.HBox h && h.getChildrenUnmodifiable()
                        .stream().anyMatch(c -> c instanceof Button b && "Furtividade".equals(b.getText()))).get(0);
                Button mark = (Button) stealthRow.getChildrenUnmodifiable().get(0);
                assertEquals("○", mark.getText());
                mark.fire();
                assertEquals("●", mark.getText());
                mark.fire();
                assertEquals("◉", mark.getText());
                mark.fire();
                assertEquals("◐", mark.getText());
                assertEquals(3, s.skillBonus(com.vttcaseiro.character.Skill.STEALTH), "pau pra toda obra");
                // extra geral nas pericias pela linha de cima
                s.setSkillBonusAll(2);
                o.view().reload(s);
                root = ((Stage) field(o.view(), "stage")).getScene().getRoot();
                button(root, "Furtividade").fire();
                button(root, "Salvaguarda").fire();
                button(root, "DESTREZA").fire();
            });
            onFx(() -> {
                List<String> log = chat(o.main());
                assertTrue(log.stream().anyMatch(t -> t.contains("Furtividade") && t.contains("+ 5")), "rola com 3 + 2 de extra: " + log);
                assertTrue(log.stream().anyMatch(t -> t.contains("Salvaguarda de")), log.toString());
                assertTrue(log.stream().anyMatch(t -> t.contains("Teste de")), log.toString());
            });
        } finally {
            close(o);
        }
    }

    @Test
    void areaDrawnOnTheMapGivesThePlaceForTheEffectImage() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                com.vttcaseiro.view.MapView map = (com.vttcaseiro.view.MapView) field(o.main(), "mapView");
                assertTrue(map.effectPlacement().isEmpty(), "sem area desenhada");
                map.setTemplating(true, "Cone");
                Method draw = com.vttcaseiro.view.MapView.class.getDeclaredMethod("drawTemplate",
                        double.class, double.class, double.class, double.class);
                draw.setAccessible(true);
                draw.invoke(map, 100.0, 100.0, 240.0, 100.0);
                double[] box = map.effectPlacement().orElseThrow();
                assertEquals("Cone", map.lastTemplateShape());
                assertEquals(140, box[2], 1e-6);

                // o que o botao "Imagem" faz depois de escolher a imagem: objeto cobrindo a area
                com.vttcaseiro.service.ImageAssetService images =
                        (com.vttcaseiro.service.ImageAssetService) field(o.main(), "imageAssetService");
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.io.ByteArrayOutputStream gif = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "gif", gif);
                Path file = dir.resolve("fogo.gif");
                Files.write(file, gif.toByteArray());
                String assetId = images.importFile(file);
                assertTrue(assetId.endsWith(".gif"), "GIF guardado como GIF: " + assetId);
                com.vttcaseiro.model.MapObject obj = new com.vttcaseiro.model.MapObject(assetId, box[0], box[1], box[2], box[3]);
                obj.setRotation(box[4]);
                com.vttcaseiro.service.TableService table = (com.vttcaseiro.service.TableService) field(o.main(), "table");
                assertTrue(table.addObject(obj), "mestre pode por o efeito");
                com.vttcaseiro.service.SceneService scenes = (com.vttcaseiro.service.SceneService) field(o.main(), "sceneService");
                assertEquals(1, scenes.getScene().getObjects().size());
                map.clearTemplate();
                assertTrue(map.effectPlacement().isEmpty());
                assertFalse(find(((Stage) field(o.main(), "stage")).getScene().getRoot(),
                        n -> n instanceof Button b && "Imagem".equals(b.getText())).isEmpty(), "botao na barra de Area");
            });
        } finally {
            close(o);
        }
    }

    @Test
    void tokenElevationFacingMenuAndVisionLayers() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                com.vttcaseiro.model.TokenModel t = com.vttcaseiro.service.TokenLink.tokenFromSheet(o.sheet());
                call(o.main(), "addTokenToScene", new Class<?>[]{com.vttcaseiro.model.TokenModel.class}, t);
                com.vttcaseiro.view.MapView map = (com.vttcaseiro.view.MapView) field(o.main(), "mapView");
                MapView.TokenActions actions = (MapView.TokenActions) field(o.main(), "tokenActions");
                Method menu = MapView.class.getDeclaredMethod("buildTokenMenu", com.vttcaseiro.model.TokenModel.class, boolean.class);
                menu.setAccessible(true);
                List<String> items = ((javafx.scene.control.ContextMenu) menu.invoke(map, t, true)).getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText).filter(java.util.Objects::nonNull).toList();
                assertTrue(items.containsAll(List.of("Altura...", "Olhando para", "Visão e luz...")), items.toString());

                actions.editToken(t, "teste", x -> {
                    x.setElevationFeet(30);
                    x.setFacingDegrees(90.0);
                    x.setVisionFeet(60);
                    x.setLightFeet(20);
                    x.setDimLightFeet(20);
                });
                map.rebuildTokens();
                assertFalse(find(map, n -> n instanceof Label l && "▲ 30 ft (9 m)".equals(l.getText())).isEmpty(), "selo de altura");
                assertFalse(find(map, n -> n instanceof javafx.scene.shape.Polygon p && p.getPoints().size() == 6
                        && "#FFCF7F".equalsIgnoreCase(colorHex(p.getFill()))).isEmpty(), "seta da direcao");

                // camadas de visao em cena escura e em penumbra (previa do mestre)
                com.vttcaseiro.service.TableService table = (com.vttcaseiro.service.TableService) field(o.main(), "table");
                table.setVisionSettings(true, true, false, false);
                map.setGmPreview(true);
                map.updateVision();
                table.setVisionSettings(true, false, true, false);
                map.updateVision();
                map.setGmPreview(false);
            });
        } finally {
            close(o);
        }
    }

    private static String colorHex(javafx.scene.paint.Paint p) {
        if (!(p instanceof javafx.scene.paint.Color c)) return "";
        return String.format("#%02X%02X%02X", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    @Test
    void mapSizeCanBeChosenInCellsAndStretchesTheImage() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                com.vttcaseiro.service.SceneService scenes = (com.vttcaseiro.service.SceneService) field(o.main(), "sceneService");
                com.vttcaseiro.service.ImageAssetService images =
                        (com.vttcaseiro.service.ImageAssetService) field(o.main(), "imageAssetService");
                com.vttcaseiro.view.MapView map = (com.vttcaseiro.view.MapView) field(o.main(), "mapView");
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
                Path file = dir.resolve("mapa.png");
                javax.imageio.ImageIO.write(img, "png", file.toFile());
                scenes.getScene().setBackgroundImageAssetId(images.importFile(file));
                scenes.getScene().getGrid().setCellSize(50);
                map.loadBackgroundImage();
                assertEquals(200, map.mapWidth(), 0.01, "automatico: tamanho da imagem");
                assertEquals(100, map.mapHeight(), 0.01);

                scenes.setMapSizeCells(30, 20);
                assertEquals(30 * 50, map.mapWidth(), 0.01, "30 casas de 50 px");
                assertEquals(20 * 50, map.mapHeight(), 0.01);
                assertTrue(scenes.getScene().getGrid().hasCustomMapSize(), "fica guardado na grade da cena (vai pela rede junto)");

                scenes.setGridCellSize(70);
                assertEquals(30 * 70, map.mapWidth(), 0.01, "acompanha o tamanho da casa");

                scenes.setMapSizeCells(0, 0);
                assertEquals(200, map.mapWidth(), 0.01, "volta pro automatico");
                assertFalse(find(((Stage) field(o.main(), "stage")).getScene().getRoot(),
                        n -> n instanceof Label l && "Tamanho do mapa (em casas)".equals(l.getText())).isEmpty(), "controles na aba Mapa");
            });
        } finally {
            close(o);
        }
    }

    @Test
    void addingAResourceShowsAndKeepsIt() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                Parent row = rowWithLabel(o.root(), "Novo");
                TextField name = (TextField) find(row, n -> n instanceof TextField).get(0);
                name.setText("Fúria");
                button(row, "Adicionar").fire();
                assertEquals(1, o.sheet().getResources().size(), "o recurso entrou na ficha");
                assertEquals("Fúria", o.sheet().getResources().get(0).getName());
            });
            // a janela pode remontar (salvar / sincronizar): o recurso continua na tela
            Thread.sleep(1500);
            onFx(() -> {
                Stage stage = (Stage) field(o.view(), "stage");
                Node root = stage.getScene().getRoot();
                CharacterSheet current = o.view().getSheet();
                assertEquals(1, current.getResources().size(), "continua na ficha depois de salvar");
                assertFalse(find(root, n -> n instanceof Label l && "Fúria".equals(l.getText())).isEmpty(),
                        "o recurso aparece na tela");
            });
        } finally {
            close(o);
        }
    }

    @Test
    void bastionTabSavesWhatThePlayerTypes() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela disponivel");
        Path dir = Files.createTempDirectory("sheet-ui");
        Opened o = open(dir);
        try {
            onFx(() -> {
                javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) field(o.view(), "tabs");
                javafx.scene.control.Tab bastion = tabs.getTabs().stream().filter(t -> "Bastião".equals(t.getText()))
                        .findFirst().orElseThrow();
                tabs.getSelectionModel().select(bastion);
                Node content = bastion.getContent();
                List<TextField> fields = find(content, n -> n instanceof TextField).stream().map(n -> (TextField) n).toList();
                TextField name = fields.stream().filter(f -> "Nome do bastião".equals(f.getPromptText())).findFirst().orElseThrow();
                name.setText("Torre do Corvo");
                assertEquals("Torre do Corvo", o.sheet().getBastion().getName());
                List<javafx.scene.control.ComboBox<?>> combos = find(content, n -> n instanceof javafx.scene.control.ComboBox<?>)
                        .stream().distinct().<javafx.scene.control.ComboBox<?>>map(n -> (javafx.scene.control.ComboBox<?>) n).toList();
                assertEquals(12, combos.size(), "espaco e ordem nas 6 instalacoes especiais");
                @SuppressWarnings("unchecked")
                javafx.scene.control.ComboBox<String> order = (javafx.scene.control.ComboBox<String>) combos.get(1);
                order.setValue("Pesquisar");
                order.getOnAction().handle(new javafx.event.ActionEvent());
                assertEquals("research", o.sheet().getBastion().getSpecialFacilities().get(0).getOrder());
                String snap = System.getProperty("vtt.snap");
                if (snap != null) {
                    Stage stage = (Stage) field(o.view(), "stage");
                    javafx.scene.image.WritableImage img = stage.getScene().getRoot().snapshot(null, null);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage((int) img.getWidth(),
                            (int) img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < out.getHeight(); y++)
                        for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(out, "png", new java.io.File(snap));
                }
            });
        } finally {
            close(o);
        }
    }
}
