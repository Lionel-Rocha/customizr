package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Luz fixa no mapa (tocha na parede, fogueira, vitral, lustre): luz plena ate um raio e penumbra alem dele, como as
 * fontes do Livro do Jogador 2024. Liga e apaga. So o mestre poe e mexe; o jogador ve so a luz.
 */
public class MapLight {
    public static final int MAX_FEET = 1000;

    private String id = UUID.randomUUID().toString();
    private String name = "Luz";
    private double x;
    private double y;
    private int brightFeet = 20;
    private int dimFeet = 20;
    private boolean on = true;

    public MapLight() {
    }

    public MapLight(String name, double x, double y, int brightFeet, int dimFeet) {
        setName(name);
        setPosition(x, y);
        setBrightFeet(brightFeet);
        setDimFeet(dimFeet);
    }

    public String getId() { return id; }
    public String getName() { return name == null ? "Luz" : name; }
    public void setName(String name) {
        String n = name == null ? "" : name.strip();
        this.name = n.isEmpty() ? "Luz" : n.length() > 60 ? n.substring(0, 60) : n;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) {
        this.x = Double.isFinite(x) ? x : 0;
        this.y = Double.isFinite(y) ? y : 0;
    }
    public int getBrightFeet() { return brightFeet; }
    public void setBrightFeet(int feet) { this.brightFeet = Math.max(0, Math.min(MAX_FEET, feet)); }
    public int getDimFeet() { return dimFeet; }
    public void setDimFeet(int feet) { this.dimFeet = Math.max(0, Math.min(MAX_FEET, feet)); }
    public boolean isOn() { return on; }
    public void setOn(boolean on) { this.on = on; }

    /** Alcance total (plena + penumbra). */
    public int reachFeet() { return brightFeet + dimFeet; }

    /** Limpa o que veio pela rede. */
    public void sanitize() {
        if (id == null || id.isBlank() || id.length() > 64) id = UUID.randomUUID().toString();
        setName(name);
        setPosition(x, y);
        setBrightFeet(brightFeet);
        setDimFeet(dimFeet);
    }
}
