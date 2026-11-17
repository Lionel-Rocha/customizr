package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AbilityRollRequest;
import top.customizr.system.dto.AbilityRollResponse;
import top.customizr.system.dto.PointBuyRequest;
import top.customizr.system.model.AbilityScoreGenerator;
import top.customizr.system.model.RollResult;
import top.customizr.system.service.ChatService;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerar atributos: rolar (configuravel), array padrao e compra de pontos. Pra gravar na ficha,
 * use PUT /character/{id}/attributes com os valores escolhidos.
 */
@RestController
@RequestMapping("/ability-scores")
public class AbilityScoreController {

    private final ChatService chatService;

    public AbilityScoreController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/roll")
    public AbilityRollResponse roll(@RequestBody(required = false) AbilityRollRequest request,
                                    @AuthenticationPrincipal UserDetails loggedUser) {
        AbilityRollRequest r = request == null ? new AbilityRollRequest(null, null, null, null, null, null, null) : request;
        int dice = r.dice() == null ? 4 : r.dice();
        int sides = r.sides() == null ? 6 : r.sides();
        int drop = r.dropLowest() == null ? 1 : r.dropLowest();
        int sets = r.sets() == null ? 6 : r.sets();
        int reroll = r.rerollOnesOrBelow() == null ? 0 : r.rerollOnesOrBelow();
        List<AbilityScoreGenerator.RolledScore> rolls;
        try {
            rolls = AbilityScoreGenerator.roll(dice, sides, drop, sets, reroll,
                    faces -> ThreadLocalRandom.current().nextInt(1, faces + 1));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String method = dice + "d" + sides + (drop > 0 ? " drop lowest " + drop : "")
                + (reroll > 0 ? ", reroll " + reroll + " or less" : "");
        List<Integer> scores = rolls.stream().map(AbilityScoreGenerator.RolledScore::total)
                .sorted(Comparator.reverseOrder()).toList();
        int total = scores.stream().mapToInt(Integer::intValue).sum();
        UUID messageId = null;
        if (r.campaignId() != null) {
            StringBuilder detail = new StringBuilder();
            for (AbilityScoreGenerator.RolledScore s : rolls) {
                if (!detail.isEmpty()) detail.append(" | ");
                detail.append(s.total()).append(" ").append(s.kept());
                if (!s.dropped().isEmpty()) detail.append(" (dropped ").append(s.dropped()).append(")");
            }
            messageId = chatService.postRoll(r.campaignId(), r.characterId(), "Ability scores (" + method + ")",
                    new RollResult(method, total, detail + " = " + scores, null), null, loggedUser.getUsername()).id();
        }
        return new AbilityRollResponse(method, rolls, scores, total, messageId);
    }

    @GetMapping("/standard-array")
    public List<Integer> standardArray() {
        return AbilityScoreGenerator.STANDARD_ARRAY;
    }

    /** Confere uma distribuicao de compra de pontos (custo, quanto sobra, problemas). */
    @PostMapping("/point-buy")
    public AbilityScoreGenerator.PointBuyResult pointBuy(@RequestBody PointBuyRequest request) {
        return AbilityScoreGenerator.pointBuy(request == null ? null : request.scores(), request == null ? null : request.budget());
    }
}
