package com.vttcaseiro.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uma colecao de itens + dinheiro. A mesma classe serve tanto pro
 * inventario individual de uma ficha quanto pro inventario compartilhado
 * da Party -- a diferenca entre os dois e so QUEM guarda a referencia
 * (CharacterSheet guarda um; Party guarda outro), nao o tipo em si.
 * <p>
 * Itens com o mesmo nome (comparacao sem diferenciar maiusculas/minusculas)
 * se juntam num so registro com a quantidade somada, em vez de virar duas
 * linhas separadas -- "adicionar 3 Potion of Healing" duas vezes vira uma
 * linha com 6, nao duas linhas com 3 cada.
 */
public class Inventory {
    private List<InventoryItem> items = new ArrayList<>();
    private Money money = new Money();

    public List<InventoryItem> getItems() {
        return items;
    }

    public Money getMoney() {
        return money;
    }

    public Optional<InventoryItem> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(i -> name.equalsIgnoreCase(i.getName()))
                .findFirst();
    }

    /**
     * Junta com um item igual (mesmo nome e mesmo codigo de nao identificado) ou entra como item novo. Item nao
     * identificado nunca se junta com um item normal de mesmo nome.
     */
    public void addUnique(InventoryItem item) {
        if (item == null || item.getQuantity() <= 0) return;
        for (InventoryItem i : items) {
            if (i.getName() != null && i.getName().equalsIgnoreCase(item.getName())
                    && java.util.Objects.equals(i.getSecretId(), item.getSecretId())) {
                i.setQuantity(i.getQuantity() + item.getQuantity());
                return;
            }
        }
        items.add(item);
    }

    /** @throws IllegalArgumentException se a quantidade nao for positiva */
    public void addItem(String name, int quantity, double unitWeightLb, long unitValueCopper) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade precisa ser maior que zero.");
        }
        Optional<InventoryItem> existing = items.stream()
                .filter(i -> name != null && name.equalsIgnoreCase(i.getName()) && !i.isUnidentified()).findFirst();
        if (existing.isPresent()) {
            InventoryItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.add(new InventoryItem(name, quantity, unitWeightLb, unitValueCopper));
        }
    }

    /** Tira "quantity" unidades do item (procurado pelo nome). Se a
     * quantidade restante chegar a zero, o item sai da lista -- nao fica
     * um registro fantasma com quantidade 0.
     *
     * @return true se tinha o suficiente e a remocao foi feita; false se
     * o item nao existe ou nao tem quantidade suficiente (nesse caso nada muda) */
    public boolean removeItem(String name, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade precisa ser maior que zero.");
        }
        Optional<InventoryItem> existing = findByName(name);
        if (existing.isEmpty() || existing.get().getQuantity() < quantity) {
            return false;
        }
        InventoryItem item = existing.get();
        item.setQuantity(item.getQuantity() - quantity);
        if (item.getQuantity() == 0) {
            items.remove(item);
        }
        return true;
    }

    public double getTotalWeightLb() {
        double total = 0;
        for (InventoryItem item : items) {
            total += item.totalWeightLb();
        }
        return total;
    }

    public long getTotalValueCopper() {
        long total = money.totalInCopper();
        for (InventoryItem item : items) {
            total += item.totalValueCopper();
        }
        return total;
    }

    /** Move "quantity" unidades de um item desta inventario pra outra
     * (ex.: um jogador entregando um item pro inventario comum da Party).
     * Tudo ou nada -- se nao tiver o suficiente aqui, nada muda em
     * nenhum dos dois lados.
     *
     * @return true se a transferencia foi feita */
    public boolean transferItemTo(Inventory destination, String name, int quantity) {
        Optional<InventoryItem> existing = findByName(name);
        if (existing.isEmpty() || existing.get().getQuantity() < quantity) {
            return false;
        }
        InventoryItem source = existing.get();
        removeItem(name, quantity);
        if (source.isUnidentified()) {
            // item nao identificado leva o codigo junto (o mestre ainda consegue identificar onde ele estiver)
            InventoryItem moved = new InventoryItem(source.getName(), quantity, source.getUnitWeightLb(), source.getUnitValueCopper());
            moved.setSecretId(source.getSecretId());
            destination.addUnique(moved);
        } else {
            destination.addItem(source.getName(), quantity, source.getUnitWeightLb(), source.getUnitValueCopper());
        }
        return true;
    }
}
