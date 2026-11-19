package com.vttcaseiro.vision;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.List;

/**
 * "O que estes tokens enxergam?" -- a mesma regra da tela, sem JavaFX, pra o SERVIDOR decidir quais tokens mandar
 * pra cada jogador (e o cliente web fazer igual), e pra neblina que a visao revela. Paredes bloqueiam; a luz segue
 * as regras do 5.5e ({@link Perception}): luz de fundo da cena, luz plena e penumbra de cada fonte, visao no escuro,
 * as cegas e verdadeira; sentido sismico sente quem esta no chao; e um token que carrega luz e visto de longe no
 * escuro se estiver na linha de visao (a tocha aparece na escuridao).
 */
public final class VisionCheck {

    private record Eye(TokenModel token, double cx, double cy, double[] poly) {
    }

    /** Fonte de luz (token que carrega luz ou luz fixa do mapa). */
    private record Lamp(double cx, double cy, double[] poly, int brightFeet, int dimFeet) {
    }

    private final Perception.Light ambient;
    private final List<Eye> viewers = new ArrayList<>();
    private final List<Lamp> lights = new ArrayList<>();
    private final double cell;
    private final double feetPerCell;

    private VisionCheck(Perception.Light ambient, double cell, double feetPerCell) {
        this.ambient = ambient;
        this.cell = cell;
        this.feetPerCell = feetPerCell <= 0 ? 5 : feetPerCell;
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
        VisionCheck vc = new VisionCheck(Perception.ambient(scene), cellSize, scene.getGrid().getFeetPerCell());
        double[] b = bounds(scene);
        for (TokenModel t : viewerTokens) {
            double[] c = vc.center(t);
            vc.viewers.add(new Eye(t, c[0], c[1], Visibility.polygon(c[0], c[1], scene.getWalls(), b[0], b[1])));
        }
        if (vc.ambient != Perception.Light.BRIGHT) {
            for (TokenModel t : scene.getTokens()) {
                if (t.lightReachFeet() <= 0) continue;
                double[] c = vc.center(t);
                vc.lights.add(new Lamp(c[0], c[1], Visibility.polygon(c[0], c[1], scene.getWalls(), b[0], b[1]),
                        t.getLightFeet(), t.getDimLightFeet()));
            }
            // luzes fixas do mapa (tocha na parede, fogueira) acesas
            for (var l : scene.getLights()) {
                if (!l.isOn() || l.reachFeet() <= 0) continue;
                vc.lights.add(new Lamp(l.getX(), l.getY(), Visibility.polygon(l.getX(), l.getY(), scene.getWalls(), b[0], b[1]),
                        l.getBrightFeet(), l.getDimFeet()));
            }
        }
        return vc;
    }

    public double[] center(TokenModel t) {
        return new double[]{t.getX() + cell * t.getWidthCells() / 2.0, t.getY() + cell * t.getHeightCells() / 2.0};
    }

    private double feet(double px) {
        return px / cell * feetPerCell;
    }

    /** Luz que chega no ponto (fundo da cena + fontes com linha de visao ate ele). */
    public Perception.Light lightAt(double x, double y) {
        Perception.Light light = ambient;
        if (light == Perception.Light.BRIGHT) return light;
        for (Lamp l : lights) {
            if (!Visibility.inside(l.poly(), x, y)) continue;
            light = Perception.max(light, Perception.fromSource(l.brightFeet(), l.dimFeet(), feet(Math.hypot(x - l.cx(), y - l.cy()))));
            if (light == Perception.Light.BRIGHT) break;
        }
        return light;
    }

    /** Como o ponto do chao e visto por estes tokens (o melhor entre eles). */
    public Perception.Light seenAt(double x, double y) {
        Perception.Light best = Perception.Light.DARK;
        Perception.Light light = null;
        for (Eye v : viewers) {
            if (!Visibility.inside(v.poly(), x, y)) continue;
            if (light == null) light = lightAt(x, y);
            double d = feet(Math.hypot(x - v.cx(), y - v.cy()));
            best = Perception.max(best, Perception.seen(light, d, v.token()));
            if (best == Perception.Light.BRIGHT) break;
        }
        return best;
    }

    /** O ponto e enxergado por algum dos tokens (luz plena ou penumbra)? */
    public boolean canSee(double x, double y) {
        return seenAt(x, y) != Perception.Light.DARK;
    }

    /**
     * O token aparece pra quem tem estes olhos? Conta a altura nas distancias (voando 30 pes acima), o sentido
     * sismico (no chao, atraves de parede) e a luz propria vista de longe no escuro.
     */
    public boolean canSee(TokenModel t) {
        double[] c = center(t);
        Perception.Light light = null;
        for (Eye v : viewers) {
            if (v.token() == t) return true;
            double horizontal = feet(Math.hypot(c[0] - v.cx(), c[1] - v.cy()));
            if (Perception.tremorsenses(v.token(), t, horizontal)) return true;
            if (!Visibility.inside(v.poly(), c[0], c[1])) continue;
            if (ambient != Perception.Light.BRIGHT && t.lightReachFeet() > 0) return true;   // a tocha na escuridao
            if (light == null) light = lightAt(c[0], c[1]);
            if (Perception.seen(light, Perception.distanceFeet(horizontal, v.token(), t), v.token()) != Perception.Light.DARK) {
                return true;
            }
        }
        return false;
    }
}
