package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.AddFeatRequest;
import top.customizr.system.dto.CharacterFeatureResponse;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.ShareRequest;
import top.customizr.system.dto.SharedKind;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.AbilityScoreGenerator;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.ChatMessageType;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Monster;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterFeatureService;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.ContentImportService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lista de features da ficha, mandar pro chat e geracao de atributos. */
@SpringBootTest
@Transactional
class FeaturesAndAbilityScoresTests {

    @Autowired private CharacterFeatureService featureService;
    @Autowired private CharacterService characterService;
    @Autowired private ContentImportService contentImportService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private MonsterRepository monsterRepository;
    @Autowired private ObjectMapper objectMapper;

    private String master;
    private String player;
    private Campaign campaign;
    private Character hero;

    @BeforeEach
    void setUp() {
        String s = UUID.randomUUID().toString().substring(0, 6);
        master = "m-" + s;
        player = "p-" + s;
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "x"))));
        User p = userRepository.save(new User(player, "x"));
        membershipRepository.save(new Membership(campaign.getId(), p.getUserId(), Role.PLAYER));
        hero = characterService.create(new CreateCharacterRequest(campaign.getId(), "Hero"), player);
    }

    // ------------------------------------------------------------------ atributos

    @Test
    void rollsAbilityScoresDroppingTheLowestDice() {
        AtomicInteger i = new AtomicInteger();
        int[] dice = {1, 6, 4, 5, /* reroll do 1: */ 3};
        var rolled = AbilityScoreGenerator.roll(4, 6, 1, 1, 0, sides -> dice[i.getAndIncrement()]);
        assertEquals(15, rolled.getFirst().total(), "6 + 5 + 4, tirando o 1");
        assertEquals(List.of(1), rolled.getFirst().dropped());

        i.set(0);
        var rerolled = AbilityScoreGenerator.roll(4, 6, 1, 1, 1, sides -> dice[i.getAndIncrement()]);
        assertEquals(List.of(3), rerolled.getFirst().dropped(), "o 1 foi rolado de novo e virou 3");
        assertEquals(15, rerolled.getFirst().total());

        assertEquals(6, AbilityScoreGenerator.roll(3, 6, 0, 6, 0, sides -> 2).size(), "3d6 direto, seis valores");
        assertThrows(IllegalArgumentException.class, () -> AbilityScoreGenerator.roll(4, 6, 4, 6, 0, sides -> 1));
    }

    @Test
    void pointBuyFollowsTheTwentySevenPointTable() {
        Map<AbilityScore, Integer> scores = new EnumMap<>(AbilityScore.class);
        scores.put(AbilityScore.STRENGTH, 15);
        scores.put(AbilityScore.DEXTERITY, 14);
        scores.put(AbilityScore.CONSTITUTION, 13);
        scores.put(AbilityScore.INTELLIGENCE, 12);
        scores.put(AbilityScore.WISDOM, 10);
        scores.put(AbilityScore.CHARISMA, 8);
        var standard = AbilityScoreGenerator.pointBuy(scores, null);
        assertEquals(27, standard.cost(), "o array padrao custa exatamente 27");
        assertTrue(standard.valid());

        scores.put(AbilityScore.CHARISMA, 15);
        var tooMuch = AbilityScoreGenerator.pointBuy(scores, null);
        assertFalse(tooMuch.valid());
        assertEquals(-9, tooMuch.remaining());
        scores.put(AbilityScore.CHARISMA, 16);
        assertTrue(AbilityScoreGenerator.pointBuy(scores, null).problems().stream().anyMatch(p -> p.contains("between 8 and 15")));
    }

    // ------------------------------------------------------------------ features

    @Test
    void listsGrantedAndAvailableFeaturesAndChoicesCanBeAdded() throws Exception {
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        Feature style = featureRepository.save(new Feature("Fighting Style", "[\"Choose a {@filter style|optionalfeatures}.\"]",
                "class", FeatureType.CHOICE, xphb));
        Feature extra = featureRepository.save(new Feature("Extra Attack", "[\"You can attack {@b twice} instead of once.\"]",
                "class", FeatureType.AUTOMATIC, xphb));
        DndClass fighter = new DndClass("Fighter-" + UUID.randomUUID(), 10, 3, xphb);
        fighter.addFeature(style, 1, "fighting-style");
        fighter.addFeature(extra, 5);
        dndClassRepository.save(fighter);
        var sheet = characterService.addClass(hero.getCharacterId(), new AddCharacterClassRequest(fighter.getClassId(), 4), player);
        UUID ccId = sheet.classes().getFirst().characterClassId();

        List<CharacterFeatureResponse> level4 = featureService.list(hero.getCharacterId(), true, player);
        assertTrue(level4.stream().noneMatch(f -> f.name().equals("Extra Attack")), "Extra Attack so no nivel 5");
        CharacterFeatureResponse offer = level4.stream().filter(f -> f.name().equals("Fighting Style")).findFirst().orElseThrow();
        assertEquals("AVAILABLE", offer.status());
        assertEquals(ccId, offer.characterClassId());

        // adicionar a escolha usa a rota que ja existia (CharacterService.addFeat)
        characterService.addFeat(hero.getCharacterId(), ccId, new AddFeatRequest(offer.refId(), 1), player);
        hero.getSheet().getClasses().getFirst().setLevel(5);
        List<CharacterFeatureResponse> level5 = featureService.list(hero.getCharacterId(), true, player);
        assertEquals("SELECTED", level5.stream().filter(f -> f.name().equals("Fighting Style")).findFirst().orElseThrow().status());
        CharacterFeatureResponse extraRow = level5.stream().filter(f -> f.name().equals("Extra Attack")).findFirst().orElseThrow();
        assertEquals("GRANTED", extraRow.status());
        assertEquals("You can attack twice instead of once.", extraRow.text(), "texto renderizado a partir da descricao crua");

        ChatMessageResponse card = featureService.shareFromCharacter(hero.getCharacterId(),
                new ShareRequest(SharedKind.FEATURE, extraRow.refId(), null), player);
        assertEquals(ChatMessageType.CARD, card.type());
        assertEquals("Hero", card.speakerName());
        assertTrue(card.content().startsWith("Extra Attack\nYou can attack twice"));
    }

    @Test
    void speciesTraitsBecomeFeaturesLikeClassFeatures() throws Exception {
        String elf = "Elf-" + UUID.randomUUID().toString().substring(0, 6);
        contentImportService.importSpecies(objectMapper.readTree("""
                {"race": [{"name": "%s", "source": "XPHB", "size": ["M"], "speed": 30, "entries": [
                  "Elves are magical people.",
                  {"type": "entries", "name": "Darkvision", "entries": ["You have {@sense Darkvision|XPHB} 60 ft."]},
                  {"type": "entries", "name": "Elven Lineage", "entries": [{"type": "options", "entries": ["Drow", "High Elf"]}]}
                ]}]}""".formatted(elf)));
        var species = speciesRepository.findByNameContainingIgnoreCaseOrderByName(elf).getFirst();
        assertEquals(List.of("Darkvision", "Elven Lineage"), species.getTraits().stream().map(Feature::getName).toList());
        assertEquals("species", species.getTraits().getFirst().getCategory());
        assertEquals(FeatureType.CHOICE, species.getTraits().get(1).getType(), "tem opcoes = CHOICE, como no ClassService");
        assertTrue(species.getTraits().getFirst().getDescription().contains("{@sense Darkvision|XPHB}"), "descricao crua com tags");

        hero.chooseOrigin(species, null);
        List<CharacterFeatureResponse> rows = featureService.list(hero.getCharacterId(), true, player);
        CharacterFeatureResponse dark = rows.stream().filter(r -> r.name().equals("Darkvision")).findFirst().orElseThrow();
        assertEquals("Species: " + elf, dark.origin());
        assertEquals("You have Darkvision 60 ft.", dark.text());
    }

    @Test
    void onlyTheMasterShowsAMonsterInTheChat() {
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        Monster goblin = new Monster("Goblin-" + UUID.randomUUID(), xphb);
        goblin.updateCore("Small", "Fey (goblinoid)", "Chaotic Neutral", 15, null, 10, "3d6", "30 ft.",
                new int[]{8, 15, 10, 10, 8, 8}, "1/4");
        monsterRepository.save(goblin);

        assertThrows(AccessDeniedException.class, () -> featureService.share(campaign.getId(),
                new ShareRequest(SharedKind.MONSTER, goblin.getId(), null), player));
        ChatMessageResponse shown = featureService.share(campaign.getId(),
                new ShareRequest(SharedKind.MONSTER, goblin.getId(), null), master);
        assertTrue(shown.content().contains("AC 15 | HP 10 (3d6)"));
        assertThrows(ResponseStatusException.class, () -> featureService.share(campaign.getId(),
                new ShareRequest(SharedKind.SPELL, UUID.randomUUID(), null), master));
    }
}
