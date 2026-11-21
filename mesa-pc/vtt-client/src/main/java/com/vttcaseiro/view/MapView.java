package com.vttcaseiro.view;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.ImageCodec;
import com.vttcaseiro.service.ImageAssetService;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.util.Optional;

/**
 * Mostra um SceneModel inteiro: imagem de fundo, grade por cima e os
 * tokens, com zoom (roda do mouse) e navegacao (arrastar com o botao
 * direito). Cada chamada a rebuildTokens() redesenha a lista de tokens do
 * zero a partir do modelo -- simples de entender, trocar por atualizacao
 * incremental depois se a performance pedir (nao deve pedir, pra poucos
 * tokens de uma mesa de RPG).
 */
public class MapView extends Pane {
    private static final double MIN_ZOOM = 0.04; // mapas-mundo grandes (Azgaar) cabem inteiros na tela
    private static final double MAX_ZOOM = 4.0;

    private final SceneModel scene;
    private final ImageAssetService imageAssetService;
    private final Group content = new Group();
    private final ImageView backgroundView = new ImageView();
    private final Canvas gridCanvas = new Canvas();
    private final Pane tokenLayer = new Pane();
    /** Neblina de guerra: preta pro jogador, meio transparente pro mestre. */
    private final Canvas fogCanvas = new Canvas();
    /** null = ferramenta desligada; true = revelar; false = cobrir. */
    private Boolean fogTool;
    private double fogStartX;
    private double fogStartY;
    private final Rectangle fogPreview = new Rectangle();

    /** Quem recebe o retangulo de casas da ferramenta de neblina. */
    public interface FogEditHandler {
        void edit(int c0, int r0, int c1, int r1, boolean reveal);
    }

    private FogEditHandler fogEditHandler;

    private double zoom = 1.0;
    private double panStartSceneX;
    private double panStartSceneY;
    private double panStartTranslateX;
    private double panStartTranslateY;
    private java.util.function.Consumer<TokenModel> tokenMoveListener;
    private CharacterDropHandler characterDropHandler;

    /** Regua de medicao (Fase 3): clique-e-arraste no mapa vazio (com
     * {@link #setMeasuring} ativado) desenha uma linha e mostra a
     * distancia em pes, usando GridSettings.feetPerCell -- so uma
     * ferramenta visual local desta janela, nao sincroniza pela rede
     * (ver HANDOFF.md). */
    private boolean measuring;
    private double measureStartX;
    private double measureStartY;
    private final Line measureLine = new Line();
    private final Label measureLabel = new Label();

    /** Templates de area de efeito (Fase 3): clique-e-arraste no mapa
     * vazio (com {@link #setTemplating} ativado) desenha um circulo, cone,
     * linha ou cubo -- a mesma ideia visual do Roll20/Foundry pra
     * posicionar a area de uma magia antes de conjurar. Fica desenhado no
     * mapa depois de soltar o mouse (de proposito, pra servir de
     * referencia durante o resto da acao) ate o proximo clique-e-arraste
     * ou {@link #clearTemplate()}. So local desta janela -- nao
     * sincroniza pela rede (ver HANDOFF.md). */
    private boolean templating;
    private String templateShapeType = "Circulo";
    private double templateStartX;
    private double templateStartY;
    private final Group templateGroup = new Group();
    private final Label emptyStateLabel = new Label(
            "Tabuleiro em branco. Use \"+ Nova cena...\" (aba Mapa) pra carregar uma imagem de mapa,\n"
                    + "ou já coloque tokens: \"+ Token\", arrastar o retrato de uma ficha, ou um monstro da aba Fichas.");

    /** Chamado quando uma ficha e largada em cima do mapa. x/y ja vem
     * convertido pro espaco "nativo" do mapa (descontando zoom/pan) --
     * quem recebe nao precisa se preocupar com isso. */
    public interface CharacterDropHandler {
        void handle(String characterId, double mapX, double mapY);
    }

    public MapView(SceneModel scene) {
        this(scene, null);
    }

    /** @param imageAssetService usado pra resolver TokenModel.imageAssetId
     *   (ver TokenNode) -- pode ser null, nesse caso os tokens caem direto
     *   pro imageData/imagePath, igual sempre foi. */
    public MapView(SceneModel scene, ImageAssetService imageAssetService) {
        this.scene = scene;
        this.imageAssetService = imageAssetService;
        measureLine.setStroke(Color.web("#E8A23E"));
        measureLine.setStrokeWidth(2);
        measureLine.setVisible(false);
        measureLabel.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: #F6E9D3;"
                + " -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
        measureLabel.setVisible(false);
        measureLabel.setMouseTransparent(true);
        templateGroup.setMouseTransparent(true);
        fogCanvas.setMouseTransparent(true);
        fogPreview.setFill(Color.web("#FFCF7F", 0.15));
        fogPreview.setStroke(Color.web("#FFCF7F"));
        fogPreview.getStrokeDashArray().addAll(6.0, 4.0);
        fogPreview.setVisible(false);
        fogPreview.setMouseTransparent(true);
        drawingLayer.setMouseTransparent(true);
        visionLayer.setMouseTransparent(true);
        // Desenhos ficam embaixo dos tokens e da neblina (nao revelam area escondida).
        // A mascara de visao fica por cima dos tokens (esconde o que o jogador nao ve).
        content.getChildren().addAll(backgroundView, gridCanvas, objectLayer, drawingLayer, wallLayer, lightLayer, tokenLayer, visionLayer,
                fogCanvas, pinLayer, fogPreview, measureLine, measureLabel, templateGroup);
        getChildren().add(content);
        setStyle("-fx-background-color: #1B1411;");

        emptyStateLabel.setStyle("-fx-text-fill: #7D6A58; -fx-font-size: 14px; -fx-text-alignment: center;");
        emptyStateLabel.setMouseTransparent(true);
        getChildren().add(emptyStateLabel);
        widthProperty().addListener((obs, oldV, newV) -> centerEmptyState());
        heightProperty().addListener((obs, oldV, newV) -> centerEmptyState());
        emptyStateLabel.widthProperty().addListener((obs, oldV, newV) -> centerEmptyState());
        emptyStateLabel.heightProperty().addListener((obs, oldV, newV) -> centerEmptyState());

        loadBackgroundImage();
        redrawGrid();
        redrawDrawings();
        redrawWalls();
        redrawPins();
        redrawObjects();
        rebuildTokens();
        updateEmptyStateVisibility();

        setOnScroll(this::onScroll);
        // Alt+clique em qualquer lugar (ate em cima de token) = ping.
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isAltDown() && e.getButton() == MouseButton.PRIMARY) {
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                showPing(p.getX(), p.getY(), null);
                if (pingHandler != null) pingHandler.accept(p);
                e.consume();
            }
        });
        setOnMousePressed(this::onMapPressed);
        setOnMouseDragged(this::onMapDragged);
        setOnMouseReleased(this::onMapReleased);
        setOnDragOver(this::onDragOver);
        setOnDragDropped(this::onDragDropped);
    }

    // ------------------------------------------------------------------
    // Desenho livre e texto
    // ------------------------------------------------------------------

    /** O que o mapa pede quando alguem desenha ou apaga (o Main passa pela mesa/rede). */
    public interface DrawHandler {
        void add(com.vttcaseiro.model.Drawing drawing);

        void remove(com.vttcaseiro.model.Drawing drawing);
    }

    public static final String TOOL_PEN = "pen";
    public static final String TOOL_TEXT = "text";
    public static final String TOOL_ERASER = "eraser";

    private final javafx.scene.Group drawingLayer = new javafx.scene.Group();
    private String drawTool;
    private DrawHandler drawHandler;
    private String drawColor = "#E2483F";
    private double drawWidth = 3;
    private java.util.List<Double> drawingPoints;
    private javafx.scene.shape.Polyline drawingPreview;

    /** tool: TOOL_PEN, TOOL_TEXT, TOOL_ERASER ou null (desliga). */
    public void setDrawTool(String tool, DrawHandler handler) {
        this.drawTool = tool;
        this.drawHandler = handler;
        setCursor(tool == null ? Cursor.DEFAULT : TOOL_TEXT.equals(tool) ? Cursor.TEXT : Cursor.CROSSHAIR);
    }

    public void setDrawColor(String hex) {
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) this.drawColor = hex;
    }

    public void setDrawWidth(double width) {
        this.drawWidth = width;
    }

    public void redrawDrawings() {
        drawingLayer.getChildren().clear();
        for (com.vttcaseiro.model.Drawing d : scene.getDrawings()) {
            javafx.scene.Node node = nodeFor(d);
            if (node != null) drawingLayer.getChildren().add(node);
        }
    }

    private static javafx.scene.Node nodeFor(com.vttcaseiro.model.Drawing d) {
        java.util.List<Double> p = d.getPoints();
        if (p.size() < 2) return null;
        if (com.vttcaseiro.model.Drawing.TEXT.equals(d.getKind())) {
            Label text = new Label(d.getText());
            text.setStyle("-fx-text-fill: " + d.getColor() + "; -fx-font-size: 16px; -fx-font-weight: bold;"
                    + " -fx-effect: dropshadow(gaussian, #000000, 3, 0.7, 0, 0);");
            text.setLayoutX(p.get(0));
            text.setLayoutY(p.get(1) - 20);
            return text;
        }
        javafx.scene.shape.Polyline line = new javafx.scene.shape.Polyline();
        line.getPoints().addAll(p);
        if (p.size() == 2) line.getPoints().addAll(p.get(0) + 0.1, p.get(1) + 0.1); // um ponto so: um pingo
        line.setStroke(Color.web(d.getColor()));
        line.setStrokeWidth(d.getWidth());
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        line.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        line.setFill(null);
        return line;
    }

    /** Desenho mais perto do ponto (ate {@code max} px), pra borracha. */
    public java.util.Optional<com.vttcaseiro.model.Drawing> drawingNear(double x, double y, double max) {
        com.vttcaseiro.model.Drawing best = null;
        double bestDist = max;
        for (com.vttcaseiro.model.Drawing d : scene.getDrawings()) {
            double dist = d.distanceTo(x, y);
            if (dist <= bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    private void startDrawing(Point2D p) {
        switch (drawTool) {
            case TOOL_PEN -> {
                drawingPoints = new java.util.ArrayList<>(java.util.List.of(p.getX(), p.getY()));
                drawingPreview = new javafx.scene.shape.Polyline(p.getX(), p.getY(), p.getX() + 0.1, p.getY() + 0.1);
                drawingPreview.setStroke(Color.web(drawColor));
                drawingPreview.setStrokeWidth(drawWidth);
                drawingPreview.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
                drawingPreview.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
                drawingPreview.setMouseTransparent(true);
                content.getChildren().add(drawingPreview);
            }
            case TOOL_TEXT -> {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Texto no mapa");
                dialog.setHeaderText(null);
                dialog.setContentText("Texto:");
                dialog.showAndWait().filter(t -> !t.isBlank()).ifPresent(t -> {
                    if (drawHandler != null) drawHandler.add(com.vttcaseiro.model.Drawing.text(p.getX(), p.getY(), t, drawColor));
                });
            }
            case TOOL_ERASER -> drawingNear(p.getX(), p.getY(), 12).ifPresent(d -> {
                if (drawHandler != null) drawHandler.remove(d);
            });
            default -> { }
        }
    }

    private void continueDrawing(Point2D p) {
        if (drawingPoints == null) return;
        int n = drawingPoints.size();
        // Pula pontos muito proximos: traco leve, mensagem pequena.
        if (Math.hypot(p.getX() - drawingPoints.get(n - 2), p.getY() - drawingPoints.get(n - 1)) < 3) return;
        if (n >= com.vttcaseiro.model.Drawing.MAX_POINTS * 2) return;
        drawingPoints.add(p.getX());
        drawingPoints.add(p.getY());
        drawingPreview.getPoints().addAll(p.getX(), p.getY());
    }

    private void finishDrawing() {
        if (drawingPoints == null) return;
        content.getChildren().remove(drawingPreview);
        java.util.List<Double> pts = drawingPoints;
        drawingPoints = null;
        drawingPreview = null;
        if (drawHandler != null) drawHandler.add(com.vttcaseiro.model.Drawing.path(pts, drawColor, drawWidth));
    }

    // ------------------------------------------------------------------
    // Paredes, portas e visao dinamica
    // ------------------------------------------------------------------

    public interface WallHandler {
        void add(com.vttcaseiro.model.Wall wall);

        void remove(com.vttcaseiro.model.Wall wall);

        void toggleDoor(com.vttcaseiro.model.Wall wall);

        /** Mestre muda tipo/estado (trancar, tornar secreta, virar janela...). */
        default void editWall(com.vttcaseiro.model.Wall wall, String label, java.util.function.Consumer<com.vttcaseiro.model.Wall> change) {
        }

        /** Clique com a ferramenta Luz: por uma luz fixa ali. */
        default void placeLight(double x, double y) {
        }

        default void editLight(com.vttcaseiro.model.MapLight light, String label,
                               java.util.function.Consumer<com.vttcaseiro.model.MapLight> change) {
        }

        /** Abre o dialogo da luz (nome, luz plena, penumbra). */
        default void editLightDialog(com.vttcaseiro.model.MapLight light) {
        }

        default void removeLight(com.vttcaseiro.model.MapLight light) {
        }
    }

    public static final String TOOL_WALL = "wall";
    public static final String TOOL_DOOR = "door";
    public static final String TOOL_WINDOW = "window";
    public static final String TOOL_LIGHT = "light";
    public static final String TOOL_WALL_ERASE = "wall-erase";

    /** Icones das luzes fixas (so o mestre ve; o jogador ve a luz). */
    private final javafx.scene.Group lightLayer = new javafx.scene.Group();

    private final javafx.scene.Group wallLayer = new javafx.scene.Group();
    private final javafx.scene.Group visionLayer = new javafx.scene.Group();
    private String wallTool;
    private WallHandler wallHandler;
    private double[] wallStart;
    private javafx.scene.shape.Line wallPreview;
    private boolean gmPreview;
    /** Area que ESTA tela enxerga (null = sem restricao de visao). */
    private javafx.scene.shape.Shape visibleArea;
    /** Casas da grade que este jogador ja viu nesta cena (ficam em penumbra, sem tokens). */
    private final java.util.Set<Long> exploredCells = new java.util.HashSet<>();

    public int exploredCount() {
        return exploredCells.size();
    }

    /** Retangulos das casas exploradas, juntando as vizinhas da mesma linha. */
    private static long hexKey(int q, int r) {
        return ((long) q << 32) ^ (r & 0xffffffffL);
    }

    private javafx.scene.shape.Path exploredPath(double cell, int cols, int rows) {
        javafx.scene.shape.Path path = new javafx.scene.shape.Path();
        if (scene.getGrid().isHex()) {
            // grade hexagonal: o explorado tambem e por hexagono
            com.vttcaseiro.model.GridSettings g = scene.getGrid();
            double s = g.hexRadius() + 0.5;
            for (int[] h : g.hexesCovering(gridCanvas.getWidth(), gridCanvas.getHeight())) {
                if (!exploredCells.contains(hexKey(h[0], h[1]))) continue;
                double[] c = g.hexCenter(h[0], h[1]);
                for (int i = 0; i < 6; i++) {
                    double ang = Math.toRadians(60 * i - 30);
                    double x = c[0] + s * Math.cos(ang), y = c[1] + s * Math.sin(ang);
                    path.getElements().add(i == 0 ? new javafx.scene.shape.MoveTo(x, y) : new javafx.scene.shape.LineTo(x, y));
                }
                path.getElements().add(new javafx.scene.shape.ClosePath());
            }
            return path;
        }
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                if (!exploredCells.contains(((long) c << 32) | r)) { c++; continue; }
                int start = c;
                while (c < cols && exploredCells.contains(((long) c << 32) | r)) c++;
                double x0 = start * cell, x1 = c * cell, y0 = r * cell, y1 = (r + 1) * cell;
                path.getElements().addAll(new javafx.scene.shape.MoveTo(x0, y0), new javafx.scene.shape.LineTo(x1, y0),
                        new javafx.scene.shape.LineTo(x1, y1), new javafx.scene.shape.LineTo(x0, y1),
                        new javafx.scene.shape.ClosePath());
            }
        }
        return path;
    }

    public void setWallHandler(WallHandler handler) {
        this.wallHandler = handler;
    }

    /** TOOL_WALL, TOOL_DOOR, TOOL_WALL_ERASE ou null. */
    public void setWallTool(String tool) {
        this.wallTool = tool;
        if (tool == null && wallPreview != null) {
            content.getChildren().remove(wallPreview);
            wallPreview = null;
            wallStart = null;
        }
        if (tool != null) setCursor(Cursor.CROSSHAIR);
        else if (drawTool == null) setCursor(Cursor.DEFAULT);
    }

    /** Mestre: ver a mascara dos jogadores por cima do mapa. */
    public void setGmPreview(boolean preview) {
        this.gmPreview = preview;
        updateVision();
    }

    /** Este ponto esta na area que eu enxergo? (sem visao dinamica: sempre). */
    public boolean isVisibleToMe(double x, double y) {
        return visibleArea == null || visibleArea.contains(x, y);
    }

    public void redrawWalls() {
        wallLayer.getChildren().clear();
        boolean gm = tokenActions == null || tokenActions.isGm();
        for (com.vttcaseiro.model.Wall w : scene.getWalls()) {
            // jogador nao ve paredes nem janelas (so a escuridao atras), e a porta secreta so quando aberta
            if (!gm && (!w.isDoor() || (w.isSecret() && !w.isOpen()))) continue;
            javafx.scene.shape.Line line = new javafx.scene.shape.Line(w.getX1(), w.getY1(), w.getX2(), w.getY2());
            line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            if (w.isDoor()) {
                String color = w.isOpen() ? "#3FBE6E" : w.isLocked() ? "#D9544C" : w.isSecret() ? "#A070E0" : "#E0A040";
                line.setStroke(Color.web(color));
                line.setStrokeWidth(6);
                if (w.isOpen()) line.getStrokeDashArray().addAll(8.0, 8.0);
                else if (w.isSecret() && gm) line.getStrokeDashArray().addAll(3.0, 6.0);
                line.setCursor(Cursor.HAND);
                String state = (w.isSecret() ? "Porta secreta" : "Porta") + (w.isOpen() ? " aberta" : w.isLocked() ? " trancada" : " fechada");
                javafx.scene.control.Tooltip.install(line, new javafx.scene.control.Tooltip(state
                        + (w.gmOnly() && !gm ? "" : " -- clique para " + (w.isOpen() ? "fechar" : "abrir"))
                        + (gm ? " · botão direito: trancar, secreta, tipo" : "")));
                line.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && wallTool == null && drawTool == null && wallHandler != null) {
                        wallHandler.toggleDoor(w);
                        e.consume();
                    }
                });
            } else if (w.isWindow()) {
                line.setStroke(Color.web("#7FC8F0"));
                line.setStrokeWidth(4);
                line.getStrokeDashArray().addAll(2.0, 6.0);
                javafx.scene.control.Tooltip.install(line, new javafx.scene.control.Tooltip(
                        "Janela: deixa ver, não deixa passar · botão direito: tipo"));
            } else {
                line.setStroke(Color.web("#E2D3BC"));
                line.setStrokeWidth(3);
            }
            if (gm && wallHandler != null) {
                line.setOnContextMenuRequested(e -> {
                    wallMenu(w).show(line, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
            } else if (!w.isDoor()) {
                line.setMouseTransparent(true);
            }
            wallLayer.getChildren().add(line);
        }
        redrawLights(gm);
        updateVision();
    }

    /** Menu do mestre numa parede/porta/janela. */
    private javafx.scene.control.ContextMenu wallMenu(com.vttcaseiro.model.Wall w) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        if (w.isDoor()) {
            menu.getItems().add(menuItem(w.isOpen() ? "Fechar" : "Abrir", () -> wallHandler.toggleDoor(w)));
            menu.getItems().add(menuItem(w.isLocked() ? "Destrancar" : "Trancar (só o mestre abre)",
                    () -> wallHandler.editWall(w, "Tranca", x -> x.setLocked(!x.isLocked()))));
            menu.getItems().add(menuItem(w.isSecret() ? "Revelar (vira porta comum)" : "Tornar secreta (jogador vê parede)",
                    () -> wallHandler.editWall(w, "Porta secreta", x -> x.setSecret(!x.isSecret()))));
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        }
        javafx.scene.control.Menu kind = new javafx.scene.control.Menu("Tipo");
        kind.getItems().addAll(
                menuItem("Parede", () -> wallHandler.editWall(w, "Tipo", x -> x.setKind(false, false))),
                menuItem("Porta", () -> wallHandler.editWall(w, "Tipo", x -> x.setKind(true, false))),
                menuItem("Janela (deixa ver, não passa)", () -> wallHandler.editWall(w, "Tipo", x -> x.setKind(false, true))));
        menu.getItems().addAll(kind, menuItem("Apagar", () -> wallHandler.remove(w)));
        return menu;
    }

    /** Luzes fixas: so o mestre ve o icone (arrasta pra mover; botao direito: acender, editar, tirar). */
    private void redrawLights(boolean gm) {
        lightLayer.getChildren().clear();
        if (!gm) return;
        for (com.vttcaseiro.model.MapLight l : scene.getLights()) {
            javafx.scene.shape.Circle glow = new javafx.scene.shape.Circle(l.getX(), l.getY(), 13,
                    Color.web(l.isOn() ? "#FFCF7F" : "#8C7B6B", 0.35));
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(l.getX(), l.getY(), 7,
                    Color.web(l.isOn() ? "#FFCF7F" : "#5A4A3E"));
            dot.setStroke(Color.web("#1B1411"));
            dot.setStrokeWidth(1.5);
            javafx.scene.Group icon = new javafx.scene.Group(glow, dot);
            icon.setCursor(Cursor.HAND);
            javafx.scene.control.Tooltip.install(icon, new javafx.scene.control.Tooltip(l.getName() + " · "
                    + (l.isOn() ? "acesa" : "apagada") + " · plena " + Distances.feetAndMeters(l.getBrightFeet())
                    + ", penumbra +" + Distances.feetAndMeters(l.getDimFeet()) + " · arraste pra mover, botão direito pra mais"));
            double[] drag = new double[4];
            icon.setOnMousePressed(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                drag[0] = p.getX();
                drag[1] = p.getY();
                drag[2] = 0;
                e.consume();
            });
            icon.setOnMouseDragged(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                icon.setTranslateX(p.getX() - drag[0]);
                icon.setTranslateY(p.getY() - drag[1]);
                drag[2] = 1;
                e.consume();
            });
            icon.setOnMouseReleased(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                if (drag[2] == 1 && wallHandler != null) {
                    double nx = l.getX() + icon.getTranslateX(), ny = l.getY() + icon.getTranslateY();
                    wallHandler.editLight(l, "Mover luz", x -> x.setPosition(nx, ny));
                }
                e.consume();
            });
            icon.setOnContextMenuRequested(e -> {
                javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
                menu.getItems().addAll(
                        menuItem(l.isOn() ? "Apagar" : "Acender", () -> wallHandler.editLight(l, "Luz", x -> x.setOn(!x.isOn()))),
                        menuItem("Editar...", () -> wallHandler.editLightDialog(l)),
                        menuItem("Tirar do mapa", () -> wallHandler.removeLight(l)));
                menu.show(icon, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            lightLayer.getChildren().add(icon);
        }
    }

    /** Ponta de parede existente ou cruzamento da grade perto (10 px): junta as paredes certinho. */
    private double[] snapWallPoint(double x, double y) {
        double best = 10;
        double[] snapped = null;
        for (com.vttcaseiro.model.Wall w : scene.getWalls()) {
            for (double[] p : new double[][]{{w.getX1(), w.getY1()}, {w.getX2(), w.getY2()}}) {
                double d = Math.hypot(p[0] - x, p[1] - y);
                if (d < best) {
                    best = d;
                    snapped = p;
                }
            }
        }
        if (snapped != null) return snapped.clone();
        double cell = scene.getGrid().getCellSize();
        if (cell > 1) {
            double ox = scene.getGrid().getOffsetX();
            double oy = scene.getGrid().getOffsetY();
            double gx = Math.round((x - ox) / cell) * cell + ox;
            double gy = Math.round((y - oy) / cell) * cell + oy;
            if (Math.hypot(gx - x, gy - y) < 10) return new double[]{gx, gy};
        }
        return new double[]{x, y};
    }

    private void startWall(Point2D p) {
        if (TOOL_LIGHT.equals(wallTool)) {
            if (wallHandler != null) wallHandler.placeLight(p.getX(), p.getY());
            return;
        }
        if (TOOL_WALL_ERASE.equals(wallTool)) {
            com.vttcaseiro.model.Wall nearest = null;
            double best = 10;
            for (com.vttcaseiro.model.Wall w : scene.getWalls()) {
                double d = w.distanceTo(p.getX(), p.getY());
                if (d < best) {
                    best = d;
                    nearest = w;
                }
            }
            if (nearest != null && wallHandler != null) wallHandler.remove(nearest);
            return;
        }
        wallStart = snapWallPoint(p.getX(), p.getY());
        wallPreview = new javafx.scene.shape.Line(wallStart[0], wallStart[1], wallStart[0], wallStart[1]);
        wallPreview.setStroke(Color.web(TOOL_DOOR.equals(wallTool) ? "#E0A040" : TOOL_WINDOW.equals(wallTool) ? "#7FC8F0" : "#E2D3BC"));
        wallPreview.setStrokeWidth(TOOL_DOOR.equals(wallTool) ? 6 : 3);
        wallPreview.getStrokeDashArray().addAll(6.0, 4.0);
        wallPreview.setMouseTransparent(true);
        content.getChildren().add(wallPreview);
    }

    private void finishWall(Point2D p) {
        if (wallStart == null) return;
        double[] end = snapWallPoint(p.getX(), p.getY());
        content.getChildren().remove(wallPreview);
        wallPreview = null;
        double[] start = wallStart;
        wallStart = null;
        if (Math.hypot(end[0] - start[0], end[1] - start[1]) < 5 || wallHandler == null) return;
        com.vttcaseiro.model.Wall wall = new com.vttcaseiro.model.Wall(start[0], start[1], end[0], end[1], TOOL_DOOR.equals(wallTool));
        if (TOOL_WINDOW.equals(wallTool)) wall.setKind(false, true);
        wallHandler.add(wall);
    }

    private double[] centerOf(TokenModel t) {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        return new double[]{t.getX() + cell * t.getWidthCells() / 2.0, t.getY() + cell * t.getHeightCells() / 2.0};
    }

    private javafx.scene.shape.Shape visionPolygon(double cx, double cy) {
        double[] pts = com.vttcaseiro.vision.Visibility.polygon(cx, cy, scene.getWalls(),
                gridCanvas.getWidth(), gridCanvas.getHeight());
        javafx.scene.shape.Polygon poly = new javafx.scene.shape.Polygon(pts);
        return poly;
    }

    private static javafx.scene.shape.Shape union(javafx.scene.shape.Shape a, javafx.scene.shape.Shape b) {
        if (a == null) return b;
        if (b == null) return a;
        return javafx.scene.shape.Shape.union(a, b);
    }

    private double feetToPx(int feet) {
        return com.vttcaseiro.vision.Visibility.feetToPx(feet, scene.getGrid().getCellSize(), scene.getGrid().getFeetPerCell());
    }

    /**
     * Mascara de visao: o jogador ve o que os tokens DELE enxergam (paredes
     * bloqueiam). Cena escura: so ate a visao no escuro / luz do token, mais
     * a luz de outros tokens que ele consiga ver. Tokens fora da visao somem.
     * O mestre ve tudo (ou a mascara translucida na "Previa").
     */
    public void updateVision() {
        visionLayer.getChildren().clear();
        visibleArea = null;
        boolean gm = tokenActions == null || tokenActions.isGm();
        if (!scene.isVisionEnabled() || (gm && !gmPreview)) {
            for (javafx.scene.Node n : tokenLayer.getChildren()) n.setVisible(true);
            return;
        }
        java.util.List<TokenModel> viewers = new java.util.ArrayList<>();
        for (TokenModel t : scene.getTokens()) {
            boolean mine = gm ? (t.getCharacterSheetId() != null || t.getOwnerId() != null) : tokenActions.canControl(t);
            if (mine) viewers.add(t);
        }
        // Luz e visao do 5.5e (ver vision.Perception): o que tem luz plena, o que tem penumbra, e o que cada token
        // enxerga com visao no escuro / as cegas / verdadeira. Penumbra aparece mais escura; escuridao some.
        com.vttcaseiro.vision.Perception.Light ambient = com.vttcaseiro.vision.Perception.ambient(scene);
        boolean dark = ambient == com.vttcaseiro.vision.Perception.Light.DARK;
        javafx.scene.shape.Rectangle all = new javafx.scene.shape.Rectangle(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
        javafx.scene.shape.Shape brightLit = ambient == com.vttcaseiro.vision.Perception.Light.BRIGHT ? all : null;
        javafx.scene.shape.Shape dimLit = dark ? null : all;   // penumbra ou melhor
        if (ambient != com.vttcaseiro.vision.Perception.Light.BRIGHT) {
            // luz de qualquer token (a tocha do aliado) ilumina pra quem estiver vendo aquele lugar
            for (TokenModel l : scene.getTokens()) {
                if (l.lightReachFeet() <= 0) continue;
                double[] c = centerOf(l);
                javafx.scene.shape.Shape poly = visionPolygon(c[0], c[1]);
                if (l.getLightFeet() > 0) brightLit = union(brightLit, javafx.scene.shape.Shape.intersect(poly,
                        new javafx.scene.shape.Circle(c[0], c[1], feetToPx(l.getLightFeet()))));
                dimLit = union(dimLit, javafx.scene.shape.Shape.intersect(poly,
                        new javafx.scene.shape.Circle(c[0], c[1], feetToPx(l.lightReachFeet()))));
            }
            // luzes fixas do mapa acesas
            for (com.vttcaseiro.model.MapLight l : scene.getLights()) {
                if (!l.isOn() || l.reachFeet() <= 0) continue;
                javafx.scene.shape.Shape poly = visionPolygon(l.getX(), l.getY());
                if (l.getBrightFeet() > 0) brightLit = union(brightLit, javafx.scene.shape.Shape.intersect(poly,
                        new javafx.scene.shape.Circle(l.getX(), l.getY(), feetToPx(l.getBrightFeet()))));
                dimLit = union(dimLit, javafx.scene.shape.Shape.intersect(poly,
                        new javafx.scene.shape.Circle(l.getX(), l.getY(), feetToPx(l.reachFeet()))));
            }
        }
        javafx.scene.shape.Shape visible = null;
        javafx.scene.shape.Shape bright = null;
        for (TokenModel t : viewers) {
            double[] c = centerOf(t);
            javafx.scene.shape.Shape sightPoly = new javafx.scene.shape.Polygon(com.vttcaseiro.vision.Visibility.polygon(
                    c[0], c[1], scene.getWalls(), gridCanvas.getWidth(), gridCanvas.getHeight()));
            javafx.scene.shape.Shape darkvision = t.getVisionFeet() > 0
                    ? new javafx.scene.shape.Circle(c[0], c[1], feetToPx(t.getVisionFeet())) : null;
            int senseFeet = Math.max(t.getTruesightFeet(), t.getBlindsightFeet());
            javafx.scene.shape.Shape sense = senseFeet > 0 ? new javafx.scene.shape.Circle(c[0], c[1], feetToPx(senseFeet)) : null;
            // luz plena: lugar com luz plena, penumbra dentro da visao no escuro, ou o alcance das cegas/verdadeira
            javafx.scene.shape.Shape b = union(brightLit, union(intersectOrNull(dimLit, darkvision), sense));
            // visivel: penumbra ou melhor, ou dentro da visao no escuro (escuridao vira penumbra)
            javafx.scene.shape.Shape a = union(dimLit, union(darkvision, sense));
            if (b != null) bright = union(bright, javafx.scene.shape.Shape.intersect(sightPoly, b));
            if (a != null) visible = union(visible, javafx.scene.shape.Shape.intersect(sightPoly, a));
        }
        visibleArea = visible == null ? new javafx.scene.shape.Rectangle(0, 0, 0, 0) : visible;
        // Memoria do explorado (so jogador): casa cujo centro ja foi visto fica em penumbra.
        double cellSize = Math.max(10, scene.getGrid().getCellSize());
        int cols = gridCols();
        int rows = gridRows();
        if (!gm && scene.getGrid().isHex()) {
            com.vttcaseiro.model.GridSettings g = scene.getGrid();
            for (int[] h : g.hexesCovering(gridCanvas.getWidth(), gridCanvas.getHeight())) {
                double[] c = g.hexCenter(h[0], h[1]);
                if (visibleArea.contains(c[0], c[1])) exploredCells.add(hexKey(h[0], h[1]));
            }
        } else if (!gm) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (visibleArea.contains((c + 0.5) * cellSize, (r + 0.5) * cellSize)) exploredCells.add(((long) c << 32) | r);
                }
            }
        }
        javafx.scene.shape.Shape seen = gm || exploredCells.isEmpty() ? visible
                : union(visible, exploredPath(cellSize, cols, rows));
        javafx.scene.shape.Shape mask = seen == null ? all : javafx.scene.shape.Shape.subtract(all, seen);
        mask.setFill(gm ? Color.rgb(0, 0, 0, 0.55) : Color.web("#050608"));
        mask.setStroke(null);
        mask.setMouseTransparent(true);
        visionLayer.getChildren().add(mask);
        // penumbra: o que se ve, mas sem luz plena, fica mais escuro (Percepcao com desvantagem)
        if (visible != null && ambient != com.vttcaseiro.vision.Perception.Light.BRIGHT) {
            javafx.scene.shape.Shape dimOnly = bright == null ? visible : javafx.scene.shape.Shape.subtract(visible, bright);
            dimOnly.setFill(Color.rgb(0, 0, 0, 0.4));
            dimOnly.setStroke(null);
            dimOnly.setMouseTransparent(true);
            visionLayer.getChildren().add(dimOnly);
        }
        if (!gm && !exploredCells.isEmpty()) {
            javafx.scene.shape.Shape dim = visible == null ? exploredPath(cellSize, cols, rows)
                    : javafx.scene.shape.Shape.subtract(exploredPath(cellSize, cols, rows), visible);
            dim.setFill(Color.rgb(5, 6, 8, 0.6));
            dim.setStroke(null);
            dim.setMouseTransparent(true);
            visionLayer.getChildren().add(dim);
        }
        // tokens: a mesma regra do servidor (altura nas distancias, sentido sismico, tocha vista de longe no escuro)
        com.vttcaseiro.vision.VisionCheck eyes = com.vttcaseiro.vision.VisionCheck.of(scene, viewers);
        for (javafx.scene.Node n : tokenLayer.getChildren()) {
            if (!(n instanceof TokenNode tn)) continue;
            TokenModel t = tn.getModel();
            double[] c = centerOf(t);
            boolean seenNow = eyes.canSee(t);
            tn.setVisible(gm || viewers.contains(t) || seenNow);
            // sentido (sismico, tocha ao longe) sem enxergar o lugar: aparece meio apagado
            tn.setSensedOnly(!(gm || viewers.contains(t) || visibleArea.contains(c[0], c[1])));
        }
    }

    private static javafx.scene.shape.Shape intersectOrNull(javafx.scene.shape.Shape a, javafx.scene.shape.Shape b) {
        return a == null || b == null ? null : javafx.scene.shape.Shape.intersect(a, b);
    }

    /** Jogador soltando o token: o caminho reto nao pode atravessar parede/porta fechada. */
    private boolean allowMove(TokenModel t, double[] xy) {
        if (tokenActions == null || tokenActions.isGm() || scene.getWalls().isEmpty()) return true;
        double[] from = centerOf(t);
        double cell = Math.max(10, scene.getGrid().getCellSize());
        double tx = xy[0] + cell * t.getWidthCells() / 2.0;
        double ty = xy[1] + cell * t.getHeightCells() / 2.0;
        if (com.vttcaseiro.vision.Visibility.blocked(from[0], from[1], tx, ty, scene.getWalls())) {
            tokenActions.notice("Uma parede (ou porta fechada) bloqueia o caminho de " + t.getName() + ".");
            return false;
        }
        return true;
    }

    private java.util.function.Consumer<Point2D> pingHandler;

    /** Quem e avisado do ping local (o Main manda pela rede). */
    public void setPingHandler(java.util.function.Consumer<Point2D> handler) {
        this.pingHandler = handler;
    }

    /**
     * "Olha aqui!": tres aneis dourados que crescem e somem em ~2,5 s, com o
     * nome de quem pingou. Puramente visual, nao muda a cena.
     */
    public void showPing(double x, double y, String who) {
        javafx.scene.Group ping = new javafx.scene.Group();
        ping.setMouseTransparent(true);
        double cell = Math.max(30, scene.getGrid().getCellSize());
        javafx.animation.ParallelTransition all = new javafx.animation.ParallelTransition();
        for (int i = 0; i < 3; i++) {
            javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(x, y, cell * 0.25);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(Color.web("#FFCF7F"));
            ring.setStrokeWidth(3);
            ping.getChildren().add(ring);
            javafx.animation.ScaleTransition grow = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(900), ring);
            grow.setFromX(1);
            grow.setFromY(1);
            grow.setToX(4);
            grow.setToY(4);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(900), ring);
            fade.setFromValue(1);
            fade.setToValue(0);
            javafx.animation.ParallelTransition one = new javafx.animation.ParallelTransition(grow, fade);
            one.setDelay(javafx.util.Duration.millis(i * 450));
            ring.setOpacity(0);
            all.getChildren().add(one);
        }
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(x, y, 5, Color.web("#FFCF7F"));
        ping.getChildren().add(dot);
        if (who != null && !who.isBlank()) {
            Label name = new Label(who);
            name.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: #FFCF7F; -fx-font-weight: bold;"
                    + " -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
            name.setLayoutX(x + 10);
            name.setLayoutY(y - 28);
            ping.getChildren().add(name);
        }
        content.getChildren().add(ping);
        javafx.animation.FadeTransition out = new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), ping);
        out.setDelay(javafx.util.Duration.millis(2000));
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(ev -> content.getChildren().remove(ping));
        all.play();
        out.play();
    }

    private void centerEmptyState() {
        emptyStateLabel.setLayoutX((getWidth() - emptyStateLabel.getWidth()) / 2);
        emptyStateLabel.setLayoutY((getHeight() - emptyStateLabel.getHeight()) / 2);
    }

    /** Ferramenta de neblina do mestre: arrastar no mapa vazio revela
     * (reveal=true) ou cobre (false) as casas do retangulo. null desliga. */
    public void setFogTool(Boolean reveal, FogEditHandler handler) {
        this.fogTool = reveal;
        this.fogEditHandler = handler;
        if (reveal == null) fogPreview.setVisible(false);
    }

    public int gridCols() {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        return (int) Math.ceil(gridCanvas.getWidth() / cell);
    }

    public int gridRows() {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        return (int) Math.ceil(gridCanvas.getHeight() / cell);
    }

    /** Pinta a neblina por cima dos tokens (casa nao revelada = coberta). */
    private java.util.function.BiConsumer<java.util.List<int[]>, Boolean> fogCellsHandler;

    /** Neblina na grade hexagonal: recebe os hexagonos marcados pela ferramenta. */
    public void setFogCellsHandler(java.util.function.BiConsumer<java.util.List<int[]>, Boolean> handler) {
        this.fogCellsHandler = handler;
    }

    /** Todos os hexagonos do mapa (pra "Revelar tudo"). */
    public java.util.List<int[]> allHexCells() {
        return scene.getGrid().hexesCovering(gridCanvas.getWidth(), gridCanvas.getHeight());
    }

    public void redrawFog() {
        fogCanvas.setWidth(gridCanvas.getWidth());
        fogCanvas.setHeight(gridCanvas.getHeight());
        GraphicsContext gc = fogCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, fogCanvas.getWidth(), fogCanvas.getHeight());
        com.vttcaseiro.model.Fog fog = scene.getFog();
        if (!fog.isEnabled()) return;
        boolean gm = tokenActions == null || tokenActions.isGm();
        gc.setFill(gm ? Color.rgb(0, 0, 0, 0.55) : Color.web("#0B0C0E"));
        double cell = Math.max(10, scene.getGrid().getCellSize());
        if (scene.getGrid().isHex()) {
            com.vttcaseiro.model.GridSettings g = scene.getGrid();
            double s = g.hexRadius() + 0.6; // um tiquinho maior: sem frestas entre os hexagonos
            double[] xs = new double[6];
            double[] ys = new double[6];
            for (int[] h : g.hexesCovering(fogCanvas.getWidth(), fogCanvas.getHeight())) {
                if (fog.isRevealed(h[0], h[1])) continue;
                double[] c = g.hexCenter(h[0], h[1]);
                for (int i = 0; i < 6; i++) {
                    double ang = Math.toRadians(60 * i - 30);
                    xs[i] = c[0] + s * Math.cos(ang);
                    ys[i] = c[1] + s * Math.sin(ang);
                }
                gc.fillPolygon(xs, ys, 6);
            }
            return;
        }
        int cols = gridCols();
        int rows = gridRows();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (!fog.isRevealed(c, r)) gc.fillRect(c * cell, r * cell, cell + 0.5, cell + 0.5);
            }
        }
    }

    private static final int BLANK_COLS = 30;
    private static final int BLANK_ROWS = 20;

    private void updateEmptyStateVisibility() {
        emptyStateLabel.setVisible(false); // o cartao "Comece aqui" da tela principal faz esse papel agora
    }

    /**
     * Onde colocar um token novo: no centro do que esta visivel agora
     * (descontando zoom/pan), alinhado na grade, deslocado {@code index}
     * casas pra direita pra varios tokens seguidos nao se empilharem.
     */
    public javafx.geometry.Point2D suggestTokenPosition(int index) {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        javafx.geometry.Point2D center = content.parentToLocal(getWidth() / 2, getHeight() / 2);
        double x = Math.max(0, Math.floor(center.getX() / cell) * cell + (index % 6) * cell);
        double y = Math.max(0, Math.floor(center.getY() / cell) * cell + (index / 6) * cell);
        double[] snapped = scene.getGrid().snapTopLeft(x, y, 1);
        return new javafx.geometry.Point2D(snapped[0], snapped[1]);
    }

    /** Volta o zoom pra 1x e centraliza a navegacao -- o botao "Centralizar"
     * da barra de ferramentas flutuante usa isso. */
    public void resetView() {
        zoom = 1.0;
        content.setScaleX(1.0);
        content.setScaleY(1.0);
        content.setTranslateX(0);
        content.setTranslateY(0);
        updatePinScale();
    }

    public javafx.scene.image.Image backgroundImage() {
        return backgroundView.getImage();
    }

    /** Mostra o mapa inteiro na tela (bom pra mapa-mundo grande). */
    public void fitToView() {
        if (backgroundView.getImage() == null || getWidth() <= 0 || getHeight() <= 0) {
            resetView();
            return;
        }
        double z = Math.max(MIN_ZOOM, Math.min(1.0, Math.min(getWidth() / mapWidth(), getHeight() / mapHeight())));
        zoom = z;
        content.setScaleX(z);
        content.setScaleY(z);
        updatePinScale();
        content.applyCss();
        content.layout();
        javafx.geometry.Bounds b = content.getBoundsInParent();
        content.setTranslateX(content.getTranslateX() - b.getMinX() + (getWidth() - b.getWidth()) / 2);
        content.setTranslateY(content.getTranslateY() - b.getMinY() + (getHeight() - b.getHeight()) / 2);
    }

    // ------------------------------------------------------------------
    // Pins (pontos de informacao / atalhos pra outras cenas)
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Objetos soltos no mapa (moveis, arvores, baus...)
    // ------------------------------------------------------------------

    /** O que o mapa pede quando o mestre mexe num objeto (o Main passa pela mesa/rede). */
    public interface ObjectHandler {
        /** Mudou posicao/tamanho/giro/oculto/travado. */
        void changed(com.vttcaseiro.model.MapObject o);

        void remove(com.vttcaseiro.model.MapObject o);

        void reorder(com.vttcaseiro.model.MapObject o, boolean toFront);

        void duplicate(com.vttcaseiro.model.MapObject o);
    }

    private final Group objectLayer = new Group();
    private ObjectHandler objectHandler;
    private boolean objectEditing;
    private String selectedObjectId;
    private final java.util.Map<String, Image> objectImageCache = new java.util.HashMap<>();

    public void setObjectHandler(ObjectHandler handler) {
        this.objectHandler = handler;
    }

    /** Modo "editar objetos" (mestre): objetos recebem clique/arraste; fora dele sao so cenario. */
    public void setObjectEditing(boolean on) {
        this.objectEditing = on;
        if (!on) selectedObjectId = null;
        redrawObjects();
    }

    public boolean isObjectEditing() {
        return objectEditing;
    }

    private Image objectImage(String assetId) {
        if (assetId == null || imageAssetService == null) return null;
        Image cached = objectImageCache.get(assetId);
        if (cached != null) return cached;
        try {
            Image img = new Image(new java.io.ByteArrayInputStream(imageAssetService.readBytes(assetId)));
            if (img.isError()) return null;
            objectImageCache.put(assetId, img);
            return img;
        } catch (java.io.IOException ex) {
            return null; // imagem ainda chegando pela rede
        }
    }

    public void redrawObjects() {
        objectLayer.getChildren().clear();
        boolean gm = tokenActions == null || tokenActions.isGm();
        objectLayer.setMouseTransparent(!(objectEditing && gm));
        for (com.vttcaseiro.model.MapObject o : scene.getObjects()) {
            if (o.isHidden() && !gm) continue;
            objectLayer.getChildren().add(objectNode(o, gm));
        }
    }

    private javafx.scene.Node objectNode(com.vttcaseiro.model.MapObject o, boolean gm) {
        javafx.scene.Group node = new javafx.scene.Group();
        Image img = objectImage(o.getAssetId());
        javafx.scene.Node visual;
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(o.getWidth());
            iv.setFitHeight(o.getHeight());
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            visual = iv;
        } else {
            Rectangle ph = new Rectangle(o.getWidth(), o.getHeight(), Color.web("#45342A", 0.5));
            ph.setStroke(Color.web("#B09A82"));
            ph.getStrokeDashArray().addAll(6.0, 4.0);
            visual = ph;
        }
        node.getChildren().add(visual);
        boolean selected = objectEditing && o.getId().equals(selectedObjectId);
        if (selected || (objectEditing && o.isLocked())) {
            Rectangle outline = new Rectangle(-2, -2, o.getWidth() + 4, o.getHeight() + 4);
            outline.setFill(Color.TRANSPARENT);
            outline.setStroke(Color.web(o.isLocked() ? "#B09A82" : "#FFCF7F"));
            outline.setStrokeWidth(2 / zoom);
            outline.getStrokeDashArray().addAll(8.0, 5.0);
            outline.setMouseTransparent(true);
            node.getChildren().add(outline);
        }
        if (o.isHidden()) node.setOpacity(0.5);
        node.setLayoutX(o.getX());
        node.setLayoutY(o.getY());
        // giro em volta do centro do objeto
        node.getTransforms().add(new javafx.scene.transform.Rotate(o.getRotation(), o.getWidth() / 2, o.getHeight() / 2));
        if (!(objectEditing && gm)) return node;

        node.setCursor(o.isLocked() ? Cursor.DEFAULT : Cursor.MOVE);
        if (!o.getName().isBlank()) javafx.scene.control.Tooltip.install(node, new javafx.scene.control.Tooltip(o.getName()));
        double[] press = new double[4];
        boolean[] moved = {false};
        node.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                press[0] = p.getX();
                press[1] = p.getY();
                press[2] = o.getX();
                press[3] = o.getY();
                moved[0] = false;
                if (!o.getId().equals(selectedObjectId)) {
                    selectedObjectId = o.getId();
                    javafx.application.Platform.runLater(this::redrawObjects);
                }
                e.consume();
            } else if (e.getButton() == MouseButton.SECONDARY && objectHandler != null) {
                selectedObjectId = o.getId();
                objectMenu(o).show(node, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
        node.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || o.isLocked()) return;
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - press[0], dy = p.getY() - press[1];
            if (!moved[0] && Math.hypot(dx, dy) * zoom < 3) return;
            moved[0] = true;
            node.setLayoutX(press[2] + dx);
            node.setLayoutY(press[3] + dy);
            e.consume();
        });
        node.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (moved[0] && objectHandler != null) {
                double nx = node.getLayoutX(), ny = node.getLayoutY();
                if (e.isAltDown() || scene.getGrid().isGridless()) {
                    o.setPosition(nx, ny);
                } else {
                    // encaixa o canto na grade (Alt solta livre)
                    double c = scene.getGrid().getCellSize();
                    o.setPosition(Math.round(nx / (c / 2)) * (c / 2), Math.round(ny / (c / 2)) * (c / 2));
                }
                objectHandler.changed(o);
            }
            moved[0] = false;
            e.consume();
        });
        node.setOnScroll(e -> {
            // Ctrl+roda = tamanho; Shift+roda = gira (sem Ctrl/Shift a roda continua dando zoom no mapa)
            if (objectHandler == null || o.isLocked()) return;
            if (e.isControlDown()) {
                o.scaleBy(e.getDeltaY() > 0 ? 1.1 : 1 / 1.1);
            } else if (e.isShiftDown()) {
                o.setRotation(o.getRotation() + ((e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX()) > 0 ? 15 : -15));
            } else {
                return;
            }
            objectHandler.changed(o);
            e.consume();
        });
        return node;
    }

    private javafx.scene.control.ContextMenu objectMenu(com.vttcaseiro.model.MapObject o) {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        Runnable apply = () -> objectHandler.changed(o);
        javafx.scene.control.Menu size = new javafx.scene.control.Menu("Tamanho");
        size.getItems().addAll(
                menuItem("Maior (+25%)   Ctrl+roda", () -> { o.scaleBy(1.25); apply.run(); }),
                menuItem("Menor (-20%)", () -> { o.scaleBy(0.8); apply.run(); }),
                menuItem("1 casa", () -> { fitCells(o, 1); apply.run(); }),
                menuItem("2 casas", () -> { fitCells(o, 2); apply.run(); }),
                menuItem("3 casas", () -> { fitCells(o, 3); apply.run(); }));
        javafx.scene.control.Menu rot = new javafx.scene.control.Menu("Girar");
        rot.getItems().addAll(
                menuItem("45\u00b0 hor\u00e1rio   Shift+roda", () -> { o.setRotation(o.getRotation() + 45); apply.run(); }),
                menuItem("90\u00b0 hor\u00e1rio", () -> { o.setRotation(o.getRotation() + 90); apply.run(); }),
                menuItem("90\u00b0 anti-hor\u00e1rio", () -> { o.setRotation(o.getRotation() - 90); apply.run(); }),
                menuItem("Voltar ao normal", () -> { o.setRotation(0); apply.run(); }));
        m.getItems().addAll(size, rot,
                menuItem("Trazer para a frente", () -> objectHandler.reorder(o, true)),
                menuItem("Mandar para o fundo", () -> objectHandler.reorder(o, false)),
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem(o.isHidden() ? "Mostrar aos jogadores" : "Esconder dos jogadores", () -> { o.setHidden(!o.isHidden()); apply.run(); }),
                menuItem(o.isLocked() ? "Destravar" : "Travar (n\u00e3o arrasta sem querer)", () -> { o.setLocked(!o.isLocked()); apply.run(); }),
                menuItem("Duplicar", () -> objectHandler.duplicate(o)),
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem("Apagar objeto", () -> objectHandler.remove(o)));
        return m;
    }

    /** Ajusta o lado maior pra N casas, mantendo a proporcao e o centro. */
    private void fitCells(com.vttcaseiro.model.MapObject o, int cells) {
        double target = cells * Math.max(10, scene.getGrid().getCellSize());
        double factor = target / Math.max(o.getWidth(), o.getHeight());
        o.scaleBy(factor);
    }

    /** O que o mapa pede quando mexem num pin (o Main mostra cartao, dialogo, troca de cena). */
    public interface PinHandler {
        /** Clique com a ferramenta de pin num lugar vazio. */
        void create(double x, double y);

        /** Clique no pin: abre o cartao de informacao. */
        void open(com.vttcaseiro.model.MapPin pin);

        /** Mestre arrastou o pin. */
        void moved(com.vttcaseiro.model.MapPin pin, double x, double y);

        /** Botao direito (mestre): editar. */
        void edit(com.vttcaseiro.model.MapPin pin);

        /** Botao direito (mestre): apagar. */
        void remove(com.vttcaseiro.model.MapPin pin);
    }

    private final Group pinLayer = new Group();
    private PinHandler pinHandler;
    private boolean pinTool;

    public void setPinHandler(PinHandler handler) {
        this.pinHandler = handler;
    }

    /** Ferramenta "Pin": clicar no mapa cria um pin ali. */
    public void setPinTool(boolean on) {
        this.pinTool = on;
        setCursor(on ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public void redrawPins() {
        pinLayer.getChildren().clear();
        boolean gm = tokenActions == null || tokenActions.isGm();
        for (com.vttcaseiro.model.MapPin pin : scene.getPins()) {
            if (pin.isHidden() && !gm) continue;
            pinLayer.getChildren().add(pinNode(pin, gm));
        }
        updatePinScale();
    }

    /** Pins ficam do mesmo tamanho na tela com qualquer zoom (senao somem no mapa-mundo). */
    private void updatePinScale() {
        double s = 1.0 / zoom;
        for (javafx.scene.Node n : pinLayer.getChildren()) {
            n.setScaleX(s);
            n.setScaleY(s);
        }
    }

    private javafx.scene.Node pinNode(com.vttcaseiro.model.MapPin pin, boolean gm) {
        Color color = Color.web(Icons.pinColor(pin.getKind()));
        javafx.scene.shape.Circle disc = new javafx.scene.shape.Circle(0, 0, 16, color);
        disc.setStroke(Color.web("#1B1411"));
        disc.setStrokeWidth(2.5);
        disc.setEffect(new javafx.scene.effect.DropShadow(8, Color.rgb(0, 0, 0, 0.6)));
        javafx.scene.shape.SVGPath glyph = Icons.pinPath(pin.getKind());
        glyph.setFill(Color.web("#FFF8EC"));
        glyph.setScaleX(0.75);
        glyph.setScaleY(0.75);
        glyph.setLayoutX(-12);
        glyph.setLayoutY(-12);
        Group marker = new Group(disc, glyph);
        if (pin.getLinkedScene() != null) {
            // selinho: este pin abre outro mapa
            javafx.scene.shape.Circle badge = new javafx.scene.shape.Circle(12, -12, 7, Color.web("#1B1411"));
            badge.setStroke(Color.web("#FFCF7F"));
            badge.setStrokeWidth(1.5);
            javafx.scene.shape.SVGPath arrow = new javafx.scene.shape.SVGPath();
            arrow.setContent("M9 -15 h6 v6 M15 -15 l-6 6");
            arrow.setStroke(Color.web("#FFCF7F"));
            arrow.setStrokeWidth(1.6);
            arrow.setFill(null);
            marker.getChildren().addAll(badge, arrow);
        }
        if (pin.isHidden()) {
            disc.getStrokeDashArray().addAll(4.0, 3.0);
            disc.setStroke(Color.web("#F6E9D3"));
            marker.setOpacity(0.6);
        }
        Label label = new Label(pin.getTitle().isEmpty() ? "(sem nome)" : pin.getTitle());
        label.setStyle("-fx-background-color: #1B1411D9; -fx-background-radius: 6; -fx-text-fill: #F6E9D3;"
                + " -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 2 7 2 7;");
        label.setMouseTransparent(true);
        label.layoutBoundsProperty().addListener((o, a, b) -> {
            label.setLayoutX(-b.getWidth() / 2);
            label.setLayoutY(20);
        });
        Group node = new Group(marker, label);
        node.setLayoutX(pin.getX());
        node.setLayoutY(pin.getY());
        node.setCursor(Cursor.HAND);
        String tip = pin.getTitle() + (pin.getLinkedScene() != null ? "\nAbre: " + pin.getLinkedScene() : "")
                + (pin.isHidden() ? "\n(oculto: só você vê)" : "");
        javafx.scene.control.Tooltip.install(marker, new javafx.scene.control.Tooltip(tip));

        double[] press = new double[4];
        boolean[] dragging = {false};
        node.setOnMousePressed(e -> {
            if (!noToolActive() && !pinTool) return; // deixa a ferramenta ativa trabalhar
            if (e.getButton() == MouseButton.PRIMARY) {
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                press[0] = p.getX();
                press[1] = p.getY();
                press[2] = pin.getX();
                press[3] = pin.getY();
                dragging[0] = false;
                e.consume();
            } else if (e.getButton() == MouseButton.SECONDARY && gm && pinHandler != null) {
                javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
                javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Abrir cartão");
                open.setOnAction(a -> pinHandler.open(pin));
                javafx.scene.control.MenuItem ed = new javafx.scene.control.MenuItem("Editar pin...");
                ed.setOnAction(a -> pinHandler.edit(pin));
                javafx.scene.control.MenuItem rm = new javafx.scene.control.MenuItem("Apagar pin");
                rm.setOnAction(a -> pinHandler.remove(pin));
                menu.getItems().addAll(open, ed, rm);
                menu.show(node, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
        node.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || !gm || (!noToolActive() && !pinTool)) return;
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - press[0], dy = p.getY() - press[1];
            if (!dragging[0] && Math.hypot(dx, dy) * zoom < 5) return;
            dragging[0] = true;
            node.setLayoutX(press[2] + dx);
            node.setLayoutY(press[3] + dy);
            e.consume();
        });
        node.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY || (!noToolActive() && !pinTool) || pinHandler == null) return;
            if (dragging[0]) {
                pinHandler.moved(pin, node.getLayoutX(), node.getLayoutY());
            } else if (pinTool && gm) {
                pinHandler.edit(pin);
            } else {
                pinHandler.open(pin);
            }
            dragging[0] = false;
            e.consume();
        });
        return node;
    }

    /** Define quem e avisado quando uma ficha e arrastada pro mapa (ver
     * CharacterSheetView, que e quem inicia o arrasto). */
    public void setCharacterDropHandler(CharacterDropHandler handler) {
        this.characterDropHandler = handler;
    }

    /** Liga/desliga o modo "medir" -- enquanto ligado, clicar-e-arrastar
     * com o botao ESQUERDO numa area vazia do mapa desenha a regua (ver
     * onMapPressed/onMapDragged). Desligar esconde a regua se estivesse
     * visivel (nao faz sentido deixar uma medicao antiga na tela depois
     * de sair do modo). */
    public void setMeasuring(boolean measuring) {
        this.measuring = measuring;
        if (!measuring) {
            measureLine.setVisible(false);
            measureLabel.setVisible(false);
        }
    }

    /** Liga/desliga o modo "template de área" (ver campo templating acima).
     * shapeType e um dos textos do ComboBox da barra flutuante ("Circulo",
     * "Cone", "Linha" ou "Cubo") -- quem decide o que cada um significa e
     * {@link #finishTemplate}. Desligar NAO apaga o template desenhado
     * (diferente do measuring) -- a ideia e deixar a area visivel enquanto
     * o jogador resolve a acao; use {@link #clearTemplate()} pra isso. */
    public void setTemplating(boolean templating, String shapeType) {
        this.templating = templating;
        this.templateShapeType = shapeType;
    }

    public void setTemplateShapeType(String shapeType) {
        this.templateShapeType = shapeType;
    }

    /** Apaga o template desenhado agora (se tiver algum). */
    public void clearTemplate() {
        templateGroup.getChildren().clear();
        lastTemplate = null;
    }

    /** Ultima area desenhada: forma + origem e ponta do arrasto (null = nenhuma). */
    private String lastTemplateShape;
    private double[] lastTemplate;

    /**
     * Onde por uma imagem que cobre a area desenhada: {x, y, largura, altura, giro em graus} no formato do objeto do
     * mapa (canto de cima a esquerda antes de girar; giro em volta do centro). Vazio sem area desenhada.
     */
    public java.util.Optional<double[]> effectPlacement() {
        if (lastTemplate == null) return java.util.Optional.empty();
        return java.util.Optional.of(effectBox(lastTemplateShape, lastTemplate[0], lastTemplate[1], lastTemplate[2],
                lastTemplate[3], scene.getGrid().getCellSize()));
    }

    public String lastTemplateShape() {
        return lastTemplate == null ? null : lastTemplateShape;
    }

    /**
     * Caixa da imagem do efeito pra cada forma (mesma geometria do drawTemplate):
     * <ul>
     * <li>Circulo: quadrado em volta do circulo (centro na origem).</li>
     * <li>Cubo: o proprio quadrado.</li>
     * <li>Cone: quadrado do comprimento, centrado no meio do cone e girado pra direcao dele -- a imagem deve
     *     apontar pra DIREITA (a base do cone na borda esquerda).</li>
     * <li>Linha: comprimento x 1 casa, girada pra direcao (imagem deitada, da esquerda pra direita).</li>
     * </ul>
     */
    public static double[] effectBox(String shape, double x1, double y1, double x2, double y2, double cellSize) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.max(1, Math.hypot(dx, dy));
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double cx = x1 + dx / 2, cy = y1 + dy / 2;
        return switch (shape == null ? "" : shape) {
            case "Cubo" -> {
                double side = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
                yield new double[]{dx >= 0 ? x1 : x1 - side, dy >= 0 ? y1 : y1 - side, side, side, 0};
            }
            case "Cone" -> new double[]{cx - length / 2, cy - length / 2, length, length, angle};
            case "Linha" -> {
                double w = Math.max(4, cellSize);
                yield new double[]{cx - length / 2, cy - w / 2, length, w, angle};
            }
            default -> new double[]{x1 - length, y1 - length, 2 * length, 2 * length, 0};
        };
    }

    private void onDragOver(DragEvent event) {
        if (event.getGestureSource() != this && event.getDragboard().hasString()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasString() && characterDropHandler != null) {
            String characterId = db.getString();
            // mesma tecnica do arrasto de token (Parent.sceneToLocal): converte
            // a posicao da SCENE pro espaco de "content", que tem o zoom/pan
            // aplicado -- sem isso o token apareceria no lugar errado com zoom != 1.
            Point2D local = content.sceneToLocal(event.getSceneX(), event.getSceneY());
            characterDropHandler.handle(characterId, local.getX(), local.getY());
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    public SceneModel getSceneModel() {
        return scene;
    }

    /** Recarrega a imagem de fundo. Prefere scene.getBackgroundImageData()
     * (bytes embutidos, ver com.vttcaseiro.net.ImageCodec) a
     * scene.getBackgroundImagePath() -- e o que faz o mapa aparecer de
     * verdade pra quem recebeu a cena pela rede, nao so pra quem criou o
     * arquivo. Chame depois de trocar a cena no modelo. */
    public void loadBackgroundImage() {
        Image img = null;
        // 1) asset por hash (formato atual), 2) base64 embutido (antigo), 3) caminho local.
        if (imageAssetService != null && scene.getBackgroundImageAssetId() != null) {
            try {
                Image fromAsset = new Image(new java.io.ByteArrayInputStream(
                        imageAssetService.readBytes(scene.getBackgroundImageAssetId())));
                if (!fromAsset.isError()) img = fromAsset;
            } catch (java.io.IOException ignored) {
                // asset ainda nao chegou pela rede -- tenta os outros jeitos
            }
        }
        if (img == null) img = ImageCodec.decode(scene.getBackgroundImageData());
        if (img == null) {
            String path = scene.getBackgroundImagePath();
            if (path != null && !path.isBlank()) {
                try {
                    Image candidate = new Image(new File(path).toURI().toString());
                    if (!candidate.isError()) {
                        img = candidate;
                    }
                } catch (Exception ex) {
                    // sem imagem valida -- fica so o fundo cinza, sem travar o programa
                }
            }
        }
        // Sem imagem: um "tabuleiro em branco" com grade, pra ja dar pra jogar sem mapa.
        backgroundView.setImage(img);
        applyMapSize();
        redrawGrid();
        updateEmptyStateVisibility();
    }

    /** Redesenha as linhas da grade (chame depois de mudar tamanho/offset). */
    /** Grade de hexagonos "em pe" cobrindo a area do mapa. */
    private void drawHexGrid(GraphicsContext gc, double w, double h) {
        com.vttcaseiro.model.GridSettings g = scene.getGrid();
        double s = g.hexRadius();
        int rows = (int) Math.ceil(h / (1.5 * s)) + 2;
        int cols = (int) Math.ceil(w / g.getCellSize()) + 2;
        double[] xs = new double[6];
        double[] ys = new double[6];
        for (int r = -1; r < rows; r++) {
            int qStart = -(r / 2) - 2;
            for (int q = qStart; q < qStart + cols + 2; q++) {
                double[] c = g.hexCenter(q, r);
                if (c[0] < -g.getCellSize() || c[0] > w + g.getCellSize()) continue;
                for (int i = 0; i < 6; i++) {
                    double ang = Math.toRadians(60 * i - 30);
                    xs[i] = c[0] + s * Math.cos(ang);
                    ys[i] = c[1] + s * Math.sin(ang);
                }
                gc.strokePolygon(xs, ys, 6);
            }
        }
    }

    /**
     * Tamanho do mapa: o que o mestre escolheu em casas (a imagem estica pra caber), ou o tamanho da imagem, ou o
     * tabuleiro em branco de 30 x 20 casas.
     */
    private void applyMapSize() {
        com.vttcaseiro.model.GridSettings g = scene.getGrid();
        double cell = Math.max(10, g.getCellSize());
        Image img = backgroundView.getImage();
        double w;
        double h;
        if (g.hasCustomMapSize()) {
            w = g.getMapWidthCells() * cell;
            h = g.getMapHeightCells() * cell;
        } else if (img != null) {
            w = img.getWidth();
            h = img.getHeight();
        } else {
            w = cell * BLANK_COLS;
            h = cell * BLANK_ROWS;
        }
        backgroundView.setPreserveRatio(false);
        backgroundView.setFitWidth(img != null && g.hasCustomMapSize() ? w : 0);
        backgroundView.setFitHeight(img != null && g.hasCustomMapSize() ? h : 0);
        gridCanvas.setWidth(w);
        gridCanvas.setHeight(h);
    }

    /** Largura/altura do mapa em pixels (o tamanho escolhido ou o da imagem). */
    public double mapWidth() {
        return gridCanvas.getWidth();
    }

    public double mapHeight() {
        return gridCanvas.getHeight();
    }

    public void redrawGrid() {
        applyMapSize();
        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
        if (backgroundView.getImage() == null) {
            gc.setFill(Color.web("#261D18"));
            gc.fillRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
        }
        if (!scene.getGrid().isVisible()) {
            return;
        }
        double size = scene.getGrid().getCellSize();
        if (size <= 1) {
            return;
        }
        double w = gridCanvas.getWidth();
        double h = gridCanvas.getHeight();
        gc.setStroke(Color.rgb(255, 255, 255, 0.35));
        gc.setLineWidth(1);
        if (scene.getGrid().isGridless()) return;
        if (scene.getGrid().isHex()) {
            drawHexGrid(gc, w, h);
            return;
        }
        for (double x = scene.getGrid().getOffsetX() % size; x < w; x += size) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = scene.getGrid().getOffsetY() % size; y < h; y += size) {
            gc.strokeLine(0, y, w, y);
        }
    }

    /** Define quem e avisado quando um token termina de ser arrastado
     * (a Fase 2 usa isso pra mandar o movimento pela rede). Chame antes
     * de rebuildTokens() surtir efeito nos tokens ja existentes -- ou
     * chame rebuildTokens() de novo depois de setar. */
    public void setTokenMoveListener(java.util.function.Consumer<TokenModel> listener) {
        this.tokenMoveListener = listener;
    }

    private java.util.function.BiConsumer<TokenModel, double[]> tokenMovedFromListener;

    /** Token movido + posicao antiga (o Main usa pro desfazer). */
    public void setTokenMovedFromListener(java.util.function.BiConsumer<TokenModel, double[]> listener) {
        this.tokenMovedFromListener = listener;
    }

    /**
     * O que o mapa pede pro resto do programa quando o jogador mexe num
     * token (menu do botao direito, clique duplo). Sem isso o MapView
     * mudaria o modelo direto e nada chegaria na ficha ligada nem na rede
     * -- era exatamente o bug do "editar PV" antigo.
     */
    public interface TokenActions {
        /** Token ligado a ficha: abrir a janela dela. */
        void openSheet(TokenModel token);

        void damage(TokenModel token, int amount);

        void heal(TokenModel token, int amount);

        void setHp(TokenModel token, Integer current, Integer max, Integer temp);

        void setConditions(TokenModel token, java.util.List<String> conditions);

        void setEffects(TokenModel token, java.util.List<String> effects);

        void rollInitiative(TokenModel token);

        void remove(TokenModel token);

        /** Pode mover/editar este token (dono ou mestre)? */
        boolean canControl(TokenModel token);

        /** Pode ver os NUMEROS de PV deste token? (monstro: so o mestre) */
        boolean canSeeHp(TokenModel token);

        default boolean isGm() {
            return true;
        }

        /** Efeito que some sozinho depois de N rodadas de combate. */
        default void addTimedEffect(TokenModel token, String name, int rounds) {
        }

        /** Troca a imagem do token (arquivo ou galeria). Token de personagem troca o retrato da ficha junto. */
        default void changeImage(TokenModel token) {
        }

        /** So mestre: escolher qual jogador controla o token (e a ficha). */
        default void giveControl(TokenModel token) {
        }

        /** So mestre: camada do mestre (esconder/revelar pros jogadores). */
        default void setHidden(TokenModel token, boolean hidden) {
        }

        /** Lado do token em casas (Grande = 2, Enorme = 3, Imenso = 4). */
        /** Gira o token (graus; 0 = volta pro normal). */
        default void rotate(TokenModel token, double degrees, boolean reset) {
        }

        default void setSize(TokenModel token, int cells) {
        }

        /** Visao no escuro e luz do token, em pes. */
        /** Muda campos do token (altura, direcao, sentidos...) com desfazer e envio pela rede. */
        default void editToken(TokenModel token, String label, java.util.function.Consumer<TokenModel> change) {
            change.accept(token);
        }

        default void setVision(TokenModel token, int visionFeet, int lightFeet) {
        }

        /** Aviso curto pro usuario (ex.: parede no caminho). */
        default void notice(String text) {
        }
    }

    private TokenActions tokenActions;
    private TokenModel selectedToken;
    /** Alvos marcados NESTA tela (cada jogador mira os seus). */
    private final java.util.LinkedHashSet<String> targetIds = new java.util.LinkedHashSet<>();
    private Runnable targetListener = () -> { };

    public void setTargetListener(Runnable listener) {
        this.targetListener = listener == null ? () -> { } : listener;
    }

    /** Tokens mirados que ainda estao na cena, na ordem em que foram marcados. */
    public java.util.List<TokenModel> getTargets() {
        java.util.List<TokenModel> out = new java.util.ArrayList<>();
        for (String id : targetIds) scene.findToken(id).ifPresent(out::add);
        return out;
    }

    public boolean isTargeted(TokenModel token) {
        return token != null && targetIds.contains(token.getId());
    }

    public void toggleTarget(TokenModel token) {
        if (token == null) return;
        if (!targetIds.remove(token.getId())) targetIds.add(token.getId());
        refreshTargetMarkers();
    }

    public void clearTargets() {
        if (targetIds.isEmpty()) return;
        targetIds.clear();
        refreshTargetMarkers();
    }

    private void refreshTargetMarkers() {
        targetIds.removeIf(id -> scene.findToken(id).isEmpty());
        for (javafx.scene.Node n : tokenLayer.getChildren()) {
            if (n instanceof TokenNode tn) tn.setTargeted(targetIds.contains(tn.getModel().getId()));
        }
        targetListener.run();
    }

    public void setTokenActions(TokenActions actions) {
        this.tokenActions = actions;
        redrawWalls();
        rebuildTokens();
    }

    /** Ultimo token clicado (pra "aplicar dano no token selecionado" do chat). */
    public TokenModel getSelectedToken() {
        if (selectedToken != null && !scene.getTokens().contains(selectedToken)) selectedToken = null;
        return selectedToken;
    }

    /** Reconstroi a camada de tokens do zero a partir de scene.getTokens(). */
    public void rebuildTokens() {
        tokenLayer.getChildren().clear();
        updateEmptyStateVisibility();
        redrawFog();
        for (TokenModel token : scene.getTokens()) {
            boolean canControl = tokenActions == null || tokenActions.canControl(token);
            boolean canSeeHp = tokenActions == null || tokenActions.canSeeHp(token);
            TokenNode node = new TokenNode(token, scene.getGrid(), imageAssetService, canSeeHp);
            node.setDraggable(canControl);
            node.setSelected(token == selectedToken || selectedIds.contains(token.getId()));
            node.setTargeted(targetIds.contains(token.getId()));
            node.setMoveGuard(this::allowMove);
            node.setDragProgress(this::onTokenDragProgress);
            node.setOnMoved(tokenMoveListener);
            node.setOnMovedFrom((moved, old) -> {
                if (tokenMovedFromListener != null) tokenMovedFromListener.accept(moved, old);
                moveRestOfSelection(moved, old); // arrastar um da selecao leva o grupo junto
            });
            node.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress() && e.isControlDown()) {
                    toggleTarget(token); // Ctrl+clique = mirar / tirar a mira
                    return;
                }
                if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                    selectedToken = token;
                    if (e.isShiftDown()) {
                        // Shift+clique: poe/tira da selecao (varios tokens).
                        if (!selectedIds.remove(token.getId())) selectedIds.add(token.getId());
                    } else {
                        selectedIds.clear();
                        selectedIds.add(token.getId());
                    }
                    refreshSelection();
                    if (e.getClickCount() == 2 && tokenActions != null) {
                        if (token.getCharacterSheetId() != null || token.getMobSheetId() != null) {
                            tokenActions.openSheet(token);
                        } else if (canControl) {
                            askAmount(token, true);
                        }
                    }
                }
            });
            node.setOnContextMenuRequested(e -> {
                javafx.scene.control.ContextMenu menu = buildTokenMenu(token, canControl);
                java.util.List<TokenModel> group = getSelectedTokens();
                if (group.size() > 1 && group.contains(token)) menu.getItems().add(0, groupMenu(group));
                menu.show(node, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            tokenLayer.getChildren().add(node);
        }
        // Token que saiu do mapa sai dos alvos tambem.
        if (targetIds.removeIf(id -> scene.findToken(id).isEmpty())) targetListener.run();
        updateVision();
    }

    private javafx.scene.control.ContextMenu buildTokenMenu(TokenModel token, boolean canControl) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        if (tokenActions == null) return menu;
        if (token.getCharacterSheetId() != null || (token.getMobSheetId() != null && tokenActions.isGm())) {
            menu.getItems().add(menuItem("Abrir ficha", () -> tokenActions.openSheet(token)));
        }
        menu.getItems().add(menuItem(isTargeted(token) ? "Tirar a mira" : "Mirar (alvo do ataque)  Ctrl+clique",
                () -> toggleTarget(token)));
        if (canControl) {
            menu.getItems().addAll(
                    menuItem("Dano...", () -> askAmount(token, true)),
                    menuItem("Cura...", () -> askAmount(token, false)),
                    menuItem("Definir PV...", () -> askHp(token)),
                    new javafx.scene.control.SeparatorMenuItem(),
                    menuItem("Condições...", () -> new ConditionsPickerView(token,
                            selected -> tokenActions.setConditions(token, selected)).show()),
                    menuItem("Efeitos (Abençoado, Fúria...)...", () -> askEffects(token)),
                    menuItem("Efeito com duração (rodadas)...", () -> askTimedEffect(token)),
                    menuItem("Rolar iniciativa", () -> tokenActions.rollInitiative(token)),
                    sizeMenu(token),
                    rotateMenu(token),
                    menuItem("Visão e luz...", () -> askVision(token)),
                    menuItem("Altura...", () -> askElevation(token)),
                    facingMenu(token),
                    menuItem("Trocar imagem...", () -> tokenActions.changeImage(token)),
                    new javafx.scene.control.SeparatorMenuItem(),
                    menuItem("Remover do mapa", () -> tokenActions.remove(token)));
            if (tokenActions.isGm()) {
                menu.getItems().add(menuItem("Dar controle a...", () -> tokenActions.giveControl(token)));
                menu.getItems().add(token.isHiddenFromPlayers()
                        ? menuItem("Revelar aos jogadores", () -> tokenActions.setHidden(token, false))
                        : menuItem("Esconder dos jogadores (camada do mestre)", () -> tokenActions.setHidden(token, true)));
            }
        } else {
            javafx.scene.control.MenuItem info = new javafx.scene.control.MenuItem("(token de outro jogador)");
            info.setDisable(true);
            menu.getItems().add(info);
        }
        return menu;
    }

    /** Visao no escuro + luz que o token carrega (com atalhos de tocha/lanterna). */
    /** Fontes de luz do Livro do Jogador 2024: {nome, luz plena, penumbra alem dela}. */
    static final Object[][] LIGHT_PRESETS = {
            {"Sem luz", 0, 0}, {"Vela", 5, 5}, {"Tocha", 20, 20}, {"Lâmpada", 15, 30}, {"Lanterna coberta", 30, 30},
            {"Lanterna de foco (cone)", 60, 60}, {"Luz (truque)", 20, 20}, {"Chama Contínua", 20, 20},
            {"Luz do Dia (magia)", 60, 60}};

    /**
     * Visao e luz no modelo do 5.5e: visao no escuro, as cegas, sentido sismico, verdadeira; luz plena e penumbra.
     */
    private void askVision(TokenModel token) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Visão e luz: " + token.getName());
        dialog.setHeaderText("Regras do 5.5e. Valem com a visão dinâmica ligada (cena clara, em penumbra ou escura).");
        java.util.function.IntFunction<javafx.scene.control.Spinner<Integer>> spin = v -> {
            javafx.scene.control.Spinner<Integer> s = new javafx.scene.control.Spinner<>(0, 1000, v, 5);
            s.setEditable(true);
            s.setPrefWidth(90);
            return s;
        };
        var darkvision = spin.apply(token.getVisionFeet());
        var blindsight = spin.apply(token.getBlindsightFeet());
        var tremorsense = spin.apply(token.getTremorsenseFeet());
        var truesight = spin.apply(token.getTruesightFeet());
        var bright = spin.apply(token.getLightFeet());
        var dim = spin.apply(token.getDimLightFeet());
        javafx.scene.control.ComboBox<String> preset = new javafx.scene.control.ComboBox<>();
        preset.getItems().add("(fonte de luz)");
        for (Object[] p : LIGHT_PRESETS) preset.getItems().add(p[0] + ((int) p[1] > 0 ? " (" + p[1] + "/" + p[2] + ")" : ""));
        preset.setValue("(fonte de luz)");
        preset.setOnAction(e -> {
            int i = preset.getSelectionModel().getSelectedIndex() - 1;
            if (i < 0) return;
            bright.getValueFactory().setValue((Integer) LIGHT_PRESETS[i][1]);
            dim.getValueFactory().setValue((Integer) LIGHT_PRESETS[i][2]);
        });
        javafx.scene.control.Button dv60 = new javafx.scene.control.Button("60");
        dv60.setOnAction(e -> darkvision.getValueFactory().setValue(60));
        javafx.scene.control.Button dv120 = new javafx.scene.control.Button("120");
        dv120.setOnAction(e -> darkvision.getValueFactory().setValue(120));
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int row = 0;
        grid.addRow(row++, new Label("Visão no escuro (pés)"), darkvision, new javafx.scene.layout.HBox(4, dv60, dv120));
        grid.addRow(row++, new Label("Visão às cegas (pés)"), blindsight);
        grid.addRow(row++, new Label("Sentido sísmico (pés)"), tremorsense);
        grid.addRow(row++, new Label("Visão verdadeira (pés)"), truesight);
        grid.addRow(row++, new Label("Luz plena que carrega (pés)"), bright, preset);
        grid.addRow(row++, new Label("Penumbra além dela (pés)"), dim);
        Label help = new Label("Visão no escuro: na penumbra vê como luz plena; na escuridão, como penumbra. "
                + "Às cegas e verdadeira: vê no alcance mesmo no escuro (parede bloqueia). "
                + "Sísmico: sente quem está no chão (altura 0), até atrás de parede.");
        help.setWrapText(true);
        help.setMaxWidth(430);
        help.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8;");
        grid.add(help, 0, row, 3, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK)
                .ifPresent(b -> tokenActions.editToken(token, "Visão de " + token.getName(), t -> {
                    t.setVisionFeet(darkvision.getValue());
                    t.setBlindsightFeet(blindsight.getValue());
                    t.setTremorsenseFeet(tremorsense.getValue());
                    t.setTruesightFeet(truesight.getValue());
                    t.setLightFeet(bright.getValue());
                    t.setDimLightFeet(dim.getValue());
                }));
    }

    /** Altura em pes (voando, escalando; negativo = abaixo do chao). Passos de 5. */
    private void askElevation(TokenModel token) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Altura: " + token.getName());
        dialog.setHeaderText("Em pés. 0 = no chão; negativo = abaixo (cavando).");
        javafx.scene.control.Spinner<Integer> feet = new javafx.scene.control.Spinner<>(-1000, 10000, token.getElevationFeet(), 5);
        feet.setEditable(true);
        dialog.getDialogPane().setContent(new javafx.scene.layout.HBox(8, new Label("Altura (pés)"), feet));
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK)
                .ifPresent(b -> tokenActions.editToken(token, "Altura de " + token.getName(),
                        t -> t.setElevationFeet(feet.getValue())));
    }

    /** Submenu "Olhando para": 8 direcoes ou nenhuma (a setinha na borda do token). */
    private javafx.scene.control.Menu facingMenu(TokenModel token) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Olhando para");
        String[][] dirs = {{"Norte \u2191", "270"}, {"Nordeste \u2197", "315"}, {"Leste \u2192", "0"}, {"Sudeste \u2198", "45"},
                {"Sul \u2193", "90"}, {"Sudoeste \u2199", "135"}, {"Oeste \u2190", "180"}, {"Noroeste \u2196", "225"}};
        for (String[] d : dirs) {
            menu.getItems().add(menuItem(d[0], () -> tokenActions.editToken(token, "Direção de " + token.getName(),
                    t -> t.setFacingDegrees(Double.parseDouble(d[1])))));
        }
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        menu.getItems().add(menuItem("Sem direção", () -> tokenActions.editToken(token, "Direção de " + token.getName(),
                t -> t.setFacingDegrees(null))));
        return menu;
    }

    /** Submenu "Girar" (tambem: tecla R / Shift+R com o token selecionado). */
    private javafx.scene.control.Menu rotateMenu(TokenModel token) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Girar");
        menu.getItems().addAll(
                menuItem("45\u00b0 horário  (R)", () -> tokenActions.rotate(token, 45, false)),
                menuItem("45\u00b0 anti-horário  (Shift+R)", () -> tokenActions.rotate(token, -45, false)),
                menuItem("90\u00b0 horário", () -> tokenActions.rotate(token, 90, false)),
                menuItem("Virar 180\u00b0", () -> tokenActions.rotate(token, 180, false)),
                menuItem("Voltar ao normal", () -> tokenActions.rotate(token, 0, true)));
        return menu;
    }

    // ------------------------------------------------------------------
    // Regua de movimento: arrastando um token mostra a distancia e o deslocamento dele
    // ------------------------------------------------------------------

    private java.util.function.ToIntFunction<TokenModel> speedProvider;
    private Line moveRulerLine;
    private Label moveRulerLabel;

    /** Deslocamento do token em pes (0 = nao sabe). */
    public void setSpeedProvider(java.util.function.ToIntFunction<TokenModel> provider) {
        this.speedProvider = provider;
    }

    /** Distancia em casas (regra de 5e: diagonal conta 1) entre dois cantos de token. */
    /** Distancia em casas entre dois cantos de token, conforme a grade da cena. */
    private long cellsBetween(double x1, double y1, double x2, double y2) {
        return scene.getGrid().cellsBetween(x1, y1, x2, y2);
    }

    public static long cellsBetween(double x1, double y1, double x2, double y2, double cellSize) {
        if (cellSize <= 0) return 0;
        return Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / cellSize);
    }

    private void onTokenDragProgress(TokenModel token, double[] p) {
        if (p == null) {
            content.getChildren().removeAll(moveRulerLine, moveRulerLabel);
            moveRulerLine = null;
            moveRulerLabel = null;
            return;
        }
        double cell = scene.getGrid().getCellSize();
        double half = cell * token.getWidthCells() / 2;
        if (moveRulerLine == null) {
            moveRulerLine = new Line();
            moveRulerLine.setStrokeWidth(3);
            moveRulerLine.getStrokeDashArray().addAll(10.0, 6.0);
            moveRulerLine.setMouseTransparent(true);
            moveRulerLabel = new Label();
            moveRulerLabel.setMouseTransparent(true);
            content.getChildren().addAll(moveRulerLine, moveRulerLabel);
        }
        moveRulerLine.setStartX(p[0] + half);
        moveRulerLine.setStartY(p[1] + half);
        moveRulerLine.setEndX(p[2] + half);
        moveRulerLine.setEndY(p[3] + half);
        long cells = cellsBetween(p[0] + half, p[1] + half, p[2] + half, p[3] + half);
        long feet = Math.round(cells * scene.getGrid().getFeetPerCell());
        int speed = speedProvider == null ? 0 : speedProvider.applyAsInt(token);
        boolean over = speed > 0 && feet > speed;
        boolean dash = speed > 0 && feet > speed && feet <= speed * 2L;
        String color = !over ? "#3FBE6E" : dash ? "#E8A23E" : "#D9544C";
        moveRulerLine.setStroke(Color.web(color));
        moveRulerLabel.setText(Distances.feetAndMeters(feet) + " · " + cells + " casa" + (cells == 1 ? "" : "s")
                + (speed > 0 ? "  /  " + Distances.feetAndMeters(speed) + (over ? (dash ? "  - precisa de Disparada" : "  - longe demais") : "") : ""));
        moveRulerLabel.setStyle("-fx-background-color: #1B1411E6; -fx-background-radius: 6; -fx-padding: 2 8 2 8;"
                + " -fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + color + ";");
        moveRulerLabel.setScaleX(1 / zoom);
        moveRulerLabel.setScaleY(1 / zoom);
        moveRulerLabel.setLayoutX(p[2] + half * 2 + 6);
        moveRulerLabel.setLayoutY(p[3] - 4);
    }

    /** Submenu "Tamanho": o atual vem marcado. */
    private javafx.scene.control.Menu sizeMenu(TokenModel token) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Tamanho");
        String[][] options = {{"1", "Pequeno / Medio (1x1)"}, {"2", "Grande (2x2)"}, {"3", "Enorme (3x3)"},
                {"4", "Imenso (4x4)"}};
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        for (String[] o : options) {
            int cells = Integer.parseInt(o[0]);
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(o[1]);
            item.setToggleGroup(group);
            item.setSelected(token.getWidthCells() == cells);
            item.setOnAction(e -> tokenActions.setSize(token, cells));
            menu.getItems().add(item);
        }
        return menu;
    }

    private static javafx.scene.control.MenuItem menuItem(String text, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** Dano ou cura: aceita numero ("7") ou expressao de dados ("2d6+3"). */
    private void askAmount(TokenModel token, boolean damage) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle((damage ? "Dano em " : "Cura em ") + token.getName());
        dialog.setHeaderText(null);
        dialog.setContentText((damage ? "Dano" : "Cura") + " (número ou dados, ex.: 2d6+3):");
        dialog.showAndWait().ifPresent(text -> {
            Integer amount = parseAmount(text);
            if (amount == null || amount <= 0) return;
            if (damage) tokenActions.damage(token, amount); else tokenActions.heal(token, amount);
        });
    }

    static Integer parseAmount(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ignored) {
            // pode ser uma expressao de dados
        }
        try {
            return com.vttcaseiro.dice.DiceRoller.roll(t).total();
        } catch (com.vttcaseiro.dice.DiceException ex) {
            return null;
        }
    }

    private void askHp(TokenModel token) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("PV de " + token.getName());
        javafx.scene.control.TextField current = new javafx.scene.control.TextField(
                token.getCurrentHp() == null ? "" : String.valueOf(token.getCurrentHp()));
        javafx.scene.control.TextField max = new javafx.scene.control.TextField(
                token.getMaxHp() == null ? "" : String.valueOf(token.getMaxHp()));
        javafx.scene.control.TextField temp = new javafx.scene.control.TextField(
                token.getTempHp() == null ? "" : String.valueOf(token.getTempHp()));
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Atual"), current);
        grid.addRow(1, new Label("Máximo"), max);
        grid.addRow(2, new Label("Temporário"), temp);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b ->
                tokenActions.setHp(token, parseOrNull(current.getText()), parseOrNull(max.getText()),
                        parseOrNull(temp.getText())));
    }

    private static Integer parseOrNull(String text) {
        try {
            return text == null || text.isBlank() ? null : Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void askTimedEffect(TokenModel token) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Efeito com duração em " + token.getName());
        javafx.scene.control.TextField name = new javafx.scene.control.TextField();
        name.setPromptText("Bênção, Fúria, Escudo da Fé...");
        javafx.scene.control.Spinner<Integer> rounds = new javafx.scene.control.Spinner<>(1, 600, 10);
        rounds.setEditable(true);
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Efeito"), name);
        grid.addRow(1, new Label("Rodadas (1 min = 10)"), rounds);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (!name.getText().isBlank()) tokenActions.addTimedEffect(token, name.getText().trim(), rounds.getValue());
        });
    }

    private void askEffects(TokenModel token) {
        TextInputDialog dialog = new TextInputDialog(String.join(", ", token.getEffects()));
        dialog.setTitle("Efeitos de " + token.getName());
        dialog.setHeaderText("Efeitos livres separados por vírgula (ex.: Abençoado, Fúria, Escudo da Fé).");
        dialog.setContentText("Efeitos:");
        dialog.showAndWait().ifPresent(text -> {
            // Virgula dentro de [ ] e termo do efeito ("Anel [ca+1, salv+1]").
            java.util.List<String> list = com.vttcaseiro.character.EffectModifiers.splitList(text);
            tokenActions.setEffects(token, list);
        });
    }

    /** Zoom centrado no cursor: sem isso, dar zoom com a roda sempre
     * "empurra" o mapa em volta do centro do conteudo (0,0 do Group),
     * nao de onde o mouse esta -- cada roda de mouse muda o ponto que
     * voce estava olhando. A conta: acha o ponto do mapa que esta sob o
     * cursor ANTES de mudar o zoom (sceneToLocal, no espaco "nativo" do
     * content, que nao muda com zoom/pan), troca o zoom, e ajusta o
     * translateX/Y pra esse MESMO ponto continuar sob o cursor depois.
     * O pivot do scale no JavaFX e sempre o centro do getLayoutBounds()
     * do node (fixo, nao muda com zoom/pan), entao a correcao e so
     * "(zoomAntigo - zoomNovo) * (pontoSobCursor - pivot)". */
    private void onScroll(ScrollEvent e) {
        double oldZoom = zoom;
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == oldZoom) {
            e.consume();
            return;
        }
        Point2D mouseInContent = content.sceneToLocal(e.getSceneX(), e.getSceneY());
        javafx.geometry.Bounds bounds = content.getLayoutBounds();
        double pivotX = bounds.getMinX() + bounds.getWidth() / 2;
        double pivotY = bounds.getMinY() + bounds.getHeight() / 2;
        double oldTranslateX = content.getTranslateX();
        double oldTranslateY = content.getTranslateY();

        zoom = newZoom;
        content.setScaleX(zoom);
        content.setScaleY(zoom);
        updatePinScale();
        content.setTranslateX(oldTranslateX + (oldZoom - zoom) * (mouseInContent.getX() - pivotX));
        content.setTranslateY(oldTranslateY + (oldZoom - zoom) * (mouseInContent.getY() - pivotY));
        e.consume();
    }

    // ------------------------------------------------------------------
    // Selecao de varios tokens
    // ------------------------------------------------------------------

    private final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    private javafx.scene.shape.Rectangle selectionBox;
    private double selStartX;
    private double selStartY;

    public java.util.List<TokenModel> getSelectedTokens() {
        java.util.List<TokenModel> out = new java.util.ArrayList<>();
        for (String id : selectedIds) scene.findToken(id).ifPresent(out::add);
        return out;
    }

    public void selectTokens(java.util.Collection<TokenModel> tokens) {
        selectedIds.clear();
        for (TokenModel t : tokens) selectedIds.add(t.getId());
        selectedToken = tokens.isEmpty() ? null : tokens.iterator().next();
        refreshSelection();
    }

    public void clearSelection() {
        selectedIds.clear();
        selectedToken = null;
        refreshSelection();
    }

    private void refreshSelection() {
        selectedIds.removeIf(id -> scene.findToken(id).isEmpty());
        for (javafx.scene.Node n : tokenLayer.getChildren()) {
            if (n instanceof TokenNode tn) tn.setSelected(selectedIds.contains(tn.getModel().getId()));
        }
    }

    private boolean noToolActive() {
        return wallTool == null && drawTool == null && !measuring && !templating && fogTool == null && !pinTool;
    }

    /** Mesmo deslocamento pros outros selecionados que eu controlo (e sem atravessar parede). */
    private void moveRestOfSelection(TokenModel moved, double[] old) {
        if (!selectedIds.contains(moved.getId()) || selectedIds.size() < 2) return;
        double dx = moved.getX() - old[0];
        double dy = moved.getY() - old[1];
        for (TokenModel o : getSelectedTokens()) {
            if (o == moved) continue;
            moveToken(o, o.getX() + dx, o.getY() + dy);
        }
    }

    private boolean moveToken(TokenModel t, double x, double y) {
        if (tokenActions != null && !tokenActions.canControl(t)) return false;
        if (!allowMove(t, new double[]{x, y})) return false;
        double[] before = {t.getX(), t.getY()};
        t.setX(x);
        t.setY(y);
        if (tokenMovedFromListener != null) tokenMovedFromListener.accept(t, before);
        if (tokenMoveListener != null) tokenMoveListener.accept(t);
        return true;
    }

    /** Setas do teclado: anda a selecao {dc, dr} casas. */
    public int nudgeSelection(int dc, int dr) {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        int moved = 0;
        com.vttcaseiro.model.GridSettings g = scene.getGrid();
        double stepY = g.isHex() ? 1.5 * g.hexRadius() : cell;
        for (TokenModel t : getSelectedTokens()) {
            double[] to = g.isHex()
                    ? g.snapTopLeft(t.getX() + dc * cell + (dr != 0 && dc == 0 ? cell / 2 : 0), t.getY() + dr * stepY, t.getWidthCells())
                    : new double[]{t.getX() + dc * cell, t.getY() + dr * cell};
            if (moveToken(t, to[0], to[1])) moved++;
        }
        if (moved > 0) rebuildTokens();
        return moved;
    }

    /** Submenu "Selecao (N)": a mesma acao em todos de uma vez. */
    private javafx.scene.control.Menu groupMenu(java.util.List<TokenModel> group) {
        javafx.scene.control.Menu m = new javafx.scene.control.Menu("Seleção (" + group.size() + " tokens)");
        m.getItems().add(menuItem("Dano em todos...", () -> askAmountGroup(group, true)));
        m.getItems().add(menuItem("Cura em todos...", () -> askAmountGroup(group, false)));
        m.getItems().add(menuItem("Mirar todos", () -> {
            for (TokenModel t : group) if (!isTargeted(t)) toggleTarget(t);
        }));
        if (tokenActions.isGm()) {
            m.getItems().add(menuItem("Esconder todos dos jogadores", () -> group.forEach(t -> tokenActions.setHidden(t, true))));
            m.getItems().add(menuItem("Revelar todos", () -> group.forEach(t -> tokenActions.setHidden(t, false))));
        }
        m.getItems().add(menuItem("Remover todos do mapa", () -> {
            javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION, "Remover " + group.size() + " tokens do mapa?");
            confirm.setHeaderText(null);
            confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
                for (TokenModel t : group) if (tokenActions.canControl(t)) tokenActions.remove(t);
                clearSelection();
            });
        }));
        return m;
    }

    private void askAmountGroup(java.util.List<TokenModel> group, boolean damage) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle((damage ? "Dano em " : "Cura em ") + group.size() + " tokens");
        dialog.setHeaderText(null);
        dialog.setContentText((damage ? "Dano" : "Cura") + " (número ou dados -- rola uma vez pra todos):");
        dialog.showAndWait().ifPresent(text -> {
            Integer amount = parseAmount(text);
            if (amount == null || amount <= 0) return;
            for (TokenModel t : group) {
                if (!tokenActions.canControl(t)) continue;
                if (damage) tokenActions.damage(t, amount); else tokenActions.heal(t, amount);
            }
        });
    }

    private void onMapPressed(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && noToolActive() && !e.isAltDown()) {
            if (e.isShiftDown()) {
                // Shift+arrastar no mapa vazio: caixa de selecao.
                Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
                selStartX = p.getX();
                selStartY = p.getY();
                selectionBox = new javafx.scene.shape.Rectangle(selStartX, selStartY, 0, 0);
                selectionBox.setFill(Color.web("#FFCF7F", 0.12));
                selectionBox.setStroke(Color.web("#FFCF7F"));
                selectionBox.getStrokeDashArray().addAll(6.0, 4.0);
                selectionBox.setMouseTransparent(true);
                content.getChildren().add(selectionBox);
                e.consume();
                return;
            }
            clearSelection();
        }
        if (pinTool && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            if (pinHandler != null) pinHandler.create(p.getX(), p.getY());
            e.consume();
            return;
        }
        if (wallTool != null && e.getButton() == MouseButton.PRIMARY) {
            startWall(content.sceneToLocal(e.getSceneX(), e.getSceneY()));
            e.consume();
            return;
        }
        if (drawTool != null && e.getButton() == MouseButton.PRIMARY) {
            startDrawing(content.sceneToLocal(e.getSceneX(), e.getSceneY()));
            e.consume();
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            panStartSceneX = e.getSceneX();
            panStartSceneY = e.getSceneY();
            panStartTranslateX = content.getTranslateX();
            panStartTranslateY = content.getTranslateY();
            setCursor(Cursor.CLOSED_HAND);
            e.consume();
        } else if (measuring && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            measureStartX = p.getX();
            measureStartY = p.getY();
            measureLine.setStartX(measureStartX);
            measureLine.setStartY(measureStartY);
            measureLine.setEndX(measureStartX);
            measureLine.setEndY(measureStartY);
            measureLine.setVisible(true);
            measureLabel.setVisible(true);
            updateMeasureLabel(measureStartX, measureStartY, measureStartX, measureStartY);
            e.consume();
        } else if (fogTool != null && e.getButton() == MouseButton.PRIMARY && fogPreview.isVisible()) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            updateFogPreview(p.getX(), p.getY());
            e.consume();
        } else if (templating && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            templateStartX = p.getX();
            templateStartY = p.getY();
            drawTemplate(templateStartX, templateStartY, templateStartX, templateStartY);
            e.consume();
        } else if (fogTool != null && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            fogStartX = p.getX();
            fogStartY = p.getY();
            updateFogPreview(fogStartX, fogStartY);
            fogPreview.setVisible(true);
            e.consume();
        }
    }

    private void updateFogPreview(double x2, double y2) {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        boolean hex = scene.getGrid().isHex();
        // hex: o retangulo e livre (entram os hexagonos com o centro dentro dele)
        double x0 = hex ? Math.min(fogStartX, x2) : Math.floor(Math.min(fogStartX, x2) / cell) * cell;
        double y0 = hex ? Math.min(fogStartY, y2) : Math.floor(Math.min(fogStartY, y2) / cell) * cell;
        double x1 = hex ? Math.max(fogStartX, x2) : (Math.floor(Math.max(fogStartX, x2) / cell) + 1) * cell;
        double y1 = hex ? Math.max(fogStartY, y2) : (Math.floor(Math.max(fogStartY, y2) / cell) + 1) * cell;
        fogPreview.setX(x0);
        fogPreview.setY(y0);
        fogPreview.setWidth(x1 - x0);
        fogPreview.setHeight(y1 - y0);
    }

    private void onMapDragged(MouseEvent e) {
        if (selectionBox != null) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            selectionBox.setX(Math.min(selStartX, p.getX()));
            selectionBox.setY(Math.min(selStartY, p.getY()));
            selectionBox.setWidth(Math.abs(p.getX() - selStartX));
            selectionBox.setHeight(Math.abs(p.getY() - selStartY));
            e.consume();
            return;
        }
        if (wallStart != null && wallPreview != null) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            wallPreview.setEndX(p.getX());
            wallPreview.setEndY(p.getY());
            e.consume();
            return;
        }
        if (drawingPoints != null) {
            continueDrawing(content.sceneToLocal(e.getSceneX(), e.getSceneY()));
            e.consume();
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            content.setTranslateX(panStartTranslateX + (e.getSceneX() - panStartSceneX));
            content.setTranslateY(panStartTranslateY + (e.getSceneY() - panStartSceneY));
            e.consume();
        } else if (measuring && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            measureLine.setEndX(p.getX());
            measureLine.setEndY(p.getY());
            updateMeasureLabel(measureStartX, measureStartY, p.getX(), p.getY());
            e.consume();
        } else if (templating && e.getButton() == MouseButton.PRIMARY) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            drawTemplate(templateStartX, templateStartY, p.getX(), p.getY());
            e.consume();
        }
    }

    private void onMapReleased(MouseEvent e) {
        if (selectionBox != null) {
            javafx.geometry.Bounds box = selectionBox.getBoundsInParent();
            content.getChildren().remove(selectionBox);
            selectionBox = null;
            java.util.List<TokenModel> inside = new java.util.ArrayList<>();
            for (javafx.scene.Node n : tokenLayer.getChildren()) {
                if (!(n instanceof TokenNode tn) || !tn.isVisible()) continue;
                double[] c = centerOf(tn.getModel());
                if (box.contains(c[0], c[1])) inside.add(tn.getModel());
            }
            selectTokens(inside);
            e.consume();
            return;
        }
        if (wallStart != null) {
            finishWall(content.sceneToLocal(e.getSceneX(), e.getSceneY()));
            e.consume();
            return;
        }
        if (drawingPoints != null) {
            finishDrawing();
            e.consume();
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            setCursor(Cursor.DEFAULT);
            e.consume();
        }
        if (fogTool != null && e.getButton() == MouseButton.PRIMARY && fogPreview.isVisible()) {
            Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
            double cell = Math.max(10, scene.getGrid().getCellSize());
            fogPreview.setVisible(false);
            if (scene.getGrid().isHex()) {
                if (fogCellsHandler != null) {
                    fogCellsHandler.accept(scene.getGrid().hexesInRect(fogStartX, fogStartY, p.getX(), p.getY()), fogTool);
                }
            } else if (fogEditHandler != null) {
                fogEditHandler.edit((int) Math.floor(fogStartX / cell), (int) Math.floor(fogStartY / cell),
                        (int) Math.floor(p.getX() / cell), (int) Math.floor(p.getY() / cell), fogTool);
            }
            e.consume();
        }
        // Medicao fica visivel depois de soltar (de proposito -- da tempo
        // de ler o resultado), some so no proximo clique-e-arraste ou
        // quando setMeasuring(false) e chamado.
    }

    /** Redesenha o template do zero com a forma atual (templateShapeType)
     * a partir de x1,y1 (origem, fixa durante o arrasto) at x2,y2 (ponto
     * atual do mouse) -- chamado a cada movimento do mouse durante o
     * arrasto, igual a regua de medicao. As 4 formas usam o mesmo par de
     * pontos, so interpretado diferente:
     * <ul>
     * <li>Circulo: x1,y1 e o centro; a distancia at x2,y2 e o raio.</li>
     * <li>Cone: x1,y1 e a origem (de onde o efeito parte, ex.: o
     * conjurador); x2,y2 define a direcao e o comprimento. A largura da
     * ponta do cone e igual ao comprimento (aproximacao padrao de
     * VTT pro cone "realista" de 53 graus do 5e -- ver XPHB pg. 395).</li>
     * <li>Linha: um retangulo fino (1 casa de largura) de x1,y1 at
     * x2,y2.</li>
     * <li>Cubo: um quadrado alinhado aos eixos, com um canto em x1,y1 e o
     * lado igual ao maior dos dois deltas (fica do mesmo tamanho pra
     * qualquer direcao que arrastar).</li>
     * </ul>
     */
    private void drawTemplate(double x1, double y1, double x2, double y2) {
        templateGroup.getChildren().clear();
        lastTemplateShape = templateShapeType;
        lastTemplate = new double[]{x1, y1, x2, y2};
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dragDistance = Math.hypot(dx, dy);
        Color fill = Color.web("#E8A23E", 0.28);
        Color stroke = Color.web("#E8A23E");

        javafx.scene.shape.Shape shape;
        switch (templateShapeType) {
            case "Cone" -> {
                double angle = Math.atan2(dy, dx);
                double halfWidth = dragDistance / 2;
                double perpX = Math.cos(angle + Math.PI / 2) * halfWidth;
                double perpY = Math.sin(angle + Math.PI / 2) * halfWidth;
                Polygon cone = new Polygon(
                        x1, y1,
                        x2 + perpX, y2 + perpY,
                        x2 - perpX, y2 - perpY);
                shape = cone;
            }
            case "Linha" -> {
                // Nao usa Rectangle+setRotate: o pivot de rotacao do JavaFX e
                // o CENTRO do shape (mesma pegadinha do zoom centrado no
                // cursor, ver onScroll), nao o ponto x1,y1 -- girar assim
                // faria a linha "escorregar" do ponto clicado pra qualquer
                // angulo que nao fosse puramente horizontal. Em vez disso,
                // calcula os 4 cantos na mao (mesma tecnica do cone acima),
                // que fica ancorado em x1,y1 de verdade nao importa o angulo.
                double lineHalfWidth = Math.max(4, scene.getGrid().getCellSize()) / 2;
                double angle = Math.atan2(dy, dx);
                double perpX = Math.cos(angle + Math.PI / 2) * lineHalfWidth;
                double perpY = Math.sin(angle + Math.PI / 2) * lineHalfWidth;
                Polygon line = new Polygon(
                        x1 + perpX, y1 + perpY,
                        x2 + perpX, y2 + perpY,
                        x2 - perpX, y2 - perpY,
                        x1 - perpX, y1 - perpY);
                shape = line;
            }
            case "Cubo" -> {
                double side = Math.max(Math.abs(dx), Math.abs(dy));
                Rectangle cube = new Rectangle(side, side);
                cube.setLayoutX(dx >= 0 ? x1 : x1 - side);
                cube.setLayoutY(dy >= 0 ? y1 : y1 - side);
                shape = cube;
            }
            default -> {
                Circle circle = new Circle(dragDistance);
                circle.setLayoutX(x1);
                circle.setLayoutY(y1);
                shape = circle;
            }
        }
        shape.setFill(fill);
        shape.setStroke(stroke);
        shape.setStrokeWidth(2);
        shape.setMouseTransparent(true);

        Label sizeLabel = new Label(feetLabelFor(templateShapeType, dragDistance, dx, dy));
        sizeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: #F6E9D3;"
                + " -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
        sizeLabel.setLayoutX(x2);
        sizeLabel.setLayoutY(y2 - 10);

        templateGroup.getChildren().addAll(shape, sizeLabel);
    }

    /** Mesma regra de pes-por-casa que a regua de medicao usa (ver
     * updateMeasureLabel) -- cubo usa o lado, os outros usam a distancia
     * do arrasto (raio pro circulo, comprimento pro cone/linha). */
    private String feetLabelFor(String shapeType, double dragDistance, double dx, double dy) {
        double cellSize = scene.getGrid().getCellSize();
        double feetPerCell = scene.getGrid().getFeetPerCell();
        double sizePixels = "Cubo".equals(shapeType) ? Math.max(Math.abs(dx), Math.abs(dy)) : dragDistance;
        long cells = cellSize > 0 ? Math.round(sizePixels / cellSize) : 0;
        long feet = Math.round(cells * feetPerCell);
        String noun = switch (shapeType) {
            case "Cone" -> "cone";
            case "Linha" -> "linha";
            case "Cubo" -> "cubo";
            default -> "raio";
        };
        return Distances.feetAndMeters(feet) + " (" + noun + ")";
    }

    /** Distancia em casas pela regra de movimento do D&D 5e (PHB/XPHB):
     * diagonal custa o MESMO que ortogonal, nao a hipotenusa de
     * Pitagoras -- e o maior dos dois deltas em casas (distancia de
     * Chebyshev), nao Math.hypot. Ex.: andar 3 casas na diagonal custa
     * 3 casas (15 pes com o padrao de 5 pes/casa), nao "4,24 casas". */
    private void updateMeasureLabel(double x1, double y1, double x2, double y2) {
        double cellSize = scene.getGrid().getCellSize();
        double feetPerCell = scene.getGrid().getFeetPerCell();
        long cells = scene.getGrid().cellsBetween(x1, y1, x2, y2);
        long feet = Math.round(cells * feetPerCell);
        measureLabel.setText(Distances.feetAndMeters(feet) + " · " + cells + " casa" + (cells == 1 ? "" : "s"));
        measureLabel.setLayoutX((x1 + x2) / 2);
        measureLabel.setLayoutY((y1 + y2) / 2 - 10);
    }
}
