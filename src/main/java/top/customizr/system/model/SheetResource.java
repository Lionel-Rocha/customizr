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
 * Recurso que o jogador ou o mestre cria na ficha: cargas de um item ("Varinha de Misseis Magicos", 7),
 * usos de um talento, de uma habilidade de subclasse ou de algo da mesa.
 */
@Entity
@Table(name = "sheet_resources")
@Getter
public class SheetResource {

    public static final int MAX_USES = 999;

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private Sheet sheet;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int used;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceRecharge recharge;

    @Column(length = 300)
    private String notes;

    protected SheetResource() {}

    SheetResource(Sheet sheet, String name, int maxUses, ResourceRecharge recharge, String notes) {
        this.id = UUID.randomUUID();
        this.sheet = sheet;
        update(name, maxUses, recharge, notes);
    }

    void update(String name, int maxUses, ResourceRecharge recharge, String notes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome do recurso");
        }
        if (name.trim().length() > 80) {
            throw new IllegalArgumentException("Nome do recurso muito longo (máximo 80 letras)");
        }
        if (maxUses < 1 || maxUses > MAX_USES) {
            throw new IllegalArgumentException("Os usos máximos vão de 1 a " + MAX_USES);
        }
        if (notes != null && notes.trim().length() > 300) {
            throw new IllegalArgumentException("Notas do recurso muito longas (máximo 300 letras)");
        }
        this.name = name.trim();
        this.maxUses = maxUses;
        this.recharge = recharge == null ? ResourceRecharge.LONG_REST : recharge;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.used = Math.min(used, maxUses);
    }

    void setUsed(int used) {
        this.used = Math.max(0, Math.min(maxUses, used));
    }
}
