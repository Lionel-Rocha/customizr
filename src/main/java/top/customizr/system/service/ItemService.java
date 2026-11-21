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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ItemService {

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
        List<JsonNode> entries = entries(payload);
        Map<String, BigDecimal> bases = baseValues(entries);

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
                        importOne(entry, normalizedName, normalizedSource, bases)
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
        List<JsonNode> entries = entries(payload);
        Map<String, BigDecimal> bases = baseValues(entries);

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
                    updateOne(entry, normalizedName, normalizedSource, bases)
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

    private UpdateAttempt updateOne(JsonNode entry, String name, String sourceCode, Map<String, BigDecimal> bases) {
        Source source = sourceRepository.getSourceByCode(sourceCode).orElse(null);
        if (source == null) {
            return UpdateAttempt.skipped("Source is not configured in the database");
        }

        ItemType type = isMagical(entry) ? ItemType.MAGICAL : ItemType.MUNDANE;
        ItemCategory category = category(entry);
        Rarity rarity = parseRarity(entry);
        String referenceLink = firstText(entry, "referenceLink", "link");
        BigDecimal price = price(entry, bases);
        Item item = itemRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
        boolean created = item == null;
        if (created) {
            item = createItem(name, source, type, category, referenceLink, rarity, price);
        } else {
            item.update(name, source, type, category,
                    referenceLink != null ? referenceLink : item.getReferenceLink(),
                    rarity,
                    // preco oficial do arquivo manda; o valor do DMG so preenche quem ainda nao tem preco
                    parsePrice(entry) != null || item.getPriceGp() == null ? price : item.getPriceGp());
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

    /**
     * Le "baseitem" (armas e armaduras comuns do 5etools) e "item", nessa ordem, pra o item comum ja existir
     * quando a arma magica feita dele for importada.
     */
    private List<JsonNode> entries(JsonNode payload) {
        List<JsonNode> list = new ArrayList<>();
        boolean found = false;
        for (String key : List.of("baseitem", "item")) {
            JsonNode array = payload == null ? null : payload.get(key);
            if (array != null && array.isArray()) {
                found = true;
                array.forEach(list::add);
            }
        }
        if (!found) {
            throw badRequest("Esperava um JSON com a lista \"item\" ou \"baseitem\"");
        }
        return list;
    }

    /** Preco dos itens do proprio arquivo, por "nome|fonte" em minusculas (o formato do campo baseItem). */
    private Map<String, BigDecimal> baseValues(List<JsonNode> entries) {
        Map<String, BigDecimal> values = new HashMap<>();
        for (JsonNode entry : entries) {
            String name = text(entry, "name");
            String source = text(entry, "source");
            BigDecimal value = parsePrice(entry);
            if (name != null && source != null && value != null) {
                values.put((name.trim() + "|" + source.trim()).toLowerCase(Locale.ROOT), value);
            }
        }
        return values;
    }

    /**
     * Preco oficial do arquivo; se nao houver e o item for magico, o valor da tabela do DMG 2024 pela raridade
     * (mais o preco da arma/armadura de base). Item comum sem preco fica sem: o mestre define na campanha.
     */
    private BigDecimal price(JsonNode entry, Map<String, BigDecimal> bases) {
        BigDecimal official = parsePrice(entry);
        if (official != null || !isMagical(entry) || unknownRarity(text(entry, "rarity"))) {
            return official;   // raridade desconhecida: o DMG nao da valor, o mestre define
        }
        return MagicItemPricing.price(parseRarity(entry), text(entry, "type"), text(entry, "name"), baseValue(entry, bases));
    }

    private BigDecimal baseValue(JsonNode entry, Map<String, BigDecimal> bases) {
        String ref = text(entry, "baseItem");
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String key = ref.trim().toLowerCase(Locale.ROOT);
        if (!key.contains("|")) {
            key = key + "|xphb";
        }
        BigDecimal fromFile = bases.get(key);
        if (fromFile != null) {
            return fromFile;
        }
        String[] parts = key.split("\\|", 2);
        return sourceRepository.getSourceByCode(parts[1].toUpperCase(Locale.ROOT))
                .flatMap(source -> itemRepository.findFirstByNameIgnoreCaseAndSource_Id(parts[0], source.getId()))
                .map(Item::getPriceGp)
                .orElse(null);
    }

    private BigDecimal parsePrice(JsonNode entry) {
        JsonNode pg = entry.get("priceGp");            // calculado pelo script (gp)
        if (pg != null && pg.isNumber()) return new BigDecimal(pg.asText());
        JsonNode v = entry.get("value");               // fallback: cobre ÷ 100
        if (v != null && v.isNumber()) return new BigDecimal(v.asText()).movePointLeft(2);
        return null;
    }


    private ImportAttempt importOne(JsonNode entry, String name, String sourceCode, Map<String, BigDecimal> bases) {
        Source source = sourceRepository.getSourceByCode(sourceCode).orElse(null);
        if (source == null) {
            return ImportAttempt.skipped("Source is not configured in the database");
        }
        if (itemRepository.existsByNameAndSource_Id(name, source.getId())) {
            return ImportAttempt.skipped("Already imported");
        }

        String referenceLink = firstText(entry, "referenceLink", "link");
        BigDecimal price = price(entry, bases);
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
            throw badRequest("Falta o nome pra buscar o item");
        }
        // item proprio de campanha nao aparece na busca geral (so na busca da campanha dele)
        return itemRepository.findByNameContainingIgnoreCase(name.trim()).stream()
                .filter(item -> item.getCampaign() == null).toList();
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O item está em algum inventário");
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
            throw badRequest("Falta o id do item");
        }
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado"));
    }

    private Source findSource(UUID sourceId) {
        if (sourceId == null) {
            throw badRequest("Falta o id da fonte");
        }
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte não encontrada"));
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
                throw badRequest("Item " + field + " é obrigatório");
            }
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Item inválido: " + field);
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
        // qualquer fonte cadastrada no banco entra (a sincronizacao com o 5etools cadastra os livros);
        // so a fonte homebrew de campanha fica de fora: item do mestre nasce pela tela dele
        if (sourceCode.startsWith("HB-")) {
            return "Campaign homebrew items are created by the master";
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
        if ("NONE".equals(normalized) || unknownRarity(rarity)) {
            return Rarity.COMMON;   // "none" = item comum; "unknown" = sem raridade definida (fica sem preco)
        }
        return Rarity.valueOf(normalized);
    }

    /** "unknown" e "unknown (magic)": o 5etools nao diz a raridade. */
    private static boolean unknownRarity(String rarity) {
        if (rarity == null) return false;
        String r = rarity.trim().toLowerCase(Locale.ROOT);
        return r.startsWith("unknown");   // "varies" (familias de itens) continua de fora, como no teste dela
    }

    private boolean isMagical(JsonNode entry) {
        String type = text(entry, "type");
        String rarity = text(entry, "rarity");
        String r = rarity == null ? "" : rarity.trim().toLowerCase(Locale.ROOT);
        boolean rare = !r.isEmpty() && !r.equals("none") && !r.equals("unknown");   // "unknown (magic)" e magico
        return rare
                || entry.path("wondrous").asBoolean(false)
                || (type != null && type.contains("MAGIC"))   // ex.: MAGIC_VARIANT
                || hasValue(entry, "bonusWeapon")
                || hasValue(entry, "bonusAc")
                || hasValue(entry, "bonusSpellAttack")
                || hasValue(entry, "bonusSpellSaveDc");
    }


    /**
     * Categoria do item nas tags do Customizr, a partir do codigo de tipo do 5etools ("M|XPHB" -> "M").
     * A ordem importa: veneno antes de equipamento, montaria antes de armadura (navio tem "ac"),
     * municao ("A") nao e armadura.
     */
    private ItemCategory category(JsonNode entry) {
        String code = text(entry, "type");
        if (code != null) {
            code = code.split("\\|", 2)[0].trim().toUpperCase(Locale.ROOT);
        }
        String name = text(entry, "name");
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (entry.path("poison").asBoolean(false)) {
            return ItemCategory.POISONS;
        }
        if (code != null && Set.of("MNT", "VEH", "SHP", "AIR", "SPC", "TAH").contains(code)) {
            return ItemCategory.MOUNTS_VEHICLES;      // montarias, veiculos e arreios
        }
        if (code != null && Set.of("A", "AF", "EXP").contains(code)) {
            return ItemCategory.AMMUNITION_EXPLOSIVES;
        }
        if (entry.has("dmg1") || "M".equals(code) || "R".equals(code)) {
            return ItemCategory.WEAPON;
        }
        if (code != null && Set.of("LA", "MA", "HA", "S", "SH").contains(code) || entry.has("ac") && code == null) {
            return ItemCategory.ARMOR;
        }
        if (code != null && Set.of("P", "SC", "FD").contains(code)) {
            return ItemCategory.CONSUMABLE;           // pocoes, pergaminhos, comida e bebida
        }
        if (code != null && Set.of("T", "AT", "INS", "GS").contains(code)) {
            return ItemCategory.TOOLS;                // ferramentas, de artesao, instrumentos, jogos
        }
        if (entry.path("trinket").asBoolean(false) || lowerName.contains("trinket")
                || code != null && Set.of("$A", "$G", "TB").contains(code)) {
            return ItemCategory.TRINKETS;             // bugigangas e tesouro: gemas, objetos de arte, barras
        }
        if (code != null && Set.of("G", "SCF", "TG", "OTH", "$C").contains(code)) {
            return ItemCategory.ADVENTURING_GEAR;     // equipamento, foco (mesmo magico), mercadorias
        }
        if (entry.path("wondrous").asBoolean(false) || code != null && Set.of("RG", "RD", "WD").contains(code)
                || code == null && isMagical(entry)) {
            return ItemCategory.WONDROUS_ITEM;        // maravilhosos e os outros magicos (anel, bastao, varinha)
        }
        return code == null ? ItemCategory.ADVENTURING_GEAR : null;
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
