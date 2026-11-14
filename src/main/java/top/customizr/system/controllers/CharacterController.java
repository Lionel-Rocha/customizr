package top.customizr.system.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.AddFeatRequest;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.ChooseSubclassRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Character;
import top.customizr.system.model.Inventory;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.InventoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/character")
public class CharacterController {

    private final CharacterService characterService;
    private final InventoryService inventoryService;
    public CharacterController(CharacterService characterService, InventoryService inventoryService) {
        this.characterService = characterService;
        this.inventoryService = inventoryService;
    }

    @PostMapping("/create")
    public ResponseEntity<String> create(
            @RequestBody CreateCharacterRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        Character character = characterService.create(request, loggedUser.getUsername());
        Inventory inventory = inventoryService.getOrCreateCharacterInventory(character);
        if (character.getCharacterId() == inventory.getCharacter().getCharacterId()){
            return ResponseEntity.status(201).body("Personagem " + character.getName() + " criado com sucesso!");
        }
        return ResponseEntity.status(500).body("Deu ruim");
    }

    @GetMapping("/{id}")
    public CharacterResponse read(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.read(id, loggedUser.getUsername());
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<CharacterResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateCharacterRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return ResponseEntity.ok(characterService.update(id, request, loggedUser.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        characterService.delete(id, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/classes")
    public CharacterSheetResponse addClass(
            @PathVariable UUID id,
            @RequestBody AddCharacterClassRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.addClass(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/classes/{characterClassId}/subclass")
    public CharacterSheetResponse addSubclass(
            @PathVariable UUID id,
            @PathVariable UUID characterClassId,
            @RequestBody ChooseSubclassRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.addSubclass(id, characterClassId, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/classes/{characterClassId}/feats")
    public CharacterSheetResponse addFeat(
            @PathVariable UUID id,
            @PathVariable UUID characterClassId,
            @RequestBody AddFeatRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.addFeat(id, characterClassId, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/attributes")
    public CharacterSheetResponse updateAttributes(
            @PathVariable UUID id,
            @RequestBody UpdateAttributesRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.updateAttributes(id, request, loggedUser.getUsername());
    }

    @GetMapping("/{id}/sheet")
    public CharacterSheetResponse getCharacterSheet(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.getCharacterSheet(id, loggedUser.getUsername());
    }

    @GetMapping("/{campaignId}/characters")
    public List<CharacterResponse> campaignCharacters(@PathVariable UUID campaignId) {
        return characterService.listCampaignCharacters(campaignId).stream()
                .map(CharacterResponse::from)
                .toList();
    }
}
