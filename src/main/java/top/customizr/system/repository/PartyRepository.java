package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Party;

import java.util.UUID;

public interface PartyRepository extends JpaRepository<Party, UUID> {}
