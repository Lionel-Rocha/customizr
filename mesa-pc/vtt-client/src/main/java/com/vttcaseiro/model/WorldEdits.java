package com.vttcaseiro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O que o mestre mudou num mundo do Azgaar guardado na mesa (o arquivo original fica intacto): nomes e cores dos
 * reinos, nomes e populacao das cidades. Chave = numero do reino/povoado no Azgaar. Campo null = sem mudanca.
 */
public class WorldEdits {
    public Map<Integer, StateEdit> states = new LinkedHashMap<>();
    public Map<Integer, BurgEdit> burgs = new LinkedHashMap<>();

    public static class StateEdit {
        public String name;
        public String fullName;
        /** "#rrggbb" */
        public String color;
    }

    public static class BurgEdit {
        public String name;
        public Double population;
    }

    public Map<Integer, StateEdit> states() {
        if (states == null) states = new LinkedHashMap<>();
        return states;
    }

    public Map<Integer, BurgEdit> burgs() {
        if (burgs == null) burgs = new LinkedHashMap<>();
        return burgs;
    }

    public boolean isEmpty() {
        return states().isEmpty() && burgs().isEmpty();
    }
}
