package com.vttcaseiro.view;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;

/**
 * Avatar generico (cabeca + ombros, tipo o icone padrao de qualquer app
 * quando ninguem definiu uma foto) desenhado com formas basicas do
 * JavaFX -- nenhum arquivo de imagem envolvido. Usado como retrato
 * padrao de ficha/token quando o jogador nao escolheu uma imagem propria.
 * <p>
 * Isso existe porque eu nao tenho como baixar imagem nenhuma de lugar
 * nenhum neste ambiente (sem acesso a rede pra isso) -- entao, em vez de
 * inventar que resolvi isso, desenhei uma forma simples e generica, sem
 * nenhum risco de direito autoral (e so um circulo e uma elipse).
 */
public final class SilhouetteIcon {

    private SilhouetteIcon() {
    }

    public static Node create(double size) {
        return create(size, "#45342A", "#B09A82");
    }

    public static Node create(double size, String backgroundHex, String figureHex) {
        StackPane plate = new StackPane();
        plate.setPrefSize(size, size);
        plate.setMinSize(size, size);
        plate.setMaxSize(size, size);
        plate.setStyle("-fx-background-color: " + backgroundHex + "; -fx-background-radius: "
                + (size * 0.14) + ";");

        Circle head = new Circle(size * 0.16);
        head.setFill(Color.web(figureHex));
        head.setTranslateY(-size * 0.12);

        Ellipse shoulders = new Ellipse(size * 0.30, size * 0.20);
        shoulders.setFill(Color.web(figureHex));
        shoulders.setTranslateY(size * 0.27);

        Group silhouette = new Group(shoulders, head);

        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size * 0.28);
        clip.setArcHeight(size * 0.28);
        plate.setClip(clip);

        plate.getChildren().add(silhouette);
        return plate;
    }
}
