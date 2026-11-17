package com.vttcaseiro.character;

/**
 * Uma entrada clicavel da ficha: um trait de especie, um feat, uma feature
 * de classe, uma magia conhecida, um ataque -- tudo cabe aqui, o campo
 * {@code category} e so pra agrupar/mostrar na tela com uma cor ou aba
 * diferente (mesma ideia que a barra de cor por categoria do compendio do
 * Firecast). O clique nessa entrada manda {@code name} + {@code description}
 * pro chat (ver CharacterSheet e a tela que ainda vamos construir em cima
 * disso).
 */
public class CharacterTrait {
    private String name;
    private String description;
    private String category; // "trait", "feat", "feature", "spell", "attack" -- livre, sem enum de proposito

    public CharacterTrait() {
    }

    public CharacterTrait(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Custo pra usar (poderes de Tormenta20 / Ordem): ex. 2 PM. 0 = sem custo. */
    private int cost;
    private String costResource;

    public int getCost() { return Math.max(0, cost); }
    public void setCost(int cost) { this.cost = Math.max(0, cost); }
    public String getCostResource() { return costResource == null || costResource.isBlank() ? null : costResource; }
    public void setCostResource(String costResource) { this.costResource = costResource; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    /** Texto pronto pra postar no chat quando essa entrada e clicada. */
    public String toChatText() {
        if (description == null || description.isBlank()) {
            return name;
        }
        return name + ": " + description;
    }
}
