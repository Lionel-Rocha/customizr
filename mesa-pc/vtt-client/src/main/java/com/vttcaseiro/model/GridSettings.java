package com.vttcaseiro.model;

/** Configuracao da grade sobre o mapa. cellSize em pixels, na resolucao
 * nativa da imagem de fundo (o zoom da tela e so visual, nao muda isso).
 * feetPerCell e o que faltava pra medir distancia de verdade (regua do
 * MapView, Fase 3) -- antes a grade nao sabia quantos "pes" uma celula
 * representava, so pixels. Padrao 5 (a conversao mais comum do D&D 5e:
 * uma celula de combate = 5 pes = 1,5m). */
public class GridSettings {
    private double cellSize = 70;
    private double feetPerCell = 5;
    private double offsetX = 0;
    private double offsetY = 0;
    private boolean visible = true;

    public static final String SQUARE = "square";
    public static final String HEX = "hex";
    public static final String NONE = "none";

    /** Tipo de grade: quadrada (padrao), hexagonal (hexagonos "em pe") ou sem grade. */
    private String type;

    public String getType() { return HEX.equals(type) || NONE.equals(type) ? type : SQUARE; }
    public void setType(String type) { this.type = SQUARE.equals(type) ? null : type; }
    public boolean isHex() { return HEX.equals(type); }
    public boolean isGridless() { return NONE.equals(type); }

    // ---- hexagonos "em pe" (pointy-top): cellSize = largura do hexagono (distancia entre centros vizinhos)

    /** Raio (centro ao canto) do hexagono. */
    public double hexRadius() { return cellSize / Math.sqrt(3); }

    /** Centro do hexagono de coordenada axial (q, r). */
    public double[] hexCenter(int q, int r) {
        double s = hexRadius();
        return new double[]{offsetX + cellSize / 2 + cellSize * (q + r / 2.0), offsetY + s + 1.5 * s * r};
    }

    /** Hexagono (axial q, r) que contem o ponto. */
    public int[] hexAt(double x, double y) {
        double s = hexRadius();
        double px = x - (offsetX + cellSize / 2);
        double py = y - (offsetY + s);
        double q = (Math.sqrt(3) / 3 * px - py / 3) / s;
        double r = (2.0 / 3 * py) / s;
        // arredondamento cubico
        double cx = q, cz = r, cy = -cx - cz;
        long rx = Math.round(cx), ry = Math.round(cy), rz = Math.round(cz);
        double dx = Math.abs(rx - cx), dy = Math.abs(ry - cy), dz = Math.abs(rz - cz);
        if (dx > dy && dx > dz) rx = -ry - rz;
        else if (dy <= dz) rz = -rx - ry;
        return new int[]{(int) rx, (int) rz};
    }

    /** Todos os hexagonos que cobrem uma area w x h (pra desenhar a grade ou a neblina). */
    public java.util.List<int[]> hexesCovering(double w, double h) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        double s = hexRadius();
        int rows = (int) Math.ceil(h / (1.5 * s)) + 2;
        int cols = (int) Math.ceil(w / cellSize) + 2;
        for (int r = -1; r < rows; r++) {
            int qStart = -(r / 2) - 2;
            for (int q = qStart; q < qStart + cols + 2; q++) {
                double[] c = hexCenter(q, r);
                if (c[0] < -cellSize || c[0] > w + cellSize || c[1] < -cellSize || c[1] > h + cellSize) continue;
                out.add(new int[]{q, r});
            }
        }
        return out;
    }

    /** Hexagonos cujo centro esta dentro do retangulo (ferramenta de neblina). */
    public java.util.List<int[]> hexesInRect(double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0, x1), maxX = Math.max(x0, x1), minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int[] h : hexesCovering(maxX + cellSize, maxY + cellSize)) {
            double[] c = hexCenter(h[0], h[1]);
            if (c[0] >= minX && c[0] <= maxX && c[1] >= minY && c[1] <= maxY) out.add(h);
        }
        if (out.isEmpty()) out.add(hexAt((minX + maxX) / 2, (minY + maxY) / 2)); // clique simples: o hexagono do ponto
        return out;
    }

    public static int hexDistance(int[] a, int[] b) {
        int dq = a[0] - b[0], dr = a[1] - b[1];
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    /**
     * Onde um token de {@code cells} x {@code cells} solto em (x, y) (canto de cima) para:
     * quadrada = canto da casa; hexagonal = centrado no hexagono mais perto; sem grade = onde soltou.
     */
    public double[] snapTopLeft(double x, double y, int cells) {
        if (isGridless() || cellSize <= 0) return new double[]{x, y};
        if (isHex()) {
            double half = cellSize * cells / 2;
            int[] h = hexAt(x + half, y + half);
            double[] c = hexCenter(h[0], h[1]);
            return new double[]{c[0] - half, c[1] - half};
        }
        return new double[]{Math.round(x / cellSize) * cellSize, Math.round(y / cellSize) * cellSize};
    }

    /** Distancia em casas entre dois pontos (quadrada: diagonal conta 1; hex: passos; sem grade: em linha reta). */
    public long cellsBetween(double x1, double y1, double x2, double y2) {
        if (cellSize <= 0) return 0;
        if (isHex()) return hexDistance(hexAt(x1, y1), hexAt(x2, y2));
        if (isGridless()) return Math.round(Math.hypot(x2 - x1, y2 - y1) / cellSize);
        return Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / cellSize);
    }

    public double getCellSize() { return cellSize; }
    public void setCellSize(double cellSize) { this.cellSize = cellSize; }

    public double getFeetPerCell() { return feetPerCell; }
    public void setFeetPerCell(double feetPerCell) { this.feetPerCell = feetPerCell; }

    public double getOffsetX() { return offsetX; }
    public void setOffsetX(double offsetX) { this.offsetX = offsetX; }

    public double getOffsetY() { return offsetY; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
