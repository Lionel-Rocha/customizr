package top.customizr.system.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.LevelUpPreviewResponse;
import top.customizr.system.dto.LevelUpRequest;
import top.customizr.system.dto.LevelUpResponse;
import top.customizr.system.service.LevelUpService;

import java.util.UUID;

/** Assistente de subir de nivel (dono do personagem ou mestre). */
@RestController
@RequestMapping("/character")
public class LevelUpController {

    private final LevelUpService levelUpService;

    public LevelUpController(LevelUpService levelUpService) {
        this.levelUpService = levelUpService;
    }

    /** O que muda no proximo nivel. {@code classId} vazio = a unica classe do personagem. */
    @GetMapping("/{id}/level-up")
    public LevelUpPreviewResponse preview(@PathVariable UUID id, @RequestParam(required = false) UUID classId,
                                          @AuthenticationPrincipal UserDetails loggedUser) {
        return levelUpService.preview(id, classId, loggedUser.getUsername());
    }

    /** Sobe o nivel: PV, subclasse, Aumento de Valor de Atributo ou talento, de uma vez. */
    @PostMapping("/{id}/level-up")
    public LevelUpResponse apply(@PathVariable UUID id, @RequestBody LevelUpRequest request,
                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return levelUpService.apply(id, request, loggedUser.getUsername());
    }
}
