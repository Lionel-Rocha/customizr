package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.OtherProficienciesRequest;
import top.customizr.system.dto.ProficienciesRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.dto.SuggestedProficienciesResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Background;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Source;
import top.customizr.system.model.Species;
import top.customizr.system.model.User;
import top.customizr.system.repository.BackgroundRepository;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idiomas, ferramentas, armas e armaduras da ficha; sugestoes do 5etools; teste de ferramenta; passivas. */
@SpringBootTest
@Transactional
class OtherProficienciesTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private BackgroundRepository backgroundRepository;
    @Autowired private DndClassRepository dndClassRepository;

    private String master;
    private UUID hero;
    private Source source;

    @BeforeEach
    void setUp() {
        master = "m-" + UUID.randomUUID();
        Campaign c = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        hero = characterService.create(new CreateCharacterRequest(c.getId(), "Hero"), master).getCharacterId();
        characterService.updateAttributes(hero, new UpdateAttributesRequest(10, 16, 10, 12, 14, 10), master);
        source = sourceRepository.save(new Source("Prof tests", "PF" + UUID.randomUUID().toString().substring(0, 6)));
    }

    @Test
    void listsAreSavedWithoutRepeatsAndNullKeepsTheList() {
        var sheet = sheetService.updateOtherProficiencies(hero, new OtherProficienciesRequest(
                List.of("Common", " elvish ", "common", ""), List.of("Thieves' Tools"), List.of("Simple weapons"), List.of()), master);
        assertEquals(List.of("Common", "elvish"), sheet.details().languages(), "repetido (sem maiuscula) entra uma vez");
        assertEquals(List.of("Thieves' Tools"), sheet.details().toolProficiencies());

        sheet = sheetService.updateOtherProficiencies(hero, new OtherProficienciesRequest(null, List.of(), null, List.of("Shields")), master);
        assertEquals(List.of("Common", "elvish"), sheet.details().languages(), "nula = fica como estava");
        assertTrue(sheet.details().toolProficiencies().isEmpty(), "vazia = limpa");
        assertEquals(List.of("Simple weapons"), sheet.details().weaponProficiencies());
        assertEquals(List.of("Shields"), sheet.details().armorProficiencies());

        assertThrows(ResponseStatusException.class, () -> sheetService.updateOtherProficiencies(hero,
                new OtherProficienciesRequest(List.of("x".repeat(81)), null, null, null), master));
    }

    @Test
    void toolCheckAddsProficiencyOnlyWithTheTool() {
        sheetService.updateOtherProficiencies(hero, new OtherProficienciesRequest(null, List.of("Thieves' Tools"), null, null), master);
        SheetRollResponse withTool = sheetService.roll(hero, new SheetRollRequest(SheetRollRequest.RollKind.TOOL, null,
                AbilityScore.DEXTERITY, null, false, null, "thieves' tools"), master);
        assertTrue(withTool.roll().expression().endsWith("+5"), "DES +3 + proficiencia 2: " + withTool.roll().expression());
        SheetRollResponse without = sheetService.roll(hero, new SheetRollRequest(SheetRollRequest.RollKind.TOOL, null,
                AbilityScore.DEXTERITY, null, false, null, "Lute"), master);
        assertTrue(without.roll().expression().endsWith("+3"));
        assertTrue(without.notes().contains("Sem proficiência com Lute"));

        var details = sheetService.updateProficiencies(hero, new ProficienciesRequest(Set.of(Skill.INSIGHT), null, null), master).details();
        assertEquals(10 + 2 + 2, details.passiveInsight(), "SAB +2 + proficiencia");
        assertEquals(10 + 1, details.passiveInvestigation(), "INT +1");
    }

    @Test
    void suggestionsComeFromSpeciesBackgroundAndClasses() {
        Species elf = new Species("Elf-" + UUID.randomUUID(), source);
        elf.updateSourceData(null, null, null, Map.of("languageProficiencies",
                List.of(Map.of("common", true, "elvish", true, "anyStandard", 1))));
        speciesRepository.save(elf);
        Background criminal = new Background("Criminal-" + UUID.randomUUID(), source);
        criminal.updateSourceData(null, null, null, Map.of("toolProficiencies", List.of(Map.of("thieves' tools", true))));
        backgroundRepository.save(criminal);
        DndClass fighter = dndClassRepository.save(new DndClass("Fighter", source.getCode(), 1, 10, 3, source, "one",
                null, null, null, null, null, null, Map.of("class", Map.of(
                        "startingProficiencies", Map.of("armor", List.of("light", "heavy", "{@item shield|phb|shields}"),
                                "weapons", List.of("simple", "martial")),
                        "multiclassing", Map.of("proficienciesGained", Map.of("armor", List.of("light")))))));
        DndClass rogue = dndClassRepository.save(new DndClass("Rogue", source.getCode(), 1, 8, 3, source, "one",
                null, null, null, null, null, null, Map.of("class", Map.of(
                        "startingProficiencies", Map.of("armor", List.of("light"), "tools", List.of("{@item thieves' tools|xphb}")),
                        "multiclassing", Map.of("proficienciesGained", Map.of("armor", List.of("light"),
                                "tools", List.of("{@item thieves' tools|xphb}")))))));
        sheetService.chooseOrigin(hero, new OriginRequest(elf.getId(), criminal.getId()), master);
        characterService.addClass(hero, new AddCharacterClassRequest(fighter.getClassId(), 1), master);
        characterService.addClass(hero, new AddCharacterClassRequest(rogue.getClassId(), 1), master);

        SuggestedProficienciesResponse s = sheetService.suggestedProficiencies(hero, master);
        assertTrue(s.languages().containsAll(List.of("Common", "Elvish")), s.languages().toString());
        assertEquals(List.of("Thieves' Tools"), s.tools(), "antecedente + multiclasse de Ladino, sem repetir");
        assertEquals(List.of("Simple weapons", "Martial weapons"), s.weapons());
        assertEquals(List.of("Light armor", "Heavy armor", "Shields"), s.armor(), "Guerreiro e a primeira classe");
        assertTrue(s.choices().stream().anyMatch(c -> c.contains("choose 1 language (any standard)")), s.choices().toString());
    }
}
