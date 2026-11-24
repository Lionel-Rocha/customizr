package com.vttcaseiro.library;

import com.vttcaseiro.app.Keybindings;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.model.CardDeck;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cofre de personagens, baralhos de cartas e atalhos do teclado. */
class VaultDecksKeysTest {

    @Test
    void vaultKeepsTheSheetAndItsImagesBetweenMesas(@TempDir Path dir) throws Exception {
        CharacterVault vault = new CharacterVault(dir.resolve("cofre"));
        assertTrue(vault.list().isEmpty());
        CharacterSheet lyra = new CharacterSheet("Lyra");
        lyra.setLevel(3);
        String portrait = "a".repeat(64) + ".png";
        lyra.setPortraitImageAssetId(portrait);
        byte[] png = {1, 2, 3};
        Path folder = vault.save(lyra, id -> id.equals(portrait) ? png : null);
        assertTrue(vault.contains(lyra.getId()));

        lyra.setLevel(4); // subiu de nivel: o cofre acompanha, na mesma pasta
        assertEquals(folder, vault.save(lyra, id -> png));
        List<CharacterVault.Entry> entries = vault.list();
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).summary().contains("nível 4"));

        Map<String, byte[]> images = new LinkedHashMap<>();
        CharacterSheet back = vault.load(entries.get(0), images);
        assertEquals(lyra.getId(), back.getId(), "mesmo personagem (mesmo id) em qualquer mesa");
        assertArrayEquals(png, images.get(portrait), "retrato vai junto");

        vault.delete(entries.get(0));
        assertFalse(vault.contains(lyra.getId()));
    }

    @Test
    void decksDrawEachCardOnceUntilShuffled() {
        CardDeck deck = CardDeck.standard();
        assertEquals(54, deck.getCards().size());
        assertEquals(54, deck.remaining());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 54; i++) assertTrue(seen.add(deck.draw().getId()), "nunca repete antes de embaralhar");
        assertNull(deck.draw(), "monte vazio");
        deck.shuffle(new Random(1));
        assertEquals(54, deck.remaining(), "embaralhar junta todas");

        CardDeck tarot = CardDeck.tarot();
        assertEquals(22, tarot.getCards().size());
        assertTrue(tarot.getCards().stream().anyMatch(c -> c.getName().equals("XXI - O Mundo")));

        CardDeck road = CardDeck.fromLines("Estrada", "Emboscada: 1d4 bandidos\n\nMercador perdido\n");
        assertEquals(2, road.getCards().size());
        assertTrue(road.getCards().stream().anyMatch(c -> c.getName().equals("Emboscada") && c.getText().equals("1d4 bandidos")));
    }

    @Test
    void keybindingDefaults() {
        assertEquals(KeyCode.DIGIT1, Keybindings.defaultKey("tool.1"));
        assertEquals(KeyCode.T, Keybindings.defaultKey("target"));
        assertTrue(Keybindings.reserved(KeyCode.ESCAPE), "Esc e fixo");
        assertFalse(Keybindings.reserved(KeyCode.Q));
        assertEquals("—", Keybindings.label(null));
        if (Keybindings.keyFor("tool.1") == KeyCode.DIGIT1) {
            assertEquals("tool.1", Keybindings.actionFor(KeyCode.NUMPAD1), "teclado numerico vale como os numeros");
        }
    }
}
