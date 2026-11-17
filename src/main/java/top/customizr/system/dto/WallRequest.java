package top.customizr.system.dto;

import top.customizr.system.model.Wall;

import java.util.List;

/** Varias paredes/portas de uma vez (o desenho de uma sala inteira). type: WALL (padrao) ou DOOR. */
public record WallRequest(List<Segment> walls) {

    public record Segment(double x1, double y1, double x2, double y2, Wall.Type type) {}
}
