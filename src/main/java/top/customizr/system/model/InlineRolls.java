package top.customizr.system.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolagem no meio do texto: "ataco com [[1d20+5]] e causo [[1d8+3]]" vira
 * "ataco com ⟦17 ← 1d20[12] + 5 = 17⟧ e causo ⟦9 ← 1d8[6] + 3 = 9⟧". Quem rola e o servidor.
 * {@link #split} devolve os pedacos pro front mostrar o total em destaque e a conta ao passar o mouse.
 */
public final class InlineRolls {

    public static final char OPEN = '⟦';
    public static final char CLOSE = '⟧';
    public static final String ARROW = " ← ";
    public static final int MAX_PER_MESSAGE = 10;

    private static final Pattern REQUEST = Pattern.compile("\\[\\[([^\\[\\]]{1,60})\\]\\]");
    private static final Pattern RESULT = Pattern.compile("⟦(-?\\d+) ← ([^⟦⟧]*)⟧");

    private InlineRolls() {}

    public static String expand(String text) {
        return expand(text, DiceRoller::roll);
    }

    /** Rola cada [[expressao]] valida (no maximo 10). Expressao invalida fica como esta. */
    public static String expand(String text, Function<String, RollResult> roller) {
        if (text == null) {
            return null;
        }
        String clean = text.replace(OPEN, '[').replace(CLOSE, ']');
        Matcher m = REQUEST.matcher(clean);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String replacement = m.group();
            if (count < MAX_PER_MESSAGE) {
                try {
                    RollResult r = roller.apply(m.group(1).trim());
                    replacement = OPEN + String.valueOf(r.total()) + ARROW + r.detail() + CLOSE;
                    count++;
                } catch (IllegalArgumentException ignored) {
                    // texto qualquer entre [[ ]]: fica
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Remove os marcadores que o usuario digitou (so o servidor pode criar um). */
    public static String sanitize(String text) {
        return text == null ? null : text.replace(OPEN, '[').replace(CLOSE, ']');
    }

    public record Part(String text, Integer total, String detail) {}

    public static List<Part> split(String text) {
        List<Part> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
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
}
