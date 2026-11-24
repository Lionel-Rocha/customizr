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
}
