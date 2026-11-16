package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Spell;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpellRepository extends JpaRepository<Spell, UUID> {
    Optional<Spell> findByNameAndSource_Id(String name, UUID sourceId);
    Optional<Spell> findByNameIgnoreCaseAndSource_CodeIgnoreCase(String name, String sourceCode);
    List<Spell> findByNameContainingIgnoreCase(String name);
}
