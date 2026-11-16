package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Monster;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonsterRepository extends JpaRepository<Monster, UUID> {
    Optional<Monster> findByNameAndSource_Id(String name, UUID sourceId);

    List<Monster> findByNameContainingIgnoreCaseOrderByName(String name);

    List<Monster> findAllByOrderByName();
}
