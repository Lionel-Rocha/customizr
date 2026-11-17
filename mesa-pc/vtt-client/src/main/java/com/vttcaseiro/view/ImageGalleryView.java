package com.vttcaseiro.view;

import com.vttcaseiro.service.ImageAssetService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Janela de "galeria": lista todas as imagens que ja foram guardadas
 * alguma vez nesta mesa ({@link ImageAssetService#list()} -- cada uma UMA
 * SO VEZ no disco, ver {@code ImageAssetStore}), com uma miniatura de
 * cada uma, pra escolher uma pra reusar num token/retrato novo em vez de
 * reabrir o seletor de arquivo e (sem querer) importar a mesma imagem
 * "de novo" (o asset store ja deduplicaria por hash, mas isso nem
 * precisa acontecer se a pessoa so escolher da galeria direto).
 * <p>
 * So de escolha -- nao tem "apagar imagem da mesa" aqui (uma imagem pode
 * estar em uso por varios tokens/fichas ao mesmo tempo, e descobrir quem
 * usa o que pra avisar antes de apagar e escopo que ainda nao foi
 * resolvido, ver HANDOFF.md).
 */
public class ImageGalleryView {

    private final ImageAssetService imageAssetService;
    private final Consumer<String> onAssetChosen;

    public ImageGalleryView(ImageAssetService imageAssetService, Consumer<String> onAssetChosen) {
        this.imageAssetService = imageAssetService;
        this.onAssetChosen = onAssetChosen;
    }

    public void show() {
        Stage stage = new Stage();
        Icons.applyTo(stage);
        stage.setTitle("Galeria de imagens da mesa");

        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #1B1411;");

        List<String> ids = imageAssetService.list();

        Label header = new Label(ids.isEmpty()
                ? "Nenhuma imagem guardada nesta mesa ainda -- escolha um arquivo pelo "
                        + "menos uma vez (num token ou retrato) pra ela aparecer aqui depois."
                : "Imagens já guardadas nesta mesa -- clique duplo, ou selecione e confirme:");
        header.getStyleClass().add("label-muted");
        header.setWrapText(true);
        root.getChildren().add(header);

        ListView<String> list = new ListView<>();
        list.getItems().addAll(ids);
        list.setPrefSize(340, 360);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String assetId, boolean empty) {
                super.updateItem(assetId, empty);
                if (empty || assetId == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Image thumbnail = loadThumbnail(assetId);
                if (thumbnail != null) {
                    ImageView iv = new ImageView(thumbnail);
                    iv.setFitWidth(48);
                    iv.setFitHeight(48);
                    iv.setPreserveRatio(true);
                    setGraphic(iv);
                } else {
                    setGraphic(null);
                }
                setText(assetId);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                chooseAndClose(list, stage);
            }
        });
        root.getChildren().add(list);

        Button chooseButton = new Button("Usar a selecionada");
        chooseButton.getStyleClass().add("button-primary");
        chooseButton.setOnAction(e -> chooseAndClose(list, stage));
        root.getChildren().add(chooseButton);

        Scene scene = new Scene(root, 380, 460);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void chooseAndClose(ListView<String> list, Stage stage) {
        String selected = list.getSelectionModel().getSelectedItem();
        if (selected != null && onAssetChosen != null) {
            onAssetChosen.accept(selected);
            stage.close();
        }
    }

    private Image loadThumbnail(String assetId) {
        try {
            byte[] bytes = imageAssetService.readBytes(assetId);
            Image candidate = new Image(new ByteArrayInputStream(bytes), 48, 48, true, true);
            return candidate.isError() ? null : candidate;
        } catch (IOException ex) {
            return null;
        }
    }
}
