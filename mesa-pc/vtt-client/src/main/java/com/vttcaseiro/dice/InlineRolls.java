package com.vttcaseiro.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolagem no meio do texto do chat: "ataco com [[1d20+5]] e causo [[1d8+3]]" vira
 * "ataco com ⟦17 ← 1d20[12] + 5⟧ e causo ⟦9 ← 1d8[6] + 3⟧". Quem rola e o SERVIDOR
 * (ou a propria maquina offline); os marcadores ⟦ ⟧ que alguem digitar sao trocados
 * por [ ] antes, entao ninguem forja um resultado. A tela mostra o total em destaque
 * e a conta ao passar o mouse.
 */
public final class InlineRolls {
    public static final char OPEN = '⟦';
    public static final char CLOSE = '⟧';
    public static final String ARROW = " ← ";
    public static final int MAX_PER_MESSAGE = 10;

    private static final Pattern REQUEST = Pattern.compile("\\[\\[([^\\[\\]]{1,60})\\]\\]");
    /** Um resultado ja rolado: grupo 1 = total, grupo 2 = conta. */
    public static final Pattern RESULT = Pattern.compile("⟦(-?\\d+) ← ([^⟦⟧]*)⟧");

    private InlineRolls() {
    }

    /** Rola cada [[expressao]] valida do texto (no maximo 10). Expressao invalida fica como esta. */
    public static String expand(String text) {
        if (text == null) return null;
        String clean = text.replace(OPEN, '[').replace(CLOSE, ']');
        Matcher m = REQUEST.matcher(clean);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String replacement = m.group();
            if (count < MAX_PER_MESSAGE) {
                try {
                    RollResult r = DiceRoller.roll(m.group(1).trim());
                    replacement = OPEN + String.valueOf(r.total()) + ARROW + r.detail().replace(String.valueOf(CLOSE), "") + CLOSE;
                    count++;
                } catch (DiceException | RuntimeException ignored) {
                    // texto qualquer entre [[ ]]: deixa
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Pedaco de texto pra tela: ou texto comum, ou uma rolagem (total + conta). */
    public record Part(String text, Integer total, String detail) {
        public boolean isRoll() { return total != null; }
    }

    public static List<Part> split(String text) {
        List<Part> parts = new ArrayList<>();
        if (text == null) return parts;
        Matcher m = RESULT.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) parts.add(new Part(text.substring(last, m.start()), null, null));
            parts.add(new Part(m.group(1), Integer.parseInt(m.group(1)), m.group(2)));
            last = m.end();
        }
        if (last < text.length()) parts.add(new Part(text.substring(last), null, null));
        return parts;
    }

    /** "/me saca a espada" -> "saca a espada" (ou null se nao for uma acao). */
    public static String emote(String text) {
        if (text == null) return null;
        String t = text.strip();
        return t.regionMatches(true, 0, "/me ", 0, 4) && t.length() > 4 ? t.substring(4).strip() : null;
    }
}
