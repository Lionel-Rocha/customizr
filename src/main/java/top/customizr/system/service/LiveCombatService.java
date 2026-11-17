package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AttackRollRequest;
import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.dto.TokenAttackOption;
import top.customizr.system.dto.TokenAttackRequest;
import top.customizr.system.dto.TokenAttackResponse;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.TemplateResolveRequest;
import top.customizr.system.dto.TemplateResolveResponse;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.AreaTemplate;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.TokenLayer;
import top.customizr.system.model.Condition;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.Monster;
import top.customizr.system.model.MonsterAttacks;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.SheetAttack;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Token;
import top.customizr.system.model.User;
import top.customizr.system.repository.TokenRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combate na mesa: ataque de um token em outro (personagem pela ficha, monstro pela ficha de monstro),
 * dano/cura e condicoes no token. Quem controla o token: o mestre, ou o dono do personagem.
 * Toda mudanca de PV/condicao vai pra mesa ao vivo, e os ataques vao pro chat.
 */
@Service
public class LiveCombatService {

    /** Condicoes de quem ataca que dao desvantagem no ataque. */
    private static final Set<Condition> ATTACKER_DISADVANTAGE = EnumSet.of(
            Condition.POISONED, Condition.FRIGHTENED, Condition.BLINDED, Condition.PRONE, Condition.RESTRAINED);
    /** Condicoes do alvo que dao vantagem a quem ataca. */
    private static final Set<Condition> TARGET_GRANTS_ADVANTAGE = EnumSet.of(
            Condition.BLINDED, Condition.PARALYZED, Condition.PETRIFIED, Condition.RESTRAINED,
            Condition.STUNNED, Condition.UNCONSCIOUS);

    private final TokenRepository tokenRepository;
    private final MasterGuard masterGuard;
    private final CharacterSheetService sheetService;
    private final CharacterService characterService;
    private final ChatService chatService;
    private final LiveTableService liveTable;
    private final top.customizr.system.repository.AreaTemplateRepository templateRepository;

    public LiveCombatService(TokenRepository tokenRepository, MasterGuard masterGuard, CharacterSheetService sheetService,
                             CharacterService characterService, ChatService chatService, LiveTableService liveTable,
                             top.customizr.system.repository.AreaTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
        this.tokenRepository = tokenRepository;
        this.masterGuard = masterGuard;
        this.sheetService = sheetService;
        this.characterService = characterService;
        this.chatService = chatService;
        this.liveTable = liveTable;
    }

    /** Ataques do token: os da ficha do personagem, ou as acoes da ficha de monstro. */
    @Transactional
    public List<TokenAttackOption> attacks(UUID tokenId, String username) {
        Token token = token(tokenId);
        requireControl(token, username);
        if (token.getCharacter() != null) {
            return token.getCharacter().getSheet().getAttacks().stream().map(TokenAttackOption::from).toList();
        }
        return MonsterAttacks.of(token.statBlock()).stream().map(TokenAttackOption::from).toList();
    }

    @Transactional
    public TokenAttackResponse attack(UUID tokenId, TokenAttackRequest request, String username) {
        Token attacker = token(tokenId);
        boolean master = requireControl(attacker, username);
        if (request == null || request.attackId() == null || request.attackId().isBlank()) {
            throw badRequest("attackId is required");
        }
        Token target = null;
        if (request.targetTokenId() != null) {
            target = token(request.targetTokenId());
            if (!master && target.isHiddenFromPlayers()) throw notFound("Token");
            if (!target.getScene().getId().equals(attacker.getScene().getId())) {
                throw badRequest("The target is in another scene");
            }
        }
        List<String> notes = new ArrayList<>();
        RollMode mode = request.mode() == null ? RollMode.NORMAL : request.mode();
        Integer targetAc = target == null ? null : target.armorClass();
        if (target != null && attacker.elevation() != target.elevation()) {
            // com altura diferente, a distancia de verdade (pra conferir alcance) vai na nota
            var scene = attacker.getScene();
            double half = scene.getGridSizePx() / 2.0;
            Integer flat = scene.distanceFeet(attacker.getX() + half * attacker.getSizeCells(), attacker.getY() + half * attacker.getSizeCells(),
                    target.getX() + half * target.getSizeCells(), target.getY() + half * target.getSizeCells());
            int up = Math.abs(attacker.elevation() - target.elevation());
            if (flat != null) {
                long total = Math.round(Math.hypot(flat, up) / 5.0) * 5;
                notes.add("Distance " + total + " ft (" + up + " ft height difference)");
            }
        }

        String attackName;
        RollResult attackRoll;
        RollResult damageRoll;
        Boolean hit;
        String outcome;
        String damageType;
        if (attacker.getCharacter() != null) {
            SheetAttack sheetAttack = attacker.getCharacter().getSheet().getAttacks().stream()
                    .filter(a -> a.getId().toString().equals(request.attackId())).findFirst()
                    .orElseThrow(() -> notFound("Attack"));
            // condicoes de quem ataca (envenenado, caido...) a ficha ja aplica; aqui entram invisivel e o alvo
            mode = withTargetConditions(withInvisibleAttacker(mode, attacker, notes), target, null, notes);
            SheetRollResponse rolled = sheetService.rollAttack(attacker.getCharacter().getCharacterId(), sheetAttack.getId(),
                    new AttackRollRequest(mode, targetAc, false), username);
            notes.addAll(rolled.notes());
            attackName = sheetAttack.getName();
            attackRoll = rolled.roll();
            damageRoll = rolled.damage();
            hit = rolled.hit();
            outcome = rolled.outcome();
            damageType = sheetAttack.getDamageType();
        } else {
            MonsterAttacks.Attack action = monsterAttack(attacker, request.attackId());
            attackName = action.name();
            damageType = action.damageType();
            if (action.kind() == MonsterAttacks.Kind.ATTACK) {
                mode = withAttackerConditions(mode, attacker, notes);
                mode = withTargetConditions(withInvisibleAttacker(mode, attacker, notes), target, action.melee(), notes);
                attackRoll = DiceRoller.roll(d20(mode) + signed(action.attackBonus()));
                boolean critical = attackRoll.isCriticalHit();
                damageRoll = action.damage() == null || attackRoll.isCriticalFail() ? null
                        : DiceRoller.roll(critical ? DiceRoller.doubleDice(action.damage()) : action.damage());
                hit = targetAc == null ? null : !attackRoll.isCriticalFail() && (critical || attackRoll.total() >= targetAc);
                outcome = critical ? "CRITICAL_HIT" : attackRoll.isCriticalFail() ? "CRITICAL_MISS" : null;
            } else {
                // salvaguarda: so o dano e rolado; quem decide (e aplica, se for o caso) e o mestre
                attackRoll = null;
                damageRoll = action.damage() == null ? null : DiceRoller.roll(action.damage());
                hit = null;
                outcome = null;
                notes.add("DC " + action.saveDc() + (action.saveAbility() == null ? "" : " " + action.saveAbility()) + " save");
            }
        }

        Integer applied = null;
        boolean apply = request.applyDamage() == null || request.applyDamage();
        if (apply && target != null && Boolean.TRUE.equals(hit) && damageRoll != null) {
            applied = applyDamage(target, damageRoll.total(), damageType, notes);
        }

        // token oculto atacando: o chat nao entrega quem e
        String label = (attacker.isHiddenFromPlayers() ? "Unseen creature" : attacker.getName()) + (target == null ? "" : " -> " + target.getName()) + ": " + attackName
                + (damageType == null ? "" : " (" + damageType + ")");
        RollResult shown = attackRoll != null ? attackRoll : damageRoll;
        UUID chatId = null;
        if (shown != null) {
            String extra = (attackRoll != null && damageRoll != null ? " | damage: " + damageRoll.detail() : "")
                    + (hit == null ? "" : hit ? " | HIT" : " | MISS")
                    + (outcome == null ? "" : " | " + outcome)
                    + (applied == null ? "" : " | -" + applied + " HP")
                    + (notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")");
            chatId = chatService.postRoll(attacker.getScene().getCampaign().getId(),
                    attacker.getCharacter() == null ? null : attacker.getCharacter().getCharacterId(),
                    label, shown, extra, username).id();
        }
        return new TokenAttackResponse(label, attackRoll, damageRoll, hit, outcome, notes,
                target == null ? null : target.getId(), target == null ? null : target.getName(), applied, chatId);
    }

    /**
     * Teste pelo token: personagem rola pela ficha; monstro/NPC pela ficha de monstro (salvaguardas e pericias
     * com o bonus escrito na ficha, ou o modificador do atributo). Vai pro chat.
     */
    @Transactional
    public SheetRollResponse roll(UUID tokenId, SheetRollRequest request, String username) {
        Token token = token(tokenId);
        requireControl(token, username);
        if (request == null || request.kind() == null) throw badRequest("Roll kind is required");
        if (token.getCharacter() != null) {
            return sheetService.roll(token.getCharacter().getCharacterId(),
                    new SheetRollRequest(request.kind(), request.skill(), request.ability(), request.mode(), true), username);
        }
        MonsterRoll rolled = monsterRoll(token, request);
        String speaker = token.isHiddenFromPlayers() ? "Unseen creature" : token.getName();
        String extra = (rolled.outcome() == null ? "" : " | " + rolled.outcome())
                + (rolled.notes().isEmpty() ? "" : " (" + String.join("; ", rolled.notes()) + ")");
        UUID chatId = chatService.postRoll(token.getScene().getCampaign().getId(), null, speaker + " - " + rolled.label(),
                rolled.roll(), extra, username).id();
        return new SheetRollResponse(speaker + " - " + rolled.label(), rolled.roll(), null, null, rolled.outcome(),
                rolled.notes(), chatId);
    }

    private record MonsterRoll(String label, RollResult roll, String outcome, List<String> notes) {}

    /** Rolagem pela ficha de monstro, sem chat. */
    private MonsterRoll monsterRoll(Token token, SheetRollRequest request) {
        Monster stats = token.statBlock();
        if (stats == null) throw badRequest("This token has no stat block to roll from");
        List<String> notes = new ArrayList<>();
        RollMode mode = request.mode() == null ? RollMode.NORMAL : request.mode();
        Set<Condition> conditions = token.activeConditions();
        String label;
        int bonus;
        String outcome = null;
        switch (request.kind()) {
            case SKILL -> {
                Skill skill = request.skill();
                if (skill == null) throw badRequest("Skill is required");
                label = skill.getLabel();
                Integer written = writtenBonus(stats.getSkills(), skill.getLabel());
                bonus = written != null ? written : Attributes.modifierOf(score(stats, skill.getAbility()));
                mode = poisonedCheck(mode, conditions, notes);
            }
            case ABILITY -> {
                AbilityScore ability = requireAbility(request.ability());
                label = capitalize(ability.getKey()) + " check";
                bonus = Attributes.modifierOf(score(stats, ability));
                mode = poisonedCheck(mode, conditions, notes);
            }
            case SAVE -> {
                AbilityScore ability = requireAbility(request.ability());
                label = capitalize(ability.getKey()) + " save";
                Integer written = writtenBonus(stats.getSavingThrows(), ability.getKey().substring(0, 3));
                bonus = written != null ? written : Attributes.modifierOf(score(stats, ability));
                boolean strDex = ability == AbilityScore.STRENGTH || ability == AbilityScore.DEXTERITY;
                String failing = names(conditions, EnumSet.of(Condition.PARALYZED, Condition.PETRIFIED,
                        Condition.STUNNED, Condition.UNCONSCIOUS));
                if (strDex && !failing.isEmpty()) {
                    outcome = "AUTOMATIC_FAILURE";
                    notes.add("Automatic failure: " + failing);
                }
                if (ability == AbilityScore.DEXTERITY && conditions.contains(Condition.RESTRAINED)) {
                    notes.add("Disadvantage: restrained");
                    mode = combine(mode, RollMode.DISADVANTAGE);
                }
            }
            case INITIATIVE -> {
                label = "Initiative";
                bonus = Attributes.modifierOf(stats.getDexterity());
            }
            default -> throw badRequest("Monsters don't make death saves");
        }
        return new MonsterRoll(label, DiceRoller.roll(d20(mode) + signed(bonus)), outcome, notes);
    }

    // ------------------------------------------------------------------ areas de efeito

    /**
     * Resolve uma area: quem esta dentro faz a salvaguarda (personagem pela ficha, monstro pela ficha de monstro),
     * o dano e rolado uma vez e cada um leva inteiro, metade ou nada. Quem colocou a area ou o mestre resolve.
     */
    @Transactional
    public TemplateResolveResponse resolveTemplate(UUID templateId, TemplateResolveRequest request, String username) {
        AreaTemplate template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Area"));
        Campaign campaign = template.getScene().getCampaign();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, campaign);
        boolean master = masterGuard.canManage(user, campaign);
        if (!master && !template.getOwnerId().equals(user.getUserId())) {
            throw new AccessDeniedException("Only who placed the area or the master resolves it");
        }
        if (request == null) throw badRequest("Request body is required");
        AbilityScore ability = request.ability();
        Integer dc = request.dc();
        String damage = request.damage();
        String damageType = request.damageType();
        String label = template.getLabel() == null ? "Area" : template.getLabel();
        Token attacker = null;
        if (request.attackerTokenId() != null) {
            attacker = token(request.attackerTokenId());
            requireControl(attacker, username);
            if (request.attackId() != null) {
                MonsterAttacks.Attack action = monsterAttack(attacker, request.attackId());
                if (action.kind() != MonsterAttacks.Kind.SAVE) throw badRequest("This action is not a saving throw");
                dc = action.saveDc();
                ability = abilityOf(action.saveAbility());
                damage = action.damage();
                damageType = action.damageType();
                label = action.name();
            }
        }
        if (ability == null || dc == null) throw badRequest("ability and dc are required");
        RollResult damageRoll = null;
        if (damage != null && !damage.isBlank()) {
            try {
                damageRoll = DiceRoller.roll(damage.replaceAll("\\s+", ""));
            } catch (IllegalArgumentException ex) {
                throw badRequest(ex.getMessage());
            }
        }
        boolean half = request.halfOnSuccess() == null || request.halfOnSuccess();
        boolean apply = request.applyDamage() == null || request.applyDamage();
        List<TemplateResolveResponse.Target> results = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (Token target : template.getScene().getTokens()) {
            if (attacker != null && target.getId().equals(attacker.getId())) continue;
            if (target.getLayer() != TokenLayer.BATTLE || !template.covers(target)) continue;
            SheetRollRequest save = new SheetRollRequest(SheetRollRequest.RollKind.SAVE, null, ability, null, false);
            RollResult roll;
            String outcome;
            if (target.getCharacter() != null) {
                SheetRollResponse r = sheetService.rollFor(target.getCharacter(), save, username);
                roll = r.roll();
                outcome = r.outcome();
            } else if (target.statBlock() != null) {
                MonsterRoll r = monsterRoll(target, save);
                roll = r.roll();
                outcome = r.outcome();
            } else {
                continue; // token sem ficha (cenario, marcador) nao faz salvaguarda
            }
            boolean autoFail = "AUTOMATIC_FAILURE".equals(outcome);
            boolean saved = !autoFail && roll.total() >= dc;
            Integer applied = null;
            if (damageRoll != null) {
                int amount = saved ? (half ? damageRoll.total() / 2 : 0) : damageRoll.total();
                List<String> notes = new ArrayList<>();
                applied = apply ? applyDamage(target, amount, damageType, notes) : amount;
            }
            String name = target.isHiddenFromPlayers() ? "Unseen creature" : target.getName();
            results.add(new TemplateResolveResponse.Target(target.getId(), target.getName(), roll.total(), saved, autoFail, applied));
            lines.add(name + ": " + roll.total() + (autoFail ? " (automatic failure)" : saved ? " saved" : " failed")
                    + (applied == null ? "" : " -> " + applied + " " + (damageType == null ? "damage" : damageType)));
        }
        String abilityShort = ability.getKey().substring(0, 3).toUpperCase(Locale.ROOT);
        String title = label + " (DC " + dc + " " + abilityShort + (damageRoll == null ? "" : ", damage " + damageRoll.detail()) + ")";
        UUID chatId = chatService.postCard(campaign.getId(), attacker != null && attacker.getCharacter() != null
                        ? attacker.getCharacter().getCharacterId() : null, title,
                lines.isEmpty() ? "Nobody in the area." : String.join("\n", lines), username).id();
        return new TemplateResolveResponse(label, dc, abilityShort, damageRoll, results, chatId);
    }

    private static AbilityScore abilityOf(String shortName) {
        if (shortName == null) return AbilityScore.DEXTERITY;
        for (AbilityScore a : AbilityScore.values()) {
            if (a.getKey().substring(0, 3).equalsIgnoreCase(shortName)) return a;
        }
        return AbilityScore.DEXTERITY;
    }
    /** "DEX +6, WIS +7" / "Perception +13, Stealth +6": o bonus de uma entrada, se estiver escrito. */
    private static Integer writtenBonus(String list, String key) {
        if (list == null) return null;
        Matcher m = Pattern.compile("(?i)\\b" + Pattern.quote(key) + "\\s*([+-]\\d+)").matcher(list);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static int score(Monster m, AbilityScore ability) {
        return switch (ability) {
            case STRENGTH -> m.getStrength();
            case DEXTERITY -> m.getDexterity();
            case CONSTITUTION -> m.getConstitution();
            case INTELLIGENCE -> m.getIntelligence();
            case WISDOM -> m.getWisdom();
            case CHARISMA -> m.getCharisma();
        };
    }

    private static RollMode poisonedCheck(RollMode mode, Set<Condition> conditions, List<String> notes) {
        if (!conditions.contains(Condition.POISONED)) return mode;
        notes.add("Disadvantage: poisoned");
        return combine(mode, RollMode.DISADVANTAGE);
    }

    private static AbilityScore requireAbility(AbilityScore ability) {
        if (ability == null) throw badRequest("Ability is required");
        return ability;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : java.lang.Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Dano / cura / PV temporario no token. Personagem: vai na ficha. NPC/monstro: no PV do token. */
    @Transactional
    public TokenResponse changeHitPoints(UUID tokenId, HitPointChangeRequest request, String username) {
        Token token = token(tokenId);
        boolean master = requireControl(token, username);
        if (request == null || request.type() == null) throw badRequest("Hit point change type is required");
        if (token.getCharacter() != null) {
            characterService.changeHitPoints(token.getCharacter().getCharacterId(), request, username);
            return TokenResponse.from(token, master);
        }
        try {
            switch (request.type()) {
                case DAMAGE -> token.damage(request.amount());
                case HEALING -> token.heal(request.amount());
                case TEMPORARY -> throw badRequest("Temporary HP is only tracked on character sheets");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw badRequest(ex.getMessage());
        }
        liveTable.broadcastTokenChange(token);
        return TokenResponse.from(token, master);
    }

    /** Condicoes do token. Personagem: vai na ficha. NPC/monstro: ficam no token. */
    @Transactional
    public TokenResponse updateConditions(UUID tokenId, ConditionsRequest request, String username) {
        Token token = token(tokenId);
        boolean master = requireControl(token, username);
        if (request == null) throw badRequest("Request body is required");
        if (token.getCharacter() != null) {
            sheetService.updateConditions(token.getCharacter().getCharacterId(), request, username);
            return TokenResponse.from(token, master);
        }
        token.updateConditions(request.conditions());
        liveTable.broadcastTokenChange(token);
        return TokenResponse.from(token, master);
    }

    // ------------------------------------------------------------------ regras

    /** Aplica o dano no alvo, com resistencia/imunidade/vulnerabilidade do monstro. Devolve o que saiu do PV. */
    private Integer applyDamage(Token target, int amount, String damageType, List<String> notes) {
        int finalAmount = amount;
        Monster stats = target.getCharacter() == null ? target.statBlock() : null;
        if (stats != null && damageType != null) {
            String type = damageType.toLowerCase(Locale.ROOT);
            if (mentions(stats.getDamageImmunities(), type)) {
                finalAmount = 0;
                notes.add(target.getName() + " is immune to " + type);
            } else if (mentions(stats.getDamageResistances(), type)) {
                finalAmount = amount / 2;
                notes.add(target.getName() + " resists " + type);
            } else if (mentions(stats.getDamageVulnerabilities(), type)) {
                finalAmount = amount * 2;
                notes.add(target.getName() + " is vulnerable to " + type);
            }
        }
        try {
            if (target.getCharacter() != null) {
                target.getCharacter().getSheet().applyDamage(finalAmount);
                liveTable.characterChanged(target.getCharacter());
                if (target.getCharacter().getSheet().getHpCurrent() == 0) notes.add(target.getName() + " drops to 0 HP");
            } else if (target.getHpCurrent() != null) {
                target.damage(finalAmount);
                liveTable.broadcastTokenChange(target);
                if (target.getHpCurrent() == 0) notes.add(target.getName() + " drops to 0 HP");
            } else {
                notes.add(target.getName() + " has no HP to track");
                return null;
            }
        } catch (IllegalStateException ex) {
            notes.add(target.getName() + " has no HP to track");
            return null;
        }
        return finalAmount;
    }

    private static boolean mentions(String list, String type) {
        return list != null && list.toLowerCase(Locale.ROOT).contains(type);
    }

    private static RollMode withAttackerConditions(RollMode mode, Token attacker, List<String> notes) {
        String found = names(attacker.activeConditions(), ATTACKER_DISADVANTAGE);
        if (found.isEmpty()) return mode;
        notes.add("Disadvantage: " + found);
        return combine(mode, RollMode.DISADVANTAGE);
    }

    private static RollMode withInvisibleAttacker(RollMode mode, Token attacker, List<String> notes) {
        if (!attacker.activeConditions().contains(Condition.INVISIBLE)) return mode;
        notes.add("Advantage: attacker invisible");
        return combine(mode, RollMode.ADVANTAGE);
    }

    /** Alvo cego/paralisado/contido/atordoado/inconsciente/petrificado: vantagem; invisivel: desvantagem;
     * caido: vantagem corpo a corpo, desvantagem a distancia (so quando se sabe o tipo do ataque). */
    private static RollMode withTargetConditions(RollMode mode, Token target, Boolean melee, List<String> notes) {
        if (target == null) return mode;
        Set<Condition> conditions = target.activeConditions();
        String helps = names(conditions, TARGET_GRANTS_ADVANTAGE);
        if (!helps.isEmpty()) {
            notes.add("Advantage: target " + helps);
            mode = combine(mode, RollMode.ADVANTAGE);
        }
        if (conditions.contains(Condition.PRONE) && melee != null) {
            notes.add((melee ? "Advantage" : "Disadvantage") + ": target prone");
            mode = combine(mode, melee ? RollMode.ADVANTAGE : RollMode.DISADVANTAGE);
        }
        if (conditions.contains(Condition.INVISIBLE)) {
            notes.add("Disadvantage: target invisible");
            mode = combine(mode, RollMode.DISADVANTAGE);
        }
        return mode;
    }

    /** Vantagem + desvantagem se anulam (mesma regra da ficha). */
    private static RollMode combine(RollMode a, RollMode b) {
        if (a == RollMode.NORMAL) return b;
        if (b == RollMode.NORMAL || a == b) return a;
        return RollMode.NORMAL;
    }

    private static String names(Set<Condition> conditions, Set<Condition> filter) {
        return conditions.stream().filter(filter::contains).map(c -> c.name().toLowerCase(Locale.ROOT)).sorted()
                .reduce((x, y) -> x + ", " + y).orElse("");
    }

    private static String d20(RollMode mode) {
        return switch (mode) {
            case ADVANTAGE -> "2d20kh1";
            case DISADVANTAGE -> "2d20kl1";
            default -> "1d20";
        };
    }

    private static String signed(Integer value) {
        return value == null || value == 0 ? "" : value > 0 ? "+" + value : String.valueOf(value);
    }

    private MonsterAttacks.Attack monsterAttack(Token attacker, String attackId) {
        if (!attackId.startsWith("m:")) throw notFound("Attack");
        int index;
        try {
            index = Integer.parseInt(attackId.substring(2));
        } catch (NumberFormatException ex) {
            throw notFound("Attack");
        }
        return MonsterAttacks.of(attacker.statBlock()).stream().filter(a -> a.index() == index).findFirst()
                .orElseThrow(() -> notFound("Attack"));
    }

    // ------------------------------------------------------------------ acesso

    /** Mestre controla tudo; jogador, so o token do proprio personagem. Devolve se e o mestre. */
    private boolean requireControl(Token token, String username) {
        Campaign campaign = token.getScene().getCampaign();
        User user = masterGuard.user(username);
        if (masterGuard.canManage(user, campaign)) return true;
        masterGuard.assertMember(user, campaign);
        if (!token.controlledBy(user.getUserId())) {
            throw new AccessDeniedException("You can only act with your own character's token");
        }
        return false;
    }

    private Token token(UUID id) {
        return tokenRepository.findById(id).orElseThrow(() -> notFound("Token"));
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
