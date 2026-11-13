package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.CreatePartyRequest;
import top.customizr.system.dto.PartyResponse;
import top.customizr.system.service.PartyService;

import java.util.UUID;

@RestController
@RequestMapping("/party")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @PostMapping("/campaign/{campaignId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse create(
            @PathVariable UUID campaignId,
            @RequestBody CreatePartyRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return partyService.create(campaignId, request, loggedUser.getUsername());
    }

    @PutMapping("/{partyId}/characters/{characterId}")
    public PartyResponse addCharacter(
            @PathVariable UUID partyId,
            @PathVariable UUID characterId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return partyService.addCharacter(partyId, characterId, loggedUser.getUsername());
    }

    @DeleteMapping("/{partyId}/characters/{characterId}")
    public PartyResponse removeCharacter(
            @PathVariable UUID partyId,
            @PathVariable UUID characterId,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return partyService.removeCharacter(partyId, characterId, loggedUser.getUsername());
    }
}
