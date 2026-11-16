package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Species;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpeciesRepository extends JpaRepository<Species, UUID> {
    Optional<Species> findByNameAndSource_Id(String name, UUID sourceId);

    List<Species> findByNameContainingIgnoreCaseOrderByName(String name);

    List<Species> findAllByOrderByName();
}
