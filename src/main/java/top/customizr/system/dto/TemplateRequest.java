package top.customizr.system.dto;

import top.customizr.system.model.AreaTemplate;

/**
 * Area de efeito: shape CIRCLE (raio), CONE / LINE (comprimento, na direcao angle em graus) ou SQUARE (lado,
 * centrado no ponto). x, y em pixels do mapa; tamanhos em pes; widthFt so na linha (padrao 5).
 */
public record TemplateRequest(AreaTemplate.Shape shape, Double x, Double y, Double angle, Integer sizeFt, Integer widthFt,
                              String label, String color) {}
