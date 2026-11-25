package com.vttcaseiro.service;

import java.util.Locale;

/**
 * Interpreta o que foi digitado no chat (sem executar nada -- quem executa
 * e o Main). Separado pra ser testavel e pra a lista de comandos ficar num
 * lugar so.
 */
public final class ChatCommands {

    private ChatCommands() {
    }

    public enum Type { TEXT, ROLL, HIDDEN_ROLL, WHISPER, HELP, ERROR, EMPTY, TABLE, EMOTE, LANGUAGE, POLL }

    /** {@code target} so em WHISPER; {@code text} = mensagem, expressao ou erro. */
    public record Command(Type type, String target, String text) {
    }

    public static final String HELP = "Comandos: /r 2d6+3 (rola pra todos) | /gr 1d20+5 (rola escondido: só você "
            + "e o mestre) | /w Nome mensagem (sussurro) | /w mestre mensagem (sussurro pros mestres) | "
            + "/tabela Nome (rola uma tabela aleatoria; /tabela sozinho lista) | /me acao (ex.: /me saca a espada) | "
            + "[[1d20+5]] no meio do texto rola ali mesmo. | /em Elvish texto (fala num idioma: quem nao conhece ve embaralhado) | "
            + "/votar Pergunta? | Opcao 1 | Opcao 2 (enquete).";

    public static Command parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return new Command(Type.EMPTY, null, "");
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("/ajuda") || lower.equals("/help") || lower.equals("/?")) {
            return new Command(Type.HELP, null, HELP);
        }
        if (lower.startsWith("/gr ")) return new Command(Type.HIDDEN_ROLL, null, text.substring(4).trim());
        if (lower.startsWith("/r ")) return new Command(Type.ROLL, null, text.substring(3).trim());
        if (lower.startsWith("/roll ")) return new Command(Type.ROLL, null, text.substring(6).trim());
        if (lower.startsWith("/w ")) return parseWhisper(text.substring(3).trim());
        if (lower.startsWith("/me ") && text.length() > 4) return new Command(Type.EMOTE, null, text);
        if (lower.startsWith("/em ") || lower.startsWith("/idioma ")) {
            String rest = text.substring(text.indexOf(' ') + 1).trim();
            String lang;
            String said;
            if (rest.startsWith("\"")) {
                int end = rest.indexOf('"', 1);
                if (end < 0) return new Command(Type.ERROR, null, "Feche as aspas do idioma: /em \"Deep Speech\" texto");
                lang = rest.substring(1, end).trim();
                said = rest.substring(end + 1).trim();
            } else {
                int sp = rest.indexOf(' ');
                lang = sp < 0 ? rest : rest.substring(0, sp);
                said = sp < 0 ? "" : rest.substring(sp + 1).trim();
            }
            if (lang.isEmpty() || said.isEmpty()) return new Command(Type.ERROR, null, "Use: /em Elvish texto");
            return new Command(Type.LANGUAGE, lang, said);
        }
        if (lower.startsWith("/votar ") || lower.startsWith("/enquete ")) {
            return new Command(Type.POLL, null, text.substring(text.indexOf(' ') + 1).trim());
        }
        if (lower.equals("/tabela") || lower.equals("/t")) return new Command(Type.TABLE, null, "");
        if (lower.startsWith("/tabela ")) return new Command(Type.TABLE, null, text.substring(8).trim());
        if (lower.startsWith("/t ")) return new Command(Type.TABLE, null, text.substring(3).trim());
        if (text.startsWith("/")) return new Command(Type.ERROR, null, "Comando desconhecido. Digite /ajuda pra ver os comandos.");
        return new Command(Type.TEXT, null, text);
    }

    /**
     * "/votar Pergunta? | A | B" ou "/votar \"A\" \"B\"": pergunta (se tiver ?) e de 2 a 10 opcoes; null se nao der.
     */
    public static java.util.List<String> pollParts(String rest) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (rest == null) return null;
        if (rest.contains("|")) {
            for (String p : rest.split("\\|")) if (!p.isBlank()) parts.add(p.strip());
        } else {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"|(\\S+)").matcher(rest);
            while (m.find()) parts.add(m.group(1) != null ? m.group(1).strip() : m.group(2));
        }
        String question = !parts.isEmpty() && parts.get(0).endsWith("?") ? parts.remove(0) : "";
        if (parts.size() < 2) return null;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(question);
        out.addAll(parts.subList(0, Math.min(10, parts.size())));
        return out;
    }

    /** "/w Nome texto". Nome com espaco: aspas ("/w \"Ana Clara\" oi"). */
    private static Command parseWhisper(String rest) {
        String to;
        String message;
        if (rest.startsWith("\"")) {
            int end = rest.indexOf('"', 1);
            if (end < 0) return new Command(Type.ERROR, null, "Feche as aspas do nome: /w \"Ana Clara\" mensagem");
            to = rest.substring(1, end).trim();
            message = rest.substring(end + 1).trim();
        } else {
            int space = rest.indexOf(' ');
            to = space < 0 ? rest : rest.substring(0, space);
            message = space < 0 ? "" : rest.substring(space + 1).trim();
        }
        if (to.isEmpty() || message.isEmpty()) {
            return new Command(Type.ERROR, null, "Use: /w Nome mensagem  (ou /w mestre mensagem)");
        }
        return new Command(Type.WHISPER, to, message);
    }
}
