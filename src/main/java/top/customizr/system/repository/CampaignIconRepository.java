package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignIcon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignIconRepository extends JpaRepository<CampaignIcon, UUID> {

    List<CampaignIcon> findByCampaignOrderByLabel(Campaign campaign);

    Optional<CampaignIcon> findByCampaignAndKey(Campaign campaign, String key);
}
