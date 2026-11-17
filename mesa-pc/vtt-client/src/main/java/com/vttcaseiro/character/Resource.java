package com.vttcaseiro.character;

/**
 * Algo da ficha que tem usos contados: "Furia 3/3", "Cargas da varinha 7/7",
 * "Pocao de cura 2". Recarrega no descanso curto, no longo, ou nunca
 * (consumivel).
 */
public class Resource {
    public static final String SHORT = "curto";
    public static final String LONG = "longo";
    public static final String NEVER = "nunca";

    private String name = "";
    private int current;
    private int max;
    private String recharge = LONG;

    public Resource() {
    }

    public Resource(String name, int max, String recharge) {
        setName(name);
        setMax(max);
        this.current = this.max;
        setRecharge(recharge);
    }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name == null ? "" : name.trim(); }

    public int getCurrent() { return current; }
    public void setCurrent(int current) { this.current = Math.max(0, Math.min(max, current)); }

    public int getMax() { return max; }
    public void setMax(int max) {
        this.max = Math.max(0, max);
        if (current > this.max) current = this.max;
    }

    public String getRecharge() { return recharge == null ? LONG : recharge; }
    public void setRecharge(String recharge) {
        this.recharge = SHORT.equals(recharge) || NEVER.equals(recharge) ? recharge : LONG;
    }

    /** Gasta 1. false se ja acabou. */
    public boolean use() {
        if (current <= 0) return false;
        current--;
        return true;
    }

    /** Recupera tudo se este descanso recarrega o recurso (longo recarrega curto e longo). */
    public boolean recover(boolean longRest) {
        boolean recharges = SHORT.equals(getRecharge()) || (longRest && LONG.equals(getRecharge()));
        if (!recharges || current == max) return false;
        current = max;
        return true;
    }

    public String usesText() {
        return current + "/" + max;
    }
}
