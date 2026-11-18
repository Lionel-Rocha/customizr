package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.MarkersRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.ChatService;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Concentracao automatica: salvaguarda de CON contra CD 10 ou metade do dano; falhou ou caiu, sai o marcador. */
@SpringBootTest
@Transactional
class ConcentrationTests {

    @Autowired private LiveTableService live;
    @Autowired private LiveCombatService combat;
    @Autowired private CharacterService characterService;
    @Autowired private MonsterService monsterService;
    @Autowired private ChatService chatService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ObjectMapper objectMapper;

    private String master;
    private String ana;
    private Campaign campaign;
    private Character aria;
    private UUID sceneId;
    private TokenResponse hero;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User u = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        characterService.updateCombatStats(aria.getCharacterId(), new UpdateCombatStatsRequest(200, 200, 0, 14, 30), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Cave", null, GridType.SQUARE, 70, 5, null, null), master).id();
        hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(), null, null,
                null, null, null), master);
        concentrate(hero.id(), ana);
    }

    @Test
    void aSmallHitAgainstAToughCasterKeepsConcentration() {
        constitution(30); // +10: nunca falha contra CD 10
        combat.changeHitPoints(hero.id(), damage(4), master);
        assertTrue(hasConcentration(hero.id()), "CD 10, rolou pelo menos 11");
        assertTrue(lastChat().contains("Concentration (DC 10)") && lastChat().contains("KEPT"), lastChat());
    }

    @Test
    void aBigHitBreaksConcentrationAndTheMarkerGoesAway() {
        constitution(1); // -5: nunca chega a CD 30
        combat.changeHitPoints(hero.id(), damage(60), master);
        assertTrue(!hasConcentration(hero.id()), "CD 30 (metade de 60)");
        assertTrue(lastChat().contains("LOST"), lastChat());
    }

    @Test
    void damageThroughTheSheetAlsoChecksTheTokenOfThatCharacter() {
        constitution(1);
        characterService.changeHitPoints(aria.getCharacterId(), damage(60), master);
        assertTrue(!hasConcentration(hero.id()), "dano pela ficha tambem confere a concentracao do token");
    }

    @Test
    void droppingToZeroBreaksConcentrationWithoutARoll() {
        UUID goblinId = monsterService.search("goblin", null, null).getFirst().id();
        TokenResponse goblin = live.addToken(sceneId, new TokenRequest(null, 140.0, 0.0, 1, null, false, null, null, goblinId,
                null, null, null), master);
        concentrate(goblin.id(), master);
        combat.changeHitPoints(goblin.id(), damage(50), master);
        assertTrue(!hasConcentration(goblin.id()));
        assertTrue(lastChat().contains("dropped to 0 HP"), lastChat());
    }

    @Test
    void withoutTheMarkerNothingIsRolled() {
        live.updateMarkers(hero.id(), new MarkersRequest(List.of()), ana);
        int before = chatService.history(campaign.getId(), null, 100, master).size();
        combat.changeHitPoints(hero.id(), damage(10), master);
        assertEquals(before, chatService.history(campaign.getId(), null, 100, master).size());
    }

    private void concentrate(UUID tokenId, String user) {
        live.updateMarkers(tokenId, new MarkersRequest(List.of(new MarkersRequest.Item("Concentração", "🧠", null))), user);
    }

    private void constitution(int con) {
        characterService.updateAttributes(aria.getCharacterId(), new UpdateAttributesRequest(10, 10, con, 10, 10, 10), ana);
    }

    private static HitPointChangeRequest damage(int amount) {
        return new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, amount);
    }

    private boolean hasConcentration(UUID tokenId) {
        return live.readScene(sceneId, master).tokens().stream().filter(t -> t.id().equals(tokenId)).findFirst().orElseThrow()
                .markers().stream().anyMatch(m -> m.label().startsWith("Concentra"));
    }

    private String lastChat() {
        List<ChatMessageResponse> history = chatService.history(campaign.getId(), null, 100, master);
        return history.getLast().content();
    }
}
