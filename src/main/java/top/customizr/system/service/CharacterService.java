package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public CharacterService(
            CharacterRepository characterRepository,
            CampaignRepository campaignRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository
    ) {
        this.characterRepository = characterRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CharacterResponse create(CreateCharacterRequest request, String username) {
        requireName(request == null ? null : request.name());
        if (request.campaignId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign ID is required");
        }

        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        User owner = userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!isCampaignMaster(campaign, owner)) {
            Membership membership = membershipRepository
                    .findByCampaignIdAndUserId(campaign.getId(), owner.getUserId())
                    .orElseThrow(() -> new AccessDeniedException("You are not a member of this campaign"));
            if (membership.getRole() == Role.SPECTATOR) {
                throw new AccessDeniedException("Spectators cannot create characters");
            }
        }

        Character character = characterRepository.save(new Character(request.name().trim(), campaign, owner));
        return CharacterResponse.from(character);
    }

    @Transactional
    public CharacterResponse read(UUID characterId, String username) {
        Character character = findCharacter(characterId);
        assertCanManage(character, username);
        return CharacterResponse.from(character);
    }

    @Transactional
    public CharacterResponse update(UUID characterId, UpdateCharacterRequest request, String username) {
        requireName(request == null ? null : request.name());
        Character character = findCharacter(characterId);
        assertCanManage(character, username);
        character.setName(request.name().trim());
        return CharacterResponse.from(characterRepository.save(character));
    }

    @Transactional
    public void delete(UUID characterId, String username) {
        Character character = findCharacter(characterId);
        assertCanManage(character, username);
        characterRepository.delete(character);
    }

    private Character findCharacter(UUID characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    private void assertCanManage(Character character, String username) {
        if (character.getOwner().getName().equals(username)
                || character.getCampaign().getMaster().getName().equals(username)) {
            return;
        }
        throw new AccessDeniedException("You cannot manage this character");
    }

    private boolean isCampaignMaster(Campaign campaign, User user) {
        return campaign.getMaster().getUserId().equals(user.getUserId());
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Character name is required");
        }
    }


    public List<Character> listCampaignCharacters(UUID campaignId){
        List<Character> characters = characterRepository.allCampaignCharacters(campaignId);
        return characters;
    }
}
