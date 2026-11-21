package com.vttcaseiro.service;

import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.library.LibraryStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classes oficiais no programa de PC: Wizards e parceiros entram; World of Warcraft e Unearthed Arcana ficam de fora;
 * classe que ja estava na mesa ganha as subclasses que faltavam. Dados inventados (nada de texto de livro).
 */
class OfficialClassesImportTest {

    @Test
    void officialClassesAndMissingSubclasses(@TempDir Path dir) throws Exception {
        LibraryStorage mesa = new LibraryStorage(dir);
        ClassImportService service = new ClassImportService(mesa);
        Map<String, String> files = new HashMap<>();
        files.put("index.json", "{\"testclass\": \"class-testclass.json\", \"warcraft\": \"class-nao-veio.json\"}");
        files.put("class-testclass.json", """
                {
                  "class": [
                    {"name": "Testclass", "source": "PHB", "hd": {"number": 1, "faces": 10}, "subclassUnlockLevel": 3},
                    {"name": "Testclass", "source": "WoW", "hd": {"number": 1, "faces": 10}, "subclassUnlockLevel": 3}
                  ],
                  "subclass": [
                    {"name": "Path of Testing", "shortName": "Testing", "source": "PHB", "className": "Testclass", "classSource": "PHB"},
                    {"name": "Warcraft Path", "shortName": "Warcraft", "source": "WoW", "className": "Testclass", "classSource": "PHB"},
                    {"name": "Playtest Path", "shortName": "Playtest", "source": "UA2025Test", "className": "Testclass", "classSource": "PHB"}
                  ]
                }
                """);

        var first = service.importOfficial(files::get);
        assertEquals(1, first.classes());
        assertEquals(1, first.subclasses(), "so a subclasse da Wizards");
        DndClass imported = service.getClasses().get(0);
        assertEquals("PHB", imported.getSourceCode());
        assertTrue(service.getClasses().stream().noneMatch(c -> "WoW".equals(c.getSourceCode())));

        // um livro novo traz mais uma subclasse pra classe que ja esta na mesa
        files.put("class-testclass.json", files.get("class-testclass.json").replace("\"subclass\": [", """
                "subclass": [
                    {"name": "Partner Path", "shortName": "Partner", "source": "TDCSR", "className": "Testclass", "classSource": "PHB"},"""));
        var second = service.importOfficial(files::get);
        assertEquals(0, second.classes());
        assertEquals(1, second.subclasses(), "a do parceiro entrou na classe que ja existia");
        assertEquals(2, imported.getSubclasses().size());

        // gravou na mesa: abrir de novo traz tudo
        assertEquals(2, new ClassImportService(mesa).getClasses().get(0).getSubclasses().size());
        assertEquals(0, service.importOfficial(files::get).subclasses(), "de novo nao duplica");
        // abrir a mesa outra vez (servico novo, mesmos arquivos): nada novo, entao o chat nao recebe aviso
        assertFalse(new ClassImportService(mesa).importOfficial(files::get).changedSomething(),
                "reabrir a mesa nao repete o aviso de classes");
    }
}
