package com.vttcaseiro.service;

import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.Inventory;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.model.Campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parcelas de tesouro e itens nao identificados (sem tela: o Main e os testes chamam).
 * <p>
 * Item nao identificado: na ficha do jogador entra so o nome "misterioso" e um codigo ({@code secretId}); o nome
 * verdadeiro fica em {@link Campaign#getSecrets()}, que so o mestre tem. Ao identificar, o mestre troca o nome na
 * ficha. Assim o nome real nunca viaja pro jogador antes da hora, nem escondido nos dados.
 */
public final class TreasureService {

    private TreasureService() {
    }

    /** Destino nomeado (ficha de um personagem, ou o inventario do grupo). */
    public record Target(String label, Inventory inventory) {
    }

    /**
     * Entrega a parcela: itens pra {@code itemsTo}; moedas divididas igualmente entre {@code coinsTo} (o resto da
     * divisao fica com o primeiro); XP dividida entre os jogadores visiveis da campanha, se {@code splitXp}.
     *
     * @return resumo pro chat
     */
    public static String deliver(Campaign campaign, Campaign.Parcel parcel, Target itemsTo, List<Target> coinsTo,
                                 boolean splitXp, String date) {
        List<String> got = new ArrayList<>();
        for (Campaign.ParcelItem pi : parcel.items) {
            if (pi.name == null || pi.name.isBlank()) continue;
            int qty = Math.max(1, pi.quantity);
            if (pi.unidentified) {
                Campaign.Secret secret = new Campaign.Secret();
                secret.trueName = pi.name.trim();
                secret.publicName = pi.publicName == null || pi.publicName.isBlank() ? "Item desconhecido" : pi.publicName.trim();
                secret.notes = pi.notes == null ? "" : pi.notes;
                secret.valueCopper = pi.valueCopper;
                secret.holder = itemsTo.label();
                campaign.getSecrets().add(secret);
                // valor 0 ate identificar (o preco entregaria o que o item e)
                InventoryItem item = new InventoryItem(secret.publicName, qty, pi.weightLb, 0);
                item.setSecretId(secret.id);
                itemsTo.inventory().addUnique(item);
                got.add(qty + "x " + secret.publicName);
            } else {
                itemsTo.inventory().addItem(pi.name.trim(), qty, pi.weightLb, pi.valueCopper);
                got.add(qty + "x " + pi.name.trim());
            }
        }
        List<Target> payees = coinsTo == null || coinsTo.isEmpty() ? List.of(itemsTo) : coinsTo;
        Map<Currency, Integer> coins = new java.util.LinkedHashMap<>();
        coins.put(Currency.PLATINUM, parcel.pp);
        coins.put(Currency.GOLD, parcel.gp);
        coins.put(Currency.ELECTRUM, parcel.ep);
        coins.put(Currency.SILVER, parcel.sp);
        coins.put(Currency.COPPER, parcel.cp);
        List<String> coinText = new ArrayList<>();
        for (Map.Entry<Currency, Integer> e : coins.entrySet()) {
            int total = Math.max(0, e.getValue());
            if (total == 0) continue;
            int each = total / payees.size();
            int rest = total % payees.size();
            for (int i = 0; i < payees.size(); i++) {
                int amount = each + (i == 0 ? rest : 0);
                if (amount > 0) payees.get(i).inventory().getMoney().add(e.getKey(), amount);
            }
            coinText.add(total + " " + coinName(e.getKey()));
        }
        String xpText = "";
        if (splitXp && parcel.xp > 0) {
            List<Campaign.PlayerRecord> players = campaign.getPlayers().stream().filter(p -> p.visible).toList();
            if (!players.isEmpty()) {
                double each = Math.floor((double) parcel.xp / players.size());
                for (Campaign.PlayerRecord p : players) {
                    p.xp.add(new Campaign.Entry(date, each, parcel.name.isBlank() ? "Tesouro" : parcel.name));
                }
                xpText = (long) each + " XP pra cada um (" + players.size() + " jogadores)";
            }
        }
        parcel.delivered = true;
        parcel.deliveredTo = itemsTo.label() + (payees.size() > 1 ? " (moedas divididas entre " + payees.size() + ")" : "");
        StringBuilder sb = new StringBuilder("Tesouro");
        if (!parcel.name.isBlank()) sb.append(" \"").append(parcel.name).append('"');
        sb.append(" entregue a ").append(itemsTo.label()).append(':');
        if (!got.isEmpty()) sb.append(' ').append(String.join(", ", got)).append('.');
        if (!coinText.isEmpty()) {
            sb.append(" Moedas: ").append(String.join(", ", coinText));
            if (payees.size() > 1) sb.append(" (divididas entre ").append(payees.size()).append(")");
            sb.append('.');
        }
        if (!xpText.isEmpty()) sb.append(' ').append(xpText).append('.');
        return sb.toString();
    }

    /**
     * Identifica o item em todos os inventarios dados: troca o nome misterioso pelo verdadeiro e poe o valor.
     *
     * @return quantos empilhamentos foram trocados
     */
    public static int identify(Campaign campaign, String secretId, List<Inventory> inventories) {
        Campaign.Secret s = campaign.findSecret(secretId).orElse(null);
        if (s == null) return 0;
        int n = 0;
        for (Inventory inv : inventories) {
            if (inv == null) continue;
            List<InventoryItem> found = inv.getItems().stream().filter(i -> secretId.equals(i.getSecretId())).toList();
            for (InventoryItem i : found) {
                inv.getItems().remove(i);
                InventoryItem real = new InventoryItem(s.trueName, i.getQuantity(), i.getUnitWeightLb(), s.valueCopper);
                inv.addUnique(real);
                n++;
            }
        }
        s.identified = true;
        return n;
    }

    /** Algum desses inventarios tem o item? (pra saber de quem e a ficha a gravar) */
    public static boolean holds(Inventory inv, String secretId) {
        return inv != null && inv.getItems().stream().anyMatch(i -> secretId.equals(i.getSecretId()));
    }

    static String coinName(Currency c) {
        return switch (c) {
            case PLATINUM -> "PL";
            case GOLD -> "PO";
            case ELECTRUM -> "PE";
            case SILVER -> "PP";
            case COPPER -> "PC";
        };
    }
}
