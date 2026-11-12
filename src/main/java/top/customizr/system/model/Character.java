package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "characters")
@Getter
public class Character {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID characterId;

    @Column(nullable = false)
    @Setter
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CharacterStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToOne(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private Sheet sheet;

    protected Character() {}

    public Character(String name, Campaign campaign, User owner) {
        this.characterId = UUID.randomUUID();
        this.name = name;
        this.status = CharacterStatus.ALIVE;
        this.campaign = campaign;
        this.owner = owner;
        this.sheet = new Sheet(this);
    }
}
