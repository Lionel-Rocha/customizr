package com.vttcaseiro.character;

import com.vttcaseiro.model.Condition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Efeitos com numeros (Bencao, Escudo, Furia, "[ca+1]") e recursos com usos. */
class EffectsAndResourcesTest {

    private static final java.util.function.ToIntFunction<String> ALWAYS_3 = expr -> 3;

    @Test
    void separaListaRespeitandoColchetes() {
        assertEquals(List.of("Bencao", "Anel [ca+1, salv+1]", "Furia"),
                EffectModifiers.splitList("Bencao, Anel [ca+1, salv+1], Furia"));
        assertEquals(List.of(), EffectModifiers.splitList("  ,  "));
    }

    @Test
    void catalogoEColchetes() {
        EffectModifiers.Mods m = EffectModifiers.parse(List.of("Bênção", "Escudo", "Anel de Protecao [ca+1, salv+1]",
                "Furia", "Cansado demais"));
        assertEquals(6, m.ac, "Escudo +5 e Anel +1");
        assertEquals(1, m.attack.size(), "Bencao no ataque");
        assertEquals(0, m.check.size(), "nada em teste");
        assertEquals(2, m.save.size(), "Bencao 1d4 e Anel +1");
        assertTrue(m.resist.containsAll(List.of("bludgeoning", "piercing", "slashing")));
        assertEquals("+2", EffectModifiers.damageSuffix(List.of("Furia")));
        assertEquals("+2+1d6", EffectModifiers.damageSuffix(List.of("Furia", "Arma flamejante [dano+1d6]")));
        assertTrue(EffectModifiers.parse(List.of("Cansado demais")).isEmpty(), "efeito so de texto");
        assertEquals("CA +5", EffectModifiers.describe(List.of("Escudo")));
        assertTrue(EffectModifiers.describe(List.of("Furia")).contains("vantagem em teste de FOR"));
    }

    @Test
    void efeitoEntraNaRolagemComDadoRolado() {
        var atk = EffectModifiers.forRoll(List.of("Bencao"), RollKind.ATTACK, null, ALWAYS_3);
        assertEquals(3, atk.delta());
        assertEquals("Bencao +3 (1d4)", atk.notes().get(0));
        var bane = EffectModifiers.forRoll(List.of("Perdicao"), RollKind.DEATH_SAVE, null, ALWAYS_3);
        assertEquals(-3, bane.delta(), "Perdicao tira 1d4 da salvaguarda contra a morte");
        assertEquals(0, EffectModifiers.forRoll(List.of("Bencao"), RollKind.ABILITY_CHECK, Ability.STRENGTH, ALWAYS_3).delta());
        var rageSave = EffectModifiers.forRoll(List.of("Furia"), RollKind.SAVING_THROW, Ability.STRENGTH, ALWAYS_3);
        assertTrue(rageSave.advantage());
        assertFalse(EffectModifiers.forRoll(List.of("Furia"), RollKind.SAVING_THROW, Ability.DEXTERITY, ALWAYS_3).advantage());
        assertTrue(EffectModifiers.forRoll(List.of("Velocidade"), RollKind.SAVING_THROW, Ability.DEXTERITY, ALWAYS_3).advantage());
        assertTrue(EffectModifiers.forRoll(List.of("Foco [vant:teste]"), RollKind.INITIATIVE, Ability.DEXTERITY,
                ALWAYS_3).advantage(), "iniciativa e teste de DES");
    }

    @Test
    void vantagemDeEfeitoCancelaComDesvantagemDeCondicao() {
        RollAdjustment adj = CombatRules.rollAdjustment(List.of(Condition.POISONED.name()), 0,
                RollKind.ABILITY_CHECK, Ability.STRENGTH, RollMode.NORMAL, List.of("Furia"), ALWAYS_3);
        assertEquals(RollMode.NORMAL, adj.mode(), "envenenado (desv.) + furia (vant.) = normal");
        RollAdjustment bless = CombatRules.rollAdjustment(List.of(), 2, RollKind.ATTACK, null, RollMode.NORMAL,
                List.of("Bencao"), ALWAYS_3);
        assertEquals(-4 + 3, bless.modifierDelta(), "exaustao 2 (-4) + bencao 3");
        assertTrue(bless.notesText().contains("Bencao +3"));
    }

    @Test
    void fichaUsaEfeitosNaCaENasRolagens() {
        CharacterSheet s = new CharacterSheet("Elora");
        s.setArmorClass(15);
        assertEquals(15, s.totalArmorClass());
        s.setEffects(List.of("Escudo", "Escudo da Fe"));
        assertEquals(22, s.totalArmorClass());
        s.setEffects(List.of("Bencao"));
        int delta = s.rollAdjustment(RollKind.ATTACK, null, RollMode.NORMAL).modifierDelta();
        assertTrue(delta >= 1 && delta <= 4, "1d4 da Bencao: " + delta);
    }

    @Test
    void recursosGastamERecarregamNoDescansoCerto() {
        CharacterSheet s = new CharacterSheet("Brok");
        s.getResources().add(new Resource("Furia", 3, Resource.LONG));
        s.getResources().add(new Resource("Segundo Folego", 1, Resource.SHORT));
        s.getResources().add(new Resource("Pocao de cura", 2, Resource.NEVER));
        Resource rage = s.findResource("furia").orElseThrow();
        assertTrue(rage.use());
        assertTrue(rage.use());
        assertTrue(rage.use());
        assertFalse(rage.use(), "acabou");
        assertEquals("0/3", rage.usesText());
        s.findResource("Segundo Folego").orElseThrow().use();
        s.findResource("Pocao de cura").orElseThrow().use();

        assertEquals(List.of("Segundo Folego"), s.recoverResources(false), "curto so recupera os de curto");
        assertEquals(0, rage.getCurrent());
        s.longRest();
        assertEquals(3, rage.getCurrent(), "longo recupera os de longo");
        assertEquals(1, s.findResource("Pocao de cura").orElseThrow().getCurrent(), "consumivel nao volta");

        rage.setMax(1);
        assertEquals(1, rage.getCurrent(), "baixar o maximo corta os usos");
        Resource odd = new Resource("x", 2, "qualquer");
        assertEquals(Resource.LONG, odd.getRecharge());
    }
}
