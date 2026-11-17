package com.vttcaseiro.vision;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.List;

/**
 * "O que estes tokens enxergam?" -- a mesma regra da tela, sem JavaFX, pra
 * o SERVIDOR decidir quais tokens mandar pra cada jogador (e o cliente web
 * fazer igual). Paredes bloqueiam; cena escura limita ao raio de visao no
 * escuro / luz; luz de qualquer token ilumina o que o jogador ve; e um token
 * que carrega luz e visto de longe no escuro se estiver na linha de visao
 * (a tocha aparece na escuridao).
 */
public final class VisionCheck {

    private record Eye(double cx, double cy, double[] poly, double radius) {
    }

    private final boolean dark;
    private final List<Eye> viewers = new ArrayList<>();
    private final List<Eye> lights = new ArrayList<>();
    private final double cell;

    private VisionCheck(boolean dark, double cell) {
        this.dark = dark;
        this.cell = cell;
    }

    /** Tamanho do "mundo" pros raios: bem maior que qualquer mapa normal. */
    static double[] bounds(SceneModel scene) {
        double w = 20000;
        double h = 20000;
        for (var wall : scene.getWalls()) {
            w = Math.max(w, Math.max(wall.getX1(), wall.getX2()) + 1000);
            h = Math.max(h, Math.max(wall.getY1(), wall.getY2()) + 1000);
        }
        for (TokenModel t : scene.getTokens()) {
            w = Math.max(w, t.getX() + 1000);
            h = Math.max(h, t.getY() + 1000);
        }
        return new double[]{w, h};
    }

    public static VisionCheck of(SceneModel scene, List<TokenModel> viewerTokens) {
        double cellSize = Math.max(10, scene.getGrid().getCellSize());
        VisionCheck vc = new VisionCheck(scene.isDark(), cellSize);
        double[] b = bounds(scene);
        for (TokenModel t : viewerTokens) {
            double[] c = vc.center(t);
            double r = scene.isDark()
                    ? Visibility.feetToPx(Math.max(t.getVisionFeet(), t.getLightFeet()), cellSize, scene.getGrid().getFeetPerCell())
                    : Double.MAX_VALUE;
            vc.viewers.add(new Eye(c[0], c[1], Visibility.polygon(c[0], c[1], scene.getWalls(), b[0], b[1]), r));
        }
        if (scene.isDark()) {
            for (TokenModel t : scene.getTokens()) {
                if (t.getLightFeet() <= 0) continue;
                double[] c = vc.center(t);
                vc.lights.add(new Eye(c[0], c[1], Visibility.polygon(c[0], c[1], scene.getWalls(), b[0], b[1]),
                        Visibility.feetToPx(t.getLightFeet(), cellSize, scene.getGrid().getFeetPerCell())));
            }
        }
        return vc;
    }

    public double[] center(TokenModel t) {
        return new double[]{t.getX() + cell * t.getWidthCells() / 2.0, t.getY() + cell * t.getHeightCells() / 2.0};
    }

    /** O ponto e enxergado por algum dos tokens? */
    public boolean canSee(double x, double y) {
        for (Eye v : viewers) {
            if (!Visibility.inside(v.poly(), x, y)) continue;
            if (!dark || Math.hypot(x - v.cx(), y - v.cy()) <= v.radius()) return true;
            for (Eye l : lights) {
                if (Visibility.inside(l.poly(), x, y) && Math.hypot(x - l.cx(), y - l.cy()) <= l.radius()) return true;
            }
        }
        return false;
    }

    /** O token aparece pra quem tem estes olhos? (luz propria e vista de longe no escuro). */
    public boolean canSee(TokenModel t) {
        double[] c = center(t);
        if (canSee(c[0], c[1])) return true;
        if (dark && t.getLightFeet() > 0) {
            for (Eye v : viewers) if (Visibility.inside(v.poly(), c[0], c[1])) return true;
        }
        return false;
    }
}
