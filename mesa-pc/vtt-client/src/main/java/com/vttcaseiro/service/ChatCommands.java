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

    public enum Type { TEXT, ROLL, HIDDEN_ROLL, WHISPER, HELP, ERROR, EMPTY, TABLE, EMOTE }

    /** {@code target} so em WHISPER; {@code text} = mensagem, expressao ou erro. */
    public record Command(Type type, String target, String text) {
    }

    public static final String HELP = "Comandos: /r 2d6+3 (rola pra todos) | /gr 1d20+5 (rola escondido: só você "
            + "e o mestre) | /w Nome mensagem (sussurro) | /w mestre mensagem (sussurro pros mestres) | "
            + "/tabela Nome (rola uma tabela aleatoria; /tabela sozinho lista) | /me acao (ex.: /me saca a espada) | "
            + "[[1d20+5]] no meio do texto rola ali mesmo.";

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
        if (lower.equals("/tabela") || lower.equals("/t")) return new Command(Type.TABLE, null, "");
        if (lower.startsWith("/tabela ")) return new Command(Type.TABLE, null, text.substring(8).trim());
        if (lower.startsWith("/t ")) return new Command(Type.TABLE, null, text.substring(3).trim());
        if (text.startsWith("/")) return new Command(Type.ERROR, null, "Comando desconhecido. Digite /ajuda pra ver os comandos.");
        return new Command(Type.TEXT, null, text);
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
