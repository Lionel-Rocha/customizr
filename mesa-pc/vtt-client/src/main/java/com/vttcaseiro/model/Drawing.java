package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um desenho no mapa: traco a mao livre ("path") ou texto ("text"). Fica
 * na cena (salvo com ela) e vai pela rede pra todos. Quem desenhou
 * ({@code ownerId}) e o mestre podem apagar.
 */
public class Drawing {
    public static final String PATH = "path";
    public static final String TEXT = "text";
    /** Limites pra um desenho nao virar um arquivo/mensagem gigante. */
    public static final int MAX_POINTS = 4000;
    public static final int MAX_TEXT = 200;

    private String id = UUID.randomUUID().toString();
    private String kind = PATH;
    /** x0, y0, x1, y1, ... no espaco do mapa. Texto: so x, y. */
    private List<Double> points = new ArrayList<>();
    private String color = "#E2483F";
    private double width = 3;
    private String text;
    private String ownerId;

    public Drawing() {
    }

    public static Drawing path(List<Double> points, String color, double width) {
        Drawing d = new Drawing();
        d.kind = PATH;
        d.setPoints(points);
        d.color = color;
        d.width = width;
        return d;
    }

    public static Drawing text(double x, double y, String text, String color) {
        Drawing d = new Drawing();
        d.kind = TEXT;
        d.setPoints(List.of(x, y));
        d.setText(text);
        d.color = color;
        return d;
    }

    public String getId() { return id; }
    public String getKind() { return TEXT.equals(kind) ? TEXT : PATH; }

    public List<Double> getPoints() {
        if (points == null) points = new ArrayList<>();
        return points;
    }

    public void setPoints(List<Double> pts) {
        List<Double> list = new ArrayList<>(pts == null ? List.of() : pts);
        if (list.size() % 2 == 1) list.remove(list.size() - 1);
        if (list.size() > MAX_POINTS * 2) list = new ArrayList<>(list.subList(0, MAX_POINTS * 2));
        this.points = list;
    }

    public String getColor() {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : "#E2483F";
    }

    public void setColor(String color) { this.color = color; }

    public double getWidth() { return Math.max(1, Math.min(20, width)); }
    public void setWidth(double width) { this.width = width; }

    public String getText() { return text == null ? "" : text; }

    public void setText(String text) {
        String t = text == null ? "" : text.strip();
        this.text = t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    /** Menor distancia do ponto (x, y) ao desenho (pra borracha). */
    public double distanceTo(double x, double y) {
        List<Double> p = getPoints();
        if (p.size() < 2) return Double.MAX_VALUE;
        if (TEXT.equals(getKind())) {
            // Caixa aproximada do texto: ~8 px por letra, 20 de altura, a partir do ponto.
            double w = Math.max(20, getText().length() * 8.0);
            double dx = Math.max(0, Math.max(p.get(0) - x, x - (p.get(0) + w)));
            double dy = Math.max(0, Math.max(p.get(1) - 20 - y, y - (p.get(1) + 4)));
            return Math.hypot(dx, dy);
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 3 < p.size(); i += 2) {
            best = Math.min(best, segment(x, y, p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3)));
        }
        if (p.size() == 2) best = Math.hypot(x - p.get(0), y - p.get(1));
        return best;
    }

    private static double segment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }
}
