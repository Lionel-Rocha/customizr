package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CharacterResponse;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.CreatePartyRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.HitPointChangeRequest.HitPointChangeType;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.dto.PartyResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.InventoryService;
import top.customizr.system.service.PartyService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PV/CA e valores derivados da ficha, limite da party e inventario pelo personagem do usuario. */
@SpringBootTest
@Transactional
class SheetCombatAndPartyTests {

    @Autowired private CharacterService characterService;
    @Autowired private PartyService partyService;
    @Autowired private InventoryService inventoryService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private SourceRepository sourceRepository;

    private String master;
    private String player;
    private Campaign campaign;
    private CharacterResponse character;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        User masterUser = userRepository.save(new User(master, "password"));
        User playerUser = userRepository.save(new User(player, "password"));
        campaign = campaignRepository.save(new Campaign("Campaign", masterUser));
        membershipRepository.save(new Membership(campaign.getId(), playerUser.getUserId(), Role.PLAYER));
        character = CharacterResponse.from(characterService.create(
                new CreateCharacterRequest(campaign.getId(), "Aria"), player));
    }

    @Test
    void intelligenceAndWisdomAreSavedInTheRightPlace() {
        CharacterSheetResponse sheet = characterService.updateAttributes(
                character.characterId(), new UpdateAttributesRequest(8, 14, 12, 16, 10, 13), player);

        assertEquals(16, sheet.attributes().intelligence());
        assertEquals(10, sheet.attributes().wisdom());
    }

    @Test
    void modifiersFollowTheFifthEditionTable() {
        Attributes attributes = new Attributes(8, 9, 10, 15, 20, 1);

        assertEquals(-1, attributes.getModifier("strength"));
        assertEquals(-1, attributes.getModifier("DEX"));
        assertEquals(0, attributes.getModifier("con"));
        assertEquals(2, attributes.getModifier("INT"));
        assertEquals(5, attributes.getModifier("sab"));
        assertEquals(-5, attributes.getModifier("Carisma"));
        assertThrows(IllegalArgumentException.class, () -> attributes.getModifier("luck"));
    }

    @Test
    void derivedValuesComeFromClassesAndAttributes() {
        DndClass fighter = dndClassRepository.save(new DndClass("Fighter", 10, 3, null));
        characterService.updateAttributes(character.characterId(), new UpdateAttributesRequest(16, 14, 14, 10, 12, 8), player);
        CharacterSheetResponse sheet = characterService.addClass(
                character.characterId(), new AddCharacterClassRequest(fighter.getClassId(), 5), player);

        var combat = sheet.combat();
        assertEquals(5, combat.totalLevel());
        assertEquals(3, combat.proficiencyBonus());
        assertEquals(2, combat.initiative());
        assertEquals(11, combat.passivePerception());
        // nivel 1: 10 + 2; niveis 2-5: (5 + 1 + 2) x 4
        assertEquals(44, combat.suggestedHpMax());
        assertNull(combat.hpMax(), "PV so existe depois que alguem preencher");
    }

    @Test
    void damageSpendsTemporaryHitPointsFirstAndHealingStopsAtMax() {
        characterService.updateCombatStats(character.characterId(),
                new UpdateCombatStatsRequest(30, null, null, 16, 30), player);
        characterService.changeHitPoints(character.characterId(),
                new HitPointChangeRequest(HitPointChangeType.TEMPORARY, 5), player);

        var hit = characterService.changeHitPoints(character.characterId(),
                new HitPointChangeRequest(HitPointChangeType.DAMAGE, 8), player).combat();
        assertEquals(0, hit.hpTemp());
        assertEquals(27, hit.hpCurrent());
        assertEquals(16, hit.armorClass());

        var healed = characterService.changeHitPoints(character.characterId(),
                new HitPointChangeRequest(HitPointChangeType.HEALING, 50), player).combat();
        assertEquals(30, healed.hpCurrent());

        var down = characterService.changeHitPoints(character.characterId(),
                new HitPointChangeRequest(HitPointChangeType.DAMAGE, 99), player).combat();
        assertEquals(0, down.hpCurrent());

        assertThrows(ResponseStatusException.class, () -> characterService.updateCombatStats(character.characterId(),
                new UpdateCombatStatsRequest(10, 20, null, null, null), player), "PV atual maior que o maximo");
    }

    @Test
    void partyRespectsMaxSizeAndPlayersCanReadIt() {
        PartyResponse party = partyService.create(campaign.getId(), new CreatePartyRequest("Duo", 1), master);
        partyService.addCharacter(party.partyId(), character.characterId(), master);
        CharacterResponse second = CharacterResponse.from(characterService.create(
                new CreateCharacterRequest(campaign.getId(), "Bram"), player));

        assertThrows(ResponseStatusException.class,
                () -> partyService.addCharacter(party.partyId(), second.characterId(), master));

        PartyResponse read = partyService.read(party.partyId(), player);
        assertEquals(1, read.maxSize());
        assertEquals("Aria", read.members().getFirst().name());
        assertEquals(player, read.members().getFirst().ownerName());
        assertEquals(1, partyService.listByCampaign(campaign.getId(), player).size());

        assertThrows(ResponseStatusException.class, () -> partyService.changeMaxSize(party.partyId(), 0, master));
        assertEquals(3, partyService.changeMaxSize(party.partyId(), 3, master).maxSize());

        User outsider = userRepository.save(new User("outsider-" + UUID.randomUUID(), "password"));
        assertThrows(AccessDeniedException.class, () -> partyService.read(party.partyId(), outsider.getName()));
    }

    @Test
    void inventoryIsUsedThroughTheLoggedUsersCharacter() {
        Source source = sourceRepository.save(new Source("Test Source " + UUID.randomUUID(), "T" + UUID.randomUUID().toString().substring(0, 6)));
        Item rope = itemRepository.save(new Item("Rope", source, ItemType.MUNDANE, null, null, Rarity.COMMON, new BigDecimal("1.00")));

        Character actor = inventoryService.actorFor(character.characterId(), player);
        var inventory = inventoryService.getOrCreateCharacterInventory(actor);
        inventoryService.addItem(actor, inventory.getId(), rope.getId(), 2);
        inventoryService.changeMoney(actor, inventory.getId(), new BigDecimal("15"));

        InventoryResponse response = InventoryResponse.from(inventoryService.read(actor, inventory.getId()));
        assertEquals(InventoryResponse.OwnerType.CHARACTER, response.ownerType());
        assertEquals("Rope", response.entries().getFirst().name());
        assertEquals(2, response.entries().getFirst().quantity());
        assertEquals(0, new BigDecimal("15").compareTo(response.moneyGp()));

        User outsider = userRepository.save(new User("outsider-" + UUID.randomUUID(), "password"));
        assertThrows(AccessDeniedException.class, () -> inventoryService.actorFor(character.characterId(), outsider.getName()));
        assertEquals(character.characterId(), inventoryService.actorFor(character.characterId(), master).getCharacterId(),
                "o mestre pode agir pelo personagem");
    }
}
