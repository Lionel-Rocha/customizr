package com.vttcaseiro.net;

import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetMessageTest {

    @Test
    void moveTokenPreenchePercursoCerto() {
        NetMessage m = NetMessage.moveToken("abc-123", 10.5, 20.0);
        assertEquals("move_token", m.type);
        assertEquals("abc-123", m.tokenId);
        assertEquals(10.5, m.x);
        assertEquals(20.0, m.y);
        assertNull(m.player, "move_token nao deveria preencher campos de outro tipo");
    }

    @Test
    void addTokenCarregaOTokenInteiro() {
        TokenModel token = new TokenModel("Goblin", "/img/goblin.png");
        NetMessage m = NetMessage.addToken(token);
        assertEquals("add_token", m.type);
        assertEquals(token, m.token);
        assertEquals("Goblin", m.token.getName());
    }

    @Test
    void rollResultPreencheTotalEDetalhe() {
        NetMessage m = NetMessage.rollResult("Fulano", "2d6[4,5] = 9", 9);
        assertEquals("roll_result", m.type);
        assertEquals("Fulano", m.player);
        assertEquals("2d6[4,5] = 9", m.detail);
        assertEquals(9, m.total);
    }

    @Test
    void rollAdvantagePreencheModoEModificador() {
        NetMessage m = NetMessage.rollAdvantage("Fulano", 5);
        assertEquals("roll", m.type);
        assertEquals("advantage", m.rollMode);
        assertEquals(5, m.modifier);
        assertNull(m.expression, "vantagem nao usa expression");
    }

    @Test
    void rollDisadvantagePreencheModoEModificador() {
        NetMessage m = NetMessage.rollDisadvantage("Fulano", -1);
        assertEquals("roll", m.type);
        assertEquals("disadvantage", m.rollMode);
        assertEquals(-1, m.modifier);
    }

    @Test
    void chatPreencheJogadorETexto() {
        NetMessage m = NetMessage.chat("Fulano", "oi pessoal");
        assertEquals("chat", m.type);
        assertEquals("Fulano", m.player);
        assertEquals("oi pessoal", m.text);
    }
}
