package com.vttcaseiro.app;

import com.vttcaseiro.mob.MobAttack;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.TokenLink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** NPC rapido vira uma ficha de monstro que o resto do VTT entende (ataque, dano, tipo, CA). */
class QuickNpcTest {

    @Test
    void npcRapidoComAtaqueQueOMapaEntende() {
        MobSheet npc = Main.buildQuickNpc("Taverneiro Bram", "12", "11", "14", "Porrete", "3", "1d6+1 contundente",
                "Deve dinheiro ao Barao", "abc.png");
        assertEquals("Taverneiro Bram", npc.getName());
        assertEquals(12, npc.getArmorClass());
        assertEquals(11, npc.getHitPointsAverage());
        String action = npc.getActions().get(0).getDescription();
        assertEquals(3, MobAttack.attackBonus(action).orElseThrow(), action);
        assertEquals("1d6+1", MobAttack.damage(action).orElseThrow());
        assertEquals("bludgeoning", MobAttack.damageType(action));
        assertEquals("Notas", npc.getTraits().get(0).getName());
        TokenModel token = TokenLink.tokenFromMob(npc, npc.getName());
        assertEquals(11, token.getMaxHp());
        assertEquals(2, token.getInitiativeBonus(), "DES 14");
        assertEquals("abc.png", token.getImageAssetId());

        MobSheet blank = Main.buildQuickNpc("", "x", "", "", "", "", "", "", null);
        assertEquals("NPC", blank.getName());
        assertEquals(10, blank.getArmorClass());
        assertTrue(blank.getActions().isEmpty());
    }
}
