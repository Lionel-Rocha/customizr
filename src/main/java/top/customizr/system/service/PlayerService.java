package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Character;
import top.customizr.system.model.Sheet;
import top.customizr.system.repository.CharacterRepository;

import java.util.UUID;

@Service
public class PlayerService {

    private final CharacterService characterService;
    private final CharacterRepository characterRepository;

    public PlayerService(
            CharacterService characterService,
            CharacterRepository characterRepository
    ) {
        this.characterService = characterService;
        this.characterRepository = characterRepository;
    }

    @Transactional
    public Sheet createCharacter(String name, UUID campaignId, UserDetails loggedUser) {
        CharacterResponse created = CharacterResponse.from(characterService.create(
                new CreateCharacterRequest(campaignId, name),
                loggedUser.getUsername()
        ));
        Character character = characterRepository.findById(created.characterId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        return character.getSheet();
    }

    @Transactional
    public Sheet readCharacter(UUID characterId, UserDetails loggedUser) {
        characterService.read(characterId, loggedUser.getUsername());
        return findSheet(characterId);
    }

    @Transactional
    public Sheet updateCharacter(
            UUID characterId,
            UpdateCharacterRequest request,
            UserDetails loggedUser
    ) {
        characterService.update(characterId, request, loggedUser.getUsername());
        return findSheet(characterId);
    }

    @Transactional
    public void deleteCharacter(UUID characterId, UserDetails loggedUser) {
        characterService.read(characterId, loggedUser.getUsername());
        characterService.delete(characterId, loggedUser.getUsername());
    }

    private Sheet findSheet(UUID characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character sheet not found"));
        if (character.getSheet() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character sheet not found");
        }
        return character.getSheet();
    }
}
