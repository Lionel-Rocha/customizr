package top.customizr.system.dto;

import top.customizr.system.model.Role;

import java.util.UUID;

public record InviteResponse(
        UUID campaignId,
        String campaignName,
        String masterName,
        Role role
) {
}
