package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Objeto solto no mapa (moveis, arvores, baus, carroca...): uma imagem da galeria da mesa
 * por cima do fundo e embaixo dos desenhos/tokens. A ordem na lista da cena e a ordem de
 * desenho (o ultimo fica por cima). Objeto oculto so o mestre ve (o servidor nem manda).
 */
public class MapObject {
    public static final double MIN_SIZE = 8;
    public static final double MAX_SIZE = 8000;

    private String id = UUID.randomUUID().toString();
    private String assetId;
    private String name = "";
    private double x;
    private double y;
    private double width = 70;
    private double height = 70;
    private double rotation;
    private boolean hidden;
    /** Travado: nao arrasta sem querer (o mestre destrava no menu). */
    private boolean locked;

    public MapObject() {
    }

    public MapObject(String assetId, double x, double y, double width, double height) {
        this.assetId = assetId;
        this.x = x;
        this.y = y;
        setSize(width, height);
    }

    public String getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name == null ? "" : name.strip(); }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) {
        this.x = Double.isFinite(x) ? x : 0;
        this.y = Double.isFinite(y) ? y : 0;
    }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public void setSize(double w, double h) {
        this.width = clamp(w);
        this.height = clamp(h);
    }
    /** Aumenta/diminui mantendo o centro no lugar. */
    public void scaleBy(double factor) {
        double cx = x + width / 2, cy = y + height / 2;
        setSize(width * factor, height * factor);
        x = cx - width / 2;
        y = cy - height / 2;
    }
    public double getRotation() { return rotation; }
    public void setRotation(double degrees) {
        double r = Double.isFinite(degrees) ? degrees % 360 : 0;
        this.rotation = r < 0 ? r + 360 : r;
    }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    /** Copia com id novo (Duplicar), um pouco deslocada. */
    public MapObject duplicate(double dx, double dy) {
        MapObject c = new MapObject(assetId, x + dx, y + dy, width, height);
        c.name = name;
        c.rotation = rotation;
        c.hidden = hidden;
        return c;
    }

    /** Limpa o que veio pela rede. */
    public void sanitize() {
        setSize(width, height);
        setPosition(x, y);
        setRotation(rotation);
        setName(name);
        if (name.length() > 80) name = name.substring(0, 80);
        if (assetId != null && !assetId.matches("[0-9a-f]{64}(\\.[a-z0-9]{1,5})?")) assetId = null;
    }

    private static double clamp(double v) {
        return Double.isFinite(v) ? Math.max(MIN_SIZE, Math.min(MAX_SIZE, v)) : 70;
    }
}
