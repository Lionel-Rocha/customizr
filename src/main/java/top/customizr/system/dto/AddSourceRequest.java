package top.customizr.system.dto;

import java.util.UUID;

public record AddSourceRequest(
        UUID sourceUuid,
        UUID campaignUuid
) {
}
