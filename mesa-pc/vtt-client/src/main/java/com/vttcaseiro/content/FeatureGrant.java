package com.vttcaseiro.content;

/**
 * "Esta Feature e concedida neste nivel." No projeto dela isso e dois
 * tipos diferentes (ClassFeature e SubclassFeature), porque cada um
 * aponta pra uma tabela diferente (dnd_classes ou subclasses) no banco.
 * Aqui nao tem banco nem relacionamento de verdade -- DndClass e
 * Subclass so guardam uma {@code List<FeatureGrant>} cada um direto, 
 * entao uma classe so serve pros dois casos.
 */
public class FeatureGrant {
    private Feature feature;
    private int level;
    private String choiceGroup;

    public FeatureGrant() {
    }

    /** @throws IllegalArgumentException se o nivel nao for de 1 a 20 */
    public FeatureGrant(Feature feature, int level, String choiceGroup) {
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("O nivel da feature precisa ser de 1 a 20.");
        }
        this.feature = feature;
        this.level = level;
        this.choiceGroup = choiceGroup;
    }

    public Feature getFeature() { return feature; }
    public void setFeature(Feature feature) { this.feature = feature; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getChoiceGroup() { return choiceGroup; }
    public void setChoiceGroup(String choiceGroup) { this.choiceGroup = choiceGroup; }
}
