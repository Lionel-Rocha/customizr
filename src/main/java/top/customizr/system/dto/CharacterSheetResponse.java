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
        PartyInfo party
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
                character.getParty() == null ? null : PartyInfo.from(character.getParty())
        );
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
