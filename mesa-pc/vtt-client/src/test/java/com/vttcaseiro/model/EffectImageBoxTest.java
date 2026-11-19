package com.vttcaseiro.model;

import com.vttcaseiro.view.MapView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Caixa da imagem do efeito de area: cobre a forma desenhada e gira junto com o cone e a linha. */
class EffectImageBoxTest {

    private static final double E = 1e-6;

    @Test
    void circleAndCube() {
        // circulo: centro em (100,100), raio 50 -> quadrado 100x100
        assertArrayEquals(new double[]{50, 50, 100, 100, 0}, MapView.effectBox("Circulo", 100, 100, 150, 100, 70), E);
        // cubo arrastado pra cima e pra esquerda: o canto fica na origem
        assertArrayEquals(new double[]{30, 40, 70, 70, 0}, MapView.effectBox("Cubo", 100, 110, 30, 60, 70), E);
    }

    @Test
    void coneAndLineFollowTheDirection() {
        // cone pra baixo, de (0,0) ate (0,140): quadrado 140, centro em (0,70), girado 90 graus
        double[] cone = MapView.effectBox("Cone", 0, 0, 0, 140, 70);
        assertEquals(140, cone[2], E);
        assertEquals(140, cone[3], E);
        assertEquals(0, cone[0] + cone[2] / 2, E, "centro x");
        assertEquals(70, cone[1] + cone[3] / 2, E, "centro y no meio do cone");
        assertEquals(90, cone[4], E);

        // girando o objeto em volta do centro, a borda esquerda (base da imagem) cai na origem do cone
        double cx = cone[0] + cone[2] / 2, cy = cone[1] + cone[3] / 2;
        double rad = Math.toRadians(cone[4]);
        double leftMidX = cx + Math.cos(rad) * (-cone[2] / 2), leftMidY = cy + Math.sin(rad) * (-cone[2] / 2);
        assertEquals(0, leftMidX, E);
        assertEquals(0, leftMidY, E);

        // linha na diagonal: comprimento x 1 casa
        double[] line = MapView.effectBox("Linha", 0, 0, 300, 400, 70);
        assertEquals(500, line[2], E);
        assertEquals(70, line[3], E);
        assertEquals(Math.toDegrees(Math.atan2(400, 300)), line[4], E);
    }

    @Test
    void theMapObjectKeepsTheBoxAndRotation() {
        double[] b = MapView.effectBox("Cone", 10, 10, 10, 150, 70);
        MapObject o = new MapObject("a".repeat(64) + ".gif", b[0], b[1], b[2], b[3]);
        o.setRotation(b[4]);
        o.sanitize();
        assertEquals(b[2], o.getWidth(), E);
        assertEquals(90, o.getRotation(), E);
        assertEquals("a".repeat(64) + ".gif", o.getAssetId(), "GIF aceito no objeto");
    }
}
