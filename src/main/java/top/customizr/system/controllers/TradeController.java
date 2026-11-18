package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.dto.LootItemRequest;
import top.customizr.system.dto.LootRequest;
import top.customizr.system.dto.LootResponse;
import top.customizr.system.dto.ShopItemRequest;
import top.customizr.system.dto.ShopRequest;
import top.customizr.system.dto.ShopResponse;
import top.customizr.system.dto.SplitMoneyRequest;
import top.customizr.system.dto.TradeRequest;
import top.customizr.system.service.TradeService;

import java.util.List;
import java.util.UUID;

/** Lojas e espolio da campanha. */
@RestController
public class TradeController {

    private final TradeService trade;

    public TradeController(TradeService trade) {
        this.trade = trade;
    }

    // ------------------------------------------------------------------ lojas

    @GetMapping("/shops/campaign/{campaignId}")
    public List<ShopResponse> shops(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails user) {
        return trade.shops(campaignId, user.getUsername());
    }

    @PostMapping("/shops/campaign/{campaignId}")
    public ResponseEntity<ShopResponse> createShop(@PathVariable UUID campaignId, @RequestBody ShopRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trade.createShop(campaignId, request, user.getUsername()));
    }

    @PutMapping("/shops/{shopId}")
    public ShopResponse updateShop(@PathVariable UUID shopId, @RequestBody ShopRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.updateShop(shopId, request, user.getUsername());
    }

    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<Void> deleteShop(@PathVariable UUID shopId, @AuthenticationPrincipal UserDetails user) {
        trade.deleteShop(shopId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shops/{shopId}/items")
    public ShopResponse addShopItem(@PathVariable UUID shopId, @RequestBody ShopItemRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.addShopItem(shopId, request, user.getUsername());
    }

    @PutMapping("/shops/{shopId}/items/{entryId}")
    public ShopResponse updateShopItem(@PathVariable UUID shopId, @PathVariable UUID entryId, @RequestBody ShopItemRequest request,
                                       @AuthenticationPrincipal UserDetails user) {
        return trade.updateShopItem(shopId, entryId, request, user.getUsername());
    }

    @DeleteMapping("/shops/{shopId}/items/{entryId}")
    public ShopResponse removeShopItem(@PathVariable UUID shopId, @PathVariable UUID entryId, @AuthenticationPrincipal UserDetails user) {
        return trade.removeShopItem(shopId, entryId, user.getUsername());
    }

    /** Compra pelo personagem ({@code characterId}, {@code entryId}, {@code quantity}). */
    @PostMapping("/shops/{shopId}/buy")
    public InventoryResponse buy(@PathVariable UUID shopId, @RequestBody TradeRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.buy(shopId, request, user.getUsername());
    }

    /** Vende um item do inventario do personagem ({@code characterId}, {@code itemId}, {@code quantity}). */
    @PostMapping("/shops/{shopId}/sell")
    public InventoryResponse sell(@PathVariable UUID shopId, @RequestBody TradeRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.sell(shopId, request, user.getUsername());
    }

    // ------------------------------------------------------------------ espolio

    @GetMapping("/loot/campaign/{campaignId}")
    public List<LootResponse> loot(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails user) {
        return trade.loot(campaignId, user.getUsername());
    }

    @PostMapping("/loot/campaign/{campaignId}")
    public ResponseEntity<LootResponse> createLoot(@PathVariable UUID campaignId, @RequestBody LootRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trade.createLoot(campaignId, request, user.getUsername()));
    }

    @PutMapping("/loot/{lootId}")
    public LootResponse updateLoot(@PathVariable UUID lootId, @RequestBody LootRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.updateLoot(lootId, request, user.getUsername());
    }

    @DeleteMapping("/loot/{lootId}")
    public ResponseEntity<Void> deleteLoot(@PathVariable UUID lootId, @AuthenticationPrincipal UserDetails user) {
        trade.deleteLoot(lootId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/loot/{lootId}/items")
    public LootResponse addLootItem(@PathVariable UUID lootId, @RequestBody LootItemRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.addLootItem(lootId, request, user.getUsername());
    }

    @DeleteMapping("/loot/{lootId}/items/{entryId}")
    public LootResponse removeLootItem(@PathVariable UUID lootId, @PathVariable UUID entryId, @AuthenticationPrincipal UserDetails user) {
        return trade.removeLootItem(lootId, entryId, user.getUsername());
    }

    /** Pega do espolio pro personagem ({@code toParty}: pro grupo). */
    @PostMapping("/loot/{lootId}/take")
    public InventoryResponse take(@PathVariable UUID lootId, @RequestBody TradeRequest request, @AuthenticationPrincipal UserDetails user) {
        return trade.take(lootId, request, user.getUsername());
    }

    /** Divide as moedas igualmente (so o mestre). */
    @PostMapping("/loot/{lootId}/split-money")
    public LootResponse splitMoney(@PathVariable UUID lootId, @RequestBody(required = false) SplitMoneyRequest request,
                                   @AuthenticationPrincipal UserDetails user) {
        return trade.splitMoney(lootId, request, user.getUsername());
    }
}
