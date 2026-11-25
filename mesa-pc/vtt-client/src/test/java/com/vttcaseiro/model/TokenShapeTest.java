package com.vttcaseiro.model;

import com.vttcaseiro.net.NetCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Formato do token: guardado, copiado pela rede e com contorno certo. */
class TokenShapeTest {

    @Test
    void shapeTravelsAndDefaultsToSquare() {
        TokenModel t = new TokenModel("Lyra", null);
        assertEquals(TokenModel.SQUARE, t.getShape(), "sem escolher: quadrado (tokens antigos)");
        t.setShape("hex");
        TokenModel copy = NetCodec.deepCopy(t, TokenModel.class);
        assertEquals(TokenModel.HEX, copy.getShape(), "vai junto pela rede / no arquivo da cena");
        TokenModel other = new TokenModel("Lyra", null);
        other.copyStateFrom(t);
        assertEquals(TokenModel.HEX, other.getShape(), "update_token leva o formato");
        t.setShape("estrela");
        assertEquals(TokenModel.SQUARE, t.getShape(), "formato desconhecido vira quadrado");
        assertEquals(12, TokenModel.outline(TokenModel.HEX, 70, 70).length, "6 pontas");
        assertEquals(16, TokenModel.outline(TokenModel.OCTAGON, 70, 70).length, "8 pontas");
        assertNull(TokenModel.outline(TokenModel.CIRCLE, 70, 70));
    }

    @Test
    void shapesWeatherAndLayerTravel() {
        Drawing arrow = Drawing.shape(Drawing.ARROW, 0, 0, 100, 0, "#E2483F", 3);
        Drawing back = NetCodec.deepCopy(arrow, Drawing.class);
        assertEquals(Drawing.ARROW, back.getKind(), "a forma vai pela rede");
        assertEquals(0, back.distanceTo(50, 0), 0.01);
        Drawing circle = Drawing.shape(Drawing.CIRCLE, 0, 0, 30, 40, "#E2483F", 3);
        assertEquals(0, circle.distanceTo(50, 0), 0.01, "borracha pega na borda do circulo (raio 50)");
        Drawing rect = Drawing.shape(Drawing.RECT, 0, 0, 100, 50, "#E2483F", 3);
        assertEquals(0, rect.distanceTo(100, 25), 0.01);
        assertEquals(25, rect.distanceTo(50, 25), 0.01, "no meio fica longe das bordas");

        SceneModel s = new SceneModel();
        s.setWeather("neve");
        assertEquals("neve", NetCodec.deepCopy(s, SceneModel.class).getWeather(), "clima vai com a cena");
        s.setWeather("granizo");
        assertEquals("", s.getWeather(), "clima desconhecido: nenhum");

        TokenModel a = new TokenModel("A", null);
        a.setLayer(7);
        TokenModel b = new TokenModel("B", null);
        b.copyStateFrom(a);
        assertEquals(7, b.getLayer(), "quem fica por cima vai pela rede");
        String old = a.getId();
        a.regenerateId();
        assertNotEquals(old, a.getId());
    }

    @Test
    void sizesGoFromTinyToCustom() {
        TokenModel t = new TokenModel("Rato", null);
        assertEquals(1.0, t.getWidthCells());
        t.setWidthCells(0.5);
        assertEquals(0.5, t.getWidthCells(), "Miudo: meia casa");
        t.setWidthCells(0.2);
        assertEquals(0.5, t.getWidthCells(), "menor que Miudo nao existe");
        t.setWidthCells(2.4);
        assertEquals(2.5, t.getWidthCells(), "de meia em meia casa");
        t.setHeightCells(99);
        assertEquals(TokenModel.MAX_CELLS, t.getHeightCells());
        // cena antiga (inteiro no arquivo) continua abrindo
        TokenModel old = com.google.gson.JsonParser.parseString("{\"name\":\"Orc\",\"widthCells\":2,\"heightCells\":2}")
                .isJsonObject() ? new com.google.gson.Gson().fromJson("{\"name\":\"Orc\",\"widthCells\":2,\"heightCells\":2}", TokenModel.class) : null;
        assertEquals(2.0, old.getWidthCells());
        TokenModel copy = new TokenModel("x", null);
        t.setWidthCells(2);
        t.setHeightCells(3);
        copy.copyStateFrom(t);
        assertEquals(2.0, copy.getWidthCells());
        assertEquals(3.0, copy.getHeightCells(), "retangular viaja pela rede");

        assertEquals(0.5, com.vttcaseiro.service.TokenLink.cellsForSize("Miúdo"));
        assertEquals(0.5, com.vttcaseiro.service.TokenLink.cellsForSize("Tiny"));
        assertEquals(1.0, com.vttcaseiro.service.TokenLink.cellsForSize("Médio"));
        assertEquals(2.0, com.vttcaseiro.service.TokenLink.cellsForSize("Large"));

        GridSettings g = new GridSettings();
        g.setCellSize(70);
        double[] tiny = g.snapTopLeft(52, 30, 0.5);
        assertEquals(35.0, tiny[0], 0.01, "Miudo encaixa de meia em meia casa");
        assertEquals(35.0, tiny[1], 0.01);
        assertEquals(70.0, g.snapTopLeft(52, 30, 1)[0], 0.01, "os outros, na casa inteira");
    }
}
