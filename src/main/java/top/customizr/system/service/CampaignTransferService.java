package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CampaignExport;
import top.customizr.system.dto.CampaignImportResponse;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Background;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignSourceConfig;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.City;
import top.customizr.system.model.Country;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Encyclopedia;
import top.customizr.system.model.EncyclopediaReferenceable;
import top.customizr.system.model.ImageAsset;
import top.customizr.system.model.Inventory;
import top.customizr.system.model.Item;
import top.customizr.system.model.NPC;
import top.customizr.system.model.Party;
import top.customizr.system.model.Point;
import top.customizr.system.model.ReferenceType;
import top.customizr.system.model.Sheet;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.model.World;
import top.customizr.system.repository.BackgroundRepository;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CampaignSourceRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.EncyclopediaRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.ImageAssetRepository;
import top.customizr.system.repository.InventoryRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.NpcRepository;
import top.customizr.system.repository.PartyRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.WorldRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Exporta a campanha inteira num JSON e importa como campanha NOVA (nunca sobrescreve nada).
 * Na importacao quem importa vira o mestre e o dono de todos os personagens (o dono original fica
 * anotado nos avisos); o conteudo de catalogo e ligado por nome + fonte.
 */
@Service
public class CampaignTransferService {

    private final CampaignRepository campaignRepository;
    private final CampaignSourceRepository campaignSourceRepository;
    private final SourceRepository sourceRepository;
    private final CharacterRepository characterRepository;
    private final PartyRepository partyRepository;
    private final NpcRepository npcRepository;
    private final WorldRepository worldRepository;
    private final EncyclopediaRepository encyclopediaRepository;
    private final ImageAssetRepository imageAssetRepository;
    private final InventoryRepository inventoryRepository;
    private final ItemRepository itemRepository;
    private final DndClassRepository dndClassRepository;
    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final FeatDefinitionRepository featRepository;
    private final MonsterRepository monsterRepository;
    private final MasterGuard masterGuard;

    public CampaignTransferService(
            CampaignRepository campaignRepository, CampaignSourceRepository campaignSourceRepository,
            SourceRepository sourceRepository, CharacterRepository characterRepository, PartyRepository partyRepository,
            NpcRepository npcRepository, WorldRepository worldRepository, EncyclopediaRepository encyclopediaRepository,
            ImageAssetRepository imageAssetRepository, InventoryRepository inventoryRepository,
            ItemRepository itemRepository, DndClassRepository dndClassRepository, SpeciesRepository speciesRepository,
            BackgroundRepository backgroundRepository, FeatDefinitionRepository featRepository,
            MonsterRepository monsterRepository, MasterGuard masterGuard
    ) {
        this.campaignRepository = campaignRepository;
        this.campaignSourceRepository = campaignSourceRepository;
        this.sourceRepository = sourceRepository;
        this.characterRepository = characterRepository;
        this.partyRepository = partyRepository;
        this.npcRepository = npcRepository;
        this.worldRepository = worldRepository;
        this.encyclopediaRepository = encyclopediaRepository;
        this.imageAssetRepository = imageAssetRepository;
        this.inventoryRepository = inventoryRepository;
        this.itemRepository = itemRepository;
        this.dndClassRepository = dndClassRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.featRepository = featRepository;
        this.monsterRepository = monsterRepository;
        this.masterGuard = masterGuard;
    }

    // ------------------------------------------------------------------ exportar

    @Transactional
    public CampaignExport export(UUID campaignId, boolean includeImages, String username) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        masterGuard.requireMaster(username, campaign);

        List<CampaignExport.WorldData> worlds = worldRepository.findByCampaignOrderByName(campaign).stream()
                .map(w -> new CampaignExport.WorldData(w.getName(), w.getMapUrl(), w.getMasterInfo(),
                        w.getCountries().stream().map(c -> new CampaignExport.CountryData(c.getName(), c.getInfo(),
                                c.getMasterInfo(), c.getMapUrl(), c.getWorldMapX(), c.getWorldMapY(),
                                c.getCities().stream().map(ci -> new CampaignExport.CityData(ci.getName(), ci.getInfo(),
                                        ci.getCityMapUrl(), ci.getCountryMapX(), ci.getCountryMapY(), ci.getPopulation(),
                                        ci.isCapital(), ci.getPoints().stream().map(p -> new CampaignExport.PointData(
                                        p.getName(), p.getType(), p.getInfo(), p.getCityMapX(), p.getCityMapY())).toList()))
                                        .toList())).toList()))
                .toList();

        List<CampaignExport.EntryData> entries = encyclopediaRepository.findByCampaign(campaign)
                .map(e -> e.getEntries().stream().map(x -> new CampaignExport.EntryData(x.getEntryName(),
                        x.getInformation(), x.getType(), x.isVisible(), x.getReferenceType(), x.getReferenceName())).toList())
                .orElse(List.of());

        List<CampaignExport.NpcData> npcs = npcRepository.findByCampaign(campaign).stream()
                .map(n -> new CampaignExport.NpcData(n.getName(), n.getPersonality(), n.getMasterInformation(),
                        n.getPhotoUrl(), n.getStatBlock() == null ? null
                        : new CampaignExport.Ref(n.getStatBlock().getName(), n.getStatBlock().getSource().getCode()),
                        inventory(inventoryRepository.findByNpc(n))))
                .toList();

        List<CampaignExport.CharacterData> characters = new ArrayList<>();
        for (Character c : characterRepository.allCampaignCharacters(campaign.getId())) {
            Sheet s = c.getSheet();
            Attributes a = s.getAttributes();
            characters.add(new CampaignExport.CharacterData(
                    c.getName(), c.getOwner().getName(), c.getStatus().name(),
                    a == null ? null : new int[]{a.getStrength(), a.getDexterity(), a.getConstitution(),
                            a.getIntelligence(), a.getWisdom(), a.getCharisma()},
                    s.getHpMax(), s.getHpCurrent(), s.getHpTemp(), s.getArmorClass(), s.getSpeedFt(),
                    s.getClasses().stream().map(cc -> new CampaignExport.ClassData(cc.getDndClass().getClassName(),
                            cc.getDndClass().getSourceCode(), cc.getLevel(),
                            cc.getSubclass() == null ? null : cc.getSubclass().getName(), cc.getSubclassChosenAtLevel())).toList(),
                    c.getSpecies() == null ? null : new CampaignExport.Ref(c.getSpecies().getName(), c.getSpecies().getSource().getCode()),
                    c.getBackground() == null ? null : new CampaignExport.Ref(c.getBackground().getName(), c.getBackground().getSource().getCode()),
                    s.getSkillProficiencies(), s.getSkillExpertise(), s.getSavingThrowProficiencies(), s.getConditions(),
                    s.getExhaustion(), c.getExperiencePoints(), c.getPortraitUrl(),
                    s.getAttacks().stream().map(at -> new CampaignExport.AttackData(at.getName(), at.getAttackBonus(),
                            at.getDamage(), at.getDamageType(), at.getNotes())).toList(),
                    s.getCatalogFeats().stream().map(f -> new CampaignExport.Ref(f.getFeat().getName(),
                            f.getFeat().getSource().getCode())).toList(),
                    inventory(inventoryRepository.findByCharacter(c))));
        }

        List<CampaignExport.PartyData> parties = partyRepository.findByCampaign(campaign).stream()
                .map(p -> new CampaignExport.PartyData(p.getName(), p.getMaxSize(),
                        p.getMembers().stream().map(Character::getName).toList(),
                        inventory(inventoryRepository.findByParty(p))))
                .toList();

        List<CampaignExport.ImageData> images = includeImages
                ? imageAssetRepository.findByCampaign(campaign).stream().map(i -> new CampaignExport.ImageData(i.getId().toString(),
                        i.getFileName(), i.getContentType(), Base64.getEncoder().encodeToString(i.getData()))).toList()
                : List.of();

        return new CampaignExport(CampaignExport.FORMAT_VERSION, LocalDateTime.now(), campaign.getName(),
                campaign.getSourceConfigs().stream().filter(CampaignSourceConfig::isEnabled)
                        .map(sc -> sc.getSource().getCode()).toList(),
                worlds, entries, npcs, characters, parties, images);
    }

    private static CampaignExport.InventoryData inventory(Optional<Inventory> inventory) {
        return inventory.map(i -> new CampaignExport.InventoryData(i.getMoneyGp(),
                i.getEntries().stream().map(e -> new CampaignExport.ItemData(e.getItem().getName(),
                        e.getItem().getSource().getCode(), e.getQuantity())).toList())).orElse(null);
    }

    // ------------------------------------------------------------------ importar

    @Transactional
    public CampaignImportResponse importCampaign(CampaignExport data, String username) {
        if (data == null || data.name() == null || data.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is not a Customizr campaign export");
        }
        if (data.formatVersion() > CampaignExport.FORMAT_VERSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Export made by a newer version (format " + data.formatVersion() + ")");
        }
        User master = masterGuard.user(username);
        List<String> warnings = new ArrayList<>();
        Campaign campaign = campaignRepository.save(new Campaign(data.name().trim(), master));

        for (String code : nullSafe(data.sourceCodes())) {
            sourceRepository.getSourceByCode(code).ifPresentOrElse(
                    source -> campaignSourceRepository.save(new CampaignSourceConfig(campaign, source, true)),
                    () -> warnings.add("Source " + code + " does not exist on this server"));
        }

        // imagens primeiro: os enderecos /image/{id antigo} sao trocados pelos novos
        Map<String, String> imageUrls = new HashMap<>();
        for (CampaignExport.ImageData image : nullSafe(data.images())) {
            byte[] bytes = Base64.getDecoder().decode(image.base64());
            String hash = sha256(bytes);
            ImageAsset saved = imageAssetRepository.findByCampaignAndSha256(campaign, hash)
                    .orElseGet(() -> imageAssetRepository.save(new ImageAsset(campaign, bytes, image.contentType(), hash, image.fileName())));
            imageUrls.put("/image/" + image.id(), saved.getUrl());
        }
        Function<String, String> url = u -> u == null ? null : imageUrls.getOrDefault(u, u);

        Map<String, EncyclopediaReferenceable> references = new HashMap<>();
        for (CampaignExport.WorldData w : nullSafe(data.worlds())) {
            World world = new World(w.name(), url.apply(w.mapUrl()), w.masterInfo(), campaign);
            for (CampaignExport.CountryData c : nullSafe(w.countries())) {
                Country country = world.addCountry(c.name(), c.info(), c.masterInfo(), url.apply(c.mapUrl()), c.worldMapX(), c.worldMapY());
                references.put(ReferenceType.COUNTRY + ":" + c.name(), country);
                for (CampaignExport.CityData ci : nullSafe(c.cities())) {
                    City city = country.addCity(ci.name(), ci.info(), url.apply(ci.cityMapUrl()), ci.countryMapX(),
                            ci.countryMapY(), ci.population(), ci.capital());
                    references.put(ReferenceType.CITY + ":" + ci.name(), city);
                    for (CampaignExport.PointData p : nullSafe(ci.points())) {
                        Point point = city.addPoint(p.name(), p.type(), p.info(), p.cityMapX(), p.cityMapY());
                        references.put(ReferenceType.POINT + ":" + p.name(), point);
                    }
                }
            }
            worldRepository.save(world);
        }

        for (CampaignExport.NpcData n : nullSafe(data.npcs())) {
            NPC npc = npcRepository.save(new NPC(n.name(), n.personality(), n.masterInformation(), url.apply(n.photoUrl()), campaign));
            if (n.statBlock() != null) {
                Optional<Source> source = sourceRepository.getSourceByCode(n.statBlock().source());
                source.flatMap(s -> monsterRepository.findByNameAndSource_Id(n.statBlock().name(), s.getId()))
                        .ifPresentOrElse(npc::useStatBlock,
                                () -> warnings.add("NPC " + n.name() + ": monster " + n.statBlock().name() + " not found"));
            }
            restoreInventory(n.inventory(), Inventory.forNpc(npc), warnings);
            references.put(ReferenceType.NPC + ":" + n.name(), npc);
        }

        if (!nullSafe(data.encyclopedia()).isEmpty()) {
            Encyclopedia encyclopedia = new Encyclopedia(campaign);
            for (CampaignExport.EntryData e : data.encyclopedia()) {
                EncyclopediaReferenceable reference = e.referenceType() == null ? null
                        : references.get(e.referenceType() + ":" + e.referenceName());
                encyclopedia.addEntry(e.entryName(), e.information(), e.type(), e.visible(),
                        reference == null ? null : e.referenceType(), reference);
            }
            encyclopediaRepository.save(encyclopedia);
        }

        Map<String, Character> charactersByName = new HashMap<>();
        for (CampaignExport.CharacterData d : nullSafe(data.characters())) {
            Character character = characterRepository.save(new Character(d.name(), campaign, master));
            if (!master.getName().equals(d.ownerName())) {
                warnings.add("Character " + d.name() + " belonged to " + d.ownerName() + "; it is yours now");
            }
            Sheet sheet = character.getSheet();
            if (d.attributes() != null && d.attributes().length == 6) {
                int[] a = d.attributes();
                sheet.updateAttributes(new Attributes(a[0], a[1], a[2], a[3], a[4], a[5]));
            }
            for (CampaignExport.ClassData cd : nullSafe(d.classes())) {
                Optional<DndClass> dndClass = dndClassRepository.findByClassNameAndSourceCode(cd.className(), cd.sourceCode());
                if (dndClass.isEmpty()) {
                    warnings.add(d.name() + ": class " + cd.className() + " (" + cd.sourceCode() + ") not found");
                    continue;
                }
                CharacterClass cc = sheet.addClass(dndClass.get(), cd.level());
                if (cd.subclass() != null) {
                    dndClass.get().getSubclasses().stream().filter(sc -> sc.getName().equals(cd.subclass())).findFirst()
                            .ifPresent(sc -> cc.chooseSubclass(sc, cd.subclassChosenAtLevel() == null
                                    ? dndClass.get().getSubclassUnlockLevel() : cd.subclassChosenAtLevel()));
                }
            }
            sheet.updateCombatStats(d.hpMax(), d.hpCurrent(), d.hpTemp(), d.armorClass(), d.speedFt());
            sheet.updateProficiencies(nullSafeSet(d.skillProficiencies()), nullSafeSet(d.skillExpertise()), d.savingThrows());
            sheet.updateConditions(d.conditions(), d.exhaustion());
            character.gainExperience(d.experiencePoints());
            character.changePortrait(url.apply(d.portraitUrl()));
            Origin origin = new Origin(d, warnings);
            character.chooseOrigin(origin.species, origin.background);
            for (CampaignExport.AttackData at : nullSafe(d.attacks())) {
                sheet.addAttack(at.name(), at.attackBonus(), at.damage(), at.damageType(), at.notes());
            }
            for (CampaignExport.Ref f : nullSafe(d.feats())) {
                sourceRepository.getSourceByCode(f.source())
                        .flatMap(s -> featRepository.findByNameAndSource_Id(f.name(), s.getId()))
                        .ifPresentOrElse(feat -> sheet.addCatalogFeat(feat, Math.max(1, sheet.getTotalLevel()), null),
                                () -> warnings.add(d.name() + ": feat " + f.name() + " not found"));
            }
            restoreInventory(d.inventory(), Inventory.forCharacter(character), warnings);
            charactersByName.put(d.name(), character);
        }

        for (CampaignExport.PartyData p : nullSafe(data.parties())) {
            Party draft = new Party(p.name(), campaign);
            draft.changeMaxSize(p.maxSize());
            Party party = partyRepository.save(draft); // ID ja vem do construtor: save faz merge e devolve a gerenciada
            for (String member : nullSafe(p.members())) {
                Character c = charactersByName.get(member);
                if (c != null) party.addMember(c);
            }
            restoreInventory(p.inventory(), Inventory.forParty(party), warnings);
        }

        return new CampaignImportResponse(campaign.getId(), campaign.getName(), campaign.getCode(),
                charactersByName.size(), nullSafe(data.npcs()).size(), nullSafe(data.worlds()).size(),
                nullSafe(data.encyclopedia()).size(), imageUrls.size(), warnings);
    }

    /** Especie e antecedente pelo nome + fonte. */
    private final class Origin {
        final top.customizr.system.model.Species species;
        final Background background;

        Origin(CampaignExport.CharacterData d, List<String> warnings) {
            this.species = d.species() == null ? null : sourceRepository.getSourceByCode(d.species().source())
                    .flatMap(s -> speciesRepository.findByNameAndSource_Id(d.species().name(), s.getId()))
                    .orElseGet(() -> { warnings.add(d.name() + ": species " + d.species().name() + " not found"); return null; });
            this.background = d.background() == null ? null : sourceRepository.getSourceByCode(d.background().source())
                    .flatMap(s -> backgroundRepository.findByNameAndSource_Id(d.background().name(), s.getId()))
                    .orElseGet(() -> { warnings.add(d.name() + ": background " + d.background().name() + " not found"); return null; });
        }
    }

    private void restoreInventory(CampaignExport.InventoryData data, Inventory inventory, List<String> warnings) {
        if (data == null) return;
        if (data.moneyGp() != null && data.moneyGp().signum() > 0) inventory.changeMoney(data.moneyGp());
        for (CampaignExport.ItemData item : nullSafe(data.items())) {
            Optional<Item> found = sourceRepository.getSourceByCode(item.source())
                    .flatMap(s -> itemRepository.findByNameAndSource_Id(item.name(), s.getId()));
            if (found.isPresent() && item.quantity() > 0) inventory.addItem(found.get(), item.quantity());
            else warnings.add("Item " + item.name() + " (" + item.source() + ") not found");
        }
        inventoryRepository.save(inventory);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <E extends Enum<E>> java.util.Set<E> nullSafeSet(java.util.Set<E> set) {
        return set == null ? java.util.Set.of() : set;
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
