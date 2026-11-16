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


    @Column(columnDefinition = "TEXT")
    private String description;

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

    public void update(String category, String prerequisite, boolean repeatable, String description) {
        this.category = category;
        this.prerequisite = prerequisite;
        this.repeatable = repeatable;
        this.description = description;
    }
}
