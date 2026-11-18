package top.customizr.system.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.ItemPriceRequest;
import top.customizr.system.dto.ItemPriceResponse;
import top.customizr.system.service.ItemPriceService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Precos que o mestre da aos itens sem preco oficial, por campanha. */
@RestController
@RequestMapping("/item/prices/campaign/{campaignId}")
public class ItemPriceController {

    private final ItemPriceService prices;

    public ItemPriceController(ItemPriceService prices) {
        this.prices = prices;
    }

    @GetMapping
    public Map<UUID, BigDecimal> campaignPrices(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails user) {
        return prices.campaignPrices(campaignId, user.getUsername());
    }

    @GetMapping("/unpriced")
    public List<ItemPriceResponse> unpriced(@PathVariable UUID campaignId, @RequestParam(required = false) String name,
                                            @RequestParam(defaultValue = "false") boolean includeMagic,
                                            @AuthenticationPrincipal UserDetails user) {
        return prices.unpriced(campaignId, name, includeMagic, user.getUsername());
    }

    @PutMapping("/{itemId}")
    public ItemPriceResponse set(@PathVariable UUID campaignId, @PathVariable UUID itemId,
                                 @RequestBody(required = false) ItemPriceRequest request,
                                 @AuthenticationPrincipal UserDetails user) {
        return prices.set(campaignId, itemId, request == null ? null : request.priceGp(), user.getUsername());
    }
}
