package top.customizr.system;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemCategory;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.service.ItemService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class ItemImportTests {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @org.springframework.transaction.annotation.Transactional // getSource() e LAZY
    void importsOnlyEnabledSourcesAndPersistsSanitizedFields() throws Exception {
        String itemName = "Dump Import Test Tool";
        String payload = """
                {
                  "item": [
                    {
                      "name": "%s",
                      "source": "xphb",
                      "type": "SCF",
                      "rarity": "uncommon",
                      "wondrous": true,
                      "entries": ["Ignored source description"]
                    },
                    {"name": "%s", "source": "XPHB", "rarity": "uncommon"},
                    {"name": "Disabled Source Test Item", "source": "TCE", "rarity": "rare"},
                    {"name": "Unsupported Rarity Test Item", "source": "XPHB", "rarity": "varies"}
                  ]
                }
                """.formatted(itemName, itemName);

        var result = itemService.importFromDump(objectMapper.readTree(payload));

        assertEquals(1, result.imported().size());
        assertEquals(3, result.skipped().size());
        var saved = itemRepository.findById(result.imported().getFirst().id()).orElseThrow();
        assertEquals(itemName, saved.getName());
        assertEquals("XPHB", saved.getSource().getCode());
        assertEquals(ItemType.MAGICAL, saved.getType());
        assertEquals(ItemCategory.ADVENTURING_GEAR, saved.getCategory());
        assertEquals(Rarity.UNCOMMON, saved.getRarity());
        assertNull(saved.getReferenceLink());

        var repeated = itemService.importFromDump(objectMapper.readTree("""
                {"item":[{"name":"%s","source":"XPHB","rarity":"uncommon"}]}
                """.formatted(itemName)));
        assertEquals(0, repeated.imported().size());
        assertEquals("Already imported", repeated.skipped().getFirst().reason());
    }

    @Test
    void updatesExistingItemsAndCreatesMissingItemsFromDump() throws Exception {
        String itemName = "Dump Update Existing Test Item " + UUID.randomUUID();
        var source = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        var existingItem = itemRepository.save(new Item(
                itemName, source, ItemType.MUNDANE, null, "keep-me", Rarity.COMMON, BigDecimal.TEN
        ));

        String newItemName = "Dump Update New Test Item " + UUID.randomUUID();
        var result = itemService.updateFromDump(objectMapper.readTree("""
                {
                  "item": [
                    {"name":"%s","source":"XPHB","rarity":"rare","wondrous":true},
                    {"name":"%s","source":"XPHB","rarity":"uncommon"}
                  ]
                }
                """.formatted(itemName, newItemName)));

        assertEquals(1, result.updated().size());
        assertEquals(existingItem.getId(), result.updated().getFirst().id());
        assertEquals(1, result.created().size());
        var updatedItem = itemRepository.findById(existingItem.getId()).orElseThrow();
        assertEquals(ItemType.MAGICAL, updatedItem.getType());
        assertEquals(Rarity.RARE, updatedItem.getRarity());
        assertEquals("keep-me", updatedItem.getReferenceLink());
        assertEquals(0, BigDecimal.TEN.compareTo(updatedItem.getPriceGp()), "preço 10 (escala do banco pode ser 10.00)");
    }
}
