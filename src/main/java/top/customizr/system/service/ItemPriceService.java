package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ItemPriceResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignItemPrice;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemType;
import top.customizr.system.repository.CampaignItemPriceRepository;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.ItemRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Preco de item na campanha: o oficial do catalogo e, quando ele nao existe, o que o mestre definiu.
 * O catalogo e do servidor inteiro, entao o mestre nao mexe nele: o preco dele vale so na mesa dele.
 */
@Service
public class ItemPriceService {

    private static final int LIST_LIMIT = 300;

    private final CampaignItemPriceRepository priceRepository;
    private final CampaignRepository campaignRepository;
    private final ItemRepository itemRepository;
    private final MasterGuard masterGuard;

    public ItemPriceService(CampaignItemPriceRepository priceRepository, CampaignRepository campaignRepository,
                            ItemRepository itemRepository, MasterGuard masterGuard) {
        this.priceRepository = priceRepository;
        this.campaignRepository = campaignRepository;
        this.itemRepository = itemRepository;
        this.masterGuard = masterGuard;
    }

    /** Preco oficial, ou o da campanha, ou nulo. */
    public BigDecimal price(Campaign campaign, Item item) {
        if (item.getPriceGp() != null) return item.getPriceGp();
        return priceRepository.findByCampaignAndItem(campaign, item).map(CampaignItemPrice::getPriceGp).orElse(null);
    }

    /** Todos os precos do mestre na campanha (id do item -> po). */
    public Map<UUID, BigDecimal> pricesOf(Campaign campaign) {
        return priceRepository.findByCampaign(campaign).stream()
                .collect(Collectors.toMap(p -> p.getItem().getId(), CampaignItemPrice::getPriceGp));
    }

    /** Precos do mestre nesta campanha (todo membro ve, pra loja e inventario mostrarem o valor). */
    @Transactional
    public Map<UUID, BigDecimal> campaignPrices(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMember(username, campaign);
        return pricesOf(campaign);
    }

    /** Itens sem preco oficial (so os comuns, por padrao), com o preco que o mestre ja deu. So o mestre. */
    @Transactional
    public List<ItemPriceResponse> unpriced(UUID campaignId, String name, boolean includeMagic, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        Map<UUID, BigDecimal> mine = pricesOf(campaign);
        String q = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return itemRepository.findByPriceGpIsNull().stream()
                .filter(i -> i.getCampaign() == null)   // item do mestre ja tem o preco que ele quiser
                .filter(i -> includeMagic || i.getType() == ItemType.MUNDANE)
                .filter(i -> q.isEmpty() || i.getName().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(LIST_LIMIT)
                .map(i -> ItemPriceResponse.from(i, mine.get(i.getId())))
                .toList();
    }

    /** Mestre define (ou apaga, com nulo) o preco de um item que nao tem preco oficial. */
    @Transactional
    public ItemPriceResponse set(UUID campaignId, UUID itemId, BigDecimal priceGp, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (itemId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta o id do item");
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado"));
        if (item.getPriceGp() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, item.getName() + " já tem preço oficial");
        }
        var existing = priceRepository.findByCampaignAndItem(campaign, item);
        if (priceGp == null) {
            existing.ifPresent(priceRepository::delete);
            return ItemPriceResponse.from(item, null);
        }
        try {
            CampaignItemPrice price = existing.orElseGet(() -> new CampaignItemPrice(campaign, item, priceGp));
            price.change(priceGp);
            return ItemPriceResponse.from(item, priceRepository.save(price).getPriceGp());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private Campaign campaign(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta o id da campanha");
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campanha não encontrada"));
    }
}
