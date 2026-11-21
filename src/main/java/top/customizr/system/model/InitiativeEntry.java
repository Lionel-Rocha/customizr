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

@Entity
@Table(name = "initiative_entries")
@Getter
public class InitiativeEntry {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracker_id", nullable = false)
    private InitiativeTracker tracker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int initiative;

    /** Bonus de iniciativa (desempate). */
    @Column(nullable = false)
    private int bonus;

    @Column(name = "token_id")
    private UUID tokenId;

    protected InitiativeEntry() {}

    InitiativeEntry(InitiativeTracker tracker, String name, int initiative, int bonus, UUID tokenId) {
        this.id = UUID.randomUUID();
        this.tracker = tracker;
        this.name = Places.requireName(name, "entrada da iniciativa");
        this.initiative = initiative;
        this.bonus = bonus;
        this.tokenId = tokenId;
    }
}
