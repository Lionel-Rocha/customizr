package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.EncyclopediaEntryRequest;
import top.customizr.system.dto.EncyclopediaEntryResponse;
import top.customizr.system.service.EncyclopediaService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/encyclopedia")
public class EncyclopediaController {

    private final EncyclopediaService encyclopediaService;

    public EncyclopediaController(EncyclopediaService encyclopediaService) {
        this.encyclopediaService = encyclopediaService;
    }

    @PostMapping("/campaign/{campaignId}")
    @ResponseStatus(HttpStatus.CREATED)
    public EncyclopediaEntryResponse create(@PathVariable UUID campaignId, @RequestBody EncyclopediaEntryRequest request,
                                            @AuthenticationPrincipal UserDetails loggedUser) {
        return encyclopediaService.create(campaignId, request, loggedUser.getUsername());
    }

    /** {@code ?q=texto} pesquisa no nome, no texto e no nome do que a entrada referencia. */
    @GetMapping("/campaign/{campaignId}")
    public List<EncyclopediaEntryResponse> list(@PathVariable UUID campaignId,
                                                @RequestParam(required = false) String q,
                                                @AuthenticationPrincipal UserDetails loggedUser) {
        return encyclopediaService.list(campaignId, q, loggedUser.getUsername());
    }

    @GetMapping("/{entryId}")
    public EncyclopediaEntryResponse read(@PathVariable UUID entryId, @AuthenticationPrincipal UserDetails loggedUser) {
        return encyclopediaService.read(entryId, loggedUser.getUsername());
    }

    @PutMapping("/{entryId}")
    public EncyclopediaEntryResponse update(@PathVariable UUID entryId, @RequestBody EncyclopediaEntryRequest request,
                                            @AuthenticationPrincipal UserDetails loggedUser) {
        return encyclopediaService.update(entryId, request, loggedUser.getUsername());
    }

    /** Body: {"visible": true} mostra pros jogadores; false esconde. */
    @PatchMapping("/{entryId}/visibility")
    public EncyclopediaEntryResponse changeVisibility(@PathVariable UUID entryId, @RequestBody Map<String, Boolean> body,
                                                      @AuthenticationPrincipal UserDetails loggedUser) {
        return encyclopediaService.changeVisibility(entryId, body != null && Boolean.TRUE.equals(body.get("visible")),
                loggedUser.getUsername());
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID entryId, @AuthenticationPrincipal UserDetails loggedUser) {
        encyclopediaService.delete(entryId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
