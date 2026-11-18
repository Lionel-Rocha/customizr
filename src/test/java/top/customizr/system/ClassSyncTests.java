package top.customizr.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.service.FiveToolsSyncService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atualizacao pelo 5etools tambem traz classes e subclasses: tudo da Wizards e dos parceiros; World of Warcraft e
 * Unearthed Arcana ficam de fora. Dados inventados (nada de texto de livro).
 */
@SpringBootTest
class ClassSyncTests {

    @Autowired private FiveToolsSyncService sync;
    @Autowired private DndClassRepository classes;
    @Autowired private SourceRepository sources;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void officialAndPartnerClassesComeInWithoutWarcraftOrPlaytest() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String className = "Testclass" + suffix;
        String partner = "TP" + suffix.toUpperCase();
        Map<String, JsonNode> files = new HashMap<>();
        files.put("books.json", json.readTree("{\"book\": []}"));
        files.put("adventures.json", json.readTree("{\"adventure\": []}"));
        files.put("items-base.json", json.readTree("{}"));
        files.put("items.json", json.readTree("{}"));
        files.put("class/index.json", json.readTree("{\"testclass\": \"class-testclass.json\", \"missing\": \"class-missing.json\"}"));
        files.put("class/class-testclass.json", json.readTree("""
                {
                  "class": [
                    {"name": "%1$s", "source": "PHB", "hd": {"number": 1, "faces": 10}, "subclassUnlockLevel": 3},
                    {"name": "%1$s", "source": "WoW", "hd": {"number": 1, "faces": 10}, "subclassUnlockLevel": 3}
                  ],
                  "subclass": [
                    {"name": "Path of Testing", "shortName": "Testing", "source": "XGE", "className": "%1$s", "classSource": "PHB"},
                    {"name": "Partner Path", "shortName": "Partner", "source": "%2$s", "className": "%1$s", "classSource": "PHB"},
                    {"name": "Warcraft Path", "shortName": "Warcraft", "source": "WoW", "className": "%1$s", "classSource": "PHB"},
                    {"name": "Playtest Path", "shortName": "Playtest", "source": "UA2025Test", "className": "%1$s", "classSource": "PHB"}
                  ]
                }
                """.formatted(className, partner)));

        var result = sync.syncWith(file -> {
            if (!files.containsKey(file)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY);
            }
            return files.get(file);
        }, false);

        assertEquals(1, result.classesImported(), result.toString());
        assertEquals(2, result.subclassesAdded(), "a da Wizards e a do parceiro");
        assertEquals(3, result.classEntriesLeftOut(), "classe e subclasse de Warcraft + a de teste");
        var imported = classes.findByClassNameAndSourceCode(className, "PHB").orElseThrow();
        assertTrue(classes.findByClassNameAndSourceCode(className, "WoW").isEmpty());
        assertTrue(sources.getSourceByCode(partner).isPresent(), "fonte do parceiro criada");
        assertEquals(3, imported.getSubclassUnlockLevel());

        // de novo: nada duplica
        var again = sync.syncWith(files::get, false);
        assertEquals(0, again.classesImported());
        assertEquals(0, again.subclassesAdded());
    }
}
