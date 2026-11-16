package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.FeatDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatDefinitionRepository extends JpaRepository<FeatDefinition, UUID> {
    Optional<FeatDefinition> findByNameAndSource_Id(String name, UUID sourceId);

    List<FeatDefinition> findByNameContainingIgnoreCaseOrderByName(String name);

    List<FeatDefinition> findAllByOrderByName();
}
