package com.vttcaseiro.vision;

import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Linha de visao e bloqueio de movimento, so com numeros. */
class VisibilityTest {

    @Test
    void salaVaziaVeTudo() {
        double[] poly = Visibility.polygon(50, 50, List.of(), 200, 100);
        assertTrue(Visibility.inside(poly, 190, 90));
        assertTrue(Visibility.inside(poly, 5, 5));
        assertFalse(Visibility.inside(poly, 250, 50), "fora do mapa");
    }

    @Test
    void paredeEscondeOQueEstaAtras() {
        // Parede vertical em x=100, de y=0 a y=80 (deixa uma passagem embaixo, y 80-100).
        List<Wall> walls = List.of(new Wall(100, 0, 100, 80, false));
        double[] poly = Visibility.polygon(50, 40, walls, 200, 100);
        assertTrue(Visibility.inside(poly, 90, 40), "antes da parede");
        assertFalse(Visibility.inside(poly, 150, 40), "atras da parede");
        // Perto do chao, olhando pela passagem: ve o que esta logo depois dela.
        double[] low = Visibility.polygon(50, 90, walls, 200, 100);
        assertTrue(Visibility.inside(low, 150, 95), "pela passagem embaixo da pra ver um pedaco");
        assertFalse(Visibility.inside(low, 150, 40), "mas nao o que a parede tapa");
    }

    @Test
    void portaAbertaDeixaVerFechadaNao() {
        Wall door = new Wall(100, 0, 100, 100, true);
        assertFalse(Visibility.inside(Visibility.polygon(50, 50, List.of(door), 200, 100), 150, 50));
        door.setOpen(true);
        assertTrue(Visibility.inside(Visibility.polygon(50, 50, List.of(door), 200, 100), 150, 50));
    }

    @Test
    void movimentoAtravessandoParedeEBloqueado() {
        List<Wall> walls = List.of(new Wall(100, 0, 100, 80, false));
        assertTrue(Visibility.blocked(50, 40, 150, 40, walls));
        assertFalse(Visibility.blocked(50, 90, 150, 90, walls), "pela passagem pode");
        assertFalse(Visibility.blocked(50, 40, 90, 40, walls), "nao chegou na parede");
        assertFalse(Visibility.blocked(100, 80, 150, 90, walls), "encostar na ponta nao conta");
        Wall door = new Wall(100, 80, 100, 100, true);
        assertTrue(Visibility.blocked(50, 90, 150, 90, List.of(door)), "porta fechada bloqueia");
        door.setOpen(true);
        assertFalse(Visibility.blocked(50, 90, 150, 90, List.of(door)));
    }

    @Test
    void visaoNoEscuroDoBestiarioEPesEmPixels() {
        assertEquals(60, Visibility.darkvisionFeet(List.of("Darkvision 60 ft.", "Passive Perception 9")));
        assertEquals(120, Visibility.darkvisionFeet(List.of("Darkvision 60 ft., Truesight 120 ft.")));
        assertEquals(60, Visibility.darkvisionFeet(List.of("Visao no escuro 18 m")));
        assertEquals(0, Visibility.darkvisionFeet(List.of("Passive Perception 12")));
        assertEquals(840, Visibility.feetToPx(60, 70, 5), 0.001, "60 pes = 12 casas de 70 px");
    }
}
