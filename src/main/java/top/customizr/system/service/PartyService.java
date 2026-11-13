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

import java.util.UUID;

@Service
public class PartyService {

    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final PartyRepository partyRepository;

    public PartyService(
            CampaignRepository campaignRepository,
            CharacterRepository characterRepository,
            PartyRepository partyRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.characterRepository = characterRepository;
        this.partyRepository = partyRepository;
    }

    @Transactional
    public PartyResponse create(UUID campaignId, CreatePartyRequest request, String username) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Party name is required");
        }
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        requireCampaignMaster(campaign, username);
        return PartyResponse.from(partyRepository.save(new Party(request.name(), campaign)));
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

    private void requireCampaignMaster(Campaign campaign, String username) {
        if (!campaign.getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Only the campaign master can manage parties");
        }
    }
}
