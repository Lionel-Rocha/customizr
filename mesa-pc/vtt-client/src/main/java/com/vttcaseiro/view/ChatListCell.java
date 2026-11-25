package com.vttcaseiro.view;

import com.vttcaseiro.app.LogEntry;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Uma linha do chat. Rolagens mostram o total em destaque, marcam critico
 * (20 natural) e falha critica (1 natural), e tem menu de botao direito
 * pra aplicar o total como dano/cura no token selecionado no mapa -- o
 * atalho "rolou o dano, aplica no goblin" que mestre usa toda hora.
 */
public class ChatListCell extends ListCell<LogEntry> {

    /** Enquete pra desenhar: pergunta, opcoes, votos de cada uma e o meu voto (null = nao votei). */
    public record PollView(String question, java.util.List<String> options, java.util.List<Integer> counts, Integer myVote) {
    }

    /** O que o chat pede pro resto do programa. */
    public interface ChatActions {
        /** multiplier: 1 = normal, 0.5 = resistencia, 2 = vulnerabilidade. */
        void applyToSelectedToken(int total, double multiplier, boolean heal);

        /** Mesmo, nos tokens mirados (o texto da rolagem da o tipo de dano). */
        default void applyToTargets(int total, double multiplier, boolean heal, String rollText) {
        }

        /** Texto do botao "Rolar dano" se esta rolagem for um ataque meu com dano conhecido (null = sem botao). */
        default String followUpButton(LogEntry entry) {
            return null;
        }

        /** Rola o dano do ataque desta rolagem (dobrado se foi critico). */
        default void followUp(LogEntry entry) {
        }

        /** Imagem de um asset (null = ainda nao chegou pela rede). */
        default javafx.scene.image.Image chatImage(String assetId) {
            return null;
        }

        /** Abre a imagem grande. */
        default void openImage(String assetId) {
        }

        /** Salva a imagem num arquivo. */
        default void saveImage(String assetId) {
        }

        /** Enquete desta linha (null = nao e enquete). */
        default PollView pollFor(LogEntry entry) {
            return null;
        }

        /** Votar na opcao {@code option} da enquete desta linha. */
        default void vote(LogEntry entry, int option) {
        }

        /** Esta linha (dano/cura aplicado) pode ser desfeita? Devolve o que fazer, ou null. */
        default Runnable undoFor(LogEntry entry) {
            return null;
        }
    }

    private final ChatActions actions;

    public ChatListCell() {
        this(null);
    }

    public ChatListCell(ChatActions actions) {
        this.actions = actions;
        // Sem isto a celula cresce ate o tamanho do texto numa linha so: o
        // texto nao quebra e aparece rolagem lateral cortando a mensagem.
        setPrefWidth(0);
    }

    private static String colorFor(LogEntry.Type type) {
        return switch (type) {
            case ROLL -> "#E8A23E";
            case CHAT -> "#5B8DEF";
            case DAMAGE -> "#D9544C";
            case HEAL -> "#3FBE6E";
            case ERROR -> "#D9544C";
            case SYSTEM -> "#7D6A58";
        };
    }

    private static String prefixFor(LogEntry.Type type) {
        return switch (type) {
            case ROLL -> "Rolagem";
            case CHAT -> "Chat";
            case DAMAGE -> "Dano";
            case HEAL -> "Cura";
            case ERROR -> "Erro";
            case SYSTEM -> "Sistema";
        };
    }

    @Override
    protected void updateItem(LogEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        setPadding(Insets.EMPTY);
        setGraphic(null);
        setText(null);
        setContextMenu(null);
        if (empty || entry == null) {
            return;
        }

        String barColor = entry.isCriticalHit() ? "#3FBE6E" : entry.isCriticalFail() ? "#D9544C" : colorFor(entry.type());
        Region bar = new Region();
        bar.setPrefWidth(3);
        bar.setMaxWidth(3);
        bar.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2;");
        HBox.setHgrow(bar, Priority.NEVER);

        if (entry.isPrivate()) barColor = "#9B6BDF";
        bar.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2;");
        String who = entry.author() != null ? entry.author() : prefixFor(entry.type());
        Label meta = new Label(entry.isPrivate() ? who + "  •  " + entry.privacy() : who);
        meta.getStyleClass().add("chat-entry-meta");
        if (entry.isPrivate()) meta.setStyle("-fx-text-fill: #B79BEA; -fx-font-style: italic;");

        String emote = entry.type() == LogEntry.Type.CHAT ? com.vttcaseiro.dice.InlineRolls.emote(entry.text()) : null;
        javafx.scene.Node text = richText(emote != null ? who + " " + emote : entry.text(), emote != null);
        if (text instanceof javafx.scene.text.TextFlow flow) {
            // TextFlow nao quebra linha sozinho dentro da lista: largura = a da celula
            flow.prefWidthProperty().bind(widthProperty().subtract(34));
        }

        VBox textBox = new VBox(1, meta, text);
        if (entry.hasImage()) {
            if (entry.text() == null || entry.text().isBlank()) {
                text.setVisible(false);
                text.setManaged(false);
            }
            textBox.getChildren().add(imageNode(entry.imageAssetId()));
        }
        if (emote != null) {
            // "/me saca a espada" -> "Ana saca a espada", sem cabecalho
            meta.setVisible(false);
            meta.setManaged(false);
        }
        if (entry.total() != null) {
            HBox totalRow = new HBox(8);
            Label total = new Label(String.valueOf(entry.total()));
            total.getStyleClass().add("roll-total");
            total.setStyle("-fx-text-fill: " + barColor + "; -fx-border-color: " + barColor + "88;");
            totalRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            totalRow.getChildren().add(total);
            if (entry.isCriticalHit()) totalRow.getChildren().add(badge("CRÍTICO!", "#3FBE6E"));
            if (entry.isCriticalFail()) totalRow.getChildren().add(badge("FALHA CRÍTICA", "#D9544C"));
            textBox.getChildren().add(totalRow);
            if (actions != null) {
                setContextMenu(buildMenu(entry.total(), entry.text()));
                String follow = actions.followUpButton(entry);
                if (follow != null) {
                    javafx.scene.control.Button dmg = chip(follow, "Rola o dano deste ataque" + (entry.isCriticalHit()
                            ? " com os dados dobrados (crítico)" : ""), () -> actions.followUp(entry));
                    dmg.getStyleClass().add("roll-chip-primary");
                    textBox.getChildren().add(new HBox(4, dmg));
                }
                if (entry.naturalD20() == null && entry.type() == LogEntry.Type.ROLL) {
                    // rolagem que nao e d20 (dano, cura): botoes rapidos a vista
                    int t = entry.total();
                    HBox quick = new HBox(4,
                            chip("Dano nos alvos", "Aplica " + t + " de dano nos tokens mirados",
                                    () -> actions.applyToTargets(t, 1, false, entry.text())),
                            chip("Metade", "Aplica " + (t / 2) + " (resistência / passou na salvaguarda)",
                                    () -> actions.applyToTargets(t, 0.5, false, entry.text())),
                            chip("Curar", "Cura " + t + " nos tokens mirados",
                                    () -> actions.applyToTargets(t, 1, true, entry.text())));
                    textBox.getChildren().add(quick);
                }
            }
        }
        PollView poll = actions == null ? null : actions.pollFor(entry);
        if (poll != null) {
            textBox.getChildren().removeIf(n -> n != meta);
            Label q = new Label(poll.question().isBlank() ? "Enquete" : poll.question());
            q.setWrapText(true);
            q.setStyle("-fx-font-weight: bold;");
            VBox opts = new VBox(4);
            int total = poll.counts().stream().mapToInt(Integer::intValue).sum();
            for (int i = 0; i < poll.options().size(); i++) {
                int idx = i;
                int n = i < poll.counts().size() ? poll.counts().get(i) : 0;
                boolean mine = poll.myVote() != null && poll.myVote() == i;
                javafx.scene.control.Button b = new javafx.scene.control.Button((mine ? "✓ " : "") + poll.options().get(i)
                        + "   " + n + (n == 1 ? " voto" : " votos"));
                b.setMaxWidth(Double.MAX_VALUE);
                b.getStyleClass().add("roll-chip");
                if (mine) b.getStyleClass().add("roll-chip-primary");
                double pct = total == 0 ? 0 : (double) n / total;
                b.setStyle("-fx-background-color: linear-gradient(to right, -c-accent-deep " + (pct * 100) + "%, -c-raised "
                        + (pct * 100) + "%);");
                b.setOnAction(e -> actions.vote(entry, idx));
                opts.getChildren().add(b);
            }
            Label hint = new Label(total + (total == 1 ? " voto" : " votos") + " · clique pra votar (dá pra trocar)");
            hint.getStyleClass().add("label-muted");
            textBox.getChildren().addAll(q, opts, hint);
        }
        Runnable undo = actions == null ? null : actions.undoFor(entry);
        if (undo != null) {
            javafx.scene.control.Button back = chip("Desfazer", "Volta os PV de antes nesses tokens", undo);
            textBox.getChildren().add(new HBox(4, back));
        }
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(8, bar, textBox);
        row.getStyleClass().add("chat-entry");
        if (entry.total() != null) row.getStyleClass().add("chat-roll-card");
        if (emote != null) row.getStyleClass().add("chat-entry-emote");
        if (entry.isPrivate()) row.getStyleClass().add("chat-entry-private");
        if (entry.type() == LogEntry.Type.SYSTEM && entry.total() == null) {
            // Avisos do sistema ("Turno de...", "Ficha salva") ficam discretos
            // pra nao competir com as rolagens.
            row.getStyleClass().add("chat-entry-system");
            meta.setVisible(false);
            meta.setManaged(false);
        }

        setGraphic(row);
    }

    /** Largura maxima da miniatura no chat. */
    static final double THUMB_WIDTH = 240;
    static final double THUMB_HEIGHT = 200;

    private javafx.scene.Node imageNode(String assetId) {
        javafx.scene.image.Image img = actions == null ? null : actions.chatImage(assetId);
        if (img == null || img.isError()) {
            Label wait = new Label(img != null && img.isError() ? "(imagem não pôde ser aberta)" : "(recebendo imagem...)");
            wait.getStyleClass().add("label-muted");
            return wait;
        }
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        double w = img.getWidth() > 0 ? img.getWidth() : THUMB_WIDTH;
        double h = img.getHeight() > 0 ? img.getHeight() : THUMB_HEIGHT;
        double scale = Math.min(1, Math.min(THUMB_WIDTH / w, THUMB_HEIGHT / h));
        view.setFitWidth(w * scale);
        view.setFitHeight(h * scale);
        view.setCursor(javafx.scene.Cursor.HAND);
        view.getStyleClass().add("chat-image");
        javafx.scene.control.Tooltip.install(view, new javafx.scene.control.Tooltip("Clique para ver grande; botão direito para salvar"));
        view.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) actions.openImage(assetId);
        });
        ContextMenu menu = new ContextMenu(item("Ver grande", () -> actions.openImage(assetId)),
                item("Salvar imagem...", () -> actions.saveImage(assetId)));
        view.setOnContextMenuRequested(e -> {
            menu.show(view, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return view;
    }

    /** Texto do chat com as rolagens [[...]] em destaque (total grande, conta no tooltip). */
    static javafx.scene.Node richText(String raw, boolean emote) {
        java.util.List<com.vttcaseiro.dice.InlineRolls.Part> parts = com.vttcaseiro.dice.InlineRolls.split(raw);
        if (!emote && parts.size() <= 1 && (parts.isEmpty() || !parts.get(0).isRoll())) {
            Label text = new Label(raw);
            text.getStyleClass().add("chat-entry-text");
            text.setWrapText(true);
            text.setMaxWidth(Double.MAX_VALUE);
            return text;
        }
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        flow.setMinWidth(0);
        for (com.vttcaseiro.dice.InlineRolls.Part p : parts) {
            if (p.isRoll()) {
                Label chipLabel = new Label(String.valueOf(p.total()));
                chipLabel.getStyleClass().add("inline-roll");
                javafx.scene.control.Tooltip.install(chipLabel, new javafx.scene.control.Tooltip(p.detail()));
                flow.getChildren().add(chipLabel);
            } else {
                javafx.scene.text.Text t = new javafx.scene.text.Text(p.text());
                t.getStyleClass().add(emote ? "chat-emote-text" : "chat-flow-text");
                flow.getChildren().add(t);
            }
        }
        return flow;
    }

    private static javafx.scene.control.Button chip(String text, String tip, Runnable action) {
        javafx.scene.control.Button b = new javafx.scene.control.Button(text);
        b.getStyleClass().add("roll-chip");
        b.setTooltip(new javafx.scene.control.Tooltip(tip));
        b.setOnAction(e -> action.run());
        b.setFocusTraversable(false);
        return b;
    }

    private static Label badge(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-font-size: 11px; -fx-padding: 1 6 1 6; -fx-background-radius: 8;");
        return l;
    }

    private ContextMenu buildMenu(int total, String text) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                item("Aplicar " + total + " de dano nos ALVOS marcados", () -> actions.applyToTargets(total, 1, false, text)),
                item("Aplicar metade (" + (total / 2) + ") nos alvos", () -> actions.applyToTargets(total, 0.5, false, text)),
                item("Curar " + total + " nos alvos", () -> actions.applyToTargets(total, 1, true, text)),
                new SeparatorMenuItem(),
                item("Aplicar " + total + " de dano no token selecionado", () -> actions.applyToSelectedToken(total, 1, false)),
                item("Aplicar metade (" + (total / 2) + ") -- resistência / passou na salvaguarda",
                        () -> actions.applyToSelectedToken(total, 0.5, false)),
                item("Aplicar o dobro (" + (total * 2) + ") -- vulnerabilidade",
                        () -> actions.applyToSelectedToken(total, 2, false)),
                new SeparatorMenuItem(),
                item("Curar " + total + " no token selecionado", () -> actions.applyToSelectedToken(total, 1, true)));
        return menu;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }
}
