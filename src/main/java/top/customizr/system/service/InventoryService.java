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
    private final top.customizr.system.repository.CharacterRepository characterRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ItemRepository itemRepository,
            NpcRepository npcRepository,
            InventoryAccessService inventoryAccessService,
            top.customizr.system.repository.CharacterRepository characterRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.itemRepository = itemRepository;
        this.npcRepository = npcRepository;
        this.inventoryAccessService = inventoryAccessService;
        this.characterRepository = characterRepository;
    }

    /**
     * Personagem que o usuario logado usa pra mexer no inventario: tem que ser dele
     * (ou o mestre da campanha agindo por ele).
     */
    @Transactional
    public Character actorFor(UUID characterId, String username) {
        if (characterId == null) {
            throw badRequest("Falta o id do personagem que age");
        }
        Character actor = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personagem não encontrado"));
        if (!actor.getOwner().getName().equals(username)
                && !actor.getCampaign().getMaster().getName().equals(username)) {
            throw new org.springframework.security.access.AccessDeniedException("Este personagem não é seu");
        }
        return actor;
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
            throw badRequest("O personagem não está em um grupo");
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
            throw badRequest("Falta o id do NPC");
        }
        NPC npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPC não encontrado"));
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
            throw badRequest("Falta o id do item");
        }
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado"));
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

    /** Poe ou tira moedas de um tipo (tirar alem do que tem dessa moeda paga o resto pelo valor, com troco). */
    @Transactional
    public Inventory changeCoins(Character actor, UUID inventoryId, top.customizr.system.model.Currency currency, long amount) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        try {
            inventory.changeCoins(currency, amount);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return inventoryRepository.save(inventory);
    }

    /** Cambio entre moedas do mesmo inventario. */
    @Transactional
    public Inventory exchangeCoins(Character actor, UUID inventoryId, top.customizr.system.model.Currency from,
                                   top.customizr.system.model.Currency to, long amount) {
        Inventory inventory = findInventory(inventoryId);
        inventoryAccessService.assertCanModify(actor, inventory);
        try {
            inventory.exchangeCoins(from, to, amount);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return inventoryRepository.save(inventory);
    }

    private Inventory findInventory(UUID inventoryId) {
        if (inventoryId == null) {
            throw badRequest("Falta o id do inventário");
        }
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventário não encontrado"));
    }

    private void requireActor(Character actor) {
        if (actor == null) {
            throw badRequest("Falta informar o personagem");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
