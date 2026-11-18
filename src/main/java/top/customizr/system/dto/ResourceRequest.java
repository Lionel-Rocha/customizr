package top.customizr.system.dto;

/**
 * Gastar ou devolver usos de um recurso da ficha. {@code resource} = chave da classe ("barbarian.rage") ou o id
 * do recurso criado a mao. {@code amount}: gastar = 1 se vier vazio; devolver = tudo se vier vazio.
 */
public record ResourceRequest(String resource, Integer amount) {}
