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
import javafx.scene.layout.StackPane;
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
        hero.getChildren().addAll(Icons.logo(104), name, tagline, about, grow);
        // rodape: onde ficam as mesas e o tema, numa caixa so
        VBox footer = new VBox(8);
        footer.getStyleClass().add("hero-footer");
        if (baseFolder != null) {
            Path fn = baseFolder.getFileName();
            Label folder = new Label(fn == null ? baseFolder.toString() : fn.toString(), Icons.icon("open", 14));
            folder.getStyleClass().add("hero-footer-text");
            folder.setMaxWidth(200);
            folder.setTooltip(new javafx.scene.control.Tooltip("Pasta das mesas: " + baseFolder));
            Button change = new Button("Trocar");
            change.getStyleClass().add("button-flat");
            change.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            change.setOnAction(e -> chooseBaseFolder());
            javafx.scene.layout.Region g = new javafx.scene.layout.Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            HBox folderRow = new HBox(6, folder, g, change);
            folderRow.setAlignment(Pos.CENTER_LEFT);
            footer.getChildren().add(folderRow);
        }
        Label themeLabel = new Label("Tema");
        themeLabel.getStyleClass().add("hero-footer-text");
        javafx.scene.control.ComboBox<String> theme = com.vttcaseiro.app.Theme.picker();
        javafx.scene.layout.Region g2 = new javafx.scene.layout.Region();
        HBox.setHgrow(g2, Priority.ALWAYS);
        HBox themeRow = new HBox(6, themeLabel, g2, theme);
        themeRow.setAlignment(Pos.CENTER_LEFT);
        footer.getChildren().add(themeRow);
        footer.setMaxWidth(Double.MAX_VALUE);
        hero.getChildren().add(footer);
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
        // a descricao quebra linha dentro do cartao (em vez de cortar com "...")
        text.maxWidthProperty().bind(b.widthProperty().subtract(70));
        d.maxWidthProperty().bind(b.widthProperty().subtract(70));
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
        right.setPadding(new Insets(26, 30, 22, 28));
        HBox.setHgrow(right, Priority.ALWAYS);

        List<String> names = listMesaNames(baseFolder);
        Label title = new Label("Suas mesas");
        title.getStyleClass().add("label-title");
        title.setStyle("-fx-font-size: 22px;");
        Label count = new Label(names.isEmpty() ? "" : names.size() == 1 ? "1 campanha" : names.size() + " campanhas");
        count.getStyleClass().add("label-muted");
        javafx.scene.control.TextField search = new javafx.scene.control.TextField();
        search.setPromptText("Buscar mesa");
        search.setPrefColumnCount(12);
        search.setVisible(names.size() > 4);
        search.setManaged(names.size() > 4);
        javafx.scene.layout.Region grow = new javafx.scene.layout.Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(10, title, count, grow, search);
        header.setAlignment(Pos.CENTER_LEFT);
        Label subtitle = new Label("Cada mesa é uma campanha. Clique duas vezes pra entrar; botão direito pra mais opções.");
        subtitle.getStyleClass().add("label-muted");
        subtitle.setWrapText(true);

        javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane(14, 14);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.getStyleClass().add("mesa-grid");
        grid.setPadding(new Insets(2));
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("mesa-scroll");
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        grid.prefWrapLengthProperty().bind(scroll.widthProperty().subtract(20));

        Button openButton = actionCard("open", "Abrir mesa", "Continua a campanha escolhida.", true);
        String[] selected = {names.isEmpty() ? null : names.get(0)};
        java.util.Map<String, VBox> cards = new java.util.LinkedHashMap<>();
        Runnable paintSelection = () -> {
            cards.forEach((n, c) -> {
                c.getStyleClass().remove("mesa-card-selected");
                if (n.equals(selected[0])) c.getStyleClass().add("mesa-card-selected");
            });
            openButton.setDisable(selected[0] == null);
        };
        for (String name : names) {
            VBox card = mesaCard(baseFolder, name);
            card.setOnMouseClicked(e -> {
                selected[0] = name;
                paintSelection.run();
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2) openMesa(baseFolder, name);
            });
            card.setOnContextMenuRequested(e -> {
                selected[0] = name;
                paintSelection.run();
                mesaMenu(baseFolder, name).show(card, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            cards.put(name, card);
            grid.getChildren().add(card);
        }
        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.strip().toLowerCase();
            cards.forEach((n, c) -> {
                boolean show = q.isEmpty() || n.toLowerCase().contains(q);
                c.setVisible(show);
                c.setManaged(show);
            });
        });
        if (names.isEmpty()) {
            VBox empty = new VBox(8);
            empty.getStyleClass().add("mesa-empty");
            empty.setAlignment(Pos.CENTER);
            Label t = new Label("Comece sua primeira campanha");
            t.getStyleClass().add("mesa-name");
            Label d = new Label("Uma mesa guarda os mapas, as fichas, o diário e as imagens de uma campanha.");
            d.getStyleClass().add("label-muted");
            d.setWrapText(true);
            Button create = new Button("Nova mesa", Icons.icon("add", 14));
            create.getStyleClass().add("button-primary");
            create.setOnAction(e -> onNewMesa(baseFolder));
            empty.getChildren().addAll(Icons.logo(64), t, d, create);
            grid.getChildren().add(empty);
        }
        paintSelection.run();
        openButton.setOnAction(e -> {
            if (selected[0] != null) openMesa(baseFolder, selected[0]);
        });

        Button newMesaButton = actionCard("add", "Nova mesa", "Começa do zero.", false);
        newMesaButton.setOnAction(e -> onNewMesa(baseFolder));

        Button importButton = actionCard("upload", "Importar backup", "De outro PC (.zip).", false);
        importButton.setTooltip(new javafx.scene.control.Tooltip("Abre um .zip exportado (engrenagem ⚙ da mesa > Exportar mesa inteira) como uma mesa nova."));
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
                    error("Não foi possível importar", ex.getMessage());
                }
            });
        });
        Button joinButton = actionCard("network", "Entrar online", "Mesa de um amigo.", false);
        joinButton.setTooltip(new javafx.scene.control.Tooltip("Endereço (ou código da ponte) e senha que o mestre passou."));
        joinButton.setOnAction(e -> onJoin(baseFolder));
        // um destaque so (Abrir); os outros tres lado a lado embaixo
        javafx.scene.layout.GridPane actions = new javafx.scene.layout.GridPane();
        actions.setHgap(10);
        actions.setVgap(10);
        List<Button> secondary = new java.util.ArrayList<>(List.of(newMesaButton, importButton));
        if (onJoinOnline != null) secondary.add(1, joinButton);
        for (int i = 0; i < secondary.size(); i++) {
            javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
            col.setPercentWidth(100.0 / secondary.size());
            actions.getColumnConstraints().add(col);
            actions.add(secondary.get(i), i, 1);
        }
        actions.add(openButton, 0, 0, secondary.size(), 1);

        right.getChildren().addAll(header, subtitle, scroll, actions);
        HBox root = new HBox(hero(baseFolder), right);
        applyScene(root, 980, 640, "Customizr - Escolher mesa");
    }

    /** Cartao de uma mesa: miniatura da ultima cena, nome e um resumo (quando foi usada, quantas cenas e fichas). */
    VBox mesaCard(Path baseFolder, String name) {
        Path mesa = baseFolder.resolve(name);
        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("mesa-thumb");
        thumb.setMinSize(MESA_THUMB_W, MESA_THUMB_H);
        thumb.setMaxSize(MESA_THUMB_W, MESA_THUMB_H);
        javafx.scene.Node placeholder = Icons.icon("map", 30);
        thumb.getChildren().add(placeholder);
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
        iv.setFitWidth(MESA_THUMB_W);
        iv.setFitHeight(MESA_THUMB_H);
        iv.setPreserveRatio(false);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(MESA_THUMB_W, MESA_THUMB_H);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        iv.setClip(clip);
        thumb.getChildren().add(iv);
        // a imagem carrega fora da tela (mesa grande nao trava a lista)
        Thread t = new Thread(() -> {
            javafx.scene.image.Image img = lastSceneImage(mesa);
            if (img != null) javafx.application.Platform.runLater(() -> {
                iv.setImage(img);
                placeholder.setVisible(false);
            });
        }, "miniatura-mesa");
        t.setDaemon(true);
        t.start();
        Label n = new Label(name);
        n.getStyleClass().add("mesa-name");
        n.setMaxWidth(MESA_THUMB_W);
        Label info = new Label(mesaSummary(mesa));
        info.getStyleClass().add("mesa-info");
        info.setMaxWidth(MESA_THUMB_W);
        VBox card = new VBox(6, thumb, n, info);
        card.getStyleClass().add("mesa-card");
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setUserData(name);
        return card;
    }

    static final double MESA_THUMB_W = 200;
    static final double MESA_THUMB_H = 112;

    /** "usada ontem · 3 cenas · 2 fichas". */
    static String mesaSummary(Path mesa) {
        List<String> parts = new java.util.ArrayList<>();
        String when = lastUsed(mesa);
        if (!when.isEmpty()) parts.add(when);
        int scenes = countJson(mesa.resolve("mapas"));
        int sheets = countJson(mesa.resolve("fichas"));
        parts.add(scenes == 1 ? "1 cena" : scenes + " cenas");
        parts.add(sheets == 1 ? "1 ficha" : sheets + " fichas");
        return String.join("  ·  ", parts);
    }

    private static int countJson(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        } catch (IOException ex) {
            return 0;
        }
    }

    /** A imagem de fundo da cena mexida por ultimo (pequena), ou null. */
    static javafx.scene.image.Image lastSceneImage(Path mesa) {
        Path maps = mesa.resolve("mapas");
        if (!Files.isDirectory(maps)) return null;
        try (Stream<Path> files = Files.list(maps)) {
            List<Path> byDate = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException ex) {
                            return 0;
                        }
                    }).toList();
            for (Path p : byDate) {
                com.vttcaseiro.model.SceneModel sc = com.vttcaseiro.persistence.SceneStorage.load(p);
                String id = sc.getBackgroundImageAssetId();
                if (id == null || !com.vttcaseiro.library.ImageAssetStore.isValidAssetId(id)) continue;
                Path img = mesa.resolve("assets").resolve("images").resolve(id);
                if (!Files.exists(img)) continue;
                SafeImage.Loaded l = SafeImage.read(Files.readAllBytes(img), 420, 420L * 240L);
                if (l != null) return l.image();
            }
        } catch (IOException | RuntimeException ignored) {
            // sem miniatura: fica o icone
        }
        return null;
    }

    /** Botao direito no cartao da mesa. */
    private javafx.scene.control.ContextMenu mesaMenu(Path baseFolder, String name) {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Abrir");
        open.setOnAction(e -> openMesa(baseFolder, name));
        javafx.scene.control.MenuItem rename = new javafx.scene.control.MenuItem("Renomear...");
        rename.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog(name);
            d.initOwner(stage);
            d.setTitle("Renomear mesa");
            d.setHeaderText(null);
            d.setContentText("Nome:");
            d.showAndWait().map(String::strip).filter(x -> !x.isEmpty() && !x.equals(name)).ifPresent(n -> {
                try {
                    String safe = com.vttcaseiro.library.MesaBackup.safeName(n);
                    Path target = baseFolder.resolve(safe);
                    if (Files.exists(target)) throw new IOException("Já existe uma mesa chamada \"" + safe + "\".");
                    Files.move(baseFolder.resolve(name), target);
                    showMesaList(baseFolder);
                } catch (IOException | RuntimeException ex) {
                    error("Não deu pra renomear", ex.getMessage());
                }
            });
        });
        javafx.scene.control.MenuItem backup = new javafx.scene.control.MenuItem("Exportar backup (.zip)...");
        backup.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Salvar backup da mesa");
            fc.setInitialFileName(com.vttcaseiro.library.MesaBackup.safeName(name) + "-" + java.time.LocalDate.now() + ".zip");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Backup (.zip)", "*.zip"));
            File out = fc.showSaveDialog(stage);
            if (out == null) return;
            try {
                int n = com.vttcaseiro.library.MesaBackup.export(baseFolder.resolve(name), out.toPath());
                Alert ok = new Alert(Alert.AlertType.INFORMATION, "Backup salvo: " + out.getName() + " (" + n + " arquivos).");
                ok.initOwner(stage);
                ok.setHeaderText(null);
                ok.showAndWait();
            } catch (IOException ex) {
                error("Não deu pra salvar o backup", ex.getMessage());
            }
        });
        javafx.scene.control.MenuItem folder = new javafx.scene.control.MenuItem("Abrir a pasta da mesa");
        folder.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(baseFolder.resolve(name).toFile());
            } catch (IOException | RuntimeException ex) {
                error("Não deu pra abrir a pasta", String.valueOf(baseFolder.resolve(name)));
            }
        });
        javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Apagar (vai pra lixeira das mesas)...");
        delete.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Mandar a mesa \"" + name + "\" pra lixeira?\n\n"
                    + "Ela vai pra pasta " + baseFolder.resolve(TRASH_DIR) + " e some da lista. Pra recuperar, mova a "
                    + "pasta de volta pra " + baseFolder + ".");
            a.initOwner(stage);
            a.setHeaderText(null);
            if (a.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
            try {
                moveToTrash(baseFolder, name);
                showMesaList(baseFolder);
            } catch (IOException ex) {
                error("Não deu pra apagar", ex.getMessage());
            }
        });
        m.getItems().addAll(open, rename, backup, folder, new javafx.scene.control.SeparatorMenuItem(), delete);
        return m;
    }

    /** Lixeira das mesas (pasta escondida dentro da pasta das mesas: some da lista, da pra recuperar). */
    public static final String TRASH_DIR = ".lixeira";

    /** Move a mesa pra lixeira (com a data no nome, nunca sobrescreve). Devolve onde ela foi parar. */
    public static Path moveToTrash(Path baseFolder, String name) throws IOException {
        Path trash = Files.createDirectories(baseFolder.resolve(TRASH_DIR));
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        Path target = trash.resolve(name + " " + stamp);
        int i = 2;
        while (Files.exists(target)) target = trash.resolve(name + " " + stamp + " " + i++);
        return Files.move(baseFolder.resolve(name), target);
    }

    private void error(String title, String text) {
        Alert err = new Alert(Alert.AlertType.ERROR, text);
        err.initOwner(stage);
        err.setHeaderText(title);
        err.showAndWait();
    }

    static String lastUsed(Path mesa) {
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
        Optional<JoinRequest> asked = askJoin(stage);
        if (asked.isEmpty()) return;
        JoinRequest req = asked.get();
        try {
            onJoinOnline.accept(new LibraryStorage(onlineFolder(baseFolder, req)), req);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Não foi possível preparar a pasta da mesa online: " + ex.getMessage());
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    /** Pergunta endereco/codigo/senhas pra entrar numa mesa online (lembra o caminho, nunca as senhas). */
    /**
     * "algo.ply.gg:12345" (ou "tcp://algo.ply.gg:12345/") -> {"algo.ply.gg", "12345"}; null se nao tiver porta
     * (ou for um IPv6 sem colchetes, que tem varios ":").
     */
    public static String[] splitHostPort(String text) {
        if (text == null) return null;
        String t = text.trim().replaceFirst("^[a-zA-Z]+://", "").replaceAll("/+$", "");
        int colon = t.lastIndexOf(':');
        if (colon <= 0 || colon == t.length() - 1 || t.indexOf(':') != colon) return null;
        String host = t.substring(0, colon).trim();
        String port = t.substring(colon + 1).trim();
        if (host.isEmpty() || !port.matches("\\d{1,5}")) return null;
        int p = Integer.parseInt(port);
        if (p < 1 || p > 65535) return null;
        return new String[]{host, port};
    }

    public static Optional<JoinRequest> askJoin(javafx.stage.Window owner) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Entrar numa mesa online");
        dialog.setHeaderText("O mestre te passa o endereço (ou o código da ponte) e a senha, se tiver.");
        javafx.scene.control.TextField name = new javafx.scene.control.TextField(AppSettings.getString("online.nome", ""));
        name.setPromptText("Como você aparece pra mesa");
        javafx.scene.control.TextField host = new javafx.scene.control.TextField(AppSettings.getString("online.host", ""));
        host.setPromptText("ex.: 100.64.0.5 ou meuservidor.com");
        javafx.scene.control.TextField port = new javafx.scene.control.TextField(AppSettings.getString("online.porta", "55055"));
        port.setPrefColumnCount(6);
        host.setPromptText("ex.: 100.64.0.5, meuservidor.com ou algo.ply.gg:12345");
        // colou "endereco:porta" (o playit.gg mostra assim): separa sozinho
        host.textProperty().addListener((o, a, b) -> {
            String[] hp = splitHostPort(b);
            if (hp != null) {
                javafx.application.Platform.runLater(() -> {
                    host.setText(hp[0]);
                    port.setText(hp[1]);
                });
            }
        });
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
        if (dialog.showAndWait().filter(b -> b == enter).isEmpty()) return Optional.empty();
        JoinRequest req = new JoinRequest(name.getText().trim(), host.getText().trim(), port.getText().trim(),
                code.getText().trim(), password.getText(), tls.isSelected(), gm.isSelected(), gmPassword.getText());
        AppSettings.setString("online.nome", req.playerName());
        AppSettings.setString("online.host", req.host());
        AppSettings.setString("online.porta", req.port());
        AppSettings.setString("online.codigo", req.relayCode());
        AppSettings.setBool("online.tls", req.tls());
        return Optional.of(req);
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
