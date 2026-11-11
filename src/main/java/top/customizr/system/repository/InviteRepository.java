package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Invite;
import top.customizr.system.model.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {
    // Navega: Campaign -> id, e InvitedUser -> userId
    Optional<Invite> findByCampaignIdAndInvitedUserUserId(UUID campaignId, UUID invitedUserId);
}