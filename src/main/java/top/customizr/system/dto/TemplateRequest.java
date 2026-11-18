package top.customizr.system.dto;

import top.customizr.system.model.AreaTemplate;

/**
 * Area de efeito: shape CIRCLE (raio), CONE / LINE (comprimento, na direcao angle em graus) ou SQUARE (lado,
 * centrado no ponto). x, y em pixels do mapa; tamanhos em pes; widthFt so na linha (padrao 5).
 * darkness: escuridao magica (dentro dela nada e visto).
 */
public record TemplateRequest(AreaTemplate.Shape shape, Double x, Double y, Double angle, Integer sizeFt, Integer widthFt,
                              String label, String color, Boolean darkness) {

    public TemplateRequest(AreaTemplate.Shape shape, Double x, Double y, Double angle, Integer sizeFt, Integer widthFt,
                           String label, String color) {
        this(shape, x, y, angle, sizeFt, widthFt, label, color, null);
    }
}
