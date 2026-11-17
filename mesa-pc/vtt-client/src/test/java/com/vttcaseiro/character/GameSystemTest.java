package com.vttcaseiro.character;

import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.dice.RollResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSystemTest {

    @Test
    void tormenta20() {
        CharacterSheet s = new CharacterSheet("Aldo");
        GameSystem.TORMENTA20.initialize(s);
        assertEquals("t20", s.getSystem());
        assertSame(GameSystem.TORMENTA20, GameSystem.of(s));
        assertTrue(s.findResource("PM").isPresent());
        s.getCustomAttributes().put("DES", 16);          // valor: mod +3
        s.getCustomAttributes().put("FOR", 2);           // ja anotado como modificador
        assertEquals(3, GameSystem.TORMENTA20.attrModifier(s, "DES"));
        assertEquals(2, GameSystem.TORMENTA20.attrModifier(s, "FOR"));
        s.setLevel(5);
        s.getCustomSkills().put("Furtividade", 1);
        // metade do nivel 2 + DES 3 + treino 2
        assertEquals(7, GameSystem.TORMENTA20.skillBonus(s, "Furtividade"));
        assertEquals("1d20+7", GameSystem.TORMENTA20.skillTest(s, "Furtividade"));
        s.setLevel(7);
        assertEquals(3 + 3 + 4, GameSystem.TORMENTA20.skillBonus(s, "Furtividade"), "treino +4 no nivel 7");
        s.setLevel(15);
        s.getCustomSkillBonus().put("Furtividade", 2);
        assertEquals(7 + 3 + 6 + 2, GameSystem.TORMENTA20.skillBonus(s, "Furtividade"));
        assertEquals("1d20+3", GameSystem.TORMENTA20.attrTest(s, "DES"));
        assertEquals(13, GameSystem.TORMENTA20.suggestedDefense(s));
        Attack sword = new Attack("Espada", "Luta + 1", "1d8+2 corte", "19/x2");
        assertEquals("1d20+" + (7 + 2 + 1), GameSystem.TORMENTA20.attackTest(s, sword), "Luta (FOR 2, destreinado) + 1");
        assertEquals("1d20+5", GameSystem.TORMENTA20.attackTest(s, new Attack("Arco", "+5", "1d8", "")));
    }

    @Test
    void ordemParanormal() {
        CharacterSheet s = new CharacterSheet("Dante");
        GameSystem.ORDEM.initialize(s);
        assertEquals(1, GameSystem.ORDEM.attr(s, "AGI"), "atributos comecam em 1");
        assertTrue(s.findResource("PE").isPresent() && s.findResource("SAN").isPresent());
        assertEquals("5%", s.getCustomFields().get("NEX"));
        s.getCustomAttributes().put("AGI", 3);
        s.getCustomAttributes().put("INT", 0);
        s.getCustomSkills().put("Pontaria", 2); // veterano
        assertEquals("3d20kh1+10", GameSystem.ORDEM.skillTest(s, "Pontaria"));
        assertEquals("2d20kl1", GameSystem.ORDEM.skillTest(s, "Ocultismo"), "atributo 0: 2d20, fica o menor");
        assertEquals("1d20", GameSystem.ORDEM.attrTest(s, "VIG"));
        assertEquals("3d20kh1", GameSystem.ORDEM.initiativeTest(s));
        assertEquals(13, GameSystem.ORDEM.suggestedDefense(s));
        assertEquals(0, s.initiative(), "o fixo da iniciativa (sem dados) vai pro token");
    }

    @Test
    void dadosFicaComOMaiorOuMenor() throws Exception {
        for (int i = 0; i < 200; i++) {
            RollResult hi = DiceRoller.roll("3d20kh1+2");
            assertTrue(hi.total() >= 3 && hi.total() <= 22, hi.detail());
            assertNotNull(hi.naturalD20(), "o d20 que valeu marca critico");
            assertEquals(hi.total() - 2, hi.naturalD20());
            RollResult lo = DiceRoller.roll("2d20kl1");
            assertTrue(lo.total() >= 1 && lo.total() <= 20);
            RollResult top2 = DiceRoller.roll("4d6kh3");
            assertTrue(top2.total() >= 3 && top2.total() <= 18);
        }
        assertTrue(DiceRoller.roll("3d20kh1").detail().contains("(fora: "), "os descartados aparecem a parte");
    }

    @Test
    void salvaEAbreComOSistema() throws Exception {
        CharacterSheet s = new CharacterSheet("Rin");
        GameSystem.ORDEM.initialize(s);
        s.getCustomSkills().put("Ocultismo", 3);
        String json = com.vttcaseiro.library.LibraryStorage.characterToJson(s);
        CharacterSheet back = com.vttcaseiro.library.LibraryStorage.parseCharacterJson(json);
        assertEquals("op", back.getSystem());
        assertEquals(3, back.getCustomSkills().get("Ocultismo"));
        CharacterSheet dnd = com.vttcaseiro.library.LibraryStorage.parseCharacterJson(
                com.vttcaseiro.library.LibraryStorage.characterToJson(new CharacterSheet("Velha")));
        assertEquals(GameSystem.DND5E, dnd.getSystem(), "ficha antiga continua D&D");
        assertFalse(GameSystem.of(dnd).isCustom());
    }

    @Test
    void navegadorRecebeOsTestesProntos() {
        CharacterSheet s = new CharacterSheet("Rin");
        GameSystem.ORDEM.initialize(s);
        s.getCustomAttributes().put("PRE", 2);
        var v = com.vttcaseiro.net.WebGateway.systemView(s, GameSystem.ORDEM);
        assertEquals("Ordem Paranormal", v.get("name").getAsString());
        boolean found = false;
        for (var e : v.getAsJsonArray("skills")) {
            if (e.getAsJsonObject().get("name").getAsString().equals("Vontade")) {
                assertEquals("2d20kh1", e.getAsJsonObject().get("test").getAsString());
                found = true;
            }
        }
        assertTrue(found);
        assertEquals(2, v.getAsJsonArray("resources").size());
    }
}
