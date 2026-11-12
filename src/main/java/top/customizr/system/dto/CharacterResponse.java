package top.customizr.system.dto;

import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterStatus;

import java.util.UUID;

public record CharacterResponse(
        UUID characterId,
        String name,
        CharacterStatus status,
        UUID campaignId,
        UUID ownerId
) {
    public static CharacterResponse from(Character character) {
        return new CharacterResponse(
                character.getCharacterId(),
                character.getName(),
                character.getStatus(),
                character.getCampaign().getId(),
                character.getOwner().getUserId()
        );
    }
}
