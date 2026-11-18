package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.CampaignExport;
import top.customizr.system.dto.CampaignIconRequest;
import top.customizr.system.dto.CampaignImportResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.DoorRequest;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.dto.MapObjectRequest;
import top.customizr.system.dto.MarkersRequest;
import top.customizr.system.dto.MonsterRequest;
import top.customizr.system.dto.PlaceObjectRequest;
import top.customizr.system.dto.SceneLightingRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TemplateRequest;
import top.customizr.system.dto.TokenAttackRequest;
import top.customizr.system.dto.TokenLightRequest;
import top.customizr.system.dto.TokenPlacementRequest;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.VisionRequest;
import top.customizr.system.dto.WallRequest;
import top.customizr.system.model.AreaTemplate;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.SceneLighting;
import top.customizr.system.model.Source;
import top.customizr.system.model.TokenLayer;
import top.customizr.system.model.User;
import top.customizr.system.model.Wall;
import top.customizr.system.repository.CampaignIconRepository;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MapObjectRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SceneRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignTransferService;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.ImageService;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exportar/importar a mesa inteira, luz (cena escura, tocha, visao no escuro, escuridao) e cobertura pelas paredes. */
@SpringBootTest
@Transactional
class ExportLightAndCoverTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired private LiveTableService live;
    @Autowired private LiveCombatService combat;
    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private MonsterService monsterService;
    @Autowired private ImageService imageService;
    @Autowired private CampaignTransferService transfer;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private MapObjectRepository mapObjectRepository;
    @Autowired private CampaignIconRepository iconRepository;
    @Autowired private ObjectMapper objectMapper;

    private String master;
    private String ana;
    private String bia;
    private Campaign campaign;
    private Character aria;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        bia = "bia-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("Cripta", userRepository.save(new User(master, "p"))));
        for (String name : List.of(ana, bia)) {
            User u = userRepository.save(new User(name, "p"));
            membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        }
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Cripta", null, GridType.SQUARE, 70, 5, 1400, 1000), master).id();
    }

    @Test
    void theWholeTableTravelsInTheExport() throws Exception {
        // monta a mesa: paredes, porta trancada, escuridao, luz, tokens de todo tipo, objeto e icone
        live.addWalls(sceneId, new WallRequest(List.of(new WallRequest.Segment(280, 0, 280, 280, Wall.Type.WALL),
                new WallRequest.Segment(280, 280, 280, 420, Wall.Type.DOOR))), master);
        UUID door = sceneRepository.findById(sceneId).orElseThrow().getWalls().get(1).getId();
        live.updateDoor(door, new DoorRequest(null, true), master);
        live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 700.0, 300.0, null, 15, null, "Darkness", null, true), master);
        live.setSceneVision(sceneId, new VisionRequest(true, null), master);
        live.setSceneLighting(sceneId, new SceneLightingRequest(SceneLighting.DARK), master);

        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 70.0, 70.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        live.setTokenLight(hero.id(), new TokenLightRequest(20, 20, 60), ana);
        live.updatePlacement(hero.id(), new TokenPlacementRequest(10, null, null), ana);
        live.updateMarkers(hero.id(), new MarkersRequest(List.of(new MarkersRequest.Item("Concentração", "🧠", 3))), ana);

        UUID goblinId = monsterService.search("goblin", null, null).getFirst().id();
        live.addToken(sceneId, new TokenRequest(null, 420.0, 70.0, null, null, true, null, null, goblinId, null, null, null), master);
        var lurker = monsterService.createCustom(new MonsterRequest(campaign.getId(), "Lurker", "Large", null, null, 14, null, 40,
                null, null, 16, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null), master);
        TokenResponse lurkerToken = live.addToken(sceneId, new TokenRequest(null, 560.0, 560.0, null, null, false, null, null,
                lurker.id(), null, null, null), master);
        live.updatePlacement(lurkerToken.id(), new TokenPlacementRequest(null, TokenLayer.GM, null, 3.0, 2.0), master);
        TokenResponse caravan = live.addToken(sceneId, new TokenRequest("Caravana", 0.0, 700.0, 2, null, false, null, null, null,
                null, null, null), master);
        live.updatePlacement(caravan.id(), new TokenPlacementRequest(null, null, List.of(aria.getCharacterId())), master);

        ImageResponse tableImage = imageService.upload(campaign.getId(), PNG, "mesa.png", master);
        var table = live.addObject(campaign.getId(), new MapObjectRequest("Mesa", tableImage.id(), 2.0, 1.0, null), master);
        live.placeObject(sceneId, table.id(), new PlaceObjectRequest(140.0, 140.0, 90, false), master);
        byte[] other = PNG.clone();
        other[15] = 'Y';
        ImageResponse iconImage = imageService.upload(campaign.getId(), other, "Poisoned.png", master);
        live.putIcon(campaign.getId(), new CampaignIconRequest("condition:POISONED", "Envenenado", iconImage.id()), master);
        sceneRepository.flush();

        CampaignExport exported = transfer.export(campaign.getId(), true, master);
        assertEquals(2, exported.formatVersion());
        // passa pelo JSON de verdade, como no download
        CampaignExport read = objectMapper.readValue(objectMapper.writeValueAsBytes(exported), CampaignExport.class);
        CampaignImportResponse imported = transfer.importCampaign(read, master);
        Campaign copy = campaignRepository.findById(imported.campaignId()).orElseThrow();

        var scene = sceneRepository.findByCampaignOrderByName(copy).getFirst();
        assertEquals(SceneLighting.DARK, scene.getLighting());
        assertTrue(scene.isVisionEnabled());
        assertEquals(2, scene.getWalls().size());
        assertTrue(scene.getWalls().stream().anyMatch(w -> w.isDoor() && w.isLocked()), "porta continua trancada");
        assertTrue(scene.getTemplates().getFirst().isDarkness());
        assertEquals(5, scene.getTokens().size(), "Aria, goblin, lurker, caravana e a mesa");

        var heroCopy = scene.getTokens().stream().filter(t -> "Aria".equals(t.getName())).findFirst().orElseThrow();
        assertEquals(20, heroCopy.getLightBrightFt());
        assertEquals(60, heroCopy.getDarkvisionFt());
        assertEquals(10, heroCopy.getElevationFt());
        assertEquals("Concentração", heroCopy.getMarkers().getFirst().getLabel());
        assertNotEquals(aria.getCharacterId(), heroCopy.getCharacter().getCharacterId(), "ligado a ficha nova");

        var lurkerCopy = scene.getTokens().stream().filter(t -> "Lurker".equals(t.getName())).findFirst().orElseThrow();
        assertEquals(TokenLayer.GM, lurkerCopy.getLayer());
        assertEquals(3, lurkerCopy.width());
        assertEquals(2, lurkerCopy.height());
        assertTrue(lurkerCopy.getMonster().isCustom());
        assertEquals(copy.getId(), lurkerCopy.getMonster().getCampaign().getId(), "o monstro customizado veio pra campanha nova");

        var goblinCopy = scene.getTokens().stream().filter(t -> t.getMonster() != null && !t.getMonster().isCustom()).findFirst().orElseThrow();
        assertTrue(goblinCopy.isHidden());
        assertEquals(goblinId, goblinCopy.getMonster().getId(), "monstro do catalogo pela referencia");

        var caravanCopy = scene.getTokens().stream().filter(t -> "Caravana".equals(t.getName())).findFirst().orElseThrow();
        assertEquals(List.of("Aria"), caravanCopy.getMembers().stream().map(Character::getName).toList());

        var objectCopy = mapObjectRepository.findByCampaignOrderByName(copy).getFirst();
        assertEquals(copy.getId(), objectCopy.getImage().getCampaign().getId(), "a imagem do objeto veio junto");
        var tableToken = scene.getTokens().stream().filter(t -> "Mesa".equals(t.getName())).findFirst().orElseThrow();
        assertEquals(objectCopy.getImage().getUrl(), tableToken.getImageUrl());
        assertEquals(90, tableToken.getRotation());
        assertEquals("condition:POISONED", iconRepository.findByCampaignOrderByLabel(copy).getFirst().getKey());

        // sem imagens no arquivo, no mesmo servidor: as imagens sao copiadas da campanha original
        CampaignImportResponse withoutImages = transfer.importCampaign(transfer.export(campaign.getId(), false, master), master);
        Campaign copy2 = campaignRepository.findById(withoutImages.campaignId()).orElseThrow();
        assertEquals(copy2.getId(), mapObjectRepository.findByCampaignOrderByName(copy2).getFirst().getImage().getCampaign().getId());
    }

    @Test
    void wallsGiveHalfOrTotalCoverToAttacks() {
        UUID swordId = sheetService.addAttack(aria.getCharacterId(), new AttackRequest("Sword", 30, "1d8", "slashing", null), ana)
                .details().attacks().getFirst().id();
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        UUID goblinId = monsterService.search("goblin", null, null).getFirst().id();
        TokenResponse goblin = live.addToken(sceneId, new TokenRequest(null, 210.0, 0.0, 1, null, false, null, null, goblinId,
                null, null, null), master);

        // parede curta: tampa so a parte de cima do caminho -> meia cobertura
        live.addWalls(sceneId, new WallRequest(List.of(new WallRequest.Segment(140, -10, 140, 36, null))), master);
        var scene = sceneRepository.findById(sceneId).orElseThrow();
        var heroToken = scene.getTokens().stream().filter(t -> t.getId().equals(hero.id())).findFirst().orElseThrow();
        var goblinToken = scene.getTokens().stream().filter(t -> t.getId().equals(goblin.id())).findFirst().orElseThrow();
        assertEquals(2, scene.coverBetween(heroToken, goblinToken));
        var attack = combat.attack(hero.id(), new TokenAttackRequest(swordId.toString(), goblin.id(), null, false), ana);
        assertTrue(attack.notes().contains("Half cover (+2 AC)"), attack.notes().toString());

        // parede inteira entre os dois -> cobertura total, o ataque nao passa
        live.addWalls(sceneId, new WallRequest(List.of(new WallRequest.Segment(140, -10, 140, 500, null))), master);
        assertThrows(ResponseStatusException.class, () -> combat.attack(hero.id(),
                new TokenAttackRequest(swordId.toString(), goblin.id(), null, false), ana));
    }

    @Test
    void lightsAndDarkvisionBelongToWhoControlsTheToken() {
        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        TokenResponse torch = live.setTokenLight(hero.id(), new TokenLightRequest(20, 20, 60), ana);
        assertEquals(20, torch.lightBrightFt());
        assertEquals(60, torch.darkvisionFt());
        assertThrows(AccessDeniedException.class, () -> live.setTokenLight(hero.id(), new TokenLightRequest(0, 0, 0), bia));
        assertThrows(ResponseStatusException.class, () -> live.setTokenLight(hero.id(), new TokenLightRequest(5000, 0, 0), ana));
        assertEquals(null, live.setTokenLight(hero.id(), new TokenLightRequest(0, 0, 60), ana).lightBrightFt(), "apagou a tocha");

        assertThrows(AccessDeniedException.class, () -> live.setSceneLighting(sceneId, new SceneLightingRequest(SceneLighting.DARK), ana));
        assertEquals(SceneLighting.DARK, live.setSceneLighting(sceneId, new SceneLightingRequest(SceneLighting.DARK), master).lighting());
        assertEquals(SceneLighting.DARK, live.readScene(sceneId, ana).lighting());
        var darkness = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 300.0, 300.0, null, 15, null,
                "Darkness", null, true), ana);
        assertTrue(darkness.darkness());
    }
}
