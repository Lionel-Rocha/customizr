package com.vttcaseiro.net;

import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere que uma NetMessage sobrevive a uma volta completa de
 * codificar -> decodificar, e que o JSON gerado cabe numa linha so (o
 * protocolo e "uma mensagem por linha" -- se o Gson um dia mudar o
 * padrao e comecar a gerar quebra de linha dentro do JSON compacto,
 * esse teste pega isso).
 */
class NetCodecTest {

    @Test
    void moveTokenSobreviveAIdaEVolta() {
        NetMessage original = NetMessage.moveToken("tok-1", 12.0, 34.5);
        String json = NetCodec.encode(original);
        assertFalse(json.contains("\n"), "uma mensagem precisa caber numa linha so");

        NetMessage decoded = NetCodec.decode(json);
        assertEquals("move_token", decoded.type);
        assertEquals("tok-1", decoded.tokenId);
        assertEquals(12.0, decoded.x);
        assertEquals(34.5, decoded.y);
    }

    @Test
    void addTokenComObjetoAninhadoSobreviveAIdaEVolta() {
        TokenModel token = new TokenModel("Orc", "/img/orc.png");
        token.setCurrentHp(15);
        token.setMaxHp(15);

        String json = NetCodec.encode(NetMessage.addToken(token));
        NetMessage decoded = NetCodec.decode(json);

        assertEquals("add_token", decoded.type);
        assertEquals("Orc", decoded.token.getName());
        assertEquals(15, decoded.token.getCurrentHp());
        assertEquals(token.getId(), decoded.token.getId());
    }

    @Test
    void chatComAcentosENumerosSobreviveAIdaEVolta() {
        NetMessage original = NetMessage.chat("Jogador 1", "ataquei com +5, causei 12 de dano!");
        NetMessage decoded = NetCodec.decode(NetCodec.encode(original));
        assertEquals(original.text, decoded.text);
        assertTrue(decoded.text.contains("+5"));
    }
}
