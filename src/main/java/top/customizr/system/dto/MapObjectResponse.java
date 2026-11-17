package top.customizr.system.dto;

import top.customizr.system.model.MapObject;
import top.customizr.system.model.TokenLayer;

import java.util.UUID;

public record MapObjectResponse(UUID id, String name, UUID imageId, String imageUrl, double widthCells, double heightCells,
                                TokenLayer layer) {

    public static MapObjectResponse from(MapObject o) {
        return new MapObjectResponse(o.getId(), o.getName(), o.getImage().getId(), o.getImage().getUrl(), o.getWidthCells(),
                o.getHeightCells(), o.getLayer());
    }
}
