package top.customizr.system.controllers;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import top.customizr.system.dto.*;
import top.customizr.system.model.Campaign;
import top.customizr.system.service.CampaignService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("campaign")
public class CampaignController {

    private CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping("/create")
    public ResponseEntity<UUID> create(@RequestBody @NonNull CreateCampaignRequest request, @AuthenticationPrincipal UserDetails userLogado) {
        Campaign created = campaignService.createCampaign(
                request.name(),
                userLogado.getUsername()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.status(200).body(created.getId());
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<ErrorResponse> update(@PathVariable UUID id, @RequestBody UpdateCampaignRequest request) {
        campaignService.updateCampaign(request, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();  // 204
    }

    @GetMapping("/{id}")
    public Campaign readCampaign(@PathVariable UUID id){
        Campaign campaign = campaignService.readCampaign(id);
        return campaign;
    }

    @PostMapping("/addSource")
    public ResponseEntity<Void> addSource(@RequestBody AddSourceRequest request){
        campaignService.addSource(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    public ResponseEntity<String> inviteUser(@RequestBody @NonNull InviteRequest request, @AuthenticationPrincipal UserDetails userLogado){
        campaignService.invite(request.campaignId(), request.role(), userLogado, request.username());
        String response = "Usuário " + request.username() + " convidado com o papel " + request.role();

        return ResponseEntity.status(200).body(response);

    }

    @PostMapping("/accept-invite")
    public ResponseEntity<String> acceptInvite(@RequestBody AcceptInviteRequest request, @AuthenticationPrincipal UserDetails loggedUser){
        campaignService.acceptInvite(request.campaignId(), loggedUser);
        String response = "Usuário " + loggedUser.getUsername() + " aceitou o convite.";
        return ResponseEntity.status(200).body(response);
    }
}