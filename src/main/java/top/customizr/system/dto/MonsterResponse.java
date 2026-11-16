package top.customizr.system.dto;

import top.customizr.system.model.Monster;

import java.util.UUID;

public record MonsterResponse(
        UUID id, String name, String source, String size, String creatureType, String alignment,
        Integer armorClass, String armorNote, Integer hpAverage, String hpFormula, String speed,
        int strength, int dexterity, int constitution, int intelligence, int wisdom, int charisma,
        String savingThrows, String skills, String damageVulnerabilities, String damageResistances,
        String damageImmunities, String conditionImmunities, String senses, Integer passivePerception,
        String languages, String challengeRating, Integer experiencePoints, int proficiencyBonus,
        String traits, String actions, String bonusActions, String reactions, String legendaryActions,
        String spellcasting
) {
    public static MonsterResponse from(Monster m) {
        return new MonsterResponse(m.getId(), m.getName(), m.getSource().getCode(), m.getSize(), m.getCreatureType(),
                m.getAlignment(), m.getArmorClass(), m.getArmorNote(), m.getHpAverage(), m.getHpFormula(), m.getSpeed(),
                m.getStrength(), m.getDexterity(), m.getConstitution(), m.getIntelligence(), m.getWisdom(), m.getCharisma(),
                m.getSavingThrows(), m.getSkills(), m.getDamageVulnerabilities(), m.getDamageResistances(),
                m.getDamageImmunities(), m.getConditionImmunities(), m.getSenses(), m.getPassivePerception(),
                m.getLanguages(), m.getChallengeRating(), m.getExperiencePoints(), m.getProficiencyBonus(),
                m.getTraits(), m.getActions(), m.getBonusActions(), m.getReactions(), m.getLegendaryActions(),
                m.getSpellcasting());
    }

    /** Versao curta pra listas. */
    public record Summary(UUID id, String name, String source, String creatureType, String challengeRating,
                          Integer armorClass, Integer hpAverage) {
        public static Summary from(Monster m) {
            return new Summary(m.getId(), m.getName(), m.getSource().getCode(), m.getCreatureType(),
                    m.getChallengeRating(), m.getArmorClass(), m.getHpAverage());
        }
    }
}
