package com.vttcaseiro.view;

import com.vttcaseiro.model.Condition;
import com.vttcaseiro.model.TokenModel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Janela pra marcar/desmarcar condicoes (status effects) num token -- ver
 * {@link Condition} (lista fixa de condicoes do 5e) e {@link TokenNode}
 * (onde os selinhos coloridos aparecem depois, em cima do token). Aberta
 * com o botao direito num token (ver MapView.editConditions).
 * <p>
 * So existe localmente nesta janela por enquanto -- nao sincroniza pela
 * rede (mesma limitacao que o PV editado por clique duplo no token ja
 * tinha; ver HANDOFF.md).
 */
public class ConditionsPickerView {

    private final TokenModel token;
    private final Consumer<List<String>> onApplied;

    /**
     * @param onApplied chamado com a nova lista de Condition.name() quando
     *   o usuario clica "Aplicar" -- quem chama decide o que fazer com
     *   isso (setar no TokenModel e reconstruir a tela, ver MapView).
     */
    public ConditionsPickerView(TokenModel token, Consumer<List<String>> onApplied) {
        this.token = token;
        this.onApplied = onApplied;
    }

    public void show() {
        Stage stage = new Stage();
        Icons.applyTo(stage);
        stage.setTitle("Condições de " + token.getName());

        VBox root = new VBox(6);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: -c-bg;");

        Label header = new Label("Marque as condições ativas em " + token.getName() + ":");
        header.getStyleClass().add("label-muted");
        header.setWrapText(true);
        root.getChildren().add(header);

        List<String> current = token.getConditions();
        Condition[] allConditions = Condition.values();
        List<CheckBox> boxes = new ArrayList<>();
        for (Condition condition : allConditions) {
            CheckBox box = new CheckBox(condition.label());
            box.setSelected(current.contains(condition.name()));
            boxes.add(box);
            root.getChildren().add(box);
        }

        Button applyButton = new Button("Aplicar");
        applyButton.getStyleClass().add("button-primary");
        applyButton.setOnAction(e -> {
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < allConditions.length; i++) {
                if (boxes.get(i).isSelected()) {
                    selected.add(allConditions[i].name());
                }
            }
            onApplied.accept(selected);
            stage.close();
        });
        root.getChildren().add(applyButton);

        Scene scene = new Scene(root, 280, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }
}
