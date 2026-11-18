package top.customizr.system.dto;

import top.customizr.system.model.MapPin;

import java.util.UUID;

public record PinResponse(UUID id, double x, double y, String label, String icon, String note, UUID entryId,
                          boolean visibleToPlayers) {

    public static PinResponse from(MapPin p) {
        return new PinResponse(p.getId(), p.getX(), p.getY(), p.getLabel(), p.getIcon(), p.getNote(), p.getEntryId(),
                p.isVisibleToPlayers());
    }
}
