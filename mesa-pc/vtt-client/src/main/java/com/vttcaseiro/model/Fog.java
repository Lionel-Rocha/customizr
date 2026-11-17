package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Neblina de guerra por casa da grade: ligada, tudo que NAO foi revelado
 * fica preto pros jogadores (o mestre ve por baixo, meio transparente).
 * Guarda so as casas reveladas ("coluna,linha") -- vive dentro da cena
 * (salva com ela) e viaja em "fog_sync".
 */
public class Fog {
    private boolean enabled;
    private List<String> revealed = new ArrayList<>();
    /** Cache de consulta (nao vai pro JSON). */
    private transient Set<String> index;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isRevealed(int col, int row) {
        return index().contains(key(col, row));
    }

    public int revealedCount() {
        return index().size();
    }

    /** Revela (ou cobre) o retangulo de casas, cantos inclusive, em qualquer ordem. */
    public void setArea(int c0, int r0, int c1, int r1, boolean reveal) {
        Set<String> set = index();
        for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) {
            for (int r = Math.min(r0, r1); r <= Math.max(r0, r1); r++) {
                if (c < 0 || r < 0) continue;
                if (reveal) set.add(key(c, r)); else set.remove(key(c, r));
            }
        }
        revealed = new ArrayList<>(set);
    }

    /** Revela (ou cobre) estas casas. Na grade hexagonal, cada casa e um hexagono (q, r). */
    public void setCells(java.util.Collection<int[]> cells, boolean reveal) {
        Set<String> set = index();
        for (int[] c : cells) {
            if (c == null || c.length < 2) continue;
            if (reveal) set.add(key(c[0], c[1])); else set.remove(key(c[0], c[1]));
        }
        revealed = new ArrayList<>(set);
    }

    public void coverAll() {
        index().clear();
        revealed = new ArrayList<>();
    }

    public void revealAll(int cols, int rows) {
        setArea(0, 0, Math.max(0, cols - 1), Math.max(0, rows - 1), true);
    }

    private Set<String> index() {
        if (index == null) {
            index = new LinkedHashSet<>(revealed == null ? List.of() : revealed);
        }
        return index;
    }

    private static String key(int col, int row) {
        return col + "," + row;
    }
}
