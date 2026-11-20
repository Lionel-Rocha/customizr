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
    private final java.util.function.BiConsumer<MesaRepository, JoinRequest> onJoinOnline;

    /** Dados pra entrar na mesa de outra pessoa (endereco ou codigo da ponte, senhas). */
    public record JoinRequest(String playerName, String host, String port, String relayCode, String tablePassword,
                              boolean tls, boolean asGm, String masterPassword) {
    }

    /** Pasta (escondida da lista) onde fica o que vem de uma mesa online: fichas, imagens e cenas recebidas. */
    public static final String ONLINE_DIR = ".online";

    public MesaSelectorView(Stage stage, Consumer<MesaRepository> onMesaChosen) {
        this(stage, onMesaChosen, null);
    }

    public MesaSelectorView(Stage stage, Consumer<MesaRepository> onMesaChosen,
                            java.util.function.BiConsumer<MesaRepository, JoinRequest> onJoinOnline) {
        this.stage = stage;
        this.onMesaChosen = onMesaChosen;
        this.onJoinOnline = onJoinOnline;
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
        Button joinButton = actionCard("network", "Entrar numa mesa online",
                "Jogar na mesa de outra pessoa: endereço (ou código da ponte) e senha.", true);
        joinButton.setOnAction(e -> onJoin(baseFolder));
        joinButton.setVisible(onJoinOnline != null);
        joinButton.setManaged(onJoinOnline != null);
        if (onJoinOnline != null) {
            actions.add(openButton, 0, 0);
            actions.add(joinButton, 1, 0);
        } else {
            actions.add(openButton, 0, 0, 2, 1);
        }
        actions.add(newMesaButton, 0, 1);
        actions.add(importButton, 1, 1);

        right.getChildren().addAll(title, subtitle, list, actions);
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
                    .filter(n -> !n.startsWith(".")) // .online e outras pastas internas
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** Pasta local de uma mesa online (uma por endereco/codigo), dentro de .online. */
    public static Path onlineFolder(Path baseFolder, JoinRequest req) {
        String where = req.relayCode() != null && !req.relayCode().isBlank()
                ? "ponte " + req.relayCode().trim() : req.host().trim() + " " + req.port().trim();
        return baseFolder.resolve(ONLINE_DIR).resolve(com.vttcaseiro.library.MesaBackup.safeName("Online - " + where));
    }

    private void onJoin(Path baseFolder) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Entrar numa mesa online");
        dialog.setHeaderText("O mestre te passa o endereço (ou o código da ponte) e a senha, se tiver.");
        javafx.scene.control.TextField name = new javafx.scene.control.TextField(AppSettings.getString("online.nome", ""));
        name.setPromptText("Como você aparece pra mesa");
        javafx.scene.control.TextField host = new javafx.scene.control.TextField(AppSettings.getString("online.host", ""));
        host.setPromptText("ex.: 100.64.0.5 ou meuservidor.com");
        javafx.scene.control.TextField port = new javafx.scene.control.TextField(AppSettings.getString("online.porta", "55055"));
        port.setPrefColumnCount(6);
        javafx.scene.control.TextField code = new javafx.scene.control.TextField(AppSettings.getString("online.codigo", ""));
        code.setPromptText("só se for pela ponte (ex.: K7P-Q2X)");
        code.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.isBlank() && "55055".equals(port.getText())) port.setText("55070");
        });
        javafx.scene.control.PasswordField password = new javafx.scene.control.PasswordField();
        password.setPromptText("se a mesa tiver");
        javafx.scene.control.CheckBox tls = new javafx.scene.control.CheckBox("Mesa criptografada (TLS)");
        tls.setSelected(AppSettings.getBool("online.tls", false));
        javafx.scene.control.CheckBox gm = new javafx.scene.control.CheckBox("Entrar como mestre");
        javafx.scene.control.PasswordField gmPassword = new javafx.scene.control.PasswordField();
        gmPassword.setPromptText("senha de mestre");
        gmPassword.disableProperty().bind(gm.selectedProperty().not());
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Seu nome"), name);
        grid.addRow(1, new Label("Endereço"), new HBox(6, host, new Label(":"), port));
        grid.addRow(2, new Label("Código da ponte"), code);
        grid.addRow(3, new Label("Senha da mesa"), password);
        grid.add(tls, 1, 4);
        grid.add(gm, 1, 5);
        grid.add(gmPassword, 1, 6);
        HBox.setHgrow(host, Priority.ALWAYS);
        grid.setPrefWidth(460);
        dialog.getDialogPane().setContent(grid);
        javafx.scene.control.ButtonType enter = new javafx.scene.control.ButtonType("Entrar",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(enter, javafx.scene.control.ButtonType.CANCEL);
        javafx.scene.Node enterNode = dialog.getDialogPane().lookupButton(enter);
        enterNode.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> name.getText().isBlank() || host.getText().isBlank(), name.textProperty(), host.textProperty()));
        if (dialog.showAndWait().filter(b -> b == enter).isEmpty()) return;
        JoinRequest req = new JoinRequest(name.getText().trim(), host.getText().trim(), port.getText().trim(),
                code.getText().trim(), password.getText(), tls.isSelected(), gm.isSelected(), gmPassword.getText());
        // lembra o caminho (nunca as senhas)
        AppSettings.setString("online.nome", req.playerName());
        AppSettings.setString("online.host", req.host());
        AppSettings.setString("online.porta", req.port());
        AppSettings.setString("online.codigo", req.relayCode());
        AppSettings.setBool("online.tls", req.tls());
        try {
            onJoinOnline.accept(new LibraryStorage(onlineFolder(baseFolder, req)), req);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Não foi possível preparar a pasta da mesa online: " + ex.getMessage());
            alert.setHeaderText(null);
            alert.showAndWait();
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
