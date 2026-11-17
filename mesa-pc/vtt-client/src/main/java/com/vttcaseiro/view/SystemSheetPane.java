package com.vttcaseiro.view;

import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.GameSystem;
import com.vttcaseiro.character.Resource;
import com.vttcaseiro.dice.DiceRoller;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Ficha de Tormenta20 / Ordem Paranormal (e de qualquer sistema "custom" do GameSystem):
 * atributos clicaveis, PV + recursos (PM, PE, SAN) com - e +, Defesa, pericias com treino,
 * ataques (teste por pericia + dano + critico), poderes/rituais e os campos do personagem.
 * Fica dentro da mesma janela da ficha (CharacterSheetView), que cuida de salvar e sincronizar.
 */
public class SystemSheetPane extends VBox {

    private final CharacterSheet sheet;
    private final GameSystem system;
    private final CharacterSheetView.Actions actions;
    private final Runnable changed;
    private final Consumer<String> rename;
    private final List<Runnable> updaters = new ArrayList<>();
    private boolean hidden;

    public SystemSheetPane(CharacterSheet sheet, CharacterSheetView.Actions actions, Runnable changed,
                           Consumer<String> rename, Node saveBox) {
        this.sheet = sheet;
        this.system = GameSystem.of(sheet);
        this.actions = actions;
        this.changed = () -> {
            changed.run();
            refresh();
        };
        this.rename = rename;
        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #1B1411;");

        TabPane tabs = new TabPane(
                tab("Perícias", buildSkills()),
                tab("Combate", buildCombat()),
                tab(system == GameSystem.ORDEM ? "Poderes e rituais" : "Poderes e magias", buildPowers()),
                tab("Personagem", buildCharacter()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().addAll(buildHeader(saveBox), buildStats(), tabs);
        refresh();
    }

    private void refresh() {
        for (Runnable r : updaters) r.run();
    }

    private void roll(String label, String expression) {
        actions.roll(sheet, label, expression, hidden);
    }

    private static Tab tab(String title, Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return new Tab(title, sp);
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("label-section");
        return l;
    }

    private static String signed(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }

    // ------------------------------------------------------------------ cabecalho

    private Node buildHeader(Node saveBox) {
        TextField name = new TextField(sheet.getName());
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Runnable commit = () -> {
            String v = name.getText() == null ? "" : name.getText().trim();
            if (v.isEmpty()) name.setText(sheet.getName());
            else if (!v.equals(sheet.getName())) rename.accept(v);
        };
        name.setOnAction(e -> commit.run());
        name.focusedProperty().addListener((o, was, now) -> { if (!now) commit.run(); });
        HBox.setHgrow(name, Priority.ALWAYS);

        Label badge = new Label(system.name());
        badge.getStyleClass().add("system-badge");

        GridPane id = new GridPane();
        id.setHgap(8);
        id.setVgap(4);
        int col = 0;
        if (system == GameSystem.TORMENTA20) {
            Spinner<Integer> lvl = new Spinner<>(1, 20, sheet.getLevel());
            lvl.setPrefWidth(72);
            lvl.valueProperty().addListener((o, a, v) -> { sheet.setLevel(v); changed.run(); });
            id.add(labeled("Nível", lvl), col++, 0);
        } else {
            id.add(labeled("NEX", fieldEditor("NEX", 70)), col++, 0);
        }
        id.add(labeled("Classe", fieldEditor("Classe", 140)), col++, 0);
        id.add(labeled("Jogador", textField(sheet.getPlayerName(), v -> { sheet.setPlayerName(v); changed.run(); }, 120)), col, 0);

        CheckBox hiddenBox = new CheckBox("Rolagens escondidas (só você e o mestre)");
        hiddenBox.selectedProperty().addListener((o, a, v) -> hidden = v);

        HBox top = new HBox(10, name, badge);
        top.setAlignment(Pos.CENTER_LEFT);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox second = new HBox(14, id, grow, new VBox(6, hiddenBox, saveBox));
        second.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, top, second);
    }

    private TextField fieldEditor(String key, double width) {
        return textField(sheet.getCustomFields().getOrDefault(key, ""), v -> {
            if (v.isBlank()) sheet.getCustomFields().remove(key);
            else sheet.getCustomFields().put(key, v);
            changed.run();
        }, width);
    }

    private static TextField textField(String initial, Consumer<String> onCommit, double width) {
        TextField f = new TextField(initial == null ? "" : initial);
        f.setPrefWidth(width);
        f.setOnAction(e -> onCommit.accept(f.getText().trim()));
        f.focusedProperty().addListener((o, was, now) -> { if (!now) onCommit.accept(f.getText().trim()); });
        return f;
    }

    private static VBox labeled(String label, Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("label-muted");
        l.setStyle("-fx-font-size: 11px;");
        return new VBox(2, l, field);
    }

    // ------------------------------------------------------------------ atributos + recursos

    private Node buildStats() {
        HBox attrs = new HBox(8);
        for (GameSystem.Attr a : system.attrs()) attrs.getChildren().add(attrBox(a));

        FlowPane res = new FlowPane(8, 8);
        res.getChildren().add(resourceBox("PV", () -> sheet.getHpCurrent() == null ? 0 : sheet.getHpCurrent(),
                v -> sheet.setHpCurrent(v), () -> sheet.getHpMax() == null ? 0 : sheet.getHpMax(),
                v -> { sheet.setHpMax(v); if (sheet.getHpCurrent() == null || sheet.getHpCurrent() > v) sheet.setHpCurrent(v); },
                "#D9544C"));
        for (String r : system.resources()) {
            Resource resource = sheet.findResource(r).orElseGet(() -> {
                Resource nr = new Resource(r, 4, Resource.NEVER);
                sheet.getResources().add(nr);
                return nr;
            });
            String color = r.equals("SAN") ? "#9B6BDF" : r.equals("PE") ? "#E8A23E" : "#5B8DEF";
            res.getChildren().add(resourceBox(r, resource::getCurrent, resource::setCurrent, resource::getMax,
                    v -> { resource.setMax(v); resource.setCurrent(Math.min(resource.getCurrent(), v)); }, color));
        }
        res.getChildren().add(defenseBox());
        return new VBox(8, attrs, res);
    }

    private Node attrBox(GameSystem.Attr a) {
        Label key = new Label(a.key());
        key.getStyleClass().add("attr-key");
        TextField value = new TextField(String.valueOf(system.attr(sheet, a.key())));
        value.setPrefColumnCount(3);
        value.setAlignment(Pos.CENTER);
        value.getStyleClass().add("attr-value");
        Runnable commit = () -> {
            try {
                int v = Integer.parseInt(value.getText().trim());
                if (v != system.attr(sheet, a.key())) {
                    sheet.getCustomAttributes().put(a.key(), v);
                    changed.run();
                }
            } catch (NumberFormatException ex) {
                value.setText(String.valueOf(system.attr(sheet, a.key())));
            }
        };
        value.setOnAction(e -> commit.run());
        value.focusedProperty().addListener((o, was, now) -> { if (!now) commit.run(); });
        Label sub = new Label();
        sub.getStyleClass().add("label-muted");
        updaters.add(() -> sub.setText(system == GameSystem.TORMENTA20
                ? "mod " + signed(system.attrModifier(sheet, a.key()))
                : system.testDice(sheet, a.key()).replace("kh1", " (maior)").replace("kl1", " (menor)")));
        Button r = new Button("Rolar");
        r.getStyleClass().add("roll-chip");
        r.setTooltip(new Tooltip("Teste de " + a.label()));
        r.setOnAction(e -> roll(a.label(), system.attrTest(sheet, a.key())));
        VBox box = new VBox(3, key, value, sub, r);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("attr-card");
        Tooltip.install(box, new Tooltip(a.label()));
        return box;
    }

    private Node resourceBox(String name, IntSupplier cur, IntConsumer setCur, IntSupplier max, IntConsumer setMax,
                             String color) {
        Label title = new Label(name);
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label value = new Label();
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Region bar = new Region();
        bar.setPrefHeight(6);
        bar.setMaxHeight(6);
        Region barBg = new Region();
        barBg.setPrefHeight(6);
        barBg.setStyle("-fx-background-color: #352920; -fx-background-radius: 3;");
        javafx.scene.layout.StackPane barBox = new javafx.scene.layout.StackPane(barBg, bar);
        barBox.setAlignment(Pos.CENTER_LEFT);
        barBox.setPrefWidth(130);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        updaters.add(() -> {
            value.setText(cur.getAsInt() + " / " + max.getAsInt());
            double f = max.getAsInt() <= 0 ? 0 : Math.max(0, Math.min(1, cur.getAsInt() / (double) max.getAsInt()));
            bar.setMaxWidth(130 * f);
            bar.setPrefWidth(130 * f);
        });
        Button minus = new Button("-");
        Button plus = new Button("+");
        minus.setOnAction(e -> { setCur.accept(Math.max(0, cur.getAsInt() - 1)); changed.run(); });
        plus.setOnAction(e -> { setCur.accept(Math.min(max.getAsInt(), cur.getAsInt() + 1)); changed.run(); });
        TextField maxField = textField(String.valueOf(max.getAsInt()), v -> {
            try {
                int m = Math.max(0, Integer.parseInt(v));
                if (m != max.getAsInt()) { setMax.accept(m); changed.run(); }
            } catch (NumberFormatException ignored) { }
        }, 52);
        maxField.setTooltip(new Tooltip("Máximo de " + name));
        HBox buttons = new HBox(4, minus, plus, new Label("máx"), maxField);
        if (!name.equals("PV")) {
            Button lose = new Button("Perder...");
            lose.setTooltip(new Tooltip("Rola a perda (ex.: 1d6 de SAN) e desconta"));
            lose.setOnAction(e -> {
                javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(name.equals("SAN") ? "1d6" : "1");
                d.setTitle("Perder " + name);
                d.setHeaderText(null);
                d.setContentText("Quanto (número ou dados, ex.: 1d4, 2d6):");
                d.showAndWait().map(String::trim).filter(x -> !x.isEmpty()).ifPresent(x -> loseResource(name, cur, setCur, x));
            });
            buttons.getChildren().add(lose);
        }
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(3, new HBox(8, title, value), barBox, buttons);
        box.getStyleClass().add("attr-card");
        return box;
    }

    private Node defenseBox() {
        Label title = new Label("Defesa");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #E8A23E;");
        TextField def = textField(sheet.getArmorClass() == null ? "" : String.valueOf(sheet.getArmorClass()), v -> {
            try {
                sheet.setArmorClass(Integer.parseInt(v));
                changed.run();
            } catch (NumberFormatException ignored) { }
        }, 60);
        def.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label hint = new Label();
        hint.getStyleClass().add("label-muted");
        hint.setStyle("-fx-font-size: 11px;");
        updaters.add(() -> hint.setText("sugerida: " + system.suggestedDefense(sheet) + " + armadura"));
        VBox box = new VBox(3, title, def, hint);
        box.getStyleClass().add("attr-card");
        Tooltip.install(box, new Tooltip("Ataques contra este personagem comparam com a Defesa (como a CA)."));
        return box;
    }

    // ------------------------------------------------------------------ pericias

    private Node buildSkills() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(4);
        g.setPadding(new Insets(8));
        String[] heads = {"Perícia", "Atrib.", "Treino", "Outros", "Total", ""};
        for (int i = 0; i < heads.length; i++) {
            Label h = new Label(heads[i]);
            h.getStyleClass().add("label-section");
            g.add(h, i, 0);
        }
        int row = 1;
        for (GameSystem.SkillDef d : system.skills()) {
            Label name = new Label(d.name());
            Label attr = new Label(d.attr());
            attr.getStyleClass().add("label-muted");
            ComboBox<String> training = new ComboBox<>();
            training.getItems().addAll(system.trainingLevels());
            training.getSelectionModel().select(system.training(sheet, d.name()));
            training.setOnAction(e -> {
                int t = training.getSelectionModel().getSelectedIndex();
                if (t <= 0) sheet.getCustomSkills().remove(d.name());
                else sheet.getCustomSkills().put(d.name(), t);
                changed.run();
            });
            TextField other = textField(system.otherBonus(sheet, d.name()) == 0 ? "" : String.valueOf(system.otherBonus(sheet, d.name())), v -> {
                try {
                    int b = v.isBlank() ? 0 : Integer.parseInt(v.replace("+", ""));
                    if (b == 0) sheet.getCustomSkillBonus().remove(d.name());
                    else sheet.getCustomSkillBonus().put(d.name(), b);
                    changed.run();
                } catch (NumberFormatException ignored) { }
            }, 48);
            Label total = new Label();
            total.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFCF7F;");
            updaters.add(() -> {
                total.setText(system == GameSystem.ORDEM
                        ? system.skillTest(sheet, d.name()).replace("kh1", "").replace("kl1", "(menor)")
                        : signed(system.skillBonus(sheet, d.name())));
                name.setStyle(system.training(sheet, d.name()) > 0 ? "-fx-font-weight: bold;" : "");
            });
            Button r = new Button("Rolar");
            r.getStyleClass().add("roll-chip");
            r.setOnAction(e -> roll(d.name(), system.skillTest(sheet, d.name())));
            g.addRow(row++, name, attr, training, other, total, r);
        }
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(130);
        g.getColumnConstraints().add(c0);
        Label hint = new Label(system == GameSystem.ORDEM
                ? "Teste = tantos d20 quanto o atributo, fica o maior (atributo 0: 2d20, fica o menor) + treino."
                : "Teste = 1d20 + metade do nível + atributo + treino (+2/+4/+6 pelo nível) + outros.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        return new VBox(6, g, hint);
    }

    // ------------------------------------------------------------------ combate

    private Node buildCombat() {
        VBox list = new VBox(8);
        list.setPadding(new Insets(8));
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.getChildren().clear();
            list.getChildren().add(section("Ataques"));
            for (Attack a : new ArrayList<>(sheet.getAttacks())) list.getChildren().add(attackRow(a, rebuild[0]));
            Button add = new Button("+ Ataque");
            add.setOnAction(e -> {
                sheet.addAttack(new Attack("Nova arma", system == GameSystem.ORDEM ? "Luta" : "Luta", "1d6", "x2"));
                changed.run();
                rebuild[0].run();
            });
            Label hint = new Label("\"Teste\" pode ser o nome de uma perícia (Luta, Pontaria) com ou sem bônus extra "
                    + "(\"Luta + 2\"), ou um bônus fixo (+7). \"Crítico\" é só anotação (ex.: 19/x2).");
            hint.getStyleClass().add("label-muted");
            hint.setWrapText(true);
            list.getChildren().addAll(add, hint);
        };
        rebuild[0].run();
        return list;
    }

    private Node attackRow(Attack a, Runnable rebuild) {
        TextField name = textField(a.getName(), v -> { a.setName(v); changed.run(); }, 150);
        TextField test = textField(a.getAttackBonusOrDc(), v -> { a.setAttackBonusOrDc(v); changed.run(); }, 100);
        TextField dmg = textField(a.getDamageAndType(), v -> { a.setDamageAndType(v); changed.run(); }, 120);
        TextField crit = textField(a.getNotes(), v -> { a.setNotes(v); changed.run(); }, 70);
        Button atk = new Button("Ataque");
        atk.getStyleClass().add("button-primary");
        atk.setOnAction(e -> roll(a.getName() + " (ataque)", system.attackTest(sheet, a)));
        Button dano = new Button("Dano");
        dano.setOnAction(e -> a.damageExpression().ifPresent(d -> roll(a.getName() + " (dano)", d)));
        Button critBtn = new Button("Crítico");
        critBtn.setTooltip(new Tooltip("Rola o dano com os dados multiplicados (x2)"));
        critBtn.setOnAction(e -> a.damageExpression().ifPresent(d -> roll(a.getName() + " (crítico)", DiceRoller.doubleDice(d))));
        Button del = new Button("✕");
        del.getStyleClass().add("button-flat");
        del.setOnAction(e -> { sheet.getAttacks().remove(a); changed.run(); rebuild.run(); });
        HBox row = new HBox(6, labeled("Arma", name), labeled("Teste", test), labeled("Dano", dmg),
                labeled("Crítico", crit), new VBox(2, new Label(" "), new HBox(4, atk, dano, critBtn, del)));
        row.setAlignment(Pos.BOTTOM_LEFT);
        row.getStyleClass().add("attr-card");
        return row;
    }

    // ------------------------------------------------------------------ poderes

    private Node buildPowers() {
        VBox list = new VBox(8);
        list.setPadding(new Insets(8));
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.getChildren().clear();
            for (CharacterTrait t : new ArrayList<>(sheet.getTraits())) {
                TextField name = textField(t.getName(), v -> { t.setName(v); changed.run(); }, 260);
                TextArea desc = new TextArea(t.getDescription());
                desc.setWrapText(true);
                desc.setPrefRowCount(3);
                desc.focusedProperty().addListener((o, was, now) -> {
                    if (!now && !desc.getText().equals(t.getDescription())) { t.setDescription(desc.getText()); changed.run(); }
                });
                Button chat = new Button("Chat");
                chat.setTooltip(new Tooltip("Mostrar no chat (sem gastar nada)"));
                chat.setOnAction(e -> actions.chat(sheet, t.toChatText()));
                Button del = new Button("✕");
                del.getStyleClass().add("button-flat");
                del.setOnAction(e -> { sheet.removeTrait(t); changed.run(); rebuild[0].run(); });
                // custo (ex.: 2 PM) e o botao Usar, que desconta e avisa no chat
                TextField cost = textField(t.getCost() == 0 ? "" : String.valueOf(t.getCost()), v -> {
                    try {
                        t.setCost(v.isBlank() ? 0 : Integer.parseInt(v.trim()));
                        changed.run();
                    } catch (NumberFormatException ignored) { }
                }, 44);
                cost.setPromptText("custo");
                ComboBox<String> res = new ComboBox<>();
                res.getItems().addAll(system.resources());
                res.setValue(t.getCostResource() != null && system.resources().contains(t.getCostResource())
                        ? t.getCostResource() : system.resources().get(0));
                res.setOnAction(e -> { t.setCostResource(res.getValue()); changed.run(); });
                Button use = new Button("Usar");
                use.getStyleClass().add("button-primary");
                use.setTooltip(new Tooltip("Desconta o custo do recurso e manda o poder pro chat"));
                use.setOnAction(e -> usePower(t, res.getValue()));
                HBox head = new HBox(6, name, cost, res, use, chat, del);
                head.setAlignment(Pos.CENTER_LEFT);
                // em janela estreita quem encolhe e o nome; custo, recurso e botoes ficam inteiros
                name.setMinWidth(110);
                HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
                cost.setMinWidth(54);
                cost.setPrefWidth(54);
                for (javafx.scene.layout.Region r : List.of(res, use, chat, del)) r.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                VBox card = new VBox(4, head, desc);
                card.getStyleClass().add("attr-card");
                list.getChildren().add(card);
            }
            Button add = new Button(system == GameSystem.ORDEM ? "+ Poder ou ritual" : "+ Poder ou magia");
            add.setOnAction(e -> {
                sheet.addTrait(new CharacterTrait("Novo poder", "", "feat"));
                changed.run();
                rebuild[0].run();
            });
            list.getChildren().add(add);
        };
        rebuild[0].run();
        return list;
    }

    /** Usa um poder: desconta o custo (se tiver) e avisa no chat. */
    void usePower(CharacterTrait t, String resourceName) {
        String res = resourceName == null ? (t.getCostResource() != null ? t.getCostResource() : system.resources().get(0)) : resourceName;
        int cost = t.getCost();
        if (cost > 0) {
            Resource r = sheet.findResource(res).orElse(null);
            if (r == null || r.getCurrent() < cost) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                        sheet.getName() + " não tem " + res + " suficiente para " + t.getName() + " (custa " + cost
                                + ", tem " + (r == null ? 0 : r.getCurrent()) + ").");
                a.setHeaderText(null);
                a.show();
                return;
            }
            r.setCurrent(r.getCurrent() - cost);
            changed.run();
            actions.chat(sheet, sheet.getName() + " usa " + t.getName() + " (-" + cost + " " + res + ", restam "
                    + r.getCurrent() + ")" + (t.getDescription() == null || t.getDescription().isBlank() ? "" : ": " + t.getDescription()));
        } else {
            actions.chat(sheet, t.toChatText());
        }
    }

    /** Perde recurso (ex.: SAN 1d6): rola, desconta e avisa no chat. */
    void loseResource(String name, IntSupplier cur, IntConsumer setCur, String expression) {
        try {
            com.vttcaseiro.dice.RollResult roll = DiceRoller.roll(expression);
            int lost = Math.max(0, roll.total());
            int before = cur.getAsInt();
            setCur.accept(Math.max(0, before - lost));
            changed.run();
            actions.chat(sheet, sheet.getName() + " perde " + lost + " " + name + " (" + roll.detail() + ") -- restam "
                    + cur.getAsInt() + (cur.getAsInt() == 0 ? ". Chegou a zero!" : "."));
        } catch (com.vttcaseiro.dice.DiceException ex) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Expressão inválida: " + expression);
            a.setHeaderText(null);
            a.show();
        }
    }

    // ------------------------------------------------------------------ personagem

    private Node buildCharacter() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(6);
        g.setPadding(new Insets(8));
        int i = 0;
        for (String f : system.fields()) {
            if (f.equals("Classe") || f.equals("NEX")) continue; // ja estao no topo
            g.add(labeled(f, fieldEditor(f, 200)), i % 3, i / 3);
            i++;
        }
        TextArea inv = area(sheet.getCustomFields().getOrDefault("Inventário", ""), v -> {
            sheet.getCustomFields().put("Inventário", v);
            changed.run();
        });
        TextArea story = area(sheet.getBackstory(), v -> { sheet.setBackstory(v); changed.run(); });
        return new VBox(8, section("Dados do personagem"), g, section("Inventário"), inv,
                section("História e anotações"), story);
    }

    private static TextArea area(String initial, Consumer<String> onCommit) {
        TextArea a = new TextArea(initial == null ? "" : initial);
        a.setWrapText(true);
        a.setPrefRowCount(6);
        a.focusedProperty().addListener((o, was, now) -> { if (!now) onCommit.accept(a.getText()); });
        return a;
    }
}
