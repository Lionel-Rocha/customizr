package com.vttcaseiro.app;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * Tema escuro em TODA janela do programa, inclusive as caixas de dialogo prontas do JavaFX (Alert,
 * TextInputDialog, ChoiceDialog, Dialog) e os menus: cada janela que abre ganha o style.css sozinha. Sem isso
 * cada dialogo aparecia com o visual claro padrao do Java.
 */
public final class Theme {
    private static boolean installed;

    private Theme() {
    }

    public static String css() {
        return Theme.class.getResource("/style.css").toExternalForm();
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

    /** Poe o style.css na cena (uma vez so). */
    public static void apply(Scene scene) {
        if (scene == null) return;
        String css = css();
        if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
    }
}
