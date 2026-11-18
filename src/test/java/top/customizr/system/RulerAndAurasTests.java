package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AurasRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.RulerRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
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
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deslocamento no token (pra pintar o movimento), auras e a regua compartilhada. */
@SpringBootTest
@Transactional
class RulerAndAurasTests {

    @Autowired private LiveTableService live;
    @Autowired private CharacterService characterService;
    @Autowired private MonsterService monsterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private top.customizr.system.repository.SceneRepository sceneRepository;

    private String master;
    private String ana;
    private String bia;
    private Character aria;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        bia = "bia-" + UUID.randomUUID();
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        for (String name : List.of(ana, bia)) {
            User u = userRepository.save(new User(name, "p"));
            membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        }
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        characterService.updateCombatStats(aria.getCharacterId(), new UpdateCombatStatsRequest(20, 20, 0, 14, 35), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Cave", null, GridType.SQUARE, 70, 5, null, null), master).id();
    }

    @Test
    void tokensCarryTheirWalkingSpeed() {
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        assertEquals(35, hero.speedFt(), "da ficha");
        UUID dragon = monsterService.search("red dragon", null, null).getFirst().id();
        TokenResponse dragonToken = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, null, null, false, null, null, dragon,
                null, null, null), master);
        assertEquals(40, dragonToken.speedFt(), "\"40 ft., climb 40 ft., fly 80 ft.\" -> andando 40");
    }

    @Test
    void aurasBelongToWhoControlsTheToken() {
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        TokenResponse withAura = live.updateAuras(hero.id(), new AurasRequest(List.of(
                new AurasRequest.Item(10, "#FFCF7F", "Aura de proteção"), new AurasRequest.Item(60, null, "Arco"))), ana);
        assertEquals(2, withAura.auras().size());
        assertEquals(10, withAura.auras().getFirst().radiusFt());
        assertEquals(2, live.readScene(sceneId, bia).tokens().getFirst().auras().size(), "todos veem as auras");
        assertThrows(AccessDeniedException.class, () -> live.updateAuras(hero.id(), new AurasRequest(List.of()), bia));
        assertThrows(ResponseStatusException.class, () -> live.updateAuras(hero.id(),
                new AurasRequest(List.of(new AurasRequest.Item(1000, null, null))), ana));
        assertThrows(ResponseStatusException.class, () -> live.updateAuras(hero.id(),
                new AurasRequest(List.of(new AurasRequest.Item(10, "red", null))), ana));
    }

    @Test
    void playersPickTheirOwnColor() {
        UUID campaignId = sceneRepository.findById(sceneId).orElseThrow().getCampaign().getId();
        var members = live.members(campaignId, bia);
        assertEquals(3, members.size(), "mestre + duas jogadoras");
        assertEquals("MASTER", members.getFirst().role());

        var after = live.changeMyColor(campaignId, new top.customizr.system.dto.ColorRequest("#C792EA"), ana);
        assertEquals("#C792EA", after.stream().filter(m -> m.name().equals(ana)).findFirst().orElseThrow().color());
        assertThrows(ResponseStatusException.class, () -> live.changeMyColor(campaignId, new top.customizr.system.dto.ColorRequest("roxo"), ana));
        assertThrows(ResponseStatusException.class, () -> live.changeMyColor(campaignId, new top.customizr.system.dto.ColorRequest("#FFFFFF"), master),
                "o mestre nao tem cor de jogador");
        assertEquals(null, live.changeMyColor(campaignId, new top.customizr.system.dto.ColorRequest(""), ana).stream()
                .filter(m -> m.name().equals(ana)).findFirst().orElseThrow().color(), "vazio volta pra automatica");
    }

    @Test
    void theRulerIsSharedButValidated() {
        live.shareRuler(sceneId, new RulerRequest(List.of(new double[] {0, 0}, new double[] {350, 70})), ana);
        live.shareRuler(sceneId, new RulerRequest(List.of()), ana); // apagar
        assertThrows(ResponseStatusException.class, () -> live.shareRuler(sceneId,
                new RulerRequest(List.of(new double[] {1, 2, 3})), ana));
    }
}
