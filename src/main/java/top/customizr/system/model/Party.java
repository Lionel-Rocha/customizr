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

    /** Limite de membros (null = sem limite). */
    @Column(name = "max_size")
    private Integer maxSize;

    protected Party() {}

    public Party(String name, Campaign campaign) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome do grupo");
        }
        this.partyId = UUID.randomUUID();
        this.name = name.trim();
        this.campaign = campaign;
    }

    public void addMember(Character character) {
        if (!campaign.getId().equals(character.getCampaign().getId())) {
            throw new IllegalArgumentException("O personagem precisa ser da mesma campanha do grupo");
        }
        if (character.getParty() != null && !partyId.equals(character.getParty().getPartyId())) {
            throw new IllegalStateException("O personagem já está em outro grupo");
        }
        if (!members.contains(character)) {
            if (maxSize != null && members.size() >= maxSize) {
                throw new IllegalStateException("O grupo está cheio (máximo " + maxSize + " personagens)");
            }
            members.add(character);
            character.setParty(this);
        }
    }

    public void changeMaxSize(Integer maxSize) {
        if (maxSize != null && maxSize < 1) {
            throw new IllegalArgumentException("O tamanho máximo do grupo precisa ser pelo menos 1");
        }
        if (maxSize != null && members.size() > maxSize) {
            throw new IllegalStateException("O grupo já tem mais de " + maxSize + " personagens");
        }
        this.maxSize = maxSize;
    }

    public void removeMember(Character character) {
        members.remove(character);
        if (partyId.equals(character.getParty() == null ? null : character.getParty().getPartyId())) {
            character.setParty(null);
        }
    }
}
