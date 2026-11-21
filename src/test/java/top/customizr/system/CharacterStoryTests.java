package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterStoryRequest;
import top.customizr.system.dto.CharacterStoryResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.StoryShareRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ChatMessageType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Historia e anotacoes do personagem; notas secretas do mestre; mostrar no chat. */
@SpringBootTest
@Transactional
class CharacterStoryTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    private String master;
    private String player;
    private UUID hero;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        Campaign c = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User p = userRepository.save(new User(player, "p"));
        membershipRepository.save(new Membership(c.getId(), p.getUserId(), Role.PLAYER));
        hero = characterService.create(new CreateCharacterRequest(c.getId(), "Aria"), player).getCharacterId();
    }

    @Test
    void ownerWritesTheStoryNullKeepsAndBlankClears() {
        CharacterStoryResponse st = sheetService.updateStory(hero, story("Chaotic Good", "Tall, green eyes", "Raised by wolves", "  "), player);
        assertEquals("Chaotic Good", st.alignment());
        assertEquals("Raised by wolves", st.backstory());
        assertNull(st.notes(), "so espacos = vazio");

        st = sheetService.updateStory(hero, new CharacterStoryRequest(null, "27", null, null, null, null, null, null, null,
                null, null, null, "", null, null, null), player);
        assertEquals("Chaotic Good", st.alignment(), "nulo fica");
        assertEquals("27", st.age());
        assertNull(st.backstory(), "texto vazio apaga");

        assertThrows(ResponseStatusException.class, () -> sheetService.updateStory(hero, new CharacterStoryRequest(
                "x".repeat(61), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null), player));
    }

    @Test
    void gameMasterNotesAreSecret() {
        assertThrows(AccessDeniedException.class, () -> sheetService.updateStory(hero, new CharacterStoryRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "peek"), player));
        sheetService.updateStory(hero, new CharacterStoryRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "She is the lost princess"), master);

        assertEquals("She is the lost princess", sheetService.story(hero, master).gmNotes());
        CharacterStoryResponse forPlayer = sheetService.story(hero, player);
        assertNull(forPlayer.gmNotes(), "o jogador nao ve");
        assertTrue(!forPlayer.master());

        // o jogador grava a historia sem mandar gmNotes: as notas do mestre ficam
        sheetService.updateStory(hero, story(null, "Freckles", null, null), player);
        assertEquals("She is the lost princess", sheetService.story(hero, master).gmNotes());
    }

    @Test
    void appearanceAndBackstoryGoToTheChat() {
        assertThrows(ResponseStatusException.class, () -> sheetService.shareStory(hero,
                new StoryShareRequest(StoryShareRequest.Part.BACKSTORY), player), "nada escrito");
        sheetService.updateStory(hero, new CharacterStoryRequest(null, "27", "1,70 m", null, "green", null, null,
                "A scar over the left eye", null, null, null, null, "Raised by wolves", null, null, null), player);

        var look = sheetService.shareStory(hero, new StoryShareRequest(StoryShareRequest.Part.APPEARANCE), player);
        assertEquals(ChatMessageType.CARD, look.type());
        assertTrue(look.content().contains("Idade: 27 | Altura: 1,70 m | Olhos: green"), look.content());
        assertTrue(look.content().contains("A scar over the left eye"));
        var back = sheetService.shareStory(hero, new StoryShareRequest(StoryShareRequest.Part.BACKSTORY), player);
        assertTrue(back.content().contains("Raised by wolves"));
    }

    private static CharacterStoryRequest story(String alignment, String appearance, String backstory, String notes) {
        return new CharacterStoryRequest(alignment, null, null, null, null, null, null, appearance, null, null, null,
                null, backstory, null, notes, null);
    }
}
