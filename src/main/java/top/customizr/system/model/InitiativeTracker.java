package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Ordem de iniciativa do combate da campanha (rodada e de quem e a vez). */
@Entity
@Table(name = "initiative_trackers")
@Getter
public class InitiativeTracker {

    @Id
    private UUID id;

    @JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, unique = true)
    private Campaign campaign;

    @Column(nullable = false)
    private int round;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<InitiativeEntry> entries = new ArrayList<>();

    protected InitiativeTracker() {}

    public InitiativeTracker(Campaign campaign) {
        if (campaign == null) throw new IllegalArgumentException("Campaign is required");
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.round = 1;
    }

    /** Entra (ou atualiza, se o token ja estava) e reordena: maior iniciativa primeiro; empate = maior bonus. */
    public InitiativeEntry add(String name, int initiative, int bonus, UUID tokenId) {
        InitiativeEntry current = entries.isEmpty() ? null : entries.get(Math.min(turnIndex, entries.size() - 1));
        if (tokenId != null) entries.removeIf(e -> tokenId.equals(e.getTokenId()));
        InitiativeEntry entry = new InitiativeEntry(this, name, initiative, bonus, tokenId);
        entries.add(entry);
        entries.sort(Comparator.comparingInt(InitiativeEntry::getInitiative).reversed()
                .thenComparing(Comparator.comparingInt(InitiativeEntry::getBonus).reversed()));
        if (current != null && entries.contains(current)) turnIndex = entries.indexOf(current);
        return entry;
    }

    public void remove(UUID entryId) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).getId().equals(entryId)) index = i;
        if (index < 0) throw new IllegalArgumentException("Entry not found");
        entries.remove(index);
        if (index < turnIndex) turnIndex--;
        if (turnIndex >= entries.size()) turnIndex = 0;
    }

    /** Passa a vez; depois do ultimo, nova rodada. */
    public void next() {
        if (entries.isEmpty()) throw new IllegalStateException("Nobody is in the initiative order");
        turnIndex++;
        if (turnIndex >= entries.size()) {
            turnIndex = 0;
            round++;
        }
    }

    public InitiativeEntry current() {
        return entries.isEmpty() ? null : entries.get(turnIndex);
    }
}
