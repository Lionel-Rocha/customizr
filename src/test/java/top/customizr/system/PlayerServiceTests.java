package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import top.customizr.system.dto.UpdateCharacterRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Sheet;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.SheetRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.PlayerService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class PlayerServiceTests {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private SheetRepository sheetRepository;

    @Autowired
    private UserRepository userRepository;

    private Campaign campaign;
    private UserDetails loggedUser;

    @BeforeEach
    void setUp() {
        String username = "player-" + UUID.randomUUID();
        userRepository.save(new top.customizr.system.model.User(username, "password"));
        loggedUser = User.withUsername(username).password("password").build();
        campaign = campaignRepository.save(new Campaign("Campaign", userRepository.findByName(username).orElseThrow()));
    }

    @Test
    void createsReadsUpdatesAndDeletesCharacterSheet() {
        Sheet created = playerService.createCharacter("Aria", campaign.getId(), loggedUser);

        assertNotNull(created.getSheetId());
        assertEquals("Aria", playerService.readCharacter(created.getCharacter().getCharacterId(), loggedUser)
                .getCharacter().getName());

        Sheet updated = playerService.updateCharacter(
                created.getCharacter().getCharacterId(),
                new UpdateCharacterRequest("Aria the Brave"),
                loggedUser
        );
        assertEquals("Aria the Brave", updated.getCharacter().getName());

        UUID characterId = created.getCharacter().getCharacterId();
        playerService.deleteCharacter(characterId, loggedUser);

        assertFalse(characterRepository.existsById(characterId));
        assertFalse(sheetRepository.existsById(created.getSheetId()));
    }
}
