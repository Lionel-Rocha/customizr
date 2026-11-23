package com.vttcaseiro.service;

import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.Inventory;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Parcelas de tesouro, itens nao identificados e o que fica so com o mestre. */
class TreasureServiceTest {

    private static Campaign.Parcel parcel() {
        Campaign.Parcel p = new Campaign.Parcel();
        p.name = "Baú do ogro";
        p.gp = 101;
        p.sp = 3;
        p.xp = 450;
        Campaign.ParcelItem rope = new Campaign.ParcelItem();
        rope.name = "Corda de cânhamo";
        rope.weightLb = 10;
        p.items.add(rope);
        Campaign.ParcelItem sword = new Campaign.ParcelItem();
        sword.name = "Espada Longa +1 da Chama";
        sword.valueCopper = 50_000;
        sword.unidentified = true;
        sword.publicName = "espada de lâmina avermelhada";
        sword.notes = "+1d6 de fogo";
        p.items.add(sword);
        return p;
    }

    private static Campaign campaignWith3Players() {
        Campaign c = new Campaign();
        for (String n : List.of("Ana", "Beto", "Caio")) {
            Campaign.PlayerRecord r = new Campaign.PlayerRecord();
            r.player = n;
            c.getPlayers().add(r);
        }
        return c;
    }

    @Test
    void deliverPutsItemsSplitsCoinsAndXpAndHidesTheTrueName() {
        Campaign c = campaignWith3Players();
        Inventory lyra = new Inventory();
        Inventory bran = new Inventory();
        Campaign.Parcel p = parcel();
        String summary = TreasureService.deliver(c, p,
                new TreasureService.Target("Lyra", lyra),
                List.of(new TreasureService.Target("Lyra", lyra), new TreasureService.Target("Bran", bran)),
                true, "23/11/2026");

        assertEquals(2, lyra.getItems().size());
        InventoryItem mystery = lyra.getItems().stream().filter(InventoryItem::isUnidentified).findFirst().orElseThrow();
        assertEquals("espada de lâmina avermelhada", mystery.getName(), "o jogador vê só o nome misterioso");
        assertEquals(0, mystery.getUnitValueCopper(), "o preço entregaria o que é");
        assertFalse(summary.contains("Chama"), "o chat não conta o nome verdadeiro: " + summary);
        assertTrue(summary.contains("espada de lâmina avermelhada"));

        assertEquals(51, lyra.getMoney().get(Currency.GOLD), "101 PO pra 2: o resto fica com o primeiro");
        assertEquals(50, bran.getMoney().get(Currency.GOLD));
        assertEquals(2, lyra.getMoney().get(Currency.SILVER));
        assertEquals(1, bran.getMoney().get(Currency.SILVER));
        assertEquals(150, c.getPlayers().get(0).totalXp(), "450 XP entre 3 jogadores");
        assertTrue(p.delivered);

        Campaign.Secret secret = c.getSecrets().get(0);
        assertEquals("Espada Longa +1 da Chama", secret.trueName);
        assertEquals("Lyra", secret.holder);
        assertEquals(secret.id, mystery.getSecretId());
    }

    @Test
    void identifyFollowsTheItemEvenAfterItMovesToTheGroup() {
        Campaign c = campaignWith3Players();
        Inventory lyra = new Inventory();
        Inventory group = new Inventory();
        TreasureService.deliver(c, parcel(), new TreasureService.Target("Lyra", lyra), List.of(), false, "");
        Campaign.Secret secret = c.getSecrets().get(0);

        // a jogadora passa o item pro grupo: o codigo vai junto
        assertTrue(lyra.transferItemTo(group, "espada de lâmina avermelhada", 1));
        assertTrue(TreasureService.holds(group, secret.id));
        assertFalse(TreasureService.holds(lyra, secret.id));

        assertEquals(1, TreasureService.identify(c, secret.id, List.of(lyra, group)));
        InventoryItem real = group.findByName("Espada Longa +1 da Chama").orElseThrow();
        assertFalse(real.isUnidentified());
        assertEquals(50_000, real.getUnitValueCopper(), "identificado: ganha o valor");
        assertTrue(secret.identified);
    }

    @Test
    void unidentifiedNeverMergesWithANormalItemOfTheSameName() {
        Inventory inv = new Inventory();
        inv.addItem("Poção", 1, 0.5, 5000);
        InventoryItem odd = new InventoryItem("Poção", 1, 0.5, 0);
        odd.setSecretId("abc");
        inv.addUnique(odd);
        assertEquals(2, inv.getItems().size());
        inv.addItem("Poção", 2, 0.5, 5000);
        assertEquals(3, inv.getItems().stream().filter(i -> !i.isUnidentified()).findFirst().orElseThrow().getQuantity());
    }

    @Test
    void parcelsSecretsAndEncountersNeverReachPlayers() {
        Campaign c = campaignWith3Players();
        c.getParcels().add(parcel());
        TreasureService.deliver(c, parcel(), new TreasureService.Target("Lyra", new Inventory()), List.of(), false, "");
        Campaign.PreparedEncounter enc = new Campaign.PreparedEncounter();
        enc.tokens.add(new TokenModel("Ogro", null));
        c.getEncounters().add(enc);
        Campaign pub = c.publicCopy();
        assertTrue(pub.getParcels().isEmpty());
        assertTrue(pub.getSecrets().isEmpty(), "nome verdadeiro não vai");
        assertTrue(pub.getEncounters().isEmpty(), "encontro preparado não vai");
        assertEquals(1, c.getEncounters().size(), "o original continua");
    }
}
