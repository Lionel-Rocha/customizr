package com.vttcaseiro.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.model.Wall;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Masmorra do One Page Dungeon (watabou.github.io/one-page-dungeon, botao direito > Export > JSON).
 * O JSON traz salas ("rects" em celulas), portas ("doors" com direcao e tipo) e notas numeradas.
 * Daqui sai uma cena pronta: a imagem desenhada por nos (piso, grade, paredes, portas, numeros),
 * as PAREDES e PORTAS do sistema de visao, e o texto das notas pro diario.
 */
public final class WatabouDungeon {

    // Tipos de porta do formato do One Page Dungeon.
    static final int EMPTY = 0, DOOR = 1, OPENING = 2, STAIRS_DOWN = 3, BARS = 4, DOUBLE = 5, SECRET = 6,
            FLUSH = 7, STAIRS_UP = 8;

    /** Resultado: imagem PNG, tamanho da celula em pixels e o que vai pra cena. */
    public record Result(String title, byte[] png, double cellPx, List<Wall> walls, String journalText,
                         int rooms, int doors, List<com.vttcaseiro.model.MapPin> pins) {
    }

    private record Door(int x, int y, int dx, int dy, int type) {
    }

    private WatabouDungeon() {
    }

    /** O JSON e do One Page Dungeon? (tem "rects" e "doors") */
    public static boolean looksLike(JsonObject o) {
        return o != null && o.has("rects") && o.get("rects").isJsonArray();
    }

    public static Result parse(String json) throws IOException {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json.startsWith("﻿") ? json.substring(1) : json);
            o = e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            throw new IOException("Não é um JSON válido: " + ex.getMessage());
        }
        if (!looksLike(o)) {
            throw new IOException("Esse JSON não e de masmorra do One Page Dungeon (não tem \"rects\"). "
                    + "Cidades e vilas do Watabou: exporte como PNG e use a opção de imagem.");
        }

        Set<Long> floor = new HashSet<>();
        int rooms = 0;
        for (JsonElement el : Json.arr(o, "rects")) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            int x = Json.intOr(r, "x", 0), y = Json.intOr(r, "y", 0);
            int w = Math.max(1, Json.intOr(r, "w", 1)), h = Math.max(1, Json.intOr(r, "h", 1));
            if (w > 200 || h > 200) continue;
            boolean rotunda = r.has("rotunda") && r.get("rotunda").isJsonPrimitive() && r.get("rotunda").getAsBoolean();
            if (w > 1 && h > 1) rooms++;
            for (int cx = x; cx < x + w; cx++) {
                for (int cy = y; cy < y + h; cy++) {
                    if (rotunda) {
                        double nx = (cx + 0.5 - (x + w / 2.0)) / (w / 2.0);
                        double ny = (cy + 0.5 - (y + h / 2.0)) / (h / 2.0);
                        if (nx * nx + ny * ny > 1.0) continue;
                    }
                    floor.add(key(cx, cy));
                }
            }
        }
        if (floor.isEmpty()) throw new IOException("A masmorra está vazia (sem salas).");
        List<Door> doors = new ArrayList<>();
        for (JsonElement el : Json.arr(o, "doors")) {
            if (!el.isJsonObject()) continue;
            JsonObject d = el.getAsJsonObject();
            Door door = new Door(Json.intOr(d, "x", 0), Json.intOr(d, "y", 0),
                    Json.intOr(d, "dir.x", 0), Json.intOr(d, "dir.y", 0), Json.intOr(d, "type", DOOR));
            doors.add(door);
            floor.add(key(door.x, door.y));
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (long k : floor) {
            int x = kx(k), y = ky(k);
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        int cols = maxX - minX + 3, rows = maxY - minY + 3; // 1 celula de margem em volta
        double cell = Math.max(24, Math.min(70, Math.floor(4000.0 / Math.max(cols, rows))));
        int ox = 1 - minX, oy = 1 - minY;

        // Paredes: borda do piso, juntando trechos em linha reta.
        List<Wall> walls = new ArrayList<>();
        mergeEdges(floor, minX, maxX, minY, maxY, ox, oy, cell, walls);
        int doorCount = 0;
        for (Door d : doors) {
            // So vira porta (parede que abre) o que e porta de verdade; tipos desconhecidos (ex.: 9, que o
            // gerador usa sem documentar) ficam como passagem livre -- melhor que travar a visao a toa.
            if (!blocksAsDoor(d.type)) continue;
            double px = (d.x + ox) * cell, py = (d.y + oy) * cell;
            if (d.dx != 0) walls.add(new Wall(px + cell / 2, py, px + cell / 2, py + cell, true)); // passagem horizontal
            else walls.add(new Wall(px, py + cell / 2, px + cell, py + cell / 2, true));
            doorCount++;
        }

        byte[] png = render(o, floor, doors, cols, rows, ox, oy, cell);
        String title = Json.str(o, "title");
        StringBuilder journal = new StringBuilder();
        String story = Json.str(o, "story");
        if (story != null) journal.append(story).append("\n\n");
        for (JsonElement el : Json.arr(o, "notes")) {
            if (!el.isJsonObject()) continue;
            String ref = Json.str(el.getAsJsonObject(), "ref");
            String text = Json.str(el.getAsJsonObject(), "text");
            if (text != null) journal.append(ref == null ? "-" : ref + ".").append(' ').append(text).append("\n\n");
        }
        // Cada nota numerada vira um pin OCULTO (so o mestre ve) em cima do numero: clicou, leu.
        List<com.vttcaseiro.model.MapPin> pins = new ArrayList<>();
        for (JsonElement el : Json.arr(o, "notes")) {
            if (!el.isJsonObject()) continue;
            JsonObject n = el.getAsJsonObject();
            String text = Json.str(n, "text");
            Double nx = Json.num(n, "pos.x"), ny = Json.num(n, "pos.y");
            if (text == null || nx == null || ny == null) continue;
            String ref = Json.str(n, "ref");
            String shortText = text.length() > 40 ? text.substring(0, 38).strip() + "..." : text;
            com.vttcaseiro.model.MapPin pin = new com.vttcaseiro.model.MapPin((nx + ox) * cell, (ny + oy) * cell,
                    (ref == null ? "" : ref + ". ") + shortText, "info");
            pin.setText(text);
            pin.setHidden(true);
            pins.add(pin);
        }
        return new Result(title == null ? "Masmorra" : title, png, cell, walls, journal.toString().strip(),
                rooms, doorCount, pins);
    }

    private static void mergeEdges(Set<Long> floor, int minX, int maxX, int minY, int maxY, int ox, int oy,
                                   double cell, List<Wall> walls) {
        // Bordas horizontais (em cima de cada celula, linha y), percorrendo x.
        for (int y = minY; y <= maxY + 1; y++) {
            int start = Integer.MIN_VALUE;
            for (int x = minX; x <= maxX + 1; x++) {
                boolean edge = floor.contains(key(x, y)) != floor.contains(key(x, y - 1));
                if (edge && start == Integer.MIN_VALUE) start = x;
                if (!edge && start != Integer.MIN_VALUE) {
                    walls.add(new Wall((start + ox) * cell, (y + oy) * cell, (x + ox) * cell, (y + oy) * cell, false));
                    start = Integer.MIN_VALUE;
                }
            }
        }
        for (int x = minX; x <= maxX + 1; x++) {
            int start = Integer.MIN_VALUE;
            for (int y = minY; y <= maxY + 1; y++) {
                boolean edge = floor.contains(key(x, y)) != floor.contains(key(x - 1, y));
                if (edge && start == Integer.MIN_VALUE) start = y;
                if (!edge && start != Integer.MIN_VALUE) {
                    walls.add(new Wall((x + ox) * cell, (start + oy) * cell, (x + ox) * cell, (y + oy) * cell, false));
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

    private static byte[] render(JsonObject o, Set<Long> floor, List<Door> doors, int cols, int rows,
                                 int ox, int oy, double cell) throws IOException {
        int w = (int) Math.round(cols * cell), h = (int) Math.round(rows * cell);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x2B2119)); // rocha
            g.fillRect(0, 0, w, h);
            // textura leve na rocha: pontinhos deterministicos
            java.util.Random rnd = new java.util.Random(cols * 31L + rows);
            g.setColor(new Color(0x362A20));
            for (int i = 0; i < cols * rows * 3; i++) g.fillRect(rnd.nextInt(w), rnd.nextInt(h), 2, 2);

            Color floorColor = new Color(0xEFE2C6), gridColor = new Color(0xD9C7A3);
            for (long k : floor) {
                double x = (kx(k) + ox) * cell, y = (ky(k) + oy) * cell;
                g.setColor(floorColor);
                g.fillRect((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(cell) + 1, (int) Math.ceil(cell) + 1);
            }
            g.setColor(new Color(0x7FB3D5));
            for (JsonElement el : Json.arr(o, "water")) {
                if (!el.isJsonObject()) continue;
                int x = Json.intOr(el.getAsJsonObject(), "x", 0), y = Json.intOr(el.getAsJsonObject(), "y", 0);
                if (floor.contains(key(x, y))) g.fillRect((int) ((x + ox) * cell), (int) ((y + oy) * cell), (int) cell, (int) cell);
            }
            g.setColor(gridColor);
            g.setStroke(new BasicStroke(1f));
            for (long k : floor) {
                int x = (int) Math.round((kx(k) + ox) * cell), y = (int) Math.round((ky(k) + oy) * cell);
                g.drawRect(x, y, (int) Math.round(cell), (int) Math.round(cell));
            }
            g.setColor(new Color(0x5B4636));
            for (JsonElement el : Json.arr(o, "columns")) {
                if (!el.isJsonObject()) continue;
                double x = Json.num(el.getAsJsonObject(), "x") == null ? 0 : Json.num(el.getAsJsonObject(), "x");
                double y = Json.num(el.getAsJsonObject(), "y") == null ? 0 : Json.num(el.getAsJsonObject(), "y");
                double r = cell * 0.22;
                // colunas ficam nas intersecoes da grade (conferido num JSON exportado de verdade)
                g.fill(new Ellipse2D.Double((x + ox) * cell - r, (y + oy) * cell - r, 2 * r, 2 * r));
            }
            // paredes grossas
            g.setColor(new Color(0x1E1510));
            g.setStroke(new BasicStroke((float) Math.max(3, cell * 0.12), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            for (long k : floor) {
                int x = kx(k), y = ky(k);
                int px = (int) Math.round((x + ox) * cell), py = (int) Math.round((y + oy) * cell), c = (int) Math.round(cell);
                if (!floor.contains(key(x, y - 1))) g.drawLine(px, py, px + c, py);
                if (!floor.contains(key(x, y + 1))) g.drawLine(px, py + c, px + c, py + c);
                if (!floor.contains(key(x - 1, y))) g.drawLine(px, py, px, py + c);
                if (!floor.contains(key(x + 1, y))) g.drawLine(px + c, py, px + c, py + c);
            }
            for (Door d : doors) drawDoor(g, d, ox, oy, cell);
            // numeros das notas
            Font font = new Font(Font.SERIF, Font.BOLD, (int) Math.max(12, cell * 0.42));
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            for (JsonElement el : Json.arr(o, "notes")) {
                if (!el.isJsonObject()) continue;
                JsonObject n = el.getAsJsonObject();
                String ref = Json.str(n, "ref");
                Double nx = Json.num(n, "pos.x"), ny = Json.num(n, "pos.y");
                if (ref == null || nx == null || ny == null) continue;
                double cx = (nx + ox) * cell, cy = (ny + oy) * cell, r = cell * 0.32;
                g.setColor(new Color(0x9E2B25));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g.setColor(new Color(0xFFF4DE));
                g.drawString(ref, (float) (cx - fm.stringWidth(ref) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 2));
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void drawDoor(Graphics2D g, Door d, int ox, int oy, double cell) {
        double x = (d.x + ox) * cell, y = (d.y + oy) * cell;
        boolean horizontalPassage = d.dx != 0;
        double cx = x + cell / 2, cy = y + cell / 2;
        switch (d.type) {
            case DOOR, DOUBLE, FLUSH, BARS, SECRET -> {
                double len = cell * 0.8, thick = cell * 0.22;
                double rx = horizontalPassage ? cx - thick / 2 : cx - len / 2;
                double ry = horizontalPassage ? cy - len / 2 : cy - thick / 2;
                double rw = horizontalPassage ? thick : len, rh = horizontalPassage ? len : thick;
                if (d.type == SECRET) {
                    g.setColor(new Color(0x1E1510));
                    g.setFont(new Font(Font.SERIF, Font.BOLD, (int) Math.max(10, cell * 0.35)));
                    g.drawString("S", (float) (cx - cell * 0.1), (float) (cy + cell * 0.12));
                    return;
                }
                g.setColor(d.type == BARS ? new Color(0x4A4A4A) : new Color(0x8B5A2B));
                g.fillRect((int) rx, (int) ry, (int) Math.ceil(rw), (int) Math.ceil(rh));
                g.setColor(new Color(0x1E1510));
                g.setStroke(new BasicStroke(2f));
                g.drawRect((int) rx, (int) ry, (int) Math.ceil(rw), (int) Math.ceil(rh));
            }
            case STAIRS_DOWN, STAIRS_UP -> {
                g.setColor(new Color(0x5B4636));
                g.setStroke(new BasicStroke(2f));
                for (int i = 1; i <= 4; i++) {
                    double t = i / 5.0;
                    if (horizontalPassage) g.drawLine((int) (x + cell * t), (int) (y + 4), (int) (x + cell * t), (int) (y + cell - 4));
                    else g.drawLine((int) (x + 4), (int) (y + cell * t), (int) (x + cell - 4), (int) (y + cell * t));
                }
            }
            default -> { }
        }
    }

    static boolean blocksAsDoor(int type) {
        return type == DOOR || type == BARS || type == DOUBLE || type == SECRET || type == FLUSH;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int ky(long k) {
        return (int) k;
    }
}
