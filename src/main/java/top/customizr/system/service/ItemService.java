package top.customizr.system.service;

import tools.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ImportItemsResponse;
import top.customizr.system.dto.UpdateItemsFromDumpResponse;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemCategory;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Source;
import top.customizr.system.repository.InventoryEntryRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.SourceRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ItemService {

    private static final Set<String> IMPORTABLE_SOURCES = Set.of(
            "XPHB", "EFA", "RHW", "XDMG", "FRAIF", "AU"
    );

    private final ItemRepository itemRepository;
    private final InventoryEntryRepository inventoryEntryRepository;
    private final SourceRepository sourceRepository;
    private final TransactionTemplate itemTransactions;

    public ItemService(
            ItemRepository itemRepository,
            InventoryEntryRepository inventoryEntryRepository,
            SourceRepository sourceRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.itemRepository = itemRepository;
        this.inventoryEntryRepository = inventoryEntryRepository;
        this.sourceRepository = sourceRepository;
        this.itemTransactions = new TransactionTemplate(transactionManager);
        this.itemTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public Item create(
            String name,
            UUID sourceId,
            String type,
            String category,
            String referenceLink,
            String rarity,
            float priceGp
    ) {
        Source source = findSource(sourceId);
        ItemType insertType = parseEnum(ItemType.class, type, "type", true);
        ItemCategory insertCategory = parseEnum(ItemCategory.class, category, "category", false);
        Rarity insertRarity = parseEnum(Rarity.class, rarity, "rarity", true);
        BigDecimal price = BigDecimal.valueOf(priceGp);

        return itemRepository.save(createItem(name, source, insertType, insertCategory, referenceLink, insertRarity, price));
    }

    public ImportItemsResponse importFromDump(JsonNode payload) {
        JsonNode entries = payload == null ? null : payload.get("item");
        if (entries == null || !entries.isArray()) {
            throw badRequest("Expected a JSON object with an item array");
        }

        List<ImportItemsResponse.ImportedItem> imported = new ArrayList<>();
        List<ImportItemsResponse.SkippedItem> skipped = new ArrayList<>();

        for (JsonNode entry : entries) {
            String name = text(entry, "name");
            String sourceCode = text(entry, "source");
            String normalizedSource = sourceCode == null ? null : sourceCode.trim().toUpperCase(Locale.ROOT);
            String normalizedName = name == null ? null : name.trim();

            String reason = validateImportEntry(entry, normalizedName, normalizedSource);
            if (reason != null) {
                skipped.add(new ImportItemsResponse.SkippedItem(normalizedName, normalizedSource, reason));
                continue;
            }

            ImportAttempt attempt;
            try {
                attempt = itemTransactions.execute(status ->
                        importOne(entry, normalizedName, normalizedSource)
                );
            } catch (DataIntegrityViolationException exception) {
                if (!alreadyImported(normalizedName, normalizedSource)) {
                    throw exception;
                }
                attempt = ImportAttempt.skipped("Already imported");
            }

            if (attempt.skipReason() != null) {
                skipped.add(new ImportItemsResponse.SkippedItem(
                        normalizedName, normalizedSource, attempt.skipReason()
                ));
            } else {
                imported.add(attempt.imported());
            }
        }

        return new ImportItemsResponse(imported, skipped);
    }

    public UpdateItemsFromDumpResponse updateFromDump(JsonNode payload) {
        JsonNode entries = payload == null ? null : payload.get("item");
        if (entries == null || !entries.isArray()) {
            throw badRequest("Expected a JSON object with an item array");
        }

        List<UpdateItemsFromDumpResponse.ProcessedItem> created = new ArrayList<>();
        List<UpdateItemsFromDumpResponse.ProcessedItem> updated = new ArrayList<>();
        List<UpdateItemsFromDumpResponse.SkippedItem> skipped = new ArrayList<>();

        for (JsonNode entry : entries) {
            String name = text(entry, "name");
            String sourceCode = text(entry, "source");
            String normalizedSource = sourceCode == null ? null : sourceCode.trim().toUpperCase(Locale.ROOT);
            String normalizedName = name == null ? null : name.trim();

            String reason = validateImportEntry(entry, normalizedName, normalizedSource);
            if (reason != null) {
                skipped.add(new UpdateItemsFromDumpResponse.SkippedItem(normalizedName, normalizedSource, reason));
                continue;
            }

            UpdateAttempt attempt = itemTransactions.execute(status ->
                    updateOne(entry, normalizedName, normalizedSource)
            );
            if (attempt.skipReason() != null) {
                skipped.add(new UpdateItemsFromDumpResponse.SkippedItem(
                        normalizedName, normalizedSource, attempt.skipReason()
                ));
            } else if (attempt.created()) {
                created.add(attempt.item());
            } else {
                updated.add(attempt.item());
            }
        }

        return new UpdateItemsFromDumpResponse(created, updated, skipped);
    }

    private UpdateAttempt updateOne(JsonNode entry, String name, String sourceCode) {
        Source source = sourceRepository.getSourceByCode(sourceCode).orElse(null);
        if (source == null) {
            return UpdateAttempt.skipped("Source is not configured in the database");
        }

        ItemType type = isMagical(entry) ? ItemType.MAGICAL : ItemType.MUNDANE;
        ItemCategory category = category(entry);
        Rarity rarity = parseRarity(entry);
        String referenceLink = firstText(entry, "referenceLink", "link");
        BigDecimal price = parsePrice(entry);
        Item item = itemRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
        boolean created = item == null;
        if (created) {
            item = createItem(name, source, type, category, referenceLink, rarity, price);
        } else {
            item.update(name, source, type, category,
                    referenceLink != null ? referenceLink : item.getReferenceLink(),
                    rarity,
                    price != null ? price : item.getPriceGp());
        }


        Item saved = itemRepository.save(item);
        return UpdateAttempt.processed(new UpdateItemsFromDumpResponse.ProcessedItem(
                saved.getId(), saved.getName(), sourceCode
        ), created);
    }

    private record UpdateAttempt(
            UpdateItemsFromDumpResponse.ProcessedItem item,
            boolean created,
            String skipReason
    ) {
        private static UpdateAttempt processed(
                UpdateItemsFromDumpResponse.ProcessedItem item,
                boolean created
        ) {
            return new UpdateAttempt(item, created, null);
        }

        private static UpdateAttempt skipped(String reason) {
            return new UpdateAttempt(null, false, reason);
        }
    }

    private String firstText(JsonNode entry, String... fields) {
        for (String f : fields) {
            String t = text(entry, f);
            if (t != null && !t.isBlank()) return t.trim();
        }
        return null;
    }

    private BigDecimal parsePrice(JsonNode entry) {
        JsonNode pg = entry.get("priceGp");            // calculado pelo script (gp)
        if (pg != null && pg.isNumber()) return new BigDecimal(pg.asText());
        JsonNode v = entry.get("value");               // fallback: cobre ÷ 100
        if (v != null && v.isNumber()) return new BigDecimal(v.asText()).movePointLeft(2);
        return null;
    }


    private ImportAttempt importOne(JsonNode entry, String name, String sourceCode) {
        Source source = sourceRepository.getSourceByCode(sourceCode).orElse(null);
        if (source == null) {
            return ImportAttempt.skipped("Source is not configured in the database");
        }
        if (itemRepository.existsByNameAndSource_Id(name, source.getId())) {
            return ImportAttempt.skipped("Already imported");
        }

        String referenceLink = firstText(entry, "referenceLink", "link");
        BigDecimal price = parsePrice(entry);
        Item item = createItem(name, source,
                isMagical(entry) ? ItemType.MAGICAL : ItemType.MUNDANE,
                category(entry), referenceLink, parseRarity(entry), price);
        Item saved = itemRepository.save(item);
        return ImportAttempt.imported(new ImportItemsResponse.ImportedItem(
                saved.getId(), saved.getName(), sourceCode
        ));
    }

    private boolean alreadyImported(String name, String sourceCode) {
        Source source = sourceRepository.getSourceByCode(sourceCode).orElse(null);
        return source != null && itemRepository.existsByNameAndSource_Id(name, source.getId());
    }

    private record ImportAttempt(
            ImportItemsResponse.ImportedItem imported,
            String skipReason
    ) {
        private static ImportAttempt imported(ImportItemsResponse.ImportedItem item) {
            return new ImportAttempt(item, null);
        }

        private static ImportAttempt skipped(String reason) {
            return new ImportAttempt(null, reason);
        }
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
    public List<Item> listAll() {
        return itemRepository.findAll();
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

    private <T extends Enum<T>> T parseEnum(
            Class<T> enumType,
            String value,
            String field,
            boolean required
    ) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw badRequest("Item " + field + " is required");
            }
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid item " + field);
        }
    }

    private String validateImportEntry(JsonNode entry, String name, String sourceCode) {
        if (entry == null || !entry.isObject()) {
            return "Item entry must be an object";
        }
        if (name == null || name.isBlank()) {
            return "Item name is required";
        }
        if (name.length() > 255) {
            return "Item name exceeds 255 characters";
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            return "Item source is required";
        }
        if (!IMPORTABLE_SOURCES.contains(sourceCode)) {
            return "Source is not enabled for import";
        }
        if (!hasValidRarity(entry)) {
            return "Item rarity is unsupported";
        }
        return null;
    }

    private boolean hasValidRarity(JsonNode entry) {
        String rarity = text(entry, "rarity");
        if (rarity == null || rarity.isBlank()) {
            return true;
        }
        try {
            parseRarity(entry);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Rarity parseRarity(JsonNode entry) {
        String rarity = text(entry, "rarity");
        if (rarity == null || rarity.isBlank()) {
            return Rarity.COMMON;
        }
        String normalized = rarity.replaceAll("[^A-Za-z_]", "").toUpperCase(Locale.ROOT);
        return Rarity.valueOf(normalized);
    }

    private boolean isMagical(JsonNode entry) {
        String type = text(entry, "type");
        return entry.path("wondrous").asBoolean(false)
                || (type != null && type.contains("MAGIC"))   // ex.: MAGIC_VARIANT
                || hasValue(entry, "bonusWeapon")
                || hasValue(entry, "bonusAc")
                || hasValue(entry, "bonusSpellAttack")
                || hasValue(entry, "bonusSpellSaveDc");
    }


    private ItemCategory category(JsonNode entry) {
        String code = text(entry, "type");
        if (code != null) {
            code = code.split("\\|", 2)[0].trim().toUpperCase(Locale.ROOT);
        }
        if (entry.has("dmg1") || "M".equals(code) || "R".equals(code)) {
            return ItemCategory.WEAPON;
        }
        if (entry.has("ac") || (code != null && Set.of("A", "HA", "MA", "LA", "S", "SH").contains(code))) {
            return ItemCategory.ARMOR;
        }
        if ("P".equals(code)) {
            return ItemCategory.CONSUMABLE;
        }
        if ("AF".equals(code) || "EXP".equals(code)) {
            return ItemCategory.AMMUNITION_EXPLOSIVES;
        }
        if ("INS".equals(code)) {
            return ItemCategory.TOOLS;
        }
        if (code != null && Set.of("G", "SCF", "WD", "RD").contains(code)) {
            return ItemCategory.ADVENTURING_GEAR;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private boolean hasValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull();
    }
}
