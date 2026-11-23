package com.vttcaseiro.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nomes sortidos pros monstros na mesa: em vez de "Goblin 2, Goblin 3", "Goblin Feroz", "Goblin de Olho Vermelho".
 * Ajuda o mestre a narrar ("o de olho vermelho foge") e os jogadores a escolher alvo. Os apelidos servem pra
 * qualquer genero (nunca sai "Aranha Caolho").
 */
public final class FlavorNames {
    static final List<String> EPITHETS = List.of(
            "Feroz", "Veloz", "Voraz", "Audaz", "Ágil", "Grande", "Jovem", "Ancião", "Imponente", "Nervoso",
            "Sorrateiro", "Faminto", "Ruidoso", "Silencioso", "Teimoso", "Covarde", "Sanguinário", "Paciente",
            "de Olho Vermelho", "de Chifre Quebrado", "com Cicatriz", "sem Dentes", "de Uma Orelha", "Manco",
            "de Pelo Cinza", "de Pele Escura", "de Garras Longas", "de Dentes Tortos", "com Tatuagem",
            "de Capuz", "com Elmo Amassado", "com Brinco de Osso", "de Olhar Vazio", "que Coça", "que Rosna",
            "que Fareja", "de Cauda Partida", "Malhado", "Pálido", "Sujo", "Barulhento", "Desconfiado");

    private FlavorNames() {
    }

    /**
     * Um nome livre pra {@code base} que ainda nao esta em {@code taken} (nomes dos tokens da cena). Se acabarem
     * os apelidos, cai no numero ("Goblin 43").
     */
    public static String pick(String base, Collection<String> taken, Random random) {
        String b = base == null || base.isBlank() ? "Monstro" : base.trim();
        Set<String> used = taken.stream().filter(java.util.Objects::nonNull)
                .map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> options = new ArrayList<>();
        for (String e : EPITHETS) {
            String name = b + " " + e;
            if (!used.contains(name.toLowerCase(Locale.ROOT))) options.add(name);
        }
        if (!options.isEmpty()) return options.get(random.nextInt(options.size()));
        int n = 2;
        while (used.contains((b + " " + n).toLowerCase(Locale.ROOT))) n++;
        return b + " " + n;
    }
}
