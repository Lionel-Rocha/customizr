package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ImageAsset;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> {
    Optional<ImageAsset> findByCampaignAndSha256(Campaign campaign, String sha256);

    java.util.List<ImageAsset> findByCampaign(Campaign campaign);
}
