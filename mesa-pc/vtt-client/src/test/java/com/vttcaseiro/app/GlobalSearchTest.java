package com.vttcaseiro.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Busca unica (Ctrl+K): o que bate e em que ordem. */
class GlobalSearchTest {

    static GlobalSearch.Item it(GlobalSearch.Kind k, String title) {
        return new GlobalSearch.Item(k, title, null, title);
    }

    @Test
    void ranksStartThenWordThenContainsAndIgnoresAccents() {
        List<GlobalSearch.Item> all = List.of(
                it(GlobalSearch.Kind.COMPENDIO, "Fireball"),
                it(GlobalSearch.Kind.MONSTRO, "Dragão Vermelho Adulto"),
                it(GlobalSearch.Kind.FICHA, "Lyra"),
                it(GlobalSearch.Kind.PIN, "Vau do Dragão"),
                it(GlobalSearch.Kind.DIARIO, "Lendas dragonicas"),
                it(GlobalSearch.Kind.CENA, "Covil do dragao"));
        List<GlobalSearch.Item> r = GlobalSearch.filter(all, "DRAGAO", 10);
        assertEquals("Dragão Vermelho Adulto", r.get(0).title(), "comeca com o texto");
        assertEquals(List.of("Vau do Dragão", "Covil do dragao"), r.subList(1, 3).stream().map(GlobalSearch.Item::title).toList(),
                "palavra que comeca; empate pela ordem dos tipos (lugar antes de cena)");
        assertEquals(3, r.size(), "\"dragonicas\" nao contem \"dragao\"");
        assertEquals(4, GlobalSearch.filter(all, "drag", 10).size(), "\"drag\" tambem pega dragonicas");
        assertTrue(GlobalSearch.filter(all, "  ", 10).isEmpty());
        assertEquals(2, GlobalSearch.filter(all, "dra", 2).size(), "limite");
    }

    @Test
    void containsInsideAWordStillCounts() {
        assertEquals(1, GlobalSearch.score("fireball", "ball"));
        assertEquals(2, GlobalSearch.score("vau do dragao", "dra"));
        assertEquals(3, GlobalSearch.score("dragao", "dra"));
        assertEquals(0, GlobalSearch.score("lyra", "x"));
        assertEquals("cao", GlobalSearch.fold(" CÃO "));
    }
}
