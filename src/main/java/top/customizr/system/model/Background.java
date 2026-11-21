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



    // Mesmo padrao do Feature/DndClass: description = "entries" do 5etools em JSON cru (com as tags {@...}
    // pro front renderizar), descriptionText = o mesmo ja em texto, metadata = a entrada original inteira.
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "source_code")
    private String sourceCode;

    private Integer page;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private java.util.Map<String, Object> metadata;

    /** Caracteristicas do antecedente: cada um e um {@link Feature} (categoria "background"), como as features de classe. */
    @jakarta.persistence.ManyToMany
    @jakarta.persistence.JoinTable(name = "background_features",
            joinColumns = @jakarta.persistence.JoinColumn(name = "background_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "feature_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    private java.util.List<Feature> features = new java.util.ArrayList<>();

    protected Background() {}

    public Background(String name, Source source) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome do antecedente");
        }
        if (source == null) {
            throw new IllegalArgumentException("Falta a fonte do antecedente");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
    }

    public void update(String abilityScoreIncreases, String skillProficiencies, String grantedFeat) {
        this.abilityScoreIncreases = abilityScoreIncreases;
        this.skillProficiencies = skillProficiencies;
        this.grantedFeat = grantedFeat;
    }

    public void replaceFeatures(java.util.List<Feature> features) {
        this.features.clear();
        this.features.addAll(features);
    }

    /** Dados da importacao do 5etools (descricao crua com tags, texto pronto, pagina e a entrada original). */
    public void updateSourceData(String rawEntries, String descriptionText, Integer page, java.util.Map<String, Object> metadata) {
        this.description = rawEntries;
        this.descriptionText = descriptionText;
        this.sourceCode = source.getCode();
        this.page = page;
        this.metadata = metadata;
    }
}
