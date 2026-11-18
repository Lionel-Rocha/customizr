package top.customizr.system.dto;

import java.util.List;

/** Regua compartilhada: os pontos medidos (x, y em pixels do mapa). Lista vazia = apaga a regua. */
public record RulerRequest(List<double[]> points) {}
