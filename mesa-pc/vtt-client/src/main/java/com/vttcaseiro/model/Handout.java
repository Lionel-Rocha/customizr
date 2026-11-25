package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
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
    /** Anotacao de jogador compartilhada com o grupo: quem escreveu (o servidor carimba). */
    private String author;
    /** Pra quem vai (nomes dos jogadores); vazio = todos. */
    private List<String> recipients = new ArrayList<>();

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

    public List<String> getRecipients() {
        if (recipients == null) recipients = new ArrayList<>();
        return recipients;
    }

    public void setRecipients(List<String> names) {
        getRecipients().clear();
        if (names == null) return;
        for (String n : names) if (n != null && !n.isBlank() && !recipients.contains(n.strip())) recipients.add(n.strip());
    }

    /** Este jogador recebe? (sem lista: todos) */
    public boolean isFor(String playerName) {
        return getRecipients().isEmpty() || (playerName != null && getRecipients().contains(playerName));
    }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }
}
