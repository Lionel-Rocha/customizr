package com.vttcaseiro.app;

import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.view.Distances;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ferramentas do mapa de mundo: regua de viagem, procurar lugar pelo nome e o ano do mundo no calendario. */
class WorldMapToolsTest {

    @Test
    void rulerShowsTravelOnlyOnWorldMaps() {
        assertFalse(Distances.isWorldScale(5), "mapa de combate");
        assertFalse(Distances.isWorldScale(100));
        assertTrue(Distances.isWorldScale(5280), "1 milha por casa");
        // 36 km = 1 dia de marcha normal (4,5 km/h x 8 h); rapido 6 km/h = 6 h
        String t = Distances.travel(36_000 / 0.3048);
        assertTrue(t.startsWith("36 km (22 mi)"), t);
        assertTrue(t.contains("a pé: 1 dia"), t);
        assertTrue(t.contains("rápido: 6 h"), t);
        assertEquals("300 m", Distances.travel(300 / 0.3048));
        assertTrue(Distances.travel(2500 / 0.3048).startsWith("2,5 km (1,6 mi)"), Distances.travel(2500 / 0.3048));
    }

    @Test
    void placeSearchIgnoresAccentsAndCase() {
        assertEquals("arvore alta", Main.foldText("  Árvore ALTA "));
        assertTrue(Main.foldText("Paz'tasse").contains(Main.foldText("PAZ")));
        assertEquals("", Main.foldText(null));
    }

    @Test
    void worldYearKeepsDayMonthAndHour() {
        Campaign.Calendar c = new Campaign.Calendar();
        c.day = c.dayOf(12, 3, 1);
        c.minuteOfDay = 15 * 60;
        c.setYear(4001);
        long[] d = c.date();
        assertEquals(4001, d[0]);
        assertEquals(2, d[1], "marco (indice 2)");
        assertEquals(12, d[2]);
        assertEquals(15 * 60, c.minuteOfDay);
        c.setYear(-5);
        assertEquals(1, c.date()[0]);
    }
}
