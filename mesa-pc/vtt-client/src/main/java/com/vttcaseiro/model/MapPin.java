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
            "perigo", "tesouro", "info"};

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

    /** Limpa o que veio pela rede (tamanhos, tipo, link so https). */
    public void sanitize() {
        setTitle(title);
        setText(text);
        if (getKind().equals("info") && !"info".equals(kind)) kind = "info";
        if (generatorUrl != null && !generatorUrl.startsWith("https://")) generatorUrl = null;
        if (!Double.isFinite(x)) x = 0;
        if (!Double.isFinite(y)) y = 0;
    }
}
