package com.vttcaseiro.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridAndObjectsTest {

    @Test
    void hexagonosEncaixamEContamPassos() {
        GridSettings g = new GridSettings();
        g.setType(GridSettings.HEX);
        assertTrue(g.isHex());
        for (int q = -3; q <= 3; q++) {
            for (int r = -3; r <= 3; r++) {
                double[] c = g.hexCenter(q, r);
                assertArrayEquals(new int[]{q, r}, g.hexAt(c[0], c[1]), "centro volta pro mesmo hexagono");
                assertArrayEquals(new int[]{q, r}, g.hexAt(c[0] + 10, c[1] - 8), "perto do centro tambem");
            }
        }
        assertEquals(3, GridSettings.hexDistance(new int[]{0, 0}, new int[]{3, 0}));
        assertEquals(3, GridSettings.hexDistance(new int[]{0, 0}, new int[]{-3, 3}));
        assertEquals(2, GridSettings.hexDistance(new int[]{0, 0}, new int[]{1, 1}));
        // token 1x1 solto perto do hexagono (2,1): o centro dele vai pro centro do hexagono
        double[] c = g.hexCenter(2, 1);
        double[] snapped = g.snapTopLeft(c[0] - 35 + 9, c[1] - 35 - 7, 1);
        assertEquals(c[0] - 35, snapped[0], 0.001);
        assertEquals(c[1] - 35, snapped[1], 0.001);
        double[] a = g.hexCenter(0, 0), b = g.hexCenter(4, -2);
        assertEquals(4, g.cellsBetween(a[0], a[1], b[0], b[1]));
    }

    @Test
    void quadradaESemGrade() {
        GridSettings g = new GridSettings();
        assertEquals(GridSettings.SQUARE, g.getType());
        assertArrayEquals(new double[]{140, 70}, g.snapTopLeft(150, 60, 1), 0.001);
        assertEquals(3, g.cellsBetween(0, 0, 210, 140), "diagonal conta 1");
        g.setType(GridSettings.NONE);
        assertTrue(g.isGridless());
        assertArrayEquals(new double[]{151, 63}, g.snapTopLeft(151, 63, 1), 0.001, "sem grade: fica onde soltou");
        assertEquals(5, g.cellsBetween(0, 0, 210, 280), "sem grade: linha reta (350 px = 5 casas)");
        g.setType(GridSettings.SQUARE);
        assertEquals(GridSettings.SQUARE, g.getType());
    }

    @Test
    void objetosNaCena() {
        SceneModel s = new SceneModel();
        String asset = "a".repeat(64) + ".png";
        MapObject table = new MapObject(asset, 100, 100, 140, 70);
        table.scaleBy(2);
        assertEquals(280, table.getWidth(), 0.001);
        assertEquals(100 + 70 - 140, table.getX(), 0.001, "aumenta mantendo o centro");
        table.setRotation(-90);
        assertEquals(270, table.getRotation());
        MapObject bad = new MapObject("../../etc/passwd", 0, 0, 1e9, -5);
        MapObject secret = new MapObject(asset, 0, 0, 70, 70);
        secret.setHidden(true);
        s.setObjects(List.of(table, bad, secret));
        assertEquals(2, s.getObjects().size(), "imagem com id estranho nao entra");
        assertEquals(1, s.visibleObjects(false).size());
        assertEquals(2, s.visibleObjects(true).size());
        MapObject copy = table.duplicate(35, 35);
        assertNotEquals(table.getId(), copy.getId());
        assertEquals(table.getX() + 35, copy.getX(), 0.001);
    }
}
