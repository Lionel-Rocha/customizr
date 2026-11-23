package com.vttcaseiro.view;

import com.vttcaseiro.model.Campaign;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Janela "Campanha": jogadores (XP, ouro, presenca), calendario com clima, PdMs, aventuras, sessoes e anotacoes
 * do mestre. O mestre edita; o jogador ve so o que esta marcado como visivel (somente leitura). Cada mudanca do
 * mestre chama {@code onChanged} (o Main grava e manda pra mesa).
 */
public final class CampaignView {
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Stage stage = new Stage();
    private final boolean gm;
    private final Consumer<Campaign> onChanged;
    private Campaign data;
    private final TabPane tabs = new TabPane();
    /** Evita salvar enquanto a tela esta sendo preenchida. */
    private boolean filling;

    public CampaignView(Window owner, Campaign data, boolean gm, Consumer<Campaign> onChanged) {
        this.data = data;
        this.gm = gm;
        this.onChanged = onChanged;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle(gm ? "Campanha (mestre)" : "Campanha");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rebuild();
        Scene sc = new Scene(tabs, 900, 640);
        stage.setScene(sc);
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public Stage getStage() {
        return stage;
    }

    /** Chegou versao nova (rede): troca os dados e redesenha mantendo a aba aberta. */
    public void reload(Campaign fresh) {
        this.data = fresh;
        int sel = tabs.getSelectionModel().getSelectedIndex();
        rebuild();
        if (sel >= 0 && sel < tabs.getTabs().size()) tabs.getSelectionModel().select(sel);
    }

    private void rebuild() {
        tabs.getTabs().setAll(
                new Tab("Jogadores", playersTab()),
                new Tab("Calendário", calendarTab()),
                new Tab("PdMs", npcsTab()),
                new Tab("Aventuras", adventuresTab()),
                new Tab("Sessões", sessionsTab()));
        if (gm) {
            tabs.getTabs().add(new Tab("Tesouro", treasureTab()));
            tabs.getTabs().add(new Tab("Encontros", encountersTab()));
            tabs.getTabs().add(new Tab("Anotações do mestre", notesTab()));
        }
    }

    private void changed() {
        if (!filling && gm && onChanged != null) onChanged.accept(data);
    }

    static String today() {
        return LocalDate.now().format(BR);
    }

    /** Data de hoje no formato dos lancamentos (dd/mm/aaaa). */
    public static String todayText() {
        return today();
    }

    // ================================================================ util de formulario

    private TextField field(String value, Consumer<String> set) {
        TextField f = new TextField(value == null ? "" : value);
        f.setEditable(gm);
        f.textProperty().addListener((o, a, b) -> {
            set.accept(b);
            changed();
        });
        return f;
    }

    private TextArea area(String value, String prompt, int rows, Consumer<String> set) {
        TextArea a = new TextArea(value == null ? "" : value);
        a.setWrapText(true);
        a.setPrefRowCount(rows);
        a.setPromptText(prompt);
        a.setEditable(gm);
        a.textProperty().addListener((o, x, b) -> {
            set.accept(b);
            changed();
        });
        return a;
    }

    private CheckBox visibleBox(boolean value, Consumer<Boolean> set, Runnable refreshList) {
        CheckBox c = new CheckBox("Visível aos jogadores");
        c.setSelected(value);
        c.setTooltip(new javafx.scene.control.Tooltip("Desmarcado: só você vê (o servidor nem manda pros jogadores)."));
        c.selectedProperty().addListener((o, a, b) -> {
            set.accept(b);
            refreshList.run();
            changed();
        });
        return c;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("label-muted");
        return l;
    }

    private static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        return l;
    }

    private static ScrollPane scroll(Node n) {
        ScrollPane sp = new ScrollPane(n);
        sp.setFitToWidth(true);
        return sp;
    }

    private Optional<Double> askNumber(String title, String label) {
        TextInputDialog d = new TextInputDialog();
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(label);
        return d.showAndWait().map(s -> s.trim().replace(',', '.')).flatMap(s -> {
            try {
                return Optional.of(Double.parseDouble(s));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        });
    }

    private Optional<String> askText(String title, String label, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(label);
        return d.showAndWait().map(String::trim);
    }

    /** Grade de formulario: a coluna dos rotulos nunca encolhe ("..."), a dos campos estica. */
    private static GridPane grid() {
        GridPane g = new GridPane();
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints();
        labels.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(labels, fields);
        return g;
    }

    static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * Lista a esquerda + editor a direita (padrao das abas de PdMs, aventuras, sessoes). {@code label} da o texto
     * da linha; {@code editor} monta o formulario do item escolhido.
     */
    private <T> Node masterDetail(List<T> items, Function<T, String> label, java.util.function.BiFunction<T, Runnable, Node> editor,
                                  Supplier<T> create, String addText, String empty) {
        ListView<T> list = new ListView<>();
        list.getItems().setAll(items);
        list.setPlaceholder(new Label(empty));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean emptyCell) {
                super.updateItem(item, emptyCell);
                setText(emptyCell || item == null ? null : label.apply(item));
            }
        });
        VBox detail = new VBox(8);
        detail.setPadding(new Insets(10));
        Runnable refreshCells = list::refresh;
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            filling = true;
            detail.getChildren().setAll(b == null ? List.of(muted(gm ? "Escolha um item na lista ou crie um novo."
                    : "Escolha um item na lista.")) : List.of(editor.apply(b, refreshCells)));
            filling = false;
        });
        VBox left = new VBox(6, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        if (gm) {
            Button add = new Button(addText, Icons.icon("add", 14));
            add.setOnAction(e -> {
                T item = create.get();
                items.add(0, item);
                list.getItems().add(0, item);
                list.getSelectionModel().select(item);
                changed();
            });
            Button del = new Button("Apagar", Icons.icon("trash", 14));
            del.setOnAction(e -> {
                T item = list.getSelectionModel().getSelectedItem();
                if (item == null) return;
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                        "Apagar \"" + label.apply(item) + "\"?");
                a.initOwner(stage);
                a.setHeaderText(null);
                if (a.showAndWait().filter(bt -> bt == javafx.scene.control.ButtonType.OK).isEmpty()) return;
                items.remove(item);
                list.getItems().remove(item);
                changed();
            });
            left.getChildren().add(0, new HBox(6, add, del));
        }
        left.setPadding(new Insets(10));
        left.setMinWidth(220);
        detail.getChildren().add(muted(gm ? "Escolha um item na lista ou crie um novo." : "Escolha um item na lista."));
        SplitPane split = new SplitPane(left, scroll(detail));
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.32);
        if (!items.isEmpty()) list.getSelectionModel().select(0);
        return split;
    }

    private static String eye(boolean visible) {
        return visible ? "" : "🙈 ";
    }

    // ================================================================ Jogadores

    private Node playersTab() {
        List<Campaign.PlayerRecord> players = data.getPlayers();
        Node md = masterDetail(players,
                p -> eye(p.visible) + (p.character.isBlank() ? p.player : p.character + " (" + p.player + ")")
                        + "  •  " + num(p.totalXp()) + " XP  •  " + num(p.totalGold()) + " PO",
                this::playerEditor,
                () -> {
                    Campaign.PlayerRecord p = new Campaign.PlayerRecord();
                    p.player = "Novo jogador";
                    return p;
                }, "Jogador", "Nenhum jogador ainda.");
        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 10, 0, 10));
        if (gm) {
            Button xpAll = new Button("+XP pra todos");
            xpAll.setOnAction(e -> askNumber("XP pra todos", "Quanto de XP cada um ganha:").ifPresent(v -> {
                String why = askText("XP pra todos", "Motivo (opcional):", "Sessão").orElse("");
                data.giveXpToAll(v, today(), why);
                changed();
                reload(data);
            }));
            Button goldAll = new Button("+PO pra todos");
            goldAll.setOnAction(e -> askNumber("Ouro pra todos", "Quantas PO cada um ganha (negativo tira):").ifPresent(v -> {
                String why = askText("Ouro pra todos", "Motivo (opcional):", "Tesouro").orElse("");
                data.giveGoldToAll(v, today(), why);
                changed();
                reload(data);
            }));
            Button presence = new Button("Marcar presença de todos");
            presence.setTooltip(new javafx.scene.control.Tooltip("Soma 1 presença pra cada jogador visível na lista."));
            presence.setOnAction(e -> {
                players.forEach(p -> p.presences++);
                changed();
                reload(data);
            });
            box.getChildren().add(new HBox(6, xpAll, goldAll, presence));
            if (onlinePlayers != null) {
                Button fromTable = new Button("+ Jogadores conectados");
                fromTable.setTooltip(new javafx.scene.control.Tooltip("Adiciona quem está conectado na mesa e ainda não está na lista."));
                fromTable.setOnAction(e -> {
                    int added = 0;
                    for (String name : onlinePlayers.get()) {
                        String clean = name.replaceAll("\\s*\\(mestre\\)$", "").trim();
                        if (clean.isEmpty() || name.endsWith("(mestre)")) continue;
                        boolean exists = players.stream().anyMatch(p -> p.player.equalsIgnoreCase(clean));
                        if (!exists) {
                            Campaign.PlayerRecord p = new Campaign.PlayerRecord();
                            p.player = clean;
                            players.add(p);
                            added++;
                        }
                    }
                    if (added > 0) {
                        changed();
                        reload(data);
                    }
                });
                ((HBox) box.getChildren().get(0)).getChildren().add(fromTable);
            }
        }
        VBox all = new VBox(box, md);
        VBox.setVgrow(md, Priority.ALWAYS);
        return all;
    }

    /** Quem esta conectado (nomes), pro botao "+ Jogadores conectados". */
    private Supplier<List<String>> onlinePlayers;

    public CampaignView withOnlinePlayers(Supplier<List<String>> s) {
        this.onlinePlayers = s;
        rebuild();
        return this;
    }

    private Node playerEditor(Campaign.PlayerRecord p, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        TextField player = field(p.player, v -> {
            p.player = v;
            refresh.run();
        });
        TextField character = field(p.character, v -> {
            p.character = v;
            refresh.run();
        });
        g.addRow(0, new Label("Jogador"), player);
        g.addRow(1, new Label("Personagem"), character);
        Label totals = new Label();
        Runnable updateTotals = () -> totals.setText("XP: " + num(p.totalXp()) + "  (nível " + Campaign.level5e(p.totalXp())
                + " no D&D 5e)   •   Ouro: " + num(p.totalGold()) + " PO   •   Presenças: " + p.presences
                + "   •   Faltas: " + p.absences);
        updateTotals.run();
        VBox box = new VBox(8, g, totals);
        if (gm) {
            HBox counters = new HBox(6);
            Button plusPresence = new Button("+ presença");
            plusPresence.setOnAction(e -> { p.presences++; updateTotals.run(); changed(); });
            Button plusAbsence = new Button("+ falta");
            plusAbsence.setOnAction(e -> { p.absences++; updateTotals.run(); changed(); });
            Button reset = new Button("Zerar presenças/faltas");
            reset.setOnAction(e -> { p.presences = 0; p.absences = 0; updateTotals.run(); changed(); });
            counters.getChildren().addAll(plusPresence, plusAbsence, reset);
            box.getChildren().add(counters);
            box.getChildren().add(visibleBox(p.visible, v -> p.visible = v, refresh));
        }
        box.getChildren().add(ledger("XP", p.xp, () -> { updateTotals.run(); refresh.run(); }));
        box.getChildren().add(ledger("Ouro (PO)", p.gold, () -> { updateTotals.run(); refresh.run(); }));
        if (gm) {
            box.getChildren().add(new Label("Anotações sobre o jogador (só você vê)"));
            box.getChildren().add(area(p.gmNotes, "ex.: gosta de combate; está devendo um favor ao duque", 3, v -> p.gmNotes = v));
        }
        return box;
    }

    /** Lancamentos (XP ou ouro): lista com data, valor e motivo; o mestre adiciona e apaga. */
    private Node ledger(String what, List<Campaign.Entry> entries, Runnable onChange) {
        ListView<Campaign.Entry> lv = new ListView<>();
        lv.getItems().setAll(entries);
        lv.setPrefHeight(130);
        lv.setPlaceholder(new Label("Nenhum lançamento."));
        lv.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Campaign.Entry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : (e.date.isBlank() ? "" : e.date + "  ") + (e.amount >= 0 ? "+" : "")
                        + num(e.amount) + (e.reason.isBlank() ? "" : "  —  " + e.reason));
            }
        });
        VBox box = new VBox(4, title(what), lv);
        if (gm) {
            Button add = new Button("+ lançamento");
            add.setOnAction(e -> askNumber(what, "Valor (negativo tira):").ifPresent(v -> {
                String why = askText(what, "Motivo (opcional):", "").orElse("");
                Campaign.Entry en = new Campaign.Entry(today(), v, why);
                entries.add(en);
                lv.getItems().add(en);
                onChange.run();
                changed();
            }));
            Button del = new Button("Apagar lançamento");
            del.setOnAction(e -> {
                Campaign.Entry en = lv.getSelectionModel().getSelectedItem();
                if (en == null) return;
                entries.remove(en);
                lv.getItems().remove(en);
                onChange.run();
                changed();
            });
            box.getChildren().add(new HBox(6, add, del));
        }
        return box;
    }

    // ================================================================ Calendario

    private Node calendarTab() {
        Campaign.Calendar c = data.getCalendar();
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        if (!gm && !c.visible) {
            box.getChildren().add(muted("O mestre ainda não mostrou o calendário."));
            return box;
        }
        Label date = new Label();
        date.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label weather = new Label();
        weather.setWrapText(true);
        Runnable refresh = () -> {
            date.setText(c.dateText());
            weather.setText(c.weather());
        };
        refresh.run();
        box.getChildren().addAll(date, weather);
        if (gm) {
            HBox advance = new HBox(6);
            advance.setAlignment(Pos.CENTER_LEFT);
            for (int d : new int[]{1, 7, 30}) {
                Button b = new Button("+" + d + (d == 1 ? " dia" : " dias"));
                b.setOnAction(e -> { c.day += d; refresh.run(); changed(); });
                advance.getChildren().add(b);
            }
            Button back = new Button("−1 dia");
            back.setOnAction(e -> { c.day = Math.max(0, c.day - 1); refresh.run(); changed(); });
            Button set = new Button("Ir pra data...");
            set.setOnAction(e -> askText("Ir pra data", "Dia/mês/ano (ex.: 12/3/1452):", "").ifPresent(t -> {
                String[] p = t.split("[/\\-. ]+");
                try {
                    int day = Integer.parseInt(p[0]);
                    int month = Integer.parseInt(p[1]);
                    long year = Long.parseLong(p[2]);
                    long total = (year - 1) * c.yearLength();
                    for (int i = 0; i < month - 1 && i < c.months.size(); i++) total += c.months.get(i).days;
                    c.day = Math.max(0, total + day - 1);
                    refresh.run();
                    changed();
                } catch (RuntimeException ex) {
                    // formato errado: ignora
                }
            }));
            Button reroll = new Button("Sortear outro clima");
            reroll.setTooltip(new javafx.scene.control.Tooltip("Troca a semente: o clima de todos os dias muda."));
            reroll.setOnAction(e -> { c.seed = new java.util.Random().nextLong(); refresh.run(); changed(); });
            advance.getChildren().addAll(back, set, reroll);
            box.getChildren().add(advance);
            box.getChildren().add(visibleBox(c.visible, v -> c.visible = v, () -> { }));
            GridPane g = grid();
            g.setHgap(8);
            g.setVgap(6);
            g.addRow(0, new Label("Era (opcional)"), field(c.era, v -> { c.era = v; refresh.run(); }));
            g.addRow(1, new Label("Nome da lua"), field(c.moonName, v -> { c.moonName = v; refresh.run(); }));
            TextField cycle = field(String.valueOf(c.moonCycle), v -> {
                try {
                    c.moonCycle = Math.max(2, Integer.parseInt(v.trim()));
                    refresh.run();
                } catch (NumberFormatException ignored) {
                    // digitando
                }
            });
            g.addRow(2, new Label("Ciclo da lua (dias)"), cycle);
            box.getChildren().add(g);
            box.getChildren().add(title("Meses (um por linha: nome = dias)"));
            box.getChildren().add(area(monthsText(c), "Janeiro = 30", 6, v -> { parseMonths(c, v); refresh.run(); }));
            box.getChildren().add(title("Dias da semana (um por linha)"));
            box.getChildren().add(area(String.join("\n", c.weekdays), "Domingo", 4, v -> {
                c.weekdays.clear();
                for (String l : v.split("\n")) if (!l.isBlank()) c.weekdays.add(l.trim());
                refresh.run();
            }));
            box.getChildren().add(title("Estações (nome = dia do ano em que começa, mínima, máxima, % de chuva)"));
            box.getChildren().add(area(seasonsText(c), "Verão = 1, 22, 34, 55", 4, v -> { parseSeasons(c, v); refresh.run(); }));
            box.getChildren().add(title("Notas do calendário (só você vê: festivais, prazos...)"));
            box.getChildren().add(area(c.notes, "ex.: dia 15 de Junho é o festival da colheita", 3, v -> c.notes = v));
        }
        return scroll(box);
    }

    static String monthsText(Campaign.Calendar c) {
        StringBuilder sb = new StringBuilder();
        for (Campaign.Month m : c.months) sb.append(m.name).append(" = ").append(m.days).append('\n');
        return sb.toString().trim();
    }

    static void parseMonths(Campaign.Calendar c, String text) {
        java.util.List<Campaign.Month> out = new java.util.ArrayList<>();
        for (String l : text.split("\n")) {
            if (l.isBlank()) continue;
            String[] p = l.split("=");
            String name = p[0].trim();
            int days = 30;
            if (p.length > 1) {
                try {
                    days = Math.max(1, Math.min(1000, Integer.parseInt(p[1].trim())));
                } catch (NumberFormatException ignored) {
                    // fica 30
                }
            }
            if (!name.isEmpty()) out.add(new Campaign.Month(name, days));
        }
        if (!out.isEmpty()) {
            c.months.clear();
            c.months.addAll(out);
        }
    }

    static String seasonsText(Campaign.Calendar c) {
        StringBuilder sb = new StringBuilder();
        for (Campaign.Season s : c.seasons) {
            sb.append(s.name).append(" = ").append(s.startDay).append(", ").append(s.tempMin).append(", ")
                    .append(s.tempMax).append(", ").append(s.rainChance).append('\n');
        }
        return sb.toString().trim();
    }

    static void parseSeasons(Campaign.Calendar c, String text) {
        java.util.List<Campaign.Season> out = new java.util.ArrayList<>();
        for (String l : text.split("\n")) {
            String[] p = l.split("=");
            if (p.length < 2 || p[0].isBlank()) continue;
            String[] n = p[1].split(",");
            try {
                out.add(new Campaign.Season(p[0].trim(), Integer.parseInt(n[0].trim()),
                        n.length > 1 ? Integer.parseInt(n[1].trim()) : 15, n.length > 2 ? Integer.parseInt(n[2].trim()) : 28,
                        n.length > 3 ? Integer.parseInt(n[3].trim()) : 30));
            } catch (RuntimeException ignored) {
                // linha incompleta: pula
            }
        }
        if (!out.isEmpty()) {
            c.seasons.clear();
            c.seasons.addAll(out);
        }
    }

    // ================================================================ PdMs

    private Node npcsTab() {
        return masterDetail(data.getNpcs(),
                n -> eye(n.visible) + (n.name.isBlank() ? "(sem nome)" : n.name) + "  •  " + n.attitude
                        + (n.place.isBlank() ? "" : "  •  " + n.place),
                this::npcEditor, Campaign.Npc::new, "PdM",
                gm ? "Nenhum PdM ainda (taverneiro, vilão, contato...)." : "Nenhum PdM conhecido ainda.");
    }

    private Node npcEditor(Campaign.Npc n, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nome"), field(n.name, v -> { n.name = v; refresh.run(); }));
        ComboBox<String> att = new ComboBox<>();
        att.getItems().addAll(Campaign.ATTITUDES);
        att.setValue(n.attitude);
        att.setDisable(!gm);
        att.valueProperty().addListener((o, a, b) -> { n.attitude = b; refresh.run(); changed(); });
        g.addRow(1, new Label("Atitude com o grupo"), att);
        g.addRow(2, new Label("Onde está"), field(n.place, v -> { n.place = v; refresh.run(); }));
        g.addRow(3, new Label("Papel"), field(n.role, v -> n.role = v));
        VBox box = new VBox(8, g, new Label(gm ? "O que os jogadores sabem" : "O que vocês sabem"),
                area(n.description, "aparência, o que disse, o que pediu...", 5, v -> n.description = v));
        if (gm) {
            box.getChildren().add(visibleBox(n.visible, v -> n.visible = v, refresh));
            box.getChildren().add(new Label("Segredos (só você vê)"));
            box.getChildren().add(area(n.gmNotes, "ex.: é espião do culto; sabe onde está o artefato", 4, v -> n.gmNotes = v));
        }
        return box;
    }

    // ================================================================ Aventuras

    private Node adventuresTab() {
        return masterDetail(data.getAdventures(),
                a -> eye(a.visible) + "#" + a.number + " " + (a.name.isBlank() ? "(sem nome)" : a.name) + "  •  " + a.state,
                this::adventureEditor,
                () -> {
                    Campaign.Adventure a = new Campaign.Adventure();
                    a.number = data.nextAdventureNumber();
                    a.start = today();
                    return a;
                }, "Aventura", gm ? "Nenhuma aventura ainda." : "Nenhuma aventura revelada ainda.");
    }

    private Node adventureEditor(Campaign.Adventure a, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nº"), field(String.valueOf(a.number), v -> {
            try {
                a.number = Integer.parseInt(v.trim());
                refresh.run();
            } catch (NumberFormatException ignored) {
                // digitando
            }
        }));
        g.addRow(1, new Label("Nome"), field(a.name, v -> { a.name = v; refresh.run(); }));
        ComboBox<String> state = new ComboBox<>();
        state.getItems().addAll(Campaign.ADVENTURE_STATES);
        state.setValue(a.state);
        state.setDisable(!gm);
        state.valueProperty().addListener((o, x, b) -> {
            a.state = b;
            if (("Completa".equals(b) || "Falhou".equals(b)) && a.end.isBlank()) a.end = today();
            refresh.run();
            changed();
        });
        g.addRow(2, new Label("Estado"), state);
        g.addRow(3, new Label("Começo"), field(a.start, v -> a.start = v));
        g.addRow(4, new Label("Fim"), field(a.end, v -> a.end = v));
        VBox box = new VBox(8, g, new Label("Descrição / objetivo"),
                area(a.description, "o que o grupo precisa fazer", 4, v -> a.description = v),
                new Label("Resultados"), area(a.results, "o que aconteceu no fim", 3, v -> a.results = v));
        if (gm) {
            box.getChildren().add(visibleBox(a.visible, v -> a.visible = v, refresh));
            box.getChildren().add(new Label("Notas do mestre (só você vê)"));
            box.getChildren().add(area(a.gmNotes, "ganchos, reviravoltas, recompensa", 4, v -> a.gmNotes = v));
        }
        return box;
    }

    // ================================================================ Sessoes

    private Node sessionsTab() {
        return masterDetail(data.getSessions(),
                s -> eye(s.visible) + "Sessão " + s.number + (s.date.isBlank() ? "" : "  •  " + s.date),
                this::sessionEditor,
                () -> {
                    Campaign.SessionLog s = new Campaign.SessionLog();
                    s.number = data.nextSessionNumber();
                    s.date = today();
                    return s;
                }, "Sessão", "Nenhuma sessão registrada.");
    }

    private Node sessionEditor(Campaign.SessionLog s, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nº"), field(String.valueOf(s.number), v -> {
            try {
                s.number = Integer.parseInt(v.trim());
                refresh.run();
            } catch (NumberFormatException ignored) {
                // digitando
            }
        }));
        g.addRow(1, new Label("Data"), field(s.date, v -> { s.date = v; refresh.run(); }));
        TextField link = field(s.link, v -> s.link = v);
        link.setPromptText("link da gravação, do resumo... (opcional)");
        g.addRow(2, new Label("Link"), link);
        VBox box = new VBox(8, g, new Label("Resumo"), area(s.summary, "o que aconteceu nesta sessão", 10, v -> s.summary = v));
        if (gm) box.getChildren().add(visibleBox(s.visible, v -> s.visible = v, refresh));
        return box;
    }

    // ================================================================ acoes do mestre (Main)

    /** O que precisa do resto do programa: fichas, grupo, mapa, iniciativa. */
    public interface GmActions {
        /** Abre a janela de entrega (pra quem vao itens/moedas/XP) e entrega. */
        void deliver(Campaign.Parcel parcel);

        /** Troca o nome misterioso pelo verdadeiro em quem estiver com o item. */
        void identify(Campaign.Secret secret);

        /** Tokens selecionados no mapa viram um encontro pronto (null se nada selecionado). */
        Campaign.PreparedEncounter captureSelection();

        /** Coloca o encontro no mapa (no meio da tela). */
        void deploy(Campaign.PreparedEncounter encounter, boolean hidden, boolean rollInitiative);
    }

    private GmActions actions;

    public CampaignView withGmActions(GmActions a) {
        this.actions = a;
        rebuild();
        return this;
    }

    // ================================================================ Tesouro

    private Node treasureTab() {
        Node parcels = masterDetail(data.getParcels(),
                p -> (p.delivered ? "✓ " : "") + (p.name.isBlank() ? "(sem nome)" : p.name) + "  •  " + p.items.size()
                        + " item(ns)" + (p.gp > 0 ? "  •  " + p.gp + " PO" : "") + (p.xp > 0 ? "  •  " + p.xp + " XP" : ""),
                this::parcelEditor,
                () -> {
                    Campaign.Parcel p = new Campaign.Parcel();
                    p.name = "Tesouro " + (data.getParcels().size() + 1);
                    return p;
                }, "Parcela", "Nenhuma parcela. Prepare o tesouro antes da sessão e entregue num clique.");
        ListView<Campaign.Secret> secrets = new ListView<>();
        secrets.getItems().setAll(data.getSecrets().stream().filter(x -> !x.identified).toList());
        secrets.setPrefHeight(130);
        secrets.setPlaceholder(new Label("Nenhum item não identificado com os jogadores."));
        secrets.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Campaign.Secret x, boolean empty) {
                super.updateItem(x, empty);
                setText(empty || x == null ? null : "\"" + x.publicName + "\"  →  " + x.trueName
                        + (x.holder.isBlank() ? "" : "   (com " + x.holder + ")"));
            }
        });
        Button identify = new Button("Identificar");
        identify.setTooltip(new javafx.scene.control.Tooltip("Troca o nome misterioso pelo verdadeiro na ficha de quem estiver com o item."));
        identify.setOnAction(e -> {
            Campaign.Secret x = secrets.getSelectionModel().getSelectedItem();
            if (x == null || actions == null) return;
            actions.identify(x);
            secrets.getItems().remove(x);
        });
        identify.setDisable(actions == null);
        VBox bottom = new VBox(6, title("Itens não identificados (os jogadores veem só o nome da esquerda)"), secrets,
                new HBox(6, identify));
        bottom.setPadding(new Insets(0, 10, 10, 10));
        VBox all = new VBox(parcels, bottom);
        VBox.setVgrow(parcels, Priority.ALWAYS);
        return all;
    }

    private TextField intField(int value, java.util.function.IntConsumer set) {
        TextField f = field(String.valueOf(value), v -> {
            try {
                set.accept(Math.max(0, Integer.parseInt(v.trim().isEmpty() ? "0" : v.trim())));
            } catch (NumberFormatException ignored) {
                // digitando
            }
        });
        f.setPrefColumnCount(5);
        f.setMaxWidth(90);
        return f;
    }

    private Node parcelEditor(Campaign.Parcel p, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nome"), field(p.name, v -> { p.name = v; refresh.run(); }));
        HBox coins = new HBox(6, new Label("PL"), intField(p.pp, v -> p.pp = v), new Label("PO"),
                intField(p.gp, v -> { p.gp = v; refresh.run(); }), new Label("PE"), intField(p.ep, v -> p.ep = v),
                new Label("PP"), intField(p.sp, v -> p.sp = v), new Label("PC"), intField(p.cp, v -> p.cp = v));
        coins.setAlignment(Pos.CENTER_LEFT);
        coins.getChildren().forEach(c -> {
            if (c instanceof Label l) l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        });
        g.addRow(1, new Label("Moedas"), coins);
        g.addRow(2, new Label("XP (total)"), intField(p.xp, v -> { p.xp = v; refresh.run(); }));
        ListView<Campaign.ParcelItem> items = new ListView<>();
        items.getItems().setAll(p.items);
        items.setPrefHeight(150);
        items.setPlaceholder(new Label("Nenhum item."));
        items.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Campaign.ParcelItem i, boolean empty) {
                super.updateItem(i, empty);
                setText(empty || i == null ? null : i.quantity + "x " + i.name
                        + (i.unidentified ? "   (não identificado: \"" + i.publicName + "\")" : "")
                        + (i.valueCopper > 0 ? "   " + num(i.valueCopper / 100.0) + " PO" : ""));
            }
        });
        Button add = new Button("Item", Icons.icon("add", 14));
        add.setOnAction(e -> editItem(new Campaign.ParcelItem()).ifPresent(i -> {
            p.items.add(i);
            items.getItems().add(i);
            refresh.run();
            changed();
        }));
        Button edit = new Button("Editar item");
        edit.setOnAction(e -> {
            Campaign.ParcelItem i = items.getSelectionModel().getSelectedItem();
            if (i == null) return;
            editItem(i).ifPresent(x -> { items.refresh(); changed(); });
        });
        Button del = new Button("Apagar item");
        del.setOnAction(e -> {
            Campaign.ParcelItem i = items.getSelectionModel().getSelectedItem();
            if (i == null) return;
            p.items.remove(i);
            items.getItems().remove(i);
            refresh.run();
            changed();
        });
        Label status = new Label(p.delivered ? "Entregue a " + p.deliveredTo + "." : "Ainda não entregue.");
        status.getStyleClass().add("label-muted");
        Button deliver = new Button(p.delivered ? "Entregar de novo..." : "Entregar...", Icons.icon("send", 14));
        deliver.getStyleClass().add("button-primary");
        deliver.setDisable(actions == null);
        deliver.setOnAction(e -> {
            if (actions != null) actions.deliver(p);
        });
        return new VBox(8, g, title("Itens"), items, new HBox(6, add, edit, del),
                new Label("Notas (só você vê)"), area(p.notes, "de onde vem, armadilha no baú...", 3, v -> p.notes = v),
                new HBox(10, deliver, status));
    }

    /** Janela de um item da parcela; devolve o item preenchido se confirmou. */
    private Optional<Campaign.ParcelItem> editItem(Campaign.ParcelItem i) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Item do tesouro");
        d.setHeaderText(null);
        javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, javafx.scene.control.ButtonType.CANCEL);
        TextField name = new TextField(i.name);
        name.setPromptText("nome verdadeiro, ex.: Espada Longa +1 da Chama");
        TextField qty = new TextField(String.valueOf(i.quantity));
        TextField weight = new TextField(i.weightLb == 0 ? "" : num(i.weightLb));
        weight.setPromptText("libras");
        TextField value = new TextField(i.valueCopper == 0 ? "" : num(i.valueCopper / 100.0));
        value.setPromptText("em PO");
        CheckBox unid = new CheckBox("Não identificado (os jogadores veem outro nome até você identificar)");
        unid.setSelected(i.unidentified);
        TextField pub = new TextField(i.publicName);
        pub.setPromptText("o que os jogadores veem, ex.: espada de lâmina avermelhada");
        pub.disableProperty().bind(unid.selectedProperty().not());
        TextArea notes = new TextArea(i.notes);
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        notes.setPromptText("o que o item faz (só você vê)");
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nome"), name);
        g.addRow(1, new Label("Quantidade"), qty);
        g.addRow(2, new Label("Peso"), weight);
        g.addRow(3, new Label("Valor"), value);
        g.add(unid, 1, 4);
        g.addRow(5, new Label("Nome pros jogadores"), pub);
        g.addRow(6, new Label("Notas"), notes);
        GridPane.setHgrow(name, Priority.ALWAYS);
        g.setPrefWidth(520);
        d.getDialogPane().setContent(g);
        d.getDialogPane().lookupButton(ok).disableProperty().bind(name.textProperty().isEmpty());
        javafx.application.Platform.runLater(name::requestFocus);
        if (d.showAndWait().filter(b -> b == ok).isEmpty()) return Optional.empty();
        i.name = name.getText().trim();
        i.quantity = parseInt(qty.getText(), 1);
        i.weightLb = parseDouble(weight.getText());
        i.valueCopper = Math.round(parseDouble(value.getText()) * 100);
        i.unidentified = unid.isSelected();
        i.publicName = pub.getText().trim();
        if (i.unidentified && i.publicName.isEmpty()) i.publicName = "Item desconhecido";
        i.notes = notes.getText();
        return Optional.of(i);
    }

    static int parseInt(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s.trim()));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    static double parseDouble(String s) {
        try {
            return Math.max(0, Double.parseDouble(s.trim().replace(',', '.')));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ================================================================ Encontros

    private Node encountersTab() {
        Node md = masterDetail(data.getEncounters(),
                e -> (e.name.isBlank() ? "(sem nome)" : e.name) + "  •  " + e.tokens.size() + " token(s)",
                this::encounterEditor,
                () -> {
                    Campaign.PreparedEncounter e = new Campaign.PreparedEncounter();
                    e.name = "Encontro " + (data.getEncounters().size() + 1);
                    return e;
                }, "Vazio", "Nenhum encontro pronto. Coloque os monstros no mapa, selecione (Shift+clique ou "
                        + "arraste uma caixa) e use \"Salvar seleção do mapa\".");
        Button capture = new Button("Salvar seleção do mapa como encontro", Icons.icon("add", 14));
        capture.getStyleClass().add("button-primary");
        capture.setTooltip(new javafx.scene.control.Tooltip("Os tokens selecionados no mapa (com posição, PV, "
                + "condições...) viram um encontro pronto. Depois é só tirar do mapa e colocar de volta quando precisar."));
        capture.setDisable(actions == null);
        capture.setOnAction(e -> {
            if (actions == null) return;
            Campaign.PreparedEncounter enc = actions.captureSelection();
            if (enc == null) return;
            askText("Encontro pronto", "Nome do encontro:", enc.name).ifPresent(n -> {
                if (!n.isBlank()) enc.name = n;
                data.getEncounters().add(0, enc);
                changed();
                reload(data);
            });
        });
        VBox top = new VBox(6, new HBox(6, capture));
        top.setPadding(new Insets(10, 10, 0, 10));
        VBox all = new VBox(top, md);
        VBox.setVgrow(md, Priority.ALWAYS);
        return all;
    }

    private Node encounterEditor(Campaign.PreparedEncounter e, Runnable refresh) {
        GridPane g = grid();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nome"), field(e.name, v -> { e.name = v; refresh.run(); }));
        ListView<String> tokens = new ListView<>();
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (var t : e.tokens) counts.merge(t.getName() == null ? "?" : t.getName().replaceAll("\\s+\\d+$", ""), 1L, Long::sum);
        counts.forEach((n, c) -> tokens.getItems().add(c + "x " + n));
        tokens.setPrefHeight(150);
        tokens.setPlaceholder(new Label("Sem tokens (use \"Salvar seleção do mapa\")."));
        CheckBox hidden = new CheckBox("Colocar escondidos dos jogadores");
        hidden.setSelected(true);
        CheckBox init = new CheckBox("Rolar iniciativa e pôr no rastreador");
        init.setSelected(true);
        Button deploy = new Button("Colocar no mapa", Icons.icon("map", 14));
        deploy.getStyleClass().add("button-primary");
        deploy.setDisable(actions == null || e.tokens.isEmpty());
        deploy.setOnAction(x -> {
            if (actions != null) actions.deploy(e, hidden.isSelected(), init.isSelected());
        });
        return new VBox(8, g, title("Tokens"), tokens, hidden, init, deploy,
                new Label("Notas (só você vê)"), area(e.notes, "tática, onde ficam, o que carregam...", 4, v -> e.notes = v));
    }

    // ================================================================ Anotacoes

    private Node notesTab() {
        TextArea a = area(data.getGmNotes(), "Ideias, segredos, ganchos, nomes prontos... Só você vê.", 30, data::setGmNotes);
        VBox box = new VBox(8, muted("Só você vê. Nunca vai pros jogadores."), a);
        VBox.setVgrow(a, Priority.ALWAYS);
        box.setPadding(new Insets(12));
        return box;
    }

    /** Pros testes: o TabPane. */
    TabPane tabs() {
        return tabs;
    }

    @SuppressWarnings("unused")
    private static <T> ChangeListener<T> noop() {
        return (o, a, b) -> { };
    }
}
