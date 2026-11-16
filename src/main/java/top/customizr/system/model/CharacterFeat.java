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

/** Talento do catalogo escolhido pelo personagem (CharacterFeat do diagrama). */
@Entity
@Table(name = "character_feats")
@Getter
public class CharacterFeat {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private Sheet sheet;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feat_id", nullable = false)
    private FeatDefinition feat;

    @Column(name = "character_level", nullable = false)
    private int characterLevel;

    /** Escolhas do talento em texto (ex.: "STR +1; Cleric spells"). */
    @Column(length = 500)
    private String choices;

    protected CharacterFeat() {}

    CharacterFeat(Sheet sheet, FeatDefinition feat, int characterLevel, String choices) {
        if (feat == null) {
            throw new IllegalArgumentException("Feat is required");
        }
        if (characterLevel < 1 || characterLevel > 20) {
            throw new IllegalArgumentException("Character level must be between 1 and 20");
        }
        this.id = UUID.randomUUID();
        this.sheet = sheet;
        this.feat = feat;
        this.characterLevel = characterLevel;
        this.choices = choices == null || choices.isBlank() ? null : choices.trim();
    }
}
