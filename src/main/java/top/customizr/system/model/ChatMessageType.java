package top.customizr.system.model;

public enum ChatMessageType {
    /** Texto normal (pode ter rolagens no meio: [[1d20+5]]). */
    MESSAGE,
    /** "/me saca a espada" -> "Aria saca a espada". */
    EMOTE,
    /** "/r 1d20+5 Ataque" -> cartao de rolagem. */
    ROLL,
    /** "/w Nome mensagem" -> so autor, destinatario e mestre veem. */
    WHISPER,
    /** Cartao com uma feature, magia, item ou monstro mandado da ficha/catalogo (titulo + texto). */
    CARD
}
