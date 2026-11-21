package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CatalogItemResponse;
import top.customizr.system.dto.CustomItemRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemCategory;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Source;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.InventoryEntryRepository;
import top.customizr.system.repository.ItemRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Itens dentro da campanha: busca no catalogo (filtrando oficial / nao oficial / do mestre) e os itens
 * proprios do mestre, que ficam na fonte homebrew da campanha, ao lado dos oficiais, sem misturar com o
 * catalogo do servidor (outra campanha nao ve) e sem serem tocados pela atualizacao do 5etools.
 */
@Service
public class CampaignItemService {

    private static final int SEARCH_LIMIT = 100;
    private static final Set<String> ORIGINS = Set.of("OFFICIAL", "UNOFFICIAL", "UNKNOWN", "CUSTOM");

    private final ItemRepository itemRepository;
    private final CampaignRepository campaignRepository;
    private final InventoryEntryRepository inventoryEntryRepository;
    private final MonsterService monsterService;
    private final ItemPriceService itemPrices;
    private final MasterGuard masterGuard;

    public CampaignItemService(ItemRepository itemRepository, CampaignRepository campaignRepository,
                               InventoryEntryRepository inventoryEntryRepository, MonsterService monsterService,
                               ItemPriceService itemPrices, MasterGuard masterGuard) {
        this.itemRepository = itemRepository;
        this.campaignRepository = campaignRepository;
        this.inventoryEntryRepository = inventoryEntryRepository;
        this.monsterService = monsterService;
        this.itemPrices = itemPrices;
        this.masterGuard = masterGuard;
    }

    /**
     * Busca pra quem esta na campanha: catalogo do servidor + itens do mestre desta campanha.
     * {@code origin}: vazio = todos, ou OFFICIAL, UNOFFICIAL, UNKNOWN, CUSTOM (pode juntar com virgula).
     */
    @Transactional
    public List<CatalogItemResponse> search(UUID campaignId, String name, String origin, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMember(username, campaign);
        Set<String> wanted = origins(origin);
        String q = name == null ? "" : name.trim();
        if (q.isEmpty() && !wanted.equals(Set.of("CUSTOM"))) throw badRequest("Falta o nome pra buscar o item");
        List<Item> found = q.isEmpty() ? itemRepository.findByCampaign(campaign) : itemRepository.findByNameContainingIgnoreCase(q);
        Map<UUID, BigDecimal> prices = itemPrices.pricesOf(campaign);
        String lower = q.toLowerCase(Locale.ROOT);
        return found.stream()
                .filter(i -> i.getCampaign() == null || i.getCampaign().getId().equals(campaign.getId()))
                .filter(i -> wanted.isEmpty() || wanted.contains(CatalogItemResponse.origin(i)))
                .sorted(Comparator.comparing((Item i) -> !i.getName().toLowerCase(Locale.ROOT).startsWith(lower))
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(SEARCH_LIMIT)
                .map(i -> CatalogItemResponse.from(i, i.getPriceGp() != null ? i.getPriceGp() : prices.get(i.getId())))
                .toList();
    }

    /** Itens proprios do mestre na campanha (todo membro ve: eles aparecem em loja e inventario). */
    @Transactional
    public List<CatalogItemResponse> custom(UUID campaignId, String username) {
        return search(campaignId, null, "CUSTOM", username);
    }

    @Transactional
    public CatalogItemResponse create(UUID campaignId, CustomItemRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (r == null || r.name() == null || r.name().isBlank()) throw badRequest("Falta o nome do item");
        Source source = monsterService.homebrewSource(campaign);
        String name = r.name().trim();
        requireFreeName(name, source, null);
        try {
            Item item = Item.custom(name, source, campaign, type(r.type(), ItemType.MUNDANE), category(r.category(), null),
                    blank(r.referenceLink()), rarity(r.rarity(), Rarity.COMMON), r.priceGp());
            return CatalogItemResponse.from(itemRepository.save(item), item.getPriceGp());
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    @Transactional
    public CatalogItemResponse update(UUID campaignId, UUID itemId, CustomItemRequest r, String username) {
        Item item = customOfMaster(campaignId, itemId, username);
        if (r == null) throw badRequest("Falta o corpo da requisição");
        String name = r.name() == null || r.name().isBlank() ? item.getName() : r.name().trim();
        if (!name.equals(item.getName())) requireFreeName(name, item.getSource(), item.getId());
        BigDecimal price = Boolean.TRUE.equals(r.clearPrice()) ? null : r.priceGp() != null ? r.priceGp() : item.getPriceGp();
        try {
            item.update(name, item.getSource(), type(r.type(), item.getType()), r.category() == null ? item.getCategory()
                            : category(r.category(), null), r.referenceLink() == null ? item.getReferenceLink() : blank(r.referenceLink()),
                    rarity(r.rarity(), item.getRarity()), price);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return CatalogItemResponse.from(item, item.getPriceGp());
    }

    /** Apaga o item do mestre; sai das lojas e espolios, mas nao se estiver no inventario de alguem. */
    @Transactional
    public void delete(UUID campaignId, UUID itemId, String username) {
        Item item = customOfMaster(campaignId, itemId, username);
        if (inventoryEntryRepository.existsByItem(item)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O item está em algum inventário");
        }
        itemRepository.delete(item);
    }

    private Item customOfMaster(UUID campaignId, UUID itemId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (itemId == null) throw badRequest("Falta o id do item");
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado"));
        if (item.getCampaign() == null || !item.getCampaign().getId().equals(campaign.getId())) {
            throw badRequest("Aqui só dá pra mudar os itens próprios desta campanha");
        }
        return item;
    }

    private void requireFreeName(String name, Source source, UUID self) {
        itemRepository.findByNameAndSource_Id(name, source.getId()).filter(other -> !other.getId().equals(self))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um item chamado " + name); });
    }

    private Set<String> origins(String origin) {
        if (origin == null || origin.isBlank() || origin.equalsIgnoreCase("ALL")) return Set.of();
        Set<String> set = new java.util.HashSet<>();
        for (String part : origin.split(",")) {
            String o = part.trim().toUpperCase(Locale.ROOT);
            if (!ORIGINS.contains(o)) throw badRequest("Origem desconhecida " + part + " (OFFICIAL, UNOFFICIAL, UNKNOWN, CUSTOM)");
            set.add(o);
        }
        return set;
    }

    private static ItemType type(String value, ItemType fallback) {
        return value == null || value.isBlank() ? fallback : parse(ItemType.class, value, "type");
    }

    private static ItemCategory category(String value, ItemCategory fallback) {
        return value == null || value.isBlank() ? fallback : parse(ItemCategory.class, value, "category");
    }

    private static Rarity rarity(String value, Rarity fallback) {
        return value == null || value.isBlank() ? fallback : parse(Rarity.class, value.replace(" ", "").replace("_", ""), "rarity");
    }

    private static <T extends Enum<T>> T parse(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw badRequest("Item inválido: " + field);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Campaign campaign(UUID id) {
        if (id == null) throw badRequest("Falta o id da campanha");
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campanha não encontrada"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
