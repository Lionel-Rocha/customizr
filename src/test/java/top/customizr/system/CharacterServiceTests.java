package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CharacterServiceTests {

    @Autowired
    private CharacterService characterService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private DndClassRepository dndClassRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private UserRepository userRepository;

    private Campaign campaign;
    private User player;

    @BeforeEach
    void setUp() {
        User master = userRepository.save(new User("master-" + UUID.randomUUID(), "password"));
        player = userRepository.save(new User("player-" + UUID.randomUUID(), "password"));
        campaign = campaignRepository.save(new Campaign("Campaign", master));
        membershipRepository.save(new Membership(campaign.getId(), player.getUserId(), Role.PLAYER));
    }

    @Test
    void createsReadsUpdatesAndDeletesOwnedCharacter() {
        CharacterResponse created = characterService.create(
                new CreateCharacterRequest(campaign.getId(), "Aria"),
                player.getName()
        );

        assertEquals("Aria", characterService.read(created.characterId(), player.getName()).name());
        assertEquals("Aria the Brave", characterService.update(
                created.characterId(),
                new UpdateCharacterRequest("Aria the Brave"),
                player.getName()
        ).name());

        characterService.delete(created.characterId(), player.getName());
        assertThrows(ResponseStatusException.class,
                () -> characterService.read(created.characterId(), player.getName()));
    }

    @Test
    void rejectsCharacterCreationByNonMember() {
        User outsider = userRepository.save(new User("outsider-" + UUID.randomUUID(), "password"));

        assertThrows(AccessDeniedException.class, () -> characterService.create(
                new CreateCharacterRequest(campaign.getId(), "Intruder"),
                outsider.getName()
        ));
    }

    @Test
    void persistsClassAndSubclassFeaturesWithCharacterSubclassChoiceLevel() {
        Feature classFeature = featureRepository.save(
                new Feature("Fighting Style", "", "Combat", FeatureType.CHOICE, null));
        Feature subclassFeature = featureRepository.save(
                new Feature("Improved Critical", "", "Combat", FeatureType.AUTOMATIC, null));
        DndClass fighter = new DndClass("Fighter", 10, 3, null);
        fighter.addFeature(classFeature, 1, "fighting-style");
        Subclass champion = fighter.addSubclass("Champion", 3, null);
        champion.addFeature(subclassFeature, 3);
        dndClassRepository.save(fighter);

        Character character = characterRepository.save(new Character("Aria", campaign, player));
        CharacterClass characterClass = character.getSheet().addClass(fighter, 5);
        characterClass.chooseSubclass(champion, 3);
        characterClass.selectFeature(classFeature, 1);
        characterRepository.save(character);

        Character loaded = characterRepository.findById(character.getCharacterId()).orElseThrow();
        CharacterClass loadedClass = loaded.getSheet().getClasses().getFirst();
        assertEquals(3, loadedClass.getSubclassChosenAtLevel());
        assertEquals(classFeature.getFeatureId(),
                loadedClass.getFeatureSelections().getFirst().getFeature().getFeatureId());
        assertEquals(subclassFeature.getFeatureId(),
                loadedClass.getSubclass().getFeatures().getFirst().getFeature().getFeatureId());
    }
}
