package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.ShopItemRequest;
import top.customizr.system.dto.ShopRequest;
import top.customizr.system.dto.ShopResponse;
import top.customizr.system.dto.TradeRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignTransferService;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.InventoryService;
import top.customizr.system.service.ItemPriceService;
import top.customizr.system.service.ItemService;
import top.customizr.system.service.MagicItemPricing;
import top.customizr.system.service.TradeService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importacao com precos oficiais, valor do DMG 2024 pros magicos e preco do mestre pros comuns sem preco. */
@SpringBootTest
@Transactional
class ItemPricesTests {

    @Autowired private ItemService itemService;
    @Autowired private ItemPriceService itemPrices;
    @Autowired private TradeService trade;
    @Autowired private CampaignTransferService transfer;
    @Autowired private CharacterService characterService;
    @Autowired private InventoryService inventoryService;
    @Autowired private ItemRepository itemRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ObjectMapper json = new ObjectMapper();
    private String tag;
    private Source xphb;
    private Source xdmg;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        // a importacao roda em transacao propria: a fonte tem que estar gravada de verdade
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            if (sourceRepository.getSourceByCode("XDMG").isEmpty()) {
                sourceRepository.save(new Source("Dungeon Master's Guide (2024)", "XDMG"));
            }
        });
        xdmg = sourceRepository.getSourceByCode("XDMG").orElseThrow();
    }

    @Test
    void dmgTableByRarityWithConsumablesScrollsAndBaseItems() {
        assertEquals(0, new BigDecimal("4000").compareTo(MagicItemPricing.price(Rarity.RARE, "RG|XDMG", "Ring", null)));
        assertEquals(0, new BigDecimal("200").compareTo(MagicItemPricing.price(Rarity.UNCOMMON, "P|XPHB", "Potion", null)), "consumivel: metade");
        assertEquals(0, new BigDecimal("4015").compareTo(MagicItemPricing.price(Rarity.RARE, "M", "Flame Tongue", new BigDecimal("15"))), "soma a arma");
        assertEquals(0, new BigDecimal("300").compareTo(MagicItemPricing.price(Rarity.UNCOMMON, "SC|XPHB", "Spell Scroll (Level 3)", null)), "2 x 150");
        assertEquals(0, new BigDecimal("30").compareTo(MagicItemPricing.price(Rarity.COMMON, "SC|XPHB", "Spell Scroll (Cantrip)", null)));
        assertEquals(0, new BigDecimal("200000").compareTo(MagicItemPricing.price(Rarity.LEGENDARY, null, "Vorpal", null)));
        assertNull(MagicItemPricing.price(Rarity.ARTIFACT, null, "Wand of Orcus", null), "artefato nao tem preco");
    }

    @Test
    void importReadsBaseItemsAndPricesMagicItemsByTheDmg() {
        var result = itemService.importFromDump(dump());
        assertEquals(5, result.imported().size(), result.skipped().toString());

        Item sword = item("Sword " + tag, xphb);
        assertEquals(ItemType.MUNDANE, sword.getType());
        assertEquals(0, new BigDecimal("15").compareTo(sword.getPriceGp()), "valor oficial (1500 pc)");
        Item flame = item("Flame " + tag, xdmg);
        assertEquals(ItemType.MAGICAL, flame.getType());
        assertEquals(0, new BigDecimal("4015").compareTo(flame.getPriceGp()), "rara 4000 + espada 15");
        assertEquals(0, new BigDecimal("200").compareTo(item("Potion " + tag, xdmg).getPriceGp()));
        assertNull(item("Grenade " + tag, xdmg).getPriceGp(), "comum sem preco oficial fica sem");
        assertEquals(ItemType.MUNDANE, item("Grenade " + tag, xdmg).getType());
        assertNull(item("Orcus " + tag, xdmg).getPriceGp());
    }

    @Test
    void masterPricesUnpricedMundaneItemsForTheCampaignOnly() {
        itemService.importFromDump(dump());
        Item grenade = item("Grenade " + tag, xdmg);
        Item sword = item("Sword " + tag, xphb);
        String master = "m-" + tag, ana = "ana-" + tag;
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User anaUser = userRepository.save(new User(ana, "p"));
        membershipRepository.save(new Membership(campaign.getId(), anaUser.getUserId(), Role.PLAYER));
        Character aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        inventoryService.getOrCreateCharacterInventory(aria).changeMoney(new BigDecimal("100"));

        var unpriced = itemPrices.unpriced(campaign.getId(), tag, false, master);
        assertTrue(unpriced.stream().anyMatch(i -> i.itemId().equals(grenade.getId())));
        assertTrue(unpriced.stream().noneMatch(i -> i.itemId().equals(sword.getId())), "ja tem preco oficial");
        assertTrue(unpriced.stream().noneMatch(i -> i.name().startsWith("Orcus")), "magico so com includeMagic");
        assertThrows(AccessDeniedException.class, () -> itemPrices.set(campaign.getId(), grenade.getId(), BigDecimal.TEN, ana));
        assertThrows(ResponseStatusException.class, () -> itemPrices.set(campaign.getId(), sword.getId(), BigDecimal.TEN, master));

        itemPrices.set(campaign.getId(), grenade.getId(), new BigDecimal("25"), master);
        assertNull(itemRepository.findById(grenade.getId()).orElseThrow().getPriceGp(), "catalogo nao muda");
        assertEquals(0, new BigDecimal("25").compareTo(itemPrices.campaignPrices(campaign.getId(), ana).get(grenade.getId())));

        ShopResponse shop = trade.createShop(campaign.getId(), new ShopRequest("Armeiro", null, true, 50), master);
        shop = trade.addShopItem(shop.id(), new ShopItemRequest(grenade.getId(), null, null), master);
        assertEquals(0, new BigDecimal("25").compareTo(shop.items().getFirst().priceGp()), "loja usa o preco do mestre");
        var after = trade.buy(shop.id(), new TradeRequest(aria.getCharacterId(), shop.items().getFirst().entryId(), null, 2, null), ana);
        assertEquals(0, new BigDecimal("50").compareTo(after.moneyGp()));

        var imported = transfer.importCampaign(transfer.export(campaign.getId(), false, master), master);
        assertEquals(0, new BigDecimal("25").compareTo(itemPrices.campaignPrices(imported.campaignId(), master).get(grenade.getId())),
                "preco do mestre vai na exportacao");

        itemPrices.set(campaign.getId(), grenade.getId(), null, master);
        assertTrue(itemPrices.campaignPrices(campaign.getId(), master).isEmpty(), "vazio apaga");
    }

    private Item item(String name, Source source) {
        return itemRepository.findByNameAndSource_Id(name, source.getId()).orElseThrow();
    }

    private JsonNode dump() {
        String sword = "Sword " + tag;
        return json.readTree("""
                {"baseitem": [
                  {"name": "%1$s", "source": "XPHB", "type": "M|XPHB", "rarity": "none", "value": 1500, "dmg1": "1d8"}
                ],
                 "item": [
                  {"name": "Flame %2$s", "source": "XDMG", "type": "M|XPHB", "rarity": "rare", "baseItem": "%3$s|xphb"},
                  {"name": "Potion %2$s", "source": "XDMG", "type": "P|XPHB", "rarity": "uncommon"},
                  {"name": "Grenade %2$s", "source": "XDMG", "type": "EXP|XDMG", "rarity": "none"},
                  {"name": "Orcus %2$s", "source": "XDMG", "rarity": "artifact", "wondrous": true}
                ]}
                """.formatted(sword, tag, sword.toLowerCase()));
    }
}
