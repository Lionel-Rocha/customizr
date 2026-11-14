package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.AddFeatRequest;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.ChooseSubclassRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Feature;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.User;
import top.customizr.system.repository.*;

import java.util.List;
import java.util.UUID;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final DndClassRepository dndClassRepository;
    private final SubclassRepository subclassRepository;
    private final FeatureRepository featureRepository;
    private final InventoryRepository inventoryRepository;

    public CharacterService(
            CharacterRepository characterRepository,
            CampaignRepository campaignRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            DndClassRepository dndClassRepository,
            SubclassRepository subclassRepository,
            FeatureRepository featureRepository,
            InventoryRepository inventoryRepository
    ) {
        this.characterRepository = characterRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.dndClassRepository = dndClassRepository;
        this.subclassRepository = subclassRepository;
        this.featureRepository = featureRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public Character create(CreateCharacterRequest request, String username) {
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
        return character;
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

    @Transactional
    public CharacterSheetResponse addClass(
            UUID characterId,
            AddCharacterClassRequest request,
            String username
    ) {
        if (request == null || request.classId() == null) {
            throw badRequest("Class ID is required");
        }
        Character character = findManageableCharacter(characterId, username);
        DndClass dndClass = dndClassRepository.findById(request.classId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        try {
            character.getSheet().addClass(dndClass, request.level());
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse addSubclass(
            UUID characterId,
            UUID characterClassId,
            ChooseSubclassRequest request,
            String username
    ) {
        if (request == null || request.subclassId() == null) {
            throw badRequest("Subclass ID is required");
        }
        Character character = findManageableCharacter(characterId, username);
        CharacterClass characterClass = findCharacterClass(character, characterClassId);
        Subclass subclass = subclassRepository.findById(request.subclassId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subclass not found"));
        try {
            characterClass.chooseSubclass(subclass, request.chosenAtLevel());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw badRequest(ex.getMessage());
        }
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse addFeat(
            UUID characterId,
            UUID characterClassId,
            AddFeatRequest request,
            String username
    ) {
        if (request == null || request.featureId() == null) {
            throw badRequest("Feature ID is required");
        }
        Character character = findManageableCharacter(characterId, username);
        CharacterClass characterClass = findCharacterClass(character, characterClassId);
        Feature feature = featureRepository.findById(request.featureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found"));
        try {
            characterClass.selectFeature(feature, request.selectedAtLevel());
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse updateAttributes(
            UUID characterId,
            UpdateAttributesRequest request,
            String username
    ) {
        if (request == null) {
            throw badRequest("Ability scores are required");
        }
        Character character = findManageableCharacter(characterId, username);
        try {
            character.getSheet().updateAttributes(new Attributes(
                    request.strength(),
                    request.dexterity(),
                    request.constitution(),
                    request.intelligence(),
                    request.wisdom(),
                    request.charisma()
            ));
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse getCharacterSheet(UUID characterId, String username) {
        return CharacterSheetResponse.from(findManageableCharacter(characterId, username));
    }

    private Character findCharacter(UUID characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    private Character findManageableCharacter(UUID characterId, String username) {
        Character character = findCharacter(characterId);
        assertCanManage(character, username);
        return character;
    }

    private CharacterClass findCharacterClass(Character character, UUID characterClassId) {
        if (characterClassId == null) {
            throw badRequest("Character class ID is required");
        }
        return character.getSheet().getClasses().stream()
                .filter(characterClass -> characterClass.getId().equals(characterClassId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character class not found"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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

    //addClass
    //addSubclass
    //addFeat
    //updateAttributes
    //getCharacterSheet (gets all information about this character: class, subclass, feats, attributes, party).



}
