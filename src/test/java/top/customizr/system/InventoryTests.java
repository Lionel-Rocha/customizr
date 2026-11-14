package top.customizr.system;

import org.junit.jupiter.api.Test;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Inventory;
import top.customizr.system.model.Item;
import top.customizr.system.model.ItemType;
import top.customizr.system.model.Rarity;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryTests {

    @Test
    void tracksItemQuantitiesAndMoney() {
        User owner = new User("owner", "password");
        Character character = new Character("Aria", new Campaign("Campaign", owner), owner);
        Item potion = new Item(
                "Potion",
                new Source("Player's Handbook", "PHB"),
                ItemType.MUNDANE,
                null,
                null,
                Rarity.COMMON,
                new BigDecimal("2.50")
        );
        Inventory inventory = Inventory.forCharacter(character);

        inventory.addItem(potion, 2);
        inventory.addItem(potion, 3);
        inventory.changeMoney(new BigDecimal("10.50"));

        assertEquals(5, inventory.getEntries().iterator().next().getQuantity());
        assertEquals(new BigDecimal("10.50"), inventory.getMoneyGp());

        inventory.removeItem(potion.getId(), 2);
        assertEquals(3, inventory.getEntries().iterator().next().getQuantity());
        assertThrows(IllegalArgumentException.class, () -> inventory.removeItem(potion.getId(), 4));
        assertThrows(IllegalArgumentException.class, () -> inventory.changeMoney(new BigDecimal("-11.00")));
    }
}
