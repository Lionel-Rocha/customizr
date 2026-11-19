package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Uma parede (segmento) no mapa. Parede bloqueia visao e movimento; porta bloqueia so quando fechada (qualquer um
 * abre a porta que ve); porta trancada so o mestre abre; porta secreta o jogador nem ve (e uma parede pra ele) ate o
 * mestre revelar; janela deixa ver, mas nao deixa passar.
 */
public class Wall {
    private String id = UUID.randomUUID().toString();
    private double x1;
    private double y1;
    private double x2;
    private double y2;
    private boolean door;
    private boolean open;
    /** Deixa ver atraves, mas nao passar (vidro, grade, seteira). */
    private boolean window;
    /** Porta trancada: so o mestre abre. */
    private boolean locked;
    /** Porta secreta: pro jogador e parede ate o mestre revelar (ou abrir). */
    private boolean secret;

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
    public boolean isWindow() { return window && !door; }
    public boolean isLocked() { return locked && door; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isSecret() { return secret && door; }
    public void setSecret(boolean secret) { this.secret = secret; }

    /** Tipo: parede, porta ou janela (trancada/secreta so valem pra porta). */
    public void setKind(boolean door, boolean window) {
        this.door = door;
        this.window = window && !door;
        if (!door) {
            open = false;
            locked = false;
            secret = false;
        }
    }

    /** Porta que so o mestre abre agora (trancada ou secreta fechada). */
    public boolean gmOnly() {
        return door && (locked || (secret && !open));
    }

    /** Bloqueia a passagem agora? (parede, janela, porta fechada) */
    public boolean blocks() {
        return !door || !open;
    }

    /** Bloqueia a visao agora? (janela deixa ver) */
    public boolean blocksSight() {
        return !isWindow() && blocks();
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
