package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.AzgaarImportResponse;
import top.customizr.system.dto.CityRequest;
import top.customizr.system.dto.CountryRequest;
import top.customizr.system.dto.PointRequest;
import top.customizr.system.dto.WorldRequest;
import top.customizr.system.dto.WorldResponse;
import top.customizr.system.service.WorldService;

import java.util.List;
import java.util.UUID;

/** Mundo, paises, cidades e pontos de interesse da campanha. */
@RestController
@RequestMapping("/world")
public class WorldController {

    private final WorldService worldService;

    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }

    @PostMapping("/campaign/{campaignId}")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse createWorld(@PathVariable UUID campaignId, @RequestBody WorldRequest request,
                                     @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.createWorld(campaignId, request, loggedUser.getUsername());
    }

    @GetMapping("/campaign/{campaignId}")
    public List<WorldResponse> listWorlds(@PathVariable UUID campaignId, @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.listWorlds(campaignId, loggedUser.getUsername());
    }

    /** Corpo: o JSON "Full" exportado pelo Azgaar. {@code mapUrl} opcional (ex.: /image/{id} do PNG enviado). */
    @PostMapping("/campaign/{campaignId}/import/azgaar")
    @ResponseStatus(HttpStatus.CREATED)
    public AzgaarImportResponse importAzgaar(@PathVariable UUID campaignId, @RequestBody JsonNode azgaarJson,
                                             @RequestParam(required = false) Integer maxCitiesPerCountry,
                                             @RequestParam(required = false) String mapUrl,
                                             @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.importAzgaar(campaignId, azgaarJson, maxCitiesPerCountry, mapUrl, loggedUser.getUsername());
    }

    @GetMapping("/{worldId}")
    public WorldResponse readWorld(@PathVariable UUID worldId, @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.readWorld(worldId, loggedUser.getUsername());
    }

    @PutMapping("/{worldId}")
    public WorldResponse updateWorld(@PathVariable UUID worldId, @RequestBody WorldRequest request,
                                     @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.updateWorld(worldId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/{worldId}")
    public ResponseEntity<Void> deleteWorld(@PathVariable UUID worldId, @AuthenticationPrincipal UserDetails loggedUser) {
        worldService.deleteWorld(worldId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{worldId}/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse.CountryResponse addCountry(@PathVariable UUID worldId, @RequestBody CountryRequest request,
                                                    @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.addCountry(worldId, request, loggedUser.getUsername());
    }

    @GetMapping("/countries/{countryId}")
    public WorldResponse.CountryResponse readCountry(@PathVariable UUID countryId,
                                                     @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.readCountry(countryId, loggedUser.getUsername());
    }

    @PutMapping("/countries/{countryId}")
    public WorldResponse.CountryResponse updateCountry(@PathVariable UUID countryId, @RequestBody CountryRequest request,
                                                       @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.updateCountry(countryId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/countries/{countryId}")
    public ResponseEntity<Void> deleteCountry(@PathVariable UUID countryId, @AuthenticationPrincipal UserDetails loggedUser) {
        worldService.deleteCountry(countryId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/countries/{countryId}/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse.CityResponse addCity(@PathVariable UUID countryId, @RequestBody CityRequest request,
                                              @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.addCity(countryId, request, loggedUser.getUsername());
    }

    @GetMapping("/cities/{cityId}")
    public WorldResponse.CityResponse readCity(@PathVariable UUID cityId, @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.readCity(cityId, loggedUser.getUsername());
    }

    @PutMapping("/cities/{cityId}")
    public WorldResponse.CityResponse updateCity(@PathVariable UUID cityId, @RequestBody CityRequest request,
                                                 @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.updateCity(cityId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/cities/{cityId}")
    public ResponseEntity<Void> deleteCity(@PathVariable UUID cityId, @AuthenticationPrincipal UserDetails loggedUser) {
        worldService.deleteCity(cityId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cities/{cityId}/points")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse.PointResponse addPoint(@PathVariable UUID cityId, @RequestBody PointRequest request,
                                                @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.addPoint(cityId, request, loggedUser.getUsername());
    }

    @PutMapping("/points/{pointId}")
    public WorldResponse.PointResponse updatePoint(@PathVariable UUID pointId, @RequestBody PointRequest request,
                                                   @AuthenticationPrincipal UserDetails loggedUser) {
        return worldService.updatePoint(pointId, request, loggedUser.getUsername());
    }

    @DeleteMapping("/points/{pointId}")
    public ResponseEntity<Void> deletePoint(@PathVariable UUID pointId, @AuthenticationPrincipal UserDetails loggedUser) {
        worldService.deletePoint(pointId, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
