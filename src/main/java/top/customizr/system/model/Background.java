package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.UUID;

/** Antecedente importado do 5etools. No de 2024 (5.5) da os aumentos de atributo e um talento. */
@Entity
@Table(name = "backgrounds", uniqueConstraints = @UniqueConstraint(
        name = "uk_backgrounds_name_source", columnNames = {"name", "source_id"}))
@Getter
public class Background {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "ability_score_increases", length = 500)
    private String abilityScoreIncreases;

    @Column(name = "skill_proficiencies", length = 500)
    private String skillProficiencies;

    @Column(name = "granted_feat")
    private String grantedFeat;


    @Column(columnDefinition = "TEXT")
    private String description;

    protected Background() {}

    public Background(String name, Source source) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Background name is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Background source is required");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
    }

    public void update(String abilityScoreIncreases, String skillProficiencies, String grantedFeat, String description) {
        this.abilityScoreIncreases = abilityScoreIncreases;
        this.skillProficiencies = skillProficiencies;
        this.grantedFeat = grantedFeat;
        this.description = description;
    }
}
