package com.vttcaseiro.persistence;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CombatRules;
import com.vttcaseiro.character.RollKind;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.content.EntriesRenderer;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobAttack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaAndMobTest {

    @Test
    void fichaV1ComTracoDeAtaqueViraLinhaDeAtaqueEGanhaVersao(@TempDir Path dir) throws Exception {
        LibraryStorage lib = new LibraryStorage(dir);
        String v1 = "{\"id\":\"x\",\"name\":\"Velha\",\"traits\":["
                + "{\"name\":\"Ataque com arma\",\"description\":\"Ataque corpo a corpo: +5 para acertar, 1d8+3 de dano.\",\"category\":\"attack\"},"
                + "{\"name\":\"Visao no escuro\",\"description\":\"18 m\",\"category\":\"species\"}]}";
        Files.writeString(dir.resolve("fichas").resolve("Velha.json"), v1);

        CharacterSheet sheet = lib.loadCharacter("Velha");
        assertEquals(1, sheet.getTraits().size(), "o traco de ataque saiu dos tracos");
        assertEquals(1, sheet.getAttacks().size());
        assertEquals("+5", sheet.getAttacks().get(0).getAttackBonusOrDc());
        assertEquals("1d8+3", sheet.getAttacks().get(0).damageExpression().orElseThrow());

        lib.saveCharacter(sheet, "Velha");
        String saved = Files.readString(dir.resolve("fichas").resolve("Velha.json"));
        assertTrue(saved.contains("\"schemaVersion\": " + SchemaMigrations.CURRENT), saved);
        // Abrir de novo nao migra duas vezes.
        assertEquals(1, lib.loadCharacter("Velha").getAttacks().size());
    }

    @Test
    void arquivoDeVersaoFuturaAbreMesmoAssim(@TempDir Path dir) throws Exception {
        LibraryStorage lib = new LibraryStorage(dir);
        Files.writeString(dir.resolve("fichas").resolve("Nova.json"),
                "{\"schemaVersion\": 99, \"id\":\"y\", \"name\":\"Nova\", \"campoDoFuturo\": 1}");
        assertEquals("Nova", lib.loadCharacter("Nova").getName());
    }

    @Test
    void textoDeAcaoDeMonstro2024ELegivelERolavel() {
        String raw = "{@atkr m} {@hit 4}, reach 5 ft. {@h}5 ({@damage 1d6 + 2}) Slashing damage.";
        String text = EntriesRenderer.renderInline(raw);
        assertTrue(text.startsWith("Ataque corpo a corpo: +4"), text);
        assertEquals(4, MobAttack.attackBonus(text).getAsInt());
        assertEquals("1d6+2", MobAttack.damage(text).orElseThrow());

        String breath = EntriesRenderer.renderInline("{@actSave dex} {@dc 21}, cone. {@actSaveFail} 59 ({@damage 17d6}) Fire.");
        assertTrue(breath.startsWith("Salvaguarda de DES: CD 21"), breath);
        assertEquals(21, MobAttack.saveDc(breath).getAsInt());
        assertTrue(MobAttack.attackBonus(breath).isEmpty());

        // Monstro importado antes desta correcao ("m +4, reach...") tambem rola.
        assertEquals(4, MobAttack.attackBonus("m +4, reach 5 ft. Acerto: 5 (1d6 + 2)").getAsInt());
        assertEquals(8, MobAttack.attackBonus("Melee Weapon Attack: +8 to hit").getAsInt());
    }

    @Test
    void regrasDeCondicaoValemProTokenDeMonstro() {
        var adj = CombatRules.rollAdjustment(List.of("POISONED"), 0, RollKind.ATTACK, null, RollMode.NORMAL);
        assertEquals(RollMode.DISADVANTAGE, adj.mode());
        assertTrue(CombatRules.rollAdjustment(List.of("STUNNED"), 0, RollKind.SAVING_THROW, Ability.DEXTERITY,
                RollMode.NORMAL).autoFail());
    }
}
