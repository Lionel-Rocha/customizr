package com.vttcaseiro.content;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Subclass {
    private String id;
    private String name;
    private String shortName;
    private String sourceCode;
    private int unlockLevel;
    private List<FeatureGrant> features = new ArrayList<>();

    public Subclass() {
        this.id = UUID.randomUUID().toString();
    }

    public Subclass(String name, String shortName, String sourceCode, int unlockLevel) {
        this();
        this.name = name;
        this.shortName = shortName;
        this.sourceCode = sourceCode;
        this.unlockLevel = unlockLevel;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public int getUnlockLevel() { return unlockLevel; }
    public void setUnlockLevel(int unlockLevel) { this.unlockLevel = unlockLevel; }

    public List<FeatureGrant> getFeatures() { return features; }

    public void addFeature(Feature feature, int level, String choiceGroup) {
        features.add(new FeatureGrant(feature, level, choiceGroup));
    }
}
