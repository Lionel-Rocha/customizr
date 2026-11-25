package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;

import java.util.ArrayList;
import java.util.List;

/** XP e nivel (tabela de XP do D&D 5e, igual em 2014 e 2024) e dar XP pro grupo de uma vez. */
public final class XpService {
    private XpService() {
    }

    /** XP pra chegar a cada nivel (indice 0 = nivel 1). */
    public static final int[] THRESHOLDS = {0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000, 85000, 100000,
            120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000};

    /** Nivel que essa XP permite (1 a 20). */
    public static int levelForXp(long xp) {
        int level = 1;
        for (int i = 0; i < THRESHOLDS.length; i++) if (xp >= THRESHOLDS[i]) level = i + 1;
        return level;
    }

    /** Quanto falta pro proximo nivel (0 no 20). */
    public static long toNextLevel(long xp) {
        int lvl = levelForXp(xp);
        return lvl >= 20 ? 0 : THRESHOLDS[lvl] - xp;
    }

    /** O que cada ficha ganhou e se ja pode subir de nivel. */
    public record Award(String name, int gained, int totalXp, int level, int canReach) {
        public boolean canLevelUp() {
            return canReach > level;
        }
    }

    /**
     * Da XP pras fichas: {@code amount} pra cada uma, ou dividido entre elas ({@code split}; a sobra se perde, como
     * na mesa). Devolve o que cada uma ganhou.
     */
    public static List<Award> award(List<CharacterSheet> sheets, int amount, boolean split) {
        List<Award> out = new ArrayList<>();
        if (sheets == null || sheets.isEmpty() || amount <= 0) return out;
        int each = split ? amount / sheets.size() : amount;
        for (CharacterSheet s : sheets) {
            int total = (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, s.getExperiencePoints()) + each);
            s.setExperiencePoints(total);
            out.add(new Award(s.getName(), each, total, Math.max(1, s.getLevel()), levelForXp(total)));
        }
        return out;
    }

    /** Texto pro chat: "Lyra +250 XP (2950, pode subir pro nivel 4!)". */
    public static String summary(List<Award> awards, String reason) {
        if (awards.isEmpty()) return "Ninguém recebeu XP.";
        StringBuilder b = new StringBuilder("XP");
        if (reason != null && !reason.isBlank()) b.append(" (").append(reason.strip()).append(')');
        b.append(':');
        for (Award a : awards) {
            b.append("\n  ").append(a.name()).append(" +").append(a.gained()).append(" XP (total ").append(a.totalXp());
            if (a.canLevelUp()) b.append(", pode subir pro nível ").append(a.canReach()).append('!');
            b.append(')');
        }
        return b.toString();
    }
}
