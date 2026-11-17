package com.vttcaseiro.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumEntryType;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.content.Feature;
import com.vttcaseiro.content.FeatureType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Conteudo do 5e.tools indo pra ficha, com o compendio 2024 real que vem no programa. */
class CompendiumToSheetTest {

    private static Compendium seed() throws Exception {
        try (InputStream in = CompendiumToSheetTest.class.getResourceAsStream("/seed/compendio.json")) {
            assertNotNull(in, "seed/compendio.json precisa estar no classpath");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<CompendiumEntry> entries = new Gson().fromJson(json, new TypeToken<List<CompendiumEntry>>() { }.getType());
            return new Compendium(entries);
        }
    }

    @Test
    void magiaDoCompendioViraLinhaDaTabelaComNivelTempoAlcanceEComponentes() throws Exception {
        Compendium c = seed();
        CompendiumEntry fireball = c.findByTypeAndName(CompendiumEntryType.SPELL, "Fireball").orElseThrow();
        PreparedSpell spell = CompendiumToSheet.spellFromEntry(fireball);
        assertEquals(3, spell.getLevel());
        assertFalse(spell.getCastingTime().isBlank());
        assertFalse(spell.getRange().isBlank());
        assertTrue(spell.isMaterialRequired(), "Fireball tem componente M");
        CompendiumEntry bless = c.findByTypeAndName(CompendiumEntryType.SPELL, "Bless").orElseThrow();
        assertTrue(CompendiumToSheet.spellFromEntry(bless).isConcentration());
    }

    @Test
    void antecedenteEntraComOTalentoQueConcede() throws Exception {
        Compendium c = seed();
        CompendiumEntry sage = c.findByTypeAndName(CompendiumEntryType.BACKGROUND, "Sage").orElseThrow();
        CharacterSheet sheet = new CharacterSheet("X");
        String msg = CompendiumToSheet.addEntry(sheet, sage, c);
        assertEquals("Sage", sheet.getBackground());
        assertTrue(sheet.getTraits().size() >= 2, msg); // antecedente + talento de origem
        assertFalse(sheet.traitsInCategory("feat").isEmpty(), msg);
        // Repetir nao duplica
        CompendiumToSheet.addEntry(sheet, sage, c);
        assertEquals(sheet.getTraits().stream().map(t -> t.getName().toLowerCase()).distinct().count(), sheet.getTraits().size());
    }

    @Test
    void itemVaiProEquipamentoComPesoEValor() throws Exception {
        Compendium c = seed();
        CompendiumEntry rope = c.byType(CompendiumEntryType.ITEM).stream()
                .filter(e -> e.getItemValueGp() != null && e.getItemValueGp() > 0).findFirst().orElseThrow();
        CharacterSheet sheet = new CharacterSheet("X");
        CompendiumToSheet.addEntry(sheet, rope, c);
        assertEquals(1, sheet.getInventory().getItems().size());
        assertEquals(Math.round(rope.getItemValueGp() * 100), sheet.getInventory().getItems().get(0).getUnitValueCopper());
    }

    @Test
    void aplicarClasseDefineDadoSalvaguardasConjuracaoEAcrescentaSoOQueFalta() {
        DndClass wizard = new DndClass("Wizard", "XPHB", 1, 6, 3);
        wizard.setSpellcastingAbility("int");
        wizard.getSavingThrows().addAll(List.of("int", "wis"));
        wizard.getWeaponProficiencies().add("simple");
        wizard.getSkillChoices().addAll(List.of("arcana", "history", "animal handling"));
        wizard.setSkillChoiceCount(2);
        wizard.addFeature(new Feature("Spellcasting", "Voce conjura.", "class", FeatureType.values()[0], "XPHB"), 1, null);
        wizard.addFeature(new Feature("Arcane Recovery", "Recupera slots.", "class", FeatureType.values()[0], "XPHB"), 1, null);
        wizard.addFeature(new Feature("Scholar", "Especialista.", "class", FeatureType.values()[0], "XPHB"), 2, null);

        assertEquals(List.of(Skill.ARCANA, Skill.HISTORY, Skill.ANIMAL_HANDLING), CompendiumToSheet.skillChoices(wizard));

        CharacterSheet sheet = new CharacterSheet("Elora");
        sheet.setLevel(1);
        var r1 = CompendiumToSheet.applyClass(sheet, wizard, null, List.of(Skill.ARCANA));
        assertEquals("d6", sheet.getHitDie());
        assertEquals(Ability.INTELLIGENCE, sheet.getSpellcastingAbility());
        assertTrue(sheet.isSavingThrowProficient(Ability.WISDOM));
        assertTrue(sheet.isSkillProficient(Skill.ARCANA));
        assertEquals(2, r1.traitsAdded());

        sheet.setLevel(2);
        var r2 = CompendiumToSheet.applyClass(sheet, wizard, null, List.of());
        assertEquals(1, r2.traitsAdded(), "subiu de nivel: so a caracteristica nova");
        assertEquals("simple", sheet.getWeaponProficiencies());
    }
}
