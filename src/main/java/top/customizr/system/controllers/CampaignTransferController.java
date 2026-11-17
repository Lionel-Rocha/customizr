package top.customizr.system.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.CampaignExport;
import top.customizr.system.dto.CampaignImportResponse;
import top.customizr.system.service.CampaignTransferService;

import java.util.UUID;

/** Backup da campanha: exporta tudo num JSON e importa como campanha nova. */
@RestController
@RequestMapping("/campaign")
public class CampaignTransferController {

    private final CampaignTransferService transferService;

    public CampaignTransferController(CampaignTransferService transferService) {
        this.transferService = transferService;
    }

    /** So o mestre. {@code ?images=false} deixa as imagens de fora (arquivo bem menor). */
    @GetMapping("/{id}/export")
    public ResponseEntity<CampaignExport> export(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "true") boolean images,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        CampaignExport data = transferService.export(id, images, loggedUser.getUsername());
        String file = data.name().replaceAll("[^A-Za-z0-9_-]+", "-") + ".customizr.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .body(data);
    }

    /** Cria uma campanha NOVA com o conteudo do arquivo; quem importa vira o mestre. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignImportResponse importCampaign(@RequestBody CampaignExport data,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return transferService.importCampaign(data, loggedUser.getUsername());
    }
}
