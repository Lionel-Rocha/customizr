package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loja do mestre: itens com preco (em pecas de cobre) e estoque (-1 = sem fim). Aberta = jogadores veem e compram. */
public class Shop {
    public static class Item {
        private String name = "";
        private long priceCopper;
        private int stock = -1;
        private String description = "";
        private double weightLb;

        public Item() {
        }

        public Item(String name, long priceCopper, int stock, String description) {
            setName(name);
            this.priceCopper = Math.max(0, priceCopper);
            this.stock = stock;
            setDescription(description);
        }

        public String getName() { return name == null ? "" : name; }
        public void setName(String name) { this.name = name == null ? "" : name.strip(); }
        public long getPriceCopper() { return Math.max(0, priceCopper); }
        public void setPriceCopper(long cp) { this.priceCopper = Math.max(0, cp); }
        /** -1 = sem fim. */
        public int getStock() { return stock < 0 ? -1 : stock; }
        public void setStock(int stock) { this.stock = stock < 0 ? -1 : stock; }
        public String getDescription() { return description == null ? "" : description; }
        public void setDescription(String d) { this.description = d == null ? "" : d.strip(); }
        public double getWeightLb() { return Math.max(0, weightLb); }
        public void setWeightLb(double w) { this.weightLb = w; }
        public boolean inStock(int qty) { return getStock() < 0 || getStock() >= qty; }
    }

    private String id = UUID.randomUUID().toString();
    private String name = "Loja";
    private String description = "";
    private boolean open;
    /** Quanto a loja paga quando o jogador vende (fracao do preco): 0,5 = metade. */
    private double buyBackRate = 0.5;
    private List<Item> items = new ArrayList<>();

    public Shop() {
    }

    public Shop(String name) {
        setName(name);
    }

    public String getId() { return id; }
    public String getName() { return name == null || name.isBlank() ? "Loja" : name; }
    public void setName(String name) { this.name = name == null ? "" : name.strip(); }
    public String getDescription() { return description == null ? "" : description; }
    public void setDescription(String d) { this.description = d == null ? "" : d.strip(); }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public double getBuyBackRate() { return buyBackRate <= 0 || buyBackRate > 1 ? 0.5 : buyBackRate; }
    public void setBuyBackRate(double r) { this.buyBackRate = r; }

    public List<Item> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }

    public java.util.Optional<Item> findItem(String name) {
        return getItems().stream().filter(i -> i.getName().equalsIgnoreCase(name == null ? "" : name.strip())).findFirst();
    }
}
