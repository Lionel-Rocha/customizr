package top.customizr.system.model;

/**
 * Camada do token no mapa, de baixo pra cima: SCENERY (objetos do cenario, debaixo de todos),
 * BATTLE (personagens e criaturas, a padrao), OVERHEAD (telhado, copa de arvore: por cima dos tokens)
 * e GM (so o mestre ve: armadilhas, anotacoes, inimigos guardados).
 */
public enum TokenLayer {
    SCENERY,
    BATTLE,
    OVERHEAD,
    GM
}
