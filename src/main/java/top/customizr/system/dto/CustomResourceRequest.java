package top.customizr.system.dto;

import top.customizr.system.model.ResourceRecharge;

/** Recurso criado a mao: nome, usos maximos (1 a 999), quando volta (padrao: descanso longo) e anotacao. */
public record CustomResourceRequest(String name, Integer maxUses, ResourceRecharge recharge, String notes) {}
