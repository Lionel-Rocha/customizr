package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CreatePartyRequest;
import top.customizr.system.dto.PartyResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Party;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.PartyRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.model.User;
import java.util.List;

import java.util.UUID;

@Service
public class PartyService {

    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final PartyRepository partyRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public PartyService(
            CampaignRepository campaignRepository,
            CharacterRepository characterRepository,
            PartyRepository partyRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.characterRepository = characterRepository;
        this.partyRepository = partyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PartyResponse create(UUID campaignId, CreatePartyRequest request, String username) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Party name is required");
        }
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        requireCampaignMaster(campaign, username);
        Party party = new Party(request.name(), campaign);
        try {
            party.changeMaxSize(request.maxSize());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return PartyResponse.from(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse read(UUID partyId, String username) {
        Party party = findParty(partyId);
        requireCampaignMember(party.getCampaign(), username);
        return PartyResponse.from(party);
    }

    @Transactional
    public List<PartyResponse> listByCampaign(UUID campaignId, String username) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        requireCampaignMember(campaign, username);
        return partyRepository.findByCampaign(campaign).stream().map(PartyResponse::from).toList();
    }

    @Transactional
    public PartyResponse changeMaxSize(UUID partyId, Integer maxSize, String username) {
        Party party = findParty(partyId);
        requireCampaignMaster(party.getCampaign(), username);
        try {
            party.changeMaxSize(maxSize);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        return PartyResponse.from(party);
    }

    @Transactional
    public PartyResponse addCharacter(UUID partyId, UUID characterId, String username) {
        Party party = findParty(partyId);
        requireCampaignMaster(party.getCampaign(), username);
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        try {
            party.addMember(character);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        return PartyResponse.from(party);
    }

    @Transactional
    public PartyResponse removeCharacter(UUID partyId, UUID characterId, String username) {
        Party party = findParty(partyId);
        requireCampaignMaster(party.getCampaign(), username);
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (character.getParty() == null
                || !character.getParty().getPartyId().equals(party.getPartyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character is not in this party");
        }
        party.removeMember(character);
        return PartyResponse.from(party);
    }

    private Party findParty(UUID partyId) {
        return partyRepository.findById(partyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Party not found"));
    }

    private void requireCampaignMember(Campaign campaign, String username) {
        if (campaign.getMaster().getName().equals(username)) {
            return;
        }
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (membershipRepository.findByCampaignIdAndUserId(campaign.getId(), user.getUserId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this campaign");
        }
    }

    private void requireCampaignMaster(Campaign campaign, String username) {
        if (!campaign.getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Only the campaign master can manage parties");
        }
    }
}
