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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.NpcRequest;
import top.customizr.system.dto.NpcResponse;
import top.customizr.system.service.NpcService;

import java.util.List;
import java.util.UUID;

/** NPCs da campanha. Jogadores veem tudo menos {@code masterInformation}. */
@RestController
@RequestMapping("/npc")
public class NpcController {

    private final NpcService npcService;

    public NpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    @PostMapping("/campaign/{campaignId}")
    @ResponseStatus(HttpStatus.CREATED)
    public NpcResponse create(
            @PathVariable UUID campaignId,
            @RequestBody NpcRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return npcService.create(campaignId, request.name(), request.personality(), request.masterInformation(),
                request.photoUrl(), loggedUser.getUsername());
    }

    @GetMapping("/campaign/{campaignId}")
    public List<NpcResponse> listByCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return npcService.listByCampaign(campaignId, loggedUser.getUsername());
    }

    @GetMapping("/{id}")
    public NpcResponse read(@PathVariable UUID id, @AuthenticationPrincipal UserDetails loggedUser) {
        return npcService.read(id, loggedUser.getUsername());
    }

    @PutMapping("/{id}")
    public NpcResponse update(
            @PathVariable UUID id,
            @RequestBody NpcRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return npcService.update(id, request.name(), request.personality(), request.masterInformation(),
                request.photoUrl(), loggedUser.getUsername());
    }

    /** Body: {"monsterId": "..."} (null desliga). */
    @PutMapping("/{id}/stat-block")
    public NpcResponse useStatBlock(@PathVariable UUID id, @RequestBody java.util.Map<String, UUID> body,
                                    @AuthenticationPrincipal UserDetails loggedUser) {
        return npcService.useStatBlock(id, body == null ? null : body.get("monsterId"), loggedUser.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UserDetails loggedUser) {
        npcService.delete(id, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
