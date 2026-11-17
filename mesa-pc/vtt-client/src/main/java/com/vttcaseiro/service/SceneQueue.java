package com.vttcaseiro.service;

import java.util.List;
import java.util.Optional;

/** Fila de cenas da sessao: qual e a proxima depois da atual. */
public final class SceneQueue {

    private SceneQueue() {
    }

    /** A seguinte a {@code current} na fila; cena fora da fila (ou nenhuma) = a primeira. Vazio no fim. */
    public static Optional<String> next(List<String> queue, String current) {
        if (queue == null || queue.isEmpty()) return Optional.empty();
        int i = current == null ? -1 : queue.indexOf(current);
        return i + 1 < queue.size() ? Optional.of(queue.get(i + 1)) : Optional.empty();
    }
}
