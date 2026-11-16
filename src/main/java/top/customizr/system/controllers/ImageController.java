package top.customizr.system.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.model.ImageAsset;
import top.customizr.system.service.ImageService;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/image")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /** multipart/form-data com {@code file} e {@code campaignId}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@RequestParam UUID campaignId, @RequestParam("file") MultipartFile file,
                                @AuthenticationPrincipal UserDetails loggedUser) throws IOException {
        return imageService.upload(campaignId, file.getBytes(), file.getOriginalFilename(), loggedUser.getUsername());
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> read(@PathVariable UUID imageId, @AuthenticationPrincipal UserDetails loggedUser) {
        ImageAsset image = imageService.read(imageId, loggedUser.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.getData());
    }
}
