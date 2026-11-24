package com.vttcaseiro.service;

import com.vttcaseiro.character.PreparedSpell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Area da magia lida do texto (ingles do 5etools e portugues). */
class SpellAreaTest {

    @Test
    void readsTheCommonShapes() {
        assertEquals(new SpellArea("Cone", 15), SpellArea.parse("Self (15-foot cone)"));
        assertEquals(new SpellArea("Linha", 100), SpellArea.parse("Self (100-foot line)"));
        assertEquals(new SpellArea("Circulo", 20), SpellArea.parse("Each creature in a 20-foot-radius Sphere centered on a point"));
        assertEquals(new SpellArea("Cubo", 10), SpellArea.parse("a 10-foot Cube originating from you"));
        assertEquals(new SpellArea("Circulo", 10), SpellArea.parse("a 10-foot Emanation"));
        assertEquals(new SpellArea("Cone", 15), SpellArea.parse("Pessoal (cone de 4,5 m)"));
        assertEquals(new SpellArea("Circulo", 20), SpellArea.parse("esfera de 6 m de raio"));
        assertEquals(new SpellArea("Circulo", 20), SpellArea.parse("uma esfera com 6 metros de raio"));
        assertNull(SpellArea.parse("150 feet"), "so alcance, sem area");
        assertNull(SpellArea.parse(""));
    }

    @Test
    void spellUsesAreaThenRange() {
        PreparedSpell fireball = new PreparedSpell(3, "Fireball");
        fireball.setRange("150 feet");
        assertNull(SpellArea.of(fireball));
        fireball.setArea(SpellArea.phrase("A bright streak... 20-foot-radius Sphere centered on that point"));
        assertEquals(new SpellArea("Circulo", 20), SpellArea.of(fireball));
        PreparedSpell hands = new PreparedSpell(1, "Burning Hands");
        hands.setRange("Self (15-foot cone)");
        assertEquals("Cone", SpellArea.of(hands).shape());
    }
}
