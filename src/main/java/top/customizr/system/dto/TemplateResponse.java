package top.customizr.system.dto;

import top.customizr.system.model.AreaTemplate;

import java.util.UUID;

public record TemplateResponse(UUID id, AreaTemplate.Shape shape, double x, double y, double angle, int sizeFt, int widthFt,
                               String label, String color, UUID ownerId) {

    public static TemplateResponse from(AreaTemplate t) {
        return new TemplateResponse(t.getId(), t.getShape(), t.getX(), t.getY(), t.getAngle(), t.getSizeFt(), t.getWidthFt(),
                t.getLabel(), t.getColor(), t.getOwnerId());
    }
}
