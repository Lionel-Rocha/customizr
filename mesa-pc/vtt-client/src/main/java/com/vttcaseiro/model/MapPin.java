package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Pin (ponto de informacao) num mapa: "Cidade de Alta Torre", "Loja do ferreiro", "Covil do dragao".
 * Clicar abre um cartao com o texto; se tiver uma cena ligada, o mestre abre aquela cena dali
 * (mapa do pais -> pin da cidade -> mapa da cidade -> pin da loja...). Pin oculto so o mestre ve.
 */
public class MapPin {
    public static final int MAX_TITLE = 80;
    public static final int MAX_TEXT = 4000;

    /** Tipos de pin (o icone desenhado). */
    public static final String[] KINDS = {"cidade", "capital", "vila", "loja", "taverna", "templo", "masmorra",
            "perigo", "tesouro", "info", "rotulo"};
    /** Rotulo: so o texto escrito no mapa (nome de reino, cidade, rio...), do tamanho do mapa (cresce com o zoom). */
    public static final String LABEL = "rotulo";
    /** Estilo do rotulo: titulo (serifa italica, pra reinos e regioes) ou lugar (letra simples). */
    public static final String STYLE_TITLE = "titulo";
    public static final String STYLE_PLACE = "lugar";

    private String id = UUID.randomUUID().toString();
    private double x;
    private double y;
    private String title = "";
    private String text = "";
    private String kind = "info";
    /** Nome da cena da mesa que este pin abre (null = so informacao). */
    private String linkedScene;
    /** So o mestre ve (o servidor nem manda pros jogadores). */
    private boolean hidden;
    /** Link pra gerar o mapa deste lugar num gerador (ex.: cidade do Watabou). Opcional. */
    private String generatorUrl;
    /** Rotulo: altura da letra em pixels do mapa (0 = padrao). */
    private double labelSize;
    private String labelStyle;
    /** De onde veio (ex.: "azgaar:burg:12"), pra o editor do mundo achar o pin quando renomeia. */
    private String ref;

    public MapPin() {
    }

    public MapPin(double x, double y, String title, String kind) {
        this.x = x;
        this.y = y;
        setTitle(title);
        setKind(kind);
    }

    public String getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String title) {
        String t = title == null ? "" : title.strip();
        this.title = t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }

    public String getText() { return text == null ? "" : text; }
    public void setText(String text) {
        String t = text == null ? "" : text;
        this.text = t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    public String getKind() {
        for (String k : KINDS) if (k.equals(kind)) return k;
        return "info";
    }
    public void setKind(String kind) { this.kind = kind; }

    public String getLinkedScene() { return linkedScene == null || linkedScene.isBlank() ? null : linkedScene; }
    public void setLinkedScene(String linkedScene) { this.linkedScene = linkedScene; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getGeneratorUrl() {
        return generatorUrl != null && generatorUrl.startsWith("https://") ? generatorUrl : null;
    }
    public void setGeneratorUrl(String generatorUrl) { this.generatorUrl = generatorUrl; }

    public boolean isLabel() { return LABEL.equals(getKind()); }

    public double getLabelSize() { return labelSize > 0 ? Math.max(6, Math.min(400, labelSize)) : 24; }
    public void setLabelSize(double labelSize) { this.labelSize = labelSize; }

    public String getLabelStyle() { return STYLE_TITLE.equals(labelStyle) ? STYLE_TITLE : STYLE_PLACE; }
    public void setLabelStyle(String labelStyle) { this.labelStyle = labelStyle; }

    public String getRef() { return ref == null || ref.isBlank() ? null : ref; }
    public void setRef(String ref) { this.ref = ref == null ? null : ref.length() > 60 ? ref.substring(0, 60) : ref; }

    /** Limpa o que veio pela rede (tamanhos, tipo, link so https). */
    public void sanitize() {
        setTitle(title);
        setText(text);
        if (getKind().equals("info") && !"info".equals(kind)) kind = "info";
        if (generatorUrl != null && !generatorUrl.startsWith("https://")) generatorUrl = null;
        if (!Double.isFinite(x)) x = 0;
        if (!Double.isFinite(y)) y = 0;
        if (!Double.isFinite(labelSize)) labelSize = 0;
        setRef(ref);
    }
}
