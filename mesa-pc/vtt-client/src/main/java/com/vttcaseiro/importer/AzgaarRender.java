package com.vttcaseiro.importer;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Desenha o mapa do Azgaar a partir do JSON (a geometria das celulas): mar com profundidade, terra pela cor do
 * bioma e escurecendo com a altura (ou pela cor do reino, no estilo politico), costa, fronteiras, rios, estradas,
 * povoados e o nome dos reinos. Desenha o mundo inteiro ou so um recorte (em pixels do mapa), sempre nitido, porque
 * vem dos poligonos e nao de uma imagem ampliada. As linhas e as letras tem tamanho fixo na imagem de saida.
 */
public final class AzgaarRender {
    private AzgaarRender() {
    }

    public enum Style { RELEVO, POLITICO }

    /** Desenha o recorte (rx, ry, rw, rh em pixels do mapa) em outW x outH e devolve o PNG. */
    public static byte[] png(AzgaarMap.World world, AzgaarJson.Geometry g, double rx, double ry, double rw, double rh,
                             int outW, int outH, Style style) throws IOException {
        BufferedImage img = image(world, g, rx, ry, rw, rh, outW, outH, style);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Tamanho de saida pra um recorte: lado maior = longSide, mantendo a proporcao. */
    public static int[] outSize(double rw, double rh, int longSide) {
        if (rw >= rh) return new int[]{longSide, Math.max(1, (int) Math.round(longSide * rh / rw))};
        return new int[]{Math.max(1, (int) Math.round(longSide * rw / rh)), longSide};
    }

    public static BufferedImage image(AzgaarMap.World world, AzgaarJson.Geometry g, double rx, double ry, double rw, double rh,
                                      int outW, int outH, Style style) {
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gr.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        gr.setColor(new Color(0x34, 0x5c, 0x96));
        gr.fillRect(0, 0, outW, outH);
        double s = outW / rw;
        AffineTransform base = gr.getTransform();
        AffineTransform map = new AffineTransform(base);
        map.scale(s, outH / rh);
        map.translate(-rx, -ry);
        gr.setTransform(map);
        float px = (float) (1 / s); // 1 pixel da imagem em unidades do mapa

        // celulas: so as que tocam o recorte
        double margin = 40;
        int n = g.cells();
        boolean[] visible = new boolean[n];
        Path2D[] polys = new Path2D[n];
        for (int i = 0; i < n; i++) {
            int[] v = g.cellVertices()[i];
            if (v.length < 3) continue;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            Path2D.Double p = new Path2D.Double();
            boolean first = true;
            for (int vi : v) {
                if (vi < 0 || vi >= g.vertexX().length) continue;
                double x = g.vertexX()[vi], y = g.vertexY()[vi];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (first) p.moveTo(x, y); else p.lineTo(x, y);
                first = false;
            }
            p.closePath();
            if (maxX < rx - margin || minX > rx + rw + margin || maxY < ry - margin || minY > ry + rh + margin) continue;
            visible[i] = true;
            polys[i] = p;
        }
        for (int i = 0; i < n; i++) {
            if (!visible[i]) continue;
            Color c = cellColor(g, i, style);
            gr.setColor(c);
            gr.fill(polys[i]);
            gr.setStroke(new BasicStroke(px * 0.8f)); // tampa a fresta entre poligonos vizinhos
            gr.draw(polys[i]);
        }

        // costa e fronteiras: a aresta (os 2 vertices em comum) entre vizinhos diferentes
        BasicStroke coast = new BasicStroke(px * 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke border = new BasicStroke(px * 1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                new float[]{px * 6, px * 3}, 0);
        Color coastColor = new Color(0x2f, 0x2a, 0x24);
        Color borderColor = new Color(0x6a, 0x22, 0x22, 210);
        for (int i = 0; i < n; i++) {
            if (!visible[i]) continue;
            int[] nb = i < g.cellNeighbors().length ? g.cellNeighbors()[i] : new int[0];
            for (int j : nb) {
                if (j <= i || j >= n) continue;
                boolean landI = g.height()[i] >= 20, landJ = g.height()[j] >= 20;
                boolean isCoast = landI != landJ;
                boolean isBorder = landI && landJ && g.state()[i] != g.state()[j];
                if (!isCoast && !isBorder) continue;
                int[] edge = sharedEdge(g.cellVertices()[i], g.cellVertices()[j]);
                if (edge == null) continue;
                Path2D.Double line = new Path2D.Double();
                line.moveTo(g.vertexX()[edge[0]], g.vertexY()[edge[0]]);
                line.lineTo(g.vertexX()[edge[1]], g.vertexY()[edge[1]]);
                gr.setStroke(isCoast ? coast : border);
                gr.setColor(isCoast ? coastColor : borderColor);
                gr.draw(line);
            }
        }

        // estradas
        for (AzgaarJson.Route r : g.routes()) {
            if (r.xs().length < 2) continue;
            String grp = r.group() == null ? "" : r.group();
            if (grp.startsWith("sea")) {
                gr.setColor(new Color(255, 255, 255, 70));
                gr.setStroke(new BasicStroke(px * 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{px * 4, px * 4}, 0));
            } else if (grp.startsWith("trail")) {
                gr.setColor(new Color(0x7a, 0x55, 0x30, 200));
                gr.setStroke(new BasicStroke(px * 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{px * 3, px * 3}, 0));
            } else {
                gr.setColor(new Color(0x6b, 0x40, 0x1c, 220));
                gr.setStroke(new BasicStroke(px * 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            Path2D.Double p = new Path2D.Double();
            p.moveTo(r.xs()[0], r.ys()[0]);
            for (int i = 1; i < r.xs().length; i++) p.lineTo(r.xs()[i], r.ys()[i]);
            gr.draw(p);
        }

        // rios: pelos centros das celulas, mais grossos quanto maior a vazao
        gr.setColor(new Color(0x4d, 0x86, 0xb8));
        for (AzgaarJson.River r : g.rivers()) {
            Path2D.Double p = new Path2D.Double();
            boolean first = true;
            for (int c : r.cells()) {
                if (c < 0 || c >= n) continue;
                if (first) p.moveTo(g.cellX()[c], g.cellY()[c]); else p.lineTo(g.cellX()[c], g.cellY()[c]);
                first = false;
            }
            if (first) continue;
            float w = (float) Math.max(0.8, Math.min(4.5, 0.6 + Math.log10(Math.max(1, r.discharge())) * 0.9));
            gr.setStroke(new BasicStroke(px * w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gr.draw(p);
        }

        // a partir daqui, em pixels da imagem (letras e pontos com tamanho fixo)
        gr.setTransform(base);
        double sy = outH / rh;
        // nomes dos reinos
        Font stateFont = new Font(Font.SERIF, Font.BOLD | Font.ITALIC, Math.max(14, Math.min(40, (int) (outW / 45.0))));
        for (int i = 0; i < g.statePoles().size(); i++) {
            double[] pole = g.statePoles().get(i);
            double x = (pole[0] - rx) * s, y = (pole[1] - ry) * sy;
            if (x < 0 || y < 0 || x > outW || y > outH) continue;
            label(gr, g.stateNames().get(i).toUpperCase(), stateFont, x, y, new Color(70, 35, 30, 175), new Color(255, 250, 235, 70));
        }
        // povoados: capitais sempre; os outros conforme cabem (os maiores primeiro)
        List<AzgaarMap.Burg> burgs = new ArrayList<>();
        for (AzgaarMap.Burg b : world.burgs()) if (b.x() >= rx && b.y() >= ry && b.x() <= rx + rw && b.y() <= ry + rh) burgs.add(b);
        burgs.sort(Comparator.comparing(AzgaarMap.Burg::capital).thenComparingDouble(AzgaarMap.Burg::population).reversed());
        int maxLabels = Math.max(12, (int) (outW * outH / 60000.0));
        Font capFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, outW / 110));
        Font townFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, outW / 150));
        int labeled = 0;
        for (AzgaarMap.Burg b : burgs) {
            double x = (b.x() - rx) * s, y = (b.y() - ry) * sy;
            double r = b.capital() ? 5 : b.population() >= 2000 ? 3.5 : 2.5;
            gr.setColor(Color.WHITE);
            gr.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            gr.setColor(new Color(0x22, 0x1c, 0x18));
            gr.setStroke(new BasicStroke(b.capital() ? 1.6f : 1.1f));
            gr.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            if (labeled < maxLabels) {
                label(gr, b.name(), b.capital() ? capFont : townFont, x, y - r - 4, new Color(0x1e, 0x18, 0x14),
                        new Color(255, 250, 240, 200));
                labeled++;
            }
        }
        gr.dispose();
        return img;
    }

    /** Cor da celula: mar mais escuro no fundo; terra pela cor do bioma (ou do reino) e mais escura no alto. */
    static Color cellColor(AzgaarJson.Geometry g, int i, Style style) {
        int h = g.height()[i];
        if (h < 20) {
            double t = Math.max(0, Math.min(1, h / 20.0));
            return mix(new Color(0x23, 0x46, 0x7c), new Color(0x5d, 0x8f, 0xc9), t);
        }
        int b = g.biome()[i];
        Color biome = parse(b >= 0 && b < g.biomeColors().length ? g.biomeColors()[b] : null, new Color(0xc8, 0xd6, 0x8f));
        double alt = (h - 20) / 80.0; // 0 na praia, 1 no pico
        if (style == Style.POLITICO) {
            // pergaminho tingido com a cor do reino; o relevo so escurece um pouco
            Color parchment = new Color(0xf0, 0xe6, 0xcf);
            int st = g.state()[i];
            String sc = st > 0 && st < g.stateColors().length ? g.stateColors()[st] : null;
            Color tint = sc == null ? parchment : mix(parchment, parse(sc, parchment), 0.55);
            return mix(tint, new Color(0x5a, 0x4a, 0x3a), alt * 0.25);
        }
        Color land = biome;
        if (h >= 70) land = mix(land, new Color(0x8a, 0x7d, 0x6b), Math.min(1, (h - 70) / 25.0) * 0.8);
        if (h >= 90) land = mix(land, new Color(0xf2, 0xf2, 0xf2), (h - 90) / 10.0 * 0.7);
        return mix(land, Color.BLACK, alt * 0.22);
    }

    /** Os dois vertices em comum entre duas celulas (a aresta que elas dividem). */
    static int[] sharedEdge(int[] a, int[] b) {
        int first = -1;
        for (int x : a) {
            for (int y : b) {
                if (x != y) continue;
                if (first < 0) first = x;
                else if (x != first) return new int[]{first, x};
            }
        }
        return null;
    }

    private static void label(Graphics2D gr, String text, Font font, double x, double y, Color fill, Color halo) {
        if (text == null || text.isBlank()) return;
        FontMetrics fm = gr.getFontMetrics(font);
        float tx = (float) (x - fm.stringWidth(text) / 2.0);
        float ty = (float) y;
        GlyphVector gv = font.createGlyphVector(gr.getFontRenderContext(), text);
        java.awt.Shape shape = gv.getOutline(tx, ty);
        gr.setColor(halo);
        gr.setStroke(new BasicStroke(Math.max(2.5f, font.getSize() / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gr.draw(shape);
        gr.setColor(fill);
        gr.fill(shape);
    }

    private static Color parse(String hex, Color fallback) {
        if (hex == null) return fallback;
        try {
            String h = hex.strip();
            if (h.startsWith("#") && h.length() == 4) {
                h = "#" + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2) + h.charAt(3) + h.charAt(3);
            }
            return h.startsWith("#") && h.length() >= 7 ? new Color(Integer.parseInt(h.substring(1, 7), 16)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static Color mix(Color a, Color b, double t) {
        double k = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }
}
