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
 * Parede ou porta da cena: um segmento de (x1, y1) a (x2, y2), em pixels da imagem do mapa.
 * Parede e porta fechada bloqueiam a visao e o movimento dos jogadores; porta aberta nao.
 * Porta trancada so o mestre abre.
 */
@Entity
@Table(name = "scene_walls")
@Getter
public class Wall {

    public enum Type { WALL, DOOR }

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    private double x1;
    private double y1;
    private double x2;
    private double y2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(nullable = false)
    private boolean locked;

    protected Wall() {}

    Wall(Scene scene, double x1, double y1, double x2, double y2, Type type) {
        for (double v : new double[] {x1, y1, x2, y2}) {
            if (Double.isNaN(v) || Double.isInfinite(v) || v < -10_000 || v > 100_000) {
                throw new IllegalArgumentException("Wall is outside the map");
            }
        }
        if (Math.hypot(x2 - x1, y2 - y1) < 1) throw new IllegalArgumentException("A wall needs two different points");
        this.id = UUID.randomUUID();
        this.scene = scene;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.type = type == null ? Type.WALL : type;
    }

    public boolean isDoor() {
        return type == Type.DOOR;
    }

    /** Abre/fecha a porta. Parede nao abre. */
    public void setOpen(boolean open) {
        if (!isDoor()) throw new IllegalArgumentException("Only doors open");
        this.open = open;
    }

    public void setLocked(boolean locked) {
        if (!isDoor()) throw new IllegalArgumentException("Only doors can be locked");
        this.locked = locked;
        if (locked) this.open = false;
    }

    /** Bloqueia visao e passagem: parede, ou porta fechada. */
    public boolean blocks() {
        return !isDoor() || !open;
    }

    /** O caminho de (ax, ay) a (bx, by) cruza esta parede? (encostar na ponta nao conta) */
    public boolean crosses(double ax, double ay, double bx, double by) {
        double d1 = cross(x1, y1, x2, y2, ax, ay);
        double d2 = cross(x1, y1, x2, y2, bx, by);
        double d3 = cross(ax, ay, bx, by, x1, y1);
        double d4 = cross(ax, ay, bx, by, x2, y2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double ox, double oy, double px, double py, double qx, double qy) {
        return (px - ox) * (qy - oy) - (py - oy) * (qx - ox);
    }
}
