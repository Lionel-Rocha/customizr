package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;

import java.util.UUID;

/**
 * Resolve a area: cada token dentro dela faz a salvaguarda contra a CD; o dano e rolado uma vez so (como no livro)
 * e cada um leva inteiro (falhou), metade (passou, halfOnSuccess) ou nada.
 * Atalho: attackerTokenId + attackId de uma acao de salvaguarda do monstro preenche CD, atributo e dano.
 */
public record TemplateResolveRequest(AbilityScore ability, Integer dc, String damage, String damageType,
                                     Boolean halfOnSuccess, Boolean applyDamage, UUID attackerTokenId, String attackId) {}
