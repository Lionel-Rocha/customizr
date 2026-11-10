package top.customizr.system.controllers;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import top.customizr.system.dto.AddSourceRequest;
import top.customizr.system.dto.CreateCampaignRequest;
import top.customizr.system.dto.UpdateCampaignRequest;
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
    public ResponseEntity<Void> create(@RequestBody @NonNull CreateCampaignRequest request, @AuthenticationPrincipal UserDetails userLogado) {
        Campaign created = campaignService.createCampaign(
                request.name(),
                userLogado.getUsername()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).build();
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
    public String inviteUser(@RequestBody UUID user){
        //1. cria um convite no banco de dados
        return "abc";
    }

    @PostMapping("/accept-invite")
    public String acceptInvite(@RequestBody UUID inviteCode){
        //1. o usuário que recebeu o invite que tem que aceitar. Então vai pegar o current User
        //passado pelo JWT e vai aceitar se tiver um convite com aquele código
        return "abc";
    }
}