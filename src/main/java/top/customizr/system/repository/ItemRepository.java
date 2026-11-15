package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findBySource_Id(UUID sourceId);
    List<Item> findByNameContainingIgnoreCase(String name);
    boolean existsByNameAndSource_Id(String name, UUID sourceId);
    Optional<Item> findByNameAndSource_Id(String name, UUID sourceId);
}
