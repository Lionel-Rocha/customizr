package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Character;

import java.util.UUID;

public interface CharacterRepository extends JpaRepository<Character, UUID> {}
