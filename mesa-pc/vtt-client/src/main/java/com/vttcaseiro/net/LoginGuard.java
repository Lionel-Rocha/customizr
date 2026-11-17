package com.vttcaseiro.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limite de tentativas de senha por IP: {@link #MAX_FAILURES} erros dentro
 * de {@link #WINDOW_MS} bloqueiam aquele IP por {@link #BLOCK_MS}. Sem isso,
 * alguem podia testar senhas da mesa sem parar.
 */
final class LoginGuard {
    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 10 * 60_000L;
    static final long BLOCK_MS = 5 * 60_000L;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    LoginGuard() {
        this(System::currentTimeMillis);
    }

    LoginGuard(LongSupplier clock) {
        this.clock = clock;
    }

    boolean isBlocked(String ip) {
        Long until = blockedUntil.get(ip);
        if (until == null) return false;
        if (clock.getAsLong() >= until) {
            blockedUntil.remove(ip);
            return false;
        }
        return true;
    }

    synchronized void failed(String ip) {
        long now = clock.getAsLong();
        Deque<Long> list = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        list.addLast(now);
        while (!list.isEmpty() && now - list.peekFirst() > WINDOW_MS) list.removeFirst();
        if (list.size() >= MAX_FAILURES) {
            blockedUntil.put(ip, now + BLOCK_MS);
            list.clear();
        }
    }

    void succeeded(String ip) {
        failures.remove(ip);
    }
}
