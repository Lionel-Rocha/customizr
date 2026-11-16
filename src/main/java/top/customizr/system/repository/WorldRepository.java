package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.World;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorldRepository extends JpaRepository<World, UUID> {
    List<World> findByCampaignOrderByName(Campaign campaign);
}
