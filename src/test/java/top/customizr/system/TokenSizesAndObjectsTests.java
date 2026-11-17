package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.dto.MapObjectRequest;
import top.customizr.system.dto.MapObjectResponse;
import top.customizr.system.dto.PlaceObjectRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TemplateRequest;
import top.customizr.system.dto.TokenPlacementRequest;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
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
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SceneRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.ImageService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tamanho livre dos tokens (padrao pelo tamanho da criatura) e biblioteca de objetos com imagem. */
@SpringBootTest
@Transactional
class TokenSizesAndObjectsTests {

    /** So o comeco de um PNG: o servidor confere o tipo pelos primeiros bytes. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired private LiveTableService live;
    @Autowired private CharacterService characterService;
    @Autowired private MonsterService monsterService;
    @Autowired private ImageService imageService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private ObjectMapper objectMapper;

    private String master;
    private String ana;
    private Campaign campaign;
    private Character aria;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User u = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Hall", null, GridType.SQUARE, 70, 5, 2100, 1400), master).id();
    }

    @Test
    void monstersStartWithTheirCreatureSizeAndTheMasterResizesFreely() {
        TokenResponse dragon = live.addToken(sceneId, monster("red dragon"), master);
        assertEquals(3, dragon.widthCells(), "Enorme = 3 x 3");
        assertEquals(1, live.addToken(sceneId, monster("goblin"), master).widthCells(), "Pequeno = 1");

        TokenResponse gargantuan = live.updatePlacement(dragon.id(), new TokenPlacementRequest(null, null, null, 6.0, null), master);
        assertEquals(6, gargantuan.widthCells(), "Imenso pode passar de 4 x 4");
        assertEquals(6, gargantuan.heightCells(), "so a largura vinda = quadrado");
        assertEquals(6, gargantuan.sizeCells());

        TokenResponse hero = live.addToken(sceneId, new TokenRequest(null, 700.0, 700.0, 1, null, false, aria.getCharacterId(),
                null, null, null, null, null), master);
        TokenResponse tiny = live.updatePlacement(hero.id(), new TokenPlacementRequest(null, null, null, 0.5, 0.5), master);
        assertEquals(0.5, tiny.widthCells(), "Miudo = meia casa");
        assertThrows(ResponseStatusException.class, () -> live.updatePlacement(hero.id(),
                new TokenPlacementRequest(null, null, null, 0.3, null), master), "de meia em meia casa");
        assertThrows(AccessDeniedException.class, () -> live.updatePlacement(hero.id(),
                new TokenPlacementRequest(null, null, null, 2.0, null), ana), "so o mestre muda o tamanho");

        // area pega o token miudo pelo centro dele
        var area = live.addTemplate(sceneId, new TemplateRequest(AreaTemplate.Shape.CIRCLE, 717.0, 717.0, null, 5, null, null, null), master);
        var template = sceneRepository.findById(sceneId).orElseThrow().getTemplates().stream()
                .filter(t -> t.getId().equals(area.id())).findFirst().orElseThrow();
        var token = sceneRepository.findById(sceneId).orElseThrow().getTokens().stream()
                .filter(t -> t.getId().equals(hero.id())).findFirst().orElseThrow();
        assertTrue(template.covers(token));
    }

    @Test
    void objectImagesBecomeALibraryThatPlacesSceneryTokens() {
        ImageResponse image = imageService.upload(campaign.getId(), PNG, "mesa.png", master);
        MapObjectResponse table = live.addObject(campaign.getId(), new MapObjectRequest("Mesa", image.id(), 2.0, 1.0, null), master);
        assertEquals(TokenLayer.SCENERY, table.layer(), "objeto vai pro cenario por padrao");
        assertEquals(1, live.listObjects(campaign.getId(), master).size());
        assertThrows(AccessDeniedException.class, () -> live.listObjects(campaign.getId(), ana));
        assertThrows(ResponseStatusException.class, () -> live.addObject(campaign.getId(),
                new MapObjectRequest("Torta", image.id(), 0.2, null, null), master));

        TokenResponse placed = live.placeObject(sceneId, table.id(), new PlaceObjectRequest(140.0, 140.0, 90, false), master);
        assertEquals("Mesa", placed.name());
        assertEquals(2, placed.widthCells());
        assertEquals(1, placed.heightCells(), "objeto pode ser retangulo");
        assertEquals(TokenLayer.SCENERY, placed.layer());
        assertEquals(90, placed.rotation());
        assertEquals(image.url(), placed.imageUrl());

        live.updateObject(table.id(), new MapObjectRequest("Mesa longa", null, 3.0, null, null), master);
        assertEquals(List.of("Mesa longa"), live.listObjects(campaign.getId(), master).stream().map(MapObjectResponse::name).toList());
        live.deleteObject(table.id(), master);
        assertTrue(live.listObjects(campaign.getId(), master).isEmpty());
    }

    private TokenRequest monster(String name) {
        UUID id = monsterService.search(name, null, null).getFirst().id();
        return new TokenRequest(null, 0.0, 0.0, null, null, false, null, null, id, null, null, null);
    }
}
