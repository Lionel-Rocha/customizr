package com.vttcaseiro.inventory;

/**
 * Um item guardado num inventario. {@code unitWeightLb} e o peso de UMA
 * unidade (em libras, padrao 5e); o peso total do empilhamento e
 * {@code unitWeightLb * quantity} -- calculado, nunca guardado solto,
 * pra nao arriscar os dois numeros saindo de sincronia depois de uma
 * mudanca de quantidade.
 */
public class InventoryItem {
    private String name;
    private int quantity;
    private double unitWeightLb;
    private long unitValueCopper;
    /**
     * Item nao identificado: o nome guardado aqui e o que o jogador ve ("espada de lamina avermelhada"); o nome
     * verdadeiro fica so com o mestre (Campaign.secrets, por este id). null = item normal/identificado.
     */
    private String secretId;

    public InventoryItem() {
    }

    public InventoryItem(String name, int quantity, double unitWeightLb, long unitValueCopper) {
        this.name = name;
        this.quantity = quantity;
        this.unitWeightLb = unitWeightLb;
        this.unitValueCopper = unitValueCopper;
    }

    public String getName() { return name; }

    public String getSecretId() { return secretId == null || secretId.isBlank() ? null : secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public boolean isUnidentified() { return getSecretId() != null; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = Math.max(0, quantity); }

    public double getUnitWeightLb() { return unitWeightLb; }
    public void setUnitWeightLb(double unitWeightLb) { this.unitWeightLb = unitWeightLb; }

    public long getUnitValueCopper() { return unitValueCopper; }
    public void setUnitValueCopper(long unitValueCopper) { this.unitValueCopper = unitValueCopper; }

    public double totalWeightLb() {
        return unitWeightLb * quantity;
    }

    public long totalValueCopper() {
        return unitValueCopper * quantity;
    }
}
