package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.customizr.system.model.Character;

import java.util.List;
import java.util.UUID;

public interface CharacterRepository extends JpaRepository<Character, UUID> {
    @Query("select c from Character c where c.campaign.id = :campaignId")
    List<Character> allCampaignCharacters(@Param("campaignId") UUID campaignId);
}
