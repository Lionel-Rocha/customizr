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

    /** "30 ft (9 m)". */
    public static String feetAndMeters(double feet) {
        long f = Math.round(feet);
        return f + " ft (" + meters(f) + ")";
    }
}
