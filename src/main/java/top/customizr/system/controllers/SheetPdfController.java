package top.customizr.system.controllers;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.service.SheetPdfService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Ficha em PDF pra baixar ou imprimir (dono do personagem ou mestre). */
@RestController
@RequestMapping("/character")
public class SheetPdfController {

    private final SheetPdfService pdfService;

    public SheetPdfController(SheetPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping(value = "/{id}/sheet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal UserDetails loggedUser) {
        byte[] pdf = pdfService.render(id, loggedUser.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdfService.fileName(id), StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }
}
