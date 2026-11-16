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
import top.customizr.system.dto.SheetRequests;
import top.customizr.system.dto.CharacterImportResponse;
import top.customizr.system.service.CharacterImportService;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.dto.HitPointChangeRequest;
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
    private final CharacterImportService characterImportService;
    private final top.customizr.system.service.CharacterSheetService sheetService;
    public CharacterController(CharacterService characterService, InventoryService inventoryService,
                               CharacterImportService characterImportService,
                               top.customizr.system.service.CharacterSheetService sheetService) {
        this.characterService = characterService;
        this.inventoryService = inventoryService;
        this.characterImportService = characterImportService;
        this.sheetService = sheetService;
    }

    // ------------------------------------------------------------------ ficha completa (CharacterSheetService)

    @PutMapping("/{id}/origin")
    public CharacterSheetResponse chooseOrigin(@PathVariable UUID id, @RequestBody SheetRequests.Origin request,
                                               @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.chooseOrigin(id, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/proficiencies")
    public CharacterSheetResponse updateProficiencies(@PathVariable UUID id, @RequestBody SheetRequests.Proficiencies request,
                                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.updateProficiencies(id, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/conditions")
    public CharacterSheetResponse updateConditions(@PathVariable UUID id, @RequestBody SheetRequests.Conditions request,
                                                   @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.updateConditions(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/spell-slots/spend")
    public CharacterSheetResponse spendSpellSlot(@PathVariable UUID id, @RequestBody SheetRequests.SpendSlot request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.spendSpellSlot(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/rest")
    public top.customizr.system.dto.RestResponse rest(@PathVariable UUID id, @RequestBody SheetRequests.Rest request,
                                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.rest(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/attacks")
    public CharacterSheetResponse addAttack(@PathVariable UUID id, @RequestBody SheetRequests.Attack request,
                                            @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.addAttack(id, request, loggedUser.getUsername());
    }

    @DeleteMapping("/{id}/attacks/{attackId}")
    public CharacterSheetResponse removeAttack(@PathVariable UUID id, @PathVariable UUID attackId,
                                               @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.removeAttack(id, attackId, loggedUser.getUsername());
    }

    @PostMapping("/{id}/attacks/{attackId}/roll")
    public top.customizr.system.dto.SheetRollResponse rollAttack(@PathVariable UUID id, @PathVariable UUID attackId,
                                                                 @RequestBody(required = false) SheetRequests.AttackRoll request,
                                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.rollAttack(id, attackId, request, loggedUser.getUsername());
    }

    /** Teste de pericia, atributo, salvaguarda, iniciativa ou contra a morte (toChat = vai pro chat). */
    @PostMapping("/{id}/roll")
    public top.customizr.system.dto.SheetRollResponse roll(@PathVariable UUID id, @RequestBody SheetRequests.Roll request,
                                                           @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.roll(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/catalog-feats")
    public CharacterSheetResponse addCatalogFeat(@PathVariable UUID id, @RequestBody SheetRequests.CatalogFeat request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.addCatalogFeat(id, request, loggedUser.getUsername());
    }

    @DeleteMapping("/{id}/catalog-feats/{characterFeatId}")
    public CharacterSheetResponse removeCatalogFeat(@PathVariable UUID id, @PathVariable UUID characterFeatId,
                                                    @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.removeCatalogFeat(id, characterFeatId, loggedUser.getUsername());
    }

    /** So o mestre. Body: {"amount": 300} (negativo tira). */
    @PostMapping("/{id}/experience")
    public CharacterSheetResponse gainExperience(@PathVariable UUID id, @RequestBody SheetRequests.Experience request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.gainExperience(id, request, loggedUser.getUsername());
    }

    /** Body: {"portraitUrl": "/image/..."} */
    @PutMapping("/{id}/portrait")
    public CharacterSheetResponse changePortrait(@PathVariable UUID id, @RequestBody SheetRequests.Portrait request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.changePortrait(id, request, loggedUser.getUsername());
    }

    /**
     * Importa um personagem de arquivo: ator do Foundry VTT (dnd5e), JSON do D&D Beyond ou PDF preenchivel.
     * multipart/form-data com {@code file} e {@code campaignId}.
     */
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CharacterImportResponse> importCharacter(
            @org.springframework.web.bind.annotation.RequestParam UUID campaignId,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal UserDetails loggedUser
    ) throws java.io.IOException {
        CharacterImportResponse imported = characterImportService.importCharacter(
                campaignId, file.getOriginalFilename(), file.getBytes(), loggedUser.getUsername());
        Character character = characterService.listCampaignCharacters(campaignId).stream()
                .filter(c -> c.getCharacterId().equals(imported.characterId()))
                .findFirst().orElse(null);
        if (character != null) {
            inventoryService.getOrCreateCharacterInventory(character);
        }
        return ResponseEntity.status(201).body(imported);
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

    @PutMapping("/{id}/combat")
    public CharacterSheetResponse updateCombatStats(
            @PathVariable UUID id,
            @RequestBody UpdateCombatStatsRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.updateCombatStats(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/hp")
    public CharacterSheetResponse changeHitPoints(
            @PathVariable UUID id,
            @RequestBody HitPointChangeRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return characterService.changeHitPoints(id, request, loggedUser.getUsername());
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
