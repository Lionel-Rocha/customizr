package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Loot;

import java.util.List;
import java.util.UUID;

@Repository
public interface LootRepository extends JpaRepository<Loot, UUID> {

    List<Loot> findByCampaignOrderByName(Campaign campaign);
}
