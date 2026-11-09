package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Campaign;

public interface CampaignRepository extends JpaRepository<Campaign, String> {
}
