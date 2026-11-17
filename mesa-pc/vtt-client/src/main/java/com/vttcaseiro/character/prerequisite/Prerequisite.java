package com.vttcaseiro.character.prerequisite;

import com.vttcaseiro.character.CharacterSheet;

/**
 * Um pre-requisito pra pegar um feat (ou qualquer outra coisa que precise
 * de validacao parecida no futuro). Inspirado no padrao que vi no projeto
 * da minha noiva (Prerequisite com AbilityPrerequisite/LevelPrerequisite/
 * FeatPrerequisite/etc. implementando -- cada tipo sabe validar a si
 * mesmo, em vez de um "if" gigante checando tipo de pre-requisito num
 * lugar so). Novo tipo de pre-requisito = nova classe implementando isto,
 * sem mexer em nada que ja existe.
 */
public interface Prerequisite {
    boolean isMetBy(CharacterSheet sheet);

    /** Texto pronto pra mostrar na tela (ex.: "Nivel 4 ou mais"). */
    String describe();
}
