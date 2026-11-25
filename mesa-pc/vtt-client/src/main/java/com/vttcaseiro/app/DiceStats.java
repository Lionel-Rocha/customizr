package com.vttcaseiro.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estatisticas do d20 por pessoa: quantas rolagens, 20 naturais, 1 naturais e a media do dado. */
public final class DiceStats {
    private DiceStats() {
    }

    public record Line(String who, int rolls, int crits, int fumbles, double average) {
    }

    /** Uma linha por pessoa (rolagem escondida fica de fora), quem mais rolou primeiro. */
    public static List<Line> of(List<LogEntry> entries) {
        Map<String, int[]> acc = new LinkedHashMap<>(); // rolagens, 20s, 1s, soma
        for (LogEntry e : entries) {
            if (e == null || e.type() != LogEntry.Type.ROLL || e.naturalD20() == null || e.privacy() != null) continue;
            String who = e.author() == null || e.author().isBlank() ? "?" : e.author();
            int[] a = acc.computeIfAbsent(who, k -> new int[4]);
            a[0]++;
            if (e.naturalD20() == 20) a[1]++;
            if (e.naturalD20() == 1) a[2]++;
            a[3] += e.naturalD20();
        }
        List<Line> out = new ArrayList<>();
        acc.forEach((who, a) -> out.add(new Line(who, a[0], a[1], a[2], a[0] == 0 ? 0 : (double) a[3] / a[0])));
        out.sort((x, y) -> y.rolls() - x.rolls());
        return out;
    }

    /** Texto pro chat: "Mais sortudo: Ana (3 vezes 20)..." */
    public static String summary(List<Line> lines, String title) {
        if (lines.isEmpty()) return title + ": nenhum d20 rolado ainda.";
        StringBuilder b = new StringBuilder(title).append(":");
        for (Line l : lines) {
            b.append("\n  ").append(l.who()).append(": ").append(l.rolls()).append(l.rolls() == 1 ? " rolagem" : " rolagens")
                    .append(", média ").append(String.format(java.util.Locale.ROOT, "%.1f", l.average()).replace('.', ','))
                    .append(", ").append(l.crits()).append("x 20, ").append(l.fumbles()).append("x 1");
        }
        Line lucky = lines.stream().max((x, y) -> Double.compare(x.average(), y.average())).orElse(null);
        Line crits = lines.stream().max((x, y) -> x.crits() - y.crits()).orElse(null);
        if (lucky != null && lines.size() > 1) b.append("\nMais sortudo (média): ").append(lucky.who());
        if (crits != null && crits.crits() > 0) b.append("\nMais 20 naturais: ").append(crits.who()).append(" (").append(crits.crits()).append(")");
        return b.toString();
    }
}
