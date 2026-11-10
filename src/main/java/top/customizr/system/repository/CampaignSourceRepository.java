package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignSourceConfig;
import top.customizr.system.model.Source;

import java.util.UUID;

@Repository
public interface CampaignSourceRepository extends JpaRepository<CampaignSourceConfig, UUID> {
    boolean existsByCampaignAndSource(Campaign campaign, Source source);
}
