package com.vttcaseiro.view;

/**
 * Distancias mostradas em pes e metros juntos, como nos livros em portugues: 5 pes = 1,5 m
 * ("30 ft (9 m)", "15 ft (4,5 m)").
 */
public final class Distances {

    private Distances() {
    }

    /** Metros pela conversao dos livros (5 pes = 1,5 m), sem casas a toa: 9, 4,5, 1,5. */
    public static String meters(double feet) {
        double m = Math.round(feet * 0.3 * 10) / 10.0;
        String text = m == Math.rint(m) ? String.valueOf((long) m) : String.valueOf(m).replace('.', ',');
        return text + " m";
    }

    /** A partir de quantos pes por casa a cena e um mapa de regiao/mundo (a regua mede viagem, nao combate). */
    public static final double WORLD_FEET_PER_CELL = 500;

    public static boolean isWorldScale(double feetPerCell) {
        return feetPerCell >= WORLD_FEET_PER_CELL;
    }

    /**
     * Distancia de viagem: "120 km (75 mi) · a pé: 3 dias e 3 h · rápido: 2 dias e 4 h" (8 h de marcha por dia,
     * ritmos do livro). Abaixo de 1 km mostra metros.
     */
    public static String travel(double feet) {
        double km = feet * 0.3048 / 1000;
        double mi = feet / 5280;
        String dist = km < 1 ? Math.round(km * 1000) + " m"
                : km < 10 ? oneDecimal(km) + " km (" + oneDecimal(mi) + " mi)"
                : Math.round(km) + " km (" + Math.round(mi) + " mi)";
        if (km < 0.5) return dist;
        return dist + " · a pé: " + com.vttcaseiro.model.Campaign.travelText(com.vttcaseiro.model.Campaign.travelHours(km, 1, false))
                + " · rápido: " + com.vttcaseiro.model.Campaign.travelText(com.vttcaseiro.model.Campaign.travelHours(km, 2, false));
    }

    private static String oneDecimal(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r).replace('.', ',');
    }

    /** "30 ft (9 m)". */
    public static String feetAndMeters(double feet) {
        long f = Math.round(feet);
        return f + " ft (" + meters(f) + ")";
    }
}
