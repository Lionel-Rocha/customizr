package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Optional<Campaign> findCampaignByCode(String code);
    List<Campaign> findByMaster_UserId(UUID userId);

    @Query("""
    select distinct c
    from Campaign c, Membership m
    where c.id = m.campaignId
      and m.userId = :userId
      and m.role = :role
    """)
    List<Campaign> findCampaignsByUserIdAndRole(
            @Param("userId") UUID userId,
            @Param("role") Role role
    );
}
