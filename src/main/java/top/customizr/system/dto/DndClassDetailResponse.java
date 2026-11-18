package top.customizr.system.dto;

import top.customizr.system.model.ClassFeature;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.Spell;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.SubclassFeature;
import top.customizr.system.model.SubclassSpell;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record DndClassDetailResponse(
        UUID classId,
        String name,
        String source,
        String edition,
        Integer page,
        Boolean srd,
        int hitDieNumber,
        int hitDie,
        int subclassUnlockLevel,
        String spellcastingAbility,
        String casterProgression,
        String preparedSpells,
        String preparedSpellsChange,
        Map<String, Object> metadata,
        List<FeatureGrant> features,
        List<SpellDetail> availableSpells,
        List<SubclassDetail> subclasses
) {
    public static DndClassDetailResponse from(DndClass dndClass) {
        return new DndClassDetailResponse(
                dndClass.getClassId(),
                dndClass.getClassName(),
                dndClass.getSourceCode(),
                dndClass.getEdition(),
                dndClass.getPage(),
                dndClass.getSrd(),
                dndClass.getHitDieNumber(),
                dndClass.getHitDie(),
                dndClass.getSubclassUnlockLevel(),
                dndClass.getSpellcastingAbility(),
                dndClass.getCasterProgression(),
                dndClass.getPreparedSpells(),
                dndClass.getPreparedSpellsChange(),
                dndClass.getMetadata(),
                dndClass.getFeatures().stream()
                        .sorted(Comparator.comparingInt(ClassFeature::getLevel)
                                .thenComparing(grant -> grant.getFeature().getName(),
                                        String.CASE_INSENSITIVE_ORDER))
                        .map(FeatureGrant::from)
                        .toList(),
                dndClass.getAvailableSpells().stream()
                        .sorted(Comparator.comparing(Spell::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(SpellDetail::from)
                        .toList(),
                dndClass.getSubclasses().stream()
                        .sorted(Comparator.comparingInt(Subclass::getUnlockLevel)
                                .thenComparing(Subclass::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(SubclassDetail::from)
                        .toList()
        );
    }

    public record FeatureGrant(
            UUID featureId,
            String name,
            String description,
            String category,
            String type,
            SourceResponse source,
            String sourceCode,
            int level,
            String choiceGroup,
            Map<String, Object> metadata
    ) {
        private static FeatureGrant from(ClassFeature grant) {
            return from(grant.getFeature(), grant.getLevel(), grant.getChoiceGroup());
        }

        private static FeatureGrant from(SubclassFeature grant) {
            return from(grant.getFeature(), grant.getLevel(), grant.getChoiceGroup());
        }

        private static FeatureGrant from(Feature feature, int level, String choiceGroup) {
            return new FeatureGrant(
                    feature.getFeatureId(),
                    feature.getName(),
                    feature.getDescription(),
                    feature.getCategory(),
                    feature.getType().name(),
                    sourceOf(feature.getSource()),
                    feature.getSourceCode(),
                    level,
                    choiceGroup,
                    feature.getMetadata()
            );
        }
    }

    public record SubclassDetail(
            UUID subclassId,
            String name,
            String shortName,
            String sourceCode,
            int unlockLevel,
            SourceResponse source,
            Map<String, Object> metadata,
            List<FeatureGrant> features,
            List<SubclassSpellGrant> spells
    ) {
        private static SubclassDetail from(Subclass subclass) {
            return new SubclassDetail(
                    subclass.getSubclassId(),
                    subclass.getName(),
                    subclass.getShortName(),
                    subclass.getSourceCode(),
                    subclass.getUnlockLevel(),
                    sourceOf(subclass.getSource()),
                    subclass.getMetadata(),
                    subclass.getFeatures().stream()
                            .sorted(Comparator.comparingInt(SubclassFeature::getLevel)
                                    .thenComparing(grant -> grant.getFeature().getName(),
                                            String.CASE_INSENSITIVE_ORDER))
                            .map(FeatureGrant::from)
                            .toList(),
                    subclass.getSpells().stream()
                            .sorted(Comparator.comparing(
                                            SubclassSpell::getGrantedAtLevel,
                                            Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(grant -> grant.getSpell().getName(),
                                            String.CASE_INSENSITIVE_ORDER))
                            .map(SubclassSpellGrant::from)
                            .toList()
            );
        }
    }

    public record SubclassSpellGrant(
            SpellDetail spell,
            String grantType,
            Integer grantedAtLevel,
            Map<String, Object> metadata
    ) {
        private static SubclassSpellGrant from(SubclassSpell grant) {
            return new SubclassSpellGrant(
                    SpellDetail.from(grant.getSpell()),
                    grant.getGrantType().name(),
                    grant.getGrantedAtLevel(),
                    grant.getMetadata()
            );
        }
    }

    public record SpellDetail(
            UUID spellId,
            String name,
            String school,
            String level,
            String castingTime,
            int rangeFt,
            Set<String> components,
            DurationDetail duration,
            SourceResponse source,
            String description
    ) {
        private static SpellDetail from(Spell spell) {
            return new SpellDetail(
                    spell.getSpellId(),
                    spell.getName(),
                    spell.getSchool().name(),
                    spell.getLevel().name(),
                    spell.getCastingTime().name(),
                    spell.getRangeFt(),
                    spell.getComponents().stream().map(Enum::name)
                            .collect(java.util.stream.Collectors.toSet()),
                    new DurationDetail(
                            spell.getDuration().getAmount(),
                            spell.getDuration().getUnit().name(),
                            spell.getDuration().isConcentration()
                    ),
                    sourceOf(spell.getSource()),
                    spell.getDescription()
            );
        }
    }

    public record DurationDetail(int amount, String unit, boolean concentration) {}

    private static SourceResponse sourceOf(top.customizr.system.model.Source source) {
        return source == null ? null : SourceResponse.from(source);
    }
}
