package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Character;
import top.customizr.system.model.Inventory;
import top.customizr.system.model.NPC;
import top.customizr.system.model.Party;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByCharacter(Character character);
    Optional<Inventory> findByParty(Party party);
    Optional<Inventory> findByNpc(NPC npc);
}
