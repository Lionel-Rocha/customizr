package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.customizr.system.model.Campaign;

import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
}
