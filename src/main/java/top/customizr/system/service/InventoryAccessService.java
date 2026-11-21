package top.customizr.system.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import top.customizr.system.model.Character;
import top.customizr.system.model.Inventory;
import top.customizr.system.repository.InventoryRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryAccessService {

    private final InventoryRepository inventoryRepository;

    public InventoryAccessService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public List<Inventory> characterInventories(Character character) {
        if (character == null) {
            throw new IllegalArgumentException("Falta informar o personagem");
        }
        List<Inventory> inventories = new ArrayList<>();
        inventoryRepository.findByCharacter(character).ifPresent(inventories::add);
        if (character.getParty() != null) {
            inventoryRepository.findByParty(character.getParty()).ifPresent(inventories::add);
        }
        return List.copyOf(inventories);
    }

    public void assertCanModify(Character actor, Inventory target) {
        if (actor == null || target == null) {
            throw new IllegalArgumentException("Faltam quem age e o inventário");
        }
        if (target.getCharacter() != null
                && target.getCharacter().getOwner().getUserId().equals(actor.getOwner().getUserId())) {
            return;
        }
        if (target.getParty() != null && actor.getParty() != null
                && target.getParty().getPartyId().equals(actor.getParty().getPartyId())) {
            return;
        }
        if (target.getNpc() != null
                && target.getNpc().getCampaign().getMaster().getUserId().equals(actor.getOwner().getUserId())) {
            return;
        }
        throw new AccessDeniedException("Você não acessa este inventário");
    }
}
