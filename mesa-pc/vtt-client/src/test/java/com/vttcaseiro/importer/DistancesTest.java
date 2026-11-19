package com.vttcaseiro.importer;

import com.vttcaseiro.view.Distances;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Medidas do mapa em pes e metros juntos (5 pes = 1,5 m, como nos livros em portugues). */
class DistancesTest {
    @Test
    void feetAndMeters() {
        assertEquals("30 ft (9 m)", Distances.feetAndMeters(30));
        assertEquals("5 ft (1,5 m)", Distances.feetAndMeters(5));
        assertEquals("15 ft (4,5 m)", Distances.feetAndMeters(15));
        assertEquals("120 ft (36 m)", Distances.feetAndMeters(120));
        assertEquals("0 ft (0 m)", Distances.feetAndMeters(0));
    }
}
