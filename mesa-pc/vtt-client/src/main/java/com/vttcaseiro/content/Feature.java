package com.vttcaseiro.content;

import java.util.UUID;

/** Uma feature (de classe ou subclasse) -- "Rage", "Spellcasting",
 * "Arcane Recovery"... {@code category} e "class" ou "subclass", so pra
 * saber de onde veio quando aparecer solta numa lista. */
public class Feature {
    private String id;
    private String name;
    private String description;
    private String category;
    private FeatureType type;
    private String sourceCode;

    public Feature() {
        this.id = UUID.randomUUID().toString();
    }

    public Feature(String name, String description, String category, FeatureType type, String sourceCode) {
        this();
        this.name = name;
        this.description = description;
        this.category = category;
        this.type = type;
        this.sourceCode = sourceCode;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public FeatureType getType() { return type; }
    public void setType(FeatureType type) { this.type = type; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
}
