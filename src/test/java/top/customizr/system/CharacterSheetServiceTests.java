package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.AddFeatRequest;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.ChooseSubclassRequest;
import top.customizr.system.dto.CreatePartyRequest;
import top.customizr.system.dto.PartyResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.PartyService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CharacterSheetServiceTests {

    @Autowired
    private CharacterService characterService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private DndClassRepository dndClassRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private UserRepository userRepository;

    private Campaign campaign;
    private CharacterResponse character;
    private String username;

    @BeforeEach
    void setUp() {
        username = "master-" + UUID.randomUUID();
        User master = userRepository.save(new User(username, "password"));
        campaign = campaignRepository.save(new Campaign("Campaign", master));
        character = characterService.create(
                new CreateCharacterRequest(campaign.getId(), "Aria"),
                username
        );
    }

    @Test
    void buildsCharacterSheetWithClassSubclassFeatAttributesAndParty() {
        Feature classFeat = featureRepository.save(
                new Feature("Fighting Style", "", "Combat", FeatureType.CHOICE, null));
        Feature subclassFeat = featureRepository.save(
                new Feature("Improved Critical", "", "Combat", FeatureType.CHOICE, null));
        DndClass fighter = new DndClass("Fighter", 10, 3, null);
        fighter.addFeature(classFeat, 1, "fighting-style");
        var champion = fighter.addSubclass("Champion", 3, null);
        champion.addFeature(subclassFeat, 3, "champion-feature");
        dndClassRepository.save(fighter);

        CharacterSheetResponse withClass = characterService.addClass(
                character.characterId(),
                new AddCharacterClassRequest(fighter.getClassId(), 3),
                username
        );
        UUID characterClassId = withClass.classes().getFirst().characterClassId();

        characterService.addFeat(
                character.characterId(),
                characterClassId,
                new AddFeatRequest(classFeat.getFeatureId(), 1),
                username
        );
        characterService.addSubclass(
                character.characterId(),
                characterClassId,
                new ChooseSubclassRequest(champion.getSubclassId(), 3),
                username
        );
        characterService.addFeat(
                character.characterId(),
                characterClassId,
                new AddFeatRequest(subclassFeat.getFeatureId(), 3),
                username
        );
        characterService.updateAttributes(
                character.characterId(),
                new UpdateAttributesRequest(30, 12, 14, 10, 16, 8),
                username
        );

        PartyResponse party = partyService.create(campaign.getId(), new CreatePartyRequest("Heroes"), username);
        partyService.addCharacter(party.partyId(), character.characterId(), username);

        CharacterSheetResponse sheet = characterService.getCharacterSheet(character.characterId(), username);

        assertEquals(1, sheet.classes().size());
        assertEquals("Fighter", sheet.classes().getFirst().className());
        assertEquals("Champion", sheet.classes().getFirst().subclass().name());
        assertEquals("Fighting Style", sheet.classes().getFirst().feats().getFirst().name());
        assertEquals("Improved Critical", sheet.classes().getFirst().feats().get(1).name());
        assertEquals(30, sheet.attributes().strength());
        assertNotNull(sheet.party());
        assertEquals("Heroes", sheet.party().name());
        assertEquals(character.characterId(), sheet.party().members().getFirst().characterId());

        partyService.removeCharacter(party.partyId(), character.characterId(), username);
        assertNull(characterService.getCharacterSheet(character.characterId(), username).party());
    }

    @Test
    void rejectsAbilityScoresOutsideTheConfiguredRange() {
        assertThrows(ResponseStatusException.class, () -> characterService.updateAttributes(
                character.characterId(),
                new UpdateAttributesRequest(31, 10, 10, 10, 10, 10),
                username
        ));
    }
}
