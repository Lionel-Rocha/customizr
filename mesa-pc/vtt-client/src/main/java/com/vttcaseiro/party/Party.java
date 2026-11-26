package com.vttcaseiro.party;

import com.vttcaseiro.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O grupo de personagens de uma mesa. Hoje existe uma Party por mesa (ver
 * MesaRepository.saveParty/loadParty) -- o suficiente pra maioria das
 * campanhas, que tem um grupo so. {@code sharedInventory} e onde vai o
 * loot do grupo (tesouro comum, itens que ninguem pegou pra si ainda);
 * cada personagem ainda tem o proprio {@link com.vttcaseiro.character.CharacterSheet#getInventory()}
 * separado -- um item pode ser movido de um pro outro com
 * {@link Inventory#transferItemTo}.
 */
public class Party {
    private String id;
    private String name = "Party";
    private List<String> characterIds = new ArrayList<>();
    private Inventory sharedInventory = new Inventory();

    public Party() {
        this.id = UUID.randomUUID().toString();
    }

    public Party(String name) {
        this();
        this.name = name;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getCharacterIds() { return characterIds; }

    public Inventory getSharedInventory() { return sharedInventory; }
    public void setSharedInventory(Inventory inventory) { this.sharedInventory = inventory == null ? new Inventory() : inventory; }

    public boolean hasMember(String characterId) {
        return characterIds.contains(characterId);
    }

    /** Nao adiciona duplicado -- entrar na party duas vezes nao faz nada
     * na segunda vez. */
    public void addMember(String characterId) {
        if (characterId != null && !characterIds.contains(characterId)) {
            characterIds.add(characterId);
        }
    }

    public void removeMember(String characterId) {
        characterIds.remove(characterId);
    }
}
