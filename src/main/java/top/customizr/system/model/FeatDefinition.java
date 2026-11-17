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

/** Talento importado do 5etools (FeatDefinition do diagrama). Pre-requisitos guardados como texto por enquanto. */
@Entity
@Table(name = "feat_definitions", uniqueConstraints = @UniqueConstraint(
        name = "uk_feat_definitions_name_source", columnNames = {"name", "source_id"}))
@Getter
public class FeatDefinition {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(length = 40)
    private String category;

    @Column(length = 1000)
    private String prerequisite;

    @Column(nullable = false)
    private boolean repeatable;



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

    protected FeatDefinition() {}

    public FeatDefinition(String name, Source source) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Feat name is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Feat source is required");
        }
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.source = source;
    }

    public void update(String category, String prerequisite, boolean repeatable) {
        this.category = category;
        this.prerequisite = prerequisite;
        this.repeatable = repeatable;
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
