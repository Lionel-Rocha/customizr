package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.model.Source;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.service.ContentImportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Especies, antecedentes e talentos no formato dos arquivos do 5etools. */
@SpringBootTest
@Transactional
class ContentImportTests {

    @Autowired private ContentImportService contentImportService;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        if (sourceRepository.getSourceByCode("PHB").isEmpty()) {
            sourceRepository.save(new Source("Player's Handbook (2014)", "PHB"));
        }
        // XPHB vem do data.sql
    }

    @Test
    void importsSpeciesWithSizeSpeedAndReadableText() throws Exception {
        String json = """
                {"race": [
                  {"name": "Elf", "source": "XPHB", "size": ["M"], "speed": 30,
                   "entries": [{"type": "entries", "name": "Darkvision",
                                "entries": ["You have {@sense Darkvision|XPHB} with a range of 60 feet."]},
                               {"type": "entries", "name": "Trance", "entries": ["You don't need to sleep."]}]},
                  {"name": "Dwarf", "source": "PHB", "size": ["M"], "speed": {"walk": 25},
                   "ability": [{"con": 2}], "entries": ["Bold and hardy."]},
                  {"name": "Homebrew", "source": "HB", "size": ["S"], "speed": 30}
                ]}""";
        ContentImportResponse result = contentImportService.importSpecies(objectMapper.readTree(json));

        assertEquals(2, result.created().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().getFirst().reason().contains("not configured"));

        var elf = contentImportService.species("elf").getFirst();
        assertEquals("Medium", elf.size());
        assertEquals(30, elf.speedFt());
        assertTrue(elf.description().contains("Darkvision. You have Darkvision with a range of 60 feet."));
        var dwarf = contentImportService.species("Dwarf").getFirst();
        assertEquals(25, dwarf.speedFt());
        assertEquals("CON +2", dwarf.abilityScoreIncreases());

        ContentImportResponse again = contentImportService.importSpecies(objectMapper.readTree(json));
        assertEquals(0, again.created().size());
        assertEquals(2, again.updated().size(), "importar de novo atualiza, nao duplica");
    }

    @Test
    void importsBackgroundsWithAbilityChoicesSkillsAndFeat() throws Exception {
        String json = """
                {"background": [
                  {"name": "Acolyte", "source": "XPHB",
                   "ability": [{"choose": {"weighted": {"from": ["int", "wis", "cha"], "weights": [2, 1]}}}],
                   "feats": [{"magic initiate; cleric|xphb": true}],
                   "skillProficiencies": [{"insight": true, "religion": true}],
                   "entries": [{"type": "list", "items": [
                      {"type": "item", "name": "Skill Proficiencies:", "entry": "{@skill Insight} and {@skill Religion}"}]}]}
                ]}""";
        contentImportService.importBackgrounds(objectMapper.readTree(json));

        var acolyte = contentImportService.backgrounds(null).stream()
                .filter(b -> b.name().equals("Acolyte")).findFirst().orElseThrow();
        assertTrue(acolyte.abilityScoreIncreases().startsWith("choose from INT, WIS, CHA"));
        assertEquals("Insight, Religion", acolyte.skillProficiencies());
        assertEquals("Magic Initiate; Cleric", acolyte.grantedFeat());
        assertEquals("- Skill Proficiencies: Insight and Religion", acolyte.description());
    }

    @Test
    void importsFeatsWithCategoryAndPrerequisites() throws Exception {
        String json = """
                {"feat": [
                  {"name": "Alert", "source": "XPHB", "category": "O",
                   "entries": ["When you roll Initiative, you can add your {@variantrule Proficiency|XPHB} Bonus."]},
                  {"name": "Grappler", "source": "XPHB", "category": "G",
                   "prerequisite": [{"level": 4, "ability": [{"str": 13, "dex": 13}]}], "entries": ["Strong grip."]},
                  {"name": "Copy", "source": "XPHB", "_copy": {"name": "Alert"}}
                ]}""";
        ContentImportResponse result = contentImportService.importFeats(objectMapper.readTree(json));

        assertEquals(2, result.created().size());
        assertEquals("Copies of other entries (_copy) are not supported yet", result.skipped().getFirst().reason());
        var alert = contentImportService.feats("alert").getFirst();
        assertEquals("Origin", alert.category());
        assertTrue(alert.description().contains("add your Proficiency Bonus"));
        var grappler = contentImportService.feats("Grappler").getFirst();
        assertEquals("General", grappler.category());
        assertEquals("Level 4, STR 13+ or DEX 13+", grappler.prerequisite());

        assertThrows(ResponseStatusException.class,
                () -> contentImportService.importFeats(objectMapper.readTree("{\"spell\": []}")));
    }
}
