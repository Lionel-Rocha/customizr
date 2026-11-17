package com.vttcaseiro.view;

import com.vttcaseiro.app.AppSettings;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.repository.MesaRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tela inicial: escolher (ou criar) uma mesa (campanha), igual o Foundry
 * pede pra escolher/criar um "World" antes de entrar no jogo, ou o Roll20
 * pede pra escolher qual "Game" abrir. Uma mesa e so uma pasta com
 * "mapas/" e "fichas/" dentro (ver LibraryStorage) -- nao existe mais a
 * ideia antiga de "biblioteca opcional"; toda mesa JA E uma biblioteca.
 */
public class MesaSelectorView {
    private final Stage stage;
    private final Consumer<MesaRepository> onMesaChosen;

    public MesaSelectorView(Stage stage, Consumer<MesaRepository> onMesaChosen) {
        this.stage = stage;
        this.onMesaChosen = onMesaChosen;
    }

    public void show() {
        Optional<Path> saved = AppSettings.loadMesasFolder();
        if (saved.isPresent()) {
            showMesaList(saved.get());
        } else {
            showChooseBaseFolder();
        }
    }

    /** Painel da esquerda: logo, nome e o que e o programa (igual nas duas telas). */
    private VBox hero(Path baseFolder) {
        VBox hero = new VBox(10);
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(36, 28, 24, 28));
        hero.setPrefWidth(330);
        hero.setMinWidth(300);
        Label name = new Label("Customizr");
        name.getStyleClass().add("hero-title");
        Label tagline = new Label("historias em volta da fogueira");
        tagline.getStyleClass().add("hero-tagline");
        Label about = new Label("Sua mesa virtual de RPG: mapa, fichas, dados e chat pra jogar com os amigos, "
                + "no mesmo computador, na rede de casa ou pela internet.");
        about.getStyleClass().add("hero-about");
        about.setWrapText(true);
        about.setMaxWidth(260);
        javafx.scene.layout.Region grow = new javafx.scene.layout.Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        hero.getChildren().addAll(Icons.logo(132), name, tagline, about, grow);
        if (baseFolder != null) {
            Label folder = new Label("Pasta das mesas:\n" + baseFolder);
            folder.getStyleClass().add("label-muted");
            folder.setWrapText(true);
            folder.setMaxWidth(270);
            folder.setStyle("-fx-font-size: 11px; -fx-text-alignment: center;");
            Button change = new Button("Trocar pasta...");
            change.getStyleClass().add("button-flat");
            change.setOnAction(e -> chooseBaseFolder());
            hero.getChildren().addAll(folder, change);
        }
        return hero;
    }

    private void chooseBaseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher pasta onde ficam suas mesas");
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            AppSettings.saveMesasFolder(folder.toPath());
            showMesaList(folder.toPath());
        }
    }

    /** Um botao grande: icone + titulo + uma linha explicando. */
    private Button actionCard(String icon, String title, String description, boolean primary) {
        Label t = new Label(title);
        t.getStyleClass().add("action-card-title");
        Label d = new Label(description);
        d.getStyleClass().add("action-card-text");
        d.setWrapText(true);
        VBox text = new VBox(2, t, d);
        HBox content = new HBox(12, Icons.icon(icon, 26), text);
        content.setAlignment(Pos.CENTER_LEFT);
        Button b = new Button();
        b.setGraphic(content);
        b.getStyleClass().add("action-card");
        if (primary) b.getStyleClass().add("action-card-primary");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMinHeight(76);
        b.setPrefHeight(76);
        b.setMaxHeight(76);
        return b;
    }

    private void showChooseBaseFolder() {
        VBox right = new VBox(16);
        right.setPadding(new Insets(48, 36, 36, 36));
        right.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Bem-vindo!");
        title.getStyleClass().add("label-title");
        title.setStyle("-fx-font-size: 24px;");
        Label hint = new Label("Primeiro, escolha uma pasta deste computador pra guardar suas mesas (campanhas). "
                + "Cada mesa vira uma subpasta com mapas, fichas, imagens e sons.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        Button chooseButton = actionCard("open", "Escolher pasta das mesas",
                "Pode ser em Documentos, num pendrive ou numa pasta sincronizada.", true);
        chooseButton.setOnAction(e -> chooseBaseFolder());
        right.getChildren().addAll(title, hint, chooseButton);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox root = new HBox(hero(null), right);
        applyScene(root, 900, 560, "Customizr");
    }

    private void showMesaList(Path baseFolder) {
        VBox right = new VBox(12);
        right.setPadding(new Insets(28, 32, 24, 28));
        HBox.setHgrow(right, Priority.ALWAYS);

        Label title = new Label("Suas mesas");
        title.getStyleClass().add("label-title");
        title.setStyle("-fx-font-size: 22px;");
        Label subtitle = new Label("Cada mesa é uma campanha. Clique duas vezes pra entrar.");
        subtitle.getStyleClass().add("label-muted");

        ListView<String> list = new ListView<>();
        list.getStyleClass().add("mesa-list");
        list.setPlaceholder(new Label("Nenhuma mesa aqui ainda: crie a primeira abaixo."));
        list.setMinHeight(150);
        list.getItems().addAll(listMesaNames(baseFolder));
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label n = new Label(item);
                n.getStyleClass().add("mesa-name");
                Label when = new Label(lastUsed(baseFolder.resolve(item)));
                when.getStyleClass().add("label-muted");
                when.setStyle("-fx-font-size: 11px;");
                HBox row = new HBox(10, Icons.icon("map", 18), new VBox(1, n, when));
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button openButton = actionCard("open", "Abrir mesa", "Continua a campanha selecionada na lista.", true);
        openButton.setDisable(true);
        list.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldV, newV) -> openButton.setDisable(newV == null));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String name = list.getSelectionModel().getSelectedItem();
                if (name != null) {
                    openMesa(baseFolder, name);
                }
            }
        });
        openButton.setOnAction(e -> {
            String name = list.getSelectionModel().getSelectedItem();
            if (name != null) {
                openMesa(baseFolder, name);
            }
        });
        if (!list.getItems().isEmpty()) list.getSelectionModel().select(0);

        Button newMesaButton = actionCard("add", "Nova mesa", "Comeca uma campanha do zero.", false);
        newMesaButton.setOnAction(e -> onNewMesa(baseFolder));

        Button importButton = actionCard("upload", "Importar backup", "Mesa de outro computador (.zip).", false);
        importButton.setTooltip(new javafx.scene.control.Tooltip("Abre um .zip exportado (aba Rede > Backup da mesa) como uma mesa nova."));
        importButton.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Escolher backup da mesa (.zip)");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Backup (.zip)", "*.zip"));
            File zip = chooser.showOpenDialog(stage);
            if (zip == null) return;
            javafx.scene.control.TextInputDialog nameDialog = new javafx.scene.control.TextInputDialog(
                    zip.getName().replaceAll("(-\\d{4}-\\d{2}-\\d{2})?\\.zip$", ""));
            nameDialog.setTitle("Importar backup");
            nameDialog.setHeaderText(null);
            nameDialog.setContentText("Nome da mesa nova:");
            nameDialog.showAndWait().ifPresent(name -> {
                try {
                    com.vttcaseiro.library.MesaBackup.importTo(zip.toPath(), baseFolder, name);
                    showMesaList(baseFolder);
                } catch (java.io.IOException ex) {
                    javafx.scene.control.Alert err = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR, ex.getMessage());
                    err.setHeaderText("Não foi possível importar");
                    err.showAndWait();
                }
            });
        });
        javafx.scene.layout.GridPane actions = new javafx.scene.layout.GridPane();
        actions.setHgap(10);
        actions.setVgap(10);
        javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        actions.getColumnConstraints().addAll(half, half);
        actions.add(openButton, 0, 0, 2, 1);
        actions.add(newMesaButton, 0, 1);
        actions.add(importButton, 1, 1);

        Label join = new Label("Vai jogar na mesa de outra pessoa? Abra (ou crie) qualquer mesa e use a aba Rede > "
                + "Conectar. Pelo navegador, nem precisa instalar: o mestre te passa um link.");
        join.getStyleClass().add("tip-box");
        join.setWrapText(true);
        join.setMaxWidth(Double.MAX_VALUE);
        join.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        right.getChildren().addAll(title, subtitle, list, actions, join);
        HBox root = new HBox(hero(baseFolder), right);
        applyScene(root, 900, 600, "Customizr - Escolher mesa");
    }

    private static String lastUsed(Path mesa) {
        try {
            java.time.Instant latest = Files.getLastModifiedTime(mesa).toInstant();
            for (String f : new String[]{"chat-log.jsonl", "mapas", "fichas", "handouts.json"}) {
                Path p = mesa.resolve(f);
                if (Files.exists(p)) {
                    java.time.Instant t = Files.getLastModifiedTime(p).toInstant();
                    if (t.isAfter(latest)) latest = t;
                }
            }
            java.time.LocalDate d = java.time.LocalDate.ofInstant(latest, java.time.ZoneId.systemDefault());
            long days = java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now());
            if (days <= 0) return "usada hoje";
            if (days == 1) return "usada ontem";
            if (days < 30) return "usada há " + days + " dias";
            return "usada em " + d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (IOException ex) {
            return "";
        }
    }

    private List<String> listMesaNames(Path baseFolder) {
        try (Stream<Path> paths = Files.list(baseFolder)) {
            return paths.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void onNewMesa(Path baseFolder) {
        TextInputDialog dialog = new TextInputDialog("Minha campanha");
        dialog.setTitle("Nova mesa");
        dialog.setHeaderText(null);
        dialog.setContentText("Nome da mesa (campanha):");
        Optional<String> name = dialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }
        openMesa(baseFolder, name.get().trim());
    }

    private void openMesa(Path baseFolder, String name) {
        try {
            LibraryStorage mesa = new LibraryStorage(baseFolder.resolve(name));
            onMesaChosen.accept(mesa);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Não foi possível abrir/criar a mesa: " + ex.getMessage());
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    private void applyScene(javafx.scene.Parent box, double width, double height, String title) {
        Scene scene = new Scene(box, width, height);
        box.getStyleClass().add("selector-root");
        Icons.applyTo(stage);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }
}
