package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Condition;
import top.customizr.system.model.EntryType;
import top.customizr.system.model.PointType;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.model.Skill;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Campanha inteira num JSON so (backup / levar pra outro servidor). Catalogo (classes, itens,
 * monstros, especies...) vai por referencia "nome + fonte": quem importa precisa ter o mesmo conteudo
 * importado; o que nao existir vira aviso. Imagens vao junto em base64 (opcional).
 */
public record CampaignExport(
        int formatVersion,
        LocalDateTime exportedAt,
        String name,
        List<String> sourceCodes,
        List<WorldData> worlds,
        List<EntryData> encyclopedia,
        List<NpcData> npcs,
        List<CharacterData> characters,
        List<PartyData> parties,
        List<ImageData> images
) {
    public static final int FORMAT_VERSION = 1;

    public record Ref(String name, String source) {}

    public record WorldData(String name, String mapUrl, String masterInfo, List<CountryData> countries) {}

    public record CountryData(String name, String info, String masterInfo, String mapUrl, Float worldMapX,
                              Float worldMapY, List<CityData> cities) {}

    public record CityData(String name, String info, String cityMapUrl, Float countryMapX, Float countryMapY,
                           Integer population, boolean capital, List<PointData> points) {}

    public record PointData(String name, PointType type, String info, Float cityMapX, Float cityMapY) {}

    public record EntryData(String entryName, String information, EntryType type, boolean visible,
                            ReferenceType referenceType, String referenceName) {}

    public record NpcData(String name, String personality, String masterInformation, String photoUrl, Ref statBlock,
                          InventoryData inventory) {}

    public record ClassData(String className, String sourceCode, int level, String subclass, Integer subclassChosenAtLevel) {}

    public record AttackData(String name, int attackBonus, String damage, String damageType, String notes) {}

    public record ItemData(String name, String source, int quantity) {}

    public record InventoryData(BigDecimal moneyGp, List<ItemData> items) {}

    public record CharacterData(
            String name,
            String ownerName,
            String status,
            int[] attributes,
            Integer hpMax, Integer hpCurrent, Integer hpTemp, Integer armorClass, Integer speedFt,
            List<ClassData> classes,
            Ref species,
            Ref background,
            Set<Skill> skillProficiencies,
            Set<Skill> skillExpertise,
            Set<AbilityScore> savingThrows,
            Set<Condition> conditions,
            int exhaustion,
            int experiencePoints,
            String portraitUrl,
            List<AttackData> attacks,
            List<Ref> feats,
            InventoryData inventory
    ) {}

    public record PartyData(String name, Integer maxSize, List<String> members, InventoryData inventory) {}

    public record ImageData(String id, String fileName, String contentType, String base64) {}
}
