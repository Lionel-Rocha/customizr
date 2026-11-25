package com.vttcaseiro.service;

import com.vttcaseiro.model.Encounter;

import java.util.Locale;
import java.util.Objects;

/**
 * Relogio do turno, contado em cada maquina com o relogio dela: recomeca quando a vez muda (rodada, token, tempo
 * escolhido ou "reiniciar" do mestre). So avisa quando acaba; ninguem perde o turno sozinho.
 */
public final class TurnTimer {
    /** A partir de quantos segundos o relogio fica amarelo. */
    public static final int WARN_SECONDS = 10;

    private String key;
    private long totalMs;
    private long startedAt;
    /** Pausado: quanto faltava na hora da pausa (-1 = correndo). */
    private long pausedLeftMs = -1;
    private boolean expiredFired;

    /** Atualiza com o combate de agora (chamar sempre que o encontro mudar). */
    public void update(Encounter e, long now) {
        String k = e == null || !e.isActive() || e.getTurnSeconds() <= 0 || e.getCurrentTokenId() == null ? null
                : e.getRound() + "|" + e.getCurrentTokenId() + "|" + e.getTimerResets() + "|" + e.getTurnSeconds();
        if (!Objects.equals(k, key)) {
            key = k;
            startedAt = now;
            pausedLeftMs = -1;
            expiredFired = false;
        }
        if (k == null) return;
        totalMs = e.getTurnSeconds() * 1000L;
        if (e.isTimerPaused() && pausedLeftMs < 0) {
            pausedLeftMs = leftMs(now);
        } else if (!e.isTimerPaused() && pausedLeftMs >= 0) {
            startedAt = now - (totalMs - pausedLeftMs);
            pausedLeftMs = -1;
        }
    }

    public boolean running() {
        return key != null;
    }

    public boolean paused() {
        return key != null && pausedLeftMs >= 0;
    }

    public long leftMs(long now) {
        if (key == null) return 0;
        if (pausedLeftMs >= 0) return pausedLeftMs;
        return Math.max(0, totalMs - (now - startedAt));
    }

    /** true uma vez so, no primeiro tique depois que o tempo acabou. */
    public boolean checkExpired(long now) {
        if (key == null || expiredFired || paused() || leftMs(now) > 0) return false;
        expiredFired = true;
        return true;
    }

    /** "ok", "warn" (nos ultimos segundos) ou "over" (acabou). */
    public String state(long now) {
        long left = leftMs(now);
        if (left <= 0) return "over";
        return left <= WARN_SECONDS * 1000L ? "warn" : "ok";
    }

    /** 0:42 (arredonda pra cima: so mostra 0:00 quando acabou mesmo). */
    public static String format(long ms) {
        long s = (Math.max(0, ms) + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "Desligado", "30 s", "1 min", "1 min 30 s". */
    public static String label(int seconds) {
        if (seconds <= 0) return "Desligado";
        if (seconds < 60) return seconds + " s";
        int m = seconds / 60;
        int s = seconds % 60;
        return m + " min" + (s == 0 ? "" : " " + s + " s");
    }
}
