package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.EncyclopediaEntryRequest;
import top.customizr.system.dto.EncyclopediaEntryResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.City;
import top.customizr.system.model.Country;
import top.customizr.system.model.Encyclopedia;
import top.customizr.system.model.EncyclopediaEntry;
import top.customizr.system.model.EncyclopediaReferenceable;
import top.customizr.system.model.NPC;
import top.customizr.system.model.Point;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CityRepository;
import top.customizr.system.repository.CountryRepository;
import top.customizr.system.repository.EncyclopediaEntryRepository;
import top.customizr.system.repository.EncyclopediaRepository;
import top.customizr.system.repository.NpcRepository;
import top.customizr.system.repository.PointRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Enciclopedia da campanha. O mestre escreve e decide o que esta visivel; jogadores so veem as
 * entradas visiveis (um segredo vira publico com {@code PATCH .../visibility}).
 */
@Service
public class EncyclopediaService {

    private final EncyclopediaRepository encyclopediaRepository;
    private final EncyclopediaEntryRepository entryRepository;
    private final CampaignRepository campaignRepository;
    private final NpcRepository npcRepository;
    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final PointRepository pointRepository;
    private final MasterGuard masterGuard;

    public EncyclopediaService(
            EncyclopediaRepository encyclopediaRepository,
            EncyclopediaEntryRepository entryRepository,
            CampaignRepository campaignRepository,
            NpcRepository npcRepository,
            CountryRepository countryRepository,
            CityRepository cityRepository,
            PointRepository pointRepository,
            MasterGuard masterGuard
    ) {
        this.encyclopediaRepository = encyclopediaRepository;
        this.entryRepository = entryRepository;
        this.campaignRepository = campaignRepository;
        this.npcRepository = npcRepository;
        this.countryRepository = countryRepository;
        this.cityRepository = cityRepository;
        this.pointRepository = pointRepository;
        this.masterGuard = masterGuard;
    }

    @Transactional
    public EncyclopediaEntryResponse create(UUID campaignId, EncyclopediaEntryRequest request, String username) {
        Campaign campaign = findCampaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (request == null) throw badRequest("Request body is required");
        Encyclopedia encyclopedia = encyclopediaRepository.findByCampaign(campaign)
                .orElseGet(() -> encyclopediaRepository.save(new Encyclopedia(campaign)));
        EncyclopediaReferenceable reference = resolve(campaign, request.referenceType(), request.referenceId());
        try {
            EncyclopediaEntry entry = encyclopedia.addEntry(request.entryName(), request.information(), request.type(),
                    request.visible() == null || request.visible(), request.referenceType(), reference);
            encyclopediaRepository.save(encyclopedia);
            return EncyclopediaEntryResponse.from(entry);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    /** Lista (e busca, se {@code query} vier) as entradas que o usuario pode ver. */
    @Transactional
    public List<EncyclopediaEntryResponse> list(UUID campaignId, String query, String username) {
        Campaign campaign = findCampaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        String q = query == null || query.isBlank() ? null : norm(query);
        return encyclopediaRepository.findByCampaign(campaign)
                .map(encyclopedia -> encyclopedia.getEntries().stream()
                        .filter(entry -> master || entry.isVisible())
                        .filter(entry -> q == null || norm(entry.getEntryName()).contains(q)
                                || norm(entry.getInformation()).contains(q)
                                || norm(entry.getReferenceName()).contains(q))
                        .map(EncyclopediaEntryResponse::from)
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public EncyclopediaEntryResponse read(UUID entryId, String username) {
        EncyclopediaEntry entry = findEntry(entryId);
        boolean master = masterGuard.requireMember(username, entry.getEncyclopedia().getCampaign());
        if (!master && !entry.isVisible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found");
        }
        return EncyclopediaEntryResponse.from(entry);
    }

    @Transactional
    public EncyclopediaEntryResponse update(UUID entryId, EncyclopediaEntryRequest request, String username) {
        EncyclopediaEntry entry = findEntry(entryId);
        Campaign campaign = entry.getEncyclopedia().getCampaign();
        masterGuard.requireMaster(username, campaign);
        if (request == null) throw badRequest("Request body is required");
        EncyclopediaReferenceable reference = resolve(campaign, request.referenceType(), request.referenceId());
        try {
            entry.update(request.entryName(), request.information(), request.type(),
                    request.visible() == null ? entry.isVisible() : request.visible());
            entry.reference(request.referenceType(), reference);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return EncyclopediaEntryResponse.from(entry);
    }

    @Transactional
    public EncyclopediaEntryResponse changeVisibility(UUID entryId, boolean visible, String username) {
        EncyclopediaEntry entry = findEntry(entryId);
        masterGuard.requireMaster(username, entry.getEncyclopedia().getCampaign());
        entry.setVisible(visible);
        return EncyclopediaEntryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID entryId, String username) {
        EncyclopediaEntry entry = findEntry(entryId);
        masterGuard.requireMaster(username, entry.getEncyclopedia().getCampaign());
        entry.getEncyclopedia().getEntries().remove(entry);
    }

    private EncyclopediaReferenceable resolve(Campaign campaign, ReferenceType type, UUID id) {
        if (type == null && id == null) {
            return null;
        }
        if (type == null || id == null) {
            throw badRequest("Reference type and reference ID must be given together");
        }
        UUID owner;
        EncyclopediaReferenceable reference;
        switch (type) {
            case NPC -> {
                NPC npc = npcRepository.findById(id).orElseThrow(() -> notFound("NPC"));
                owner = npc.getCampaign().getId();
                reference = npc;
            }
            case COUNTRY -> {
                Country country = countryRepository.findById(id).orElseThrow(() -> notFound("Country"));
                owner = country.getWorld().getCampaign().getId();
                reference = country;
            }
            case CITY -> {
                City city = cityRepository.findById(id).orElseThrow(() -> notFound("City"));
                owner = city.getCountry().getWorld().getCampaign().getId();
                reference = city;
            }
            case POINT -> {
                Point point = pointRepository.findById(id).orElseThrow(() -> notFound("Point"));
                owner = point.getCity().getCountry().getWorld().getCampaign().getId();
                reference = point;
            }
            default -> throw badRequest("Unknown reference type");
        }
        if (!owner.equals(campaign.getId())) {
            throw badRequest("The reference belongs to another campaign");
        }
        return reference;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Campaign findCampaign(UUID campaignId) {
        if (campaignId == null) throw badRequest("Campaign ID is required");
        return campaignRepository.findById(campaignId).orElseThrow(() -> notFound("Campaign"));
    }

    private EncyclopediaEntry findEntry(UUID entryId) {
        return entryRepository.findById(entryId).orElseThrow(() -> notFound("Entry"));
    }

    private ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
