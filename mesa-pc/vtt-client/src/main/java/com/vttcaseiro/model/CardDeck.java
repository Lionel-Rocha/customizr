package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Baralho de cartas do mestre: cartas com nome, texto e (opcional) imagem. Compra do monte, a carta comprada vai
 * pro descarte; "embaralhar" junta tudo de volta.
 */
public class CardDeck {
    public static final int MAX_CARDS = 500;

    /** Uma carta. */
    public static class Card {
        private String id = UUID.randomUUID().toString();
        private String name = "";
        private String text = "";
        private String imageAssetId;

        public Card() {
        }

        public Card(String name, String text, String imageAssetId) {
            this.name = name == null ? "" : name.strip();
            this.text = text == null ? "" : text.strip();
            this.imageAssetId = imageAssetId;
        }

        public String getId() { return id; }
        public String getName() { return name == null ? "" : name; }
        public String getText() { return text == null ? "" : text; }
        public String getImageAssetId() { return imageAssetId; }
    }

    private String id = UUID.randomUUID().toString();
    private String name = "Baralho";
    private List<Card> cards = new ArrayList<>();
    /** Ids na ordem do monte (o primeiro e o de cima). */
    private List<String> pile = new ArrayList<>();

    public CardDeck() {
    }

    public CardDeck(String name, List<Card> cards) {
        setName(name);
        this.cards = new ArrayList<>(cards.subList(0, Math.min(cards.size(), MAX_CARDS)));
        shuffle(new Random());
    }

    public String getId() { return id; }
    public String getName() { return name == null ? "Baralho" : name; }
    public void setName(String name) { this.name = name == null || name.isBlank() ? "Baralho" : name.strip(); }

    public List<Card> getCards() {
        if (cards == null) cards = new ArrayList<>();
        return cards;
    }

    public int remaining() {
        return pile == null ? 0 : pile.size();
    }

    /** Junta tudo (descarte e cartas dadas) no monte e embaralha. */
    public void shuffle(Random rnd) {
        pile = new ArrayList<>();
        for (Card c : getCards()) pile.add(c.getId());
        Collections.shuffle(pile, rnd);
    }

    /** Compra a de cima (null = monte vazio). */
    public Card draw() {
        if (pile == null) pile = new ArrayList<>();
        while (!pile.isEmpty()) {
            String id = pile.remove(0);
            for (Card c : getCards()) if (c.getId().equals(id)) return c;
        }
        return null;
    }

    /** Baralho comum: 52 cartas e 2 coringas. */
    public static CardDeck standard() {
        String[] ranks = {"Ás", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Valete", "Dama", "Rei"};
        String[][] suits = {{"Espadas", "♠"}, {"Copas", "♥"}, {"Ouros", "♦"}, {"Paus", "♣"}};
        List<Card> out = new ArrayList<>();
        for (String[] s : suits) for (String r : ranks) out.add(new Card(r + " de " + s[0] + " " + s[1], "", null));
        out.add(new Card("Coringa", "", null));
        out.add(new Card("Coringa", "", null));
        return new CardDeck("Baralho comum", out);
    }

    /** Tarô: os 22 arcanos maiores. */
    public static CardDeck tarot() {
        String[] names = {"O Louco", "O Mago", "A Sacerdotisa", "A Imperatriz", "O Imperador", "O Hierofante",
                "Os Enamorados", "O Carro", "A Força", "O Eremita", "A Roda da Fortuna", "A Justiça", "O Enforcado",
                "A Morte", "A Temperança", "O Diabo", "A Torre", "A Estrela", "A Lua", "O Sol", "O Julgamento", "O Mundo"};
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < names.length; i++) out.add(new Card(roman(i) + " - " + names[i], "", null));
        return new CardDeck("Tarô (arcanos maiores)", out);
    }

    /** Uma carta por linha ("Nome: texto" ou so o nome). */
    public static CardDeck fromLines(String name, String lines) {
        List<Card> out = new ArrayList<>();
        for (String line : lines.split("\\R")) {
            String l = line.strip();
            if (l.isEmpty()) continue;
            int colon = l.indexOf(':');
            out.add(colon > 0 ? new Card(l.substring(0, colon), l.substring(colon + 1), null) : new Card(l, "", null));
        }
        return new CardDeck(name, out);
    }

    static String roman(int n) {
        if (n == 0) return "0";
        int[] v = {10, 9, 5, 4, 1};
        String[] s = {"X", "IX", "V", "IV", "I"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) {
            b.append(s[i]);
            n -= v[i];
        }
        return b.toString();
    }
}
