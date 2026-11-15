package top.customizr.system.dto;

import java.util.UUID;

public record ItemCreateRequest (
        String name,
        UUID sourceId,
        String type,
        String category,
        String referenceLink,
        String rarity,
        float priceGp,
        boolean custom
) {

}
