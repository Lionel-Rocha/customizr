package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.InitiativeRollRequest;
import top.customizr.system.dto.MarkersRequest;
import top.customizr.system.dto.MoveTokenRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TemplateRequest;
import top.customizr.system.dto.TemplateResolveRequest;
import top.customizr.system.dto.TemplateResolveResponse;
import top.customizr.system.dto.TemplateResponse;
import top.customizr.system.dto.TokenPlacementRequest;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.AreaTemplate;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.TokenLayer;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Areas de efeito com salvaguarda, marcadores com duracao, altura, camadas e token de grupo (caravana). */
@SpringBootTest
@Transactional
class AreasMarkersLayersTests {

    @Autowired private LiveTableService live;
    @Autowired private LiveCombatService combat;
    @Autowired private CharacterService characterService;
    @Autowired private MonsterService monsterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private top.customizr.system.repository.SceneRepository sceneRepository;

    private String master;
    private String ana;
    private String bia;
    private String cris;
    private Campaign campaign;
    private Character aria;
    private Character bruno;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        bia = "bia-" + UUID.randomUUID();
        cris = "cris-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        for (String name : List.of(ana, bia, cris)) {
            User u = userRepository.save(new User(name, "p"));
            membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        }
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        characterService.updateCombatStats(aria.getCharacterId(), new UpdateCombatStatsRequest(200, 200, 0, 14, 30), ana);
        bruno = characterService.create(new CreateCharacterRequest(campaign.getId(), "Bruno"), bia);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Field", null, GridType.SQUARE, 70, 5, 2100, 1400), master).id();
    }

    @Test
    void areaShapesFollowTheBookGeometry() {
        // cena de 70 px por casa de 5 pes: 14 px por pe
        TemplateResponse circle = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 700.0, 700.0, null, 20, null, "Fireball", null), master);
        TemplateResponse cone = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CONE, 0.0, 700.0, 0.0, 30, null, null, null), master);
        TemplateResponse line = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.LINE, 0.0, 0.0, 90.0, 100, 5, null, null), master);
        var templates = live.readScene(sceneId, master).templates();
        assertEquals(3, templates.size());
        AreaTemplate c = template(circle.id());
        assertTrue(c.covers(700 + 19 * 14, 700), "dentro do raio de 20 pes");
        assertFalse(c.covers(700 + 21 * 14, 700));
        AreaTemplate k = template(cone.id());
        assertTrue(k.covers(20 * 14, 700 + 9 * 14), "a 20 pes, o cone tem 20 de largura (10 pra cada lado)");
        assertFalse(k.covers(20 * 14, 700 + 11 * 14));
        assertFalse(k.covers(-14, 700), "atras da origem nao");
        AreaTemplate l = template(line.id());
        assertTrue(l.covers(30, 99 * 14), "linha pra baixo, 5 pes de largura");
        assertFalse(l.covers(50, 50), "fora da largura");
    }

    @Test
    void aBreathWeaponAreaRollsEverySaveAndAppliesFullOrHalfDamage() {
        TokenResponse dragon = live.addToken(sceneId, monster("red dragon", 0, 560, 3), master);
        TokenResponse goblin = live.addToken(sceneId, monster("goblin", 420, 630, 1), master);
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 560.0, 700.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        TokenResponse farAway = live.addToken(sceneId, monster("zombie", 1960, 70, 1), master);
        String breath = combat.attacks(dragon.id(), master).stream().filter(a -> a.kind().equals("SAVE")).findFirst().orElseThrow().id();

        // cone de 60 pes saindo do dragao pra direita
        TemplateResponse cone = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CONE, 210.0, 665.0, 0.0, 60,
                null, "Fire Breath", null), master);
        TemplateResolveResponse result = combat.resolveTemplate(cone.id(), new TemplateResolveRequest(null, null, null, null,
                null, null, dragon.id(), breath), master);

        assertEquals(21, result.dc());
        assertEquals("DEX", result.ability());
        assertEquals(2, result.targets().size(), "goblin e Aria; o dragao nao se acerta e o zumbi esta longe");
        int damage = result.damageRoll().total();
        for (TemplateResolveResponse.Target t : result.targets()) {
            int expected = t.saved() ? damage / 2 : damage;
            assertEquals(expected, t.damageApplied(), t.name());
        }
        TemplateResolveResponse.Target ariaResult = result.targets().stream().filter(t -> t.tokenId().equals(hero.id())).findFirst().orElseThrow();
        assertEquals(200 - ariaResult.damageApplied(), characterRepository.findById(aria.getCharacterId()).orElseThrow().getSheet().getHpCurrent());
        assertTrue(result.targets().stream().noneMatch(t -> t.tokenId().equals(farAway.id())));
        assertTrue(result.targets().stream().anyMatch(t -> t.tokenId().equals(goblin.id())));
    }

    @Test
    void playersPlaceAndResolveTheirOwnAreas() {
        live.addToken(sceneId, monster("goblin", 700, 700, 1), master);
        TemplateResponse fireball = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 735.0, 735.0, null, 20,
                null, "Fireball", null), ana);
        assertThrows(AccessDeniedException.class, () -> combat.resolveTemplate(fireball.id(),
                new TemplateResolveRequest(AbilityScore.DEXTERITY, 15, "8d6", "fire", true, true, null, null), bia));
        TemplateResolveResponse result = combat.resolveTemplate(fireball.id(),
                new TemplateResolveRequest(AbilityScore.DEXTERITY, 15, "8d6", "fire", true, true, null, null), ana);
        assertEquals(1, result.targets().size());
        assertThrows(AccessDeniedException.class, () -> live.removeTemplate(fireball.id(), bia));
        live.removeTemplate(fireball.id(), master);
        assertTrue(live.readScene(sceneId, ana).templates().isEmpty());
    }

    @Test
    void markersCountDownOnTheTokensTurnAndDisappear() {
        TokenResponse goblin = live.addToken(sceneId, monster("goblin", 700, 700, 1), master);
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        live.updateMarkers(goblin.id(), new MarkersRequest(List.of(new MarkersRequest.Item("Burning", "🔥", 2),
                new MarkersRequest.Item("Marked", "🎯", null))), master);
        live.updateMarkers(hero.id(), new MarkersRequest(List.of(new MarkersRequest.Item("Concentration", "🧠", null))), ana);
        assertThrows(AccessDeniedException.class, () -> live.updateMarkers(goblin.id(), new MarkersRequest(List.of()), ana));

        live.rollInitiative(campaign.getId(), new InitiativeRollRequest(List.of(goblin.id(), hero.id())), master);
        // duas voltas completas passam por dois turnos do goblin
        for (int i = 0; i < 4; i++) live.nextTurn(campaign.getId(), master);
        var markers = live.readScene(sceneId, master).tokens().stream().filter(t -> t.id().equals(goblin.id())).findFirst()
                .orElseThrow().markers();
        assertEquals(1, markers.size(), "Burning acabou; Marked fica ate tirarem");
        assertEquals("Marked", markers.getFirst().label());
        assertNull(markers.getFirst().roundsLeft());
    }

    @Test
    void aCaravanTokenCarriesSeveralSheetsAndLayersHideOrOrderTokens() {
        TokenResponse caravan = live.addToken(sceneId, new TokenRequest("Caravana", 0.0, 0.0, 2, null, false, null, null, null,
                null, null, null), master);
        TokenResponse withMembers = live.updatePlacement(caravan.id(),
                new TokenPlacementRequest(null, null, List.of(aria.getCharacterId(), bruno.getCharacterId())), master);
        assertEquals(2, withMembers.members().size());
        assertEquals("Aria", withMembers.members().getFirst().name());

        live.moveToken(caravan.id(), new MoveTokenRequest(140, 0), ana);
        live.moveToken(caravan.id(), new MoveTokenRequest(280, 0), bia);
        assertThrows(AccessDeniedException.class, () -> live.moveToken(caravan.id(), new MoveTokenRequest(0, 0), cris),
                "quem nao tem ficha na caravana nao move");
        assertThrows(AccessDeniedException.class, () -> live.updatePlacement(caravan.id(),
                new TokenPlacementRequest(null, null, List.of()), ana), "so o mestre escolhe quem viaja");

        // altura: o dono muda a do proprio token; camada so o mestre
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 700.0, 700.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        assertEquals(30, live.updatePlacement(hero.id(), new TokenPlacementRequest(30, null, null), ana).elevationFt());
        assertThrows(AccessDeniedException.class, () -> live.updatePlacement(hero.id(),
                new TokenPlacementRequest(null, TokenLayer.SCENERY, null), ana));

        TokenResponse trap = live.addToken(sceneId, new TokenRequest("Armadilha", 1400.0, 700.0, 1, null, false, null, null, null,
                null, null, null), master);
        TokenResponse cart = live.addToken(sceneId, new TokenRequest("Carroca", 1400.0, 140.0, 2, null, false, null, null, null,
                null, null, null), master);
        live.updatePlacement(trap.id(), new TokenPlacementRequest(null, TokenLayer.GM, null), master);
        live.updatePlacement(cart.id(), new TokenPlacementRequest(null, TokenLayer.SCENERY, null), master);
        var seenByAna = live.readScene(sceneId, ana).tokens();
        assertTrue(seenByAna.stream().noneMatch(t -> t.id().equals(trap.id())), "camada do mestre some pros jogadores");
        assertEquals(TokenLayer.SCENERY, seenByAna.stream().filter(t -> t.id().equals(cart.id())).findFirst().orElseThrow().layer());

        var initiative = live.rollInitiative(campaign.getId(), new InitiativeRollRequest(null), master);
        assertTrue(initiative.entries().stream().noneMatch(e -> e.name().equals("Carroca")), "objeto do cenario nao entra na iniciativa");
    }

    @Test
    void wallsGiveTotalCoverAgainstAreas() {
        TokenResponse inside = live.addToken(sceneId, monster("goblin", 700, 700, 1), master);
        TokenResponse behindWall = live.addToken(sceneId, monster("zombie", 910, 700, 1), master);
        // parede vertical em x=840 entre a origem e o zumbi
        live.addWalls(sceneId, new top.customizr.system.dto.WallRequest(List.of(
                new top.customizr.system.dto.WallRequest.Segment(840, 0, 840, 1400, null))), master);
        TemplateResponse fireball = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 735.0, 735.0, null, 20,
                null, "Fireball", null), master);
        TemplateResolveResponse result = combat.resolveTemplate(fireball.id(),
                new TemplateResolveRequest(AbilityScore.DEXTERITY, 15, "8d6", "fire", true, true, null, null), master);
        assertEquals(1, result.targets().size(), "o zumbi esta no raio, mas atras da parede");
        assertEquals(inside.id(), result.targets().getFirst().tokenId());
        assertTrue(result.targets().stream().noneMatch(t -> t.tokenId().equals(behindWall.id())));
    }

    private AreaTemplate template(UUID id) {
        return sceneRepository.findById(sceneId).orElseThrow().getTemplates().stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
    }

    private TokenRequest monster(String name, double x, double y, int size) {
        UUID id = monsterService.search(name, null, null).getFirst().id();
        return new TokenRequest(null, x, y, size, null, false, null, null, id, null, null, null);
    }
}
