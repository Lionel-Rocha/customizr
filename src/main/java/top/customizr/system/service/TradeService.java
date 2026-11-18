package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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
import top.customizr.system.model.Inventory;
import top.customizr.system.model.Item;
import top.customizr.system.model.Loot;
import top.customizr.system.model.Shop;
import top.customizr.system.model.ShopEntry;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.LootRepository;
import top.customizr.system.repository.ShopRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lojas e espolio. O mestre monta; o jogador compra, vende e pega pelo proprio personagem (o mestre pode agir por
 * qualquer um). O dinheiro e o do inventario do personagem (em po). Tudo que muda vai pro chat e pra mesa ao vivo.
 */
@Service
public class TradeService {

    private final ShopRepository shopRepository;
    private final LootRepository lootRepository;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final MasterGuard masterGuard;
    private final ChatService chatService;
    private final LiveEventService events;
    private final ItemPriceService itemPrices;

    public TradeService(ShopRepository shopRepository, LootRepository lootRepository, CampaignRepository campaignRepository,
                        CharacterRepository characterRepository, ItemRepository itemRepository, InventoryService inventoryService,
                        MasterGuard masterGuard, ChatService chatService, LiveEventService events,
                        ItemPriceService itemPrices) {
        this.itemPrices = itemPrices;
        this.shopRepository = shopRepository;
        this.lootRepository = lootRepository;
        this.campaignRepository = campaignRepository;
        this.characterRepository = characterRepository;
        this.itemRepository = itemRepository;
        this.inventoryService = inventoryService;
        this.masterGuard = masterGuard;
        this.chatService = chatService;
        this.events = events;
    }

    // ------------------------------------------------------------------ lojas (mestre monta)

    /** Mestre ve todas; jogador, so as abertas. */
    @Transactional
    public List<ShopResponse> shops(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        return shopRepository.findByCampaignOrderByName(campaign).stream().filter(s -> master || s.isOpen())
                .map(s -> ShopResponse.from(s, itemPrices.pricesOf(campaign))).toList();
    }

    @Transactional
    public ShopResponse createShop(UUID campaignId, ShopRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (r == null) throw badRequest("Request body is required");
        Shop shop = valid(() -> new Shop(campaign, r.name(), r.description(), Boolean.TRUE.equals(r.open()), r.buyBackPercent()));
        return broadcast(shopRepository.save(shop));
    }

    @Transactional
    public ShopResponse updateShop(UUID shopId, ShopRequest r, String username) {
        Shop shop = shopOfMaster(shopId, username);
        if (r == null) throw badRequest("Request body is required");
        valid(() -> { shop.update(r.name() == null ? shop.getName() : r.name(), r.description() == null ? shop.getDescription() : r.description(),
                r.open() == null ? shop.isOpen() : r.open(), r.buyBackPercent() == null ? shop.getBuyBackPercent() : r.buyBackPercent()); return null; });
        return broadcast(shop);
    }

    @Transactional
    public void deleteShop(UUID shopId, String username) {
        Shop shop = shopOfMaster(shopId, username);
        UUID campaignId = shop.getCampaign().getId();
        shopRepository.delete(shop);
        events.publish(campaignId, "trade", Map.of("campaignId", campaignId));
    }

    @Transactional
    public ShopResponse addShopItem(UUID shopId, ShopItemRequest r, String username) {
        Shop shop = shopOfMaster(shopId, username);
        if (r == null || r.itemId() == null) throw badRequest("itemId is required");
        Item item = item(r.itemId());
        valid(() -> shop.addItem(item, r.priceGp(), r.stock()));
        shopRepository.flush();
        return broadcast(shop);
    }

    @Transactional
    public ShopResponse updateShopItem(UUID shopId, UUID entryId, ShopItemRequest r, String username) {
        Shop shop = shopOfMaster(shopId, username);
        if (r == null) throw badRequest("Request body is required");
        valid(() -> { shop.entry(entryId).update(r.priceGp(), r.stock()); return null; });
        return broadcast(shop);
    }

    @Transactional
    public ShopResponse removeShopItem(UUID shopId, UUID entryId, String username) {
        Shop shop = shopOfMaster(shopId, username);
        valid(() -> shop.getEntries().remove(shop.entry(entryId)));
        return broadcast(shop);
    }

    // ------------------------------------------------------------------ comprar e vender (jogador)

    /** Compra pelo personagem: desconta o dinheiro, poe no inventario, baixa o estoque. */
    @Transactional
    public InventoryResponse buy(UUID shopId, TradeRequest r, String username) {
        Shop shop = shop(shopId);
        Character buyer = actor(r, shop.getCampaign(), username);
        if (!shop.isOpen() && !masterGuard.canManage(masterGuard.user(username), shop.getCampaign())) {
            throw new AccessDeniedException("This shop is closed");
        }
        int quantity = quantity(r);
        ShopEntry entry = valid(() -> shop.entry(r.entryId()));
        BigDecimal price = entryPrice(entry);
        if (price == null) throw badRequest(entry.getItem().getName() + " has no price; ask the master");
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
        Inventory inventory = inventoryService.getOrCreateCharacterInventory(buyer);
        valid(() -> {
            if (inventory.getMoneyGp().compareTo(total) < 0) {
                throw new IllegalArgumentException(buyer.getName() + " needs " + gp(total) + " but has " + gp(inventory.getMoneyGp()));
            }
            entry.take(quantity);
            inventory.changeMoney(total.negate());
            inventory.addItem(entry.getItem(), quantity);
            return null;
        });
        chatService.postCard(shop.getCampaign().getId(), buyer.getCharacterId(), "🛒 " + shop.getName(),
                buyer.getName() + " bought " + quantity + "x " + entry.getItem().getName() + " for " + gp(total), username);
        broadcast(shop);
        return InventoryResponse.from(inventory);
    }

    /** Vende pro lojista: tira do inventario e recebe a porcentagem da loja (volta pro estoque, se a loja vende o item). */
    @Transactional
    public InventoryResponse sell(UUID shopId, TradeRequest r, String username) {
        Shop shop = shop(shopId);
        Character seller = actor(r, shop.getCampaign(), username);
        if (!shop.isOpen() && !masterGuard.canManage(masterGuard.user(username), shop.getCampaign())) {
            throw new AccessDeniedException("This shop is closed");
        }
        if (r.itemId() == null) throw badRequest("itemId is required");
        int quantity = quantity(r);
        Item item = item(r.itemId());
        BigDecimal base = shop.getEntries().stream().filter(e -> e.getItem().getId().equals(item.getId())).findFirst()
                .map(this::entryPrice).orElseGet(() -> itemPrices.price(shop.getCampaign(), item));
        if (base == null) throw badRequest(item.getName() + " has no price; ask the master");
        BigDecimal total = base.multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(shop.getBuyBackPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        Inventory inventory = inventoryService.getOrCreateCharacterInventory(seller);
        valid(() -> {
            inventory.removeItem(item.getId(), quantity);
            inventory.changeMoney(total);
            return null;
        });
        shop.getEntries().stream().filter(e -> e.getItem().getId().equals(item.getId())).findFirst().ifPresent(e -> e.restock(quantity));
        chatService.postCard(shop.getCampaign().getId(), seller.getCharacterId(), "💰 " + shop.getName(),
                seller.getName() + " sold " + quantity + "x " + item.getName() + " for " + gp(total), username);
        broadcast(shop);
        return InventoryResponse.from(inventory);
    }

    // ------------------------------------------------------------------ espolio

    @Transactional
    public List<LootResponse> loot(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        return lootRepository.findByCampaignOrderByName(campaign).stream().filter(l -> master || l.isVisible())
                .map(LootResponse::from).toList();
    }

    @Transactional
    public LootResponse createLoot(UUID campaignId, LootRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (r == null) throw badRequest("Request body is required");
        Loot loot = valid(() -> new Loot(campaign, r.name(), r.moneyGp(), Boolean.TRUE.equals(r.visible())));
        return broadcast(lootRepository.save(loot));
    }

    @Transactional
    public LootResponse updateLoot(UUID lootId, LootRequest r, String username) {
        Loot loot = lootOfMaster(lootId, username);
        if (r == null) throw badRequest("Request body is required");
        valid(() -> { loot.update(r.name() == null ? loot.getName() : r.name(), r.moneyGp(),
                r.visible() == null ? loot.isVisible() : r.visible()); return null; });
        return broadcast(loot);
    }

    @Transactional
    public void deleteLoot(UUID lootId, String username) {
        Loot loot = lootOfMaster(lootId, username);
        UUID campaignId = loot.getCampaign().getId();
        lootRepository.delete(loot);
        events.publish(campaignId, "trade", Map.of("campaignId", campaignId));
    }

    @Transactional
    public LootResponse addLootItem(UUID lootId, LootItemRequest r, String username) {
        Loot loot = lootOfMaster(lootId, username);
        if (r == null || r.itemId() == null) throw badRequest("itemId is required");
        Item item = item(r.itemId());
        valid(() -> loot.addItem(item, r.quantity() == null ? 1 : r.quantity()));
        lootRepository.flush();
        return broadcast(loot);
    }

    /** O mestre tira do bau (sem dar pra ninguem). */
    @Transactional
    public LootResponse removeLootItem(UUID lootId, UUID entryId, String username) {
        Loot loot = lootOfMaster(lootId, username);
        valid(() -> loot.take(entryId, loot.getEntries().stream().filter(e -> e.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This item is not in the loot")).getQuantity()));
        return broadcast(loot);
    }

    /** Pega do espolio: vai pro inventario do personagem (ou do grupo dele). */
    @Transactional
    public InventoryResponse take(UUID lootId, TradeRequest r, String username) {
        Loot loot = loot(lootId);
        Character taker = actor(r, loot.getCampaign(), username);
        if (!loot.isVisible() && !masterGuard.canManage(masterGuard.user(username), loot.getCampaign())) {
            throw new AccessDeniedException("This loot is not visible yet");
        }
        int quantity = quantity(r);
        Inventory target = Boolean.TRUE.equals(r.toParty()) ? inventoryService.getOrCreatePartyInventory(taker)
                : inventoryService.getOrCreateCharacterInventory(taker);
        Item item = valid(() -> loot.take(r.entryId(), quantity));
        valid(() -> target.addItem(item, quantity));
        chatService.postCard(loot.getCampaign().getId(), taker.getCharacterId(), "🎁 " + loot.getName(),
                taker.getName() + " took " + quantity + "x " + item.getName() + (Boolean.TRUE.equals(r.toParty()) ? " for the party" : ""), username);
        broadcast(loot);
        return InventoryResponse.from(target);
    }

    /** Divide as moedas igualmente (sobra o que nao divide, em pc). So o mestre. */
    @Transactional
    public LootResponse splitMoney(UUID lootId, SplitMoneyRequest r, String username) {
        Loot loot = lootOfMaster(lootId, username);
        List<Character> characters;
        if (r != null && r.characterIds() != null && !r.characterIds().isEmpty()) {
            characters = r.characterIds().stream().map(id -> characterRepository.findById(id)
                    .filter(c -> c.getCampaign().getId().equals(loot.getCampaign().getId()))
                    .orElseThrow(() -> notFound("Character"))).toList();
        } else {
            characters = characterRepository.allCampaignCharacters(loot.getCampaign().getId());
        }
        if (characters.isEmpty()) throw badRequest("Nobody to split the money with");
        BigDecimal share = loot.getMoneyGp().divide(BigDecimal.valueOf(characters.size()), 2, RoundingMode.DOWN);
        if (share.signum() == 0) throw badRequest("Not enough money to split");
        List<String> names = new ArrayList<>();
        for (Character c : characters) {
            inventoryService.getOrCreateCharacterInventory(c).changeMoney(share);
            names.add(c.getName());
        }
        loot.takeMoney(share.multiply(BigDecimal.valueOf(characters.size())));
        chatService.postCard(loot.getCampaign().getId(), null, "🎁 " + loot.getName(),
                gp(share) + " each for " + String.join(", ", names), username);
        return broadcast(loot);
    }

    // ------------------------------------------------------------------ apoio

    private Character actor(TradeRequest r, Campaign campaign, String username) {
        if (r == null || r.characterId() == null) throw badRequest("characterId is required");
        Character c = inventoryService.actorFor(r.characterId(), username);
        if (!c.getCampaign().getId().equals(campaign.getId())) throw badRequest("This character is from another campaign");
        return c;
    }

    private static int quantity(TradeRequest r) {
        int q = r.quantity() == null ? 1 : r.quantity();
        if (q < 1 || q > 1000) throw badRequest("Quantity goes from 1 to 1000");
        return q;
    }

    private static String gp(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + " gp";
    }

    private ShopResponse broadcast(Shop shop) {
        UUID campaignId = shop.getCampaign().getId();
        events.publish(campaignId, "trade", Map.of("campaignId", campaignId));
        return ShopResponse.from(shop, itemPrices.pricesOf(shop.getCampaign()));
    }

    /** Preco da loja; senao o oficial do catalogo; senao o que o mestre deu na campanha. */
    private BigDecimal entryPrice(ShopEntry entry) {
        return entry.getPriceGp() != null ? entry.getPriceGp() : itemPrices.price(entry.getShop().getCampaign(), entry.getItem());
    }

    private LootResponse broadcast(Loot loot) {
        UUID campaignId = loot.getCampaign().getId();
        events.publish(campaignId, "trade", Map.of("campaignId", campaignId));
        return LootResponse.from(loot);
    }

    private Shop shopOfMaster(UUID id, String username) {
        Shop shop = shop(id);
        masterGuard.requireMaster(username, shop.getCampaign());
        return shop;
    }

    private Loot lootOfMaster(UUID id, String username) {
        Loot loot = loot(id);
        masterGuard.requireMaster(username, loot.getCampaign());
        return loot;
    }

    private Shop shop(UUID id) {
        return shopRepository.findById(id).orElseThrow(() -> notFound("Shop"));
    }

    private Loot loot(UUID id) {
        return lootRepository.findById(id).orElseThrow(() -> notFound("Loot"));
    }

    private Item item(UUID id) {
        return itemRepository.findById(id).orElseThrow(() -> notFound("Item"));
    }

    private Campaign campaign(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> notFound("Campaign"));
    }

    private static <T> T valid(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
