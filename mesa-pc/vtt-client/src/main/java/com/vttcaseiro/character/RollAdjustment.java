package com.vttcaseiro.character;

import java.util.List;

/**
 * O que as condicoes/efeitos da ficha fazem com um teste de d20: o modo
 * final (vantagem/desvantagem ja combinadas com a escolha do jogador), o
 * ajuste no modificador (exaustao) e notas pra mostrar no chat ("Envenenado:
 * desvantagem"). {@code autoFail} = falha automatica (ex.: salvaguarda de
 * DES paralisado) -- quem rola ainda mostra o dado, mas avisa.
 */
public record RollAdjustment(RollMode mode, int modifierDelta, List<String> notes, boolean autoFail) {

    public String notesText() {
        return notes.isEmpty() ? "" : " [" + String.join("; ", notes) + "]";
    }
}
