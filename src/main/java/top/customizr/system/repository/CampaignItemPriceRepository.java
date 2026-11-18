package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignItemPrice;
import top.customizr.system.model.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignItemPriceRepository extends JpaRepository<CampaignItemPrice, UUID> {

    List<CampaignItemPrice> findByCampaign(Campaign campaign);

    Optional<CampaignItemPrice> findByCampaignAndItem(Campaign campaign, Item item);
}
