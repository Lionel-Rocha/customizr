package com.vttcaseiro.view;

import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.service.ImageAssetService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ficha feita com um modelo de PDF editavel: a propria pagina do PDF (imagem) com os campos editaveis por cima, no
 * mesmo lugar. Botao direito num campo com numero: rolar d20 + o valor. Serve pra personagem e pra monstro/PdM.
 */
public final class TemplateSheetView {

    /** O que a janela pede pro resto do programa. */
    public interface Actions {
        /** Um valor mudou (o Main grava e manda pra mesa, com uma pequena espera). */
        void changed();

        /** Rolar d20 + modificador com um rotulo. */
        void roll(String label, int modifier);

        /** Exportar o PDF preenchido (null = esta maquina nao tem o PDF original). */
        default Runnable exportPdf() {
            return null;
        }
    }

    private final Stage stage = new Stage();
    private SheetTemplate template;
    private Map<String, String> values;
    private final ImageAssetService assets;
    private final boolean editable;
    private final Actions actions;
    private final VBox pagesBox = new VBox(14);
    private double zoom = 1.25;
    /** Controles por nome de campo (um campo pode aparecer em mais de um lugar). */
    private final Map<String, List<Node>> controls = new java.util.HashMap<>();
    private boolean filling;
    private Runnable onClosed = () -> { };

    public TemplateSheetView(Window owner, String title, SheetTemplate template, Map<String, String> values,
                             ImageAssetService assets, boolean editable, Actions actions) {
        this.template = template;
        this.values = values;
        this.assets = assets;
        this.editable = editable;
        this.actions = actions;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle(title);
        pagesBox.setPadding(new Insets(14));
        pagesBox.setAlignment(Pos.TOP_CENTER);
        ScrollPane sp = new ScrollPane(pagesBox);
        sp.setFitToWidth(true);
        sp.setPannable(true);
        Slider zoomSlider = new Slider(0.6, 2.4, zoom);
        zoomSlider.setPrefWidth(160);
        zoomSlider.valueProperty().addListener((o, a, b) -> {
            zoom = b.doubleValue();
            build();
        });
        Label hint = new Label(editable ? "Clique num campo pra escrever. Botão direito num campo com número: rolar d20 + valor."
                : "Só leitura. Botão direito num campo com número: rolar d20 + valor.");
        hint.getStyleClass().add("label-muted");
        Label zoomLabel = new Label("Zoom");
        zoomLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox bar = new HBox(10, zoomLabel, zoomSlider);
        bar.setAlignment(Pos.CENTER_LEFT);
        Runnable export = actions == null ? null : actions.exportPdf();
        if (export != null) {
            Button pdf = new Button("Exportar PDF preenchido...", Icons.icon("download", 14));
            pdf.setOnAction(e -> export.run());
            bar.getChildren().add(pdf);
        }
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        bar.getChildren().addAll(grow, hint);
        bar.setPadding(new Insets(8, 12, 8, 12));
        BorderPane root = new BorderPane(sp);
        root.setTop(bar);
        build();
        double firstW = template.getPages().isEmpty() ? 600 : template.getPages().get(0).width;
        stage.setScene(new Scene(root, Math.min(1200, firstW * zoom + 60), 860));
        stage.setOnHidden(e -> onClosed.run());
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public Stage getStage() {
        return stage;
    }

    public void setOnClosed(Runnable r) {
        this.onClosed = r == null ? () -> { } : r;
    }

    /** Chegou versao nova (rede, dano no token): atualiza os valores sem perder o lugar. */
    public void reload(SheetTemplate t, Map<String, String> fresh) {
        this.template = t;
        if (fresh != values) {
            // copia pro mapa que a janela ja edita (quem grava continua apontando pra ele)
            values.clear();
            values.putAll(fresh);
        }
        filling = true;
        try {
            for (Map.Entry<String, List<Node>> e : controls.entrySet()) {
                String v = values.getOrDefault(e.getKey(), "");
                for (Node n : e.getValue()) setControl(n, v);
            }
        } finally {
            filling = false;
        }
    }

    private void build() {
        controls.clear();
        pagesBox.getChildren().clear();
        for (int i = 0; i < template.getPages().size(); i++) {
            SheetTemplate.Page page = template.getPages().get(i);
            double w = page.width * zoom;
            double h = page.height * zoom;
            ImageView bg = new ImageView();
            bg.setFitWidth(w);
            bg.setFitHeight(h);
            bg.setPreserveRatio(false);
            bg.setSmooth(true);
            Image img = pageImage(page.assetId);
            if (img != null) bg.setImage(img);
            Pane overlay = new Pane();
            overlay.setPrefSize(w, h);
            overlay.setMinSize(w, h);
            overlay.setMaxSize(w, h);
            for (SheetTemplate.Field f : template.getFields()) {
                if (f.page != i) continue;
                Node c = control(f);
                c.relocate(f.x * zoom, f.y * zoom);
                overlay.getChildren().add(c);
                controls.computeIfAbsent(f.name, k -> new ArrayList<>()).add(c);
            }
            StackPane pageNode = new StackPane(bg, overlay);
            pageNode.setMaxSize(w, h);
            pageNode.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, #00000088, 12, 0.2, 0, 3);");
            if (img == null) {
                Label wait = new Label("(imagem da página ainda chegando pela rede)");
                wait.setStyle("-fx-text-fill: #555;");
                pageNode.getChildren().add(1, wait);
            }
            pagesBox.getChildren().add(pageNode);
        }
    }

    private Image pageImage(String assetId) {
        if (assetId == null || assets == null || !assets.exists(assetId)) return null;
        try {
            SafeImage.Loaded l = SafeImage.read(assets.readBytes(assetId), 4096, 4096L * 6000L);
            return l == null ? null : l.image();
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private Node control(SheetTemplate.Field f) {
        double w = Math.max(10, f.w * zoom);
        double h = Math.max(10, f.h * zoom);
        String value = values.getOrDefault(f.name, "");
        Node node;
        if ("caixa".equals(f.type)) {
            CheckBox cb = new CheckBox();
            cb.setSelected(com.vttcaseiro.importer.PdfTemplateReader.isYes(value));
            cb.setDisable(!editable);
            cb.setStyle("-fx-opacity: 1;");
            cb.selectedProperty().addListener((o, a, b) -> put(f.name, b ? "sim" : ""));
            StackPane box = new StackPane(cb);
            box.setPrefSize(w, h);
            node = box;
        } else if ("lista".equals(f.type) && !f.options.isEmpty()) {
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll(f.options);
            combo.setValue(value.isBlank() ? null : value);
            combo.setDisable(!editable);
            combo.setPrefSize(w, h);
            combo.setStyle(fieldStyle(h, f) + "-fx-opacity: 1;");
            combo.valueProperty().addListener((o, a, b) -> put(f.name, b == null ? "" : b));
            node = combo;
        } else {
            TextInputControl t = f.multiline ? new TextArea(value) : new TextField(value);
            if (t instanceof TextArea ta) ta.setWrapText(true);
            t.setEditable(editable);
            t.setPrefSize(w, h);
            t.setMinSize(w, h);
            t.setMaxSize(w, h);
            t.setStyle(fieldStyle(h, f));
            t.textProperty().addListener((o, a, b) -> put(f.name, b));
            t.setContextMenu(rollMenu(f, t));
            node = t;
        }
        node.getStyleClass().add("template-field"); // style.css: claro como o papel, com qualquer tema
        Tooltip.install(node, new Tooltip(f.label));
        return node;
    }

    /** Campo transparente sobre o desenho do PDF, letra escura (como no papel). */
    private String fieldStyle(double h, SheetTemplate.Field f) {
        double size = f.fontSize > 0 ? f.fontSize * zoom : Math.max(8, Math.min(16, h * 0.62));
        if (f.multiline) size = Math.min(size, 13 * zoom / 1.25);
        return "-fx-background-color: rgba(120,160,255,0.10); -fx-background-radius: 0; -fx-border-color: transparent;"
                + " -fx-text-fill: #111111; -fx-control-inner-background: transparent; -fx-highlight-fill: #8ab4ff;"
                + " -fx-padding: 0 2 0 2; -fx-font-size: " + String.format(java.util.Locale.ROOT, "%.1f", size) + "px;";
    }

    private ContextMenu rollMenu(SheetTemplate.Field f, TextInputControl t) {
        ContextMenu menu = new ContextMenu();
        MenuItem roll = new MenuItem("Rolar d20 + este valor");
        roll.setOnAction(e -> {
            Integer mod = SheetTemplate.intOf(t.getText());
            if (mod != null && actions != null) actions.roll(f.label, mod);
        });
        menu.setOnShowing(e -> roll.setDisable(SheetTemplate.intOf(t.getText()) == null));
        menu.getItems().add(roll);
        return menu;
    }

    private void put(String field, String value) {
        if (filling) return;
        values.put(field, value);
        // o mesmo campo em outro lugar da ficha acompanha
        List<Node> others = controls.get(field);
        if (others != null && others.size() > 1) {
            filling = true;
            try {
                for (Node n : others) setControl(n, value);
            } finally {
                filling = false;
            }
        }
        if (actions != null) actions.changed();
    }

    private static void setControl(Node n, String v) {
        if (n instanceof TextInputControl t) {
            if (!t.getText().equals(v)) t.setText(v);
        } else if (n instanceof ComboBox<?> c) {
            @SuppressWarnings("unchecked") ComboBox<String> cs = (ComboBox<String>) c;
            cs.setValue(v == null || v.isBlank() ? null : v);
        } else if (n instanceof StackPane sp && !sp.getChildren().isEmpty() && sp.getChildren().get(0) instanceof CheckBox cb) {
            cb.setSelected(com.vttcaseiro.importer.PdfTemplateReader.isYes(v));
        }
    }

    /** Pros testes: quantos controles foram montados. */
    int controlCount() {
        return controls.values().stream().mapToInt(List::size).sum();
    }

    /** Pros testes: escrever num campo como se fosse o usuario. */
    void typeInto(String field, String text) {
        List<Node> list = controls.get(field);
        if (list == null || list.isEmpty()) return;
        if (list.get(0) instanceof TextInputControl t) t.setText(text);
    }

    @SuppressWarnings("unused")
    private static Node spacer() {
        return new Region();
    }
}
