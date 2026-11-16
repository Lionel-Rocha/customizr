package top.customizr.system.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByCampaignOrderByCreatedAtDesc(Campaign campaign, Pageable pageable);

    List<ChatMessage> findByCampaignAndCreatedAtBeforeOrderByCreatedAtDesc(
            Campaign campaign, LocalDateTime before, Pageable pageable);

    void deleteByCampaign(Campaign campaign);
}
