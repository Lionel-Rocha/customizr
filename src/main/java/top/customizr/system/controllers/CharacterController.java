package top.customizr.system.controllers;

import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.AttackRollRequest;
import top.customizr.system.dto.CatalogFeatRequest;
import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.ExperienceRequest;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.PortraitRequest;
import top.customizr.system.dto.ProficienciesRequest;
import top.customizr.system.dto.RestRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SpendSpellSlotRequest;
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
    private final top.customizr.system.service.CharacterFeatureService featureService;
    public CharacterController(CharacterService characterService, InventoryService inventoryService,
                               CharacterImportService characterImportService,
                               top.customizr.system.service.CharacterSheetService sheetService,
                               top.customizr.system.service.CharacterFeatureService featureService) {
        this.featureService = featureService;
        this.characterService = characterService;
        this.inventoryService = inventoryService;
        this.characterImportService = characterImportService;
        this.sheetService = sheetService;
    }

    // ------------------------------------------------------------------ features da ficha (CharacterFeatureService)

    /**
     * Tudo que o personagem tem (features de classe/subclasse, especie, antecedente, talentos, magias, itens),
     * com o texto pronto. {@code available=true} inclui as escolhas que a classe oferece e ainda nao foram feitas.
     */
    @GetMapping("/{id}/features")
    public java.util.List<top.customizr.system.dto.CharacterFeatureResponse> features(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean available,
            @AuthenticationPrincipal UserDetails loggedUser) {
        return featureService.list(id, available, loggedUser.getUsername());
    }

    /** Manda uma feature/talento/magia/item da ficha pro chat, falando como o personagem. */
    @PostMapping("/{id}/features/share")
    public top.customizr.system.dto.ChatMessageResponse shareFeature(
            @PathVariable UUID id,
            @RequestBody top.customizr.system.dto.ShareRequest request,
            @AuthenticationPrincipal UserDetails loggedUser) {
        return featureService.shareFromCharacter(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/classes/{characterClassId}/spells/{spellId}")
    public CharacterSheetResponse prepareSpell(@PathVariable UUID id, @PathVariable UUID characterClassId,
                                               @PathVariable UUID spellId, @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.prepareSpell(id, characterClassId, spellId, true, loggedUser.getUsername());
    }

    @DeleteMapping("/{id}/classes/{characterClassId}/spells/{spellId}")
    public CharacterSheetResponse unprepareSpell(@PathVariable UUID id, @PathVariable UUID characterClassId,
                                                 @PathVariable UUID spellId, @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.prepareSpell(id, characterClassId, spellId, false, loggedUser.getUsername());
    }

    // ------------------------------------------------------------------ ficha completa (CharacterSheetService)

    @PutMapping("/{id}/origin")
    public CharacterSheetResponse chooseOrigin(@PathVariable UUID id, @RequestBody OriginRequest request,
                                               @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.chooseOrigin(id, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/proficiencies")
    public CharacterSheetResponse updateProficiencies(@PathVariable UUID id, @RequestBody ProficienciesRequest request,
                                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.updateProficiencies(id, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/conditions")
    public CharacterSheetResponse updateConditions(@PathVariable UUID id, @RequestBody ConditionsRequest request,
                                                   @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.updateConditions(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/spell-slots/spend")
    public CharacterSheetResponse spendSpellSlot(@PathVariable UUID id, @RequestBody SpendSpellSlotRequest request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.spendSpellSlot(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/rest")
    public top.customizr.system.dto.RestResponse rest(@PathVariable UUID id, @RequestBody RestRequest request,
                                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.rest(id, request, loggedUser.getUsername());
    }

    /** Gasta usos de um recurso (Furia, Pontos de Foco, cargas de item...). */
    @PostMapping("/{id}/resources/spend")
    public CharacterSheetResponse spendResource(@PathVariable UUID id,
                                                @RequestBody top.customizr.system.dto.ResourceRequest request,
                                                @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.spendResource(id, request, loggedUser.getUsername());
    }

    /** Devolve usos (amount vazio = todos). */
    @PostMapping("/{id}/resources/recover")
    public CharacterSheetResponse recoverResource(@PathVariable UUID id,
                                                  @RequestBody top.customizr.system.dto.ResourceRequest request,
                                                  @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.recoverResource(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/resources")
    public CharacterSheetResponse addCustomResource(@PathVariable UUID id,
                                                    @RequestBody top.customizr.system.dto.CustomResourceRequest request,
                                                    @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.addCustomResource(id, request, loggedUser.getUsername());
    }

    @PutMapping("/{id}/resources/{resourceId}")
    public CharacterSheetResponse updateCustomResource(@PathVariable UUID id, @PathVariable UUID resourceId,
                                                       @RequestBody top.customizr.system.dto.CustomResourceRequest request,
                                                       @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.updateCustomResource(id, resourceId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/{id}/resources/{resourceId}")
    public CharacterSheetResponse removeCustomResource(@PathVariable UUID id, @PathVariable UUID resourceId,
                                                       @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.removeCustomResource(id, resourceId, loggedUser.getUsername());
    }

    /** Inspiracao Heroica: o mestre da (true); o dono ou o mestre tira (false). */
    @PutMapping("/{id}/inspiration")
    public CharacterSheetResponse changeInspiration(@PathVariable UUID id,
                                                    @RequestBody top.customizr.system.dto.InspirationRequest request,
                                                    @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.changeInspiration(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/attacks")
    public CharacterSheetResponse addAttack(@PathVariable UUID id, @RequestBody AttackRequest request,
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
                                                                 @RequestBody(required = false) AttackRollRequest request,
                                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.rollAttack(id, attackId, request, loggedUser.getUsername());
    }

    /** Teste de pericia, atributo, salvaguarda, iniciativa ou contra a morte (toChat = vai pro chat). */
    @PostMapping("/{id}/roll")
    public top.customizr.system.dto.SheetRollResponse roll(@PathVariable UUID id, @RequestBody SheetRollRequest request,
                                                           @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.roll(id, request, loggedUser.getUsername());
    }

    @PostMapping("/{id}/catalog-feats")
    public CharacterSheetResponse addCatalogFeat(@PathVariable UUID id, @RequestBody CatalogFeatRequest request,
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
    public CharacterSheetResponse gainExperience(@PathVariable UUID id, @RequestBody ExperienceRequest request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return sheetService.gainExperience(id, request, loggedUser.getUsername());
    }

    /** Body: {"portraitUrl": "/image/..."} */
    @PutMapping("/{id}/portrait")
    public CharacterSheetResponse changePortrait(@PathVariable UUID id, @RequestBody PortraitRequest request,
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
