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

/** Especie (raca) importada do 5etools. Aumentos de atributo so existem nas de 2014 (5e). */
@Entity
@Table(name = "species", uniqueConstraints = @UniqueConstraint(
        name = "uk_species_name_source", columnNames = {"name", "source_id"}))
@Getter
public class Species {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(length = 40)
    private String size;

    @Column(name = "speed_ft")
    private Integer speedFt;

    @Column(name = "ability_score_increases", length = 500)
    private String abilityScoreIncreases;



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

    /** Tracos da especie: cada um e um {@link Feature} (categoria "species"), como as features de classe. */
    @jakarta.persistence.ManyToMany
    @jakarta.persistence.JoinTable(name = "species_traits",
            joinColumns = @jakarta.persistence.JoinColumn(name = "species_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "feature_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    private java.util.List<Feature> traits = new java.util.ArrayList<>();

    protected Species() {}

    public Species(String name, Source source) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome da espécie");
        }
        if (source == null) {
            throw new IllegalArgumentException("Falta a fonte da espécie");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
    }

    public void update(String size, Integer speedFt, String abilityScoreIncreases) {
        this.size = size;
        this.speedFt = speedFt;
        this.abilityScoreIncreases = abilityScoreIncreases;
    }

    public void replaceTraits(java.util.List<Feature> traits) {
        this.traits.clear();
        this.traits.addAll(traits);
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
