package com.vttcaseiro.compendium;

/**
 * Toda categoria de conteudo que pode virar uma entrada de compendio e
 * ser anexada numa ficha. De proposito e uma lista fechada (enum, nao
 * String livre) -- a tela vai precisar saber desenhar cada tipo de um
 * jeito diferente (classe tem nivel, item tem peso/valor, magia tem
 * nivel/escola), e um enum fechado deixa o compilador avisar se um tipo
 * novo for esquecido em algum switch.
 */
public enum CompendiumEntryType {
    CLASS,
    SUBCLASS,
    BACKGROUND,
    SPECIES,
    FEAT,
    ITEM,
    SPELL
}
