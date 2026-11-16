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

/** Ataque da ficha ("Espada longa", +5, 1d8+3 cortante). */
@Entity
@Table(name = "sheet_attacks")
@Getter
public class SheetAttack {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private Sheet sheet;

    @Column(nullable = false)
    private String name;

    @Column(name = "attack_bonus", nullable = false)
    private int attackBonus;

    /** Expressao de dano, ex.: "1d8+3". */
    @Column(nullable = false, length = 60)
    private String damage;

    @Column(name = "damage_type", length = 40)
    private String damageType;

    @Column(length = 500)
    private String notes;

    protected SheetAttack() {}

    SheetAttack(Sheet sheet, String name, int attackBonus, String damage, String damageType, String notes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attack name is required");
        }
        DiceRoller.roll(damage, sides -> 1); // valida a expressao de dano
        this.id = UUID.randomUUID();
        this.sheet = sheet;
        this.name = name.trim();
        this.attackBonus = attackBonus;
        this.damage = damage.replaceAll("\\s+", "");
        this.damageType = damageType == null || damageType.isBlank() ? null : damageType.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}
