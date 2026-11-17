package top.customizr.system.dto;

/** Porta: open abre/fecha (jogador tambem, se nao estiver trancada); locked so o mestre. Campo null fica como esta. */
public record DoorRequest(Boolean open, Boolean locked) {}
