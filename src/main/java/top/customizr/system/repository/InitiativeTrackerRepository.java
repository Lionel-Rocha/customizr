package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.InitiativeTracker;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InitiativeTrackerRepository extends JpaRepository<InitiativeTracker, UUID> {
    Optional<InitiativeTracker> findByCampaign(Campaign campaign);
}
