package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Source;

import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {
}
