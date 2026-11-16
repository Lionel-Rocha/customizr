package top.customizr.system;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AzgaarImportResponse;
import top.customizr.system.dto.ChatMessageRequest;
import top.customizr.system.dto.CityRequest;
import top.customizr.system.dto.CountryRequest;
import top.customizr.system.dto.EncyclopediaEntryRequest;
import top.customizr.system.dto.EncyclopediaEntryResponse;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.dto.PointRequest;
import top.customizr.system.dto.WorldRequest;
import top.customizr.system.dto.WorldResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.EntryType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.PointType;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.repository.WorldRepository;
import top.customizr.system.service.ChatService;
import top.customizr.system.service.EncyclopediaService;
import top.customizr.system.service.ImageService;
import top.customizr.system.service.WorldService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class WorldEncyclopediaImageTests {

    @Autowired private WorldService worldService;
    @Autowired private EncyclopediaService encyclopediaService;
    @Autowired private ImageService imageService;
    @Autowired private ChatService chatService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private WorldRepository worldRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;

    private String master;
    private String player;
    private String outsider;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        outsider = "outsider-" + UUID.randomUUID();
        User masterUser = userRepository.save(new User(master, "password"));
        User playerUser = userRepository.save(new User(player, "password"));
        userRepository.save(new User(outsider, "password"));
        campaign = campaignRepository.save(new Campaign("Campaign", masterUser));
        membershipRepository.save(new Membership(campaign.getId(), playerUser.getUserId(), Role.PLAYER));
    }

    @Test
    void masterBuildsTheWorldAndPlayersReadItWithoutMasterNotes() {
        WorldResponse world = worldService.createWorld(campaign.getId(),
                new WorldRequest("Arton", null, "O vilao e o rei"), master);
        var country = worldService.addCountry(world.id(),
                new CountryRequest("Deheon", "Reino central", "Golpe em preparo", null, 0.5f, 0.4f), master);
        var city = worldService.addCity(country.countryId(),
                new CityRequest("Valkaria", null, null, 0.3f, 0.6f, 50000, true), master);
        worldService.addPoint(city.cityId(), new PointRequest("Estalagem do Macaco", PointType.INN, null, 0.1f, 0.2f), master);

        WorldResponse seenByPlayer = worldService.readWorld(world.id(), player);
        assertNull(seenByPlayer.masterInfo());
        assertNull(seenByPlayer.countries().getFirst().masterInfo());
        var valkaria = seenByPlayer.countries().getFirst().cities().getFirst();
        assertEquals(PointType.INN, valkaria.points().getFirst().type());
        assertTrue(valkaria.generatorUrl().startsWith("https://watabou.github.io/city-generator/"));
        assertTrue(valkaria.generatorUrl().contains("name=Valkaria"));
        assertEquals("Golpe em preparo", worldService.readCountry(country.countryId(), master).masterInfo());

        assertThrows(AccessDeniedException.class, () -> worldService.addCountry(world.id(),
                new CountryRequest("Pirata", null, null, null, null, null), player));
        assertThrows(AccessDeniedException.class, () -> worldService.readWorld(world.id(), outsider));
        assertThrows(ResponseStatusException.class, () -> worldService.addCity(country.countryId(),
                new CityRequest("Fora", null, null, 1.5f, 0f, null, null), master), "coordenada fora do mapa");

        worldService.deleteCity(city.cityId(), master);
        assertTrue(worldService.readCountry(country.countryId(), master).cities().isEmpty());
    }

    @Test
    void importsTheRealAzgaarExcerptAsCountriesCitiesAndEncyclopedia() throws Exception {
        var json = objectMapper.readTree(resource("/importer/azgaar-solia-recorte.json"));

        AzgaarImportResponse result = worldService.importAzgaar(campaign.getId(), json, 3, "/image/mapa", master);

        assertEquals("Solia", result.worldName());
        assertEquals(21, result.countries(), "22 estados menos os Neutrals");
        assertEquals(result.countries(), result.encyclopediaEntries());
        WorldResponse world = worldService.readWorld(result.worldId(), player);
        assertEquals("/image/mapa", world.mapUrl());
        var espokos = world.countries().stream().filter(c -> c.name().equals("Espokos")).findFirst().orElseThrow();
        assertEquals("Espokian Theocracy", espokos.info());
        var capital = espokos.cities().stream().filter(WorldResponse.CityResponse::capital).findFirst().orElseThrow();
        assertEquals("Espokos", capital.name());
        assertEquals(407.84 / 1024, capital.countryMapX(), 0.001, "posicao relativa ao mapa");
        assertEquals(14810, capital.population());
        assertTrue(espokos.cities().size() <= 3);

        List<EncyclopediaEntryResponse> entries = encyclopediaService.list(campaign.getId(), "theocracy", player);
        assertEquals(ReferenceType.COUNTRY, entries.getFirst().referenceType());
        assertEquals(espokos.countryId(), entries.getFirst().referenceId());

        assertThrows(ResponseStatusException.class, () -> worldService.importAzgaar(campaign.getId(),
                objectMapper.readTree("{\"foo\": 1}"), null, null, master));
    }

    @Test
    void hiddenEncyclopediaEntriesOnlyShowUpForTheMaster() {
        var world = worldService.createWorld(campaign.getId(), new WorldRequest("Mundo", null, null), master);
        var country = worldService.addCountry(world.id(), new CountryRequest("Norte", null, null, null, null, null), master);
        EncyclopediaEntryResponse secret = encyclopediaService.create(campaign.getId(), new EncyclopediaEntryRequest(
                "A Queda do Norte", "O rei morreu envenenado.", EntryType.HISTORICAL_FACT, false,
                ReferenceType.COUNTRY, country.countryId()), master);
        encyclopediaService.create(campaign.getId(), new EncyclopediaEntryRequest(
                "Guilda dos Ferreiros", "Aceita aprendizes.", null, null, null, null), master);

        assertEquals(1, encyclopediaService.list(campaign.getId(), null, player).size());
        assertEquals(2, encyclopediaService.list(campaign.getId(), null, master).size());
        assertThrows(ResponseStatusException.class, () -> encyclopediaService.read(secret.id(), player));
        assertEquals("Norte", secret.referenceName());

        encyclopediaService.changeVisibility(secret.id(), true, master);
        assertEquals(1, encyclopediaService.list(campaign.getId(), "envenenado", player).size());
        assertThrows(AccessDeniedException.class, () -> encyclopediaService.create(campaign.getId(),
                new EncyclopediaEntryRequest("x", null, null, null, null, null), player));
        assertThrows(ResponseStatusException.class, () -> encyclopediaService.create(campaign.getId(),
                new EncyclopediaEntryRequest("x", null, null, null, ReferenceType.NPC, null), master));
    }

    @Test
    void imagesAreCheckedByContentAndNotDuplicated() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);

        ImageResponse first = imageService.upload(campaign.getId(), png.toByteArray(), "mapa.png", player);
        ImageResponse again = imageService.upload(campaign.getId(), png.toByteArray(), "copia.png", master);
        assertEquals(first.id(), again.id(), "mesma imagem, mesmo registro");
        assertEquals("image/png", first.contentType());
        assertEquals("/image/" + first.id(), first.url());
        assertEquals(png.size(), imageService.read(first.id(), master).getData().length);

        assertThrows(ResponseStatusException.class, () -> imageService.upload(campaign.getId(),
                "<svg onload=alert(1)>".getBytes(), "x.png", master), "conteudo que nao e imagem");
        assertThrows(AccessDeniedException.class, () -> imageService.read(first.id(), outsider));
    }

    @Test
    void deletingTheCampaignTakesWorldChatEncyclopediaAndImagesAlong() throws Exception {
        var world = worldService.createWorld(campaign.getId(), new WorldRequest("Mundo", null, null), master);
        worldService.addCountry(world.id(), new CountryRequest("Norte", null, null, null, null, null), master);
        encyclopediaService.create(campaign.getId(), new EncyclopediaEntryRequest("Lenda", null, null, null, null, null), master);
        chatService.post(campaign.getId(), new ChatMessageRequest("/r 1d20", null), player);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        imageService.upload(campaign.getId(), png.toByteArray(), "a.png", master);
        entityManager.flush();
        entityManager.clear();

        campaignRepository.delete(campaignRepository.findById(campaign.getId()).orElseThrow());
        entityManager.flush();

        assertFalse(worldRepository.existsById(world.id()));
    }

    private String resource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
