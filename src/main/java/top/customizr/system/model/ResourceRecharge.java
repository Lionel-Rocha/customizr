package top.customizr.system.model;

/**
 * Quando um recurso da ficha volta.
 * <ul>
 *   <li>{@code SHORT_REST}: tudo volta no descanso curto (e no longo);</li>
 *   <li>{@code SHORT_REST_ONE}: um uso volta no descanso curto, tudo no longo (regra de 2024 da Furia,
 *       Canalizar Divindade, Forma Selvagem, Retomar o Folego);</li>
 *   <li>{@code LONG_REST}: tudo volta so no descanso longo;</li>
 *   <li>{@code MANUAL}: nao volta sozinho (cargas de item, usos que o mestre devolve).</li>
 * </ul>
 */
public enum ResourceRecharge {
    SHORT_REST,
    SHORT_REST_ONE,
    LONG_REST,
    MANUAL
}
