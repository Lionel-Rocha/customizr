package top.customizr.system;

import jakarta.transaction.Transactional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.CharacterStoryRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.CustomResourceRequest;
import top.customizr.system.dto.OtherProficienciesRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Currency;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.InventoryService;
import top.customizr.system.service.SheetPdfService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ficha em PDF: o texto sai certo (acentos, sem as notas do mestre), quebra pagina e respeita quem pode ver. */
@SpringBootTest
@Transactional
class SheetPdfTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private InventoryService inventoryService;
    @Autowired private SheetPdfService pdfService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private DndClassRepository dndClassRepository;

    @Test
    void pdfHasTheWholeSheetInPortugueseWithoutTheMasterSecret() throws Exception {
        String master = "master-" + UUID.randomUUID();
        Campaign c = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        Source source = sourceRepository.save(new Source("Pdf tests", "PD" + UUID.randomUUID().toString().substring(0, 6)));
        DndClass fighter = dndClassRepository.save(new DndClass("Fighter", source.getCode(), 1, 10, 3, source, "one",
                null, null, null, null, null, null, null));
        var created = characterService.create(new CreateCharacterRequest(c.getId(), "Aria Ventoleste"), master);
        UUID hero = created.getCharacterId();
        characterService.updateAttributes(hero, new UpdateAttributesRequest(16, 12, 14, 10, 10, 8), master);
        characterService.addClass(hero, new AddCharacterClassRequest(fighter.getClassId(), 4), master);
        characterService.updateCombatStats(hero, new UpdateCombatStatsRequest(36, 30, null, 18, 30), master);
        sheetService.addAttack(hero, new AttackRequest("Espada longa", 5, "1d8+3", "cortante", null), master);
        sheetService.addCustomResource(hero, new CustomResourceRequest("Varinha", 7, null, null), master);
        sheetService.updateOtherProficiencies(hero, new OtherProficienciesRequest(List.of("Common", "Elvish"), null, null, null), master);
        var inventory = inventoryService.getOrCreateCharacterInventory(created);
        inventoryService.changeCoins(created, inventory.getId(), Currency.SILVER, 37);
        sheetService.updateStory(hero, new CharacterStoryRequest("Caótica e Boa", "27", null, null, null, null, null,
                "Cicatriz no olho 👁 esquerdo → capa verde", null, null, null, null,
                "Criada por lobos. ".repeat(400), null, null, "Herdeira perdida"), master);

        byte[] pdf = pdfService.render(hero, master);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2, "historia longa vira mais paginas");
            String text = new PDFTextStripper().getText(doc);
            for (String expected : List.of("Aria Ventoleste", "Fighter 4", "Caótica e Boa", "TENDÊNCIA", "FORÇA", "Salvaguarda", "Atletismo", "PV ATUAL", "TESTES CONTRA A MORTE", "d10 4/4",
                    "Espada longa", "Retomar o Fôlego", "Varinha", "Idiomas: Common, Elvish", "37 pp",
                    "Cicatriz no olho ? esquerdo -> capa verde", "Criada por lobos.", "página 2")) {
                assertTrue(text.contains(expected), "faltou \"" + expected + "\"");
            }
            assertFalse(text.contains("Herdeira perdida"), "as notas do mestre nao vao pro PDF");
        }
        assertEquals("Aria Ventoleste.pdf", pdfService.fileName(hero));

        String stranger = "stranger-" + UUID.randomUUID();
        userRepository.save(new User(stranger, "p"));
        assertThrows(AccessDeniedException.class, () -> pdfService.render(hero, stranger));
    }
}
