package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.inventory.Money;
import com.vttcaseiro.model.Shop;

/** Comprar e vender numa loja: mexe no dinheiro e no inventario da ficha e no estoque da loja. */
public final class ShopService {
    private ShopService() {
    }

    /** Compra: devolve null se deu certo, ou o motivo de nao dar (sem dinheiro, sem estoque...). */
    public static String buy(Shop shop, String itemName, int qty, CharacterSheet sheet) {
        if (qty <= 0) return "Quantidade inválida.";
        Shop.Item item = shop.findItem(itemName).orElse(null);
        if (item == null) return "A loja não tem " + itemName + ".";
        if (!item.inStock(qty)) return "Só tem " + item.getStock() + " de " + item.getName() + " na loja.";
        long total = item.getPriceCopper() * qty;
        Money money = sheet.getInventory().getMoney();
        if (money.totalInCopper() < total) {
            return sheet.getName() + " não tem dinheiro: " + coins(total) + " (tem " + coins(money.totalInCopper()) + ").";
        }
        money.pay(total);
        sheet.getInventory().addItem(item.getName(), qty, item.getWeightLb(), item.getPriceCopper());
        if (item.getStock() > 0) item.setStock(item.getStock() - qty);
        return null;
    }

    /**
     * Venda: a loja paga {@link Shop#getBuyBackRate()} do preco dela (ou do valor do item na ficha, se ela nao vende
     * aquele item). Devolve null se deu certo, ou o motivo.
     */
    public static String sell(Shop shop, String itemName, int qty, CharacterSheet sheet) {
        if (qty <= 0) return "Quantidade inválida.";
        InventoryItem have = sheet.getInventory().findByName(itemName).orElse(null);
        if (have == null || have.getQuantity() < qty) return sheet.getName() + " não tem " + qty + " " + itemName + ".";
        long unit = sellPrice(shop, have);
        if (unit <= 0) return "A loja não sabe quanto vale " + itemName + " (sem preço).";
        sheet.getInventory().removeItem(have.getName(), qty);
        addCoins(sheet.getInventory().getMoney(), unit * qty);
        shop.findItem(have.getName()).ifPresent(i -> { if (i.getStock() >= 0) i.setStock(i.getStock() + qty); });
        return null;
    }

    /** Quanto a loja paga por uma unidade. */
    public static long sellPrice(Shop shop, InventoryItem have) {
        long base = shop.findItem(have.getName()).map(Shop.Item::getPriceCopper).orElse(have.getUnitValueCopper());
        return Math.round(base * shop.getBuyBackRate());
    }

    /** Soma cobre na bolsa em moedas grandes (po, pp, pc). */
    static void addCoins(Money money, long copper) {
        long gp = copper / 100;
        long sp = (copper % 100) / 10;
        long cp = copper % 10;
        if (gp > 0) money.add(Currency.GOLD, (int) Math.min(Integer.MAX_VALUE, gp));
        if (sp > 0) money.add(Currency.SILVER, (int) sp);
        if (cp > 0) money.add(Currency.COPPER, (int) cp);
    }

    /** 1550 -> "15 po 5 pp"; 0 -> "de graça". */
    public static String coins(long copper) {
        if (copper <= 0) return "de graça";
        long gp = copper / 100, sp = (copper % 100) / 10, cp = copper % 10;
        StringBuilder b = new StringBuilder();
        if (gp > 0) b.append(gp).append(" po");
        if (sp > 0) b.append(b.length() > 0 ? " " : "").append(sp).append(" pp");
        if (cp > 0) b.append(b.length() > 0 ? " " : "").append(cp).append(" pc");
        return b.toString();
    }

    /** "2,5" po -> 250 pc. */
    public static long goldToCopper(String gold) {
        try {
            return Math.max(0, Math.round(Double.parseDouble(gold.strip().replace(',', '.')) * 100));
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
