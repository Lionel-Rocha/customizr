package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.Character;
import top.customizr.system.model.Inventory;
import top.customizr.system.model.Item;
import top.customizr.system.model.NPC;
import top.customizr.system.model.Party;
import top.customizr.system.repository.InventoryRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.NpcRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ItemRepository itemRepository;
    private final NpcRepository npcRepository;
    private final InventoryAccessService inventoryAccessService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ItemRepository itemRepository,
            NpcRepository npcRepository,
            InventoryAccessService inventoryAccessService
    ) {
        this.inventoryRepository = inventoryRepository;
        this.itemRepository = itemRepository;
        this.npcRepository = npcRepository;
        this.inventoryAccessService = inventoryAccessService;
    }

    @Transactional
    public List<Inventory> characterInventories(Character actor) {
        return inventoryAccessService.characterInventories(actor);
    }

    @Transactional
    public Inventory getOrCreateCharacterInventory(Character actor) {
        requireActor(actor);
        return inventoryRepository.findByCharacter(actor)
                .orElseGet(() -> inventoryRepository.save(Inventory.forCharacter(actor)));
    }

    @Transactional
    public Inventory getOrCreatePartyInventory(Character actor) {
        requireActor(actor);
        Party party = actor.getParty();
        if (party == null) {
            throw badRequest("Character is not in a party");
        }
        Inventory inventory = inventoryRepository.findByParty(party)
                .orElseGet(() -> inventoryRepository.save(Inventory.forParty(party)));
        inventoryAccessService.assertCanModify(actor, inventory);
        return inventory;
    }

    @Transactional
    public Inventory getOrCreateNpcInventory(Character actor, UUID npcId) {
        requireActor(actor);
        if (npcId == null) {
            throw badRequest("NPC ID is required");
        }
        NPC npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPC not found"));
        Inventory inventory = inventoryRepository.findByNpc(npc)
                .orElseGet(() -> inventoryRepository.save(Inventory.forNpc(npc)));
        inventoryAccessService.assertCanModify(actor, inventory);
        return inventory;
    }

    @Transactional
    public Inventory read(Character actor, UUID inventoryId) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        return inventory;
    }

    @Transactional
    public Inventory addItem(Character actor, UUID inventoryId, UUID itemId, int quantity) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        if (itemId == null) {
            throw badRequest("Item ID is required");
        }
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        try {
            inventory.addItem(item, quantity);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory removeItem(Character actor, UUID inventoryId, UUID itemId, int quantity) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        try {
            inventory.removeItem(itemId, quantity);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory changeMoney(Character actor, UUID inventoryId, BigDecimal amount) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        try {
            inventory.changeMoney(amount);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return inventoryRepository.save(inventory);
    }

    private Inventory findInventory(UUID inventoryId) {
        if (inventoryId == null) {
            throw badRequest("Inventory ID is required");
        }
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    private void requireActor(Character actor) {
        if (actor == null) {
            throw badRequest("Character is required");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
