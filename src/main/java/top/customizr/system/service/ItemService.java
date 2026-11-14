package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemCategory;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Source;
import top.customizr.system.repository.InventoryEntryRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.SourceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final InventoryEntryRepository inventoryEntryRepository;
    private final SourceRepository sourceRepository;

    public ItemService(
            ItemRepository itemRepository,
            InventoryEntryRepository inventoryEntryRepository,
            SourceRepository sourceRepository
    ) {
        this.itemRepository = itemRepository;
        this.inventoryEntryRepository = inventoryEntryRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public Item create(
            String name,
            UUID sourceId,
            ItemType type,
            ItemCategory category,
            String referenceLink,
            Rarity rarity,
            BigDecimal priceGp
    ) {
        Source source = findSource(sourceId);
        return itemRepository.save(createItem(name, source, type, category, referenceLink, rarity, priceGp));
    }

    @Transactional
    public Item read(UUID itemId) {
        return findItem(itemId);
    }

    @Transactional
    public List<Item> listBySource(UUID sourceId) {
        findSource(sourceId);
        return itemRepository.findBySource_Id(sourceId);
    }

    @Transactional
    public List<Item> search(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("Item search name is required");
        }
        return itemRepository.findByNameContainingIgnoreCase(name.trim());
    }

    @Transactional
    public Item update(
            UUID itemId,
            String name,
            UUID sourceId,
            ItemType type,
            ItemCategory category,
            String referenceLink,
            Rarity rarity,
            BigDecimal priceGp
    ) {
        Item item = findItem(itemId);
        Source source = findSource(sourceId);
        try {
            item.update(name, source, type, category, referenceLink, rarity, priceGp);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return itemRepository.save(item);
    }

    @Transactional
    public void delete(UUID itemId) {
        Item item = findItem(itemId);
        if (inventoryEntryRepository.existsByItem(item)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is present in an inventory");
        }
        itemRepository.delete(item);
    }

    private Item createItem(
            String name,
            Source source,
            ItemType type,
            ItemCategory category,
            String referenceLink,
            Rarity rarity,
            BigDecimal priceGp
    ) {
        try {
            return new Item(name, source, type, category, referenceLink, rarity, priceGp);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private Item findItem(UUID itemId) {
        if (itemId == null) {
            throw badRequest("Item ID is required");
        }
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Source findSource(UUID sourceId) {
        if (sourceId == null) {
            throw badRequest("Source ID is required");
        }
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
