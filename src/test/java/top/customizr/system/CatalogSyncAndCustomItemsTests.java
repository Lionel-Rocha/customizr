package top.customizr.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CatalogItemResponse;
import top.customizr.system.dto.CustomItemRequest;
import top.customizr.system.dto.ShopItemRequest;
import top.customizr.system.dto.ShopRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignItemService;
import top.customizr.system.service.CampaignTransferService;
import top.customizr.system.service.FiveToolsSyncService;
import top.customizr.system.service.ItemService;
import top.customizr.system.service.TradeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atualizacao pelo 5etools (com dados falsos, sem internet) separando oficial e nao oficial, e itens proprios
 * do mestre. Sem @Transactional: o importador grava cada item na sua propria transacao.
 */
@SpringBootTest
class CatalogSyncAndCustomItemsTests {

    @Autowired private FiveToolsSyncService fiveTools;
    @Autowired private CampaignItemService campaignItems;
    @Autowired private ItemService itemService;
    @Autowired private TradeService trade;
    @Autowired private CampaignTransferService transfer;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    private final ObjectMapper json = new ObjectMapper();
    private String tag;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Test
    void syncKeepsOnlyOfficialBooksUnlessAskedAndRegistersTheirSources() {
        String wotc = "W" + tag, partner = "P" + tag, stray = "S" + tag;
        Map<String, JsonNode> files = Map.of(
                "books.json", read("{\"book\":[{\"name\":\"Livro Oficial " + tag + "\",\"source\":\"" + wotc + "\",\"author\":\"Wizards RPG Team\"}]}"),
                "adventures.json", read("{\"adventure\":[{\"name\":\"Aventura Parceira " + tag + "\",\"source\":\"" + partner + "\",\"author\":\"Critical Role\"}]}"),
                "items-base.json", read("{\"baseitem\":[{\"name\":\"Club " + tag + "\",\"source\":\"" + wotc + "\",\"type\":\"M\",\"rarity\":\"none\",\"value\":10}]}"),
                "items.json", read("{\"item\":["
                        + "{\"name\":\"Amulet " + tag + "\",\"source\":\"" + wotc + "\",\"rarity\":\"rare\",\"wondrous\":true},"
                        + "{\"name\":\"Odd Box " + tag + "\",\"source\":\"" + wotc + "\",\"rarity\":\"unknown (magic)\",\"wondrous\":true},"
                        + "{\"name\":\"Partner Ring " + tag + "\",\"source\":\"" + partner + "\",\"type\":\"RG\",\"rarity\":\"uncommon\"},"
                        + "{\"name\":\"Stray Rope " + tag + "\",\"source\":\"" + stray + "\",\"type\":\"G\",\"rarity\":\"none\",\"value\":100}]}"));

        var first = fiveTools.syncWith(files::get, false);
        assertEquals(3, first.itemsCreated());
        var odd = item("Odd Box " + tag, wotc);
        assertEquals(top.customizr.system.model.ItemType.MAGICAL, odd.getType());
        assertEquals(null, odd.getPriceGp(), "raridade desconhecida: sem valor do DMG, o mestre define");
        assertEquals(2, first.unofficialItemsLeftOut());
        assertEquals(Boolean.TRUE, sourceRepository.getSourceByCode(wotc).orElseThrow().getOfficial());
        assertEquals("Livro Oficial " + tag, sourceRepository.getSourceByCode(wotc).orElseThrow().getName());
        assertTrue(sourceRepository.getSourceByCode(partner).isEmpty(), "nao oficial nem cadastra a fonte");
        assertEquals(0, new BigDecimal("4000").compareTo(item("Amulet " + tag, wotc).getPriceGp()), "valor do DMG 2024");

        var second = fiveTools.syncWith(files::get, true);
        assertEquals(2, second.itemsCreated(), "parceiro e fonte fora da lista");
        assertEquals(3, second.itemsUpdated());
        assertEquals(Boolean.FALSE, sourceRepository.getSourceByCode(partner).orElseThrow().getOfficial());
        assertEquals(Boolean.FALSE, sourceRepository.getSourceByCode(stray).orElseThrow().getOfficial());
        assertEquals(0, new BigDecimal("1").compareTo(item("Stray Rope " + tag, stray).getPriceGp()));
    }

    @Test
    void masterItemsLiveBesideTheCatalogOnlyInTheirCampaign() {
        String master = "m-" + tag, ana = "ana-" + tag;
        Campaign campaign = campaignRepository.save(new Campaign("C " + tag, userRepository.save(new User(master, "p"))));
        Campaign other = campaignRepository.save(new Campaign("O " + tag, userRepository.save(new User("o-" + tag, "p"))));
        User anaUser = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), anaUser.getUserId(), Role.PLAYER));
        UUID campaignId = campaign.getId();

        String name = "Espada do Avo " + tag;
        CatalogItemResponse sword = campaignItems.create(campaignId,
                new CustomItemRequest(name, "MAGICAL", "WEAPON", "RARE", new BigDecimal("500"), null, null), master);
        assertEquals("CUSTOM", sword.origin());
        assertThrows(AccessDeniedException.class, () -> campaignItems.create(campaignId,
                new CustomItemRequest("Outra " + tag, null, null, null, null, null, null), ana));
        assertThrows(ResponseStatusException.class, () -> campaignItems.create(campaignId,
                new CustomItemRequest(name, null, null, null, null, null, null), master), "nome repetido");

        List<CatalogItemResponse> found = campaignItems.search(campaignId, "Espada do Avo " + tag, null, ana);
        assertEquals(1, found.size(), "o jogador acha na busca da campanha");
        assertTrue(campaignItems.search(campaignId, "Espada do Avo " + tag, "OFFICIAL", ana).isEmpty(), "filtro de origem");
        assertTrue(itemService.search("Espada do Avo " + tag).isEmpty(), "busca geral nao mostra");
        assertTrue(campaignItems.search(other.getId(), "Espada do Avo " + tag, null, "o-" + tag).isEmpty(), "outra campanha nao ve");

        var shop = trade.createShop(campaignId, new ShopRequest("Ferreiro", null, true, 50), master);
        shop = trade.addShopItem(shop.id(), new ShopItemRequest(sword.id(), null, null), master);
        assertEquals(0, new BigDecimal("500").compareTo(shop.items().getFirst().priceGp()));
        var otherShop = trade.createShop(other.getId(), new ShopRequest("X", null, true, 50), "o-" + tag);
        assertThrows(ResponseStatusException.class,
                () -> trade.addShopItem(otherShop.id(), new ShopItemRequest(sword.id(), null, null), "o-" + tag));

        var updated = campaignItems.update(campaignId, sword.id(), new CustomItemRequest(null, null, null, null, new BigDecimal("650"), null, null), master);
        assertEquals(0, new BigDecimal("650").compareTo(updated.priceGp()));

        var imported = transfer.importCampaign(transfer.export(campaignId, false, master), master);
        var copies = campaignItems.custom(imported.campaignId(), master);
        assertEquals(1, copies.size(), "item do mestre vai na exportacao");
        assertFalse(copies.getFirst().id().equals(sword.id()), "vira um item da campanha nova");
        var importedShop = trade.shops(imported.campaignId(), master).getFirst();
        assertEquals(copies.getFirst().id(), importedShop.items().getFirst().itemId(), "loja aponta pro item da campanha nova");

        campaignItems.delete(campaignId, sword.id(), master);
        assertTrue(campaignItems.custom(campaignId, master).isEmpty());
    }

    private JsonNode read(String text) {
        return json.readTree(text);
    }

    private top.customizr.system.model.Item item(String name, String sourceCode) {
        var source = sourceRepository.getSourceByCode(sourceCode).orElseThrow();
        return itemRepository.findByNameAndSource_Id(name, source.getId()).orElseThrow();
    }
}
