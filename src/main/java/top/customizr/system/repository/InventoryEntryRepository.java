package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.InventoryEntry;
import top.customizr.system.model.Item;

import java.util.UUID;

public interface InventoryEntryRepository extends JpaRepository<InventoryEntry, UUID> {
    boolean existsByItem(Item item);
}
