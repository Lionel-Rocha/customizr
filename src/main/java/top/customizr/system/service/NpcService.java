package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.NPC;
import top.customizr.system.model.User;
import top.customizr.system.dto.NpcResponse;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.InventoryRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.NpcRepository;
import top.customizr.system.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final CampaignRepository campaignRepository;
    private final InventoryRepository inventoryRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public NpcService(
            NpcRepository npcRepository,
            CampaignRepository campaignRepository,
            InventoryRepository inventoryRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository
    ) {
        this.npcRepository = npcRepository;
        this.campaignRepository = campaignRepository;
        this.inventoryRepository = inventoryRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NpcResponse create(
            UUID campaignId,
            String name,
            String personality,
            String masterInformation,
            String photoUrl,
            String username
    ) {
        Campaign campaign = findCampaign(campaignId);
        requireCampaignMaster(campaign, username);
        try {
            NPC npc = npcRepository.save(new NPC(name, personality, masterInformation, photoUrl, campaign));
            return NpcResponse.from(npc, true);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    @Transactional
    public NpcResponse read(UUID npcId, String username) {
        NPC npc = findNpc(npcId);
        requireCampaignMember(npc.getCampaign(), username);
        return NpcResponse.from(npc, isCampaignMaster(npc.getCampaign(), username));
    }

    @Transactional
    public List<NpcResponse> listByCampaign(UUID campaignId, String username) {
        Campaign campaign = findCampaign(campaignId);
        requireCampaignMember(campaign, username);
        boolean includeMasterInformation = isCampaignMaster(campaign, username);
        return npcRepository.findByCampaign(campaign).stream()
                .map(npc -> NpcResponse.from(npc, includeMasterInformation))
                .toList();
    }

    @Transactional
    public NpcResponse update(
            UUID npcId,
            String name,
            String personality,
            String masterInformation,
            String photoUrl,
            String username
    ) {
        NPC npc = findNpc(npcId);
        requireCampaignMaster(npc.getCampaign(), username);
        try {
            npc.update(name, personality, masterInformation, photoUrl);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return NpcResponse.from(npcRepository.save(npc), true);
    }

    @Transactional
    public void delete(UUID npcId, String username) {
        NPC npc = findNpc(npcId);
        requireCampaignMaster(npc.getCampaign(), username);
        inventoryRepository.findByNpc(npc).ifPresent(inventoryRepository::delete);
        npcRepository.delete(npc);
    }

    private NPC findNpc(UUID npcId) {
        if (npcId == null) {
            throw badRequest("NPC ID is required");
        }
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPC not found"));
    }

    private Campaign findCampaign(UUID campaignId) {
        if (campaignId == null) {
            throw badRequest("Campaign ID is required");
        }
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private void requireCampaignMember(Campaign campaign, String username) {
        User user = findUser(username);
        if (!campaign.getMaster().getUserId().equals(user.getUserId())
                && membershipRepository.findByCampaignIdAndUserId(campaign.getId(), user.getUserId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this campaign");
        }
    }

    private void requireCampaignMaster(Campaign campaign, String username) {
        if (!isCampaignMaster(campaign, username)) {
            throw new AccessDeniedException("Only the campaign master can manage NPCs");
        }
    }

    private boolean isCampaignMaster(Campaign campaign, String username) {
        return campaign.getMaster().getUserId().equals(findUser(username).getUserId());
    }

    private User findUser(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
