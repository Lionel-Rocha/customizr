package top.customizr.system.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.InventoryItemRequest;
import top.customizr.system.dto.InventoryMoneyRequest;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.model.Character;
import top.customizr.system.service.InventoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Inventario do personagem e o da party dele (os que ele pode ver). */
    @GetMapping("/character/{characterId}")
    public List<InventoryResponse> characterInventories(
            @PathVariable UUID characterId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(characterId, loggedUser.getUsername());
        inventoryService.getOrCreateCharacterInventory(actor);
        if (actor.getParty() != null) {
            inventoryService.getOrCreatePartyInventory(actor);
        }
        return inventoryService.characterInventories(actor).stream().map(InventoryResponse::from).toList();
    }

    @GetMapping("/npc/{npcId}")
    public InventoryResponse npcInventory(
            @PathVariable UUID npcId,
            @RequestParam UUID actorCharacterId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(actorCharacterId, loggedUser.getUsername());
        return InventoryResponse.from(inventoryService.getOrCreateNpcInventory(actor, npcId));
    }

    @GetMapping("/{inventoryId}")
    public InventoryResponse read(
            @PathVariable UUID inventoryId,
            @RequestParam UUID actorCharacterId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(actorCharacterId, loggedUser.getUsername());
        return InventoryResponse.from(inventoryService.read(actor, inventoryId));
    }

    @PostMapping("/{inventoryId}/items")
    public InventoryResponse addItem(
            @PathVariable UUID inventoryId,
            @RequestBody InventoryItemRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(request.actorCharacterId(), loggedUser.getUsername());
        return InventoryResponse.from(inventoryService.addItem(actor, inventoryId, request.itemId(), request.quantity()));
    }

    @DeleteMapping("/{inventoryId}/items/{itemId}")
    public InventoryResponse removeItem(
            @PathVariable UUID inventoryId,
            @PathVariable UUID itemId,
            @RequestParam UUID actorCharacterId,
            @RequestParam(defaultValue = "1") int quantity,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(actorCharacterId, loggedUser.getUsername());
        return InventoryResponse.from(inventoryService.removeItem(actor, inventoryId, itemId, quantity));
    }

    @PatchMapping("/{inventoryId}/money")
    public InventoryResponse changeMoney(
            @PathVariable UUID inventoryId,
            @RequestBody InventoryMoneyRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character actor = inventoryService.actorFor(request.actorCharacterId(), loggedUser.getUsername());
        return InventoryResponse.from(inventoryService.changeMoney(actor, inventoryId, request.amountInGold()));
    }
}
