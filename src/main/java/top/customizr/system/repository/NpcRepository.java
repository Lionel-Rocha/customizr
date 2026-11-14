package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.NPC;

import java.util.List;
import java.util.UUID;

public interface NpcRepository extends JpaRepository<NPC, UUID> {
    List<NPC> findByCampaign(Campaign campaign);
}
