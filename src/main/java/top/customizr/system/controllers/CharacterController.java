package top.customizr.system.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.service.CharacterService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/character")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @PostMapping("/create")
    public ResponseEntity<CharacterResponse> create(
            @RequestBody CreateCharacterRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        CharacterResponse character = characterService.create(request, loggedUser.getUsername());
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/character/{id}")
                .buildAndExpand(character.characterId())
                .toUri();
        return ResponseEntity.created(location).body(character);
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
}
