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
        String spellcasting,
        UUID campaignId, boolean custom, java.util.List<MonsterAction> customActions
) {
    public static MonsterResponse from(Monster m) {
        return new MonsterResponse(m.getId(), m.getName(), m.getSource().getCode(), m.getSize(), m.getCreatureType(),
                m.getAlignment(), m.getArmorClass(), m.getArmorNote(), m.getHpAverage(), m.getHpFormula(), m.getSpeed(),
                m.getStrength(), m.getDexterity(), m.getConstitution(), m.getIntelligence(), m.getWisdom(), m.getCharisma(),
                m.getSavingThrows(), m.getSkills(), m.getDamageVulnerabilities(), m.getDamageResistances(),
                m.getDamageImmunities(), m.getConditionImmunities(), m.getSenses(), m.getPassivePerception(),
                m.getLanguages(), m.getChallengeRating(), m.getExperiencePoints(), m.getProficiencyBonus(),
                m.getTraits(), m.getActions(), m.getBonusActions(), m.getReactions(), m.getLegendaryActions(),
                m.getSpellcasting(),
                m.getCampaign() == null ? null : m.getCampaign().getId(), m.isCustom(), customActions(m));
    }

    /** Acoes como foram digitadas na ficha customizada (pra reabrir o editor igual). */
    @SuppressWarnings("unchecked")
    private static java.util.List<MonsterAction> customActions(Monster m) {
        if (m.getMetadata() == null || !(m.getMetadata().get("customActions") instanceof java.util.List<?> list)) {
            return java.util.List.of();
        }
        java.util.List<MonsterAction> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof java.util.Map<?, ?> a)) continue;
            out.add(new MonsterAction(str(a.get("name")), str(a.get("kind")), num(a.get("attackBonus")),
                    a.get("melee") instanceof Boolean b ? b : null, str(a.get("reach")), num(a.get("saveDc")),
                    str(a.get("saveAbility")), str(a.get("damage")), str(a.get("damageType")), str(a.get("text"))));
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer num(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    /** Versao curta pra listas. */
    public record Summary(UUID id, String name, String source, String creatureType, String challengeRating,
                          Integer armorClass, Integer hpAverage, boolean custom) {
        public static Summary from(Monster m) {
            return new Summary(m.getId(), m.getName(), m.getSource().getCode(), m.getCreatureType(),
                    m.getChallengeRating(), m.getArmorClass(), m.getHpAverage(), m.isCustom());
        }
    }
}
