package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.InventoryResponse;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Currency;
import top.customizr.system.model.Source;
import top.customizr.system.model.Species;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.InventoryService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Moedas separadas na bolsa (receber, gastar com troco, cambio) e capacidade de carga. */
@SpringBootTest
@Transactional
class CoinPurseAndCarryingTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private InventoryService inventoryService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SpeciesRepository speciesRepository;

    @Test
    void coinsStaySeparateAndPayingGivesChange() {
        Character hero = hero();
        var inventory = inventoryService.getOrCreateCharacterInventory(hero);
        UUID id = inventory.getId();

        inventoryService.changeCoins(hero, id, Currency.SILVER, 37);
        inventoryService.changeCoins(hero, id, Currency.GOLD, 5);
        assertEquals(coins(0, 5, 0, 37, 0), coins(hero, id), "37 pp ficam 37 pp (nao viram 3,7 po)");
        assertEquals(0, new BigDecimal("8.70").compareTo(inventoryService.read(hero, id).getMoneyGp()), "moneyGp = valor da bolsa");

        // loja/espolio seguem em po: pagar 1,5 po usa a prata primeiro
        inventoryService.changeMoney(hero, id, new BigDecimal("-1.5"));
        assertEquals(coins(0, 5, 0, 22, 0), coins(hero, id));

        // tirar 1 pl sem ter platina paga 10 po pelo valor... sem dinheiro bastante, erro
        assertThrows(ResponseStatusException.class, () -> inventoryService.changeCoins(hero, id, Currency.PLATINUM, -1));
        inventoryService.changeCoins(hero, id, Currency.GOLD, 10);
        inventoryService.changeCoins(hero, id, Currency.PLATINUM, -1);
        assertEquals(0, new BigDecimal("7.20").compareTo(inventoryService.read(hero, id).getMoneyGp()));

        // cambio: 20 pp -> 2 po; 25 pp nao fecha em po
        inventoryService.changeCoins(hero, id, Currency.SILVER, 30);
        long goldBefore = coins(hero, id).get("gp");
        inventoryService.exchangeCoins(hero, id, Currency.SILVER, Currency.GOLD, 20);
        assertEquals(goldBefore + 2, coins(hero, id).get("gp"));
        assertThrows(ResponseStatusException.class, () -> inventoryService.exchangeCoins(hero, id, Currency.SILVER, Currency.GOLD, 5));
        inventoryService.exchangeCoins(hero, id, Currency.GOLD, Currency.COPPER, 1);
        assertEquals(100, coins(hero, id).get("cp"));

        // receber em po sem moeda: entra quebrado em po/pp/pc
        inventoryService.changeMoney(hero, id, new BigDecimal("1.23"));
        assertEquals(103, coins(hero, id).get("cp"));
    }

    @Test
    void carryingCapacityFollowsStrengthAndSize() {
        Character hero = hero();
        assertNull(characterService.getCharacterSheet(hero.getCharacterId(), hero.getOwner().getName())
                .details().carryingCapacityLb(), "sem atributos");
        var sheet = characterService.updateAttributes(hero.getCharacterId(), new UpdateAttributesRequest(16, 10, 10, 10, 10, 10),
                hero.getOwner().getName());
        assertEquals(240, sheet.details().carryingCapacityLb(), "FOR 16 x 15");
        assertEquals(480, sheet.details().pushDragLiftLb());

        Source source = sourceRepository.save(new Source("Carry tests", "CT" + UUID.randomUUID().toString().substring(0, 6)));
        Species giant = new Species("Big-" + UUID.randomUUID(), source);
        giant.update("Large", 35, null);
        speciesRepository.save(giant);
        var large = sheetService.chooseOrigin(hero.getCharacterId(), new OriginRequest(giant.getId(), null), hero.getOwner().getName());
        assertEquals(480, large.details().carryingCapacityLb(), "Grande dobra");
    }

    private Character hero() {
        String master = "m-" + UUID.randomUUID();
        Campaign c = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        return characterService.create(new CreateCharacterRequest(c.getId(), "Hero"), master);
    }

    private Map<String, Long> coins(Character hero, UUID inventoryId) {
        return InventoryResponse.from(inventoryService.read(hero, inventoryId)).coins();
    }

    private static Map<String, Long> coins(long pp, long gp, long ep, long sp, long cp) {
        return Map.of("pp", pp, "gp", gp, "ep", ep, "sp", sp, "cp", cp);
    }
}
