package com.vttcaseiro.service;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.DamageTypes;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobAttack;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.GmRequest;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Alvos, ataque contra a CA, resistencias e o pedido pro mestre. */
class TargetingAndDamageTest {

    private TableService table;
    private SceneService scenes;
    private CharacterService chars;
    private TableAndEncounterTest.FakePort port;
    private MobSheet ogreSheet;
    private TokenModel ogre;
    private CharacterSheet elora;
    private TokenModel eloraTok;
    private final AtomicLong now = new AtomicLong(1_000_000);

    private void setUp(Path dir) throws Exception {
        chars = new CharacterService(new LibraryStorage(dir));
        scenes = new SceneService(new SceneModel());
        port = new TableAndEncounterTest.FakePort();
        table = new TableService(scenes, chars, port);
        table.setClock(now::get);

        ogreSheet = new MobSheet("Ogro");
        ogreSheet.setArmorClass(11);
        ogreSheet.getDamageResistances().add("Fire");
        ogreSheet.getDamageImmunities().add("poison");
        table.setMobLookup(id -> id.equals(ogreSheet.getId()) ? ogreSheet : null);
        ogre = TokenLink.tokenFromMob(ogreSheet, "Ogro");
        ogre.setMaxHp(60);
        ogre.setCurrentHp(60);

        elora = chars.create("Elora");
        elora.setHpMax(30);
        elora.setHpCurrent(30);
        elora.setArmorClass(15);
        elora.setDamageVulnerabilities(List.of("frio"));
        eloraTok = TokenLink.tokenFromSheet(elora);
        scenes.addToken(ogre);
        scenes.addToken(eloraTok);
    }

    @Test
    void tiposDeDanoEmPortuguesEInglesERegras() {
        assertEquals("slashing", DamageTypes.find("1d8+3 cortante"));
        assertEquals("fire", DamageTypes.find("Dano: Bola de Fogo (fogo)"));
        assertEquals("lightning", DamageTypes.find("LIGHTNING damage"));
        assertEquals("acid", DamageTypes.find("ácido"));
        assertNull(DamageTypes.find("Elora - Dano: Adaga"));
        var d = DamageTypes.defenses(List.of("bludgeoning, piercing, and slashing from nonmagical attacks"),
                List.of("Poison"), List.of("fogo"));
        assertEquals(5, DamageTypes.apply(11, "slashing", d).amount(), "resistencia arredonda pra baixo");
        assertEquals(0, DamageTypes.apply(11, "poison", d).amount());
        assertEquals(22, DamageTypes.apply(11, "fire", d).amount());
        assertEquals(11, DamageTypes.apply(11, null, d).amount(), "sem tipo nao muda");
        assertEquals("resistente a cortante", DamageTypes.apply(11, "slashing", d).note());
    }

    @Test
    void ataqueComparaComACaENaoMostraCaDeMonstroProJogador(@TempDir Path dir) throws Exception {
        setUp(dir);
        port.gm = false;
        port.connected = true;
        var out = table.resolveAttack("sheet:x", "Beto", 13, 9, List.of(ogre, eloraTok));
        assertTrue(out.get(0).hit(), "13 vs CA 11 acerta");
        assertFalse(out.get(1).hit(), "13 vs CA 15 erra");
        String pub = port.announced.get(port.announced.size() - 1);
        assertTrue(pub.contains("Ogro: acertou"), pub);
        assertFalse(pub.contains("CA 11"), "CA do monstro nao vai pro chat publico: " + pub);
        assertTrue(pub.contains("Elora: errou (CA 15)"), "CA de personagem pode aparecer: " + pub);

        var crit = table.resolveAttack("k", "Beto", 3, 20, List.of(eloraTok));
        assertTrue(crit.get(0).hit() && crit.get(0).crit(), "20 natural sempre acerta");
        assertTrue(table.lastAttackWasCritical("k"));
        var fumble = table.resolveAttack("k2", "Beto", 40, 1, List.of(ogre));
        assertFalse(fumble.get(0).hit(), "1 natural sempre erra");

        TokenModel unknown = new TokenModel("Sombra", null);
        scenes.addToken(unknown);
        assertNull(table.resolveAttack("k3", "Beto", 30, 10, List.of(unknown)).get(0).ac());
    }

    @Test
    void danoCaiSoNosAcertadosUmaVezEExpira(@TempDir Path dir) throws Exception {
        setUp(dir);
        table.resolveAttack("sheet:a", "Ana", 13, 10, List.of(ogre, eloraTok));
        assertTrue(table.hasPendingAttack("sheet:a"));
        assertEquals(List.of(ogre), table.takeHits("sheet:a"));
        assertTrue(table.takeHits("sheet:a").isEmpty(), "segundo dano nao aplica de novo");

        table.resolveAttack("sheet:a", "Ana", 13, 10, List.of(ogre));
        now.addAndGet(TableService.ATTACK_MEMORY_MS + 1);
        assertFalse(table.hasPendingAttack("sheet:a"), "ataque velho nao vale");
        assertTrue(table.takeHits("sheet:a").isEmpty());
    }

    @Test
    void resistenciaImunidadeEVulnerabilidadeNaHoraDoDano(@TempDir Path dir) throws Exception {
        setUp(dir);
        table.damageTargets(List.of(ogre), 20, "fire", "Bola de Fogo");
        assertEquals(50, ogre.getCurrentHp(), "resistente a fogo: 10");
        assertTrue(port.announced.stream().anyMatch(m -> m.contains("resistente a fogo: 20 -> 10")), port.announced.toString());
        table.damageTargets(List.of(ogre), 20, "poison", null);
        assertEquals(50, ogre.getCurrentHp(), "imune a veneno");
        table.damageTargets(List.of(eloraTok), 6, "cold", null);
        assertEquals(18, elora.getHpCurrent(), "vulneravel a frio: 12, pela ficha");
    }

    @Test
    void jogadorPedeProMestreEOMestreAplica(@TempDir Path dir) throws Exception {
        setUp(dir);
        elora.setOwnerPlayerId("ana");
        port.gm = false;
        port.connected = true;
        port.me = "ana";
        table.damageTargets(List.of(ogre, eloraTok), 10, "fire", "Raio de Fogo");
        assertEquals(60, ogre.getCurrentHp(), "jogador nao mexe no monstro");
        assertEquals(1, port.gmRequests.size());
        GmRequest req = port.gmRequests.get(0);
        assertEquals("damage", req.getKind());
        assertEquals(List.of(ogre.getId()), req.getTokenIds(), "o token da propria ficha aplica aqui mesmo");

        table.saveTargets(List.of(ogre), Ability.DEXTERITY, 15, 28, "fire", true, "Bola de Fogo");
        assertEquals("save", port.gmRequests.get(1).getKind());
        assertEquals(15, port.gmRequests.get(1).getDc());

        table.handleGmRequest(req, "Ana");
        assertEquals(60, ogre.getCurrentHp(), "so o mestre atende pedido");

        port.gm = true;
        table.handleGmRequest(req, "Ana");
        assertEquals(55, ogre.getCurrentHp(), "mestre aplica com a resistencia do ogro");
        assertTrue(port.announcedGm.stream().anyMatch(m -> m.startsWith("Ana pediu 10 de dano de fogo")), port.announcedGm.toString());

        table.handleGmRequest(GmRequest.save(List.of(ogre.getId()), "DEXTERITY", 100, 20, "fire", true, "x"), "Ana");
        assertEquals(45, ogre.getCurrentHp(), "falhou a CD 100: 20 de fogo, resistente = 10");
    }

    @Test
    void magiaDoCompendioSabeTipoSalvaguardaECura() {
        PreparedSpell fireball = new PreparedSpell(3, "Fireball");
        CompendiumToSheet.fillSpellDice(fireball, "Duration: Instantaneous\n\nEach creature makes a Dexterity saving"
                + " throw, taking 8d6 Fire damage on a failed save or half as much damage on a successful one.");
        assertEquals("fire", fireball.getDamageType());
        assertEquals(Ability.DEXTERITY, fireball.saveAbilityOrNull());
        assertTrue(fireball.isHalfOnSave());
        assertFalse(fireball.isSpellAttack());

        PreparedSpell bolt = new PreparedSpell(0, "Fire Bolt");
        CompendiumToSheet.fillSpellDice(bolt, "Duration: Instantaneous\n\nMake a ranged spell attack against the"
                + " target. On a hit, the target takes 1d10 Fire damage.");
        assertTrue(bolt.isSpellAttack());
        assertEquals("fire", bolt.getDamageType());

        PreparedSpell cure = new PreparedSpell(1, "Cure Wounds");
        CompendiumToSheet.fillSpellDice(cure, "Duration: Instantaneous\n\nA creature you touch regains a number of"
                + " Hit Points equal to 2d8 plus your spellcasting ability modifier.");
        assertTrue(cure.isHealing());

        assertEquals("slashing", MobAttack.damageType("Hit: 5 (1d6 + 2) Slashing damage."));
        assertNull(MobAttack.damageType("sem dano"));
    }
}
