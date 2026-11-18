package top.customizr.system;

import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.PortraitRequest;
import top.customizr.system.dto.ProficienciesRequest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CampaignExport;
import top.customizr.system.dto.CampaignImportResponse;
import top.customizr.system.dto.CountryRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.CreatePartyRequest;
import top.customizr.system.dto.EncyclopediaEntryRequest;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.dto.InventoryMoneyRequest;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.dto.WorldRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Currency;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Monster;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.model.Role;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignTransferService;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.EncyclopediaService;
import top.customizr.system.service.ImageService;
import top.customizr.system.service.InventoryService;
import top.customizr.system.service.NpcService;
import top.customizr.system.service.PartyService;
import top.customizr.system.service.WorldService;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CampaignTransferAndCoinsTests {

    @Autowired private CampaignTransferService transferService;
    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private WorldService worldService;
    @Autowired private EncyclopediaService encyclopediaService;
    @Autowired private NpcService npcService;
    @Autowired private PartyService partyService;
    @Autowired private InventoryService inventoryService;
    @Autowired private ImageService imageService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void coinsAreConvertedToGoldAndBrokenDownBack() {
        assertEquals(Map.of(Currency.PLATINUM, 12L, Currency.GOLD, 3L, Currency.SILVER, 4L, Currency.COPPER, 5L),
                Currency.breakdown(new BigDecimal("123.45"), true));
        assertEquals(0, new BigDecimal("0.5").compareTo(new InventoryMoneyRequest(null, new BigDecimal("5"), Currency.SILVER).amountInGold()));
        assertEquals(0, new BigDecimal("20").compareTo(new InventoryMoneyRequest(null, new BigDecimal("2"), Currency.PLATINUM).amountInGold()));

        String master = "m-" + UUID.randomUUID();
        Campaign c = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        Character hero = characterService.create(new CreateCharacterRequest(c.getId(), "Hero"), master);
        var inventory = inventoryService.getOrCreateCharacterInventory(hero);
        inventoryService.changeMoney(hero, inventory.getId(), new InventoryMoneyRequest(null, new BigDecimal("37"), Currency.SILVER).amountInGold());
        InventoryResponse response = InventoryResponse.from(inventoryService.read(hero, inventory.getId()));
        assertEquals(Map.of("pp", 0L, "gp", 3L, "ep", 0L, "sp", 7L, "cp", 0L), response.coins());
    }

    @Test
    void exportAndImportRecreateTheCampaignAsANewOne() throws Exception {
        String master = "master-" + UUID.randomUUID();
        String player = "player-" + UUID.randomUUID();
        Campaign campaign = campaignRepository.save(new Campaign("Lost Mine", userRepository.save(new User(master, "p"))));
        User p = userRepository.save(new User(player, "p"));
        membershipRepository.save(new Membership(campaign.getId(), p.getUserId(), Role.PLAYER));
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();

        // imagem, mundo, enciclopedia ligada ao pais
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(3, 3, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        ImageResponse map = imageService.upload(campaign.getId(), png.toByteArray(), "map.png", master);
        var world = worldService.createWorld(campaign.getId(), new WorldRequest("Faerun", map.url(), "secret"), master);
        var country = worldService.addCountry(world.id(), new CountryRequest("Phandalin", null, null, null, 0.5f, 0.5f), master);
        encyclopediaService.create(campaign.getId(), new EncyclopediaEntryRequest("About Phandalin", "Small town", null,
                false, ReferenceType.COUNTRY, country.countryId()), master);

        // NPC com ficha de monstro
        Monster bandit = new Monster("Bandit-" + UUID.randomUUID(), xphb);
        bandit.updateCore("Medium", "Humanoid", "Neutral", 12, null, 11, "2d8+2", "30 ft.", new int[]{11, 12, 12, 10, 10, 10}, "1/8");
        monsterRepository.save(bandit);
        var npc = npcService.create(campaign.getId(), "Glasstaff", null, "Is Iarno", null, master);
        npcService.useStatBlock(npc.id(), bandit.getId(), master);

        // personagem com classe, pericia, ataque e item; party
        DndClass fighter = dndClassRepository.save(new DndClass("Fighter-" + UUID.randomUUID(), "XPHB", 1, 10, 3, xphb,
                null, null, null, null, null, null, null, null));
        Item rope = itemRepository.save(new Item("Rope-" + UUID.randomUUID(), xphb, ItemType.MUNDANE, null, null, Rarity.COMMON, BigDecimal.ONE));
        Character hero = characterService.create(new CreateCharacterRequest(campaign.getId(), "Hero"), player);
        characterService.updateAttributes(hero.getCharacterId(), new UpdateAttributesRequest(16, 12, 14, 8, 10, 13), player);
        characterService.addClass(hero.getCharacterId(), new AddCharacterClassRequest(fighter.getClassId(), 3), player);
        characterService.updateCombatStats(hero.getCharacterId(), new UpdateCombatStatsRequest(28, 20, null, 18, 30), player);
        sheetService.updateProficiencies(hero.getCharacterId(), new ProficienciesRequest(Set.of(Skill.ATHLETICS), null, null), player);
        sheetService.addAttack(hero.getCharacterId(), new AttackRequest("Longsword", 5, "1d8+3", "slashing", null), player);
        sheetService.changePortrait(hero.getCharacterId(), new PortraitRequest(map.url()), player);
        var inventory = inventoryService.getOrCreateCharacterInventory(hero);
        inventoryService.addItem(hero, inventory.getId(), rope.getId(), 2);
        inventoryService.changeMoney(hero, inventory.getId(), new BigDecimal("12.5"));
        inventoryService.changeCoins(hero, inventory.getId(), Currency.SILVER, 30);
        // recurso feito a mao (2 de 7 cargas gastas) e Inspiracao Heroica viajam no arquivo
        sheetService.addCustomResource(hero.getCharacterId(), new top.customizr.system.dto.CustomResourceRequest(
                "Wand of Magic Missiles", 7, top.customizr.system.model.ResourceRecharge.MANUAL, null), player);
        String wandId = sheetService.addCustomResource(hero.getCharacterId(), new top.customizr.system.dto.CustomResourceRequest(
                "Ring charges", 3, null, null), player).details().resources().stream()
                .filter(r -> r.name().equals("Wand of Magic Missiles")).findFirst().orElseThrow().id();
        sheetService.spendResource(hero.getCharacterId(), new top.customizr.system.dto.ResourceRequest(wandId, 2), player);
        sheetService.changeInspiration(hero.getCharacterId(), new top.customizr.system.dto.InspirationRequest(true), master);
        var party = partyService.create(campaign.getId(), new CreatePartyRequest("Heroes", 4), master);
        partyService.addCharacter(party.partyId(), hero.getCharacterId(), master);

        assertThrows(AccessDeniedException.class, () -> transferService.export(campaign.getId(), true, player),
                "so o mestre exporta");
        CampaignExport exported = transferService.export(campaign.getId(), true, master);
        // passa pelo JSON de verdade, como o arquivo baixado
        CampaignExport file = objectMapper.readValue(objectMapper.writeValueAsString(exported), CampaignExport.class);

        String importer = "importer-" + UUID.randomUUID();
        userRepository.save(new User(importer, "p"));
        CampaignImportResponse result = transferService.importCampaign(file, importer);

        assertNotEquals(campaign.getId(), result.campaignId(), "sempre uma campanha nova");
        assertEquals("Lost Mine", result.name());
        assertEquals(1, result.characters());
        assertEquals(1, result.images());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("belonged to " + player)));

        var newWorld = worldService.listWorlds(result.campaignId(), importer).getFirst();
        assertEquals("secret", newWorld.masterInfo());
        assertTrue(newWorld.mapUrl().startsWith("/image/") && !newWorld.mapUrl().equals(map.url()), "endereco da imagem trocado pelo novo");
        var entry = encyclopediaService.list(result.campaignId(), null, importer).getFirst();
        assertEquals(newWorld.countries().getFirst().countryId(), entry.referenceId(), "referencia religada ao pais novo");
        assertEquals(bandit.getName(), npcService.listByCampaign(result.campaignId(), importer).getFirst().statBlockName());

        Character copy = characterRepository.allCampaignCharacters(result.campaignId()).getFirst();
        var sheet = characterService.getCharacterSheet(copy.getCharacterId(), importer);
        assertEquals(importer, copy.getOwner().getName());
        assertEquals(3, sheet.combat().totalLevel());
        assertEquals(20, sheet.combat().hpCurrent());
        assertEquals(16, sheet.attributes().strength());
        assertTrue(sheet.details().skills().stream().anyMatch(s -> s.skill() == Skill.ATHLETICS && s.proficient()));
        assertEquals("Longsword", sheet.details().attacks().getFirst().name());
        assertEquals(newWorld.mapUrl(), sheet.details().portraitUrl());
        assertEquals("Heroes", sheet.party().name());
        assertTrue(sheet.details().heroicInspiration());
        var wand = sheet.details().resources().stream().filter(r -> r.name().equals("Wand of Magic Missiles")).findFirst().orElseThrow();
        assertEquals(5, wand.remaining());
        assertEquals(top.customizr.system.model.ResourceRecharge.MANUAL, wand.recharge());
        var copiedInventory = InventoryResponse.from(inventoryService.getOrCreateCharacterInventory(copy));
        assertEquals(2, copiedInventory.entries().getFirst().quantity());
        assertEquals(35L, copiedInventory.coins().get("sp"), "moedas separadas viajam no arquivo (5 pp do 12,5 po + 30 pp)");
        assertEquals(0, new BigDecimal("15.5").compareTo(copiedInventory.moneyGp()), "12,5 po + 30 pp");

        assertThrows(ResponseStatusException.class, () -> transferService.importCampaign(
                objectMapper.readValue("{\"formatVersion\": 1}", CampaignExport.class), importer));
    }
}
