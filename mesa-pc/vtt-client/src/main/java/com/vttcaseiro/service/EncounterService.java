package com.vttcaseiro.service;

import com.vttcaseiro.model.Encounter;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras do rastreador de combate, sem tela nem rede (testavel):
 * ordem por iniciativa, avancar turno, contar rodadas e vencer efeitos com
 * duracao. Quem chama (TableService) cuida de tirar o efeito vencido do
 * token/ficha e de mandar o encontro pela rede.
 */
public final class EncounterService {

    private EncounterService() {
    }

    /** Resultado de avancar um turno. */
    public record TurnResult(TokenModel current, int round, boolean newRound, List<Encounter.TimedEffect> expired) {
    }

    /** Maior iniciativa primeiro; empate = maior bonus; sem iniciativa vai pro fim. */
    public static List<TokenModel> order(SceneModel scene) {
        List<TokenModel> tokens = new ArrayList<>(scene.getTokens());
        tokens.sort(Comparator
                .comparing((TokenModel t) -> t.getInitiative() == null)
                .thenComparing((TokenModel t) -> t.getInitiative() == null ? 0 : -t.getInitiative())
                .thenComparing(t -> -t.getInitiativeBonus())
                .thenComparing(t -> t.getName() == null ? "" : t.getName(), String.CASE_INSENSITIVE_ORDER));
        return tokens;
    }

    /** Grupo do token na iniciativa: monstros da mesma ficha (null = sozinho; personagens nunca agrupam). */
    public static String groupKey(TokenModel t) {
        return t.getMobSheetId() != null && t.getCharacterSheetId() == null ? "mob:" + t.getMobSheetId() : null;
    }

    /** Mesmo grupo e mesma iniciativa: jogam juntos. */
    static boolean sameTurn(TokenModel a, TokenModel b) {
        String ka = groupKey(a);
        return ka != null && ka.equals(groupKey(b)) && java.util.Objects.equals(a.getInitiative(), b.getInitiative());
    }

    /**
     * A ordem juntando os grupos (com "agrupar monstros" ligado): cada item e a lista de quem joga junto. Desligado,
     * cada token e um item.
     */
    public static List<List<TokenModel>> groupedOrder(SceneModel scene) {
        List<List<TokenModel>> out = new ArrayList<>();
        boolean group = scene.getEncounter().isGroupMonsters();
        for (TokenModel t : order(scene)) {
            if (group && !out.isEmpty() && sameTurn(out.get(out.size() - 1).get(0), t)) out.get(out.size() - 1).add(t);
            else out.add(new ArrayList<>(List.of(t)));
        }
        return out;
    }

    /** Comeca o combate na rodada 1, no primeiro da ordem. */
    public static Optional<TokenModel> start(SceneModel scene) {
        List<TokenModel> order = order(scene);
        Encounter e = scene.getEncounter();
        e.setActive(!order.isEmpty());
        e.setRound(order.isEmpty() ? 0 : 1);
        e.setCurrentTokenId(order.isEmpty() ? null : order.get(0).getId());
        return order.isEmpty() ? Optional.empty() : Optional.of(order.get(0));
    }

    /**
     * Passa a vez. Ao voltar pro topo da ordem comeca uma rodada nova e todo
     * efeito com duracao perde 1 rodada; os que chegam a 0 voltam em
     * {@code expired} (e saem da lista). Se quem estava na vez sumiu da cena,
     * segue do topo.
     */
    public static TurnResult next(SceneModel scene) {
        Encounter e = scene.getEncounter();
        List<TokenModel> order = order(scene);
        if (order.isEmpty()) {
            e.setActive(false);
            e.setCurrentTokenId(null);
            return new TurnResult(null, e.getRound(), false, List.of());
        }
        if (!e.isActive()) {
            TokenModel first = start(scene).orElseThrow();
            return new TurnResult(first, 1, true, List.of());
        }
        int idx = indexOf(order, e.getCurrentTokenId());
        int nextIdx = idx + 1;
        if (e.isGroupMonsters() && idx >= 0) {
            // o grupo inteiro jogou: pula os outros do mesmo grupo
            while (nextIdx < order.size() && sameTurn(order.get(idx), order.get(nextIdx))) nextIdx++;
        }
        boolean newRound = idx < 0 ? false : nextIdx >= order.size();
        if (idx < 0) nextIdx = 0;
        if (nextIdx >= order.size()) nextIdx = 0;
        List<Encounter.TimedEffect> expired = new ArrayList<>();
        if (newRound) {
            e.setRound(Math.max(1, e.getRound()) + 1);
            for (Encounter.TimedEffect fx : new ArrayList<>(e.getTimedEffects())) {
                fx.setRemainingRounds(fx.getRemainingRounds() - 1);
                if (fx.getRemainingRounds() <= 0) {
                    expired.add(fx);
                    e.getTimedEffects().remove(fx);
                }
            }
        }
        TokenModel current = order.get(nextIdx);
        e.setCurrentTokenId(current.getId());
        return new TurnResult(current, e.getRound(), newRound, expired);
    }

    public static void end(SceneModel scene) {
        Encounter e = scene.getEncounter();
        e.setActive(false);
        e.setRound(0);
        e.setCurrentTokenId(null);
        e.getTimedEffects().clear();
    }

    public static Encounter.TimedEffect addTimedEffect(SceneModel scene, TokenModel token, String name, int rounds) {
        Encounter.TimedEffect fx = new Encounter.TimedEffect(token.getId(), name, Math.max(1, rounds));
        scene.getEncounter().getTimedEffects().add(fx);
        return fx;
    }

    /** Efeitos com duracao de um token (pra mostrar "Bencao (3 rd)"). */
    public static List<Encounter.TimedEffect> effectsOf(SceneModel scene, String tokenId) {
        List<Encounter.TimedEffect> result = new ArrayList<>();
        for (Encounter.TimedEffect fx : scene.getEncounter().getTimedEffects()) {
            if (fx.getTokenId() != null && fx.getTokenId().equals(tokenId)) result.add(fx);
        }
        return result;
    }

    private static int indexOf(List<TokenModel> order, String id) {
        if (id == null) return -1;
        for (int i = 0; i < order.size(); i++) {
            if (id.equals(order.get(i).getId())) return i;
        }
        return -1;
    }
}
