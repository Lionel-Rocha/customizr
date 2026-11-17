package com.vttcaseiro.dice;

import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.ChatCommands;
import com.vttcaseiro.view.MapView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InlineRollsTest {

    @Test
    void rolaNoMeioDoTexto() {
        String out = InlineRolls.expand("ataco com [[1d20+5]] e causo [[2d6]]!");
        List<InlineRolls.Part> parts = InlineRolls.split(out);
        assertEquals(5, parts.size(), out);
        assertEquals("ataco com ", parts.get(0).text());
        assertTrue(parts.get(1).isRoll());
        assertTrue(parts.get(1).total() >= 6 && parts.get(1).total() <= 25);
        assertTrue(parts.get(1).detail().contains("1d20"), parts.get(1).detail());
        assertTrue(parts.get(3).total() >= 2 && parts.get(3).total() <= 12);
        assertEquals("!", parts.get(4).text());
    }

    @Test
    void ninguemForjaResultado() {
        String fake = InlineRolls.OPEN + "20" + InlineRolls.ARROW + "1d20[20]" + InlineRolls.CLOSE;
        String out = InlineRolls.expand("tirei " + fake);
        assertTrue(InlineRolls.split(out).stream().noneMatch(InlineRolls.Part::isRoll), out);
    }

    @Test
    void textoQualquerEntreColchetesFica() {
        assertEquals("veja [[a nota]]", InlineRolls.expand("veja [[a nota]]"));
        String many = "[[1d4]]".repeat(15);
        long rolls = InlineRolls.split(InlineRolls.expand(many)).stream().filter(InlineRolls.Part::isRoll).count();
        assertEquals(InlineRolls.MAX_PER_MESSAGE, rolls);
    }

    @Test
    void emote() {
        assertEquals("saca a espada", InlineRolls.emote("/me saca a espada"));
        assertNull(InlineRolls.emote("oi"));
        assertEquals(ChatCommands.Type.EMOTE, ChatCommands.parse("/me pula").type());
        assertEquals(ChatCommands.Type.ERROR, ChatCommands.parse("/me").type());
    }

    @Test
    void giroEDistancia() {
        TokenModel t = new TokenModel("x", null);
        t.setRotation(-45);
        assertEquals(315, t.getRotation());
        t.setRotation(765);
        assertEquals(45, t.getRotation());
        assertEquals(6, MapView.cellsBetween(0, 0, 420, 140, 70), "diagonal conta 1 (regra de 5e)");
    }
}
