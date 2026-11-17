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
            throw new IllegalArgumentException("Scene campaign is required");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        update(name, mapUrl, gridType, gridSizePx, feetPerCell, widthPx, heightPx);
    }

    public void update(String name, String mapUrl, GridType gridType, Integer gridSizePx, Integer feetPerCell,
                       Integer widthPx, Integer heightPx) {
        this.name = Places.requireName(name, "Scene");
        this.mapUrl = Places.blankToNull(mapUrl);
        this.gridType = gridType == null ? GridType.SQUARE : gridType;
        int size = gridSizePx == null ? 70 : gridSizePx;
        if (size < 8 || size > 500) throw new IllegalArgumentException("Grid size must be between 8 and 500 px");
        this.gridSizePx = size;
        int feet = feetPerCell == null ? 5 : feetPerCell;
        if (feet < 1 || feet > 5280) throw new IllegalArgumentException("Feet per cell must be between 1 and 5280");
        this.feetPerCell = feet;
        if ((widthPx != null && widthPx < 1) || (heightPx != null && heightPx < 1)) {
            throw new IllegalArgumentException("Map size must be positive");
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

    public void revealCells(Collection<int[]> cells, boolean reveal) {
        for (int[] cell : cells) {
            if (cell == null || cell.length != 2) throw new IllegalArgumentException("Cells are [column, row] pairs");
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
