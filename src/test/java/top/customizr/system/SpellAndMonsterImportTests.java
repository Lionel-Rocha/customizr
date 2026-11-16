package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.dto.MonsterResponse;
import top.customizr.system.dto.NpcResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Components;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.DurationUnit;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.Time;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.MonsterService;
import top.customizr.system.service.NpcService;
import top.customizr.system.service.SpellImportService;

import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Magias e monstros no formato do 5etools (amostras do SRD 5.2, CC-BY-4.0). */
@SpringBootTest
@Transactional
class SpellAndMonsterImportTests {

    @Autowired private SpellImportService spellImportService;
    @Autowired private MonsterService monsterService;
    @Autowired private NpcService npcService;
    @Autowired private SpellRepository spellRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private ObjectMapper objectMapper;

    private Source xphb;

    @BeforeEach
    void setUp() {
        xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) {
            sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        }
    }

    @Test
    void importsSpellsAndKeepsWhatDoesNotFitInTheDescription() throws Exception {
        DndClass wizard = dndClassRepository.findByClassNameAndSourceCode("Wizard", "XPHB")
                .orElseGet(() -> dndClassRepository.save(new DndClass("Wizard", 6, 3, xphb)));
        JsonNode file = json("/importer/spells-srd52-amostra.json");
        ((tools.jackson.databind.node.ObjectNode) file.path("spell").get(0))
                .putObject("classes").putArray("fromClassList").addObject().put("name", "Wizard").put("source", "XPHB");

        ContentImportResponse result = spellImportService.importSpells(file);
        assertEquals(7, result.created().size(), result.skipped().toString());

        Spell fireball = spell("Fireball");
        assertEquals(SpellLevel.THIRD, fireball.getLevel());
        assertEquals(SpellSchool.Evocation, fireball.getSchool());
        assertEquals(150, fireball.getRangeFt());
        assertTrue(fireball.getComponents().contains(Components.Material));
        assertTrue(fireball.getDescription().startsWith("Material: a ball of bat guano and sulfur."));
        assertEquals(DurationUnit.INSTANTANEOUS, fireball.getDuration().getUnit());
        assertTrue(wizard.getAvailableSpells().contains(fireball), "fromClassList liga a magia a classe");

        Spell alarm = spell("Alarm");
        assertEquals(Time.OneMinute, alarm.getCastingTime());
        assertEquals(8, alarm.getDuration().getAmount());
        assertEquals(DurationUnit.HOUR, alarm.getDuration().getUnit());
        assertTrue(alarm.getDescription().contains("Ritual."));

        assertTrue(spell("Burning Hands").getDescription().startsWith("Range: Self (15-foot cone)."));
        assertEquals(Time.Reaction, spell("Counterspell").getCastingTime());
        assertTrue(spell("Counterspell").getDescription().startsWith("Reaction trigger: which you take when"));
        Spell clairvoyance = spell("Clairvoyance");
        assertEquals(Time.TenMinutes, clairvoyance.getCastingTime());
        assertEquals(5280, clairvoyance.getRangeFt(), "1 milha");
        assertTrue(clairvoyance.isConcentration());
        assertEquals(DurationUnit.UNTIL_DISPELLED, spell("Arcane Lock").getDuration().getUnit());

        ContentImportResponse again = spellImportService.importSpells(json("/importer/spells-srd52-amostra.json"));
        assertEquals(7, again.updated().size(), "importar de novo atualiza");
    }

    @Test
    void linksSpellsToClassesFromTheLookupFile() throws Exception {
        DndClass wizard = dndClassRepository.findByClassNameAndSourceCode("Wizard", "XPHB")
                .orElseGet(() -> dndClassRepository.save(new DndClass("Wizard", 6, 3, xphb)));
        spellImportService.importSpells(json("/importer/spells-srd52-amostra.json"));
        JsonNode lookup = objectMapper.readTree("""
                {"xphb": {"acid splash": {"class": {"XPHB": {"Sorcerer": true, "Wizard": true}}},
                          "spell that does not exist": {"class": {"XPHB": {"Wizard": true}}}}}""");

        assertEquals(1, spellImportService.importClassLists(lookup), "so o Wizard existe; magia inexistente ignorada");
        assertTrue(wizard.getAvailableSpells().contains(spell("Acid Splash")));
        assertEquals(0, spellImportService.importClassLists(lookup), "nao liga duas vezes");
    }

    @Test
    void importsMonstersWithReadableStatBlocks() throws Exception {
        ContentImportResponse result = monsterService.importBestiary(json("/importer/bestiary-srd52-amostra.json"));
        assertEquals(5, result.created().size(), result.skipped().toString());

        MonsterResponse dragon = monsterService.read(monsterService.search("red dragon", null, null).getFirst().id());
        assertEquals("Huge", dragon.size());
        assertEquals("Dragon (chromatic)", dragon.creatureType());
        assertEquals(19, dragon.armorClass());
        assertEquals(256, dragon.hpAverage());
        assertEquals("19d12 + 133", dragon.hpFormula());
        assertEquals("40 ft., climb 40 ft., fly 80 ft.", dragon.speed());
        assertEquals("17", dragon.challengeRating());
        assertEquals(18000, dragon.experiencePoints());
        assertEquals(6, dragon.proficiencyBonus());
        assertEquals("DEX +6, WIS +7", dragon.savingThrows());
        assertEquals("Fire", dragon.damageImmunities());
        assertEquals("Blindsight 60 ft., Darkvision 120 ft.", dragon.senses());
        assertTrue(dragon.legendaryActions() != null && !dragon.legendaryActions().contains("{@"));
        assertTrue(dragon.actions().contains("Multiattack."));
        assertTrue(dragon.actions().contains("Melee Attack Roll: +"), "marcacoes de 2024 viram texto");
        assertTrue(dragon.actions().contains("Dexterity Saving Throw:") && dragon.actions().contains("Failure:"));

        MonsterResponse goblin = monsterService.read(monsterService.search("goblin", null, null).getFirst().id());
        assertEquals("Fey (goblinoid)", goblin.creatureType());
        assertEquals("Chaotic Neutral", goblin.alignment());
        assertEquals("1/4", goblin.challengeRating());
        assertEquals(50, goblin.experiencePoints());

        MonsterResponse mage = monsterService.read(monsterService.search("mage", null, null).getFirst().id());
        assertTrue(mage.spellcasting() != null && mage.spellcasting().contains("At will:"));

        assertEquals(2, monsterService.search(null, 0.0, 1.0).size(), "goblin (1/4) e zumbi (1/4)");
        assertThrows(ResponseStatusException.class, () -> monsterService.importBestiary(objectMapper.readTree("{\"x\":1}")));
    }

    @Test
    void npcCanUseAMonsterStatBlockAndOnlyTheMasterChangesIt() throws Exception {
        monsterService.importBestiary(json("/importer/bestiary-srd52-amostra.json"));
        UUID captain = monsterService.search("bandit captain", null, null).getFirst().id();
        String master = "master-" + UUID.randomUUID();
        String player = "player-" + UUID.randomUUID();
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User playerUser = userRepository.save(new User(player, "p"));
        membershipRepository.save(new Membership(campaign.getId(), playerUser.getUserId(), Role.PLAYER));
        NpcResponse npc = npcService.create(campaign.getId(), "Vex", "Sarcastic", "Works for the duke", null, master);

        NpcResponse withSheet = npcService.useStatBlock(npc.id(), captain, master);
        assertEquals("Bandit Captain", withSheet.statBlockName());
        assertEquals(captain, npcService.read(npc.id(), player).statBlockId());
        assertThrows(AccessDeniedException.class, () -> npcService.useStatBlock(npc.id(), null, player));
        assertNull(npcService.useStatBlock(npc.id(), null, master).statBlockId());
    }

    private Spell spell(String name) {
        return spellRepository.findByNameAndSource_Id(name, xphb.getId()).orElseThrow();
    }

    private JsonNode json(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return objectMapper.readTree(in.readAllBytes());
        }
    }
}
