package top.customizr.system.dto;

import top.customizr.system.model.Wall;

import java.util.UUID;

/** Parede/porta da cena. Os jogadores tambem recebem: a tela calcula a linha de visao com elas. */
public record WallResponse(UUID id, double x1, double y1, double x2, double y2, Wall.Type type, boolean open, boolean locked) {

    public static WallResponse from(Wall w) {
        return new WallResponse(w.getId(), w.getX1(), w.getY1(), w.getX2(), w.getY2(), w.getType(), w.isOpen(), w.isLocked());
    }
}
