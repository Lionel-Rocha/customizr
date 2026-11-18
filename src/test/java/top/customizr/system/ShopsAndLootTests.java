package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.dto.LootItemRequest;
import top.customizr.system.dto.LootRequest;
import top.customizr.system.dto.LootResponse;
import top.customizr.system.dto.ShopItemRequest;
import top.customizr.system.dto.ShopRequest;
import top.customizr.system.dto.ShopResponse;
import top.customizr.system.dto.SplitMoneyRequest;
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
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.InventoryService;
import top.customizr.system.service.TradeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lojas (comprar, vender, estoque) e espolio (pegar, dividir moedas). */
@SpringBootTest
@Transactional
class ShopsAndLootTests {

    @Autowired private TradeService trade;
    @Autowired private CharacterService characterService;
    @Autowired private InventoryService inventoryService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private top.customizr.system.service.CampaignTransferService transfer;

    private String master;
    private String ana;
    private String bia;
    private Campaign campaign;
    private Character aria;
    private Character bruno;
    private Item rope;
    private Item potion;

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
        bruno = characterService.create(new CreateCharacterRequest(campaign.getId(), "Bruno"), bia);
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        rope = itemRepository.save(new Item("Rope-" + UUID.randomUUID(), xphb, ItemType.MUNDANE, null, null, Rarity.COMMON, BigDecimal.ONE));
        potion = itemRepository.save(new Item("Potion-" + UUID.randomUUID(), xphb, ItemType.MAGICAL, null, null, Rarity.COMMON, new BigDecimal("50")));
        inventoryService.getOrCreateCharacterInventory(aria).changeMoney(new BigDecimal("120"));
    }

    @Test
    void buyingTakesMoneyAndStockAndSellingPaysTheShopRate() {
        ShopResponse shop = trade.createShop(campaign.getId(), new ShopRequest("Alquimista", "Poções e ervas", false, 50), master);
        shop = trade.addShopItem(shop.id(), new ShopItemRequest(potion.getId(), null, 2), master);
        UUID potionEntry = shop.items().getFirst().entryId();
        assertEquals(0, new BigDecimal("50").compareTo(shop.items().getFirst().priceGp()), "preco do catalogo");

        assertTrue(trade.shops(campaign.getId(), ana).isEmpty(), "loja fechada nao aparece pro jogador");
        UUID shopId = shop.id();
        assertThrows(AccessDeniedException.class, () -> trade.buy(shopId, new TradeRequest(aria.getCharacterId(), potionEntry, null, 1, null), ana));
        trade.updateShop(shopId, new ShopRequest(null, null, true, null), master);

        InventoryResponse after = trade.buy(shopId, new TradeRequest(aria.getCharacterId(), potionEntry, null, 2, null), ana);
        assertEquals(0, new BigDecimal("20").compareTo(after.moneyGp()), "120 - 2 x 50");
        assertEquals(0, trade.shops(campaign.getId(), ana).getFirst().items().getFirst().stock(), "estoque acabou");
        assertThrows(ResponseStatusException.class, () -> trade.buy(shopId, new TradeRequest(aria.getCharacterId(), potionEntry, null, 1, null), ana),
                "sem estoque");
        assertThrows(AccessDeniedException.class, () -> trade.buy(shopId, new TradeRequest(aria.getCharacterId(), potionEntry, null, 1, null), bia),
                "personagem de outra pessoa");

        InventoryResponse sold = trade.sell(shopId, new TradeRequest(aria.getCharacterId(), null, potion.getId(), 1, null), ana);
        assertEquals(0, new BigDecimal("45").compareTo(sold.moneyGp()), "20 + metade de 50");
        assertEquals(1, trade.shops(campaign.getId(), ana).getFirst().items().getFirst().stock(), "voltou pro estoque");
    }

    @Test
    void shopsAndLootTravelInTheExport() {
        ShopResponse shop = trade.createShop(campaign.getId(), new ShopRequest("Ferreiro", "Martelo e brasa", true, 40), master);
        trade.addShopItem(shop.id(), new ShopItemRequest(rope.getId(), new BigDecimal("2"), 5), master);
        LootResponse chest = trade.createLoot(campaign.getId(), new LootRequest("Baú", new BigDecimal("30"), false), master);
        trade.addLootItem(chest.id(), new LootItemRequest(potion.getId(), 2), master);

        var imported = transfer.importCampaign(transfer.export(campaign.getId(), false, master), master);
        var shops = trade.shops(imported.campaignId(), master);
        assertEquals(1, shops.size());
        assertEquals(40, shops.getFirst().buyBackPercent());
        assertEquals(5, shops.getFirst().items().getFirst().stock());
        var loot = trade.loot(imported.campaignId(), master);
        assertEquals(0, new BigDecimal("30").compareTo(loot.getFirst().moneyGp()));
        assertEquals(2, loot.getFirst().items().getFirst().quantity());
    }

    @Test
    void notEnoughMoneyIsRefused() {
        ShopResponse shop = trade.createShop(campaign.getId(), new ShopRequest("Ferreiro", null, true, null), master);
        shop = trade.addShopItem(shop.id(), new ShopItemRequest(rope.getId(), new BigDecimal("500"), null), master);
        UUID shopId = shop.id(), entry = shop.items().getFirst().entryId();
        assertThrows(ResponseStatusException.class, () -> trade.buy(shopId, new TradeRequest(aria.getCharacterId(), entry, null, 1, null), ana));
        assertEquals(0, new BigDecimal("120").compareTo(inventoryService.getOrCreateCharacterInventory(aria).getMoneyGp()));
    }

    @Test
    void lootIsTakenAndItsMoneySplitEvenly() {
        LootResponse chest = trade.createLoot(campaign.getId(), new LootRequest("Baú do dragão", new BigDecimal("100.01"), false), master);
        UUID lootId = chest.id();
        chest = trade.addLootItem(lootId, new LootItemRequest(rope.getId(), 3), master);
        UUID ropeEntry = chest.items().getFirst().entryId();
        assertTrue(trade.loot(campaign.getId(), ana).isEmpty(), "baú escondido");
        assertThrows(AccessDeniedException.class, () -> trade.take(lootId, new TradeRequest(aria.getCharacterId(), ropeEntry, null, 1, null), ana));

        trade.updateLoot(lootId, new LootRequest(null, null, true), master);
        InventoryResponse got = trade.take(lootId, new TradeRequest(aria.getCharacterId(), ropeEntry, null, 2, null), ana);
        assertEquals(2, got.entries().stream().filter(e -> e.itemId().equals(rope.getId())).findFirst().orElseThrow().quantity());
        assertEquals(1, trade.loot(campaign.getId(), ana).getFirst().items().getFirst().quantity());
        assertThrows(ResponseStatusException.class, () -> trade.take(lootId, new TradeRequest(bruno.getCharacterId(), ropeEntry, null, 5, null), bia));

        assertThrows(AccessDeniedException.class, () -> trade.splitMoney(lootId, null, ana));
        LootResponse split = trade.splitMoney(lootId, new SplitMoneyRequest(List.of(aria.getCharacterId(), bruno.getCharacterId())), master);
        assertEquals(0, new BigDecimal("0.01").compareTo(split.moneyGp()), "100,01 / 2 = 50 cada, sobra 1 pc");
        assertEquals(0, new BigDecimal("170").compareTo(inventoryService.getOrCreateCharacterInventory(aria).getMoneyGp()), "120 + 50");
        assertEquals(0, new BigDecimal("50").compareTo(inventoryService.getOrCreateCharacterInventory(bruno).getMoneyGp()));
    }
}
