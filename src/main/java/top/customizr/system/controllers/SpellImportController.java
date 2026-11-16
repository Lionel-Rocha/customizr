package top.customizr.system.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.service.SpellImportService;

import java.util.Map;

/** Importacao de magias do 5etools (o CRUD manual continua no SpellController). */
@RestController
@RequestMapping("/spell")
public class SpellImportController {

    private final SpellImportService spellImportService;

    public SpellImportController(SpellImportService spellImportService) {
        this.spellImportService = spellImportService;
    }

    /** Corpo: o arquivo de magias do 5etools ({"spell": [...]}). */
    @PostMapping("/import")
    public ContentImportResponse importSpells(@RequestBody JsonNode payload) {
        return spellImportService.importSpells(payload);
    }

    /** Corpo: gendata-spell-source-lookup.json do 5etools. Liga magias as classes ja importadas. */
    @PostMapping("/class-lists/import")
    public Map<String, Integer> importClassLists(@RequestBody JsonNode lookup) {
        return Map.of("linked", spellImportService.importClassLists(lookup));
    }
}
