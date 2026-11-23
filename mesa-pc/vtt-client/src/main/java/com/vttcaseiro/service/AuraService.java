package com.vttcaseiro.service;

import com.vttcaseiro.model.Aura;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Calcula quem esta dentro das auras (sem tela; o TableService aplica). O efeito que a aura da entra na lista de
 * efeitos do token com o sufixo "(aura de Fulano)": o calculo das rolagens ja entende (os colchetes, ou o nome do
 * catalogo sem o parenteses), e da pra saber qual efeito veio de aura pra tirar quando o token sai.
 */
public final class AuraService {
    private static final Pattern FROM_AURA = Pattern.compile(".*\\(aura de .*\\)\\s*$");

    private AuraService() {
    }

    /** Efeito que veio de uma aura (sai sozinho)? */
    public static boolean isAuraEffect(String effect) {
        return effect != null && FROM_AURA.matcher(effect).matches();
    }

    public static String effectFrom(TokenModel source, Aura aura) {
        return aura.getEffect() + " (aura de " + (source.getName() == null ? "?" : source.getName()) + ")";
    }

    /** Token de jogador (tem dono ou ficha) ou do mestre (monstro/PdM)? Aliado = mesmo lado. */
    static boolean playerSide(TokenModel t) {
        return t.getOwnerId() != null || t.getCharacterSheetId() != null;
    }

    /**
     * Pra cada token, os efeitos de aura que ele deveria ter agora (lista vazia = nenhum). So entram os tokens da
     * cena; distancia de centro a centro, em pes pela grade.
     */
    public static Map<String, List<String>> desired(SceneModel scene) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<TokenModel> tokens = scene.getTokens();
        double cell = Math.max(1, scene.getGrid().getCellSize());
        double feetPerCell = scene.getGrid().getFeetPerCell() > 0 ? scene.getGrid().getFeetPerCell() : 5;
        for (TokenModel t : tokens) out.put(t.getId(), new ArrayList<>());
        for (TokenModel src : tokens) {
            if (src.getAuras().isEmpty()) continue;
            double sx = src.getX() + cell * src.getWidthCells() / 2.0;
            double sy = src.getY() + cell * src.getHeightCells() / 2.0;
            for (Aura a : src.getAuras()) {
                if (a.getEffect().isBlank()) continue;
                // raio a partir da borda do token (criatura grande cobre mais), em pixels
                double radiusPx = a.getRadiusFeet() / feetPerCell * cell + cell * (Math.max(src.getWidthCells(), src.getHeightCells()) - 1) / 2.0;
                for (TokenModel t : tokens) {
                    boolean self = t.getId().equals(src.getId());
                    if (self && !a.isIncludeSelf()) continue;
                    boolean sameSide = playerSide(t) == playerSide(src);
                    if (!self) {
                        if (Aura.ALLIES.equals(a.getTarget()) && !sameSide) continue;
                        if (Aura.ENEMIES.equals(a.getTarget()) && sameSide) continue;
                    }
                    double tx = t.getX() + cell * t.getWidthCells() / 2.0;
                    double ty = t.getY() + cell * t.getHeightCells() / 2.0;
                    double reach = radiusPx + cell * (Math.max(t.getWidthCells(), t.getHeightCells()) - 1) / 2.0;
                    if (self || Math.hypot(tx - sx, ty - sy) <= reach + 0.5) {
                        String e = effectFrom(src, a);
                        List<String> list = out.get(t.getId());
                        if (!list.contains(e)) list.add(e);
                    }
                }
            }
        }
        return out;
    }

    /** Os efeitos atuais com os de aura trocados pelos de agora (os outros ficam como estao, na ordem). */
    public static List<String> merge(List<String> current, List<String> auraEffects) {
        List<String> out = new ArrayList<>();
        for (String e : current) if (!isAuraEffect(e)) out.add(e);
        out.addAll(auraEffects);
        return out;
    }
}
