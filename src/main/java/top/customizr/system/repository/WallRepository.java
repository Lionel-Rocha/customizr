package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Wall;

import java.util.UUID;

@Repository
public interface WallRepository extends JpaRepository<Wall, UUID> {}
