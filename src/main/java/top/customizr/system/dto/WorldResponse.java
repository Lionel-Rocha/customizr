package top.customizr.system.dto;

import top.customizr.system.model.City;
import top.customizr.system.model.Country;
import top.customizr.system.model.GeneratorLinks;
import top.customizr.system.model.Point;
import top.customizr.system.model.PointType;
import top.customizr.system.model.World;

import java.util.List;
import java.util.UUID;

/** Mundo com paises, cidades e pontos. {@code masterInfo} so vai pro mestre. */
public record WorldResponse(
        UUID id,
        UUID campaignId,
        String name,
        String mapUrl,
        String masterInfo,
        List<CountryResponse> countries
) {
    public static WorldResponse from(World world, boolean master) {
        return new WorldResponse(
                world.getId(),
                world.getCampaign().getId(),
                world.getName(),
                world.getMapUrl(),
                master ? world.getMasterInfo() : null,
                world.getCountries().stream().map(c -> CountryResponse.from(c, master)).toList()
        );
    }

    public record CountryResponse(
            UUID countryId,
            String name,
            String info,
            String masterInfo,
            String mapUrl,
            Float worldMapX,
            Float worldMapY,
            List<CityResponse> cities
    ) {
        public static CountryResponse from(Country country, boolean master) {
            return new CountryResponse(
                    country.getCountryId(),
                    country.getName(),
                    country.getInfo(),
                    master ? country.getMasterInfo() : null,
                    country.getMapUrl(),
                    country.getWorldMapX(),
                    country.getWorldMapY(),
                    country.getCities().stream().map(CityResponse::from).toList()
            );
        }
    }

    public record CityResponse(
            UUID cityId,
            String name,
            String info,
            String cityMapUrl,
            Float countryMapX,
            Float countryMapY,
            Integer population,
            boolean capital,
            String generatorUrl,
            List<PointResponse> points
    ) {
        public static CityResponse from(City city) {
            return new CityResponse(
                    city.getCityId(),
                    city.getName(),
                    city.getInfo(),
                    city.getCityMapUrl(),
                    city.getCountryMapX(),
                    city.getCountryMapY(),
                    city.getPopulation(),
                    city.isCapital(),
                    GeneratorLinks.forCity(city.getName(), city.getPopulation(), city.isCapital()),
                    city.getPoints().stream().map(PointResponse::from).toList()
            );
        }
    }

    public record PointResponse(UUID pointId, String name, PointType type, String info, Float cityMapX, Float cityMapY) {
        public static PointResponse from(Point point) {
            return new PointResponse(point.getPointId(), point.getName(), point.getType(), point.getInfo(),
                    point.getCityMapX(), point.getCityMapY());
        }
    }
}
