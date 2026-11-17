package top.customizr.system.controllers;

import top.customizr.system.dto.BackgroundResponse;
import top.customizr.system.dto.FeatResponse;
import top.customizr.system.dto.SpeciesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.service.ContentImportService;

import java.util.List;

/** Especies, antecedentes e talentos (importados dos arquivos do 5etools). */
@RestController
@RequestMapping("/content")
public class ContentController {

    private final ContentImportService contentImportService;

    public ContentController(ContentImportService contentImportService) {
        this.contentImportService = contentImportService;
    }

    @PostMapping("/species/import")
    public ContentImportResponse importSpecies(@RequestBody JsonNode payload) {
        return contentImportService.importSpecies(payload);
    }

    @PostMapping("/backgrounds/import")
    public ContentImportResponse importBackgrounds(@RequestBody JsonNode payload) {
        return contentImportService.importBackgrounds(payload);
    }

    @PostMapping("/feats/import")
    public ContentImportResponse importFeats(@RequestBody JsonNode payload) {
        return contentImportService.importFeats(payload);
    }

    @GetMapping("/species")
    public List<SpeciesResponse> species(@RequestParam(required = false) String name) {
        return contentImportService.species(name);
    }

    @GetMapping("/backgrounds")
    public List<BackgroundResponse> backgrounds(@RequestParam(required = false) String name) {
        return contentImportService.backgrounds(name);
    }

    @GetMapping("/feats")
    public List<FeatResponse> feats(@RequestParam(required = false) String name) {
        return contentImportService.feats(name);
    }
}
