package top.customizr.system.model;

/** Validacoes comuns de mundo/pais/cidade/ponto. */
final class Places {

    private Places() {}

    static String requireName(String name, String what) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Falta o nome: " + what);
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Nome muito longo: " + what);
        }
        return name.trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Posicao no mapa, relativa (0 = borda esquerda/topo, 1 = direita/baixo). */
    static Float coordinate(Float value, String what) {
        if (value != null && (value.isNaN() || value < 0f || value > 1f)) {
            throw new IllegalArgumentException(what + " precisa ficar entre 0 e 1 (relativo ao mapa)");
        }
        return value;
    }
}
