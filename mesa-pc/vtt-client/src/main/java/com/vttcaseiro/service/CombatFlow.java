package com.vttcaseiro.service;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.ClassResources;
import com.vttcaseiro.character.DamageTypes;
import com.vttcaseiro.character.EffectModifiers;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.SpellSlots;
import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.model.TokenModel;

import java.util.List;
import java.util.function.Consumer;

/**
 * O "fluxo" de combate que o programa e o navegador usam igual: dano depois
 * do ataque cair so nos acertados (com critico dobrando os dados), efeito de
 * magia conjurada (Bencao...) e o que os dados da magia fazem (cura, area com
 * salvaguarda, ataque, acerto automatico). Quem rola (programa: local ou pela
 * rede; navegador: pelo servidor) entra pelo {@link Roller}.
 */
public final class CombatFlow {

    private CombatFlow() {
    }

    /** Rola e, quando o resultado sair, chama {@code after} (pode ser null). */
    public interface Roller {
        void roll(String label, String expression, boolean hidden, Consumer<RollResult> after);
    }

    /** Tipo de dano citado no rotulo DEPOIS de "Dano" (o nome do personagem nao conta). */
    public static String damageTypeIn(String label) {
        if (label == null) return null;
        int at = label.indexOf("Dano");
        return DamageTypes.find(at >= 0 ? label.substring(at) : label);
    }

    /** Dano de arma + bonus dos efeitos ativos (Furia +2). Outras rolagens ficam iguais. */
    public static String withDamageBonus(String label, String expression, List<String> effects) {
        if (label == null || !label.contains("Dano")) return expression;
        return expression + EffectModifiers.damageSuffix(effects);
    }

    /**
     * Rolagem de dano de uma fonte (ficha ou monstro): se o ultimo ataque
     * desta fonte acertou alguem, o dano cai neles (com resistencias); se foi
     * critico, os dados dobram sozinhos.
     */
    public static void rollFromSource(TableService table, Roller roller, String sourceKey, String label,
                                      String expression, boolean hidden) {
        boolean damage = label.contains("Dano");
        if (damage && !label.contains("CRITICO") && table.lastAttackWasCritical(sourceKey)) {
            expression = DiceRoller.doubleDice(expression);
            label = label.replace("Dano:", "Dano CRÍTICO:");
        }
        if (damage && table.hasPendingAttack(sourceKey)) {
            String type = damageTypeIn(label);
            String finalLabel = label;
            roller.roll(label, expression, hidden, r -> {
                List<TokenModel> hits = table.takeHits(sourceKey);
                if (!hits.isEmpty()) table.damageTargets(hits, r.total(), type, finalLabel);
            });
        } else {
            roller.roll(label, expression, hidden, null);
        }
    }

    /**
     * Magia do catalogo (Bencao, Escudo...) vira efeito com duracao: nos alvos,
     * ou no token da propria ficha. Devolve um aviso (ou null) pra mostrar.
     */
    public static String applySpellEffect(TableService table, List<TokenModel> sceneTokens, List<TokenModel> targets,
                                          CharacterSheet sheet, PreparedSpell spell) {
        int rounds = ClassResources.spellRounds(spell.getName());
        if (rounds <= 0 || EffectModifiers.termsOf(spell.getName()) == null) return null;
        List<TokenModel> self = sceneTokens.stream().filter(t -> sheet.getId().equals(t.getCharacterSheetId())).toList();
        List<TokenModel> who = ClassResources.selfOnly(spell.getName()) || targets.isEmpty() ? self : targets;
        if (who.isEmpty()) {
            return spell.getName() + ": ponha o token de " + sheet.getName() + " no mapa (ou mire os alvos) pra o efeito"
                    + " entrar sozinho.";
        }
        table.applySpellEffect(who, spell.getName(), rounds);
        return null;
    }

    /** O que os dados da magia fazem: cura, area com salvaguarda, ataque, ou acerto automatico. */
    public static void castDice(TableService table, Roller roller, List<TokenModel> targets, CharacterSheet sheet,
                                PreparedSpell spell, int slotLevel, String dice, boolean hidden) {
        String key = "sheet:" + sheet.getId();
        String level = spell.getLevel() > 0 && slotLevel > spell.getLevel() ? " (nivel " + slotLevel + ")" : "";
        if (spell.isHealing()) {
            roller.roll(sheet.getName() + " - Cura: " + spell.getName() + level, dice, hidden,
                    targets.isEmpty() ? null : r -> table.healTargets(targets, r.total(), spell.getName()));
            return;
        }
        String type = spell.getDamageType().isBlank() ? null : spell.getDamageType();
        String label = sheet.getName() + " - Dano: " + spell.getName() + level
                + (type == null ? "" : " (" + DamageTypes.namePt(type) + ")");
        Ability save = spell.saveAbilityOrNull();
        if (save != null && !targets.isEmpty()) {
            // Magia de area: os alvos rolam a salvaguarda contra a CD da ficha.
            roller.roll(label, dice, hidden, r -> table.saveTargets(targets, save, sheet.spellSaveDc(),
                    r.total(), type, spell.isHalfOnSave(), spell.getName()));
        } else if (spell.isSpellAttack() || save != null) {
            rollFromSource(table, roller, key, label, dice, hidden); // cai em quem o "Ataque de magia" acertou
        } else {
            // Sem ataque nem salvaguarda (Misseis Magicos): acerta os alvos direto.
            roller.roll(label, dice, hidden,
                    targets.isEmpty() ? null : r -> table.damageTargets(targets, r.total(), type, spell.getName()));
        }
    }

    /**
     * Gasta o espaco (o do nivel pedido, ou o menor disponivel >= nivel da
     * magia), cuida da concentracao e devolve o texto pro chat. null = sem
     * espaco sobrando (nada muda).
     */
    public static String spendForCast(CharacterSheet sheet, PreparedSpell spell, int wantedSlot) {
        SpellSlots slots = sheet.getSpellSlots();
        int slot = 0;
        if (spell.getLevel() > 0) {
            int from = Math.max(spell.getLevel(), wantedSlot);
            for (int lvl = from; lvl <= SpellSlots.MAX_LEVEL; lvl++) {
                if (slots.getCurrent(lvl) > 0) {
                    slot = lvl;
                    break;
                }
            }
            if (slot == 0) return null;
            slots.useSlot(slot);
        }
        String extra = slot > spell.getLevel() && spell.getLevel() > 0 ? " no nível " + slot : "";
        if (spell.isConcentration()) {
            if (sheet.getConcentratingOn() != null && !sheet.getConcentratingOn().equalsIgnoreCase(spell.getName())) {
                extra += " (encerra a concentração em " + sheet.getConcentratingOn() + ")";
            }
            sheet.setConcentratingOn(spell.getName());
        }
        return sheet.getName() + " conjura " + spell.toChatText() + extra;
    }

    /** Nivel do espaco que {@link #spendForCast} vai usar (pra rolar os dados certos). */
    public static int slotFor(CharacterSheet sheet, PreparedSpell spell, int wantedSlot) {
        if (spell.getLevel() <= 0) return 0;
        for (int lvl = Math.max(spell.getLevel(), wantedSlot); lvl <= SpellSlots.MAX_LEVEL; lvl++) {
            if (sheet.getSpellSlots().getCurrent(lvl) > 0) return lvl;
        }
        return 0;
    }
}
