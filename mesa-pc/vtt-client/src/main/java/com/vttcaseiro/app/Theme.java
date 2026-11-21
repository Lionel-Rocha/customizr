package com.vttcaseiro.app;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tema de TODA janela do programa, inclusive as caixas de dialogo prontas do JavaFX e os menus: cada janela que
 * abre ganha o style.css e o tema escolhido sozinha. Trocar o tema repinta na hora as janelas abertas.
 * <p>
 * O style.css guarda as cores como variaveis (-c-bg, -c-accent...). O tema padrao ("Fogueira") e o proprio
 * style.css; os outros carregam theme-derived.css (tons intermediarios calculados) e o theme-&lt;nome&gt;.css (as
 * cores base daquele tema).
 */
public final class Theme {
    public static final String DEFAULT = "fogueira";
    /** chave -> nome mostrado, na ordem do seletor (a chave fica igual: e o que esta salvo nas preferencias). */
    public static final Map<String, String> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("fogueira", "Fogueira (padrão)");
        THEMES.put("claro", "Claro");
        THEMES.put("escuro", "Escuro");
        THEMES.put("gotico", "Ordem Paranormal");
        THEMES.put("scifi", "Starfinder");
        THEMES.put("cyberpunk", "Cyberpunk RED");
        THEMES.put("apocalipse", "Apocalypse World");
    }

    private static boolean installed;
    private static String current;

    private Theme() {
    }

    public static String css() {
        return url("/style.css");
    }

    private static String url(String resource) {
        return Theme.class.getResource(resource).toExternalForm();
    }

    /** Tema escolhido (guardado nas preferencias desta maquina). */
    public static synchronized String current() {
        if (current == null) {
            String saved = AppSettings.getString("tema", DEFAULT);
            current = THEMES.containsKey(saved) ? saved : DEFAULT;
        }
        return current;
    }

    /** Troca o tema, guarda a escolha e repinta todas as janelas abertas. */
    public static synchronized void set(String key) {
        if (!THEMES.containsKey(key)) key = DEFAULT;
        current = key;
        AppSettings.setString("tema", key);
        for (Window w : Window.getWindows()) apply(w.getScene());
    }

    /** Liga o tema pra todas as janelas, as que ja existem e as que abrirem depois. Chamar uma vez. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        for (Window w : Window.getWindows()) watch(w);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) change.getAddedSubList().forEach(Theme::watch);
            }
        });
    }

    private static void watch(Window w) {
        apply(w.getScene());
        w.sceneProperty().addListener((o, old, scene) -> apply(scene));
    }

    private static final Object GUARD = new Object();

    /**
     * Poe o style.css (uma vez so, primeiro) e as folhas do tema atual (por ultimo) na cena. Se alguma tela
     * acrescentar o style.css depois, o tema volta pro fim sozinho (senao as cores padrao venceriam).
     */
    public static void apply(Scene scene) {
        if (scene == null) return;
        if (!scene.getProperties().containsKey(GUARD)) {
            scene.getProperties().put(GUARD, Boolean.TRUE);
            scene.getStylesheets().addListener((ListChangeListener<String>) c -> {
                if (Boolean.TRUE.equals(scene.getProperties().get("theme.applying"))) return;
                javafx.application.Platform.runLater(() -> reorder(scene));
            });
        }
        reorder(scene);
    }

    private static void reorder(Scene scene) {
        var sheets = scene.getStylesheets();
        String base = css();
        java.util.List<String> want = new java.util.ArrayList<>();
        want.add(base);
        for (String s : sheets) if (!s.equals(base) && !s.contains("/theme-") && !want.contains(s)) want.add(s);
        String key = current();
        if (!DEFAULT.equals(key)) {
            want.add(url("/theme-derived.css"));
            want.add(url("/theme-" + key + ".css"));
        }
        if (want.equals(sheets)) return;
        scene.getProperties().put("theme.applying", Boolean.TRUE);
        try {
            sheets.setAll(want);
        } finally {
            scene.getProperties().remove("theme.applying");
        }
    }

    /** Caixinha de escolher o tema (troca na hora). */
    public static ComboBox<String> picker() {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(THEMES.values());
        box.setValue(THEMES.get(current()));
        box.setOnAction(e -> THEMES.forEach((k, label) -> {
            if (label.equals(box.getValue())) set(k);
        }));
        return box;
    }
}
