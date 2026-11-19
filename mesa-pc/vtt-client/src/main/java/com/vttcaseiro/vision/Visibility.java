package com.vttcaseiro.vision;

import com.vttcaseiro.model.Wall;

import java.util.ArrayList;
import java.util.List;

/**
 * Linha de visao 2D: o poligono do que um ponto enxerga com as paredes no
 * caminho (algoritmo classico de lancar raios para as pontas das paredes,
 * com um raio um pouquinho de cada lado pra "dobrar a esquina"), e o teste
 * de "este movimento atravessa uma parede?". Sem JavaFX: so numeros.
 */
public final class Visibility {

    private static final double EPS_ANGLE = 1e-4;

    private Visibility() {
    }

    /**
     * Poligono visivel a partir de (ox, oy), dentro do retangulo do mapa
     * (0, 0, width, height). Devolve x0, y0, x1, y1, ... em ordem de angulo.
     */
    public static double[] polygon(double ox, double oy, List<Wall> walls, double width, double height) {
        List<double[]> segs = new ArrayList<>();
        for (Wall w : walls) {
            if (w.blocksSight() && w.length() > 0.5) segs.add(new double[]{w.getX1(), w.getY1(), w.getX2(), w.getY2()});
        }
        // Bordas do mapa (um pouco pra fora, pra o token na beirada nao "vazar").
        double m = 1;
        segs.add(new double[]{-m, -m, width + m, -m});
        segs.add(new double[]{width + m, -m, width + m, height + m});
        segs.add(new double[]{width + m, height + m, -m, height + m});
        segs.add(new double[]{-m, height + m, -m, -m});

        List<Double> angles = new ArrayList<>();
        for (double[] s : segs) {
            for (int k = 0; k < 4; k += 2) {
                double a = Math.atan2(s[k + 1] - oy, s[k] - ox);
                angles.add(a - EPS_ANGLE);
                angles.add(a);
                angles.add(a + EPS_ANGLE);
            }
        }
        angles.sort(Double::compare);

        List<Double> out = new ArrayList<>();
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (double a : angles) {
            double dx = Math.cos(a);
            double dy = Math.sin(a);
            double best = Double.MAX_VALUE;
            for (double[] s : segs) {
                double t = rayHit(ox, oy, dx, dy, s);
                if (t >= 0 && t < best) best = t;
            }
            if (best == Double.MAX_VALUE) best = Math.max(width, height) * 4;
            double px = ox + dx * best;
            double py = oy + dy * best;
            if (Math.abs(px - lastX) < 0.01 && Math.abs(py - lastY) < 0.01) continue;
            out.add(px);
            out.add(py);
            lastX = px;
            lastY = py;
        }
        double[] result = new double[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /** Distancia t ao longo do raio ate o segmento, ou -1 se nao cruza. */
    private static double rayHit(double ox, double oy, double dx, double dy, double[] s) {
        double sx = s[2] - s[0];
        double sy = s[3] - s[1];
        double denom = dx * sy - dy * sx;
        if (Math.abs(denom) < 1e-12) return -1;
        double t = ((s[0] - ox) * sy - (s[1] - oy) * sx) / denom;
        double u = ((s[0] - ox) * dy - (s[1] - oy) * dx) / denom;
        if (t < 0 || u < 0 || u > 1) return -1;
        return t;
    }

    /** O caminho reto de A ate B atravessa alguma parede (ou porta fechada)? */
    public static boolean blocked(double ax, double ay, double bx, double by, List<Wall> walls) {
        for (Wall w : walls) {
            if (w.blocks() && crosses(ax, ay, bx, by, w.getX1(), w.getY1(), w.getX2(), w.getY2())) return true;
        }
        return false;
    }

    /** Os dois segmentos se cruzam de verdade (encostar so na ponta nao conta). */
    static boolean crosses(double ax, double ay, double bx, double by,
                           double cx, double cy, double dx, double dy) {
        double d1 = orient(cx, cy, dx, dy, ax, ay);
        double d2 = orient(cx, cy, dx, dy, bx, by);
        double d3 = orient(ax, ay, bx, by, cx, cy);
        double d4 = orient(ax, ay, bx, by, dx, dy);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orient(double ax, double ay, double bx, double by, double px, double py) {
        double v = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        return Math.abs(v) < 1e-9 ? 0 : v;
    }

    /** O ponto P esta dentro do poligono (x0, y0, x1, y1...)? */
    public static boolean inside(double[] poly, double px, double py) {
        boolean in = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[2 * i];
            double yi = poly[2 * i + 1];
            double xj = poly[2 * j];
            double yj = poly[2 * j + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    /** Pes -> pixels do mapa pela grade (5 pes por casa, 70 px por casa = 14 px/pe). */
    public static double feetToPx(double feet, double cellSize, double feetPerCell) {
        if (feetPerCell <= 0) feetPerCell = 5;
        return feet / feetPerCell * cellSize;
    }

    /** "Darkvision 60 ft." / "Visao no escuro 18 m" -> pes (0 se nao tem). */
    public static int darkvisionFeet(List<String> senses) {
        if (senses == null) return 0;
        java.util.regex.Pattern ft = java.util.regex.Pattern.compile(
                "(?i)(darkvision|vis[aã]o no escuro|blindsight|truesight|vis[aã]o verdadeira)\\s*(\\d+)\\s*(ft|p[eé]s|m)?");
        int best = 0;
        for (String s : senses) {
            java.util.regex.Matcher m = ft.matcher(s);
            while (m.find()) {
                int v = Integer.parseInt(m.group(2));
                if ("m".equalsIgnoreCase(m.group(3))) v = (int) Math.round(v / 1.5 * 5);
                best = Math.max(best, v);
            }
        }
        return best;
    }
}
