package top.customizr.system.controllers;

import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ImportItemsResponse;
import top.customizr.system.dto.ItemCreateRequest;
import top.customizr.system.dto.UpdateItemsFromDumpResponse;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemCategory;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.service.ItemService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/item")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportItemsResponse createFromDump(@RequestBody JsonNode payload) {
        return itemService.importFromDump(payload);
    }

    @PostMapping("/update-from-dump")
    public UpdateItemsFromDumpResponse updateFromDump(@RequestBody JsonNode payload) {
        return itemService.updateFromDump(payload);
    }

    @PostMapping("/create")
    public ResponseEntity<Item> create(@RequestBody ItemCreateRequest request) {
        Item item = itemService.create(
                request.name(),
                request.sourceId(),
                request.type(),
                request.category(),
                request.referenceLink(),
                request.rarity(),
                request.priceGp()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @GetMapping("/{itemId}")
    public Item read(@PathVariable UUID itemId) {
        return itemService.read(itemId);
    }

    @GetMapping("/all")
    public List<Item> readAll() {
        return itemService.listAll();
    }

    @GetMapping("/source/{sourceId}")
    public List<Item> readAllBySource(@PathVariable UUID sourceId) {
        return itemService.listBySource(sourceId);
    }

    @GetMapping("/search")
    public List<Item> search(@RequestParam String name) {
        return itemService.search(name);
    }

    @PutMapping("/update/{itemId}")
    public Item update(@PathVariable UUID itemId, @RequestBody ItemCreateRequest request) {
        return itemService.update(
                itemId,
                request.name(),
                request.sourceId(),
                parseEnum(ItemType.class, request.type(), "type", true),
                parseEnum(ItemCategory.class, request.category(), "category", false),
                request.referenceLink(),
                parseEnum(Rarity.class, request.rarity(), "rarity", true),
                BigDecimal.valueOf(request.priceGp())
        );
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable UUID itemId) {
        itemService.delete(itemId);
        return ResponseEntity.noContent().build();
    }

    private <T extends Enum<T>> T parseEnum(
            Class<T> enumType,
            String value,
            String field,
            boolean required
    ) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw badRequest(field + " is required");
            }
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Invalid " + field);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
