package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parties")
@Getter
public class Party {

    @Id
    private UUID partyId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @OneToMany(mappedBy = "party")
    private List<Character> members = new ArrayList<>();

    protected Party() {}

    public Party(String name, Campaign campaign) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Party name is required");
        }
        this.partyId = UUID.randomUUID();
        this.name = name.trim();
        this.campaign = campaign;
    }

    public void addMember(Character character) {
        if (!campaign.getId().equals(character.getCampaign().getId())) {
            throw new IllegalArgumentException("Character must belong to the party's campaign");
        }
        if (character.getParty() != null && !partyId.equals(character.getParty().getPartyId())) {
            throw new IllegalStateException("Character already belongs to another party");
        }
        if (!members.contains(character)) {
            members.add(character);
            character.setParty(this);
        }
    }

    public void removeMember(Character character) {
        members.remove(character);
        if (partyId.equals(character.getParty() == null ? null : character.getParty().getPartyId())) {
            character.setParty(null);
        }
    }
}
