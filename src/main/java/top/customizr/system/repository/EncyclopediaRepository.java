package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Encyclopedia;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncyclopediaRepository extends JpaRepository<Encyclopedia, UUID> {
    Optional<Encyclopedia> findByCampaign(Campaign campaign);
}
