package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Nomes no estilo de cada cultura do Azgaar (cadeia de letras aprendida da base de nomes). */
class AzgaarNamesTest {

    static AzgaarNames.Book book() {
        JsonArray bases = JsonParser.parseString("[{\"name\":\"Elven\",\"i\":0,\"min\":5,\"max\":10,\"b\":"
                + "\"Aelindor,Aerendil,Caladwen,Elandriel,Faelivrin,Galadhor,Ithilwen,Lorindel,Mirandel,Nimrodel,Silvaren,Thalion\"},"
                + "{\"name\":\"Vazia\",\"i\":1,\"min\":3,\"max\":6,\"b\":\"\"}]").getAsJsonArray();
        JsonArray cultures = JsonParser.parseString("[{\"i\":0,\"name\":\"Wildlands\",\"base\":1},"
                + "{\"i\":1,\"name\":\"Silvanos\",\"base\":0},{\"i\":2,\"name\":\"Some\",\"base\":0,\"removed\":true}]").getAsJsonArray();
        return AzgaarNames.read(bases, cultures);
    }

    @Test
    void suggestsNewNamesInTheCultureStyleWithinItsLength() {
        AzgaarNames.Book b = book();
        assertEquals(1, b.usableCultures().size(), "base vazia e cultura removida ficam de fora");
        assertEquals("Silvanos", b.usableCultures().get(0).name());
        Random rnd = new Random(42);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            String n = b.suggest(1, rnd);
            assertNotNull(n);
            assertTrue(n.length() >= 5 && n.length() <= 10, n);
            assertTrue(Character.isUpperCase(n.charAt(0)), n);
            seen.add(n);
        }
        assertTrue(seen.size() > 10, "variedade: " + seen);
        assertNull(b.suggest(99, rnd), "cultura que nao existe");
    }

    @Test
    void neverHangsOnTinyOrOddBases() {
        AzgaarNames.Base tiny = new AzgaarNames.Base("x", 8, 12, java.util.List.of("Ab", "Cd"));
        String n = AzgaarNames.generate(tiny, new Random(1)); // nao da pra chegar a 8 letras: devolve algo, nao trava
        assertNotNull(n);
        assertEquals("Aldor-Veth", AzgaarNames.capitalize("aldor-veth"));
    }
}
