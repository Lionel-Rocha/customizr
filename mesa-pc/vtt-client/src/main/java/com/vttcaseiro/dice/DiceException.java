package com.vttcaseiro.dice;

/** Expressao de dados invalida ou fora dos limites. A mensagem ja vem pronta
 * para mostrar ao jogador (em portugues, igual ao resto do programa). */
public class DiceException extends Exception {
    public DiceException(String message) {
        super(message);
    }
}
