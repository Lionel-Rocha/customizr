package com.vttcaseiro.service;

import com.vttcaseiro.model.MapZone;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.List;

/** Contas das zonas do mapa (o servidor e o programa do mestre usam as mesmas). */
public final class ZoneRules {
    private ZoneRules() {
    }

    /** Centro do token (canto + metade do tamanho). */
    public static double[] center(SceneModel scene, TokenModel t, double x, double y) {
        double cell = Math.max(10, scene.getGrid().getCellSize());
        return new double[]{x + cell * t.getWidthCells() / 2.0, y + cell * t.getHeightCells() / 2.0};
    }

    /** Zonas em que o token ENTROU indo de (oldX, oldY) pra posicao atual (as que disparam ao entrar). */
    public static List<MapZone> entered(SceneModel scene, TokenModel t, double oldX, double oldY) {
        List<MapZone> out = new ArrayList<>();
        double[] a = center(scene, t, oldX, oldY);
        double[] b = center(scene, t, t.getX(), t.getY());
        for (MapZone z : scene.getZones()) {
            if (z.triggersOnEnter() && !z.contains(a[0], a[1]) && z.contains(b[0], b[1])) out.add(z);
        }
        return out;
    }

    /** Zonas de dano onde o token esta agora (pro comeco do turno). */
    public static List<MapZone> damageAt(SceneModel scene, TokenModel t) {
        List<MapZone> out = new ArrayList<>();
        double[] c = center(scene, t, t.getX(), t.getY());
        for (MapZone z : scene.getZones()) {
            if (MapZone.DAMAGE.equals(z.getKind()) && !z.getDamage().isBlank() && z.contains(c[0], c[1])) out.add(z);
        }
        return out;
    }

    /**
     * Casas a mais por terreno dificil numa linha reta de {@code cells} casas: cada casa (amostrada no centro dela,
     * sem contar a de saida) que cai numa zona de terreno conta mais uma.
     */
    public static long terrainExtra(SceneModel scene, double x1, double y1, double x2, double y2, long cells) {
        if (cells <= 0) return 0;
        List<MapZone> terrain = new ArrayList<>();
        for (MapZone z : scene.getZones()) if (MapZone.TERRAIN.equals(z.getKind())) terrain.add(z);
        if (terrain.isEmpty()) return 0;
        long extra = 0;
        for (long i = 1; i <= cells; i++) {
            double f = (double) i / cells;
            double px = x1 + (x2 - x1) * f;
            double py = y1 + (y2 - y1) * f;
            for (MapZone z : terrain) {
                if (z.contains(px, py)) {
                    extra++;
                    break;
                }
            }
        }
        return extra;
    }

    /** Onde o token chega na cena de destino: no portal que volta pra cena de origem, senao no primeiro portal. */
    public static double[] arrivalPoint(SceneModel target, String fromScene, TokenModel t) {
        MapZone back = null;
        MapZone any = null;
        for (MapZone z : target.getZones()) {
            if (!MapZone.PORTAL.equals(z.getKind())) continue;
            if (any == null) any = z;
            if (fromScene != null && fromScene.equals(z.getTargetScene())) back = z;
        }
        MapZone at = back != null ? back : any;
        if (at == null) return null;
        double cell = Math.max(10, target.getGrid().getCellSize());
        // um pouco fora do portal (senao chega e ja dispara de volta): logo abaixo dele
        double x = at.getX() + at.getWidth() / 2 - cell * t.getWidthCells() / 2.0;
        double y = at.getY() + at.getHeight() + 2;
        return new double[]{x, y};
    }
}
