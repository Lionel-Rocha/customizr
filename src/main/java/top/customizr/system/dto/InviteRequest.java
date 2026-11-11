package top.customizr.system.dto;

import top.customizr.system.model.Role;

import java.util.UUID;

public record InviteRequest(
        String username,
        UUID campaignId,
        Role role
) {
}
