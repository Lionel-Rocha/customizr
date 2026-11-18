package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.CatalogItemResponse;
import top.customizr.system.dto.CustomItemRequest;
import top.customizr.system.dto.FiveToolsSyncResponse;
import top.customizr.system.service.CampaignItemService;
import top.customizr.system.service.FiveToolsSyncService;

import java.util.List;
import java.util.UUID;

/** Itens vistos da campanha: busca com filtro de origem, itens do mestre e atualizacao pelo 5etools. */
@RestController
@RequestMapping("/item/campaign/{campaignId}")
public class CampaignItemController {

    private final CampaignItemService items;
    private final FiveToolsSyncService fiveTools;

    public CampaignItemController(CampaignItemService items, FiveToolsSyncService fiveTools) {
        this.items = items;
        this.fiveTools = fiveTools;
    }

    @GetMapping("/search")
    public List<CatalogItemResponse> search(@PathVariable UUID campaignId, @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String origin,
                                            @AuthenticationPrincipal UserDetails user) {
        return items.search(campaignId, name, origin, user.getUsername());
    }

    @GetMapping("/custom")
    public List<CatalogItemResponse> custom(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails user) {
        return items.custom(campaignId, user.getUsername());
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse create(@PathVariable UUID campaignId, @RequestBody CustomItemRequest request,
                                      @AuthenticationPrincipal UserDetails user) {
        return items.create(campaignId, request, user.getUsername());
    }

    @PutMapping("/custom/{itemId}")
    public CatalogItemResponse update(@PathVariable UUID campaignId, @PathVariable UUID itemId,
                                      @RequestBody CustomItemRequest request, @AuthenticationPrincipal UserDetails user) {
        return items.update(campaignId, itemId, request, user.getUsername());
    }

    @DeleteMapping("/custom/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID campaignId, @PathVariable UUID itemId, @AuthenticationPrincipal UserDetails user) {
        items.delete(campaignId, itemId, user.getUsername());
    }

    /** Busca os itens no 5etools e atualiza o catalogo (so o mestre; nao oficiais so com includeUnofficial). */
    @PostMapping("/sync-5etools")
    public FiveToolsSyncResponse sync(@PathVariable UUID campaignId,
                                      @RequestParam(defaultValue = "false") boolean includeUnofficial,
                                      @AuthenticationPrincipal UserDetails user) {
        return fiveTools.sync(campaignId, includeUnofficial, user.getUsername());
    }
}
