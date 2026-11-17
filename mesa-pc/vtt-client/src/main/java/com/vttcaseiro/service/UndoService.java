package com.vttcaseiro.service;

import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.NetCodec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Pilha de "desfazer" das acoes no mapa feitas NESTA maquina. Cada entrada
 * guarda uma copia do token antes e depois da acao:
 * <ul>
 *   <li>{@code before == null}: o token foi ADICIONADO (desfazer = remover);</li>
 *   <li>{@code after == null}: o token foi REMOVIDO (desfazer = recolocar);</li>
 *   <li>os dois: o token mudou (posicao/PV/condicoes...; desfazer = voltar
 *       ao {@code before}).</li>
 * </ul>
 * Desfazer mexe so no token daquela entrada -- nao volta a cena inteira
 * pra um "retrato" antigo, entao nao apaga o que os outros jogadores
 * fizeram nesse meio tempo.
 */
public class UndoService {
    public static final int MAX = 50;

    public record Entry(String label, TokenModel before, TokenModel after) {
        public String tokenId() {
            return before != null ? before.getId() : after.getId();
        }
    }

    private final Deque<Entry> stack = new ArrayDeque<>();

    public void record(String label, TokenModel before, TokenModel after) {
        if (before == null && after == null) return;
        stack.push(new Entry(label, copy(before), copy(after)));
        while (stack.size() > MAX) stack.removeLast();
    }

    public Optional<Entry> pop() {
        return Optional.ofNullable(stack.poll());
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int size() {
        return stack.size();
    }

    public void clear() {
        stack.clear();
    }

    public static TokenModel copy(TokenModel token) {
        return token == null ? null : NetCodec.deepCopy(token, TokenModel.class);
    }
}
