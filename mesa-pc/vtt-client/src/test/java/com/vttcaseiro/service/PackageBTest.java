package com.vttcaseiro.service;

import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.library.JsonListStore;
import com.vttcaseiro.model.RandomTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackageBTest {

    @Test
    void dificuldadeDeEncontro2024() {
        List<Integer> party = List.of(3, 3, 3, 3); // baixa 600, moderada 900, alta 1600
        var r = EncounterCalculator.evaluate(party, List.of(50, 50, 50, 50));
        assertEquals(600, r.low());
        assertEquals(900, r.moderate());
        assertEquals(1600, r.high());
        assertEquals(EncounterCalculator.Rating.TRIVIAL, r.rating(), "4 goblins (200) e pouco pra 4 de nivel 3");
        assertEquals(EncounterCalculator.Rating.LOW, EncounterCalculator.evaluate(party, List.of(450)).rating());
        assertEquals(EncounterCalculator.Rating.MODERATE, EncounterCalculator.evaluate(party, List.of(700)).rating());
        assertEquals(EncounterCalculator.Rating.HIGH, EncounterCalculator.evaluate(party, List.of(1100)).rating());
        assertEquals(EncounterCalculator.Rating.DEADLY, EncounterCalculator.evaluate(party, List.of(1800)).rating());
        assertEquals(22000, EncounterCalculator.evaluate(List.of(25), List.of()).high(), "nivel acima de 20 conta como 20");
        assertTrue(r.summary().contains("XP dos monstros: 200"));
    }

    @Test
    void tabelaAleatoriaComFaixasELinhasSoltas() {
        RandomTable t = new RandomTable("Floresta", "1-3: 2d4 lobos\n4: um urso-coruja\nUm druida perdido\n\n");
        assertEquals(3, t.rows().size());
        assertEquals(5, t.dieSize(), "linha sem faixa continua depois da ultima");
        assertEquals("d5 = 2: 2d4 lobos", t.roll(n -> 2));
        assertEquals("d5 = 5: Um druida perdido", t.roll(n -> 5));
        assertEquals("(tabela vazia)", new RandomTable("x", " \n ").roll(n -> 1));
        for (RandomTable sample : RandomTable.samples()) {
            assertTrue(sample.dieSize() >= 6, sample.getName());
            for (int v = 1; v <= sample.dieSize(); v++) {
                final int value = v;
                assertFalse(sample.roll(n -> value).contains("nenhuma linha"), sample.getName() + " " + v);
            }
        }
    }

    @Test
    void comandoTabelaNoChat() {
        assertEquals(ChatCommands.Type.TABLE, ChatCommands.parse("/tabela Tesouro rapido").type());
        assertEquals("Tesouro rapido", ChatCommands.parse("/tabela Tesouro rapido").text());
        assertEquals("clima", ChatCommands.parse("/t clima").text());
        assertEquals("", ChatCommands.parse("/tabela").text());
        assertTrue(ChatCommands.HELP.contains("/tabela"));
    }

    @Test
    void filaDeCenasEArquivoDaMesa(@TempDir Path dir) {
        JsonListStore<String> store = new JsonListStore<>(dir, "fila-cenas.json", new TypeToken<List<String>>() { }.getType());
        assertFalse(store.exists());
        store.save(List.of("Taverna", "Estrada", "Cripta"));
        List<String> q = new JsonListStore<String>(dir, "fila-cenas.json", new TypeToken<List<String>>() { }.getType()).load();
        assertEquals(List.of("Taverna", "Estrada", "Cripta"), q);
        assertEquals("Estrada", SceneQueue.next(q, "Taverna").orElseThrow());
        assertEquals("Taverna", SceneQueue.next(q, "Outra").orElseThrow(), "cena fora da fila: comeca do inicio");
        assertTrue(SceneQueue.next(q, "Cripta").isEmpty(), "fim da fila");
        assertTrue(SceneQueue.next(List.of(), null).isEmpty());
    }
}
