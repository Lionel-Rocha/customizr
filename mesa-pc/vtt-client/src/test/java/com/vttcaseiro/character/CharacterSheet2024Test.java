package com.vttcaseiro.character;

import com.google.gson.Gson;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.service.CharacterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras da ficha 2024 que a tela calcula sozinha. */
class CharacterSheet2024Test {

    @Test
    void calculosDerivados() {
        CharacterSheet s = new CharacterSheet("Teste");
        s.setLevel(5);               // prof +3
        s.setDexterity(16);          // +3
        s.setWisdom(14);             // +2
        s.setIntelligence(18);       // +4
        assertEquals(3, s.proficiencyBonus());
        assertEquals(3, s.initiative());
        s.setInitiativeBonus(3);     // talento Alert
        assertEquals(6, s.initiative());

        assertEquals(12, s.passivePerception());
        s.setSkillProficient(Skill.PERCEPTION, true);
        assertEquals(15, s.passivePerception());
        s.setSkillExpertise(Skill.PERCEPTION, true);
        assertEquals(18, s.passivePerception());

        assertFalse(s.isSpellcaster());
        s.setSpellcastingAbility(Ability.INTELLIGENCE);
        assertEquals(15, s.spellSaveDc());       // 8 + 4 + 3
        assertEquals(7, s.spellAttackBonus());   // 4 + 3
    }

    @Test
    void caComEscudoESemArmaduraPreenchida() {
        CharacterSheet s = new CharacterSheet("Teste");
        s.setDexterity(14);
        assertEquals(12, s.totalArmorClass());   // 10 + DES quando CA vazia
        s.setArmorClass(16);
        s.setShieldEquipped(true);
        assertEquals(18, s.totalArmorClass());
    }

    @Test
    void danoTiraPrimeiroDoTemporarioECuraRespeitaMaximo() {
        CharacterSheet s = new CharacterSheet("Teste");
        s.setHpMax(20);
        s.setHpCurrent(20);
        s.setHpTemp(5);
        s.applyDamage(8);
        assertEquals(0, s.getHpTemp());
        assertEquals(17, s.getHpCurrent());
        s.applyDamage(100);
        assertEquals(0, s.getHpCurrent());
        s.setDeathSaveFailures(2);
        s.heal(50);
        assertEquals(20, s.getHpCurrent());
        assertEquals(0, s.getDeathSaveFailures(), "sair de 0 PV zera os testes contra a morte");
    }

    @Test
    void dadosDeVidaEDescansoLongo() {
        CharacterSheet s = new CharacterSheet("Teste");
        s.setLevel(2);
        s.setHpMax(18);
        s.setHpCurrent(3);
        s.getSpellSlots().setMax(1, 3);
        s.getSpellSlots().setCurrent(1, 0);
        assertTrue(s.spendHitDie());
        assertTrue(s.spendHitDie());
        assertFalse(s.spendHitDie());
        assertEquals(0, s.hitDiceRemaining());
        s.longRest();
        assertEquals(18, s.getHpCurrent());
        assertEquals(2, s.hitDiceRemaining());
        assertEquals(3, s.getSpellSlots().getCurrent(1));
    }

    @Test
    void tracoDeCategoriaAntigaApareceEmOutros() {
        CharacterSheet s = new CharacterSheet("Teste");
        s.addTrait(new CharacterTrait("Ataque com arma", "", "attack"));
        s.addTrait(new CharacterTrait("Furia", "", "class"));
        s.addTrait(new CharacterTrait("Sem categoria", "", null));
        assertEquals(1, s.traitsInCategory("class").size());
        assertEquals(2, s.traitsInCategory("other").size());
    }

    @Test
    void ataqueExtraiBonusEDano() {
        Attack a = new Attack("Espada longa", "+5", "1d8 + 3 cortante", "");
        assertEquals(5, a.attackBonus().getAsInt());
        assertEquals("1d8+3", a.damageExpression().orElseThrow());
        Attack magia = new Attack("Raio de Fogo", "CD 13", "2d10 fogo", "");
        assertTrue(magia.attackBonus().isEmpty());
        assertEquals("2d10", magia.damageExpression().orElseThrow());
        assertTrue(new Attack("X", "", "especial", "").damageExpression().isEmpty());
    }

    @Test
    void fichaAntigaSemOsCamposNovosAbreComPadroes() {
        String json = "{\"id\":\"abc\",\"name\":\"Velha\",\"level\":3,\"dexterity\":12}";
        CharacterSheet s = new Gson().fromJson(json, CharacterSheet.class);
        assertEquals("Velha", s.getName());
        assertEquals("d8", s.getHitDie());
        assertEquals(3, s.getAttunedItems().size());
        assertNotNull(s.getSpells());
        assertEquals(11, s.totalArmorClass());
    }

    @Test
    void camposNovosSobrevivemAoSalvarEAbrir(@TempDir Path dir) throws IOException {
        LibraryStorage lib = new LibraryStorage(dir);
        CharacterSheet s = new CharacterSheet("Mago");
        s.setSpellcastingAbility(Ability.INTELLIGENCE);
        s.getSpells().add(new PreparedSpell(1, "Escudo Arcano"));
        s.getAttunedItems().set(0, "Anel de protecao");
        s.setHpTemp(4);
        lib.saveCharacter(s, s.getName());
        CharacterSheet loaded = lib.loadCharacter("Mago");
        assertEquals(Ability.INTELLIGENCE, loaded.getSpellcastingAbility());
        assertEquals("Escudo Arcano", loaded.getSpells().get(0).getName());
        assertEquals("Anel de protecao", loaded.getAttunedItems().get(0));
        assertEquals(4, loaded.getHpTemp());
    }

    @Test
    void renomearApagaOArquivoAntigoMasNaoSobrescreveOutraFicha(@TempDir Path dir) throws IOException {
        LibraryStorage lib = new LibraryStorage(dir);
        CharacterService service = new CharacterService(lib);
        CharacterSheet a = service.create("Aragorn");
        service.create("Legolas");

        a.setName("Passolargo");
        service.save(a);
        assertEquals(List.of("Legolas", "Passolargo"), service.list());

        a.setName("Legolas");
        assertThrows(IOException.class, () -> service.save(a));
        assertEquals("Legolas", lib.loadCharacter("Legolas").getName());
        assertNotEquals(a.getId(), lib.loadCharacter("Legolas").getId());
    }
}
