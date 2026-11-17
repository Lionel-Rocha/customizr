package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.MapObject;

import java.util.List;
import java.util.UUID;

@Repository
public interface MapObjectRepository extends JpaRepository<MapObject, UUID> {

    List<MapObject> findByCampaignOrderByName(Campaign campaign);
}
