package top.customizr.system;

import jakarta.transaction.Transactional;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterImportResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterImportService;
import top.customizr.system.service.CharacterService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importacao de personagem de Foundry (arquivo real), D&D Beyond e PDF preenchivel. */
@SpringBootTest
@Transactional
class CharacterImportTests {

    @Autowired private CharacterImportService importService;
    @Autowired private CharacterService characterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DndClassRepository dndClassRepository;

    private String master;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("Campaign", userRepository.save(new User(master, "password"))));
        if (dndClassRepository.findAll().stream().noneMatch(c -> c.getClassName().equals("Fighter"))) {
            dndClassRepository.save(new DndClass("Fighter", 10, 3, null));
        }
    }

    @Test
    void importsTheRealFoundryHeroAndCalculatesMissingValues() throws Exception {
        byte[] json;
        try (InputStream in = getClass().getResourceAsStream("/importer/foundry-randal-human-fighter.json")) {
            json = in.readAllBytes();
        }
        CharacterImportResponse result = importService.importCharacter(campaign.getId(), "randal.json", json, master);

        assertEquals("Randal (Human Fighter)", result.name());
        assertEquals("Foundry VTT (dnd5e)", result.format());
        assertEquals("Fighter 1", result.classesAdded().getFirst());
        CharacterSheetResponse sheet = characterService.getCharacterSheet(result.characterId(), master);
        assertEquals(16, sheet.attributes().strength());
        assertEquals(9, sheet.attributes().intelligence());
        assertEquals(13, sheet.attributes().wisdom());
        assertEquals(12, sheet.combat().hpMax(), "d10 cheio + CON 15 (+2)");
        assertEquals(12, sheet.combat().hpCurrent());
        assertEquals(18, sheet.combat().armorClass(), "cota de malha 16 (pesada, sem DES) + escudo 2");
    }

    @Test
    void importsDndBeyondJsonAndReportsClassesThatAreNotImported() {
        String ddb = """
                {"data": {
                  "name": "Thalia",
                  "stats": [{"id":1,"value":8},{"id":2,"value":15},{"id":3,"value":14},
                            {"id":4,"value":12},{"id":5,"value":10},{"id":6,"value":13}],
                  "bonusStats": [{"id":2,"value":2}],
                  "baseHitPoints": 14, "removedHitPoints": 3, "temporaryHitPoints": 0,
                  "classes": [{"level": 2, "definition": {"name": "Rogue", "hitDice": 8}},
                              {"level": 1, "definition": {"name": "Fighter", "hitDice": 10}}],
                  "race": {"weightSpeeds": {"normal": {"walk": 30}}}
                }}""";
        CharacterImportResponse result = importService.importCharacter(
                campaign.getId(), "thalia.json", ddb.getBytes(StandardCharsets.UTF_8), master);

        assertEquals("D&D Beyond (JSON)", result.format());
        assertEquals(1, result.classesAdded().size(), "so o Fighter existe no catalogo");
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("Rogue")));
        CharacterSheetResponse sheet = characterService.getCharacterSheet(result.characterId(), master);
        assertEquals(17, sheet.attributes().dexterity(), "15 + 2 de bonus");
        assertEquals(20, sheet.combat().hpMax(), "14 + CON(+2) x 3 niveis");
        assertEquals(17, sheet.combat().hpCurrent());
        assertEquals(30, sheet.combat().speedFt());
        assertEquals(13, sheet.combat().armorClass(), "sem CA no arquivo: 10 + DES");
    }

    @Test
    void importsAFillablePdfSheet() throws Exception {
        String[][] fields = {{"CharacterName", "Kael"}, {"ClassLevel", "Fighter 3"}, {"STR", "16"}, {"DEX", "12"},
                {"CON", "14"}, {"INT", "10"}, {"WIS", "13"}, {"CHA", "8"}, {"AC", "17"}, {"HPMax", "28"},
                {"HPCurrent", "20"}, {"Speed", "9 m"}};
        CharacterImportResponse result = importService.importCharacter(
                campaign.getId(), "kael.pdf", fillablePdf(fields), master);

        assertTrue(result.format().startsWith("Fillable PDF"));
        CharacterSheetResponse sheet = characterService.getCharacterSheet(result.characterId(), master);
        assertEquals("Kael", sheet.name());
        assertEquals(3, sheet.combat().totalLevel());
        assertEquals(13, sheet.attributes().wisdom());
        assertEquals(17, sheet.combat().armorClass());
        assertEquals(28, sheet.combat().hpMax());
        assertEquals(20, sheet.combat().hpCurrent());
        assertEquals(30, sheet.combat().speedFt(), "9 m = 30 pes");
    }

    @Test
    void rejectsUnknownFilesAndOutsiders() {
        assertThrows(ResponseStatusException.class, () -> importService.importCharacter(
                campaign.getId(), "x.json", "{\"foo\": 1}".getBytes(StandardCharsets.UTF_8), master));
        assertThrows(ResponseStatusException.class, () -> importService.importCharacter(
                campaign.getId(), "x.txt", "isso nao e ficha".getBytes(StandardCharsets.UTF_8), master));

        User outsider = userRepository.save(new User("outsider-" + UUID.randomUUID(), "password"));
        String ddb = "{\"name\":\"X\",\"stats\":[{\"id\":1,\"value\":10}],\"classes\":[]}";
        assertThrows(AccessDeniedException.class, () -> importService.importCharacter(
                campaign.getId(), "x.json", ddb.getBytes(StandardCharsets.UTF_8), outsider.getName()));
    }

    private static byte[] fillablePdf(String[][] fields) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");
            int y = 760;
            for (String[] kv : fields) {
                PDTextField field = new PDTextField(form);
                field.setPartialName(kv[0]);
                PDAnnotationWidget widget = field.getWidgets().getFirst();
                widget.setRectangle(new PDRectangle(40, y, 200, 14));
                widget.setPage(page);
                page.getAnnotations().add(widget);
                form.getFields().add(field);
                field.setValue(kv[1]);
                y -= 18;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
