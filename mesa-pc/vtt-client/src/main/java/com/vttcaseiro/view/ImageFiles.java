package com.vttcaseiro.view;

import javafx.stage.FileChooser;

import java.util.List;
import java.util.Locale;

/**
 * Tipos de imagem que a mesa aceita em tudo (mapa, token, objeto, efeito, retrato, galeria): JPG, JPEG, PNG e GIF.
 * GIF animado continua animado (os bytes viajam intactos e a tela usa ImageView).
 */
public final class ImageFiles {

    public static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif");

    private ImageFiles() {
    }

    /** Filtro das janelas de escolher arquivo. */
    public static FileChooser.ExtensionFilter filter() {
        return new FileChooser.ExtensionFilter("Imagens (JPG, JPEG, PNG, GIF)", "*.png", "*.jpg", "*.jpeg", "*.gif",
                "*.PNG", "*.JPG", "*.JPEG", "*.GIF");
    }

    /** O arquivo e de um tipo aceito? (pelo nome; o conteudo a tela confere ao abrir) */
    public static boolean accepts(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
