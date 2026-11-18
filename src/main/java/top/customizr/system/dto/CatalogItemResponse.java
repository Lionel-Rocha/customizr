package top.customizr.system.dto;

import top.customizr.system.model.Item;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item visto de dentro da campanha. {@code origin}: OFFICIAL (livro da Wizards), UNOFFICIAL (parceiros, terceiros),
 * UNKNOWN (fonte cadastrada a mao) ou CUSTOM (item proprio do mestre). {@code priceGp} ja e o valor que vale na
 * campanha (oficial, ou o que o mestre deu).
 */
public record CatalogItemResponse(UUID id, String name, String source, String sourceName, String origin, String type,
                                  String category, String rarity, BigDecimal priceGp, String referenceLink) {

    public static CatalogItemResponse from(Item item, BigDecimal price) {
        return new CatalogItemResponse(item.getId(), item.getName(), item.getSource().getCode(), item.getSource().getName(),
                origin(item), item.getType() == null ? null : item.getType().name(),
                item.getCategory() == null ? null : item.getCategory().name(),
                item.getRarity() == null ? null : item.getRarity().name(), price, item.getReferenceLink());
    }

    public static String origin(Item item) {
        if (item.getCampaign() != null) return "CUSTOM";
        Boolean official = item.getSource().getOfficial();
        return official == null ? "UNKNOWN" : official ? "OFFICIAL" : "UNOFFICIAL";
    }
}
