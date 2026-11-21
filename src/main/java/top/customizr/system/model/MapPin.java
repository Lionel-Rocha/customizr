package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Alfinete no mapa da cena: um ponto com icone, nome, nota e, se quiser, um verbete da enciclopedia ligado
 * (a taverna, o templo, a armadilha). Oculto = so o mestre ve.
 */
@Entity
@Table(name = "scene_pins")
@Getter
public class MapPin {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    private double x;
    private double y;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(length = 16)
    private String icon;

    @Column(length = 2000)
    private String note;

    /** Verbete da enciclopedia da campanha (o texto vem de la, com a visibilidade dela). */
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "visible_to_players", nullable = false)
    private boolean visibleToPlayers;

    protected MapPin() {}

    MapPin(Scene scene, double x, double y, String label, String icon, String note, UUID entryId, boolean visibleToPlayers) {
        this.id = UUID.randomUUID();
        this.scene = scene;
        update(x, y, label, icon, note, entryId, visibleToPlayers);
    }

    public void update(double x, double y, String label, String icon, String note, UUID entryId, boolean visibleToPlayers) {
        if (Double.isNaN(x) || Double.isNaN(y) || x < -10_000 || y < -10_000 || x > 100_000 || y > 100_000) {
            throw new IllegalArgumentException("O alfinete está fora do mapa");
        }
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Falta o nome do alfinete");
        if (label.trim().length() > 60) throw new IllegalArgumentException("Nome do alfinete muito longo");
        if (icon != null && icon.length() > 16) throw new IllegalArgumentException("Ícone do alfinete muito longo");
        if (note != null && note.length() > 2000) throw new IllegalArgumentException("Nota do alfinete muito longa");
        this.x = x;
        this.y = y;
        this.label = label.trim();
        this.icon = icon == null || icon.isBlank() ? null : icon.trim();
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.entryId = entryId;
        this.visibleToPlayers = visibleToPlayers;
    }
}
