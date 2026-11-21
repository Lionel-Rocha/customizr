package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cena da mesa ao vivo: a imagem do mapa, a grade e a neblina. A neblina guarda as casas
 * reveladas como "coluna,linha" (no hexagonal, coordenadas axiais), o resto fica coberto.
 */
@Entity
@Table(name = "scenes")
@Getter
public class Scene {

    public static final int MAX_REVEALED_CELLS = 200_000;

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false)
    private String name;

    @Column(name = "map_url", length = 1000)
    private String mapUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "grid_type", nullable = false)
    private GridType gridType;

    /** Tamanho da casa em pixels da imagem (no hex: largura do hexagono). */
    @Column(name = "grid_size_px", nullable = false)
    private int gridSizePx;

    @Column(name = "feet_per_cell", nullable = false)
    private int feetPerCell;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "fog_enabled", nullable = false)
    private boolean fogEnabled;

    /** Visao dinamica: cada jogador so ve o que os tokens dele enxergam (paredes bloqueiam). Null = desligada. */
    @Column(name = "vision_enabled")
    private Boolean visionEnabled;

    /** Luz da cena (null = clara). */
    @Enumerated(EnumType.STRING)
    @Column(name = "lighting", length = 10)
    private SceneLighting lighting;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Wall> walls = new ArrayList<>();

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AreaTemplate> templates = new ArrayList<>();

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MapPin> pins = new ArrayList<>();

    /** So uma cena ativa por campanha: e a que os jogadores veem. */
    @Column(nullable = false)
    private boolean active;

    @ElementCollection
    @CollectionTable(name = "scene_revealed_cells", joinColumns = @JoinColumn(name = "scene_id"))
    @Column(name = "cell", nullable = false, length = 24)
    private Set<String> revealedCells = new LinkedHashSet<>();

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Token> tokens = new ArrayList<>();

    protected Scene() {}

    public Scene(Campaign campaign, String name, String mapUrl, GridType gridType, Integer gridSizePx, Integer feetPerCell,
                 Integer widthPx, Integer heightPx) {
        if (campaign == null) {
            throw new IllegalArgumentException("Falta a campanha da cena");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        update(name, mapUrl, gridType, gridSizePx, feetPerCell, widthPx, heightPx);
    }

    public void update(String name, String mapUrl, GridType gridType, Integer gridSizePx, Integer feetPerCell,
                       Integer widthPx, Integer heightPx) {
        this.name = Places.requireName(name, "cena");
        this.mapUrl = Places.blankToNull(mapUrl);
        this.gridType = gridType == null ? GridType.SQUARE : gridType;
        int size = gridSizePx == null ? 70 : gridSizePx;
        if (size < 8 || size > 500) throw new IllegalArgumentException("O tamanho da casa vai de 8 a 500 px");
        this.gridSizePx = size;
        int feet = feetPerCell == null ? 5 : feetPerCell;
        if (feet < 1 || feet > 5280) throw new IllegalArgumentException("Pés por casa vai de 1 a 5280");
        this.feetPerCell = feet;
        if ((widthPx != null && widthPx < 1) || (heightPx != null && heightPx < 1)) {
            throw new IllegalArgumentException("O tamanho do mapa precisa ser positivo");
        }
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public void activate(Collection<Scene> campaignScenes) {
        for (Scene other : campaignScenes) other.setActive(false);
        this.active = true;
    }

    public void setFogEnabled(boolean fogEnabled) {
        this.fogEnabled = fogEnabled;
    }

    public SceneLighting getLighting() {
        return lighting == null ? SceneLighting.BRIGHT : lighting;
    }

    public void setLighting(SceneLighting lighting) {
        this.lighting = lighting;
    }

    /**
     * Cobertura entre dois tokens pelas paredes (regra do livro do mestre): linhas de um canto de quem ataca ate
     * os quatro cantos de uma casa do alvo; vale o canto e a casa que derem menos cobertura.
     * 0 = nenhuma, 2 = meia (1-2 linhas bloqueadas), 5 = tres quartos (3), -1 = total (as 4).
     */
    public int coverBetween(Token attacker, Token target) {
        if (walls.stream().noneMatch(Wall::blocks)) return 0;
        int best = 4;
        for (double[] from : corners(attacker.getX(), attacker.getY(), attacker.width() * gridSizePx, attacker.height() * gridSizePx)) {
            for (double[] cell : cells(target)) {
                best = Math.min(best, blockedLines(from[0], from[1], cell));
                if (best == 0) return 0;
            }
        }
        return coverOf(best);
    }

    /** Cobertura de um ponto (origem de uma area) ate o token. */
    public int coverFrom(double x, double y, Token target) {
        if (walls.stream().noneMatch(Wall::blocks)) return 0;
        int best = 4;
        for (double[] cell : cells(target)) best = Math.min(best, blockedLines(x, y, cell));
        return coverOf(best);
    }

    private static int coverOf(int blocked) {
        return blocked == 0 ? 0 : blocked <= 2 ? 2 : blocked == 3 ? 5 : -1;
    }

    private int blockedLines(double fx, double fy, double[] cell) {
        int blocked = 0;
        for (double[] to : corners(cell[0], cell[1], cell[2], cell[3])) {
            if (blockedBetween(fx, fy, to[0], to[1])) blocked++;
        }
        return blocked;
    }

    /** Casas do token (x, y, largura, altura em px); token miudo e uma casa so. */
    private List<double[]> cells(Token t) {
        List<double[]> cells = new ArrayList<>();
        int cols = (int) Math.max(1, Math.round(t.width()));
        int rows = (int) Math.max(1, Math.round(t.height()));
        double cw = t.width() * gridSizePx / cols;
        double ch = t.height() * gridSizePx / rows;
        for (int i = 0; i < cols; i++) for (int j = 0; j < rows; j++) cells.add(new double[] {t.getX() + i * cw, t.getY() + j * ch, cw, ch});
        return cells;
    }

    /** Cantos do retangulo, um pixel pra dentro (pra linha nao "encostar" na ponta de uma parede). */
    private static List<double[]> corners(double x, double y, double w, double h) {
        double e = 1;
        return List.of(new double[] {x + e, y + e}, new double[] {x + w - e, y + e},
                new double[] {x + e, y + h - e}, new double[] {x + w - e, y + h - e});
    }

    public boolean isVisionEnabled() {
        return Boolean.TRUE.equals(visionEnabled);
    }

    public void setVisionEnabled(boolean visionEnabled) {
        this.visionEnabled = visionEnabled;
    }

    public Wall addWall(double x1, double y1, double x2, double y2, Wall.Type type) {
        if (walls.size() >= 5_000) throw new IllegalArgumentException("Paredes demais nesta cena");
        Wall wall = new Wall(this, x1, y1, x2, y2, type);
        walls.add(wall);
        return wall;
    }

    public boolean removeWall(java.util.UUID wallId) {
        return walls.removeIf(w -> w.getId().equals(wallId));
    }

    public AreaTemplate addTemplate(AreaTemplate.Shape shape, double x, double y, double angle, int sizeFt, Integer widthFt,
                                    String label, String color, java.util.UUID ownerId) {
        if (templates.size() >= 200) throw new IllegalArgumentException("Áreas demais nesta cena");
        AreaTemplate template = new AreaTemplate(this, shape, x, y, angle, sizeFt, widthFt, label, color, ownerId);
        templates.add(template);
        return template;
    }

    /** Mesma coisa, ja dizendo se e escuridao magica. */
    public AreaTemplate addTemplate(AreaTemplate.Shape shape, double x, double y, double angle, int sizeFt, Integer widthFt,
                                    String label, String color, java.util.UUID ownerId, boolean darkness) {
        AreaTemplate template = addTemplate(shape, x, y, angle, sizeFt, widthFt, label, color, ownerId);
        template.setDarkness(darkness);
        return template;
    }

    public MapPin addPin(double x, double y, String label, String icon, String note, java.util.UUID entryId, boolean visible) {
        if (pins.size() >= 300) throw new IllegalArgumentException("Alfinetes demais nesta cena");
        MapPin pin = new MapPin(this, x, y, label, icon, note, entryId, visible);
        pins.add(pin);
        return pin;
    }

    public void clearWalls() {
        walls.clear();
    }

    /** O caminho entre dois pontos passa por uma parede ou porta fechada? */
    public boolean blockedBetween(double ax, double ay, double bx, double by) {
        return walls.stream().anyMatch(w -> w.blocks() && w.crosses(ax, ay, bx, by));
    }

    public void revealCells(Collection<int[]> cells, boolean reveal) {
        for (int[] cell : cells) {
            if (cell == null || cell.length != 2) throw new IllegalArgumentException("As casas são pares [coluna, linha]");
            String key = cell[0] + "," + cell[1];
            if (reveal) revealedCells.add(key);
            else revealedCells.remove(key);
        }
        if (revealedCells.size() > MAX_REVEALED_CELLS) {
            throw new IllegalArgumentException("Too many revealed cells; use revealAll instead");
        }
    }

    public void clearFog(boolean revealAll) {
        revealedCells.clear();
        if (revealAll) fogEnabled = false;
    }

    public Token addToken(String name, double x, double y, Integer sizeCells, String imageUrl, boolean hidden,
                          Character character, NPC npc, Monster monster) {
        Token token = new Token(this, name, x, y, sizeCells, imageUrl, hidden, character, npc, monster);
        tokens.add(token);
        return token;
    }

    /** Distancia em pes entre dois pontos da imagem (5e: diagonal conta como 1 casa). */
    public Integer distanceFeet(double x1, double y1, double x2, double y2) {
        double dx = Math.abs(x2 - x1) / gridSizePx;
        double dy = Math.abs(y2 - y1) / gridSizePx;
        return switch (gridType) {
            case SQUARE -> (int) Math.round(Math.max(dx, dy)) * feetPerCell;
            case NONE -> (int) Math.round(Math.hypot(dx, dy) * feetPerCell);
            case HEX -> hexSteps(x1, y1, x2, y2) * feetPerCell;
        };
    }

    /** Passos entre os hexagonos dos dois pontos (pointy-top, largura = gridSizePx). */
    private int hexSteps(double x1, double y1, double x2, double y2) {
        int[] a = hexAt(x1, y1);
        int[] b = hexAt(x2, y2);
        int dq = a[0] - b[0];
        int dr = a[1] - b[1];
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    private int[] hexAt(double x, double y) {
        double size = gridSizePx / Math.sqrt(3);
        double q = (Math.sqrt(3) / 3 * x - 1.0 / 3 * y) / size;
        double r = (2.0 / 3 * y) / size;
        double cx = q, cz = r, cy = -cx - cz;
        long rx = Math.round(cx), ry = Math.round(cy), rz = Math.round(cz);
        double xd = Math.abs(rx - cx), yd = Math.abs(ry - cy), zd = Math.abs(rz - cz);
        if (xd > yd && xd > zd) rx = -ry - rz;
        else if (yd <= zd) rz = -rx - ry;
        return new int[]{(int) rx, (int) rz};
    }
}
