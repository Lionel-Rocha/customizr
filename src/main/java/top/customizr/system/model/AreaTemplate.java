package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Area de efeito no mapa (bola de fogo, sopro em cone, relampago em linha...). Origem em pixels do mapa,
 * tamanho em pes como no livro: raio (circulo), comprimento (cone, linha) ou lado (quadrado, centrado na origem).
 * Cone de 5e: a largura numa distancia e igual a essa distancia (abertura de ~53 graus).
 */
@Entity
@Table(name = "scene_templates")
@Getter
public class AreaTemplate {

    public enum Shape { CIRCLE, CONE, LINE, SQUARE }

    /** Meia abertura do cone de 5e: atan(1/2). */
    private static final double CONE_HALF_ANGLE = Math.atan(0.5);

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Shape shape;

    private double x;
    private double y;

    /** Direcao em graus (0 = direita, 90 = baixo), usada no cone e na linha. */
    private double angle;

    @Column(name = "size_ft", nullable = false)
    private int sizeFt;

    @Column(name = "width_ft", nullable = false)
    private int widthFt;

    @Column(length = 60)
    private String label;

    @Column(length = 9)
    private String color;

    /** Quem colocou (o jogador tambem lanca magia de area); o mestre apaga qualquer uma. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    protected AreaTemplate() {}

    AreaTemplate(Scene scene, Shape shape, double x, double y, double angle, int sizeFt, Integer widthFt,
                 String label, String color, UUID ownerId) {
        if (shape == null) throw new IllegalArgumentException("Shape is required");
        if (sizeFt < 5 || sizeFt > 1000) throw new IllegalArgumentException("Area size must be between 5 and 1000 feet");
        int width = widthFt == null ? 5 : widthFt;
        if (width < 5 || width > 200) throw new IllegalArgumentException("Line width must be between 5 and 200 feet");
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(angle)) throw new IllegalArgumentException("Invalid position");
        this.id = UUID.randomUUID();
        this.scene = scene;
        this.shape = shape;
        this.x = x;
        this.y = y;
        this.angle = ((angle % 360) + 360) % 360;
        this.sizeFt = sizeFt;
        this.widthFt = width;
        this.label = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(60, label.trim().length()));
        this.color = color == null || !color.matches("#[0-9A-Fa-f]{6}") ? null : color;
        this.ownerId = ownerId;
    }

    /** O ponto (em pixels do mapa) esta dentro da area? */
    public boolean covers(double px, double py) {
        double perFoot = (double) scene.getGridSizePx() / scene.getFeetPerCell();
        double size = sizeFt * perFoot;
        double dx = px - x;
        double dy = py - y;
        double tolerance = 0.5;
        return switch (shape) {
            case CIRCLE -> Math.hypot(dx, dy) <= size + tolerance;
            case SQUARE -> Math.abs(dx) <= size / 2 + tolerance && Math.abs(dy) <= size / 2 + tolerance;
            case CONE -> {
                double dist = Math.hypot(dx, dy);
                if (dist < tolerance) yield true;
                if (dist > size + tolerance) yield false;
                double diff = Math.abs(Math.atan2(dy, dx) - Math.toRadians(angle));
                diff = Math.min(diff, 2 * Math.PI - diff);
                yield diff <= CONE_HALF_ANGLE + 1e-9;
            }
            case LINE -> {
                double rad = Math.toRadians(angle);
                double along = dx * Math.cos(rad) + dy * Math.sin(rad);
                double across = -dx * Math.sin(rad) + dy * Math.cos(rad);
                yield along >= -tolerance && along <= size + tolerance && Math.abs(across) <= widthFt * perFoot / 2 + tolerance;
            }
        };
    }

    /**
     * Pega o token se cobrir o centro de alguma casa dele (token grande ocupa varias casas) e se dessa casa
     * houver linha livre ate a origem: parede ou porta fechada no meio e cobertura total, a area nao chega.
     */
    public boolean covers(Token token) {
        int cells = token.getSizeCells();
        double g = scene.getGridSizePx();
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                double px = token.getX() + (i + 0.5) * g;
                double py = token.getY() + (j + 0.5) * g;
                if (covers(px, py) && !scene.blockedBetween(x, y, px, py)) return true;
            }
        }
        return false;
    }
}
