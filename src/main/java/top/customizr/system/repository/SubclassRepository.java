package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Subclass;

import java.util.UUID;

public interface SubclassRepository extends JpaRepository<Subclass, UUID> {}
