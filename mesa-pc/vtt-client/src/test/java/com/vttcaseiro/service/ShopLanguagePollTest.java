package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.model.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Loja (comprar/vender), idioma embaralhado e as partes da enquete. */
class ShopLanguagePollTest {

    @Test
    void buyingAndSellingMovesMoneyItemsAndStock() {
        Shop shop = new Shop("Ferreiro");
        shop.getItems().add(new Shop.Item("Longsword", 1500, 2, ""));
        shop.getItems().add(new Shop.Item("Rope", 100, -1, ""));
        CharacterSheet lyra = new CharacterSheet("Lyra");
        lyra.getInventory().getMoney().add(Currency.GOLD, 20);

        assertNull(ShopService.buy(shop, "Longsword", 1, lyra));
        assertEquals(500, lyra.getInventory().getMoney().totalInCopper(), "20 po - 15 po");
        assertEquals(1, lyra.getInventory().findByName("Longsword").orElseThrow().getQuantity());
        assertEquals(1, shop.findItem("Longsword").orElseThrow().getStock(), "estoque baixou");

        assertNotNull(ShopService.buy(shop, "Longsword", 1, lyra), "sem dinheiro (5 po pra 15)");
        assertNotNull(ShopService.buy(shop, "Longsword", 5, lyra), "sem estoque");
        assertNull(ShopService.buy(shop, "Rope", 3, lyra), "estoque sem fim");
        assertEquals(200, lyra.getInventory().getMoney().totalInCopper());

        assertNull(ShopService.sell(shop, "Longsword", 1, lyra));
        assertEquals(200 + 750, lyra.getInventory().getMoney().totalInCopper(), "a loja paga metade");
        assertTrue(lyra.getInventory().findByName("Longsword").isEmpty());
        assertEquals(2, shop.findItem("Longsword").orElseThrow().getStock(), "voltou pro estoque");
        assertNotNull(ShopService.sell(shop, "Shield", 1, lyra), "nao tem pra vender");

        assertEquals("15 po 5 pp", ShopService.coins(1550));
        assertEquals(250, ShopService.goldToCopper("2,5"));
    }

    @Test
    void languageScramblesTheSameWayAndKnowsAccents() {
        String a = LanguageScramble.scramble("Olá, amigo! O amigo voltou.", "Elvish");
        assertEquals("Olá, amigo! O amigo voltou.".length(), a.length(), "mesmo tamanho");
        assertTrue(a.contains(", ") && a.endsWith("."), "pontuacao fica");
        assertNotEquals("Olá, amigo! O amigo voltou.", a);
        String[] w = a.split("[^\\p{L}]+");
        assertEquals(w[1], w[3], "a mesma palavra embaralha igual");
        assertNotEquals(a, LanguageScramble.scramble("Olá, amigo! O amigo voltou.", "Dwarvish"), "cada idioma de um jeito");
        assertTrue(LanguageScramble.knows(List.of("Common, Élfico"), "elfico"));
        assertFalse(LanguageScramble.knows(List.of("Common"), "Elvish"));
    }

    @Test
    void pollPartsAcceptPipesOrQuotes() {
        assertEquals(List.of("Por onde?", "Floresta", "Estrada"), ChatCommands.pollParts("Por onde? | Floresta | Estrada"));
        assertEquals(List.of("", "Floresta", "Rio grande"), ChatCommands.pollParts("Floresta \"Rio grande\""));
        assertNull(ChatCommands.pollParts("sozinha"), "uma opcao so nao e enquete");
        assertEquals(ChatCommands.Type.LANGUAGE, ChatCommands.parse("/em Elvish Fujam!").type());
        assertEquals("Elvish", ChatCommands.parse("/em Elvish Fujam!").target());
        assertEquals(ChatCommands.Type.POLL, ChatCommands.parse("/votar A | B").type());
    }
}
