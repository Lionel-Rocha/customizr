package com.vttcaseiro.service;

import com.vttcaseiro.character.EffectModifiers;
import com.vttcaseiro.model.Aura;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Auras (quem esta no raio ganha o efeito, quem sai perde) e nomes sortidos dos monstros. */
class AuraAndFlavorTest {

    private static TokenModel token(String name, double cellX, double cellY, String owner) {
        TokenModel t = new TokenModel(name, null);
        t.setX(cellX * 70);
        t.setY(cellY * 70);
        t.setOwnerId(owner);
        return t;
    }

    @Test
    void auraReachesAlliesInRangeOnly() {
        SceneModel s = new SceneModel();
        s.getGrid().setCellSize(70);
        s.getGrid().setFeetPerCell(5);
        TokenModel paladin = token("Lyra", 0, 0, "ana");
        paladin.setAuras(List.of(new Aura("Aura de Proteção [salv+3]", 10, Aura.ALLIES, true)));
        TokenModel near = token("Bran", 2, 0, "beto");      // 10 pés: dentro
        TokenModel far = token("Caio", 3, 0, "caio");       // 15 pés: fora
        TokenModel goblin = token("Goblin", 1, 0, null);    // perto, mas é do outro lado
        s.addToken(paladin);
        s.addToken(near);
        s.addToken(far);
        s.addToken(goblin);

        Map<String, List<String>> want = AuraService.desired(s);
        assertEquals(List.of("Aura de Proteção [salv+3] (aura de Lyra)"), want.get(paladin.getId()), "vale pra ela também");
        assertEquals(1, want.get(near.getId()).size());
        assertTrue(want.get(far.getId()).isEmpty(), "15 pés: fora do raio");
        assertTrue(want.get(goblin.getId()).isEmpty(), "inimigo não ganha aura de aliados");

        // o efeito de aura conta nas rolagens como qualquer efeito
        EffectModifiers.Mods mods = EffectModifiers.parse(want.get(near.getId()));
        assertNotNull(mods);

        // saiu do raio: merge tira só o de aura, mantém os outros
        List<String> current = new ArrayList<>(List.of("Abençoado", "Aura de Proteção [salv+3] (aura de Lyra)"));
        assertEquals(List.of("Abençoado"), AuraService.merge(current, List.of()));
        assertTrue(AuraService.isAuraEffect("Bênção (aura de Clérigo)"));
        assertFalse(AuraService.isAuraEffect("Bênção"));
    }

    @Test
    void enemyAuraHitsTheOtherSide() {
        SceneModel s = new SceneModel();
        s.getGrid().setCellSize(70);
        TokenModel lich = token("Lich", 0, 0, null);
        lich.setAuras(List.of(new Aura("Aura de Medo [desv:ataque]", 15, Aura.ENEMIES, false)));
        TokenModel hero = token("Lyra", 2, 2, "ana"); // diagonal de ~14 pés
        TokenModel zombie = token("Zumbi", 1, 0, null);
        s.addToken(lich);
        s.addToken(hero);
        s.addToken(zombie);
        Map<String, List<String>> want = AuraService.desired(s);
        assertEquals(1, want.get(hero.getId()).size());
        assertTrue(want.get(zombie.getId()).isEmpty());
        assertTrue(want.get(lich.getId()).isEmpty(), "sem 'vale pra ele também'");
    }

    @Test
    void flavorNamesAreUniqueAndFallBackToNumbers() {
        Random r = new Random(1);
        List<String> taken = new ArrayList<>(List.of("Goblin"));
        for (int i = 0; i < FlavorNames.EPITHETS.size(); i++) {
            String n = FlavorNames.pick("Goblin", taken, r);
            assertFalse(taken.contains(n), "repetiu " + n);
            assertTrue(n.startsWith("Goblin "));
            taken.add(n);
        }
        assertEquals("Goblin 2", FlavorNames.pick("Goblin", taken, r), "acabaram os apelidos: número");
    }
}
