package com.vttcaseiro.view;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dados animados por cima do mapa: cada dado da rolagem rola (gira, quica, o
 * numero embaralha) e para no valor que saiu de verdade. Puramente visual --
 * o resultado ja veio do rolador/servidor.
 */
public class DiceAnimator extends HBox {

    /** Um dado: lados e o valor que saiu; {@code kept=false} = descartado (vantagem/desvantagem). */
    public record Die(int sides, int value, boolean kept) {
    }

    private static final Pattern GROUP = Pattern.compile("(\\d*)d(\\d+)\\s*\\[([^\\]]*)\\]");
    private static final int MAX_DICE = 8;

    public DiceAnimator() {
        setSpacing(10);
        setAlignment(Pos.CENTER);
        setMouseTransparent(true);
        setPickOnBounds(false);
    }

    /** "1d20 [14, 9 descartado] + 2" / "3d6[2,5,3] + 1" -> dados. */
    public static List<Die> parse(String detail) {
        List<Die> out = new ArrayList<>();
        if (detail == null) return out;
        Matcher m = GROUP.matcher(detail);
        while (m.find() && out.size() < MAX_DICE) {
            int sides = Integer.parseInt(m.group(2));
            for (String part : m.group(3).split(",")) {
                String p = part.trim();
                Matcher num = Pattern.compile("^(\\d+)").matcher(p);
                if (!num.find()) continue;
                out.add(new Die(sides, Integer.parseInt(num.group(1)), !p.contains("descart")));
                if (out.size() >= MAX_DICE) break;
            }
        }
        return out;
    }

    private static Polygon shapeFor(int sides, double r) {
        int corners = switch (sides) {
            case 4 -> 3;
            case 6 -> 4;
            case 8 -> 4;
            case 10, 12 -> 5;
            case 20 -> 6;
            default -> 8;
        };
        double rot = sides == 8 ? 0 : -Math.PI / 2 + (corners == 4 ? Math.PI / 4 : 0);
        Polygon p = new Polygon();
        for (int i = 0; i < corners; i++) {
            double a = rot + i * 2 * Math.PI / corners;
            p.getPoints().addAll(r * Math.cos(a), r * Math.sin(a));
        }
        return p;
    }

    /** Mostra os dados rolando e somem sozinhos depois de uns 2,5 s. */
    public void show(List<Die> dice, boolean critical, boolean fumble) {
        getChildren().clear();
        if (dice.isEmpty()) return;
        setOpacity(1);
        ParallelTransition all = new ParallelTransition();
        for (int i = 0; i < dice.size(); i++) {
            Die d = dice.get(i);
            Polygon body = shapeFor(d.sides(), 30);
            boolean nat20 = d.sides() == 20 && d.value() == 20 && d.kept();
            boolean nat1 = d.sides() == 20 && d.value() == 1 && d.kept();
            body.setFill(Color.web(!d.kept() ? "#45342A" : nat20 ? "#1F5135" : nat1 ? "#5A1F1C" : "#3A2F1A"));
            body.setStroke(Color.web(!d.kept() ? "#7D6A58" : nat20 ? "#3FBE6E" : nat1 ? "#D9544C" : "#FFCF7F"));
            body.setStrokeWidth(2.5);
            Label number = new Label("?");
            number.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                    + (!d.kept() ? "#B09A82" : "#FFCF7F") + ";");
            Label kind = new Label("d" + d.sides());
            kind.setStyle("-fx-font-size: 9px; -fx-text-fill: #B09A82;");
            kind.setTranslateY(22);
            StackPane die = new StackPane(body, number, kind);
            die.setPrefSize(70, 70);
            getChildren().add(die);

            RotateTransition spin = new RotateTransition(Duration.millis(900), body);
            spin.setByAngle(540 + 180 * ThreadLocalRandom.current().nextInt(3));
            TranslateTransition drop = new TranslateTransition(Duration.millis(900), die);
            drop.setFromY(-60 - i * 8);
            drop.setToY(0);
            drop.setInterpolator(javafx.animation.Interpolator.SPLINE(0.2, 0.8, 0.4, 1.0));
            Timeline shuffle = new Timeline();
            for (int k = 0; k < 9; k++) {
                shuffle.getKeyFrames().add(new KeyFrame(Duration.millis(k * 95),
                        e -> number.setText(String.valueOf(1 + ThreadLocalRandom.current().nextInt(d.sides())))));
            }
            shuffle.getKeyFrames().add(new KeyFrame(Duration.millis(900), e -> number.setText(String.valueOf(d.value()))));
            ParallelTransition one = new ParallelTransition(spin, drop, shuffle);
            if (nat20 || nat1) {
                ScaleTransition pop = new ScaleTransition(Duration.millis(220), die);
                pop.setDelay(Duration.millis(900));
                pop.setToX(1.25);
                pop.setToY(1.25);
                pop.setAutoReverse(true);
                pop.setCycleCount(2);
                one.getChildren().add(pop);
            }
            one.setDelay(Duration.millis(i * 70));
            all.getChildren().add(one);
        }
        FadeTransition out = new FadeTransition(Duration.millis(600), this);
        out.setDelay(Duration.millis(2600));
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(e -> getChildren().clear());
        all.play();
        out.play();
    }
}
