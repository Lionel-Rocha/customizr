package top.customizr.system.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.dto.MonsterResponse;
import top.customizr.system.service.MonsterService;

import java.util.List;
import java.util.UUID;

/** Catalogo de monstros (bestiario). */
@RestController
@RequestMapping("/monster")
public class MonsterController {

    private final MonsterService monsterService;

    public MonsterController(MonsterService monsterService) {
        this.monsterService = monsterService;
    }

    /** Corpo: um bestiary-*.json do 5etools. */
    @PostMapping("/import")
    public ContentImportResponse importBestiary(@RequestBody JsonNode payload) {
        return monsterService.importBestiary(payload);
    }

    /** {@code ?name=goblin&minCr=0.25&maxCr=2} */
    @GetMapping
    public List<MonsterResponse.Summary> search(@RequestParam(required = false) String name,
                                                @RequestParam(required = false) Double minCr,
                                                @RequestParam(required = false) Double maxCr) {
        return monsterService.search(name, minCr, maxCr);
    }

    @GetMapping("/{id}")
    public MonsterResponse read(@PathVariable UUID id) {
        return monsterService.read(id);
    }
}
