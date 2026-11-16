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

    @ManyToOne
    @JoinColumn(name = "party_id")
    private Party party;

    @OneToOne(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private Sheet sheet;

    @ManyToOne
    @JoinColumn(name = "species_id")
    private Species species;

    @ManyToOne
    @JoinColumn(name = "background_id")
    private Background background;

    @Column(name = "experience_points")
    private Integer experiencePoints;

    /** Retrato (ex.: /image/{id} enviado em POST /image). */
    @Column(name = "portrait_url", length = 1000)
    private String portraitUrl;

    /** XP pra cada nivel (1 a 20). */
    private static final int[] XP_FOR_LEVEL = {0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000,
            85000, 100000, 120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000};

    protected Character() {}

    public Character(String name, Campaign campaign, User owner) {
        this.characterId = UUID.randomUUID();
        this.name = name;
        this.status = CharacterStatus.ALIVE;
        this.campaign = campaign;
        this.owner = owner;
        this.sheet = new Sheet(this);
    }

    void setParty(Party party) {
        this.party = party;
    }

    public void chooseOrigin(Species species, Background background) {
        this.species = species;
        this.background = background;
    }

    public void changePortrait(String portraitUrl) {
        this.portraitUrl = portraitUrl == null || portraitUrl.isBlank() ? null : portraitUrl.trim();
    }

    public int getExperiencePoints() {
        return experiencePoints == null ? 0 : experiencePoints;
    }

    /** Soma (ou tira, com negativo) XP; nao fica abaixo de 0. */
    public void gainExperience(int amount) {
        this.experiencePoints = Math.max(0, getExperiencePoints() + amount);
    }

    /** Nivel que o XP atual permite (o personagem sobe adicionando nivel de classe). */
    public int getLevelByExperience() {
        int level = 1;
        for (int i = 0; i < XP_FOR_LEVEL.length; i++) {
            if (getExperiencePoints() >= XP_FOR_LEVEL[i]) level = i + 1;
        }
        return level;
    }

    /** XP que falta pro proximo nivel (null no 20). */
    public Integer getExperienceToNextLevel() {
        int level = getLevelByExperience();
        return level >= 20 ? null : XP_FOR_LEVEL[level] - getExperiencePoints();
    }

    void die() {
        this.status = CharacterStatus.DEAD;
    }

    void revive() {
        if (status == CharacterStatus.DEAD) this.status = CharacterStatus.ALIVE;
    }
}
