package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ficha de monstro customizada (homebrew) da campanha. So o nome e obrigatorio: o resto em branco vira o
 * padrao (atributos 10, ND vazio). Os ataques em {@code actions} ja funcionam no token pela mesa.
 */
public record MonsterRequest(
        UUID campaignId, String name, String size, String creatureType, String alignment,
        Integer armorClass, String armorNote, Integer hpAverage, String hpFormula, String speed,
        Integer strength, Integer dexterity, Integer constitution, Integer intelligence, Integer wisdom, Integer charisma,
        String challengeRating, String savingThrows, String skills, String damageVulnerabilities,
        String damageResistances, String damageImmunities, String conditionImmunities, String senses,
        Integer passivePerception, String languages, String traits, List<MonsterAction> actions
) {}
