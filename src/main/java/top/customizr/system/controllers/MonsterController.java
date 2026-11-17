package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import top.customizr.system.dto.MonsterCopyRequest;
import top.customizr.system.dto.MonsterRequest;
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

    /** {@code ?name=goblin&minCr=0.25&maxCr=2&campaignId=...} (com campaignId vem tambem os customizados da campanha) */
    @GetMapping
    public List<MonsterResponse.Summary> search(@RequestParam(required = false) String name,
                                                @RequestParam(required = false) Double minCr,
                                                @RequestParam(required = false) Double maxCr,
                                                @RequestParam(required = false) UUID campaignId,
                                                @AuthenticationPrincipal UserDetails user) {
        return monsterService.search(name, minCr, maxCr, campaignId, user == null ? null : user.getUsername());
    }

    @GetMapping("/{id}")
    public MonsterResponse read(@PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        return monsterService.read(id, user == null ? null : user.getUsername());
    }

    /** Ficha de monstro customizada (em branco ou preenchida) da campanha. So o mestre. */
    @PostMapping("/custom")
    public ResponseEntity<MonsterResponse> createCustom(@RequestBody MonsterRequest request,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsterService.createCustom(request, user.getUsername()));
    }

    @PutMapping("/{id}")
    public MonsterResponse updateCustom(@PathVariable UUID id, @RequestBody MonsterRequest request,
                                        @AuthenticationPrincipal UserDetails user) {
        return monsterService.updateCustom(id, request, user.getUsername());
    }

    /** Copia um monstro pra campanha (pra editar a ficha sem mexer no catalogo). */
    @PostMapping("/{id}/copy")
    public ResponseEntity<MonsterResponse> copy(@PathVariable UUID id, @RequestBody MonsterCopyRequest request,
                                                @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsterService.copyToCampaign(id, request, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustom(@PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        monsterService.deleteCustom(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
