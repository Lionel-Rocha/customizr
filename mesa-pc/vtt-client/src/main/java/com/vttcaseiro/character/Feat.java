package com.vttcaseiro.character;

import com.vttcaseiro.character.prerequisite.Prerequisite;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Um feat (talento), com uma lista de {@link Prerequisite}. Separado de
 * {@link CharacterTrait} de proposito: trait/feature/magia/ataque sao so
 * "nome + descricao + clicar manda pro chat"; feat tem essa MESMA
 * necessidade (tambem e clicavel, tambem posta no chat -- ver
 * {@link #toChatText()}) mas ALEM disso precisa saber se o personagem
 * pode pegar ele ou nao, o que exige a logica de pre-requisito.
 */
public class Feat {
    private String name;
    private String description;
    private List<Prerequisite> prerequisites = new ArrayList<>();

    public Feat() {
    }

    public Feat(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Prerequisite> getPrerequisites() { return prerequisites; }

    public void addPrerequisite(Prerequisite prerequisite) {
        prerequisites.add(prerequisite);
    }

    public boolean isAvailableFor(CharacterSheet sheet) {
        return prerequisites.stream().allMatch(p -> p.isMetBy(sheet));
    }

    /** Lista (em texto, pronta pra mostrar) dos pre-requisitos que o
     * personagem AINDA NAO cumpre -- vazia se {@link #isAvailableFor}
     * for true. */
    public List<String> unmetPrerequisites(CharacterSheet sheet) {
        return prerequisites.stream()
                .filter(p -> !p.isMetBy(sheet))
                .map(Prerequisite::describe)
                .collect(Collectors.toList());
    }

    public String toChatText() {
        if (description == null || description.isBlank()) {
            return name;
        }
        return name + ": " + description;
    }
}
