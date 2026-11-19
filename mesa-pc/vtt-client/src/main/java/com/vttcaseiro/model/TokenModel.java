package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um token no mapa. x/y sao o canto superior esquerdo, em pixels da
 * resolucao nativa do mapa (nao muda com o zoom da tela). widthCells e
 * heightCells definem o tamanho em celulas de grade (1 para a maioria das
 * criaturas, 2+ para criaturas grandes).
 * <p>
 * ownerId segue o mesmo modelo que vimos no Firecast: um dono por vez
 * (string vazia ou null = sem dono especifico / so o mestre controla).
 * Fica pronto pra quando a Fase 2 (rede) decidir quem pode mover o que.
 */
public class TokenModel {
    private String id;
    private String name;
    private String imagePath;
    /** Bytes da imagem em Base64 (ver com.vttcaseiro.net.ImageCodec) --
     * isso e o que realmente viaja pela rede, porque imagePath e um
     * caminho local que so existe na maquina de quem criou o token.
     * Null quando o token foi criado offline e ninguem codificou nada
     * ainda (nesse caso o jogo local continua usando imagePath direto). */
    private String imageData;
    /** Id do asset de imagem guardado pelo ImageAssetStore da mesa (hash
     * SHA-256 dos bytes + extensao). Quando este campo esta preenchido,
     * a tela resolve a imagem pelo ImageAssetStore (que guarda os bytes
     * UMA VEZ por conteudo na pasta "assets/images" da mesa) em vez de
     * usar imageData -- evita que 10 tokens com o mesmo retrato gravem 10
     * copias identicas dos mesmos bytes dentro de 10 arquivos .json de
     * token diferentes. imageData continua existindo pra compatibilidade
     * com saves antigos e pro caso em que o asset ainda nao foi gravado
     * localmente (por exemplo, um token recebido pela rede antes do asset
     * correspondente terminar de chegar). */
    private String imageAssetId;
    private double x;
    private double y;
    private int widthCells = 1;
    private int heightCells = 1;
    private Integer currentHp;
    private Integer maxHp;
    private String ownerId;

    /** Resultado da rolagem de iniciativa (1d20 + modificador) para o
     * rastreador de combate (ver Main.buildInitiativeTab). Null enquanto
     * ninguem rolou ainda para este token -- a lista trata null como "vai
     * pro fim da fila", nao como zero. So existe localmente nesta mesa por
     * enquanto -- nao viaja pela rede (ver HANDOFF.md). */
    private Integer initiative;

    /** Nomes (Condition.name(), ex.: "POISONED") das condicoes marcadas
     * neste token agora -- ver Condition e view.TokenNode (os selinhos
     * coloridos desenhados em cima do token). Igual initiative, so existe
     * localmente por enquanto (nao viaja pela rede -- ver HANDOFF.md).
     * Field initializer garante lista vazia (nunca null) tanto pra token
     * novo quanto pra um save antigo sem esse campo (Gson so chama este
     * construtor quando o campo nao existe no JSON). */
    private List<String> conditions = new ArrayList<>();

    /** Id da CharacterSheet que este token representa (null para tokens
     * sem ficha -- a maioria dos monstros, por exemplo). Quando uma ficha
     * e arrastada pro mapa (ver MapView.acceptCharacterDrop), o token
     * criado recebe o id dela aqui, pra tela poder abrir a ficha completa
     * a partir do token depois. */
    private String characterSheetId;

    /** Construtor sem argumentos para o Gson usar ao carregar um save (ele
     * preenche os campos por fora depois de criar o objeto). Nao use direto
     * no resto do codigo -- use o construtor com nome/imagem abaixo. */
    public TokenModel() {
        this.id = UUID.randomUUID().toString();
    }

    public TokenModel(String name, String imagePath) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.imagePath = imagePath;
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getImageData() { return imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }

    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public int getWidthCells() { return widthCells; }
    public void setWidthCells(int widthCells) { this.widthCells = Math.max(1, widthCells); }

    /** Giro do token em graus (0 = como a imagem veio), multiplos de qualquer angulo. */
    private double rotation;

    public double getRotation() { return rotation; }
    public void setRotation(double degrees) {
        double r = Double.isFinite(degrees) ? degrees % 360 : 0;
        this.rotation = r < 0 ? r + 360 : r;
    }

    public int getHeightCells() { return heightCells; }
    public void setHeightCells(int heightCells) { this.heightCells = Math.max(1, heightCells); }

    public Integer getCurrentHp() { return currentHp; }
    public void setCurrentHp(Integer currentHp) { this.currentHp = currentHp; }

    public Integer getMaxHp() { return maxHp; }
    public void setMaxHp(Integer maxHp) { this.maxHp = maxHp; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getCharacterSheetId() { return characterSheetId; }
    public void setCharacterSheetId(String characterSheetId) { this.characterSheetId = characterSheetId; }

    public Integer getInitiative() { return initiative; }
    public void setInitiative(Integer initiative) { this.initiative = initiative; }

    /** Nunca devolve null -- um save antigo sem este campo ainda assim
     * tem a lista vazia do field initializer (ver comentario do campo). */
    /** PV temporario (mostrado como uma faixa azul em cima da barra de vida). */
    private Integer tempHp;

    /** Efeitos livres que nao sao condicoes oficiais: "Abencoado",
     * "Concentrando: Bola de Fogo", "Furia"... */
    private List<String> effects = new ArrayList<>();

    /** Mostrar PV pra todos (true) ou so pro mestre/dono (false, o padrao
     * pra monstro). Hoje e so uma dica visual da propria maquina. */
    private boolean hpVisibleToAll = true;

    /** Bonus somado ao d20 no "Rolar iniciativa pra todos" (mod. de DES
     * do monstro, ou a iniciativa da ficha ligada). */
    private int initiativeBonus;

    /** Ficha de monstro (MobSheet.getId) de onde este token veio -- clique
     * duplo abre ela, com rolagens que usam as condicoes deste token. */
    private String mobSheetId;

    public String getMobSheetId() { return mobSheetId; }
    public void setMobSheetId(String mobSheetId) { this.mobSheetId = mobSheetId; }

    /** Camada do mestre: token escondido dos jogadores. O SERVIDOR nem manda
     * ele pra quem nao e mestre (nao e so visual -- nao da pra "espiar"). */
    private boolean hiddenFromPlayers;

    public boolean isHiddenFromPlayers() { return hiddenFromPlayers; }
    public void setHiddenFromPlayers(boolean hidden) { this.hiddenFromPlayers = hidden; }

    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int initiativeBonus) { this.initiativeBonus = initiativeBonus; }

    public Integer getTempHp() { return tempHp; }
    public void setTempHp(Integer tempHp) { this.tempHp = tempHp; }

    public List<String> getEffects() {
        if (effects == null) effects = new ArrayList<>();
        return effects;
    }
    public void setEffects(List<String> effects) { this.effects = effects == null ? new ArrayList<>() : new ArrayList<>(effects); }

    /** Visao no escuro em pes (0 = nenhuma). So importa com visao dinamica em cena escura. */
    private int visionFeet;
    /** Luz que o token carrega, em pes de raio (tocha ~40, lanterna ~60). */
    private int lightFeet;

    public int getVisionFeet() { return visionFeet; }
    public void setVisionFeet(int feet) { this.visionFeet = Math.max(0, Math.min(1000, feet)); }

    public int getLightFeet() { return lightFeet; }
    public void setLightFeet(int feet) { this.lightFeet = Math.max(0, Math.min(1000, feet)); }

    // ---------------- 5.5e: luz plena + penumbra, sentidos, altura e direcao

    /** Penumbra alem da luz plena ({@code lightFeet} = luz plena). Tocha: 20 plena + 20 penumbra. */
    private int dimLightFeet;
    /** Visao as cegas: percebe sem enxergar (escuridao, invisivel), ate o alcance; parede bloqueia. */
    private int blindsightFeet;
    /** Sentido sismico: sente quem esta no chao (altura 0) ate o alcance, atraves de paredes. */
    private int tremorsenseFeet;
    /** Visao verdadeira: enxerga na escuridao normal e magica ate o alcance. */
    private int truesightFeet;
    /** Altura em pes (voando, escalando; negativo = cavando/abaixo). */
    private int elevationFeet;
    /** Para onde o token olha, em graus (0 = direita, 90 = baixo); null = sem direcao marcada. */
    private Double facingDegrees;

    public int getDimLightFeet() { return dimLightFeet; }
    public void setDimLightFeet(int feet) { this.dimLightFeet = Math.max(0, Math.min(1000, feet)); }
    public int getBlindsightFeet() { return blindsightFeet; }
    public void setBlindsightFeet(int feet) { this.blindsightFeet = Math.max(0, Math.min(1000, feet)); }
    public int getTremorsenseFeet() { return tremorsenseFeet; }
    public void setTremorsenseFeet(int feet) { this.tremorsenseFeet = Math.max(0, Math.min(1000, feet)); }
    public int getTruesightFeet() { return truesightFeet; }
    public void setTruesightFeet(int feet) { this.truesightFeet = Math.max(0, Math.min(1000, feet)); }
    public int getElevationFeet() { return elevationFeet; }
    public void setElevationFeet(int feet) { this.elevationFeet = Math.max(-1000, Math.min(10000, feet)); }
    public Double getFacingDegrees() { return facingDegrees; }
    public void setFacingDegrees(Double degrees) {
        if (degrees == null || !Double.isFinite(degrees)) {
            this.facingDegrees = null;
            return;
        }
        double d = degrees % 360;
        this.facingDegrees = d < 0 ? d + 360 : d;
    }

    /** Alcance total da luz (plena + penumbra). */
    public int lightReachFeet() { return lightFeet + dimLightFeet; }

    public boolean isHpVisibleToAll() { return hpVisibleToAll; }
    public void setHpVisibleToAll(boolean v) { this.hpVisibleToAll = v; }

    /** Copia o "estado de jogo" de outro token (o mesmo, vindo da rede):
     * tudo menos id, posicao e dono. */
    public void copyStateFrom(TokenModel other) {
        if (other == null) return;
        this.name = other.name;
        this.currentHp = other.currentHp;
        this.maxHp = other.maxHp;
        this.tempHp = other.tempHp;
        this.initiative = other.initiative;
        this.initiativeBonus = other.initiativeBonus;
        this.widthCells = Math.max(1, other.widthCells);
        this.heightCells = Math.max(1, other.heightCells);
        this.hpVisibleToAll = other.hpVisibleToAll;
        this.characterSheetId = other.characterSheetId;
        this.mobSheetId = other.mobSheetId;
        this.hiddenFromPlayers = other.hiddenFromPlayers;
        this.visionFeet = other.visionFeet;
        this.lightFeet = other.lightFeet;
        setDimLightFeet(other.dimLightFeet);
        setBlindsightFeet(other.blindsightFeet);
        setTremorsenseFeet(other.tremorsenseFeet);
        setTruesightFeet(other.truesightFeet);
        setElevationFeet(other.elevationFeet);
        setFacingDegrees(other.facingDegrees);
        setRotation(other.rotation);
        // imagem nova (Trocar imagem / retrato da ficha) tambem viaja; id de asset so no formato seguro
        if (other.imageAssetId != null && other.imageAssetId.matches("[0-9a-f]{64}\\.(png|jpg|jpeg|gif|webp|bmp)")) {
            this.imageAssetId = other.imageAssetId;
        }
        setConditions(other.conditions == null ? null : new ArrayList<>(other.conditions));
        setEffects(other.effects);
    }

    /** Condicao oficial presente? (nomes de {@link Condition}). */
    public boolean hasCondition(Condition condition) {
        return conditions != null && conditions.contains(condition.name());
    }

    public List<String> getConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return conditions;
    }
    public void setConditions(List<String> conditions) {
        this.conditions = conditions == null ? new ArrayList<>() : conditions;
    }
}
