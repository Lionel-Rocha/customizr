package com.vttcaseiro.view;

import javafx.scene.control.Label;

/** Texto de lista vazia, no mesmo estilo em todo o programa (cinza, pequeno, centralizado). */
public final class EmptyHint {
    private EmptyHint() {
    }

    public static Label of(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-hint");
        l.setWrapText(true);
        l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return l;
    }
}
