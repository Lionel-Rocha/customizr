package com.vttcaseiro.importer;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.inventory.Currency;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SheetImportTest {

    @Test
    void fichaDoFoundry() throws Exception {
        String json = """
                {"name": "Thalia", "type": "character",
                 "system": {
                   "abilities": {"str": {"value": 8}, "dex": {"value": 16, "proficient": 1}, "con": {"value": 14},
                                 "int": {"value": 12, "proficient": 1}, "wis": {"value": 10}, "cha": {"value": 13}},
                   "skills": {"ste": {"value": 2}, "acr": {"value": 1}, "ath": {"value": 0}},
                   "attributes": {"hp": {"value": 20, "max": 24, "temp": 3}, "ac": {"flat": 15},
                                  "movement": {"walk": 30, "units": "ft"}},
                   "details": {"alignment": "Chaotic Good", "biography": {"value": "<p>Orfa de <b>Waterdeep</b>.</p>"}},
                   "currency": {"gp": 42, "sp": 3},
                   "traits": {"languages": {"value": ["common", "elvish"]}}
                 },
                 "items": [
                   {"type": "class", "name": "Rogue", "system": {"levels": 3, "hitDice": "d8"}},
                   {"type": "subclass", "name": "Thief", "system": {}},
                   {"type": "race", "name": "Elf", "system": {}},
                   {"type": "weapon", "name": "Shortsword", "system": {"quantity": 1, "weight": 2,
                      "damage": {"parts": [["1d6 + @mod", "piercing"]]}}},
                   {"type": "spell", "name": "Mage Hand", "system": {"level": 0}},
                   {"type": "feat", "name": "Sneak Attack", "system": {"description": {"value": "<p>2d6 extra</p>"}}},
                   {"type": "loot", "name": "Rope", "system": {"quantity": 2, "weight": 5, "price": {"value": 1, "denomination": "gp"}}}
                 ]}""";
        SheetImport.Result r = SheetImport.fromJson(json);
        CharacterSheet s = r.sheet();
        assertEquals("Foundry VTT (dnd5e)", r.format());
        assertEquals("Thalia", s.getName());
        assertEquals(16, s.getDexterity());
        assertEquals(8, s.getStrength());
        assertTrue(s.isSavingThrowProficient(Ability.DEXTERITY));
        assertTrue(s.hasSkillExpertise(Skill.STEALTH));
        assertTrue(s.isSkillProficient(Skill.ACROBATICS));
        assertFalse(s.isSkillProficient(Skill.ATHLETICS));
        assertEquals(24, s.getHpMax());
        assertEquals(20, s.getHpCurrent());
        assertEquals(15, s.getArmorClass());
        assertEquals("9 m", s.getSpeed());
        assertEquals(3, s.getLevel());
        assertEquals("Rogue", s.getClassName());
        assertEquals("Thief", s.getSubclass());
        assertEquals("Elf", s.getSpecies());
        assertEquals("1d6-1 piercing", s.getAttacks().get(0).getDamageAndType(), "FOR 8 (-1) somado no dano");
        assertEquals("+1", s.getAttacks().get(0).getAttackBonusOrDc(), "prof +2, FOR -1");
        assertEquals(1, s.getSpells().size());
        assertEquals("Orfa de Waterdeep.", s.getBackstory());
        assertEquals(42, s.getInventory().getMoney().get(Currency.GOLD));
        assertTrue(s.getInventory().findByName("Rope").isPresent());
        assertTrue(s.getLanguages().contains("Elvish"));
    }

    @Test
    void fichaDoDndBeyond() throws Exception {
        String json = """
                {"id": 1, "success": true, "data": {
                  "name": "Borin",
                  "stats": [{"id":1,"value":15},{"id":2,"value":10},{"id":3,"value":14},
                            {"id":4,"value":8},{"id":5,"value":12},{"id":6,"value":10}],
                  "bonusStats": [], "overrideStats": [{"id":6,"value":null}],
                  "race": {"fullName": "Mountain Dwarf"},
                  "classes": [{"level": 2, "definition": {"name": "Fighter", "hitDice": 10}}],
                  "background": {"definition": {"name": "Soldier"}},
                  "baseHitPoints": 18, "bonusHitPoints": null, "removedHitPoints": 5, "temporaryHitPoints": 0,
                  "currencies": {"cp": 0, "sp": 10, "gp": 15, "ep": 0, "pp": 0},
                  "inventory": [{"quantity": 1, "definition": {"name": "Battleaxe", "weight": 4, "cost": 10}}],
                  "modifiers": {
                    "race": [{"type": "bonus", "subType": "strength-score", "value": 2},
                             {"type": "language", "subType": "dwarvish", "friendlySubtypeName": "Dwarvish"}],
                    "class": [{"type": "proficiency", "subType": "strength-saving-throws"},
                              {"type": "proficiency", "subType": "athletics"}],
                    "background": [{"type": "proficiency", "subType": "intimidation"}]
                  },
                  "classSpells": [], "spells": {"race": [{"definition": {"name": "Light", "level": 0}}]}
                }}""";
        SheetImport.Result r = SheetImport.fromJson(json);
        CharacterSheet s = r.sheet();
        assertEquals("D&D Beyond (JSON)", r.format());
        assertEquals("Borin", s.getName());
        assertEquals(17, s.getStrength(), "15 + 2 de especie");
        assertEquals(2, s.getLevel());
        assertEquals("d10", s.getHitDie());
        assertEquals(22, s.getHpMax(), "18 base + CON +2 x 2 niveis");
        assertEquals(17, s.getHpCurrent());
        assertTrue(s.isSavingThrowProficient(Ability.STRENGTH));
        assertTrue(s.isSkillProficient(Skill.ATHLETICS));
        assertTrue(s.isSkillProficient(Skill.INTIMIDATION));
        assertTrue(s.getLanguages().contains("Dwarvish"));
        assertEquals(15, s.getInventory().getMoney().get(Currency.GOLD));
        assertEquals("Light", s.getSpells().get(0).getName());
        assertFalse(r.notes().isEmpty(), "avisa que a CA tem que ser preenchida");
    }

    @Test
    void fichaDoProprioCustomizrViraFichaNova() throws Exception {
        CharacterSheet original = new CharacterSheet("Lia");
        original.setWisdom(17);
        original.setOwnerPlayerId("outro-jogador");
        String json = com.vttcaseiro.library.LibraryStorage.characterToJson(original);
        SheetImport.Result r = SheetImport.fromJson(json);
        assertEquals("Lia", r.sheet().getName());
        assertEquals(17, r.sheet().getWisdom());
        assertNotEquals(original.getId(), r.sheet().getId());
        assertNull(r.sheet().getOwnerPlayerId());
    }

    @Test
    void jsonDesconhecidoDaErroClaro() {
        IOException ex = assertThrows(IOException.class, () -> SheetImport.fromJson("{\"foo\": 1}"));
        assertTrue(ex.getMessage().contains("Foundry"));
        assertThrows(IOException.class, () -> SheetImport.fromJson("nao e json {"));
    }

    @Test
    void camposDaFichaOficialDePdf() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("CharacterName", "Kael");
        f.put("ClassLevel", "Wizard 5");
        f.put("Race ", "High Elf");
        f.put("STR", "8");
        f.put("DEX", "14");
        f.put("CON", "13");
        f.put("INT", "18");
        f.put("WIS", "12");
        f.put("CHA", "10");
        f.put("ProfBonus", "+3");
        f.put("AC", "12");
        f.put("HPMax", "27");
        f.put("Speed", "30");
        f.put("ST Intelligence", "+7");
        f.put("ST Strength", "-1");
        f.put("Arcana", "+10");       // 4 + 2x3 = especialista
        f.put("History ", "+7");      // 4 + 3 = proficiente
        f.put("Stealth ", "+2");      // so o modificador
        f.put("Check Box 21", "Yes"); // salvaguarda de SAB marcada na caixa
        f.put("Wpn Name", "Quarterstaff");
        f.put("Wpn1 AtkBonus", "+2");
        f.put("Wpn1 Damage", "1d6 bludgeoning");
        f.put("GP", "15");
        f.put("Equipment", "Spellbook\n2 Potion of Healing");
        f.put("Backstory", "Cresceu em 1 torre"); // texto com numero nao vira pericia
        SheetImport.Result r = PdfFieldMapper.map(f, "teste");
        assertNotNull(r);
        CharacterSheet s = r.sheet();
        assertEquals("Kael", s.getName());
        assertEquals("Wizard", s.getClassName());
        assertEquals(5, s.getLevel());
        assertEquals("High Elf", s.getSpecies());
        assertEquals(18, s.getIntelligence());
        assertEquals(27, s.getHpMax());
        assertEquals(27, s.getHpCurrent());
        assertEquals("9 m", s.getSpeed());
        assertTrue(s.isSavingThrowProficient(Ability.INTELLIGENCE));
        assertFalse(s.isSavingThrowProficient(Ability.STRENGTH));
        assertTrue(s.isSavingThrowProficient(Ability.WISDOM));
        assertTrue(s.hasSkillExpertise(Skill.ARCANA));
        assertTrue(s.isSkillProficient(Skill.HISTORY));
        assertFalse(s.hasSkillExpertise(Skill.HISTORY));
        assertFalse(s.isSkillProficient(Skill.STEALTH));
        assertEquals("+2", s.getAttacks().get(0).getAttackBonusOrDc());
        assertEquals(15, s.getInventory().getMoney().get(Currency.GOLD));
        assertEquals(2, s.getInventory().findByName("Potion of Healing").orElseThrow().getQuantity());
    }

    @Test
    void pdfPreenchivelDeVerdade(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("ficha.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources res = new PDResources();
            res.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(res);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");
            int y = 700;
            for (String[] kv : new String[][]{{"CharacterName", "Mira"}, {"ClassLevel", "Cleric 1"},
                    {"STR", "10"}, {"DEX", "12"}, {"CON", "14"}, {"INT", "10"}, {"WIS", "16"}, {"CHA", "8"},
                    {"AC", "18"}, {"HPMax", "10"}}) {
                PDTextField field = new PDTextField(form);
                field.setPartialName(kv[0]);
                PDAnnotationWidget w = field.getWidgets().get(0);
                w.setRectangle(new PDRectangle(50, y, 200, 20));
                w.setPage(page);
                page.getAnnotations().add(w);
                form.getFields().add(field);
                field.setValue(kv[1]);
                y -= 30;
            }
            doc.save(pdf.toFile());
        }
        SheetImport.Result r = SheetImport.fromFile(pdf);
        assertTrue(r.format().startsWith("PDF preenchivel"), r.format());
        assertEquals("Mira", r.sheet().getName());
        assertEquals(16, r.sheet().getWisdom());
        assertEquals(18, r.sheet().getArmorClass());
        assertEquals("Cleric", r.sheet().getClassName());
    }

    @Test
    void pdfSemCamposNemTextoDaErroExplicando(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("vazio.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        IOException ex = assertThrows(IOException.class, () -> SheetImport.fromFile(pdf));
        assertTrue(ex.getMessage().contains("preenchivel"));
        Files.writeString(dir.resolve("x.pdf"), "%PDF-lixo");
        assertThrows(IOException.class, () -> SheetImport.fromFile(dir.resolve("x.pdf")));
    }
}
