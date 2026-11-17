package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de um jogador pro mestre aplicar algo num token que o jogador
 * nao controla (o goblin que ele acertou). Vai pela rede ate UM mestre,
 * que aplica com as regras dele (CA, resistencias, salvaguardas dos
 * monstros, que so ele conhece de verdade).
 *
 * kind: "damage" (amount + damageType nos tokens), "heal" (amount) ou
 * "save" (os tokens rolam ability contra dc; amount = dano rolado, half =
 * metade pra quem passa).
 */
public class GmRequest {
    private String kind;
    private List<String> tokenIds = new ArrayList<>();
    private int amount;
    private String damageType;
    private String ability;
    private int dc;
    private boolean half;
    private String label;

    public GmRequest() {
    }

    public static GmRequest damage(List<String> tokenIds, int amount, String damageType, String label) {
        GmRequest r = new GmRequest();
        r.kind = "damage";
        r.tokenIds = new ArrayList<>(tokenIds);
        r.amount = amount;
        r.damageType = damageType;
        r.label = label;
        return r;
    }

    public static GmRequest heal(List<String> tokenIds, int amount, String label) {
        GmRequest r = damage(tokenIds, amount, null, label);
        r.kind = "heal";
        return r;
    }

    /** Efeito de magia (Bencao...) por {@code rounds} rodadas; nome vai em label. */
    public static GmRequest effect(List<String> tokenIds, String effectName, int rounds) {
        GmRequest r = damage(tokenIds, rounds, null, effectName);
        r.kind = "effect";
        return r;
    }

    public static GmRequest save(List<String> tokenIds, String ability, int dc, int amount, String damageType,
                                 boolean half, String label) {
        GmRequest r = damage(tokenIds, amount, damageType, label);
        r.kind = "save";
        r.ability = ability;
        r.dc = dc;
        r.half = half;
        return r;
    }

    public String getKind() { return kind; }
    public List<String> getTokenIds() { return tokenIds == null ? List.of() : tokenIds; }
    public int getAmount() { return amount; }
    public String getDamageType() { return damageType; }
    public String getAbility() { return ability; }
    public int getDc() { return dc; }
    public boolean isHalf() { return half; }
    public String getLabel() { return label; }
}
