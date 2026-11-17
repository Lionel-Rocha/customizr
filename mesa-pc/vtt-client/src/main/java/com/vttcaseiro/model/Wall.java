package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Uma parede (segmento) no mapa. Parede bloqueia visao e movimento;
 * porta bloqueia so quando fechada (qualquer um abre a porta que ve).
 */
public class Wall {
    private String id = UUID.randomUUID().toString();
    private double x1;
    private double y1;
    private double x2;
    private double y2;
    private boolean door;
    private boolean open;

    public Wall() {
    }

    public Wall(double x1, double y1, double x2, double y2, boolean door) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.door = door;
    }

    public String getId() { return id; }
    public double getX1() { return x1; }
    public double getY1() { return y1; }
    public double getX2() { return x2; }
    public double getY2() { return y2; }
    public boolean isDoor() { return door; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    /** Bloqueia visao e passagem agora? */
    public boolean blocks() {
        return !door || !open;
    }

    public double length() {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    public double midX() { return (x1 + x2) / 2; }
    public double midY() { return (y1 + y2) / 2; }

    /** Distancia do ponto ao segmento (pra apagar/abrir clicando perto). */
    public double distanceTo(double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }
}
