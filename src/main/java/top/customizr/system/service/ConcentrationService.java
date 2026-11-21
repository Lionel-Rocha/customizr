package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Character;
import top.customizr.system.model.Condition;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.Monster;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.Token;
import top.customizr.system.model.TokenMarker;
import top.customizr.system.repository.TokenRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concentracao automatica (regra de 2024): quem esta com o marcador de concentracao e leva dano faz uma
 * salvaguarda de CON contra CD 10 ou metade do dano (o maior, ate 30). Falhou, cair a 0 PV ou ficar
 * incapacitado: perde a concentracao e o marcador sai. Vale pra qualquer dano (ataque, area, painel, ficha).
 */
@Service
public class ConcentrationService {

    /** Condicoes que derrubam a concentracao na hora (incapacitado e as que incluem incapacitado). */
    private static final EnumSet<Condition> BREAKS = EnumSet.of(Condition.INCAPACITATED, Condition.PARALYZED,
            Condition.PETRIFIED, Condition.STUNNED, Condition.UNCONSCIOUS);

    private final TokenRepository tokenRepository;
    private final CharacterSheetService sheetService;
    private final ChatService chatService;
    private final LiveTableService liveTable;

    public ConcentrationService(TokenRepository tokenRepository, CharacterSheetService sheetService, ChatService chatService,
                                LiveTableService liveTable) {
        this.tokenRepository = tokenRepository;
        this.sheetService = sheetService;
        this.chatService = chatService;
        this.liveTable = liveTable;
    }

    /** Resultado pra quem aplicou o dano anotar (ex.: nas notas do ataque). */
    public record Check(String tokenName, int dc, Integer total, boolean kept, String reason) {
        public String note() {
            return kept ? tokenName + " mantém a concentração (" + total + " contra CD " + dc + ")"
                    : tokenName + " perde a concentração" + (reason == null ? " (" + total + " contra CD " + dc + ")" : " (" + reason + ")");
        }
    }

    /** O marcador de concentracao ("Concentração", "Concentration", "Concentrating"...). */
    public static boolean isConcentration(TokenMarker m) {
        return m.getLabel().toLowerCase(Locale.ROOT).startsWith("concentra");
    }

    /** Dano num token: confere a concentracao dele. Sem marcador ou sem dano, nada acontece. */
    @Transactional
    public Optional<Check> afterDamage(Token token, int damage, String username) {
        if (damage <= 0 || token.getMarkers().stream().noneMatch(ConcentrationService::isConcentration)) return Optional.empty();
        int dc = Math.min(30, Math.max(10, damage / 2));
        Integer hp = token.getCharacter() != null ? token.getCharacter().getSheet().getHpCurrent() : token.getHpCurrent();
        String broken = hp != null && hp == 0 ? "caiu a 0 PV"
                : token.activeConditions().stream().anyMatch(BREAKS::contains) ? "incapacitado" : null;
        String speaker = token.isHiddenFromPlayers() ? "Criatura oculta" : token.getName();
        Check check;
        RollResult roll = null;
        if (broken != null) {
            check = new Check(speaker, dc, null, false, broken);
        } else {
            roll = conSave(token, username);
            check = new Check(speaker, dc, roll.total(), roll.total() >= dc, null);
        }
        if (!check.kept()) {
            token.replaceMarkers(token.getMarkers().stream().filter(m -> !isConcentration(m)).toList());
            liveTable.broadcastTokenChange(token);
        }
        UUID characterId = token.getCharacter() == null ? null : token.getCharacter().getCharacterId();
        String label = speaker + " - Concentração (CD " + dc + ")";
        if (roll != null) {
            chatService.postRoll(token.getScene().getCampaign().getId(), characterId, label, roll,
                    check.kept() ? " | MANTEVE" : " | PERDEU", username);
        } else {
            chatService.postCard(token.getScene().getCampaign().getId(), characterId, label, check.note(), username);
        }
        return Optional.of(check);
    }

    /** Dano pela ficha (sem token na mao): confere a concentracao em cada token desse personagem. */
    @Transactional
    public List<Check> afterCharacterDamage(Character character, int damage, String username) {
        List<Check> checks = new ArrayList<>();
        for (Token t : tokenRepository.findByCharacter(character)) afterDamage(t, damage, username).ifPresent(checks::add);
        return checks;
    }

    /** Salvaguarda de CON: personagem pela ficha (proficiencia, exaustao...), monstro pela ficha de monstro. */
    private RollResult conSave(Token token, String username) {
        if (token.getCharacter() != null) {
            SheetRollResponse r = sheetService.rollFor(token.getCharacter(),
                    new SheetRollRequest(SheetRollRequest.RollKind.SAVE, null, AbilityScore.CONSTITUTION, null, false), username);
            return r.roll();
        }
        Monster stats = token.statBlock();
        int bonus = stats == null ? 0 : writtenBonus(stats.getSavingThrows(), "con")
                .orElse(Attributes.modifierOf(stats.getConstitution()));
        return DiceRoller.roll("1d20" + (bonus == 0 ? "" : bonus > 0 ? "+" + bonus : String.valueOf(bonus)));
    }

    private static Optional<Integer> writtenBonus(String list, String key) {
        if (list == null) return Optional.empty();
        Matcher m = Pattern.compile("(?i)\\b" + key + "\\s*([+-]\\d+)").matcher(list);
        return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
    }
}
