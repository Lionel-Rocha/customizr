package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.DoorRequest;
import top.customizr.system.dto.MonsterAction;
import top.customizr.system.dto.MonsterCopyRequest;
import top.customizr.system.dto.MonsterRequest;
import top.customizr.system.dto.MonsterResponse;
import top.customizr.system.dto.MoveTokenRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.dto.TokenAttackOption;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.VisionRequest;
import top.customizr.system.dto.WallRequest;
import top.customizr.system.dto.WallResponse;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Condition;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.model.Wall;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Monstros customizados (ficha em branco ou copia), testes do monstro pelo token, paredes, portas e visao. */
@SpringBootTest
@Transactional
class MonstersAndWallsTests {

    @Autowired private MonsterService monsterService;
    @Autowired private LiveTableService live;
    @Autowired private LiveCombatService combat;
    @Autowired private CharacterService characterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ObjectMapper objectMapper;

    private String master;
    private String ana;
    private String outsider;
    private Campaign campaign;
    private Character aria;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        outsider = "x-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User anaUser = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), anaUser.getUserId(), Role.PLAYER));
        campaignRepository.save(new Campaign("Other", userRepository.save(new User(outsider, "p"))));
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Cave", null, GridType.SQUARE, 70, 5, 1400, 1000), master).id();
    }

    @Test
    void aBlankMonsterSheetGetsDefaultsAndItsAttacksWorkOnTheTable() {
        MonsterResponse blank = monsterService.createCustom(request("Cave Lurker", null), master);
        assertTrue(blank.custom());
        assertEquals(10, blank.strength(), "atributo em branco vira 10");
        assertEquals(campaign.getId(), blank.campaignId());

        MonsterResponse lurker = monsterService.updateCustom(blank.id(), request("Cave Lurker", List.of(
                new MonsterAction("Bite", "ATTACK", 5, true, null, null, null, "1d6 + 3", "piercing", null),
                new MonsterAction("Spit", "SAVE", null, null, null, 13, "CON", "2d6", "acid", "Half on a success."),
                new MonsterAction("Lurk", "OTHER", null, null, null, null, null, null, null, "It hides in the dark."))), master);
        assertEquals(3, lurker.customActions().size(), "o editor reabre com as acoes como foram digitadas");
        assertTrue(lurker.actions().contains("Bite."));

        TokenResponse token = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, null, null, lurker.id(),
                null, null, null), master);
        assertEquals(33, token.hpCurrent());
        List<TokenAttackOption> attacks = combat.attacks(token.id(), master);
        assertEquals(2, attacks.size(), "acao so de texto nao vira ataque");
        assertEquals(5, attacks.getFirst().attackBonus());
        assertEquals("1d6+3", attacks.getFirst().damage());
        assertEquals("piercing", attacks.getFirst().damageType());
        assertEquals("SAVE", attacks.get(1).kind());
        assertEquals(13, attacks.get(1).saveDc());
        assertEquals("CON", attacks.get(1).saveAbility());
    }

    @Test
    void customMonstersBelongToTheCampaignAndCatalogMonstersAreCopiedBeforeEditing() {
        MonsterResponse lurker = monsterService.createCustom(request("Cave Lurker", null), master);
        assertThrows(AccessDeniedException.class, () -> monsterService.createCustom(request("Nope", null), ana));
        assertThrows(ResponseStatusException.class, () -> monsterService.createCustom(request("Cave Lurker", null), master),
                "nome repetido na mesma campanha");

        assertTrue(monsterService.search("lurker", null, null, campaign.getId(), ana).stream().anyMatch(m -> m.id().equals(lurker.id())));
        assertTrue(monsterService.search("lurker", null, null).isEmpty(), "o catalogo nao mostra homebrew");
        assertThrows(AccessDeniedException.class, () -> monsterService.read(lurker.id(), outsider));

        UUID goblin = monsterService.search("goblin", null, null).getFirst().id();
        assertThrows(ResponseStatusException.class, () -> monsterService.updateCustom(goblin, request("X", null), master),
                "monstro do catalogo nao se edita");
        MonsterResponse copy = monsterService.copyToCampaign(goblin, new MonsterCopyRequest(campaign.getId(), "Goblin Boss"), master);
        assertTrue(copy.custom());
        assertEquals(15, copy.armorClass(), "a copia leva a ficha toda");
        UUID copyId = copy.id();
        TokenResponse token = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, null, null, copyId,
                null, null, null), master);
        assertEquals("Scimitar", combat.attacks(token.id(), master).getFirst().name(), "a copia ataca igual ao original");

        monsterService.deleteCustom(lurker.id(), master);
        assertTrue(monsterService.search("lurker", null, null, campaign.getId(), master).isEmpty());
    }

    @Test
    void monsterTokensRollChecksAndSavesFromTheStatBlock() {
        UUID dragonId = monsterService.search("red dragon", null, null).getFirst().id();
        TokenResponse dragon = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 3, null, false, null, null, dragonId,
                null, null, null), master);

        SheetRollResponse dexSave = combat.roll(dragon.id(), new SheetRollRequest(SheetRollRequest.RollKind.SAVE, null,
                AbilityScore.DEXTERITY, null, null), master);
        assertTrue(dexSave.roll().expression().endsWith("+6"), "salvaguarda escrita na ficha: DEX +6");
        SheetRollResponse perception = combat.roll(dragon.id(), new SheetRollRequest(SheetRollRequest.RollKind.SKILL,
                Skill.PERCEPTION, null, null, null), master);
        assertTrue(perception.roll().expression().endsWith("+13"));
        SheetRollResponse strength = combat.roll(dragon.id(), new SheetRollRequest(SheetRollRequest.RollKind.ABILITY, null,
                AbilityScore.STRENGTH, null, null), master);
        assertTrue(strength.roll().expression().endsWith("+8"), "FOR 27 -> +8");

        combat.updateConditions(dragon.id(), new ConditionsRequest(Set.of(Condition.PARALYZED), null), master);
        assertEquals("AUTO_FAIL", combat.roll(dragon.id(), new SheetRollRequest(SheetRollRequest.RollKind.SAVE, null,
                AbilityScore.DEXTERITY, null, null), master).outcome());
        assertThrows(AccessDeniedException.class, () -> combat.roll(dragon.id(), new SheetRollRequest(
                SheetRollRequest.RollKind.ABILITY, null, AbilityScore.STRENGTH, null, null), ana));
    }

    @Test
    void wallsAndClosedDoorsStopPlayersAndTheMasterControlsLocks() {
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        // parede vertical em x=140, de cima a baixo; porta no meio dela
        List<WallResponse> walls = live.addWalls(sceneId, new WallRequest(List.of(
                new WallRequest.Segment(140, 0, 140, 280, Wall.Type.WALL),
                new WallRequest.Segment(140, 280, 140, 420, Wall.Type.DOOR),
                new WallRequest.Segment(140, 420, 140, 1000, Wall.Type.WALL))), master);
        assertEquals(3, walls.size());
        assertThrows(AccessDeniedException.class, () -> live.addWalls(sceneId, new WallRequest(List.of(
                new WallRequest.Segment(0, 0, 70, 0, null))), ana));

        assertThrows(ResponseStatusException.class, () -> live.moveToken(hero.id(), new MoveTokenRequest(210, 0), ana),
                "jogador nao atravessa parede");
        live.moveToken(hero.id(), new MoveTokenRequest(70, 280), ana); // anda do lado de ca
        UUID door = walls.get(1).id();
        assertThrows(ResponseStatusException.class, () -> live.moveToken(hero.id(), new MoveTokenRequest(210, 280), ana),
                "porta fechada bloqueia");
        live.updateDoor(door, new DoorRequest(true, null), ana);
        live.moveToken(hero.id(), new MoveTokenRequest(210, 280), ana);

        live.updateDoor(door, new DoorRequest(null, true), master);
        assertThrows(ResponseStatusException.class, () -> live.updateDoor(door, new DoorRequest(true, null), ana), "trancada");
        assertThrows(AccessDeniedException.class, () -> live.updateDoor(door, new DoorRequest(null, false), ana),
                "so o mestre destranca");
        live.moveToken(hero.id(), new MoveTokenRequest(0, 0), master); // o mestre passa por tudo

        live.setSceneVision(sceneId, new VisionRequest(true, null), master);
        live.setTokenVision(hero.id(), new VisionRequest(null, 60), master);
        var seenByAna = live.readScene(sceneId, ana);
        assertTrue(seenByAna.visionEnabled());
        assertEquals(3, seenByAna.walls().size(), "o jogador recebe as paredes pra calcular a visao");
        assertEquals(60, seenByAna.tokens().getFirst().visionFt());
        assertTrue(seenByAna.walls().get(1).locked());
        assertFalse(seenByAna.walls().get(1).open(), "trancar fecha a porta");

        live.removeWall(sceneId, walls.getFirst().id(), master);
        assertEquals(0, live.clearWalls(sceneId, master).size());
    }

    private MonsterRequest request(String name, List<MonsterAction> actions) {
        return new MonsterRequest(campaign.getId(), name, "Medium", "monstrosity", "Unaligned", 13, "natural armor", 33, "6d8+6",
                "30 ft.", actions == null ? null : 16, null, null, null, null, null, actions == null ? null : "2",
                null, null, null, null, null, null, "darkvision 60 ft.", null, null, null, actions);
    }
}
