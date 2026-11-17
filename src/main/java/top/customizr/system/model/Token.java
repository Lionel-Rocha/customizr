package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Token no mapa. Pode estar ligado a um personagem (o dono dele move), a um NPC ou a um
 * monstro do catalogo; token oculto so o mestre ve.
 */
@Entity
@Table(name = "tokens")
@Getter
public class Token {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false)
    private String name;

    /** Canto superior esquerdo, em pixels da imagem do mapa. */
    private double x;
    private double y;

    @Column(name = "size_cells", nullable = false)
    private int sizeCells;

    /**
     * Tamanho livre, em casas (meia casa pra Miudo; objetos podem ser retangulos, ex.: mesa 2 x 1).
     * Null = quadrado de sizeCells.
     */
    @Column(name = "width_cells")
    private Double widthCells;

    @Column(name = "height_cells")
    private Double heightCells;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private boolean hidden;

    private int rotation;

    @ManyToOne
    @JoinColumn(name = "character_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Character character;

    @ManyToOne
    @JoinColumn(name = "npc_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private NPC npc;

    @ManyToOne
    @JoinColumn(name = "monster_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Monster monster;

    /** PV proprio (NPC/monstro); token de personagem usa o PV da ficha. */
    @Column(name = "hp_current")
    private Integer hpCurrent;

    @Column(name = "hp_max")
    private Integer hpMax;

    /** Camada no mapa (null = BATTLE, pra tokens de antes das camadas). */
    @Enumerated(EnumType.STRING)
    @Column(name = "layer", length = 10)
    private TokenLayer layer;

    /** Altura em pes (voando, num telhado; negativo = cova, agua). Null = no chao. */
    @Column(name = "elevation_ft")
    private Integer elevationFt;

    /**
     * Fichas que viajam juntas neste token (a caravana do grupo, um barco, uma montaria com cavaleiro).
     * O dono de qualquer uma delas move o token e enxerga por ele.
     */
    @ManyToMany
    @JoinTable(name = "token_members", joinColumns = @JoinColumn(name = "token_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id"))
    @OrderColumn(name = "position")
    private List<Character> members = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "token_markers", joinColumns = @JoinColumn(name = "token_id"))
    @OrderColumn(name = "position")
    private List<TokenMarker> markers = new ArrayList<>();

    /** Alcance da visao em pes (visao no escuro, luz...). Null = enxerga ate onde as paredes deixam. */
    @Column(name = "vision_ft")
    private Integer visionFt;

    /** Condicoes proprias (NPC/monstro); token de personagem usa as condicoes da ficha. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "token_conditions", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "condition_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Condition> conditions = new LinkedHashSet<>();

    protected Token() {}

    Token(Scene scene, String name, double x, double y, Integer sizeCells, String imageUrl, boolean hidden,
          Character character, NPC npc, Monster monster) {
        int links = (character == null ? 0 : 1) + (npc == null ? 0 : 1) + (monster == null ? 0 : 1);
        if (links > 1) {
            throw new IllegalArgumentException("A token links to one character, NPC or monster at most");
        }
        this.id = UUID.randomUUID();
        this.scene = scene;
        this.character = character;
        this.npc = npc;
        this.monster = monster;
        String fallback = character != null ? character.getName() : npc != null ? npc.getName()
                : monster != null ? monster.getName() : null;
        Monster stats = monster != null ? monster : npc != null ? npc.getStatBlock() : null;
        Double creatureSize = sizeCells == null && stats != null ? cellsFor(stats.getSize()) : null;
        update(name == null || name.isBlank() ? fallback : name, sizeCells, imageUrl, hidden, 0);
        if (creatureSize != null) resize(creatureSize, creatureSize); // monstro ja entra no tamanho da ficha
        moveTo(x, y);
        if (stats != null && stats.getHpAverage() != null) {
            this.hpMax = stats.getHpAverage();
            this.hpCurrent = stats.getHpAverage();
        }
    }

    public void update(String name, Integer sizeCells, String imageUrl, boolean hidden, int rotation) {
        this.name = Places.requireName(name, "Token");
        int size = sizeCells == null ? 1 : sizeCells;
        if (size < 1 || size > 10) throw new IllegalArgumentException("Token size must be between 1 and 10 cells");
        if (size != this.sizeCells) { widthCells = null; heightCells = null; }
        this.sizeCells = size;
        this.imageUrl = Places.blankToNull(imageUrl);
        this.hidden = hidden;
        this.rotation = Math.floorMod(rotation, 360);
    }

    public void moveTo(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (x < -10_000 || y < -10_000 || x > 100_000 || y > 100_000) {
            throw new IllegalArgumentException("Position is outside the map");
        }
        this.x = x;
        this.y = y;
    }

    public void setHitPoints(Integer hpCurrent, Integer hpMax) {
        if ((hpCurrent != null && hpCurrent < 0) || (hpMax != null && hpMax < 0)) {
            throw new IllegalArgumentException("Hit points cannot be negative");
        }
        Integer max = hpMax != null ? hpMax : this.hpMax;
        if (hpCurrent != null && max != null && hpCurrent > max) {
            throw new IllegalArgumentException("Current HP cannot exceed max HP");
        }
        if (hpMax != null) this.hpMax = hpMax;
        if (hpCurrent != null) this.hpCurrent = hpCurrent;
    }

    /** Largura em casas (Miudo = 0,5). */
    public double width() {
        return widthCells != null ? widthCells : sizeCells;
    }

    public double height() {
        return heightCells != null ? heightCells : sizeCells;
    }

    /** Muda o tamanho: de 0,5 a 30 casas, de meia em meia (Imenso pode passar de 4 x 4). */
    public void resize(double width, double height) {
        for (double v : new double[] {width, height}) {
            if (Double.isNaN(v) || v < 0.5 || v > 30 || Math.abs(v * 2 - Math.round(v * 2)) > 1e-9) {
                throw new IllegalArgumentException("Token size goes from 0.5 to 30 cells, in steps of 0.5");
            }
        }
        this.widthCells = width;
        this.heightCells = height;
        this.sizeCells = (int) Math.max(1, Math.min(30, Math.ceil(Math.max(width, height))));
    }

    /** Casas pelo tamanho de criatura do livro: Miudo 0,5; Pequeno/Medio 1; Grande 2; Enorme 3; Imenso 4. */
    public static Double cellsFor(String creatureSize) {
        if (creatureSize == null || creatureSize.isBlank()) return null;
        String first = creatureSize.trim().split("[\\s,/]+")[0].toLowerCase(java.util.Locale.ROOT);
        return switch (first) {
            case "tiny", "miudo", "miúdo" -> 0.5;
            case "small", "medium", "pequeno", "medio", "médio" -> 1.0;
            case "large", "grande" -> 2.0;
            case "huge", "enorme" -> 3.0;
            case "gargantuan", "imenso" -> 4.0;
            default -> null;
        };
    }

    public TokenLayer getLayer() {
        return layer == null ? TokenLayer.BATTLE : layer;
    }

    public void setLayer(TokenLayer layer) {
        this.layer = layer == null ? TokenLayer.BATTLE : layer;
    }

    /** Oculto de verdade pros jogadores: marcado como oculto ou na camada do mestre. */
    public boolean isHiddenFromPlayers() {
        return hidden || getLayer() == TokenLayer.GM;
    }

    public void setElevationFt(Integer elevationFt) {
        if (elevationFt != null && (elevationFt < -1000 || elevationFt > 100_000)) {
            throw new IllegalArgumentException("Elevation must be between -1000 and 100000 feet");
        }
        this.elevationFt = elevationFt == null || elevationFt == 0 ? null : elevationFt;
    }

    public int elevation() {
        return elevationFt == null ? 0 : elevationFt;
    }

    public void replaceMembers(List<Character> characters) {
        if (characters.size() > 30) throw new IllegalArgumentException("A token holds at most 30 characters");
        members.clear();
        members.addAll(characters.stream().distinct().toList());
    }

    /** Quem e dono do personagem do token ou de alguma ficha que viaja nele. */
    public boolean controlledBy(UUID userId) {
        if (character != null && character.getOwner().getUserId().equals(userId)) return true;
        return members.stream().anyMatch(c -> c.getOwner().getUserId().equals(userId));
    }

    public void replaceMarkers(List<TokenMarker> newMarkers) {
        if (newMarkers.size() > 20) throw new IllegalArgumentException("At most 20 markers per token");
        markers.clear();
        markers.addAll(newMarkers);
    }

    /** Uma rodada se passou pra este token: desce a contagem dos marcadores e tira os que acabaram. */
    public List<String> tickMarkers() {
        List<String> expired = new ArrayList<>();
        markers.removeIf(m -> {
            boolean keep = m.tick();
            if (!keep) expired.add(m.getLabel());
            return !keep;
        });
        return expired;
    }

    public void setVisionFt(Integer visionFt) {
        if (visionFt != null && (visionFt < 0 || visionFt > 10_000)) {
            throw new IllegalArgumentException("Vision must be between 0 and 10000 feet");
        }
        this.visionFt = visionFt;
    }

    /** Condicoes valendo: as da ficha (personagem) ou as do proprio token. */
    public Set<Condition> activeConditions() {
        if (character != null) return character.getSheet().getConditions();
        return conditions;
    }

    public void updateConditions(Set<Condition> newConditions) {
        if (character != null) {
            character.getSheet().updateConditions(newConditions, null);
            return;
        }
        conditions.clear();
        if (newConditions != null) conditions.addAll(newConditions);
    }

    /** CA do alvo: ficha do personagem, ou ficha do monstro (do token ou do NPC). Null se nao tiver. */
    public Integer armorClass() {
        if (character != null) return character.getSheet().getArmorClass();
        Monster stats = statBlock();
        return stats == null ? null : stats.getArmorClass();
    }

    /** Ficha de monstro por tras do token (monstro direto ou NPC com ficha). */
    public Monster statBlock() {
        return monster != null ? monster : npc != null ? npc.getStatBlock() : null;
    }

    /** Dano no PV do proprio token (NPC/monstro): nao passa de 0. */
    public void damage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Damage must be positive");
        if (hpCurrent == null) throw new IllegalStateException("This token has no hit points");
        hpCurrent = Math.max(0, hpCurrent - amount);
    }

    /** Cura no PV do proprio token: nao passa do maximo. */
    public void heal(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Healing must be positive");
        if (hpCurrent == null) throw new IllegalStateException("This token has no hit points");
        hpCurrent = hpMax == null ? hpCurrent + amount : Math.min(hpMax, hpCurrent + amount);
    }

    /** Modificador de DES pra iniciativa (ficha do personagem, do NPC ou do monstro). */
    public int initiativeBonus() {
        if (character != null) return character.getSheet().getInitiative();
        Monster stats = monster != null ? monster : npc != null ? npc.getStatBlock() : null;
        return stats == null ? 0 : Attributes.modifierOf(stats.getDexterity());
    }
}
