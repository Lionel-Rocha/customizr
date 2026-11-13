package top.customizr.system.dto;

import top.customizr.system.model.Party;

import java.util.List;
import java.util.UUID;

public record PartyResponse(UUID partyId, String name, UUID campaignId, List<UUID> characterIds) {
    public static PartyResponse from(Party party) {
        return new PartyResponse(
                party.getPartyId(),
                party.getName(),
                party.getCampaign().getId(),
                party.getMembers().stream().map(character -> character.getCharacterId()).toList()
        );
    }
}
