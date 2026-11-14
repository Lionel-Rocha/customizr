package top.customizr.system.dto;

import top.customizr.system.model.NPC;

import java.util.UUID;

public record NpcResponse(
        UUID id,
        String name,
        String personality,
        String masterInformation,
        String photoUrl,
        UUID campaignId
) {
    public static NpcResponse from(NPC npc, boolean includeMasterInformation) {
        return new NpcResponse(
                npc.getId(),
                npc.getName(),
                npc.getPersonality(),
                includeMasterInformation ? npc.getMasterInformation() : null,
                npc.getPhotoUrl(),
                npc.getCampaign().getId()
        );
    }
}
