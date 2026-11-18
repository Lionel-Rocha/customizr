package top.customizr.system;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.customizr.system.dto.ImportClassesResponse;
import top.customizr.system.model.Components;
import top.customizr.system.model.Duration;
import top.customizr.system.model.DurationUnit;
import top.customizr.system.model.Source;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.SubclassSpellGrantType;
import top.customizr.system.model.Time;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;
import top.customizr.system.service.ClassService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ClassImportTests {

    @Autowired
    private ClassService classService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DndClassRepository dndClassRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private SpellRepository spellRepository;

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
    @Transactional
    void returnsClassFeaturesAvailableSpellsAndCompleteSubclassDetails() throws Exception {
        Source spellSource = sourceRepository.save(new Source("Class Detail Test Source", "CDT"));
        Spell spell = spellRepository.save(new Spell(
                "Arc Bolt",
                SpellSchool.Evocation,
                SpellLevel.FIRST,
                Time.Action,
                60,
                Set.of(Components.Verbal, Components.Somatic),
                new Duration(1, DurationUnit.MINUTE, false),
                spellSource,
                "A bolt of arcane energy."
        ));
        String payload = """
                {
                  "class": [{
                    "name": "Detailed Test Wizard",
                    "source": "TST",
                    "hd": {"number": 1, "faces": 6},
                    "classFeatures": [{
                      "classFeature": "Storm Path|TST|3",
                      "gainSubclassFeature": true
                    }]
                  }],
                  "classFeature": [{
                    "name": "Mystic Training",
                    "className": "Detailed Test Wizard",
                    "classSource": "TST",
                    "source": "TST",
                    "level": 1,
                    "entries": ["Learn to shape arcane energy."]
                  }],
                  "subclass": [{
                    "name": "Storm Path",
                    "shortName": "Storm",
                    "className": "Detailed Test Wizard",
                    "classSource": "TST",
                    "source": "TST",
                    "subclassFeatures": ["Gale Ward|TST|3"],
                    "additionalSpells": [{
                      "prepared": {"3": ["Arc Bolt|CDT"]}
                    }]
                  }],
                  "subclassFeature": [{
                    "name": "Gale Ward",
                    "className": "Detailed Test Wizard",
                    "classSource": "TST",
                    "subclassShortName": "Storm",
                    "subclassSource": "TST",
                    "source": "TST",
                    "level": 3,
                    "entries": ["A ward of wind surrounds you."]
                  }]
                }
                """;
        classService.importClasses(objectMapper.readTree(payload));

        var dndClass = dndClassRepository.findByClassNameAndSourceCode(
                "Detailed Test Wizard", "TST"
        ).orElseThrow();
        dndClass.addAvailableSpell(spell);
        dndClassRepository.save(dndClass);

        var details = classService.getClassDetails(dndClass.getClassId());
        assertEquals("Mystic Training", details.features().getFirst().name());
        assertEquals(1, details.availableSpells().size());
        assertEquals("Arc Bolt", details.availableSpells().getFirst().name());
        assertEquals("Storm Path", details.subclasses().getFirst().name());
        assertEquals("Gale Ward", details.subclasses().getFirst().features().getFirst().name());
        assertEquals("Arc Bolt", details.subclasses().getFirst().spells().getFirst().spell().name());
        assertEquals(3, details.subclasses().getFirst().spells().getFirst().grantedAtLevel());
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

    @Test
    @Transactional
    void importsSubclassPreparedAndInnateSpellLinksAndReportsMissingSpells() throws Exception {
        Source spellSource = sourceRepository.save(new Source("Subclass Spell Test Source", "SPLTST"));
        spellRepository.save(new Spell(
                "Test Spell",
                SpellSchool.Evocation,
                SpellLevel.FIRST,
                Time.Action,
                60,
                Set.<Components>of(),
                new Duration(1, DurationUnit.MINUTE, false),
                spellSource,
                "A spell used by the import test."
        ));

        String payload = """
                {
                  "class": [{
                    "name": "Spell Import Test Class",
                    "source": "TST",
                    "hd": {"number": 1, "faces": 8},
                    "classFeatures": [{
                      "classFeature": "Test Subclass|TST|3",
                      "gainSubclassFeature": true
                    }]
                  }],
                  "subclass": [{
                    "name": "Spell Import Test Subclass",
                    "shortName": "SpellTest",
                    "className": "Spell Import Test Class",
                    "classSource": "TST",
                    "source": "TST",
                    "additionalSpells": [{
                      "prepared": {"3": ["Test Spell|spltst", "Missing Spell|SPLTST"]},
                      "innate": {"5": {"daily": {"1": ["Test Spell|spltst"]}}}
                    }]
                  }]
                }
                """;
        ImportClassesResponse response = classService.importClasses(objectMapper.readTree(payload));

        assertEquals(1, response.imported().size());
        assertEquals(2, response.subclassSpellLinksAdded());
        assertEquals(1, response.unresolvedSubclassSpells().size());
        assertEquals("Missing Spell|SPLTST", response.unresolvedSubclassSpells().getFirst().reference());

        var dndClass = dndClassRepository.findByClassNameAndSourceCode(
                "Spell Import Test Class", "TST"
        ).orElseThrow();
        var spellGrants = dndClass.getSubclasses().getFirst().getSpells();
        assertEquals(2, spellGrants.size());
        var preparedGrant = spellGrants.stream()
                .filter(grant -> grant.getGrantType() == SubclassSpellGrantType.PREPARED)
                .findFirst().orElseThrow();
        assertEquals("Test Spell", preparedGrant.getSpell().getName());
        assertEquals(3, preparedGrant.getGrantedAtLevel());
        assertEquals(List.of("3"), preparedGrant.getMetadata().get("path"));
        var innateGrant = spellGrants.stream()
                .filter(grant -> grant.getGrantType() == SubclassSpellGrantType.INNATE)
                .findFirst().orElseThrow();
        assertEquals(5, innateGrant.getGrantedAtLevel());
        assertEquals(List.of("5", "daily", "1"), innateGrant.getMetadata().get("path"));

        spellRepository.save(new Spell(
                "Missing Spell",
                SpellSchool.Evocation,
                SpellLevel.FIRST,
                Time.Action,
                60,
                Set.<Components>of(),
                new Duration(1, DurationUnit.MINUTE, false),
                spellSource,
                "The spell added before the second import."
        ));

        ImportClassesResponse secondImport = classService.importClasses(objectMapper.readTree(payload));
        assertEquals(1, secondImport.skipped().size());
        assertEquals("Already imported", secondImport.skipped().getFirst().reason());
        assertEquals(1, secondImport.subclassSpellLinksAdded());
        assertEquals(0, secondImport.unresolvedSubclassSpells().size());
        assertEquals(3, spellGrants.size());

        ImportClassesResponse thirdImport = classService.importClasses(objectMapper.readTree(payload));
        assertEquals(0, thirdImport.subclassSpellLinksAdded());
        assertEquals(3, spellGrants.size());
    }

    @Test
    @Transactional
    void reimportAddsMissingSubclassAndFeaturesWithoutDuplicatingThem() throws Exception {
        String initialPayload = """
                {
                  "class": [{
                    "name": "Subclass Sync Test Class",
                    "source": "TST",
                    "hd": {"number": 1, "faces": 8},
                    "classFeatures": [{
                      "classFeature": "Test Subclass|TST|3",
                      "gainSubclassFeature": true
                    }]
                  }]
                }
                """;
        classService.importClasses(objectMapper.readTree(initialPayload));

        String updatedPayload = """
                {
                  "class": [{
                    "name": "Subclass Sync Test Class",
                    "source": "TST",
                    "hd": {"number": 1, "faces": 8},
                    "classFeatures": [{
                      "classFeature": "Test Subclass|TST|3",
                      "gainSubclassFeature": true
                    }]
                  }],
                  "subclass": [{
                    "name": "Test Subclass",
                    "shortName": "TestSubclass",
                    "className": "Subclass Sync Test Class",
                    "classSource": "TST",
                    "source": "TST",
                    "subclassFeatures": ["Test Subclass Feature|TST|3"]
                  }],
                  "subclassFeature": [{
                    "name": "Test Subclass Feature",
                    "className": "Subclass Sync Test Class",
                    "classSource": "TST",
                    "subclassShortName": "TestSubclass",
                    "subclassSource": "TST",
                    "source": "TST",
                    "level": 3,
                    "entries": ["A feature added during reimport."]
                  }]
                }
                """;

        ImportClassesResponse secondImport = classService.importClasses(objectMapper.readTree(updatedPayload));
        assertEquals(1, secondImport.subclassesAdded());
        assertEquals(1, secondImport.subclassFeaturesAdded());

        ImportClassesResponse thirdImport = classService.importClasses(objectMapper.readTree(updatedPayload));
        assertEquals(0, thirdImport.subclassesAdded());
        assertEquals(0, thirdImport.subclassFeaturesAdded());

        var dndClass = dndClassRepository.findByClassNameAndSourceCode(
                "Subclass Sync Test Class", "TST"
        ).orElseThrow();
        assertEquals(1, dndClass.getSubclasses().size());
        assertEquals(1, dndClass.getSubclasses().getFirst().getFeatures().size());
        assertEquals(1, ((List<?>) dndClass.getMetadata().get("subclass")).size());
    }
}
