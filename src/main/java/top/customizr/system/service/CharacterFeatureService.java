package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CharacterFeatureResponse;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.dto.ShareRequest;
import top.customizr.system.dto.SharedCard;
import top.customizr.system.dto.SharedKind;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.ClassFeature;
import top.customizr.system.model.Components;
import top.customizr.system.model.EntriesRenderer;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.Item;
import top.customizr.system.model.Monster;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SubclassFeature;
import top.customizr.system.repository.BackgroundRepository;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.InventoryRepository;
import top.customizr.system.repository.ItemRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.SpellRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tudo que o personagem tem (features de classe/subclasse pelo nivel, tracos da especie, do antecedente,
 * talentos, magias preparadas e itens) e as escolhas que a classe oferece; e "mandar pro chat" qualquer
 * um deles como cartao. O texto sai das descricoes cruas (com as tags {@...}) ja renderizado.
 */
@Service
public class CharacterFeatureService {

    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;
    private final FeatureRepository featureRepository;
    private final FeatDefinitionRepository featRepository;
    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final SpellRepository spellRepository;
    private final ItemRepository itemRepository;
    private final MonsterRepository monsterRepository;
    private final InventoryRepository inventoryRepository;
    private final ChatService chatService;
    private final MasterGuard masterGuard;
    private final ObjectMapper objectMapper;

    public CharacterFeatureService(CharacterRepository characterRepository, CampaignRepository campaignRepository,
                                   FeatureRepository featureRepository, FeatDefinitionRepository featRepository,
                                   SpeciesRepository speciesRepository, BackgroundRepository backgroundRepository,
                                   SpellRepository spellRepository, ItemRepository itemRepository,
                                   MonsterRepository monsterRepository, InventoryRepository inventoryRepository,
                                   ChatService chatService, MasterGuard masterGuard, ObjectMapper objectMapper) {
        this.characterRepository = characterRepository;
        this.campaignRepository = campaignRepository;
        this.featureRepository = featureRepository;
        this.featRepository = featRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.spellRepository = spellRepository;
        this.itemRepository = itemRepository;
        this.monsterRepository = monsterRepository;
        this.inventoryRepository = inventoryRepository;
        this.chatService = chatService;
        this.masterGuard = masterGuard;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ lista da ficha

    /** Quem ve: o dono, o mestre e os outros membros da campanha (pra consultar a ficha do colega). */
    @Transactional
    public List<CharacterFeatureResponse> list(UUID characterId, boolean includeAvailable, String username) {
        Character c = character(characterId);
        masterGuard.requireMember(username, c.getCampaign());
        List<CharacterFeatureResponse> out = new ArrayList<>();
        for (CharacterClass cc : c.getSheet().getClasses()) {
            String className = cc.getDndClass().getClassName();
            Set<UUID> selected = cc.getFeatureSelections().stream()
                    .map(s -> s.getFeature().getFeatureId()).collect(Collectors.toSet());
            List<ClassFeature> classFeatures = cc.getDndClass().getFeatures().stream()
                    .filter(g -> g.getLevel() <= cc.getLevel())
                    .sorted(Comparator.comparingInt(ClassFeature::getLevel)).toList();
            for (ClassFeature g : classFeatures) {
                add(out, g.getFeature(), className + " " + g.getLevel(), g.getLevel(), g.getChoiceGroup(), selected, cc, includeAvailable);
            }
            if (cc.getSubclass() != null) {
                String sub = cc.getSubclass().getName();
                for (SubclassFeature g : cc.getSubclass().getFeatures().stream()
                        .filter(g -> g.getLevel() <= cc.getLevel())
                        .sorted(Comparator.comparingInt(SubclassFeature::getLevel)).toList()) {
                    add(out, g.getFeature(), sub + " " + g.getLevel(), g.getLevel(), g.getChoiceGroup(), selected, cc, includeAvailable);
                }
            }
            for (Spell spell : cc.getPreparedSpells()) {
                out.add(new CharacterFeatureResponse(SharedKind.SPELL, spell.getSpellId(), spell.getName(),
                        "Spell (" + className + ")", spell.getLevel().getValue(), null, null, "GRANTED", cc.getId(), null));
            }
        }
        if (c.getSpecies() != null) {
            for (Feature f : c.getSpecies().getTraits()) {
                out.add(row(SharedKind.FEATURE, f.getFeatureId(), f.getName(), "Species: " + c.getSpecies().getName(),
                        null, f.getType().name(), null, "GRANTED", null, render(f.getDescription())));
            }
        }
        if (c.getBackground() != null) {
            for (Feature f : c.getBackground().getFeatures()) {
                out.add(row(SharedKind.FEATURE, f.getFeatureId(), f.getName(), "Background: " + c.getBackground().getName(),
                        null, f.getType().name(), null, "GRANTED", null, render(f.getDescription())));
            }
        }
        for (var cf : c.getSheet().getCatalogFeats()) {
            out.add(row(SharedKind.FEAT, cf.getFeat().getId(), cf.getFeat().getName(), "Feat", cf.getCharacterLevel(),
                    null, null, "GRANTED", null, cf.getFeat().getDescriptionText()));
        }
        inventoryRepository.findByCharacter(c).ifPresent(inv -> inv.getEntries().forEach(e ->
                out.add(row(SharedKind.ITEM, e.getItem().getId(), e.getItem().getName() + (e.getQuantity() > 1 ? " x" + e.getQuantity() : ""),
                        "Item", null, null, null, "GRANTED", null, itemText(e.getItem())))));
        return out;
    }

    private void add(List<CharacterFeatureResponse> out, Feature f, String origin, int level, String choiceGroup,
                     Set<UUID> selected, CharacterClass cc, boolean includeAvailable) {
        String status;
        if (f.getType() == FeatureType.AUTOMATIC) status = "GRANTED";
        else if (selected.contains(f.getFeatureId())) status = "SELECTED";
        else if (includeAvailable) status = "AVAILABLE";
        else return;
        out.add(row(SharedKind.FEATURE, f.getFeatureId(), f.getName(), origin, level, f.getType().name(), choiceGroup,
                status, cc.getId(), render(f.getDescription())));
    }

    private static CharacterFeatureResponse row(SharedKind kind, UUID id, String name, String origin, Integer level,
                                                String type, String choiceGroup, String status, UUID ccId, String text) {
        return new CharacterFeatureResponse(kind, id, name, origin, level, type, choiceGroup, status, ccId, text);
    }

    // ------------------------------------------------------------------ cartoes no chat

    /** Monta o cartao (sem mandar), pra mostrar na tela. */
    @Transactional
    public SharedCard card(SharedKind kind, UUID refId) {
        if (kind == null || refId == null) throw badRequest("Faltam o tipo (kind) e o id (refId)");
        return switch (kind) {
            case FEATURE -> {
                Feature f = featureRepository.findById(refId).orElseThrow(() -> notFound("característica"));
                yield new SharedCard(f.getName(), render(f.getDescription()));
            }
            case FEAT -> {
                var f = featRepository.findById(refId).orElseThrow(() -> notFound("talento"));
                yield new SharedCard(f.getName() + (f.getCategory() == null ? "" : " (talento " + f.getCategory() + ")"),
                        (f.getPrerequisite() == null ? "" : "Pré-requisito: " + f.getPrerequisite() + "\n\n")
                                + nonNull(f.getDescriptionText()));
            }
            case SPECIES -> {
                var s = speciesRepository.findById(refId).orElseThrow(() -> notFound("espécie"));
                yield new SharedCard(s.getName(), nonNull(s.getDescriptionText()));
            }
            case BACKGROUND -> {
                var b = backgroundRepository.findById(refId).orElseThrow(() -> notFound("antecedente"));
                yield new SharedCard(b.getName(), nonNull(b.getDescriptionText()));
            }
            case SPELL -> {
                Spell s = spellRepository.findById(refId).orElseThrow(() -> notFound("magia"));
                yield new SharedCard(s.getName(), spellText(s));
            }
            case ITEM -> {
                Item i = itemRepository.findById(refId).orElseThrow(() -> notFound("item"));
                yield new SharedCard(i.getName(), itemText(i));
            }
            case MONSTER -> {
                Monster m = monsterRepository.findById(refId).orElseThrow(() -> notFound("monstro"));
                yield new SharedCard(m.getName(), monsterText(m));
            }
        };
    }

    /**
     * Manda o cartao pro chat da campanha. Monstro so o mestre manda (ficha de monstro e segredo
     * ate o mestre querer mostrar). characterId = falar como o personagem (dele, ou o mestre).
     */
    @Transactional
    public ChatMessageResponse share(UUID campaignId, ShareRequest request, String username) {
        if (request == null) throw badRequest("Falta o corpo da requisição");
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(() -> notFound("campanha"));
        boolean master = masterGuard.requireMember(username, campaign);
        if (request.kind() == SharedKind.MONSTER && !master) {
            throw new AccessDeniedException("Só o mestre da campanha mostra um monstro");
        }
        SharedCard card = card(request.kind(), request.refId());
        return chatService.postCard(campaignId, request.characterId(), card.title(), card.text(), username);
    }

    /** Da ficha: a campanha vem do personagem e ele fala no chat (tem que ser seu, ou voce e o mestre). */
    @Transactional
    public ChatMessageResponse shareFromCharacter(UUID characterId, ShareRequest request, String username) {
        Character c = character(characterId);
        if (!c.getOwner().getName().equals(username) && !c.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Este personagem não é seu");
        }
        if (request == null) throw badRequest("Falta o corpo da requisição");
        return share(c.getCampaign().getId(), new ShareRequest(request.kind(), request.refId(), characterId), username);
    }

    // ------------------------------------------------------------------ textos

    /** Descricao crua do 5etools (JSON com tags) -> texto; se nao for JSON, devolve como esta. */
    String render(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("[") || trimmed.startsWith("{"))) return EntriesRenderer.renderText(raw);
        try {
            String text = EntriesRenderer.render(objectMapper.readTree(trimmed));
            return text == null ? "" : text;
        } catch (RuntimeException ex) {
            return raw;
        }
    }

    static String spellText(Spell s) {
        String level = s.getLevel().getValue() == 0 ? "Truque de " + TextoPt.school(s.getSchool()) : "Nível " + s.getLevel().getValue() + ", " + TextoPt.school(s.getSchool());
        String components = s.getComponents().stream().map(Components::name).map(n -> n.substring(0, 1)).collect(Collectors.joining(", "));
        String duration = switch (s.getDuration().getUnit()) {
            case INSTANTANEOUS -> "Instantânea";
            case UNTIL_DISPELLED -> "Até ser dissipada";
            case SPECIAL -> "Especial";
            case CONCENTRATION -> "Concentração";
            default -> s.getDuration().getAmount() + " " + TextoPt.timeUnit(s.getDuration().getUnit().name(), s.getDuration().getAmount());
        };
        return level + " | " + TextoPt.castingTime(s.getCastingTime()) + " | " + (s.getRangeFt() > 0 ? s.getRangeFt() + " pés" : "ver o texto")
                + " | " + components + " | " + (s.isConcentration() && s.getDuration().getUnit() != top.customizr.system.model.DurationUnit.CONCENTRATION ? "Concentração, " : "")
                + duration + "\n\n" + s.getDescription();
    }

    static String itemText(Item i) {
        List<String> parts = new ArrayList<>();
        if (i.getType() != null) parts.add(TextoPt.word(i.getType().name()));
        if (i.getCategory() != null) parts.add(TextoPt.word(i.getCategory().name()));
        if (i.getRarity() != null) parts.add(TextoPt.word(i.getRarity().name()));
        if (i.getPriceGp() != null) parts.add(i.getPriceGp().stripTrailingZeros().toPlainString() + " po");
        return String.join(", ", parts) + (i.getReferenceLink() == null ? "" : "\n" + i.getReferenceLink());
    }

    static String monsterText(Monster m) {
        StringBuilder b = new StringBuilder();
        b.append(TextoPt.word(nonNull(m.getSize()))).append(' ').append(TextoPt.word(nonNull(m.getCreatureType())))
                .append(m.getAlignment() == null ? "" : ", " + m.getAlignment()).append('\n');
        b.append("CA ").append(m.getArmorClass()).append(" | PV ").append(m.getHpAverage())
                .append(m.getHpFormula() == null ? "" : " (" + m.getHpFormula() + ")")
                .append(" | Deslocamento ").append(nonNull(m.getSpeed())).append(" | ND ").append(m.getChallengeRating()).append('\n');
        b.append("FOR ").append(m.getStrength()).append("  DES ").append(m.getDexterity()).append("  CON ").append(m.getConstitution())
                .append("  INT ").append(m.getIntelligence()).append("  SAB ").append(m.getWisdom()).append("  CAR ").append(m.getCharisma());
        for (String section : new String[]{m.getTraits(), m.getActions(), m.getBonusActions(), m.getReactions(), m.getLegendaryActions()}) {
            if (section != null) b.append("\n\n").append(section);
        }
        return b.toString();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private Character character(UUID id) {
        return characterRepository.findById(id).orElseThrow(() -> notFound("personagem"));
    }

    private ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " não encontrado");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
