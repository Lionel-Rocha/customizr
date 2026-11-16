package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Background;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BackgroundRepository extends JpaRepository<Background, UUID> {
    Optional<Background> findByNameAndSource_Id(String name, UUID sourceId);

    List<Background> findByNameContainingIgnoreCaseOrderByName(String name);

    List<Background> findAllByOrderByName();
}
