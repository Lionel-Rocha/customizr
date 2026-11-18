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
        List<ImageData> images,
        List<SceneData> scenes,
        List<MonsterData> monsters,
        List<ObjectData> objects,
        List<IconData> icons
) {
    /** 2: cenas (paredes, areas, tokens, luz), monstros customizados, objetos e pacote de icones. */
    public static final int FORMAT_VERSION = 2;

    /** Fonte usada nas referencias a monstros customizados da propria campanha. */
    public static final String HOMEBREW = "HOMEBREW";

    public record SceneData(String name, String mapUrl, top.customizr.system.model.GridType gridType, int gridSizePx,
                            int feetPerCell, Integer widthPx, Integer heightPx, boolean fogEnabled, boolean active,
                            Set<String> revealedCells, boolean visionEnabled, top.customizr.system.model.SceneLighting lighting,
                            List<WallData> walls, List<TemplateData> templates, List<TokenData> tokens,
                            List<PinData> pins) {}

    public record PinData(double x, double y, String label, String icon, String note, String entryName, boolean visibleToPlayers) {}

    public record WallData(double x1, double y1, double x2, double y2, top.customizr.system.model.Wall.Type type,
                           boolean open, boolean locked) {}

    public record TemplateData(top.customizr.system.model.AreaTemplate.Shape shape, double x, double y, double angle,
                               int sizeFt, int widthFt, String label, String color, boolean darkness) {}

    public record MarkerData(String label, String icon, Integer roundsLeft) {}

    public record TokenData(String name, double x, double y, int sizeCells, double widthCells, double heightCells,
                            String imageUrl, boolean hidden, int rotation, String characterName, String npcName, Ref monster,
                            Integer hpCurrent, Integer hpMax, Set<Condition> conditions, Integer visionFt,
                            top.customizr.system.model.TokenLayer layer, Integer elevationFt, List<MarkerData> markers,
                            List<String> memberNames, Integer lightBrightFt, Integer lightDimFt, Integer darkvisionFt,
                            List<AuraData> auras) {}

    public record AuraData(int radiusFt, String color, String label) {}

    /** Monstro customizado da campanha (a ficha inteira, inclusive a entrada no formato do 5etools). */
    public record MonsterData(String name, String size, String creatureType, String alignment, Integer armorClass,
                              String armorNote, Integer hpAverage, String hpFormula, String speed, int[] abilities,
                              String challengeRating, String savingThrows, String skills, String damageVulnerabilities,
                              String damageResistances, String damageImmunities, String conditionImmunities, String senses,
                              Integer passivePerception, String languages, String traits, String actions, String bonusActions,
                              String reactions, String legendaryActions, String spellcasting,
                              java.util.Map<String, Object> metadata) {}

    public record ObjectData(String name, String imageUrl, double widthCells, double heightCells,
                             top.customizr.system.model.TokenLayer layer) {}

    public record IconData(String key, String label, String imageUrl) {}

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
