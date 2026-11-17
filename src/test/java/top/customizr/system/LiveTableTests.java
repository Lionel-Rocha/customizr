package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.LiveDtos;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Scene;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.LiveTableService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mesa ao vivo: cenas, neblina, tokens (oculto, quem move) e iniciativa. */
@SpringBootTest
@Transactional
class LiveTableTests {

    @Autowired private LiveTableService live;
    @Autowired private CharacterService characterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    private String master;
    private String ana;
    private String bia;
    private Campaign campaign;
    private Character aria;

    @BeforeEach
    void setUp() {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        bia = "bia-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        for (String name : List.of(ana, bia)) {
            User u = userRepository.save(new User(name, "p"));
            membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        }
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        characterService.updateAttributes(aria.getCharacterId(), new UpdateAttributesRequest(10, 16, 10, 10, 10, 10), ana);
    }

    @Test
    void playersOnlySeeTheActiveSceneAndNoHiddenTokens() {
        var cave = live.createScene(campaign.getId(), new LiveDtos.SceneRequest("Cave", "/image/x", GridType.SQUARE, 70, 5, 1400, 1000), master);
        assertTrue(cave.active(), "a primeira cena ja fica ativa");
        var town = live.createScene(campaign.getId(), new LiveDtos.SceneRequest("Town", null, null, null, null, null, null), master);
        assertFalse(town.active());

        live.addToken(cave.id(), token("Aria", 0, 0, false, aria.getCharacterId()), master);
        live.addToken(cave.id(), token("Ambush", 140, 140, true, null), master);

        assertEquals(2, live.listScenes(campaign.getId(), master).size());
        List<LiveDtos.SceneView> seenByAna = live.listScenes(campaign.getId(), ana);
        assertEquals(1, seenByAna.size());
        assertEquals(1, seenByAna.getFirst().tokens().size(), "o token oculto nao vai pro jogador");
        assertThrows(ResponseStatusException.class, () -> live.readScene(town.id(), ana), "cena inativa");

        live.activateScene(town.id(), master);
        assertEquals("Town", live.listScenes(campaign.getId(), ana).getFirst().name());
        assertThrows(AccessDeniedException.class, () -> live.createScene(campaign.getId(),
                new LiveDtos.SceneRequest("X", null, null, null, null, null, null), ana));
    }

    @Test
    void onlyTheOwnerOrTheMasterMovesATokenAndTheDistanceIsMeasured() {
        var cave = live.createScene(campaign.getId(), new LiveDtos.SceneRequest("Cave", null, GridType.SQUARE, 70, 5, null, null), master);
        var mine = live.addToken(cave.id(), token(null, 0, 0, false, aria.getCharacterId()), master);
        var goblin = live.addToken(cave.id(), token("Goblin", 700, 0, false, null), master);
        assertEquals("Aria", mine.name(), "token de personagem pega o nome da ficha");

        var moved = live.moveToken(mine.id(), new LiveDtos.MoveRequest(140, 210), ana);
        assertEquals(15, moved.distanceFeet(), "5e: diagonal conta 1 casa -> 3 casas");
        assertThrows(AccessDeniedException.class, () -> live.moveToken(mine.id(), new LiveDtos.MoveRequest(0, 0), bia));
        assertThrows(AccessDeniedException.class, () -> live.moveToken(goblin.id(), new LiveDtos.MoveRequest(0, 0), ana));
        live.moveToken(goblin.id(), new LiveDtos.MoveRequest(0, 0), master);

        var view = live.readScene(cave.id(), ana).tokens().stream().filter(t -> t.name().equals("Goblin")).findFirst().orElseThrow();
        assertNull(view.hpCurrent(), "jogador nao ve PV de monstro/NPC");
    }

    @Test
    void hexDistanceCountsSteps() {
        Scene hex = new Scene(campaign, "Hex", null, GridType.HEX, 70, 5, null, null);
        double w = 70, h = 70 * 2 / Math.sqrt(3);
        // tres hexagonos pra direita na mesma linha
        assertEquals(15, hex.distanceFeet(w / 2, h / 2, w / 2 + 3 * w, h / 2));
        // um passo pra baixo-direita
        assertEquals(5, hex.distanceFeet(w / 2, h / 2, w, h / 2 + 0.75 * h));
    }

    @Test
    void fogIsControlledByTheMaster() {
        var cave = live.createScene(campaign.getId(), new LiveDtos.SceneRequest("Cave", null, null, null, null, null, null), master);
        var fog = live.updateFog(cave.id(), new LiveDtos.FogRequest(true, List.of(new int[]{1, 2}, new int[]{-1, 4}), null, null, null), master);
        assertTrue(fog.fogEnabled());
        assertEquals(java.util.Set.of("1,2", "-1,4"), fog.revealedCells());
        fog = live.updateFog(cave.id(), new LiveDtos.FogRequest(null, null, List.of(new int[]{1, 2}), null, null), master);
        assertEquals(java.util.Set.of("-1,4"), fog.revealedCells());
        assertThrows(AccessDeniedException.class, () -> live.updateFog(cave.id(),
                new LiveDtos.FogRequest(false, null, null, null, null), ana));
        assertFalse(live.updateFog(cave.id(), new LiveDtos.FogRequest(null, null, null, null, true), master).fogEnabled());
    }

    @Test
    void initiativeOrdersTurnsAndRounds() {
        var cave = live.createScene(campaign.getId(), new LiveDtos.SceneRequest("Cave", null, null, null, null, null, null), master);
        live.addToken(cave.id(), token(null, 0, 0, false, aria.getCharacterId()), master);
        live.addToken(cave.id(), token("Hidden spy", 0, 0, true, null), master);

        var order = live.rollInitiative(campaign.getId(), null, master);
        assertEquals(1, order.entries().size(), "so os tokens visiveis da cena ativa");
        int ariaInit = order.entries().getFirst().initiative();
        assertTrue(ariaInit >= 4 && ariaInit <= 23, "1d20 + DES 3");

        order = live.addInitiativeEntry(campaign.getId(), new LiveDtos.InitiativeEntryRequest("Lair action", 20, 99), master);
        assertEquals("Lair action", order.entries().getFirst().name(), "20 com desempate alto fica na frente");
        assertEquals(1, order.turnIndex(), "quem entra no meio nao rouba a vez: continua sendo a da Aria");
        var newRound = live.nextTurn(campaign.getId(), master);
        assertEquals(2, newRound.round(), "depois da ultima, nova rodada");
        assertEquals(0, newRound.turnIndex());
        assertEquals(1, live.nextTurn(campaign.getId(), master).turnIndex());
        assertEquals(2, live.initiative(campaign.getId(), ana).entries().size(), "jogador ve a ordem");
        assertThrows(AccessDeniedException.class, () -> live.nextTurn(campaign.getId(), ana));

        live.endCombat(campaign.getId(), master);
        assertTrue(live.initiative(campaign.getId(), master).entries().isEmpty());
    }

    private static LiveDtos.TokenRequest token(String name, double x, double y, boolean hidden, UUID characterId) {
        return new LiveDtos.TokenRequest(name, x, y, 1, null, hidden, characterId, null, null, null, null, null);
    }
}
