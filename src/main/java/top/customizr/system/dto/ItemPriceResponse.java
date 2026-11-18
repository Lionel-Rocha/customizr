package top.customizr.system.dto;

import top.customizr.system.model.Item;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do catalogo com o preco oficial (se houver) e o preco que o mestre deu na campanha. */
public record ItemPriceResponse(UUID itemId, String name, String source, String type, String category, String rarity,
                                BigDecimal officialPriceGp, BigDecimal campaignPriceGp) {

    public static ItemPriceResponse from(Item item, BigDecimal campaignPrice) {
        return new ItemPriceResponse(item.getId(), item.getName(), item.getSource().getCode(),
                item.getType() == null ? null : item.getType().name(),
                item.getCategory() == null ? null : item.getCategory().name(),
                item.getRarity() == null ? null : item.getRarity().name(), item.getPriceGp(), campaignPrice);
    }
}
