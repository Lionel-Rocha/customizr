package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Uma entrada do diario: anotacao do mestre (texto + imagem opcional) que
 * ele revela aos jogadores na hora certa -- a carta do vilao, o mapa do
 * tesouro, o retrato do NPC. So o mestre cria e edita; os jogadores so
 * recebem as reveladas.
 */
public class Handout {
    public static final int MAX_TEXT = 20000;

    private String id = UUID.randomUUID().toString();
    private String title = "";
    private String text = "";
    private String imageAssetId;
    private boolean revealed;

    public Handout() {
    }

    public Handout(String title, String text) {
        setTitle(title);
        setText(text);
    }

    public String getId() { return id; }

    public String getTitle() { return title == null || title.isBlank() ? "(sem titulo)" : title; }
    public void setTitle(String title) { this.title = title == null ? "" : title.strip(); }

    public String getText() { return text == null ? "" : text; }

    public void setText(String text) {
        String t = text == null ? "" : text;
        this.text = t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }

    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }
}
