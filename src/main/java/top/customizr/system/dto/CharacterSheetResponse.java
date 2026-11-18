package top.customizr.system.dto;

import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.CharacterFeatureSelection;
import top.customizr.system.model.CharacterStatus;
import top.customizr.system.model.Party;
import top.customizr.system.model.Subclass;

import java.util.List;
import java.util.UUID;

public record CharacterSheetResponse(
        UUID characterId,
        String name,
        CharacterStatus status,
        UUID campaignId,
        UUID ownerId,
        List<CharacterClassInfo> classes,
        AttributesResponse attributes,
        PartyInfo party,
        CombatInfo combat,
        DetailsInfo details
) {
    public static CharacterSheetResponse from(Character character) {
        return new CharacterSheetResponse(
                character.getCharacterId(),
                character.getName(),
                character.getStatus(),
                character.getCampaign().getId(),
                character.getOwner().getUserId(),
                character.getSheet().getClasses().stream().map(CharacterClassInfo::from).toList(),
                character.getSheet().getAttributes() == null
                        ? null
                        : AttributesResponse.from(character.getSheet().getAttributes()),
                character.getParty() == null ? null : PartyInfo.from(character.getParty()),
                CombatInfo.from(character.getSheet()),
                DetailsInfo.from(character)
        );
    }

    /** Ficha completa: origem, XP, pericias, salvaguardas, condicoes, espacos de magia, dados de vida, ataques. */
    public record DetailsInfo(
            String portraitUrl,
            SimpleRef species,
            SimpleRef background,
            int experiencePoints,
            int levelByExperience,
            Integer experienceToNextLevel,
            List<SkillInfo> skills,
            List<SaveInfo> savingThrows,
            java.util.Set<top.customizr.system.model.Condition> conditions,
            int exhaustion,
            int deathSaveSuccesses,
            int deathSaveFailures,
            Integer currentSpeedFt,
            List<SlotInfo> spellSlots,
            int hitDiceRemaining,
            int hitDiceTotal,
            List<AttackInfo> attacks,
            List<CatalogFeatInfo> feats,
            List<ResourceInfo> resources,
            boolean heroicInspiration
    ) {
        private static DetailsInfo from(Character character) {
            top.customizr.system.model.Sheet sheet = character.getSheet();
            List<SlotInfo> slots;
            try {
                slots = sheet.getSpellSlots().stream()
                        .map(s -> new SlotInfo(s.pool().name(), s.className(), s.spellLevel(), s.totalSlots(),
                                sheet.spentSlots(s.pool(), s.spellLevel())))
                        .toList();
            } catch (IllegalArgumentException ex) {
                slots = List.of();
            }
            return new DetailsInfo(
                    character.getPortraitUrl(),
                    character.getSpecies() == null ? null
                            : new SimpleRef(character.getSpecies().getId(), character.getSpecies().getName()),
                    character.getBackground() == null ? null
                            : new SimpleRef(character.getBackground().getId(), character.getBackground().getName()),
                    character.getExperiencePoints(),
                    character.getLevelByExperience(),
                    character.getExperienceToNextLevel(),
                    java.util.Arrays.stream(top.customizr.system.model.Skill.values())
                            .map(s -> new SkillInfo(s, s.getLabel(), s.getAbility(), sheet.skillBonus(s),
                                    sheet.getSkillProficiencies().contains(s), sheet.getSkillExpertise().contains(s)))
                            .toList(),
                    java.util.Arrays.stream(top.customizr.system.model.AbilityScore.values())
                            .map(a -> new SaveInfo(a, sheet.savingThrowBonus(a), sheet.getSavingThrowProficiencies().contains(a)))
                            .toList(),
                    java.util.Set.copyOf(sheet.getConditions()),
                    sheet.getExhaustion(),
                    sheet.getDeathSaveSuccesses(),
                    sheet.getDeathSaveFailures(),
                    sheet.getCurrentSpeedFt(),
                    slots,
                    sheet.getHitDiceRemaining(),
                    sheet.getTotalLevel(),
                    sheet.getAttacks().stream().map(a -> new AttackInfo(a.getId(), a.getName(), a.getAttackBonus(),
                            a.getDamage(), a.getDamageType(), a.getNotes())).toList(),
                    sheet.getCatalogFeats().stream().map(f -> new CatalogFeatInfo(f.getId(), f.getFeat().getId(),
                            f.getFeat().getName(), f.getCharacterLevel(), f.getChoices())).toList(),
                    sheet.getResources().stream().map(r -> new ResourceInfo(r.id(), r.name(), r.className(), r.max(),
                            r.spent(), r.remaining(), r.recharge(), r.unit(), r.custom(), r.notes())).toList(),
                    sheet.getHeroicInspiration()
            );
        }
    }

    /** Recurso com usos: {@code id} = chave da classe ("barbarian.rage") ou o id do recurso criado a mao. */
    public record ResourceInfo(String id, String name, String className, int max, int spent, int remaining,
                               top.customizr.system.model.ResourceRecharge recharge, String unit, boolean custom,
                               String notes) {}

    public record SimpleRef(UUID id, String name) {}

    public record SkillInfo(top.customizr.system.model.Skill skill, String label,
                            top.customizr.system.model.AbilityScore ability, int bonus, boolean proficient, boolean expertise) {}

    public record SaveInfo(top.customizr.system.model.AbilityScore ability, int bonus, boolean proficient) {}

    public record SlotInfo(String pool, String className, int level, int total, int spent) {}

    public record AttackInfo(UUID id, String name, int attackBonus, String damage, String damageType, String notes) {}

    public record CatalogFeatInfo(UUID id, UUID featId, String name, int characterLevel, String choices) {}

    /** PV, CA e o que sai das regras (nivel, proficiencia, iniciativa, percepcao passiva, PV sugerido). */
    public record CombatInfo(
            Integer hpMax,
            Integer hpCurrent,
            Integer hpTemp,
            Integer armorClass,
            Integer speedFt,
            int totalLevel,
            int proficiencyBonus,
            int initiative,
            int passivePerception,
            Integer suggestedHpMax
    ) {
        private static CombatInfo from(top.customizr.system.model.Sheet sheet) {
            return new CombatInfo(
                    sheet.getHpMax(),
                    sheet.getHpCurrent(),
                    sheet.getHpTemp(),
                    sheet.getArmorClass(),
                    sheet.getSpeedFt(),
                    sheet.getTotalLevel(),
                    sheet.getProficiencyBonus(),
                    sheet.getInitiative(),
                    sheet.getPassivePerception(),
                    sheet.getSuggestedHpMax()
            );
        }
    }

    public record CharacterClassInfo(
            UUID characterClassId,
            UUID classId,
            String className,
            int level,
            SubclassInfo subclass,
            List<SelectedFeatureInfo> feats
    ) {
        private static CharacterClassInfo from(CharacterClass characterClass) {
            return new CharacterClassInfo(
                    characterClass.getId(),
                    characterClass.getDndClass().getClassId(),
                    characterClass.getDndClass().getClassName(),
                    characterClass.getLevel(),
                    characterClass.getSubclass() == null
                            ? null
                            : SubclassInfo.from(
                                    characterClass.getSubclass(),
                                    characterClass.getSubclassChosenAtLevel()
                            ),
                    characterClass.getFeatureSelections().stream()
                            .map(SelectedFeatureInfo::from)
                            .toList()
            );
        }
    }

    public record SubclassInfo(UUID subclassId, String name, int chosenAtLevel) {
        private static SubclassInfo from(Subclass subclass, Integer chosenAtLevel) {
            return new SubclassInfo(subclass.getSubclassId(), subclass.getName(), chosenAtLevel);
        }
    }

    public record SelectedFeatureInfo(
            UUID featureId,
            String name,
            String description,
            int selectedAtLevel,
            int offeredAtLevel,
            String choiceGroup
    ) {
        private static SelectedFeatureInfo from(CharacterFeatureSelection selection) {
            return new SelectedFeatureInfo(
                    selection.getFeature().getFeatureId(),
                    selection.getFeature().getName(),
                    selection.getFeature().getDescription(),
                    selection.getLevel(),
                    selection.getOfferedAtLevel(),
                    selection.getChoiceGroup()
            );
        }
    }

    public record PartyInfo(UUID partyId, String name, List<PartyMemberInfo> members) {
        private static PartyInfo from(Party party) {
            return new PartyInfo(
                    party.getPartyId(),
                    party.getName(),
                    party.getMembers().stream()
                            .map(character -> new PartyMemberInfo(
                                    character.getCharacterId(),
                                    character.getName()
                            ))
                            .toList()
            );
        }
    }

    public record PartyMemberInfo(UUID characterId, String name) {}
}
