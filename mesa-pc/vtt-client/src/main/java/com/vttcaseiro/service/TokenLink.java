package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Condition;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regras da ligacao token &lt;-&gt; ficha, sem nada de tela/rede (testavel).
 * <p>
 * A FICHA e a fonte da verdade de um token ligado: PV, PV temporario,
 * condicoes e efeitos do token sao sempre copiados dela. Mexer no token
 * pelo mapa (dano, cura, condicoes) aplica na ficha e a ficha empurra de
 * volta pro token -- assim os dois nunca discordam.
 */
public final class TokenLink {

    private TokenLink() {
    }

    /**
     * Copia o estado de jogo da ficha pro token. Devolve true se algo mudou
     * (so ai vale a pena mandar update_token pela rede).
     */
    public static boolean pushSheetToToken(CharacterSheet sheet, TokenModel token) {
        boolean changed = false;
        if (!Objects.equals(token.getName(), sheet.getName())) {
            token.setName(sheet.getName());
            changed = true;
        }
        if (!Objects.equals(token.getCurrentHp(), sheet.getHpCurrent())) {
            token.setCurrentHp(sheet.getHpCurrent());
            changed = true;
        }
        if (!Objects.equals(token.getMaxHp(), sheet.getHpMax())) {
            token.setMaxHp(sheet.getHpMax());
            changed = true;
        }
        Integer temp = sheet.getHpTemp() > 0 ? sheet.getHpTemp() : null;
        if (!Objects.equals(token.getTempHp(), temp)) {
            token.setTempHp(temp);
            changed = true;
        }
        if (!token.getConditions().equals(sheet.getConditions())) {
            token.setConditions(new ArrayList<>(sheet.getConditions()));
            changed = true;
        }
        List<String> effects = tokenEffectsFor(sheet);
        if (!token.getEffects().equals(effects)) {
            token.setEffects(effects);
            changed = true;
        }
        // retrato da ficha = imagem do token (trocou na ficha, troca no mapa)
        if (sheet.getPortraitImageAssetId() != null && !Objects.equals(token.getImageAssetId(), sheet.getPortraitImageAssetId())) {
            token.setImageAssetId(sheet.getPortraitImageAssetId());
            if (sheet.getPortraitImagePath() != null) token.setImagePath(sheet.getPortraitImagePath());
            changed = true;
        }
        return changed;
    }

    /** Efeitos que aparecem no token: os livres da ficha + concentracao e
     * exaustao (que na ficha sao campos proprios). */
    public static List<String> tokenEffectsFor(CharacterSheet sheet) {
        List<String> effects = new ArrayList<>(sheet.getEffects());
        if (sheet.getConcentratingOn() != null) {
            effects.add("Concentrando: " + sheet.getConcentratingOn());
        }
        if (sheet.getExhaustionLevel() > 0) {
            effects.add("Exaustao " + sheet.getExhaustionLevel());
        }
        return effects;
    }

    /** Token novo de uma ficha (arrastar pro mapa). */
    public static TokenModel tokenFromSheet(CharacterSheet sheet) {
        TokenModel token = new TokenModel(sheet.getName(), sheet.getPortraitImagePath());
        token.setCharacterSheetId(sheet.getId());
        token.setImageAssetId(sheet.getPortraitImageAssetId());
        token.setInitiativeBonus(sheet.initiative());
        int cells = cellsForSize(sheet.getSize());
        token.setWidthCells(cells);
        token.setHeightCells(cells);
        pushSheetToToken(sheet, token);
        return token;
    }

    /** Token novo de um monstro: PV medio, bonus de iniciativa = mod. de
     * DES, tamanho em celulas pelo tamanho da criatura, PV escondido dos
     * jogadores (so o mestre ve os numeros). */
    public static TokenModel tokenFromMob(MobSheet mob, String displayName) {
        TokenModel token = new TokenModel(displayName, mob.getPortraitImagePath());
        token.setMobSheetId(mob.getId());
        token.setImageAssetId(mob.getPortraitImageAssetId());
        token.setImageData(mob.getImageData());
        token.setMaxHp(mob.getHitPointsAverage());
        token.setCurrentHp(mob.getHitPointsAverage());
        token.setInitiativeBonus(CharacterSheet.abilityModifier(mob.getDexterity()));
        int cells = cellsForSize(mob.getSize());
        token.setWidthCells(cells);
        token.setHeightCells(cells);
        token.setHpVisibleToAll(false);
        token.setVisionFeet(com.vttcaseiro.vision.Visibility.darkvisionFeet(mob.getSenses()));
        return token;
    }

    /** Pequeno/Medio = 1, Grande = 2, Enorme = 3, Imenso = 4 celulas. */
    public static int cellsForSize(String size) {
        if (size == null) return 1;
        String s = size.toLowerCase();
        if (s.startsWith("gr") || s.startsWith("la") || s.equals("l")) return 2;
        if (s.startsWith("en") || s.startsWith("hu") || s.equals("h")) return 3;
        if (s.startsWith("im") || s.startsWith("ga") || s.startsWith("gi") || s.equals("g")) return 4;
        return 1;
    }

    /**
     * Dano direto num token SEM ficha (monstro/NPC): tira do PV temporario,
     * depois do atual; a 0 PV ganha Inconsciente. Devolve o texto pro chat.
     */
    public static String damageUnlinkedToken(TokenModel token, int amount) {
        int temp = token.getTempHp() == null ? 0 : token.getTempHp();
        int fromTemp = Math.min(temp, amount);
        token.setTempHp(temp - fromTemp > 0 ? temp - fromTemp : null);
        int rest = amount - fromTemp;
        if (token.getCurrentHp() == null) {
            return token.getName() + " sofre " + amount + " de dano.";
        }
        int before = token.getCurrentHp();
        int after = Math.max(0, before - rest);
        token.setCurrentHp(after);
        if (after == 0 && before > 0) {
            if (!token.hasCondition(Condition.UNCONSCIOUS)) token.getConditions().add(Condition.UNCONSCIOUS.name());
            return token.getName() + " sofre " + amount + " de dano e cai (0 PV)!";
        }
        return token.getName() + " sofre " + amount + " de dano.";
    }

    public static String healUnlinkedToken(TokenModel token, int amount) {
        if (token.getCurrentHp() == null) {
            return token.getName() + " recupera " + amount + " PV.";
        }
        int before = token.getCurrentHp();
        int after = before + amount;
        if (token.getMaxHp() != null) after = Math.min(token.getMaxHp(), after);
        token.setCurrentHp(after);
        if (before == 0 && after > 0) token.getConditions().remove(Condition.UNCONSCIOUS.name());
        return token.getName() + " recupera " + (after - before) + " PV.";
    }

    /** Texto do chat depois de aplicar dano numa ficha. */
    public static String describeDamage(String name, int amount, CharacterSheet.DamageOutcome outcome) {
        StringBuilder sb = new StringBuilder(name).append(" sofre ").append(amount).append(" de dano");
        if (outcome.absorbedByTemp() > 0) {
            sb.append(" (").append(outcome.absorbedByTemp()).append(" no PV temporario)");
        }
        sb.append(".");
        if (outcome.instantDeath()) {
            sb.append(" DANO MASSIVO: o dano restante passou do PV maximo -- morte instantanea!");
        } else if (outcome.droppedToZero()) {
            sb.append(" Caiu a 0 PV e esta Inconsciente. Testes contra a morte no inicio do turno.");
        } else if (outcome.damagedWhileDown()) {
            sb.append(" Ja estava caido: conta como 1 falha no teste contra a morte.");
        }
        if (outcome.concentrationDc() != null) {
            sb.append(" Teste de concentracao (CON): CD ").append(outcome.concentrationDc()).append(".");
        }
        return sb.toString();
    }
}
