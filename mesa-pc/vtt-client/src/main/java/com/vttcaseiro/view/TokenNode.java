package com.vttcaseiro.view;

import com.vttcaseiro.model.Condition;
import com.vttcaseiro.model.GridSettings;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.ImageCodec;
import com.vttcaseiro.service.ImageAssetService;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.util.List;

/**
 * Representacao visual de um TokenModel: imagem (ou um circulo vermelho se
 * a imagem nao carregar), nome embaixo, arrastavel com o mouse e com
 * encaixe na grade ao soltar.
 * <p>
 * O arrasto usa Parent.sceneToLocal() pra converter a posicao do mouse pro
 * espaco do token-layer, assim funciona certo mesmo com o mapa com zoom
 * (Group.scaleX/Y) aplicado em cima -- sem isso o token "escorregaria" mais
 * rapido ou mais devagar que o mouse dependendo do zoom.
 */
public class TokenNode extends StackPane {
    private final TokenModel model;
    private final GridSettings grid;
    private final ImageAssetService imageAssetService;
    private boolean snapToGrid = true;
    private java.util.function.Consumer<TokenModel> onMoved;
    private Rectangle highlightRing;

    private double pressParentX;
    private double pressParentY;
    private double pressLayoutX;
    private double pressLayoutY;

    public TokenNode(TokenModel model, GridSettings grid) {
        this(model, grid, null);
    }

    /**
     * @param imageAssetService usado pra resolver model.getImageAssetId()
     *   pra bytes de verdade (ver tryLoadImage) -- pode ser null (mesa
     *   sem ImageAssetStore por tras, ou chamador que nao quer essa
     *   resolucao), nesse caso cai direto pro imageData/imagePath, igual
     *   sempre foi.
     */
    public TokenNode(TokenModel model, GridSettings grid, ImageAssetService imageAssetService) {
        this(model, grid, imageAssetService, true);
    }

    /** @param canSeeHp false = esconde os numeros/barra de PV (monstro visto
     *   por um jogador): so aparece um aviso "Sangrando" abaixo de metade. */
    public TokenNode(TokenModel model, GridSettings grid, ImageAssetService imageAssetService, boolean canSeeHp) {
        this.model = model;
        this.grid = grid;
        this.imageAssetService = imageAssetService;
        this.canSeeHp = canSeeHp;
        buildVisual();
        setLayoutX(model.getX());
        setLayoutY(model.getY());
        installDragHandlers();
        setOnMouseEntered(e -> highlightRing.setVisible(true));
        setOnMouseExited(e -> highlightRing.setVisible(selected));
        Tooltip.install(this, new Tooltip(tooltipText()));
    }

    private final boolean canSeeHp;
    private boolean selected;
    private javafx.scene.Group targetMarker;

    /** Mira (alvo do proximo ataque): circulo vermelho com marcas nas 4 direcoes. */
    public void setTargeted(boolean targeted) {
        if (targetMarker != null) targetMarker.setVisible(targeted);
    }

    public boolean isTargeted() {
        return targetMarker != null && targetMarker.isVisible();
    }
    private boolean draggable = true;
    private boolean dragging;

    public void setSelected(boolean selected) {
        this.selected = selected;
        highlightRing.setVisible(selected);
        highlightRing.setStroke(Color.web(selected ? "#FFCF7F" : "#E8A23E"));
        highlightRing.setStrokeWidth(selected ? 3 : 2);
    }

    /** false = token de outro jogador: nao arrasta (o servidor recusaria e
     * o token ficaria fora do lugar so nesta tela). */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
        setCursor(draggable ? Cursor.HAND : Cursor.DEFAULT);
    }

    private String tooltipText() {
        StringBuilder sb = new StringBuilder(model.getName() == null ? "" : model.getName());
        if (canSeeHp && model.getCurrentHp() != null) {
            sb.append(NL).append("PV: ").append(model.getCurrentHp());
            if (model.getMaxHp() != null) sb.append(" / ").append(model.getMaxHp());
            if (model.getTempHp() != null && model.getTempHp() > 0) sb.append(" (+").append(model.getTempHp()).append(" temp)");
        }
        if (!model.getConditions().isEmpty()) {
            java.util.List<String> labels = new java.util.ArrayList<>();
            for (String c : model.getConditions()) {
                try {
                    labels.add(Condition.valueOf(c).label());
                } catch (IllegalArgumentException ex) {
                    labels.add(c);
                }
            }
            sb.append(NL).append("Condições: ").append(String.join(", ", labels));
        }
        if (!model.getEffects().isEmpty()) sb.append(NL).append("Efeitos: ").append(String.join(", ", model.getEffects()));
        if (model.getInitiative() != null) sb.append(NL).append("Iniciativa: ").append(model.getInitiative());
        if (model.getCharacterSheetId() != null) sb.append(NL).append("(clique duplo abre a ficha)");
        return sb.toString();
    }

    private static final String NL = System.lineSeparator();

    public TokenModel getModel() {
        return model;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
    }

    /** Chamado (com o TokenModel ja atualizado) quando o jogador solta o
     * token depois de arrastar. A Fase 2 usa isso pra avisar a rede --
     * sem rede conectada, ninguem registra esse callback e nada muda. */
    public void setOnMoved(java.util.function.Consumer<TokenModel> onMoved) {
        this.onMoved = onMoved;
    }

    /** Pode soltar o token em {x, y}? false = volta pro lugar (ex.: parede no caminho). */
    private java.util.function.BiPredicate<TokenModel, double[]> moveGuard;

    public void setMoveGuard(java.util.function.BiPredicate<TokenModel, double[]> guard) {
        this.moveGuard = guard;
    }

    /** Recebe o token ja movido + a posicao ANTIGA {x, y} (pro desfazer). */
    private java.util.function.BiConsumer<TokenModel, double[]> onMovedFrom;

    public void setOnMovedFrom(java.util.function.BiConsumer<TokenModel, double[]> onMovedFrom) {
        this.onMovedFrom = onMovedFrom;
    }

    private void buildVisual() {
        double size = grid.getCellSize();
        double w = size * model.getWidthCells();
        double h = size * model.getHeightCells();
        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        Image image = tryLoadImage(w, h);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(w);
            imageView.setFitHeight(h);
            imageView.setPreserveRatio(false);
            imageView.setRotate(model.getRotation()); // so a imagem gira; nome e barras ficam retos
            getChildren().add(imageView);
        } else {
            javafx.scene.Node silhouette = SilhouetteIcon.create(Math.min(w, h));
            silhouette.setRotate(model.getRotation());
            getChildren().add(silhouette);
        }

        // anel de destaque ao passar o mouse -- comeca invisivel, so aparece
        // via setOnMouseEntered/Exited (ver construtor). E um retangulo sem
        // preenchimento (so borda) do tamanho do token, levemente maior.
        highlightRing = new Rectangle(w + 4, h + 4);
        highlightRing.setFill(Color.TRANSPARENT);
        highlightRing.setStroke(Color.web("#E8A23E"));
        highlightRing.setStrokeWidth(2);
        highlightRing.setArcWidth(6);
        highlightRing.setArcHeight(6);
        highlightRing.setVisible(false);
        highlightRing.setMouseTransparent(true);
        getChildren().add(highlightRing);

        targetMarker = new javafx.scene.Group();
        double r = Math.min(w, h) / 2 + 3;
        javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(0, 0, r);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web("#E2483F"));
        ring.setStrokeWidth(2.5);
        ring.getStrokeDashArray().addAll(10.0, 5.0);
        targetMarker.getChildren().add(ring);
        for (int i = 0; i < 4; i++) {
            javafx.scene.shape.Line tick = new javafx.scene.shape.Line(0, -r - 6, 0, -r + 6);
            tick.setStroke(Color.web("#E2483F"));
            tick.setStrokeWidth(3);
            tick.setRotate(0);
            tick.getTransforms().add(new javafx.scene.transform.Rotate(90 * i, 0, 0));
            targetMarker.getChildren().add(tick);
        }
        targetMarker.setMouseTransparent(true);
        targetMarker.setManaged(false);
        targetMarker.setLayoutX(w / 2);
        targetMarker.setLayoutY(h / 2);
        targetMarker.setVisible(false);
        getChildren().add(targetMarker);

        Label label = new Label(model.getName());
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-background-color: rgba(0,0,0,0.65); -fx-padding: 1 4 1 4; -fx-background-radius: 4;");
        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
        label.setTranslateY(14);
        label.setMouseTransparent(true);
        getChildren().add(label);

        javafx.scene.Node hpBar = canSeeHp ? buildHpBar(w) : buildBloodiedMarker();
        if (hpBar != null) {
            getChildren().add(hpBar);
        }

        javafx.scene.Node effectsMarker = buildEffectsMarker();
        if (effectsMarker != null) {
            getChildren().add(effectsMarker);
        }

        if (model.hasCondition(Condition.UNCONSCIOUS)) {
            // Token caido fica apagado -- da pra ver de longe quem esta no chao.
            setOpacity(0.55);
        }
        if (model.isHiddenFromPlayers()) {
            // So o mestre chega a ver este token (o servidor nem manda pros jogadores).
            setOpacity(0.45);
            Label hiddenBadge = new Label("OCULTO");
            hiddenBadge.setStyle("-fx-background-color: #000000CC; -fx-text-fill: -c-accent-text; -fx-font-size: 8px;"
                    + " -fx-font-weight: bold; -fx-padding: 0 3 0 3; -fx-background-radius: 3;");
            StackPane.setAlignment(hiddenBadge, Pos.CENTER);
            hiddenBadge.setMouseTransparent(true);
            getChildren().add(hiddenBadge);
        }

        javafx.scene.Node badges = buildConditionBadges();
        if (badges != null) {
            getChildren().add(badges);
        }

        // para onde olha: setinha na borda, na direcao marcada
        if (model.getFacingDegrees() != null) {
            double rr = Math.min(w, h) / 2;
            javafx.scene.shape.Polygon arrow = new javafx.scene.shape.Polygon(rr + 9, 0, rr - 1, -7, rr - 1, 7);
            arrow.setFill(Color.web("#FFCF7F"));
            arrow.setStroke(Color.web("#1B1411"));
            arrow.setStrokeWidth(1.2);
            javafx.scene.Group facing = new javafx.scene.Group(arrow);
            facing.setManaged(false);
            facing.setMouseTransparent(true);
            facing.setLayoutX(w / 2);
            facing.setLayoutY(h / 2);
            facing.getTransforms().add(new javafx.scene.transform.Rotate(model.getFacingDegrees(), 0, 0));
            getChildren().add(facing);
        }

        // altura: selo no canto de cima ("▲ 30 ft" voando, "▼ 10 ft" abaixo do chao)
        if (model.getElevationFeet() != 0) {
            int e = model.getElevationFeet();
            Label elevation = new Label((e > 0 ? "▲ " : "▼ ") + Distances.feetAndMeters(Math.abs(e)));
            elevation.setStyle("-fx-background-color: " + (e > 0 ? "#1F4E79E6" : "#5A3A1AE6") + "; -fx-text-fill: white;"
                    + " -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 0 3 0 3; -fx-background-radius: 3;");
            StackPane.setAlignment(elevation, Pos.TOP_LEFT);
            elevation.setTranslateY(-10);
            elevation.setMouseTransparent(true);
            getChildren().add(elevation);
        }
        baseOpacity = getOpacity();
    }

    /** Opacidade de quando o token foi montado (caido, oculto). */
    private double baseOpacity = 1;

    /** Sentido sem enxergar (sentido sismico, tocha ao longe): aparece meio apagado. */
    public void setSensedOnly(boolean sensedOnly) {
        setOpacity(baseOpacity * (sensedOnly ? 0.6 : 1));
    }

    /** Selinhos coloridos (um circulo + 2 letras cada) pras condicoes
     * marcadas neste token (ver Condition e MapView.editConditions) --
     * mesma ideia do Roll20/Foundry, so que sem precisar de nenhum
     * icone/imagem externa. Fica no canto superior esquerdo, pra nao
     * disputar espaco com a barra de PV (centro) nem o nome (embaixo).
     * Devolve null se o token nao tiver nenhuma condicao marcada. */
    private javafx.scene.Node buildConditionBadges() {
        List<String> names = model.getConditions();
        if (names == null || names.isEmpty()) {
            return null;
        }
        HBox row = new HBox(2);
        for (String name : names) {
            Condition condition;
            try {
                condition = Condition.valueOf(name);
            } catch (IllegalArgumentException ex) {
                continue; // nome desconhecido (save de uma versao futura?) -- ignora
            }
            StackPane badge = new StackPane();
            Circle circle = new Circle(8, Color.web(condition.colorHex()));
            circle.setStroke(Color.web("#000000", 0.6));
            circle.setStrokeWidth(1);
            Label letters = new Label(condition.abbreviation());
            letters.setTextFill(Color.WHITE);
            letters.setStyle("-fx-font-size: 8px; -fx-font-weight: bold;");
            badge.getChildren().addAll(circle, letters);
            row.getChildren().add(badge);
        }
        if (row.getChildren().isEmpty()) {
            return null;
        }
        StackPane.setAlignment(row, Pos.TOP_LEFT);
        row.setTranslateX(-4);
        row.setTranslateY(-10);
        row.setMouseTransparent(true);
        return row;
    }

    /** Barrinha de PV acima do token (so aparece se currentHp/maxHp
     * estiverem preenchidos) -- verde/laranja/vermelho pela porcentagem,
     * mesma convencao que Roll20/Foundry usam. */
    /** Pra quem nao ve o PV: so avisa quando esta abaixo da metade. */
    private javafx.scene.Node buildBloodiedMarker() {
        Integer current = model.getCurrentHp();
        Integer max = model.getMaxHp();
        if (current == null || max == null || max <= 0 || current * 2 > max) return null;
        Label label = new Label(current == 0 ? "Caído" : "Sangrando");
        label.setStyle("-fx-background-color: #D9544CDD; -fx-text-fill: white; -fx-font-size: 9px;"
                + " -fx-padding: 0 4 0 4; -fx-background-radius: 3;");
        StackPane.setAlignment(label, Pos.TOP_CENTER);
        label.setTranslateY(-10);
        label.setMouseTransparent(true);
        return label;
    }

    /** Efeitos livres (concentracao, exaustao, "Abencoado"...): um selo no
     * canto, com a lista no tooltip -- o texto inteiro nao cabe no token. */
    private javafx.scene.Node buildEffectsMarker() {
        java.util.List<String> effects = model.getEffects();
        if (effects.isEmpty()) return null;
        boolean concentrating = effects.stream().anyMatch(e -> e.startsWith("Concentrando"));
        Label label = new Label(concentrating ? "C" : "E" + (effects.size() > 1 ? effects.size() : ""));
        label.setStyle("-fx-background-color: " + (concentrating ? "#4F8FA8" : "#7A5CC9")
                + "; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold;"
                + " -fx-padding: 0 4 0 4; -fx-background-radius: 8;");
        StackPane.setAlignment(label, Pos.TOP_RIGHT);
        label.setTranslateX(6);
        label.setTranslateY(-10);
        label.setMouseTransparent(true);
        return label;
    }

    private javafx.scene.Node buildHpBar(double tokenWidth) {
        Integer current = model.getCurrentHp();
        Integer max = model.getMaxHp();
        if (current == null || max == null || max <= 0) {
            return null;
        }
        double pct = Math.max(0, Math.min(1.0, current / (double) max));
        double barWidth = Math.max(20, tokenWidth * 0.85);

        Rectangle background = new Rectangle(barWidth, 5, Color.web("#000000", 0.55));
        background.setArcWidth(3);
        background.setArcHeight(3);

        String fillColor = pct > 0.66 ? "#3FBE6E" : (pct > 0.33 ? "#E0963C" : "#D9544C");
        Rectangle fill = new Rectangle(barWidth * pct, 5, Color.web(fillColor));
        fill.setArcWidth(3);
        fill.setArcHeight(3);

        javafx.scene.layout.StackPane barStack = new javafx.scene.layout.StackPane(background, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        if (model.getTempHp() != null && model.getTempHp() > 0) {
            double tempPct = Math.min(1.0, model.getTempHp() / (double) max);
            Rectangle temp = new Rectangle(barWidth * tempPct, 2, Color.web("#5B8DEF"));
            barStack.getChildren().add(temp);
            StackPane.setAlignment(temp, Pos.TOP_LEFT);
        }
        StackPane.setAlignment(barStack, Pos.TOP_CENTER);
        barStack.setTranslateY(-8);
        barStack.setMouseTransparent(true);
        return barStack;
    }

    /** Tenta abrir a imagem do token, na ordem: (1) imageAssetId resolvido
     * pelo ImageAssetStore da mesa (unica copia no disco, dedup por hash
     * -- ver ImageAssetService/ImageGalleryView); (2) os bytes embutidos
     * (model.getImageData(), ver com.vttcaseiro.net.ImageCodec) -- e o
     * que faz a imagem aparecer pra quem NAO e o dono do arquivo (outro
     * jogador conectado pela rede, ou um asset que chegou pela rede mas
     * ainda nao foi gravado localmente); (3) o caminho de arquivo local,
     * ultimo recurso pra token/mesa salvos antes do asset store existir.
     * Devolve null (sem lancar excecao) se nenhum dos tres der certo --
     * quem chama decide o que mostrar no lugar (a silhueta padrao). */
    private Image tryLoadImage(double w, double h) {
        // a imagem so precisa da resolucao da tela; token enorme (casa grande x muitas casas) nao vira textura gigante
        double big = Math.max(w, h);
        if (!Double.isFinite(big) || big <= 0) {
            w = 64;
            h = 64;
        } else if (big > 1024) {
            w = w * 1024 / big;
            h = h * 1024 / big;
        }
        if (imageAssetService != null && model.getImageAssetId() != null) {
            try {
                byte[] bytes = imageAssetService.readBytes(model.getImageAssetId());
                Image fromAsset = new Image(new java.io.ByteArrayInputStream(bytes), w, h, false, true);
                if (!fromAsset.isError()) {
                    return fromAsset;
                }
            } catch (java.io.IOException ignored) {
                // asset ainda nao chegou/nao existe localmente -- cai pros
                // outros dois jeitos abaixo, igual sempre foi antes disto existir.
            }
        }
        Image fromData = ImageCodec.decode(model.getImageData(), w, h);
        if (fromData != null) {
            return fromData;
        }
        String path = model.getImagePath();
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Image candidate = new Image(new File(path).toURI().toString(), w, h, false, true);
            return candidate.isError() ? null : candidate;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Avisado durante o arraste: {xInicial, yInicial, xAtual, yAtual}; null quando solta. */
    private java.util.function.BiConsumer<TokenModel, double[]> dragProgress;

    public void setDragProgress(java.util.function.BiConsumer<TokenModel, double[]> listener) {
        this.dragProgress = listener;
    }

    private void installDragHandlers() {
        setCursor(Cursor.HAND);
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
    }

    private void onPressed(MouseEvent e) {
        dragging = draggable && e.getButton() == javafx.scene.input.MouseButton.PRIMARY;
        if (!dragging) {
            e.consume();
            return;
        }
        Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        pressParentX = p.getX();
        pressParentY = p.getY();
        pressLayoutX = getLayoutX();
        pressLayoutY = getLayoutY();
        toFront();
        e.consume();
    }

    private void onDragged(MouseEvent e) {
        if (!dragging) {
            e.consume();
            return;
        }
        Point2D p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        double dx = p.getX() - pressParentX;
        double dy = p.getY() - pressParentY;
        setLayoutX(pressLayoutX + dx);
        setLayoutY(pressLayoutY + dy);
        if (dragProgress != null) {
            dragProgress.accept(model, new double[]{pressLayoutX, pressLayoutY, getLayoutX(), getLayoutY()});
        }
        e.consume();
    }

    private void onReleased(MouseEvent e) {
        if (!dragging) {
            e.consume();
            return;
        }
        dragging = false;
        if (dragProgress != null) dragProgress.accept(model, null);
        if (getLayoutX() == pressLayoutX && getLayoutY() == pressLayoutY) {
            e.consume();
            return; // so um clique: nao manda "mover" pela rede a toa
        }
        double finalX = getLayoutX();
        double finalY = getLayoutY();
        if (snapToGrid && grid.getCellSize() > 0) {
            double[] snapped = grid.snapTopLeft(finalX, finalY, model.getWidthCells());
            finalX = snapped[0];
            finalY = snapped[1];
            setLayoutX(finalX);
            setLayoutY(finalY);
        }
        if (moveGuard != null && !moveGuard.test(model, new double[]{finalX, finalY})) {
            setLayoutX(pressLayoutX);
            setLayoutY(pressLayoutY);
            e.consume();
            return;
        }
        double oldX = model.getX();
        double oldY = model.getY();
        model.setX(finalX);
        model.setY(finalY);
        if (onMovedFrom != null && (oldX != finalX || oldY != finalY)) {
            onMovedFrom.accept(model, new double[]{oldX, oldY});
        }
        if (onMoved != null) {
            onMoved.accept(model);
        }
        e.consume();
    }
}
