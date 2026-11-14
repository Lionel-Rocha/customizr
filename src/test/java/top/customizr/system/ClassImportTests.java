package top.customizr.system;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.customizr.system.dto.ImportClassesResponse;
import top.customizr.system.service.ClassService;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ClassImportTests {

    @Autowired
    private ClassService classService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void importsCompleteClassesRetainsMetadataAndSkipsDuplicatesAndIncompleteEntries() throws Exception {
        String payload = """
                {
                  "class": [
                    {
                      "name": "Import Test Wizard",
                      "source": "TST",
                      "hd": {"number": 1, "faces": 6},
                      "classFeatures": [{"classFeature": "Arcane Tradition|TST|2", "gainSubclassFeature": true}]
                    },
                    {"name": "Incomplete Test Class", "source": "TST"}
                  ],
                  "classFeature": [
                    {
                      "name": "Arcane Recovery",
                      "className": "Import Test Wizard",
                      "classSource": "TST",
                      "source": "TST",
                      "level": 1,
                      "entries": ["Recover spell slots."]
                    }
                  ],
                  "subclass": [
                    {
                      "name": "School of Testing",
                      "shortName": "Test",
                      "className": "Import Test Wizard",
                      "classSource": "TST",
                      "source": "TST",
                      "subclassFeatures": ["Test Feature|TST|2"]
                    }
                  ],
                  "subclassFeature": [
                    {
                      "name": "Test Feature",
                      "className": "Import Test Wizard",
                      "classSource": "TST",
                      "subclassShortName": "Test",
                      "subclassSource": "TST",
                      "source": "TST",
                      "level": 2,
                      "entries": ["A test feature."]
                    }
                  ]
                }
                """;

        ImportClassesResponse firstImport = classService.importClasses(objectMapper.readTree(payload));

        assertEquals(1, firstImport.imported().size());
        assertEquals(1, firstImport.imported().getFirst().subclasses());
        assertEquals(1, firstImport.imported().getFirst().classFeatures());
        assertEquals("Hit-die number and faces are required", firstImport.skipped().getFirst().reason());

        var savedClass = classService.getAllClasses().stream()
                .filter(dndClass -> "Import Test Wizard".equals(dndClass.name()))
                .findFirst()
                .orElseThrow();
        var classFeatures = (java.util.List<?>) savedClass.metadata().get("classFeature");
        var subclasses = (java.util.List<?>) savedClass.metadata().get("subclass");
        assertEquals(1, classFeatures.size());
        assertEquals(1, subclasses.size());
        assertEquals("Arcane Recovery", ((java.util.Map<?, ?>) classFeatures.getFirst()).get("name"));

        ImportClassesResponse secondImport = classService.importClasses(objectMapper.readTree(payload));
        assertEquals(0, secondImport.imported().size());
        assertEquals(2, secondImport.skipped().size());
        assertEquals("Already imported", secondImport.skipped().getFirst().reason());
    }

    @Test
    void addsFeatureToAnAlreadyImportedSubclass() throws Exception {
        String classPayload = """
                {
                  "class": [{
                    "name": "Feature Endpoint Test Class",
                    "source": "TST",
                    "hd": {"number": 1, "faces": 8},
                    "classFeatures": [{
                      "classFeature": "Test Subclass|TST|3",
                      "gainSubclassFeature": true
                    }]
                  }],
                  "subclass": [{
                    "name": "Feature Endpoint Test Subclass",
                    "shortName": "FeatureTest",
                    "className": "Feature Endpoint Test Class",
                    "classSource": "TST",
                    "source": "TST",
                    "subclassFeatures": []
                  }]
                }
                """;
        classService.importClasses(objectMapper.readTree(classPayload));

        var feature = classService.addSubclassFeature(objectMapper.readTree("""
                {
                  "name": "Feature Endpoint Test Feature",
                  "className": "Feature Endpoint Test Class",
                  "classSource": "TST",
                  "subclassShortName": "FeatureTest",
                  "subclassSource": "TST",
                  "source": "TST",
                  "level": 3,
                  "entries": ["Added after class import."]
                }
                """));

        assertEquals("Feature Endpoint Test Feature", feature.name());
        assertEquals(3, feature.level());
    }
}
