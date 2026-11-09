package top.customizr.system.dto;

import java.util.UUID;

public record CreateCampaignRequest(
        UUID uuid,
        String name
) {
}
