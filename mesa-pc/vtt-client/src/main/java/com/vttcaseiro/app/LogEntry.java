package com.vttcaseiro.app;

/**
 * Uma linha do chat/log. {@code total} e {@code naturalD20} so existem em
 * rolagens: o total permite "aplicar este dano no token selecionado" direto
 * do chat, e o d20 natural marca critico (20) / falha critica (1).
 * {@code privacy} != null = mensagem privada ("sussurro para Ana",
 * "escondida -- so mestre"): o chat mostra destacada, pra ninguem achar
 * que a mesa toda viu.
 */
public record LogEntry(Type type, String author, String text, Integer total, Integer naturalD20, String privacy,
                       String imageAssetId) {

    public enum Type { SYSTEM, CHAT, ROLL, DAMAGE, HEAL, ERROR }

    public LogEntry(Type type, String author, String text, Integer total, Integer naturalD20, String privacy) {
        this(type, author, text, total, naturalD20, privacy, null);
    }

    public LogEntry(Type type, String author, String text) {
        this(type, author, text, null, null, null);
    }

    public LogEntry(Type type, String author, String text, Integer total, Integer naturalD20) {
        this(type, author, text, total, naturalD20, null);
    }

    public static LogEntry system(String text) {
        return new LogEntry(Type.SYSTEM, null, text);
    }

    public static LogEntry chat(String author, String text) {
        return new LogEntry(Type.CHAT, author, text);
    }

    public static LogEntry whisper(String author, String to, String text) {
        return new LogEntry(Type.CHAT, author, text, null, null, "sussurro para " + to);
    }

    /** Imagem mandada no chat (legenda opcional; whisperTo != null = so pra essa pessoa). */
    public static LogEntry image(String author, String caption, String assetId, String whisperTo) {
        return new LogEntry(Type.CHAT, author, caption == null ? "" : caption, null, null,
                whisperTo == null || whisperTo.isBlank() ? null : "sussurro para " + whisperTo, assetId);
    }

    public boolean hasImage() { return imageAssetId != null; }

    public static LogEntry roll(String author, String text) {
        return new LogEntry(Type.ROLL, author, text);
    }

    public static LogEntry roll(String author, String text, int total, Integer naturalD20) {
        return new LogEntry(Type.ROLL, author, text, total, naturalD20, null);
    }

    public static LogEntry hiddenRoll(String author, String text, int total, Integer naturalD20) {
        return new LogEntry(Type.ROLL, author, text, total, naturalD20, "escondida -- só você e o mestre");
    }

    public static LogEntry damage(String text) {
        return new LogEntry(Type.DAMAGE, null, text);
    }

    public static LogEntry heal(String text) {
        return new LogEntry(Type.HEAL, null, text);
    }

    public static LogEntry error(String text) {
        return new LogEntry(Type.ERROR, null, text);
    }

    public boolean isCriticalHit() { return naturalD20 != null && naturalD20 == 20; }

    public boolean isCriticalFail() { return naturalD20 != null && naturalD20 == 1; }

    public boolean isPrivate() { return privacy != null; }
}
