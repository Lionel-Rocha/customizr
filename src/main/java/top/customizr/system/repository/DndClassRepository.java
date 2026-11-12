package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.DndClass;

import java.util.UUID;

public interface DndClassRepository extends JpaRepository<DndClass, UUID> {}
