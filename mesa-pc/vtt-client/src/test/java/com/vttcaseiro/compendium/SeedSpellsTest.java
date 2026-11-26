package com.vttcaseiro.compendium;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.content.EntriesRenderer;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.service.CompendiumService;
import com.vttcaseiro.service.CompendiumToSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O compendio 2024 embutido, regerado do spells-xphb.json do 5e.tools. */
class SeedSpellsTest {

    private static String seed() throws Exception {
        try (InputStream in = SeedSpellsTest.class.getResourceAsStream("/seed/compendio.json")) {
            // o conteudo dos livros fica fora do repositorio publico: sem ele (ex.: no GitHub), o teste pula
            org.junit.jupiter.api.Assumptions.assumeTrue(in != null, "sem seed/compendio.json (conteudo local)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<CompendiumEntry> entries() throws Exception {
        return new Gson().fromJson(seed(), new TypeToken<List<CompendiumEntry>>() { }.getType());
    }

    private static CompendiumEntry spell(List<CompendiumEntry> all, String name) {
        return all.stream().filter(e -> e.getType() == CompendiumEntryType.SPELL && name.equals(e.getName()))
                .findFirst().orElseThrow();
    }

    @Test
    void magiasDoSeedTemAumentoPorNivelCertoESemTagCrua() throws Exception {
        String raw = seed();
        assertFalse(raw.contains("{@"), "nenhuma tag do 5e.tools sobrando no texto");
        assertFalse(raw.contains("|||"), "nada de {@chance} mal renderizado");
        List<CompendiumEntry> all = entries();
        assertEquals(391, all.stream().filter(e -> e.getType() == CompendiumEntryType.SPELL).count());

        PreparedSpell fireball = CompendiumToSheet.spellFromEntry(spell(all, "Fireball"));
        assertEquals("8d6", fireball.getDice());
        assertEquals("1d6", fireball.getDicePerLevel(), "Bola de Fogo sobe 1d6 por nivel, nao 8d6");
        assertEquals("10d6", fireball.diceForSlot(5));

        PreparedSpell shatter = CompendiumToSheet.spellFromEntry(spell(all, "Shatter"));
        assertEquals("1d8", shatter.getDicePerLevel());
        assertTrue(spell(all, "Augury").getFullText().contains("Weal | Good"), "tabela vem inteira");
        assertTrue(spell(all, "Divine Smite").getSpellCastingTime().endsWith("Unarmed Strike"));
    }

    @Test
    void renderizadorDeChanceECondicao() {
        assertEquals("cumulative 25% chance",
                EntriesRenderer.renderInline("cumulative {@chance 25|||Random reading!|Regular reading} chance"));
    }

    @Test
    void recarregarOConteudoCorrigeTextoAntigoSemTrocarOId(@TempDir Path dir) throws Exception {
        CompendiumService service = new CompendiumService(new LibraryStorage(dir));
        CompendiumEntry old = new CompendiumEntry();
        old.setId("id-antigo");
        old.setType(CompendiumEntryType.SPELL);
        old.setName("Fireball");
        old.setSource("XPHB");
        old.setFullText("Using a Higher-Level Spell Slot: The damage increases by 8d6 for each spell slot level above 3.");
        service.addEntries(List.of(old));

        service.importCompendiumJson(seed());
        assertTrue(service.lastRefreshedCount() >= 1);
        CompendiumEntry now = service.getCompendium().findById("id-antigo").orElseThrow();
        assertTrue(now.getFullText().contains("increases by 1d6"), now.getFullText());
        assertEquals(1, service.getCompendium().getEntries().stream()
                .filter(e -> "Fireball".equals(e.getName())).count(), "nao duplica");

        service.importCompendiumJson(seed());
        assertEquals(0, service.lastRefreshedCount(), "segunda vez nao tem nada a corrigir");
    }
}
