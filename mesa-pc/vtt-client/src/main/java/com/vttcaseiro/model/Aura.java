package com.vttcaseiro.model;

/**
 * Aura de um token: um efeito (no formato dos efeitos, ex.: "Aura de Protecao [salv+3]" ou "Bencao") que vale pra
 * quem estiver a ate {@code radiusFeet} dele. Quem entra ganha o efeito sozinho; quem sai perde (ver AuraService).
 */
public class Aura {
    public static final String ALLIES = "aliados";
    public static final String ENEMIES = "inimigos";
    public static final String ALL = "todos";
    public static final int MAX_RADIUS_FEET = 300;

    private String effect = "";
    private int radiusFeet = 10;
    private String target = ALLIES;
    private boolean includeSelf = true;

    public Aura() {
    }

    public Aura(String effect, int radiusFeet, String target, boolean includeSelf) {
        setEffect(effect);
        setRadiusFeet(radiusFeet);
        setTarget(target);
        this.includeSelf = includeSelf;
    }

    public String getEffect() { return effect == null ? "" : effect; }
    public void setEffect(String effect) {
        String e = effect == null ? "" : effect.trim();
        this.effect = e.length() > 200 ? e.substring(0, 200) : e;
    }

    public int getRadiusFeet() { return Math.max(0, Math.min(MAX_RADIUS_FEET, radiusFeet)); }
    public void setRadiusFeet(int feet) { this.radiusFeet = Math.max(0, Math.min(MAX_RADIUS_FEET, feet)); }

    public String getTarget() { return ENEMIES.equals(target) || ALL.equals(target) ? target : ALLIES; }
    public void setTarget(String target) { this.target = ENEMIES.equals(target) || ALL.equals(target) ? target : ALLIES; }

    public boolean isIncludeSelf() { return includeSelf; }
    public void setIncludeSelf(boolean includeSelf) { this.includeSelf = includeSelf; }

    @Override
    public String toString() {
        String who = switch (getTarget()) {
            case ENEMIES -> "inimigos";
            case ALL -> "todos";
            default -> "aliados" + (includeSelf ? " (e ele mesmo)" : "");
        };
        return getEffect() + "  •  " + getRadiusFeet() + " pés  •  " + who;
    }
}
