package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.AzgaarImportResponse;
import top.customizr.system.dto.CityRequest;
import top.customizr.system.dto.CountryRequest;
import top.customizr.system.dto.PointRequest;
import top.customizr.system.dto.WorldRequest;
import top.customizr.system.dto.WorldResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.City;
import top.customizr.system.model.Country;
import top.customizr.system.model.Encyclopedia;
import top.customizr.system.model.EntryType;
import top.customizr.system.model.Point;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.model.World;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CityRepository;
import top.customizr.system.repository.CountryRepository;
import top.customizr.system.repository.EncyclopediaRepository;
import top.customizr.system.repository.PointRepository;
import top.customizr.system.repository.WorldRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hierarquia do mundo (mundo > pais > cidade > ponto de interesse). So o mestre cria e muda;
 * membros da campanha leem (sem as anotacoes do mestre). Importa mundo do Azgaar.
 */
@Service
public class WorldService {

    public static final int DEFAULT_CITIES_PER_COUNTRY = 15;

    private final WorldRepository worldRepository;
    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final PointRepository pointRepository;
    private final CampaignRepository campaignRepository;
    private final EncyclopediaRepository encyclopediaRepository;
    private final MasterGuard masterGuard;

    public WorldService(
            WorldRepository worldRepository,
            CountryRepository countryRepository,
            CityRepository cityRepository,
            PointRepository pointRepository,
            CampaignRepository campaignRepository,
            EncyclopediaRepository encyclopediaRepository,
            MasterGuard masterGuard
    ) {
        this.worldRepository = worldRepository;
        this.countryRepository = countryRepository;
        this.cityRepository = cityRepository;
        this.pointRepository = pointRepository;
        this.campaignRepository = campaignRepository;
        this.encyclopediaRepository = encyclopediaRepository;
        this.masterGuard = masterGuard;
    }

    // ------------------------------------------------------------------ mundo

    @Transactional
    public WorldResponse createWorld(UUID campaignId, WorldRequest request, String username) {
        Campaign campaign = findCampaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        requireBody(request);
        World world = valid(() -> new World(request.name(), request.mapUrl(), request.masterInfo(), campaign));
        return WorldResponse.from(worldRepository.save(world), true);
    }

    @Transactional
    public List<WorldResponse> listWorlds(UUID campaignId, String username) {
        Campaign campaign = findCampaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        return worldRepository.findByCampaignOrderByName(campaign).stream()
                .map(world -> WorldResponse.from(world, master)).toList();
    }

    @Transactional
    public WorldResponse readWorld(UUID worldId, String username) {
        World world = findWorld(worldId);
        return WorldResponse.from(world, masterGuard.requireMember(username, world.getCampaign()));
    }

    @Transactional
    public WorldResponse updateWorld(UUID worldId, WorldRequest request, String username) {
        World world = findWorld(worldId);
        masterGuard.requireMaster(username, world.getCampaign());
        requireBody(request);
        valid(() -> { world.update(request.name(), request.mapUrl(), request.masterInfo()); return world; });
        return WorldResponse.from(world, true);
    }

    @Transactional
    public void deleteWorld(UUID worldId, String username) {
        World world = findWorld(worldId);
        masterGuard.requireMaster(username, world.getCampaign());
        worldRepository.delete(world);
    }

    // ------------------------------------------------------------------ pais

    @Transactional
    public WorldResponse.CountryResponse addCountry(UUID worldId, CountryRequest request, String username) {
        World world = findWorld(worldId);
        masterGuard.requireMaster(username, world.getCampaign());
        requireBody(request);
        Country country = valid(() -> world.addCountry(request.name(), request.info(), request.masterInfo(),
                request.mapUrl(), request.worldMapX(), request.worldMapY()));
        worldRepository.save(world);
        return WorldResponse.CountryResponse.from(country, true);
    }

    @Transactional
    public WorldResponse.CountryResponse readCountry(UUID countryId, String username) {
        Country country = findCountry(countryId);
        return WorldResponse.CountryResponse.from(country,
                masterGuard.requireMember(username, country.getWorld().getCampaign()));
    }

    @Transactional
    public WorldResponse.CountryResponse updateCountry(UUID countryId, CountryRequest request, String username) {
        Country country = findCountry(countryId);
        masterGuard.requireMaster(username, country.getWorld().getCampaign());
        requireBody(request);
        valid(() -> { country.update(request.name(), request.info(), request.masterInfo(), request.mapUrl(),
                request.worldMapX(), request.worldMapY()); return country; });
        return WorldResponse.CountryResponse.from(country, true);
    }

    @Transactional
    public void deleteCountry(UUID countryId, String username) {
        Country country = findCountry(countryId);
        masterGuard.requireMaster(username, country.getWorld().getCampaign());
        country.getWorld().getCountries().remove(country);
    }

    // ------------------------------------------------------------------ cidade

    @Transactional
    public WorldResponse.CityResponse addCity(UUID countryId, CityRequest request, String username) {
        Country country = findCountry(countryId);
        masterGuard.requireMaster(username, country.getWorld().getCampaign());
        requireBody(request);
        City city = valid(() -> country.addCity(request.name(), request.info(), request.cityMapUrl(),
                request.countryMapX(), request.countryMapY(), request.population(), Boolean.TRUE.equals(request.capital())));
        countryRepository.save(country);
        return WorldResponse.CityResponse.from(city);
    }

    @Transactional
    public WorldResponse.CityResponse readCity(UUID cityId, String username) {
        City city = findCity(cityId);
        masterGuard.requireMember(username, city.getCountry().getWorld().getCampaign());
        return WorldResponse.CityResponse.from(city);
    }

    @Transactional
    public WorldResponse.CityResponse updateCity(UUID cityId, CityRequest request, String username) {
        City city = findCity(cityId);
        masterGuard.requireMaster(username, city.getCountry().getWorld().getCampaign());
        requireBody(request);
        valid(() -> { city.update(request.name(), request.info(), request.cityMapUrl(), request.countryMapX(),
                request.countryMapY(), request.population(), Boolean.TRUE.equals(request.capital())); return city; });
        return WorldResponse.CityResponse.from(city);
    }

    @Transactional
    public void deleteCity(UUID cityId, String username) {
        City city = findCity(cityId);
        masterGuard.requireMaster(username, city.getCountry().getWorld().getCampaign());
        city.getCountry().getCities().remove(city);
    }

    // ------------------------------------------------------------------ ponto de interesse

    @Transactional
    public WorldResponse.PointResponse addPoint(UUID cityId, PointRequest request, String username) {
        City city = findCity(cityId);
        masterGuard.requireMaster(username, city.getCountry().getWorld().getCampaign());
        requireBody(request);
        Point point = valid(() -> city.addPoint(request.name(), request.type(), request.info(),
                request.cityMapX(), request.cityMapY()));
        cityRepository.save(city);
        return WorldResponse.PointResponse.from(point);
    }

    @Transactional
    public WorldResponse.PointResponse updatePoint(UUID pointId, PointRequest request, String username) {
        Point point = findPoint(pointId);
        masterGuard.requireMaster(username, point.getCity().getCountry().getWorld().getCampaign());
        requireBody(request);
        valid(() -> { point.update(request.name(), request.type(), request.info(), request.cityMapX(),
                request.cityMapY()); return point; });
        return WorldResponse.PointResponse.from(point);
    }

    @Transactional
    public void deletePoint(UUID pointId, String username) {
        Point point = findPoint(pointId);
        masterGuard.requireMaster(username, point.getCity().getCountry().getWorld().getCampaign());
        point.getCity().getPoints().remove(point);
    }

    // ------------------------------------------------------------------ Azgaar

    /**
     * Cria um mundo a partir do JSON "Full" do Azgaar's Fantasy Map Generator (Menu > Export > JSON > Full):
     * reinos viram paises (posicao = a capital), as cidades de cada reino viram cidades (capital primeiro,
     * depois as maiores, ate {@code maxCitiesPerCountry}) e cada pais ganha uma entrada na enciclopedia.
     * Coordenadas relativas ao mapa (0 a 1), pra caber em qualquer tamanho de imagem do mapa.
     */
    @Transactional
    public AzgaarImportResponse importAzgaar(UUID campaignId, JsonNode root, Integer maxCitiesPerCountry, String mapUrl,
                                             String username) {
        Campaign campaign = findCampaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (root == null || !root.path("pack").isObject()) {
            throw badRequest("This is not an Azgaar JSON. In the generator: Menu > Export > JSON > Full");
        }
        int perCountry = maxCitiesPerCountry == null ? DEFAULT_CITIES_PER_COUNTRY : Math.max(1, Math.min(500, maxCitiesPerCountry));
        double width = root.path("info").path("width").asDouble(0);
        double height = root.path("info").path("height").asDouble(0);
        if (width <= 0 || height <= 0) {
            throw badRequest("The Azgaar JSON has no map size (info.width / info.height)");
        }
        double populationRate = root.path("settings").path("populationRate").asDouble(1000);
        double urbanization = root.path("settings").path("urbanization").asDouble(1);
        String worldName = root.path("info").path("mapName").asText("World");

        record Burg(int id, String name, float x, float y, boolean capital, int population, int state) {}
        Map<Integer, List<Burg>> burgsByState = new HashMap<>();
        Map<Integer, Burg> burgsById = new HashMap<>();
        int skipped = 0;
        for (JsonNode b : root.path("pack").path("burgs")) {
            if (!b.has("name") || !b.path("x").isNumber() || !b.path("y").isNumber() || b.has("removed")) continue;
            Burg burg = new Burg(b.path("i").asInt(), b.path("name").asText(),
                    clamp01(b.path("x").asDouble() / width), clamp01(b.path("y").asDouble() / height),
                    b.path("capital").asInt(0) == 1,
                    (int) Math.round(b.path("population").asDouble(0) * populationRate * urbanization),
                    b.path("state").asInt(0));
            burgsById.put(burg.id(), burg);
            if (burg.state() == 0) {
                skipped++;
                continue;
            }
            burgsByState.computeIfAbsent(burg.state(), k -> new ArrayList<>()).add(burg);
        }

        World world = new World(worldName, mapUrl, "Imported from Azgaar's Fantasy Map Generator (seed "
                + root.path("info").path("seed").asText("?") + ").", campaign);
        Encyclopedia encyclopedia = encyclopediaRepository.findByCampaign(campaign)
                .orElseGet(() -> encyclopediaRepository.save(new Encyclopedia(campaign)));
        int countries = 0;
        int cities = 0;
        int entries = 0;
        for (JsonNode s : root.path("pack").path("states")) {
            int stateId = s.path("i").asInt(0);
            if (stateId == 0 || s.has("removed") || !s.has("name")) continue;
            Burg capital = burgsById.get(s.path("capital").asInt(-1));
            String fullName = s.path("fullName").asText(s.path("name").asText());
            Country country = world.addCountry(s.path("name").asText(), fullName, null, null,
                    capital == null ? null : capital.x(), capital == null ? null : capital.y());
            countries++;
            List<Burg> burgs = new ArrayList<>(burgsByState.getOrDefault(stateId, List.of()));
            burgs.sort(Comparator.comparing(Burg::capital).thenComparingInt(Burg::population).reversed());
            for (int i = 0; i < burgs.size(); i++) {
                Burg burg = burgs.get(i);
                if (i >= perCountry) {
                    skipped++;
                    continue;
                }
                country.addCity(burg.name(), null, null, burg.x(), burg.y(), burg.population(), burg.capital());
                cities++;
            }
            StringBuilder info = new StringBuilder(fullName).append('.');
            if (capital != null) info.append(" Capital: ").append(capital.name()).append('.');
            if (!burgs.isEmpty()) info.append(" ").append(burgs.size()).append(" settlements.");
            encyclopedia.addEntry(country.getName(), info.toString(), EntryType.PLACE, true, ReferenceType.COUNTRY, country);
            entries++;
        }
        worldRepository.save(world);
        encyclopediaRepository.save(encyclopedia);
        return new AzgaarImportResponse(world.getId(), worldName, countries, cities, skipped, entries);
    }

    private static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------ utilitarios

    private <T> T valid(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private void requireBody(Object request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }
    }

    private Campaign findCampaign(UUID campaignId) {
        if (campaignId == null) throw badRequest("Campaign ID is required");
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private World findWorld(UUID id) {
        return worldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found"));
    }

    private Country findCountry(UUID id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Country not found"));
    }

    private City findCity(UUID id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found"));
    }

    private Point findPoint(UUID id) {
        return pointRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Point not found"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
