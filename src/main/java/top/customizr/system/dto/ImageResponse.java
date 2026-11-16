package top.customizr.system.dto;

import top.customizr.system.model.ImageAsset;

import java.util.UUID;

/** url: caminho pra usar em mapUrl / photoUrl (GET autenticado). */
public record ImageResponse(UUID id, String url, String contentType, int size, String fileName) {
    public static ImageResponse from(ImageAsset image) {
        return new ImageResponse(image.getId(), image.getUrl(), image.getContentType(), image.getSize(), image.getFileName());
    }
}
