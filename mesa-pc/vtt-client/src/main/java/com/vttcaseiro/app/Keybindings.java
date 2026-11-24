package com.vttcaseiro.app;

import javafx.scene.input.KeyCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atalhos do mapa que cada um pode trocar (Configuracoes > Atalhos do teclado). Fica nas preferencias desta maquina
 * ("atalho.&lt;acao&gt;" = nome da tecla). Esc, setas e Delete continuam fixos.
 */
public final class Keybindings {
    /** acao -> nome mostrado, na ordem da janela. */
    public static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    private static final Map<String, KeyCode> DEFAULTS = new LinkedHashMap<>();

    static {
        add("tool.1", "Medir", KeyCode.DIGIT1);
        add("tool.2", "Área de efeito", KeyCode.DIGIT2);
        add("tool.3", "Lápis", KeyCode.DIGIT3);
        add("tool.4", "Texto no mapa", KeyCode.DIGIT4);
        add("tool.5", "Borracha", KeyCode.DIGIT5);
        add("tool.6", "Revelar neblina (mestre)", KeyCode.DIGIT6);
        add("tool.7", "Cobrir neblina (mestre)", KeyCode.DIGIT7);
        add("tool.8", "Parede (mestre)", KeyCode.DIGIT8);
        add("tool.9", "Porta (mestre)", KeyCode.DIGIT9);
        add("tools.off", "Desligar ferramenta (além do Esc)", KeyCode.DIGIT0);
        add("target", "Mirar nos escolhidos", KeyCode.T);
        add("rotate", "Girar 45° (Shift: volta)", KeyCode.R);
        add("hide", "Esconder / revelar (mestre)", KeyCode.H);
        add("help", "Ajuda dos atalhos", KeyCode.F1);
    }

    private static void add(String id, String label, KeyCode key) {
        ACTIONS.put(id, label);
        DEFAULTS.put(id, key);
    }

    private Keybindings() {
    }

    public static KeyCode defaultKey(String action) {
        return DEFAULTS.get(action);
    }

    /** Tecla da acao agora (null = sem tecla). */
    public static KeyCode keyFor(String action) {
        String saved = AppSettings.getString("atalho." + action, null);
        if (saved == null) return DEFAULTS.get(action);
        if (saved.isEmpty()) return null;
        try {
            return KeyCode.valueOf(saved);
        } catch (IllegalArgumentException ex) {
            return DEFAULTS.get(action);
        }
    }

    /** Acao desta tecla (o teclado numerico vale como os numeros de cima), ou null. */
    public static String actionFor(KeyCode code) {
        KeyCode k = normalize(code);
        for (String a : ACTIONS.keySet()) {
            if (k != null && k == normalize(keyFor(a))) return a;
        }
        return null;
    }

    /** Troca a tecla; quem usava a mesma tecla fica sem (devolve qual acao perdeu, ou null). */
    public static String set(String action, KeyCode key) {
        String lost = null;
        if (key != null) {
            for (String a : ACTIONS.keySet()) {
                if (!a.equals(action) && normalize(keyFor(a)) == normalize(key)) {
                    AppSettings.setString("atalho." + a, "");
                    lost = a;
                }
            }
        }
        AppSettings.setString("atalho." + action, key == null ? "" : key.name());
        return lost;
    }

    public static void resetAll() {
        for (String a : ACTIONS.keySet()) AppSettings.setString("atalho." + a, DEFAULTS.get(a).name());
    }

    /** Teclas que nao podem ser usadas (sao fixas). */
    public static boolean reserved(KeyCode k) {
        return k == KeyCode.ESCAPE || k == KeyCode.UP || k == KeyCode.DOWN || k == KeyCode.LEFT || k == KeyCode.RIGHT
                || k == KeyCode.DELETE || k == KeyCode.BACK_SPACE || k == KeyCode.SHIFT || k == KeyCode.CONTROL
                || k == KeyCode.ALT || k == KeyCode.ALT_GRAPH || k == KeyCode.WINDOWS || k == KeyCode.META
                || k == KeyCode.TAB || k == KeyCode.ENTER;
    }

    /** "1", "T", "F1", "—" (sem tecla). */
    public static String label(KeyCode k) {
        if (k == null) return "—";
        String n = k.getName();
        return n.startsWith("Numpad ") ? n.substring(7) : n;
    }

    private static KeyCode normalize(KeyCode k) {
        if (k == null) return null;
        return switch (k) {
            case NUMPAD0 -> KeyCode.DIGIT0;
            case NUMPAD1 -> KeyCode.DIGIT1;
            case NUMPAD2 -> KeyCode.DIGIT2;
            case NUMPAD3 -> KeyCode.DIGIT3;
            case NUMPAD4 -> KeyCode.DIGIT4;
            case NUMPAD5 -> KeyCode.DIGIT5;
            case NUMPAD6 -> KeyCode.DIGIT6;
            case NUMPAD7 -> KeyCode.DIGIT7;
            case NUMPAD8 -> KeyCode.DIGIT8;
            case NUMPAD9 -> KeyCode.DIGIT9;
            default -> k;
        };
    }
}
