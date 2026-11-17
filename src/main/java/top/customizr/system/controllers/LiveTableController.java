package top.customizr.system.controllers;

import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.DoorRequest;
import top.customizr.system.dto.MarkersRequest;
import top.customizr.system.dto.TemplateRequest;
import top.customizr.system.dto.TemplateResolveRequest;
import top.customizr.system.dto.TemplateResolveResponse;
import top.customizr.system.dto.TemplateResponse;
import top.customizr.system.dto.TokenPlacementRequest;
import top.customizr.system.dto.VisionRequest;
import top.customizr.system.dto.WallRequest;
import top.customizr.system.dto.WallResponse;
import top.customizr.system.dto.FogRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.FogResponse;
import top.customizr.system.dto.InitiativeEntryRequest;
import top.customizr.system.dto.InitiativeResponse;
import top.customizr.system.dto.InitiativeRollRequest;
import top.customizr.system.dto.MoveTokenRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.SceneResponse;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.dto.TokenAttackOption;
import top.customizr.system.dto.TokenAttackRequest;
import top.customizr.system.dto.TokenAttackResponse;
import top.customizr.system.dto.TokenMovedResponse;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;

import java.util.List;
import java.util.UUID;

/**
 * Mesa ao vivo. Os navegadores abrem GET /live/campaign/{id}/events (text/event-stream, com o
 * header Authorization) e recebem: scene, scene_deleted, fog, token_added, token_updated,
 * token_moved, token_removed, initiative, initiative_ended e chat.
 */
@RestController
@RequestMapping("/live")
public class LiveTableController {

    private final LiveTableService live;
    private final LiveCombatService combat;

    public LiveTableController(LiveTableService live, LiveCombatService combat) {
        this.live = live;
        this.combat = combat;
    }

    @GetMapping(value = "/campaign/{campaignId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.subscribe(campaignId, loggedUser.getUsername());
    }

    @PostMapping("/campaign/{campaignId}/scenes")
    @ResponseStatus(HttpStatus.CREATED)
    public SceneResponse createScene(@PathVariable UUID campaignId, @RequestBody SceneRequest request,
                                          @AuthenticationPrincipal UserDetails loggedUser) {
        return live.createScene(campaignId, request, loggedUser.getUsername());
    }

    @GetMapping("/campaign/{campaignId}/scenes")
    public List<SceneResponse> listScenes(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.listScenes(campaignId, loggedUser.getUsername());
    }

    @GetMapping("/scenes/{sceneId}")
    public SceneResponse readScene(@PathVariable UUID sceneId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.readScene(sceneId, loggedUser.getUsername());
    }

    @PutMapping("/scenes/{sceneId}")
    public SceneResponse updateScene(@PathVariable UUID sceneId, @RequestBody SceneRequest request,
                                          @AuthenticationPrincipal UserDetails loggedUser) {
        return live.updateScene(sceneId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/scenes/{sceneId}")
    public ResponseEntity<Void> deleteScene(@PathVariable UUID sceneId, @AuthenticationPrincipal UserDetails loggedUser) {
        live.deleteScene(sceneId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scenes/{sceneId}/activate")
    public SceneResponse activateScene(@PathVariable UUID sceneId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.activateScene(sceneId, loggedUser.getUsername());
    }

    @PutMapping("/scenes/{sceneId}/fog")
    public FogResponse updateFog(@PathVariable UUID sceneId, @RequestBody FogRequest request,
                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return live.updateFog(sceneId, request, loggedUser.getUsername());
    }

    @PostMapping("/scenes/{sceneId}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse addToken(@PathVariable UUID sceneId, @RequestBody TokenRequest request,
                                       @AuthenticationPrincipal UserDetails loggedUser) {
        return live.addToken(sceneId, request, loggedUser.getUsername());
    }

    @PatchMapping("/tokens/{tokenId}/move")
    public TokenMovedResponse moveToken(@PathVariable UUID tokenId, @RequestBody MoveTokenRequest request,
                                         @AuthenticationPrincipal UserDetails loggedUser) {
        return live.moveToken(tokenId, request, loggedUser.getUsername());
    }

    @PutMapping("/tokens/{tokenId}")
    public TokenResponse updateToken(@PathVariable UUID tokenId, @RequestBody TokenRequest request,
                                          @AuthenticationPrincipal UserDetails loggedUser) {
        return live.updateToken(tokenId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/tokens/{tokenId}")
    public ResponseEntity<Void> removeToken(@PathVariable UUID tokenId, @AuthenticationPrincipal UserDetails loggedUser) {
        live.removeToken(tokenId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ paredes, portas e visao

    @PostMapping("/scenes/{sceneId}/walls")
    public ResponseEntity<List<WallResponse>> addWalls(@PathVariable UUID sceneId, @RequestBody WallRequest request,
                                                       @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.addWalls(sceneId, request, user.getUsername()));
    }

    @DeleteMapping("/scenes/{sceneId}/walls/{wallId}")
    public ResponseEntity<List<WallResponse>> removeWall(@PathVariable UUID sceneId, @PathVariable UUID wallId,
                                                         @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.removeWall(sceneId, wallId, user.getUsername()));
    }

    @DeleteMapping("/scenes/{sceneId}/walls")
    public ResponseEntity<List<WallResponse>> clearWalls(@PathVariable UUID sceneId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.clearWalls(sceneId, user.getUsername()));
    }

    @PatchMapping("/walls/{wallId}")
    public ResponseEntity<WallResponse> door(@PathVariable UUID wallId, @RequestBody DoorRequest request,
                                             @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.updateDoor(wallId, request, user.getUsername()));
    }

    @PutMapping("/scenes/{sceneId}/vision")
    public ResponseEntity<SceneResponse> sceneVision(@PathVariable UUID sceneId, @RequestBody VisionRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.setSceneVision(sceneId, request, user.getUsername()));
    }

    @PutMapping("/tokens/{tokenId}/vision")
    public ResponseEntity<TokenResponse> tokenVision(@PathVariable UUID tokenId, @RequestBody VisionRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.setTokenVision(tokenId, request, user.getUsername()));
    }

    // ------------------------------------------------------------------ altura, camada, grupo, marcadores, areas

    /** Altura (dono ou mestre), camada e fichas que viajam no token (so o mestre). */
    @PutMapping("/tokens/{tokenId}/placement")
    public ResponseEntity<TokenResponse> placement(@PathVariable UUID tokenId, @RequestBody TokenPlacementRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.updatePlacement(tokenId, request, user.getUsername()));
    }

    @PutMapping("/tokens/{tokenId}/markers")
    public ResponseEntity<TokenResponse> markers(@PathVariable UUID tokenId, @RequestBody MarkersRequest request,
                                                 @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.updateMarkers(tokenId, request, user.getUsername()));
    }

    @PostMapping("/scenes/{sceneId}/templates")
    public ResponseEntity<TemplateResponse> addTemplate(@PathVariable UUID sceneId, @RequestBody TemplateRequest request,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(live.addTemplate(sceneId, request, user.getUsername()));
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> removeTemplate(@PathVariable UUID templateId, @AuthenticationPrincipal UserDetails user) {
        live.removeTemplate(templateId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/scenes/{sceneId}/templates")
    public ResponseEntity<Void> clearTemplates(@PathVariable UUID sceneId, @AuthenticationPrincipal UserDetails user) {
        live.clearTemplates(sceneId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Salvaguarda de todos dentro da area e o dano (inteiro, metade ou nada). */
    @PostMapping("/templates/{templateId}/resolve")
    public ResponseEntity<TemplateResolveResponse> resolveTemplate(@PathVariable UUID templateId,
                                                                   @RequestBody TemplateResolveRequest request,
                                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.resolveTemplate(templateId, request, user.getUsername()));
    }

    // ------------------------------------------------------------------ combate

    @GetMapping("/tokens/{tokenId}/attacks")
    public ResponseEntity<List<TokenAttackOption>> attacks(@PathVariable UUID tokenId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.attacks(tokenId, user.getUsername()));
    }

    @PostMapping("/tokens/{tokenId}/attack")
    public ResponseEntity<TokenAttackResponse> attack(@PathVariable UUID tokenId, @RequestBody TokenAttackRequest request,
                                                      @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.attack(tokenId, request, user.getUsername()));
    }

    /** Teste do token: personagem pela ficha, monstro pela ficha de monstro (kind, ability, skill, mode). */
    @PostMapping("/tokens/{tokenId}/roll")
    public ResponseEntity<SheetRollResponse> roll(@PathVariable UUID tokenId, @RequestBody SheetRollRequest request,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.roll(tokenId, request, user.getUsername()));
    }

    @PostMapping("/tokens/{tokenId}/hp")
    public ResponseEntity<TokenResponse> hitPoints(@PathVariable UUID tokenId, @RequestBody HitPointChangeRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.changeHitPoints(tokenId, request, user.getUsername()));
    }

    @PutMapping("/tokens/{tokenId}/conditions")
    public ResponseEntity<TokenResponse> conditions(@PathVariable UUID tokenId, @RequestBody ConditionsRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(combat.updateConditions(tokenId, request, user.getUsername()));
    }

    @GetMapping("/campaign/{campaignId}/initiative")
    public InitiativeResponse initiative(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.initiative(campaignId, loggedUser.getUsername());
    }

    /** Body opcional: {"tokenIds": [...]}; vazio = todos os tokens visiveis da cena ativa. */
    @PostMapping("/campaign/{campaignId}/initiative/roll")
    public InitiativeResponse rollInitiative(@PathVariable UUID campaignId,
                                                  @RequestBody(required = false) InitiativeRollRequest request,
                                                  @AuthenticationPrincipal UserDetails loggedUser) {
        return live.rollInitiative(campaignId, request, loggedUser.getUsername());
    }

    @PostMapping("/campaign/{campaignId}/initiative/entries")
    public InitiativeResponse addEntry(@PathVariable UUID campaignId, @RequestBody InitiativeEntryRequest request,
                                            @AuthenticationPrincipal UserDetails loggedUser) {
        return live.addInitiativeEntry(campaignId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/campaign/{campaignId}/initiative/entries/{entryId}")
    public InitiativeResponse removeEntry(@PathVariable UUID campaignId, @PathVariable UUID entryId,
                                               @AuthenticationPrincipal UserDetails loggedUser) {
        return live.removeInitiativeEntry(campaignId, entryId, loggedUser.getUsername());
    }

    @PostMapping("/campaign/{campaignId}/initiative/next")
    public InitiativeResponse nextTurn(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        return live.nextTurn(campaignId, loggedUser.getUsername());
    }

    @DeleteMapping("/campaign/{campaignId}/initiative")
    public ResponseEntity<Void> endCombat(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        live.endCombat(campaignId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
