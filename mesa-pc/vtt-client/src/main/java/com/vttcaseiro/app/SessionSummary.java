package com.vttcaseiro.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resumo da sessao a partir do chat: cenas, combates (e rodadas), criticos e falhas criticas, dano aplicado, quem
 * caiu, tesouro/XP, cartas e tabelas. Rolagem escondida e sussurro ficam de fora (o resumo pode ir pra Campanha, que
 * os jogadores leem).
 */
public final class SessionSummary {
    private SessionSummary() {
    }

    static final int MAX_LINES = 12;

    public static String build(List<LogEntry> entries, LocalDate date, boolean gm) {
        Set<String> scenes = new LinkedHashSet<>();
        int combats = 0;
        int rounds = 0;
        List<String> crits = new ArrayList<>();
        List<String> fumbles = new ArrayList<>();
        List<String> damage = new ArrayList<>();
        List<String> falls = new ArrayList<>();
        List<String> loot = new ArrayList<>();
        List<String> cards = new ArrayList<>();
        Map<String, Integer> talk = new LinkedHashMap<>();
        for (LogEntry e : entries) {
            if (e == null || e.text() == null || e.privacy() != null) continue; // escondido e sussurro: fora
            String t = e.text().strip();
            String low = t.toLowerCase(Locale.ROOT);
            if (e.type() == LogEntry.Type.SYSTEM || e.type() == LogEntry.Type.DAMAGE || e.type() == LogEntry.Type.HEAL) {
                if (t.startsWith("Cena aberta: ")) scenes.add(t.substring("Cena aberta: ".length()).strip());
                else if (t.startsWith("Combate! Rodada 1")) {
                    combats++;
                    rounds++;
                } else if (t.startsWith("--- Rodada ")) rounds++;
                else if (t.startsWith("Dano aplicado: ") || t.startsWith("Cura aplicado: ")) damage.add(t);
                else if (low.contains(" caiu") || low.contains("inconsciente") || low.contains("morreu")
                        || low.contains("estabiliz") || low.contains("salvaguarda contra a morte")) falls.add(t);
                else if (low.contains("tesouro") || low.contains("entreg") || low.contains(" xp")
                        || low.contains("subiu de nível") || low.contains("subiu de nivel")) loot.add(t);
                else if (t.startsWith("Carta de ") || t.startsWith("Tabela ")) cards.add(t);
            } else if (e.type() == LogEntry.Type.ROLL && e.naturalD20() != null) {
                String who = e.author() == null ? "" : e.author() + ": ";
                String what = t.contains(":") ? t.substring(0, t.indexOf(':')).strip() : t;
                if (e.naturalD20() == 20) crits.add(who + what);
                else if (e.naturalD20() == 1) fumbles.add(who + what);
            } else if (e.type() == LogEntry.Type.CHAT && e.author() != null && !"servidor".equals(e.author())) {
                talk.merge(e.author(), 1, Integer::sum);
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("Sessão de ").append(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append('\n');
        if (!scenes.isEmpty()) b.append("\nCenas: ").append(String.join(", ", scenes)).append('\n');
        if (combats > 0) b.append("Combates: ").append(combats).append(" (").append(rounds)
                .append(rounds == 1 ? " rodada" : " rodadas").append(" no total)\n");
        section(b, "Críticos", crits);
        section(b, "Falhas críticas", fumbles);
        section(b, "Quem caiu / quase morreu", falls);
        section(b, "Dano e cura", damage);
        section(b, "Tesouro, XP e níveis", loot);
        section(b, "Cartas e tabelas", cards);
        if (!talk.isEmpty()) {
            List<Map.Entry<String, Integer>> top = new ArrayList<>(talk.entrySet());
            top.sort((x, y) -> y.getValue() - x.getValue());
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Integer> en : top.subList(0, Math.min(5, top.size()))) {
                names.add(en.getKey() + " (" + en.getValue() + ")");
            }
            b.append("\nMensagens no chat: ").append(String.join(", ", names)).append('\n');
        }
        if (b.indexOf("\n", b.indexOf("\n") + 1) < 0) b.append("\n(Nada marcante no chat ainda: role uns dados!)\n");
        b.append("\nAnotações do mestre:\n- ");
        return b.toString();
    }

    private static void section(StringBuilder b, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        b.append('\n').append(title).append(":\n");
        int n = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < n; i++) b.append("- ").append(lines.get(i)).append('\n');
        if (lines.size() > n) b.append("- ... e mais ").append(lines.size() - n).append('\n');
    }
}
