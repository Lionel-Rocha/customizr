package com.vttcaseiro.app;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.dice.DiceException;
import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.ImageCodec;
import com.vttcaseiro.party.Party;
import com.vttcaseiro.repository.MesaRepository;
import com.vttcaseiro.service.CharacterService;
import com.vttcaseiro.service.ChatCommands;
import com.vttcaseiro.service.EncounterService;
import com.vttcaseiro.service.TableService;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumReference;
import com.vttcaseiro.service.ClassImportService;
import com.vttcaseiro.service.CompendiumImportService;
import com.vttcaseiro.service.CompendiumService;
import com.vttcaseiro.service.ImageAssetService;
import com.vttcaseiro.service.MesaService;
import com.vttcaseiro.service.MobImportService;
import com.vttcaseiro.service.MobService;
import com.vttcaseiro.service.NetworkService;
import com.vttcaseiro.service.PartyService;
import com.vttcaseiro.service.SceneService;
import com.vttcaseiro.view.CharacterSheetView;
import com.vttcaseiro.view.ImageGalleryView;
import com.vttcaseiro.view.MobSheetView;
import com.vttcaseiro.view.ChatListCell;
import com.vttcaseiro.view.MapView;
import com.vttcaseiro.view.MesaSelectorView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Janela principal. Depois da reestruturacao de backend: Main NAO mexe
 * mais direto em SceneModel, GameClient/GameServer ou LibraryStorage --
 * isso tudo mora em com.vttcaseiro.service (MesaService, SceneService,
 * CharacterService, NetworkService). Main so monta a tela e liga os
 * eventos da UI aos metodos dos servicos, e os servicos avisam a tela
 * quando algo muda (SceneService.SceneChangeListener) em vez da tela
 * ficar lembrando de atualizar tudo manualmente depois de cada acao --
 * esse era o maior "raso" que a revisao anterior apontou: tudo (UI, rede,
 * arquivo, regra de negocio) misturado numa classe so.
 * <p>
 * IMPORTANTE: este arquivo (e os outros 8 que usam javafx.*) agora
 * COMPILA de verdade com o javac real, contra uma biblioteca de stubs
 * do JavaFX escrita a mao (nao e o JavaFX de verdade -- ver HANDOFF.md,
 * secao "Verificacao dos arquivos com JavaFX"). Isso pega erro de tipo,
 * metodo inexistente, parametro errado etc., mas NAO prova que a tela
 * renderiza certo, que o layout fica bonito, ou que um clique realmente
 * dispara o que deveria -- isso continua sem rodar neste ambiente (sem
 * JavaFX real, sem tela). A parte de rede (sockets, heartbeat, validacao
 * de dono/mestre) foi testada via simulacao equivalente em Java puro --
 * ver HANDOFF.md pra saber exatamente o que rodou e o que so foi revisado.
 */
public class Main extends Application {

    private Stage stage;
    private MesaRepository mesaRepository;
    private MesaService mesaService;
    private CharacterService characterService;
    private MobService mobService;
    private MobImportService mobImportService;
    private PartyService partyService;
    private ImageAssetService imageAssetService;
    private CompendiumService compendiumService;
    private CompendiumImportService compendiumImportService;
    private SceneService sceneService;
    private ClassImportService classImportService;
    /** Regras da mesa (token, ficha ligada, combate) -- ver TableService. */
    private TableService table;
    /** Desfazer (Ctrl+Z) das acoes no mapa feitas nesta maquina. */
    private final com.vttcaseiro.service.UndoService undo = new com.vttcaseiro.service.UndoService();
    /** true enquanto um desfazer esta sendo aplicado (nao grava ele mesmo). */
    private boolean undoing;
    private final NetworkService networkService = new NetworkService();

    private MapView mapView;
    private BorderPane root;
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private ListView<LogEntry> chatListView;
    /** Imagens do chat ja abertas (id do asset -> imagem). */
    private final java.util.Map<String, javafx.scene.image.Image> chatImages = new java.util.HashMap<>();
    private Label netStatusLabel;
    private TextField gridSizeField;
    private ComboBox<String> gridTypeCombo;
    private TextField feetPerCellField;
    /** Atualiza os campos de tamanho do mapa (cena trocada, grade mudou). */
    private Runnable mapSizeRefresh = () -> { };
    private ListView<String> characterListView;
    private ListView<String> mobListView;
    private ListView<String> partyMemberListView;
    private ListView<InventoryItem> partyInventoryListView;
    private ListView<TokenModel> initiativeListView;
    /** Quem esta no turno agora (null = nenhuma rodada em andamento). E o
     * TokenModel em si, NAO um indice -- um indice ficaria invalido toda
     * vez que a lista reordena (ex.: editar a iniciativa de QUALQUER
     * token chama refreshInitiativeList, que reordena tudo; um indice
     * fixo passaria a apontar pro token errado depois disso). */
    private Label roundLabel;
    /** Ferramenta de neblina ativa: null, true (revelar) ou false (cobrir). */
    private Boolean fogToolMode;
    private List<Button> gmOnlyInitiativeButtons = List.of();
    /** Mestre: confirmar cada pedido de dano/cura/salvaguarda/efeito dos jogadores. */
    private boolean approveRequests;

    /** "Ana: 12 de dano de fogo em Goblin, Orc (Raio de Fogo)". */
    private String describeRequest(com.vttcaseiro.model.GmRequest r, String player) {
        List<String> names = new java.util.ArrayList<>();
        for (String id : r.getTokenIds()) sceneService.getScene().findToken(id).ifPresent(t -> names.add(t.getName()));
        String what = switch (String.valueOf(r.getKind())) {
            case "damage" -> r.getAmount() + " de dano" + (r.getDamageType() == null ? ""
                    : " de " + com.vttcaseiro.character.DamageTypes.namePt(r.getDamageType()));
            case "heal" -> "curar " + r.getAmount();
            case "save" -> "salvaguarda CD " + r.getDc() + " (" + r.getAmount() + " de dano)";
            case "effect" -> "efeito " + r.getLabel() + " por " + r.getAmount() + " rodada(s)";
            default -> r.getKind();
        };
        return player + ": " + what + " em " + String.join(", ", names)
                + (r.getLabel() != null && !"effect".equals(r.getKind()) ? " (" + r.getLabel() + ")" : "");
    }
    private com.vttcaseiro.library.MacroStore macroStore;
    private com.vttcaseiro.library.HandoutStore handoutStore;
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.RandomTable> tableStore;
    private com.vttcaseiro.library.JsonListStore<String> sceneQueueStore;
    private com.vttcaseiro.library.AudioAssetStore audioStore;
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.SoundEntry> soundIndex;
    private final SoundPlayer soundPlayer = new SoundPlayer();
    private com.vttcaseiro.view.DiceAnimator diceAnimator;
    /** Nome do arquivo da cena aberta (pra "próxima cena" da fila). */
    private String currentMapName;
    /** Area do mapa atual (o cartao do pin e o "Voltar" ficam por cima dela). */
    private StackPane mapStack;
    /** Rolagem de ataque -> como rolar o dano dela (botao "Rolar dano" no cartao do chat). */
    private interface DamageRoller {
        void roll(String label, String expression, boolean hidden);
    }

    private record FollowUp(String damageLabel, String expression, DamageRoller roller) {
    }

    private final java.util.Map<String, FollowUp> followUps = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, FollowUp> e) {
                    return size() > 200;
                }
            });

    private void registerFollowUp(String rollLabel, String damageLabel, String expression, DamageRoller roller) {
        if (rollLabel == null || expression == null || expression.isBlank()) return;
        followUps.put(rollLabel, new FollowUp(damageLabel, expression, roller));
    }

    /** O ataque registrado desta rolagem do chat, se for minha. */
    private FollowUp followUpOf(LogEntry e) {
        if (e == null || e.type() != LogEntry.Type.ROLL || e.text() == null) return null;
        if (e.author() != null && !e.author().equals(networkService.getPlayerName())) return null;
        synchronized (followUps) {
            for (var entry : followUps.entrySet()) {
                if (e.text().startsWith(entry.getKey() + ":")) return entry.getValue();
            }
        }
        return null;
    }

    /** Modo "editar objetos" ligado (sobrevive a troca de cena). */
    private boolean objectEditMode;
    private javafx.scene.Node pinCard;
    /** Cenas por onde o mestre passou clicando em pins (pro botao "Voltar"). */
    private final java.util.Deque<String> sceneTrail = new java.util.ArrayDeque<>();
    private Label currentSceneLabel;
    /** Ferramentas do mapa na ordem dos atalhos 1..9; e o "desliga tudo" (Esc/0). */
    private List<ToggleButton> toolHotkeys = List.of();
    private Runnable toolsOff = () -> { };
    /** Mestre: o diario inteiro (arquivo). Jogador: so o que o mestre revelou. */
    private final ObservableList<com.vttcaseiro.model.Handout> journalItems = FXCollections.observableArrayList();
    private final java.util.Map<String, com.vttcaseiro.model.Handout> receivedHandouts = new java.util.LinkedHashMap<>();
    private ListView<com.vttcaseiro.model.Handout> journalList;
    private VBox journalViewer;
    private List<javafx.scene.Node> gmOnlyJournalControls = List.of();
    private javafx.scene.layout.FlowPane macroRow;
    /** Lista de cenas em arvore (pastas do mestre). */
    private javafx.scene.control.TreeView<SceneEntry> mapListView;
    /** Uma linha da arvore: pasta (path = caminho dela) ou cena (path = pasta onde esta). */
    record SceneEntry(String name, String path, boolean folder) {
        @Override public String toString() { return name; }
    }
    private com.vttcaseiro.library.SceneFolders sceneFolders;
    /** "Mostrar rapidinho": a cena pra onde o botao Voltar leva. */
    private String sceneToReturnTo;
    private Button returnSceneButton;
    private Label classCountLabel;
    private Label compendiumCountLabel;
    private Label mobCountLabel;
    private ListView<CompendiumEntry> compendiumSearchListView;
    private CharacterSheet lastOpenedCharacter;
    /** Salvamento automatico da cena: 2 s depois da ultima mudanca. */
    private final javafx.animation.PauseTransition sceneAutosave =
            new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
    /** A cena que tem mudanca pendente (guardada aqui pra, ao trocar de cena,
     * gravar a ANTIGA antes -- nao a nova). */
    private SceneModel pendingSceneSave;
    /** Backup automatico da mesa (zip em backups/) a cada tanto tempo, numa thread so dele. */
    private javafx.animation.Timeline autoBackupTimer;
    private final java.util.concurrent.ExecutorService backupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-automatico");
        t.setDaemon(true);
        return t;
    });
    private Label backupStatus;
    static final int[] BACKUP_MINUTES = {0, 5, 10, 15, 30, 60};
    private Label sceneSaveStatus;
    /** Layout: barra lateral de navegacao | painel aberto | mapa | chat. */
    private final StackPane mapHolder = new StackPane();
    private SplitPane mainSplit;
    private VBox sidePanel;
    private Label sidePanelTitle;
    private final StackPane sidePanelBody = new StackPane();
    private final java.util.Map<String, javafx.scene.Node> panelContents = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, ToggleButton> railButtons = new java.util.LinkedHashMap<>();
    private String openPanelName;
    private double sidePanelDivider = 0.25;
    private VBox chatColumn;
    /** "Escondida" ao lado dos botoes rapidos de dado do chat. */
    private CheckBox hiddenQuickRolls;
    /** Janelas de ficha abertas agora, por id da ficha -- abrir de novo so
     * traz a janela pra frente, e mudancas de fora (dano no token, ficha
     * vinda da rede) atualizam a janela certa. */
    private final java.util.Map<String, CharacterSheetView> openSheetViews = new java.util.HashMap<>();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Theme.install(); // caixas de dialogo e menus no tema escuro tambem
        new MesaSelectorView(stage, this::onMesaChosen, this::onJoinOnline).show();
    }

    /** Entrar na mesa de outra pessoa direto da tela inicial: abre a pasta local dessa mesa e conecta. */
    private void onJoinOnline(MesaRepository mesa, MesaSelectorView.JoinRequest req) {
        onMesaChosen(mesa);
        networkService.connectThroughRelay(req.relayCode());
        onConnect(req.playerName(), req.host(), req.port(), req.tablePassword(), req.asGm(), req.masterPassword(), req.tls());
    }

    private void onMesaChosen(MesaRepository mesa) {
        Theme.install();
        this.mesaRepository = mesa;
        this.mesaService = new MesaService(mesa);
        this.characterService = new CharacterService(mesa);
        this.mobService = new MobService(mesa);
        this.mobImportService = new MobImportService(mesa, mobService);
        this.partyService = new PartyService(mesa);
        this.imageAssetService = new ImageAssetService(mesa);
        this.compendiumService = new CompendiumService(mesa);
        this.compendiumImportService = new CompendiumImportService(compendiumService);
        this.sceneService = new SceneService(new SceneModel());
        this.classImportService = new ClassImportService(mesa);
        syncOfficialClasses();
        this.table = new TableService(sceneService, characterService, tablePort);
        this.table.setMobLookup(id -> mobService.findById(id).orElse(null));
        this.macroStore = new com.vttcaseiro.library.MacroStore(mesa.getRoot());
        this.handoutStore = new com.vttcaseiro.library.HandoutStore(mesa.getRoot());
        this.tableStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "tabelas.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.RandomTable>>() { }.getType());
        if (!tableStore.exists()) tableStore.save(new java.util.ArrayList<>(com.vttcaseiro.model.RandomTable.samples()));
        try {
            this.audioStore = new com.vttcaseiro.library.AudioAssetStore(mesa.getRoot());
        } catch (IOException ex) {
            this.audioStore = null;
        }
        this.soundIndex = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "sons.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.SoundEntry>>() { }.getType());
        this.sceneQueueStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "fila-cenas.json",
                new com.google.gson.reflect.TypeToken<List<String>>() { }.getType());
        buildMainScene();
        runBackup("abertura", false); // copia de como a mesa estava antes desta sessao
        restartAutoBackupTimer();
    }

    private void buildMainScene() {
        sceneAutosave.setOnFinished(e -> flushSceneAutosave());
        mapView = new MapView(sceneService.getScene(), imageAssetService);
        wireSceneServiceListener();
        wireTokenMoveListener();
        wireCharacterDropHandler();

        root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildMainSplit());
        root.setLeft(buildNavRail());

        Scene scene = new Scene(root, 1366, 860);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        // Ctrl+Z desfaz a ultima acao no mapa (campos de texto continuam
        // com o proprio Ctrl+Z, que eles consomem antes deste atalho).
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onMapKey);
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Z,
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN), this::onUndo);
        stage.setTitle("Customizr - " + mesaService.getName());
        com.vttcaseiro.view.Icons.applyTo(stage);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            // Fechar o programa grava tudo que estava pendente: a cena e as
            // fichas abertas (fechar a janela da ficha dispara o save dela).
            flushSceneAutosave();
            if (autoBackupTimer != null) autoBackupTimer.stop();
            for (CharacterSheetView view : new ArrayList<>(openSheetViews.values())) {
                view.close();
            }
            networkService.disconnect();
        });

        // Historico da sessao anterior volta pro chat; dai em diante toda
        // mensagem nova e guardada no chat-log.jsonl da mesa.
        com.vttcaseiro.library.ChatLogStore chatLog = new com.vttcaseiro.library.ChatLogStore(mesaRepository.getRoot());
        List<LogEntry> history = chatLog.loadLast(300);
        if (!history.isEmpty()) {
            logEntries.addAll(history);
            logEntries.add(LogEntry.system("--- fim do histórico (sessões anteriores) ---"));
        }
        logEntries.addListener((ListChangeListener<LogEntry>) change -> {
            while (change.next()) {
                if (change.wasAdded()) change.getAddedSubList().forEach(chatLog::append);
            }
        });
        logEntries.add(LogEntry.system("Mesa \"" + mesaService.getName()
                + "\" aberta. Crie uma cena ou uma ficha para começar."));
        if (compendiumService.getCompendium().getEntries().isEmpty()) {
            logEntries.add(LogEntry.system("Dica: compêndio vazio. Fichas > Abrir compêndio > \"Carregar conteúdo 2024 pronto\"."));
        }
    }

    /** Ponte entre SceneService (o "backend" da cena) e a tela: toda vez
     * que o servico avisa que algo mudou, a tela reage aqui -- em um
     * lugar so, em vez de cada metodo de acao (onNewScene, onAddToken...)
     * ter que lembrar de chamar mapView.rebuildTokens() manualmente. */
    private void wireSceneServiceListener() {
        sceneService.addListener(new SceneService.SceneChangeListener() {
            @Override
            public void onSceneReplaced(SceneModel newScene) {
                flushSceneAutosave(); // grava a cena que esta saindo, se tinha mudanca
                undo.clear();         // desfazer de outra cena nao faz sentido
                undoOrder.clear();
                mapView = new MapView(newScene, imageAssetService);
                wireTokenMoveListener();
                wireCharacterDropHandler();
                mapHolder.getChildren().setAll(buildMapArea());
                if (gridSizeField != null) {
                    gridSizeField.setText(String.valueOf((int) newScene.getGrid().getCellSize()));
                }
                if (gridTypeCombo != null) {
                    var oldHandler = gridTypeCombo.getOnAction();
                    gridTypeCombo.setOnAction(null); // so atualiza o que aparece, nao reenvia a cena
                    gridTypeCombo.setValue(newScene.getGrid().isHex() ? "Hexagonal"
                            : newScene.getGrid().isGridless() ? "Sem grade" : "Quadrada");
                    gridTypeCombo.setOnAction(oldHandler);
                }
                if (feetPerCellField != null) {
                    feetPerCellField.setText(formatFeet(newScene.getGrid().getFeetPerCell()));
                }
                refreshInitiativeList();
            }

            @Override
            public void onTokenAdded(TokenModel token) {
                mapView.rebuildTokens();
                refreshInitiativeList();
                scheduleSceneAutosave();
            }

            @Override
            public void onTokenMoved(TokenModel token) {
                mapView.rebuildTokens();
                scheduleSceneAutosave();
                if (table != null) table.revealFogByVision();   // so faz algo no mestre, com a opcao ligada
            }

            @Override
            public void onTokenRemoved(TokenModel token) {
                mapView.rebuildTokens();
                refreshInitiativeList();
                scheduleSceneAutosave();
            }

            @Override
            public void onGridChanged() {
                mapView.redrawGrid();
                mapView.rebuildTokens();
                scheduleSceneAutosave();
                mapSizeRefresh.run();
            }

            @Override
            public void onTokenUpdated(TokenModel token) {
                mapView.rebuildTokens();
                refreshInitiativeList();
                scheduleSceneAutosave();
            }

            @Override
            public void onEncounterChanged() {
                mapView.rebuildTokens();
                refreshInitiativeList();
                scheduleSceneAutosave();
            }

            @Override
            public void onFogChanged() {
                mapView.redrawFog();
                scheduleSceneAutosave();
            }

            @Override
            public void onDrawingsChanged() {
                mapView.redrawDrawings();
                scheduleSceneAutosave();
            }

            @Override
            public void onWallsChanged() {
                mapView.redrawWalls();
                scheduleSceneAutosave();
                if (table != null) table.revealFogByVision();   // porta aberta mostra o outro lado
            }

            @Override
            public void onPinsChanged() {
                mapView.redrawPins();
                scheduleSceneAutosave();
            }

            @Override
            public void onObjectsChanged() {
                mapView.redrawObjects();
                scheduleSceneAutosave();
            }
        });
    }

    private void wireCharacterDropHandler() {
        mapView.setCharacterDropHandler((characterId, x, y) -> {
            Optional<CharacterSheet> maybeSheet = characterService.findOpen(characterId);
            if (maybeSheet.isEmpty()) {
                showError("Essa ficha não está aberta nesta sessão. Abra ela "
                        + "na aba Fichas (clique duplo na lista) antes de arrastar.");
                return;
            }
            CharacterSheet sheet = maybeSheet.get();
            TokenModel token = com.vttcaseiro.service.TokenLink.tokenFromSheet(sheet);
            token.setX(x);
            token.setY(y);
            if (sheet.getPortraitImageAssetId() == null && sheet.getPortraitImagePath() != null) {
                // Retrato antigo (so caminho de arquivo): entra no asset store
                // uma vez e passa a viajar por id, nao embutido em cada mensagem.
                Optional<String> assetId = importIntoAssetStoreOrWarn(new File(sheet.getPortraitImagePath()));
                if (assetId.isPresent()) {
                    sheet.setPortraitImageAssetId(assetId.get());
                    token.setImageAssetId(assetId.get());
                } else {
                    token.setImageData(encodeImageOrWarn(sheet.getPortraitImagePath()));
                }
            }
            if (networkService.isConnected()) {
                networkService.sendAddToken(token);
            } else {
                sceneService.addToken(token);
                logEntries.add(LogEntry.system("Token criado a partir da ficha: " + sheet.getName()));
            }
        });
    }

    /** So avisa a rede quando o jogador termina de arrastar o proprio
     * token -- o modelo e a tela ja foram atualizados direto pelo
     * TokenNode (arrasto responsivo nao pode esperar ida-e-volta de
     * rede), entao nao faz sentido tambem notificar o SceneChangeListener
     * aqui (isso so re-desenharia a camada de tokens a toa). */
    private void wireTokenMoveListener() {
        mapView.setTokenMoveListener(token -> {
            networkService.sendMoveToken(token.getId(), token.getX(), token.getY());
            scheduleSceneAutosave();
        });
        mapView.setTokenMovedFromListener((token, old) -> {
            if (undoing) return;
            TokenModel before = com.vttcaseiro.service.UndoService.copy(token);
            before.setX(old[0]);
            before.setY(old[1]);
            undo.record("Mover " + token.getName(), before, token);
        });
        mapView.setTokenActions(tokenActions);
        mapView.setFogTool(fogToolMode, table::editFog);
        mapView.setSpeedProvider(this::speedFeetOf);
        mapView.setFogCellsHandler(table::editFogCells);
    }

    /** Deslocamento do token em pes (ficha: "9 m" / "30 ft"; monstro: deslocamento a pe). 0 = nao sabe. */
    private int speedFeetOf(TokenModel token) {
        try {
            if (token.getCharacterSheetId() != null) {
                Optional<CharacterSheet> sheet = characterService.findById(token.getCharacterSheetId());
                if (sheet.isPresent()) return speedTextToFeet(sheet.get().getSpeed());
            }
            if (token.getMobSheetId() != null) {
                Optional<com.vttcaseiro.mob.MobSheet> mob = mobService.findById(token.getMobSheetId());
                if (mob.isPresent()) return mob.get().getSpeedWalk();
            }
        } catch (RuntimeException ignored) {
            // sem ficha legivel: regua sem limite
        }
        return 0;
    }

    static int speedTextToFeet(String speed) {
        if (speed == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(speed);
        if (!m.find()) return 0;
        double n = Double.parseDouble(m.group(1).replace(',', '.'));
        String low = speed.toLowerCase();
        boolean feet = low.contains("ft") || low.contains("pe") || low.contains("pé") || (!low.contains("m") && n > 20);
        return (int) Math.round(feet ? n : n / 1.5 * 5);
    }

    /** Roda uma acao num token guardando o antes/depois pro desfazer. */
    private void recorded(String label, TokenModel token, Runnable action) {
        TokenModel before = undoing ? null : com.vttcaseiro.service.UndoService.copy(token);
        action.run();
        if (!undoing) {
            undo.record(label, before, token);
            pushUndo(new UndoAction(label, null)); // marcador: "a vez e do token"
        }
    }

    /** Desfazer de cena (parede, desenho): o que fazer pra voltar. undo == null = entrada de token. */
    private record UndoAction(String label, Runnable undo) {
    }

    /** Ordem de TUDO que da pra desfazer (tokens e cena), a mais recente em cima. */
    private final java.util.ArrayDeque<UndoAction> undoOrder = new java.util.ArrayDeque<>();

    private void pushUndo(UndoAction action) {
        if (undoing) return;
        undoOrder.push(action);
        while (undoOrder.size() > 200) undoOrder.removeLast();
    }

    /** Ctrl+Z: desfaz a ultima acao no mapa feita nesta maquina (token, parede ou desenho). */
    private void onUndo() {
        UndoAction top = undoOrder.poll();
        if (top != null && top.undo() != null) {
            undoing = true;
            try {
                top.undo().run();
                logEntries.add(LogEntry.system("Desfeito: " + top.label() + "."));
            } finally {
                undoing = false;
            }
            return;
        }
        onUndoToken();
    }

    private void onUndoToken() {
        Optional<com.vttcaseiro.service.UndoService.Entry> maybe = undo.pop();
        if (maybe.isEmpty()) {
            logEntries.add(LogEntry.system("Nada pra desfazer."));
            return;
        }
        var entry = maybe.get();
        undoing = true;
        try {
            Optional<TokenModel> current = sceneService.getScene().findToken(entry.tokenId());
            if (entry.before() == null) {
                current.ifPresent(table::removeToken);                 // foi adicionado -> tira
            } else if (current.isEmpty()) {
                addTokenToScene(com.vttcaseiro.service.UndoService.copy(entry.before())); // foi removido -> volta
            } else {
                TokenModel token = current.get();
                TokenModel before = entry.before();
                if (token.getX() != before.getX() || token.getY() != before.getY()) {
                    token.setX(before.getX());
                    token.setY(before.getY());
                    sceneService.moveToken(token.getId(), before.getX(), before.getY());
                    networkService.sendMoveToken(token.getId(), before.getX(), before.getY());
                }
                if (table.linkedSheet(token).isPresent()) {
                    // Ficha ligada: volta PV/condicoes/efeitos PELA FICHA (senao a
                    // ficha empurraria o valor novo de volta pro token).
                    table.setHp(token, before.getCurrentHp(), before.getMaxHp(), before.getTempHp());
                    table.setConditions(token, before.getConditions());
                    table.setEffects(token, before.getEffects());
                    if (!java.util.Objects.equals(token.getInitiative(), before.getInitiative())) {
                        token.setInitiative(before.getInitiative());
                        table.publishTokenUpdate(token);
                    }
                } else {
                    token.copyStateFrom(before);
                    table.publishTokenUpdate(token);
                }
            }
            logEntries.add(LogEntry.system("Desfeito: " + entry.label() + "."));
        } finally {
            undoing = false;
        }
    }

    // ==================================================================
    // Salvamento automatico da cena
    // ==================================================================

    /** Qualquer mudanca na cena (token, PV, condicao, grade...) agenda um
     * save. So quem manda na cena grava (mestre, ou offline) -- o jogador
     * recebe a cena do servidor e nao precisa de copia propria. */
    private void scheduleSceneAutosave() {
        if (!networkService.isGm()) return;
        pendingSceneSave = sceneService.getScene();
        if (sceneSaveStatus != null) sceneSaveStatus.setText("Cena: alterações não salvas...");
        sceneAutosave.playFromStart();
    }

    private void flushSceneAutosave() {
        sceneAutosave.stop();
        SceneModel scene = pendingSceneSave;
        pendingSceneSave = null;
        if (scene == null) return;
        String name = scene.getName();
        boolean unnamed = name == null || name.isBlank() || name.equals("Novo mapa");
        if (unnamed && scene.getTokens().isEmpty() && scene.getBackgroundImageAssetId() == null
                && scene.getBackgroundImageData() == null) {
            return; // tabuleiro vazio que ninguem usou -- nada pra guardar
        }
        String target = unnamed ? "Cena sem nome" : name;
        try {
            mesaService.saveMap(scene, target);
            if (unnamed) refreshMapList();
            if (sceneSaveStatus != null) {
                sceneSaveStatus.setText("Cena \"" + target + "\" salva automaticamente as "
                        + java.time.LocalTime.now().withNano(0));
            }
        } catch (IOException | RuntimeException ex) {
            logEntries.add(LogEntry.error("Salvamento automático da cena falhou: " + ex.getMessage()));
        }
    }

    // ==================================================================
    // Token <-> ficha <-> rede (regras no TableService; aqui so a ponte)
    // ==================================================================

    /** O que o TableService precisa da tela/rede. */
    private final TableService.Port tablePort = new TableService.Port() {
        @Override public boolean isConnected() { return networkService.isConnected(); }
        @Override public boolean isGm() { return networkService.isGm(); }
        @Override public String myPlayerId() { return networkService.getPlayerId(); }
        @Override public void sendUpdateToken(TokenModel token) { networkService.sendUpdateToken(token); }
        @Override public void sendRemoveToken(String tokenId) { networkService.sendRemoveToken(tokenId); }
        @Override public void sendCharacterSync(CharacterSheet sheet) { networkService.sendCharacterSync(sheet); }
        @Override public void sendEncounter(com.vttcaseiro.model.Encounter e) { networkService.sendEncounter(e); }
        @Override public void sendFog(com.vttcaseiro.model.Fog fog) { networkService.sendFog(fog); }
        @Override public void announce(String text) { Main.this.announce(text); }
        @Override public void sendDrawingAdd(com.vttcaseiro.model.Drawing d) { networkService.sendDrawingAdd(d); }
        @Override public void sendWalls(SceneModel s) { networkService.sendWalls(s); }
        @Override public void sendPins(SceneModel s) { networkService.sendPins(s); }
        @Override public void sendObjects(SceneModel s) { networkService.sendObjects(s); }
        @Override public void sendDoorToggle(String id) { networkService.sendDoorToggle(id); }
        @Override public void sendDrawingRemove(String id) { networkService.sendDrawingRemove(id); }
        @Override public void sendDrawingClear(String ownerId) { networkService.sendDrawingClear(ownerId); }
        @Override
        public void requestGm(com.vttcaseiro.model.GmRequest request) {
            networkService.sendGmRequest(request);
            logEntries.add(LogEntry.system("Pedido enviado ao mestre: " + request.getKind()
                    + (request.getAmount() > 0 ? " " + request.getAmount() : "")
                    + " em " + request.getTokenIds().size() + " alvo(s)."));
        }

        @Override
        public void announceGm(String text) {
            // Fica so na tela do mestre (offline: na tela de quem usa).
            logEntries.add(LogEntry.whisper(networkService.getPlayerName(), "mestre", text));
        }
        @Override public void error(String text) { showError(text); }

        @Override
        public void sheetChangedOutside(CharacterSheet sheet) {
            CharacterSheetView view = openSheetViews.get(sheet.getId());
            if (view != null) view.reload(sheet);
        }
    };

    /** Menu do token no mapa -> TableService. */
    private final MapView.TokenActions tokenActions = new MapView.TokenActions() {
        @Override
        public void openSheet(TokenModel token) {
            if (token.getCharacterSheetId() == null && token.getMobSheetId() != null) {
                // Ficha de monstro: so o mestre (jogador nao ve o bloco do monstro).
                if (!networkService.isGm()) return;
                mobService.findById(token.getMobSheetId()).ifPresentOrElse(
                        mob -> openMobView(mob, token),
                        () -> showError("A ficha deste monstro não esta nesta mesa."));
                return;
            }
            characterService.findById(token.getCharacterSheetId()).ifPresentOrElse(
                    Main.this::openSheetView,
                    () -> showError("A ficha ligada a este token não esta nesta mesa (ainda não sincronizou?)."));
        }

        @Override public void damage(TokenModel token, int amount) {
            recorded("Dano em " + token.getName(), token, () -> table.damage(token, amount));
        }
        @Override public void heal(TokenModel token, int amount) {
            recorded("Cura em " + token.getName(), token, () -> table.heal(token, amount));
        }
        @Override public void setHp(TokenModel t, Integer cur, Integer max, Integer temp) {
            recorded("PV de " + t.getName(), t, () -> table.setHp(t, cur, max, temp));
        }
        @Override public void setConditions(TokenModel token, List<String> c) {
            recorded("Condições de " + token.getName(), token, () -> table.setConditions(token, c));
        }
        @Override public void setEffects(TokenModel token, List<String> e) {
            recorded("Efeitos de " + token.getName(), token, () -> table.setEffects(token, e));
        }
        @Override public void rollInitiative(TokenModel token) {
            recorded("Iniciativa de " + token.getName(), token, () -> table.rollInitiative(token, true));
        }
        @Override public void remove(TokenModel token) {
            if (!undoing) undo.record("Remover " + token.getName(), token, null);
            table.removeToken(token);
        }
        @Override public boolean canControl(TokenModel token) { return table.canControl(token); }
        @Override public boolean canSeeHp(TokenModel token) { return table.canSeeHp(token); }
        @Override public boolean isGm() { return networkService.isGm(); }

        @Override
        public void addTimedEffect(TokenModel token, String name, int rounds) {
            table.addTimedEffect(token, name, rounds);
        }

        @Override
        public void setHidden(TokenModel token, boolean hidden) {
            recorded((hidden ? "Esconder " : "Revelar ") + token.getName(), token, () -> table.setHidden(token, hidden));
        }

        @Override
        public void setSize(TokenModel token, int cells) {
            recorded("Tamanho de " + token.getName(), token, () -> table.setSize(token, cells));
        }

        @Override
        public void rotate(TokenModel token, double degrees, boolean reset) {
            recorded("Girar " + token.getName(), token, () -> {
                if (reset) table.resetRotation(token);
                else table.rotateToken(token, degrees);
            });
        }

        @Override
        public void editToken(TokenModel token, String label, java.util.function.Consumer<TokenModel> change) {
            recorded(label, token, () -> table.editToken(token, change));
        }

        @Override
        public void changeImage(TokenModel token) {
            ChoiceDialog<String> from = new ChoiceDialog<>("Arquivo do computador", "Arquivo do computador", "Galeria da mesa");
            from.setTitle("Imagem de " + token.getName());
            from.setHeaderText(token.getCharacterSheetId() != null ? "Também troca o retrato da ficha." : null);
            from.setContentText("De onde vem a imagem?");
            Optional<String> choice = from.showAndWait();
            if (choice.isEmpty()) return;
            if (choice.get().startsWith("Galeria")) {
                new com.vttcaseiro.view.ImageGalleryView(imageAssetService, assetId -> recorded("Imagem de " + token.getName(),
                        token, () -> table.setTokenImage(token, assetId, null))).show();
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Escolher imagem de " + token.getName());
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            File file = chooser.showOpenDialog(stage);
            if (file == null) return;
            importIntoAssetStoreOrWarn(file).ifPresent(assetId -> recorded("Imagem de " + token.getName(), token,
                    () -> table.setTokenImage(token, assetId, file.getAbsolutePath())));
        }

        @Override
        public void setVision(TokenModel token, int visionFeet, int lightFeet) {
            recorded("Visão de " + token.getName(), token, () -> table.setTokenVision(token, visionFeet, lightFeet));
        }

        @Override
        public void notice(String text) {
            logEntries.add(LogEntry.system(text));
        }

        @Override
        public void giveControl(TokenModel token) {
            java.util.Map<String, String> players = networkService.getPlayerIdsByName();
            if (!networkService.isConnected() || players.isEmpty()) {
                showError("Nenhum jogador conectado pra receber o controle (só funciona numa mesa em rede).");
                return;
            }
            List<String> names = new ArrayList<>(players.keySet());
            names.add(0, "(ninguém -- só o mestre)");
            ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(Math.min(1, names.size() - 1)), names);
            dialog.setTitle("Dar controle de " + token.getName());
            dialog.setHeaderText(null);
            dialog.setContentText("Quem controla este token (e a ficha ligada)?");
            dialog.showAndWait().ifPresent(name -> table.giveControl(token, players.get(name),
                    players.containsKey(name) ? name : null));
        }
    };

    /** Ficha de monstro com rolagens. {@code token} (pode ser null, aberta
     * pela lista) da o nome e as condicoes que mudam as rolagens. */
    private void openMobView(MobSheet mob, TokenModel token) {
        String name = token != null && token.getName() != null ? token.getName() : mob.getName();
        String mobKey = "mob:" + (token != null ? token.getId() : mob.getId());
        MobSheetView.Actions mobActions = new MobSheetView.Actions() {
            @Override
            public void rollD20(String label, int modifier, com.vttcaseiro.character.RollKind kind,
                                com.vttcaseiro.character.Ability ability, com.vttcaseiro.character.RollMode chosen,
                                boolean hidden) {
                var adj = com.vttcaseiro.character.CombatRules.rollAdjustment(
                        token == null ? List.of() : token.getConditions(), 0, kind, ability, chosen,
                        token == null ? List.of() : token.getEffects(), com.vttcaseiro.character.CombatRules::rollDice);
                rollD20WithAdjustment(label, modifier, adj, hidden,
                        kind == com.vttcaseiro.character.RollKind.ATTACK ? resolveAgainstTargets(mobKey, name) : null);
            }

            @Override
            public void followUp(String attackLabel, String damageLabel, String expression) {
                registerFollowUp(attackLabel, damageLabel, expression, this::roll);
            }

            @Override
            public void attack(String attackLabel, int bonus, String damageLabel, String damageExpression,
                               com.vttcaseiro.character.RollMode chosen, boolean hidden) {
                var adj = com.vttcaseiro.character.CombatRules.rollAdjustment(
                        token == null ? List.of() : token.getConditions(), 0, com.vttcaseiro.character.RollKind.ATTACK,
                        null, chosen, token == null ? List.of() : token.getEffects(), com.vttcaseiro.character.CombatRules::rollDice);
                java.util.function.Consumer<RollResult> againstTargets = resolveAgainstTargets(mobKey, name);
                rollD20WithAdjustment(attackLabel, bonus, adj, hidden, r -> {
                    if (againstTargets != null) againstTargets.accept(r);
                    if (r.isCriticalFail()) {
                        announce(name + ": falha crítica -- sem dano.");
                        return;
                    }
                    if (damageExpression == null) return;
                    if (r.isCriticalHit()) {
                        roll(damageLabel.replace("Dano:", "Dano CRÍTICO:"), DiceRoller.doubleDice(damageExpression), hidden);
                    } else {
                        roll(damageLabel, damageExpression, hidden);
                    }
                });
            }

            @Override
            public void rollInitiative() {
                if (token != null) tokenActions.rollInitiative(token);
                else rollD20(name + " - Iniciativa", MobSheet.abilityModifier(mob.getDexterity()),
                        com.vttcaseiro.character.RollKind.INITIATIVE, com.vttcaseiro.character.Ability.DEXTERITY,
                        com.vttcaseiro.character.RollMode.NORMAL, true);
            }

            @Override
            public String hpText() {
                if (token == null || token.getCurrentHp() == null) return null;
                return token.getCurrentHp() + (token.getMaxHp() == null ? "" : " / " + token.getMaxHp())
                        + (token.getTempHp() == null ? "" : " (+" + token.getTempHp() + ")");
            }

            @Override
            public void changeImage() {
                if (token != null) tokenActions.changeImage(token);
                else showError("Abra a ficha pelo token no mapa pra trocar a imagem dele.");
            }

            @Override
            public void roll(String label, String expression, boolean hidden) {
                rollFromSource(mobKey, label, withDamageBonus(label, expression,
                        token == null ? List.of() : token.getEffects()), hidden);
            }

            @Override
            public void chat(String text) {
                announce(text);
            }
        };
        new MobSheetView(mob, name, mobActions, trait -> onMobTraitClicked(mob, trait)).show();
    }

    /** d20 com vantagem/desvantagem/exaustao ja decididas pelas regras. */
    private void rollD20WithAdjustment(String baseLabel, int modifier, com.vttcaseiro.character.RollAdjustment adj,
                                       boolean hidden) {
        rollD20WithAdjustment(baseLabel, modifier, adj, hidden, null);
    }

    /** Rolagens que esperam o resultado (ataque contra alvos, dano nos acertados).
     * Pela rede quem rola e o servidor: guarda o "depois" ate a rolagem voltar. */
    private final java.util.Map<String, java.util.ArrayDeque<java.util.function.Consumer<RollResult>>> pendingRolls =
            new java.util.HashMap<>();

    private void expectRoll(String label, java.util.function.Consumer<RollResult> after) {
        if (after != null) pendingRolls.computeIfAbsent(label, k -> new java.util.ArrayDeque<>()).add(after);
    }

    private void rollD20WithAdjustment(String baseLabel, int modifier, com.vttcaseiro.character.RollAdjustment adj,
                                       boolean hidden, java.util.function.Consumer<RollResult> after) {
        int finalMod = modifier + adj.modifierDelta();
        String modeText = adj.mode() == com.vttcaseiro.character.RollMode.NORMAL ? "" : " (" + adj.mode().labelPt() + ")";
        String title = baseLabel + modeText + adj.notesText() + (adj.autoFail() ? " -- FALHA AUTOMÁTICA" : "");
        String mode = switch (adj.mode()) {
            case ADVANTAGE -> "advantage";
            case DISADVANTAGE -> "disadvantage";
            case NORMAL -> "normal";
        };
        if (networkService.isConnected()) {
            expectRoll(title, after);
            networkService.sendRoll(title, d20Expression(finalMod), mode, finalMod, hidden);
            return;
        }
        RollResult result = switch (adj.mode()) {
            case ADVANTAGE -> DiceRoller.rollWithAdvantage(finalMod);
            case DISADVANTAGE -> DiceRoller.rollWithDisadvantage(finalMod);
            case NORMAL -> rollOrZero(d20Expression(finalMod));
        };
        addLocalRoll(title, result, hidden);
        if (after != null) after.accept(result);
    }

    /** Mensagem pro chat de todo mundo (ou so local, offline). */
    private void announce(String text) {
        if (networkService.isConnected()) {
            networkService.sendChat(text);
        } else {
            logEntries.add(LogEntry.chat(networkService.getPlayerName(), com.vttcaseiro.dice.InlineRolls.expand(text)));
        }
    }

    private static RollResult rollOrZero(String expression) {
        try {
            return DiceRoller.roll(expression);
        } catch (DiceException ex) {
            return new RollResult(0, expression + " inválido");
        }
    }

    /** Abre a janela da ficha (ou traz pra frente se ja estiver aberta). */
    private void openSheetView(CharacterSheet sheet) {
        lastOpenedCharacter = sheet;
        CharacterSheetView existing = openSheetViews.get(sheet.getId());
        if (existing != null) {
            if (existing.getSheet() != sheet) existing.reload(sheet);
            existing.toFront();
            return;
        }
        CharacterSheetView view = new CharacterSheetView(sheet, sheetActions, imageAssetService);
        view.setOnClosed(() -> openSheetViews.remove(sheet.getId()));
        openSheetViews.put(sheet.getId(), view);
        view.show();
    }

    // ==================================================================
    // Construcao da tela
    // ==================================================================

    private HBox buildTopBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label brand = new Label("Customizr", com.vttcaseiro.view.Icons.logo(24));
        brand.setGraphicTextGap(8);
        brand.getStyleClass().add("top-bar-brand");
        Label sep = new Label("/");
        sep.getStyleClass().add("top-bar-sep");
        Label title = new Label(mesaService.getName());
        title.getStyleClass().add("top-bar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button hint = new Button("Atalhos (F1)");
        hint.getStyleClass().add("button-flat");
        hint.setStyle("-fx-font-size: 11px;");
        hint.setOnAction(e -> showShortcuts());
        hint.setTooltip(new Tooltip("Alt+clique = ping, Ctrl+Z desfaz, Shift+arrastar = selecionar varios..."));

        netStatusLabel = new Label();
        netStatusLabel.textProperty().bind(networkService.statusProperty());
        netStatusLabel.getStyleClass().add("status-pill");
        Runnable paintStatus = () -> {
            String st = networkService.statusProperty().get();
            boolean online = st != null && !st.startsWith("Offline");
            netStatusLabel.getStyleClass().remove("status-pill-online");
            if (online) netStatusLabel.getStyleClass().add("status-pill-online");
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(paintStatus));
        paintStatus.run();

        netStatusLabel.setCursor(javafx.scene.Cursor.HAND);
        netStatusLabel.setTooltip(new Tooltip("Hospedar, conectar e backup"));
        netStatusLabel.setOnMouseClicked(e -> openPanel("Rede"));
        hint.setText("?");
        hint.setTooltip(new Tooltip("Atalhos (F1)"));
        returnSceneButton = new Button();
        returnSceneButton.getStyleClass().add("button-primary");
        returnSceneButton.setTooltip(new Tooltip("Volta todo mundo pra cena de antes do \"Mostrar rapidinho\""));
        returnSceneButton.setOnAction(e -> {
            String back = sceneToReturnTo;
            sceneToReturnTo = null;
            refreshReturnButton();
            if (back != null) onOpenMapFromMesa(back);
        });
        refreshReturnButton();
        Button gear = new Button("⚙");
        gear.getStyleClass().add("button-flat");
        gear.setStyle("-fx-font-size: 15px;");
        gear.setTooltip(new Tooltip("Configurações da mesa: backup e cópias automáticas"));
        gear.setOnAction(e -> showMesaSettings());
        bar.getChildren().addAll(brand, sep, title, spacer, returnSceneButton, hint, gear, netStatusLabel);
        return bar;
    }

    // ==================================================================
    // Pins: pontos de informacao e atalhos pra outras cenas
    // ==================================================================

    // ==================================================================
    // Objetos soltos no mapa
    // ==================================================================

    private void onAddObjectFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Imagem do objeto");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null) return;
        for (File f : files) {
            Optional<String> asset = importIntoAssetStoreOrWarn(f);
            asset.ifPresent(this::addObjectFromAsset);
        }
    }

    /**
     * Imagem no efeito de area: cobre a area desenhada (circulo, cone, linha, cubo) com uma imagem, girada na direcao
     * do cone/linha. Vira um objeto do mapa (sincroniza, anima GIF, apaga/move pelo modo Editar objetos).
     */
    private void onEffectImage() {
        Optional<double[]> box = mapView.effectPlacement();
        if (box.isEmpty()) {
            showError("Desenhe a área primeiro: ligue \"Área\", escolha a forma e arraste no mapa.");
            return;
        }
        if (!networkService.isGm()) {
            showError("Só o mestre põe imagem nos efeitos (ela vira um objeto do mapa).");
            return;
        }
        String shape = mapView.lastTemplateShape();
        ChoiceDialog<String> from = new ChoiceDialog<>("Arquivo do computador", "Arquivo do computador", "Galeria da mesa");
        from.setTitle("Imagem no efeito (" + shape + ")");
        from.setHeaderText("JPG, JPEG, PNG ou GIF (animado continua animado).");
        from.setContentText("De onde vem a imagem?");
        Optional<String> choice = from.showAndWait();
        if (choice.isEmpty()) return;
        java.util.function.Consumer<String> place = assetId -> {
            double[] b = box.get();
            com.vttcaseiro.model.MapObject o = new com.vttcaseiro.model.MapObject(assetId, b[0], b[1], b[2], b[3]);
            o.setRotation(b[4]);
            o.setName("Efeito: " + shape);
            if (table.addObject(o)) {
                pushUndo(new UndoAction("Efeito", () -> table.removeObject(o)));
                mapView.clearTemplate();
            }
        };
        if (choice.get().startsWith("Galeria")) {
            new com.vttcaseiro.view.ImageGalleryView(imageAssetService, place::accept).show();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Imagem do efeito");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = chooser.showOpenDialog(stage);
        if (file != null) importIntoAssetStoreOrWarn(file).ifPresent(place);
    }

    /**
     * Dialogo da luz fixa: nome e fonte (presets do Livro do Jogador 2024) ou luz plena + penumbra na mao.
     * Devolve false se cancelou.
     */
    private boolean lightDialog(com.vttcaseiro.model.MapLight light, boolean isNew) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(isNew ? "Nova luz no mapa" : "Luz: " + light.getName());
        dialog.setHeaderText("Luz plena e penumbra em pés (regras do 5.5e).");
        TextField name = new TextField(light.getName());
        javafx.scene.control.Spinner<Integer> bright = new javafx.scene.control.Spinner<>(0, 1000, light.getBrightFeet(), 5);
        javafx.scene.control.Spinner<Integer> dim = new javafx.scene.control.Spinner<>(0, 1000, light.getDimFeet(), 5);
        bright.setEditable(true);
        dim.setEditable(true);
        ComboBox<String> preset = new ComboBox<>();
        Object[][] presets = {{"Vela", 5, 5}, {"Tocha", 20, 20}, {"Lâmpada", 15, 30}, {"Lanterna coberta", 30, 30},
                {"Fogueira", 20, 20}, {"Braseiro", 10, 10}, {"Lustre / candelabro", 20, 20}, {"Chama Contínua", 20, 20},
                {"Luz do Dia (magia)", 60, 60}, {"Vitral / claraboia", 30, 30}};
        preset.getItems().add("(escolher)");
        for (Object[] o : presets) preset.getItems().add(o[0] + " (" + o[1] + "/" + o[2] + ")");
        preset.setValue("(escolher)");
        preset.setOnAction(e -> {
            int i = preset.getSelectionModel().getSelectedIndex() - 1;
            if (i < 0) return;
            name.setText((String) presets[i][0]);
            bright.getValueFactory().setValue((Integer) presets[i][1]);
            dim.getValueFactory().setValue((Integer) presets[i][2]);
        });
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Fonte"), preset);
        grid.addRow(1, new Label("Nome"), name);
        grid.addRow(2, new Label("Luz plena (pés)"), bright);
        grid.addRow(3, new Label("Penumbra além dela (pés)"), dim);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return false;
        bright.increment(0);
        dim.increment(0);
        light.setName(name.getText());
        light.setBrightFeet(bright.getValue());
        light.setDimFeet(dim.getValue());
        return true;
    }

    /** Poe um objeto com essa imagem no centro da tela (lado maior = 2 casas) e liga o modo de edicao. */
    private void addObjectFromAsset(String assetId) {
        double cell = Math.max(10, sceneService.getScene().getGrid().getCellSize());
        double w = 2 * cell, h = 2 * cell;
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                    new java.io.ByteArrayInputStream(imageAssetService.readBytes(assetId)));
            if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) {
                double f = 2 * cell / Math.max(img.getWidth(), img.getHeight());
                w = img.getWidth() * f;
                h = img.getHeight() * f;
            }
        } catch (IOException ignored) {
            // fica 2x2 casas
        }
        javafx.geometry.Point2D p = mapView.suggestTokenPosition(0);
        com.vttcaseiro.model.MapObject o = new com.vttcaseiro.model.MapObject(assetId, p.getX(), p.getY(), w, h);
        if (table.addObject(o)) {
            pushUndo(new UndoAction("Objeto", () -> table.removeObject(o)));
            if (!objectEditMode) {
                objectEditMode = true;
                mapView.setObjectEditing(true);
                logEntries.add(LogEntry.system("Modo \"Editar objetos\" ligado: arraste o objeto, Ctrl+roda muda o "
                        + "tamanho, Shift+roda gira. Desligue em Objetos > Editar objetos quando terminar."));
            }
        }
    }

    private void onCreatePin(double x, double y) {
        com.vttcaseiro.model.MapPin pin = new com.vttcaseiro.model.MapPin(x, y, "", "cidade");
        if (!editPinDialog(pin, true)) return;
        if (table.addPin(pin)) {
            pushUndo(new UndoAction("Pin", () -> table.removePin(pin)));
            showPinCard(pin);
        }
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "cidade" -> "Cidade";
            case "capital" -> "Capital";
            case "vila" -> "Vila / aldeia";
            case "loja" -> "Loja";
            case "taverna" -> "Taverna / estalagem";
            case "templo" -> "Templo";
            case "masmorra" -> "Masmorra / covil";
            case "perigo" -> "Perigo";
            case "tesouro" -> "Tesouro";
            default -> "Informação";
        };
    }

    /** Dialogo de criar/editar pin. Devolve true se confirmou (o pin ja vem alterado). */
    private boolean editPinDialog(com.vttcaseiro.model.MapPin pin, boolean isNew) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(isNew ? "Novo pin" : "Editar pin");
        dialog.setHeaderText(isNew ? "Um ponto no mapa: clique nele pra ver as informações ou abrir outro mapa."
                : null);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        TextField title = new TextField(pin.getTitle());
        title.setPromptText("Ex.: Alta Torre, Forja do Anão, Covil do Dragão");
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList(com.vttcaseiro.model.MapPin.KINDS));
        kind.setValue(pin.getKind());
        kind.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String k, boolean empty) {
                super.updateItem(k, empty);
                setText(empty || k == null ? null : kindLabel(k));
                setGraphic(empty || k == null ? null : pinSwatch(k));
            }
        });
        kind.setButtonCell(kind.getCellFactory().call(null));
        javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(pin.getText());
        text.setPromptText("O que os jogadores (ou você) precisam saber: quem manda ali, o que vende, ganchos...");
        text.setWrapText(true);
        text.setPrefRowCount(6);
        CheckBox hidden = new CheckBox("Oculto: só o mestre vê (os jogadores nem recebem)");
        hidden.setSelected(pin.isHidden());

        ComboBox<String> link = new ComboBox<>();
        String none = "(nenhum: só informação)";
        link.getItems().add(none);
        String current = currentMapName != null ? currentMapName : sceneService.getScene().getName();
        for (String m : mesaService.listMaps()) if (!m.equals(current)) link.getItems().add(m);
        link.setValue(pin.getLinkedScene() != null && link.getItems().contains(pin.getLinkedScene())
                ? pin.getLinkedScene() : none);
        link.setMaxWidth(Double.MAX_VALUE);
        Button newScene = new Button("Nova cena com imagem...", com.vttcaseiro.view.Icons.icon("add", 13));
        newScene.setTooltip(new Tooltip("Escolha a imagem do mapa deste lugar (ex.: cidade exportada do Watabou em PNG)."
                + " Vira uma cena nova, já ligada a este pin."));
        newScene.setOnAction(e -> {
            String suggested = title.getText().isBlank() ? "Novo lugar" : title.getText().strip();
            String created = createSceneFromImage(suggested);
            if (created != null) {
                if (!link.getItems().contains(created)) link.getItems().add(created);
                link.setValue(created);
            }
        });
        Button generate = new Button("Gerar no Watabou", com.vttcaseiro.view.Icons.icon("open", 13));
        generate.setTooltip(new Tooltip("Abre o gerador (cidade, vila ou masmorra) no navegador com este nome. "
                + "La: Export > PNG; depois \"Nova cena com imagem...\" aqui."));
        Runnable refreshGen = () -> generate.setDisable(com.vttcaseiro.importer.GeneratorLinks
                .forPlace(title.getText(), kind.getValue(), 0) == null || title.getText().isBlank());
        title.textProperty().addListener((o, a, b) -> refreshGen.run());
        kind.valueProperty().addListener((o, a, b) -> refreshGen.run());
        refreshGen.run();
        generate.setOnAction(e -> {
            String url = pin.getGeneratorUrl() != null && title.getText().strip().equals(pin.getTitle())
                    ? pin.getGeneratorUrl()
                    : com.vttcaseiro.importer.GeneratorLinks.forPlace(title.getText(), kind.getValue(), 0);
            if (url != null) getHostServices().showDocument(url);
        });

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 4, 4, 4));
        grid.addRow(0, new Label("Nome"), title);
        grid.addRow(1, new Label("Tipo"), kind);
        grid.addRow(2, new Label("Texto"), text);
        grid.add(hidden, 1, 3);
        grid.addRow(4, new Label("Abre o mapa"), link);
        HBox linkButtons = new HBox(6, newScene, generate);
        grid.add(linkButtons, 1, 5);
        Label linkHint = new Label("Ligado a uma cena, o pin vira uma porta: o mestre clica e a mesa toda vai pra la "
                + "(com \"Voltar\" pra este mapa).");
        linkHint.getStyleClass().add("label-muted");
        linkHint.setWrapText(true);
        linkHint.setMaxWidth(420);
        grid.add(linkHint, 1, 6);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        c2.setPrefWidth(420);
        grid.getColumnConstraints().addAll(c1, c2);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(title::requestFocus);

        Optional<javafx.scene.control.ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != javafx.scene.control.ButtonType.OK) return false;
        String t = title.getText().strip();
        pin.setTitle(t.isEmpty() ? kindLabel(kind.getValue()) : t);
        pin.setKind(kind.getValue());
        pin.setText(text.getText());
        pin.setHidden(hidden.isSelected());
        pin.setLinkedScene(none.equals(link.getValue()) ? null : link.getValue());
        if (pin.getGeneratorUrl() == null) {
            pin.setGeneratorUrl(com.vttcaseiro.importer.GeneratorLinks.forPlace(pin.getTitle(), pin.getKind(), 0));
        }
        return true;
    }

    private javafx.scene.Node pinSwatch(String kind) {
        javafx.scene.shape.Circle c = new javafx.scene.shape.Circle(8,
                javafx.scene.paint.Color.web(com.vttcaseiro.view.Icons.pinColor(kind)));
        javafx.scene.shape.SVGPath g = com.vttcaseiro.view.Icons.pinPath(kind);
        g.setFill(javafx.scene.paint.Color.WHITE);
        g.setScaleX(0.45);
        g.setScaleY(0.45);
        return new StackPane(c, g);
    }

    /** Cria (e salva) uma cena nova a partir de uma imagem, SEM trocar a cena atual. Devolve o nome. */
    private String createSceneFromImage(String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Imagem do mapa deste lugar");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = chooser.showOpenDialog(stage);
        if (file == null) return null;
        Optional<String> name = askSceneName(suggestedName);
        if (name.isEmpty()) return null;
        if (mesaService.listMaps().contains(name.get())) {
            showError("Já existe uma cena chamada \"" + name.get() + "\".");
            return null;
        }
        try {
            com.vttcaseiro.importer.MapImageFit.Fitted img = com.vttcaseiro.importer.MapImageFit.fit(
                    java.nio.file.Files.readAllBytes(file.toPath()), file.getName());
            SceneModel scene = new SceneModel();
            scene.setBackgroundImageAssetId(mesaRepository.getImageAssetStore().store(img.bytes(), "mapa" + img.extension()));
            mesaService.saveMap(scene, name.get());
            refreshMapList();
            logEntries.add(LogEntry.system("Cena criada (sem abrir): " + name.get()));
            return name.get();
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível criar a cena: " + ex.getMessage());
            return null;
        }
    }

    /** Cartao de informacao do pin, por cima do mapa (canto direito). */
    private void showPinCard(com.vttcaseiro.model.MapPin pin) {
        if (mapStack == null) return;
        if (pinCard != null) mapStack.getChildren().remove(pinCard);
        boolean gm = networkService.isGm();

        javafx.scene.Node swatch = pinSwatch(pin.getKind());
        Label title = new Label(pin.getTitle());
        title.getStyleClass().add("pin-card-title");
        title.setWrapText(true);
        Label kind = new Label(kindLabel(pin.getKind()) + (pin.isHidden() ? "  -  oculto (só você vê)" : ""));
        kind.getStyleClass().add("label-muted");
        Button close = new Button("\u2715");
        close.getStyleClass().add("button-flat");
        close.setOnAction(e -> { mapStack.getChildren().remove(pinCard); pinCard = null; });
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(10, swatch, new VBox(1, title, kind), grow, close);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header);
        card.getStyleClass().add("pin-card");
        if (!pin.getText().isBlank()) {
            Label body = new Label(pin.getText());
            body.setWrapText(true);
            body.getStyleClass().add("pin-card-text");
            ScrollPane sp = new ScrollPane(body);
            sp.setFitToWidth(true);
            sp.setMaxHeight(260);
            sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            card.getChildren().add(sp);
        } else if (gm) {
            Label empty = new Label("Sem texto ainda. \"Editar\" pra escrever o que tem aqui.");
            empty.getStyleClass().add("label-muted");
            card.getChildren().add(empty);
        }
        javafx.scene.layout.FlowPane actions = new javafx.scene.layout.FlowPane(6, 6);
        if (pin.getLinkedScene() != null) {
            if (gm) {
                Button go = new Button("Abrir mapa: " + pin.getLinkedScene(), com.vttcaseiro.view.Icons.icon("map", 14));
                go.getStyleClass().add("button-primary");
                go.setOnAction(e -> openPinScene(pin));
                actions.getChildren().add(go);
            } else {
                Button ask = new Button("Pedir pra ir: " + pin.getLinkedScene());
                ask.getStyleClass().add("button-primary");
                ask.setOnAction(e -> onChatInput("/w mestre Quero ir para \"" + pin.getTitle() + "\" ("
                        + pin.getLinkedScene() + ")"));
                actions.getChildren().add(ask);
            }
        }
        if (gm) {
            Button ed = new Button("Editar", com.vttcaseiro.view.Icons.icon("sheet", 13));
            ed.setOnAction(e -> {
                if (editPinDialog(pin, false)) {
                    table.updatePin(pin);
                    showPinCard(pin);
                }
            });
            actions.getChildren().add(ed);
            if (pin.isHidden()) {
                Button reveal = new Button("Mostrar aos jogadores");
                reveal.setOnAction(e -> { pin.setHidden(false); table.updatePin(pin); showPinCard(pin); });
                actions.getChildren().add(reveal);
            }
            String url = pin.getGeneratorUrl();
            if (url != null && pin.getLinkedScene() == null) {
                Button gen = new Button("Gerar mapa no Watabou", com.vttcaseiro.view.Icons.icon("open", 13));
                gen.setTooltip(new Tooltip("Abre o gerador no navegador com este lugar. Exporte em PNG e use "
                        + "Editar > \"Nova cena com imagem...\" pra ligar o mapa a este pin."));
                gen.setOnAction(e -> getHostServices().showDocument(url));
                actions.getChildren().add(gen);
            }
        }
        if (!actions.getChildren().isEmpty()) card.getChildren().add(actions);
        card.setMaxSize(340, Region.USE_PREF_SIZE);
        card.setPrefWidth(340);
        pinCard = card;
        mapStack.getChildren().add(card);
        StackPane.setAlignment(card, Pos.TOP_RIGHT);
        StackPane.setMargin(card, new Insets(70, 16, 16, 16));
    }

    /** Mestre entra no mapa ligado ao pin (a mesa toda vai junto). */
    private void openPinScene(com.vttcaseiro.model.MapPin pin) {
        String target = pin.getLinkedScene();
        if (target == null) return;
        if (!mesaService.listMaps().contains(target)) {
            showError("A cena \"" + target + "\" não existe mais nesta mesa. Edite o pin e escolha outra.");
            return;
        }
        String here = currentMapName != null ? currentMapName : sceneService.getScene().getName();
        flushSceneAutosave();
        if (here != null && mesaService.listMaps().contains(here)) sceneTrail.push(here);
        onOpenMapFromMesa(target);
    }

    /** Cartao "Comece aqui" no meio do mapa enquanto a cena esta vazia (sem imagem, tokens nem paredes). */
    private javafx.scene.Node buildEmptyMapHint() {
        Label title = new Label("Comece aqui");
        title.getStyleClass().add("map-empty-title");
        Label text = new Label();
        text.getStyleClass().add("map-empty-text");
        text.setWrapText(true);
        text.setMaxWidth(380);
        text.setStyle("-fx-text-alignment: center;");
        Button newScene = new Button("Nova cena (imagem)...", com.vttcaseiro.view.Icons.icon("add", 14));
        newScene.getStyleClass().add("button-primary");
        newScene.setOnAction(e -> onNewScene());
        Button importMap = new Button("Importar mapa (Azgaar / Watabou)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importMap.setOnAction(e -> onImportGeneratedMap());
        HBox buttons = new HBox(8, newScene, importMap);
        buttons.setAlignment(Pos.CENTER);
        VBox card = new VBox(10, com.vttcaseiro.view.Icons.logo(56), title, text, buttons);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("map-empty-hint");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Runnable refresh = () -> {
            SceneModel sc = sceneService.getScene();
            boolean empty = sc.getBackgroundImageAssetId() == null && sc.getBackgroundImagePath() == null
                    && sc.getBackgroundImageData() == null && sc.getTokens().isEmpty() && sc.getWalls().isEmpty()
                    && sc.getDrawings().isEmpty();
            boolean gm = networkService.isGm();
            text.setText(gm
                    ? "Escolha uma imagem de mapa do seu computador, ou traga um mapa pronto do Azgaar (mundos) "
                    + "ou do Watabou (masmorras, cidades e vilas). Depois é só arrastar fichas e monstros pra cá."
                    : "Esperando o mestre abrir um mapa. Enquanto isso, abra sua ficha na aba Fichas.");
            buttons.setVisible(gm);
            buttons.setManaged(gm);
            card.setVisible(empty);
        };
        sceneService.addListener(new SceneService.SceneChangeListener() {
            @Override public void onSceneReplaced(SceneModel s) { Platform.runLater(refresh); }
            @Override public void onTokenAdded(TokenModel t) { Platform.runLater(refresh); }
            @Override public void onTokenRemoved(TokenModel t) { Platform.runLater(refresh); }
            @Override public void onWallsChanged() { Platform.runLater(refresh); }
            @Override public void onDrawingsChanged() { Platform.runLater(refresh); }
        });
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(refresh));
        refresh.run();
        return card;
    }

    private SplitPane buildMainSplit() {
        mapHolder.getChildren().setAll(buildMapArea());
        buildSidePanel();
        chatColumn = buildChatPanel();
        chatColumn.setMinWidth(260);
        chatColumn.setPrefWidth(330);
        mainSplit = new SplitPane();
        mainSplit.getStyleClass().add("main-split");
        mainSplit.getItems().addAll(sidePanel, mapHolder, chatColumn);
        SplitPane.setResizableWithParent(sidePanel, false);
        SplitPane.setResizableWithParent(chatColumn, false);
        mainSplit.setDividerPositions(sidePanelDivider, 0.76);
        openPanel("Mapa");
        return mainSplit;
    }

    /** Painel da esquerda: titulo + conteudo de uma secao por vez (as outras ficam escondidas, nao destruidas). */
    private void buildSidePanel() {
        String[] icons = {"map", "sheet", "party", "combat", "journal", "sound", "network"};
        Tab[] tabs = {buildMapTab(), buildCharacterTab(), buildPartyTab(), buildInitiativeTab(), buildJournalTab(),
                buildSoundTab(), buildNetworkTab()};
        for (Tab t : tabs) {
            javafx.scene.Node content = t.getContent();
            t.setContent(null);
            compactHints(content);
            groupIntoCards(content, t.getText());
            content.setVisible(false);
            panelContents.put(t.getText(), content);
            sidePanelBody.getChildren().add(content);
        }
        sidePanelTitle = new Label();
        sidePanelTitle.getStyleClass().add("side-panel-title");
        Button close = new Button("✕");
        close.getStyleClass().addAll("button-flat", "side-panel-close");
        close.setTooltip(new Tooltip("Fechar o painel (o mapa fica maior)"));
        close.setOnAction(e -> closePanel());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, sidePanelTitle, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("side-panel-header");
        VBox.setVgrow(sidePanelBody, Priority.ALWAYS);
        sidePanel = new VBox(header, sidePanelBody);
        sidePanel.getStyleClass().add("side-panel");
        sidePanel.setMinWidth(300);
        sidePanel.setPrefWidth(360);
        railIcons = icons;
        // ja com os cartoes montados: esconde o que e so do mestre, e de novo quando mudar o papel (conectou/hospedou)
        refreshInitiativeList();
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(this::refreshInitiativeList));
    }

    private String[] railIcons;

    /** Barra de navegacao da esquerda: um botao grande (icone + nome) por secao; clicar de novo fecha o painel. */
    private VBox buildNavRail() {
        VBox rail = new VBox(2);
        rail.getStyleClass().add("nav-rail");
        String[] tips = {"Cenas, mapas importados e grade", "Fichas de personagem, compêndio e monstros",
                "O grupo: PV, CA/Defesa e percepção de cada um", "Iniciativa, testes em grupo e descanso",
                "Textos e imagens pros jogadores, tabelas aleatórias", "Música e sons ambiente",
                "Hospedar ou entrar numa mesa, backup"};
        int i = 0;
        for (String name : panelContents.keySet()) {
            String label = "Party".equals(name) ? "Grupo" : name;
            ToggleButton b = new ToggleButton(label, com.vttcaseiro.view.Icons.icon(railIcons[i], 20));
            b.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
            b.getStyleClass().add("nav-rail-button");
            b.setTooltip(new Tooltip(tips[i]));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> togglePanel(name));
            railButtons.put(name, b);
            if ("Rede".equals(name)) {
                Region spacer = new Region();
                VBox.setVgrow(spacer, Priority.ALWAYS);
                rail.getChildren().add(spacer);
            }
            rail.getChildren().add(b);
            i++;
        }
        refreshRail();
        return rail;
    }

    private void togglePanel(String name) {
        if (name.equals(openPanelName) && mainSplit.getItems().contains(sidePanel)) closePanel();
        else openPanel(name);
    }

    /** Mostra a secao no painel da esquerda (abre o painel se estava fechado). */
    void openPanel(String name) {
        if (!panelContents.containsKey(name)) return;
        openPanelName = name;
        panelContents.forEach((k, v) -> v.setVisible(k.equals(name)));
        sidePanelTitle.setText("Party".equals(name) ? "Grupo" : name);
        if (!mainSplit.getItems().contains(sidePanel)) {
            double[] pos = mainSplit.getDividerPositions();
            double chatPos = pos.length > 0 ? pos[pos.length - 1] : 0.76;
            mainSplit.getItems().add(0, sidePanel);
            mainSplit.setDividerPositions(sidePanelDivider, chatPos);
        }
        refreshRail();
    }

    private void closePanel() {
        if (mainSplit.getItems().contains(sidePanel)) {
            double[] pos = mainSplit.getDividerPositions();
            if (pos.length > 1) sidePanelDivider = pos[0];
            double chatPos = pos.length > 1 ? pos[1] : 0.76;
            mainSplit.getItems().remove(sidePanel);
            mainSplit.setDividerPositions(chatPos);
        }
        openPanelName = null;
        refreshRail();
    }

    private void refreshRail() {
        railButtons.forEach((k, b) -> b.setSelected(k.equals(openPanelName)));
    }

    /** Altura de uma linha das listas dos paineis (pra calcular quanto a lista ocupa). */
    static final double LIST_ROW = 30;

    /**
     * Lista vazia ou curta encolhe (vazia: so a frase); com mais itens cresce ate a altura que tinha (depois rola).
     */
    static void fitListHeight(ListView<?> lv) {
        double max = lv.getPrefHeight() > 0 ? lv.getPrefHeight() : 200;
        if (lv.getPlaceholder() instanceof Label ph) {
            ph.setWrapText(true);
            ph.setPadding(new Insets(0, 8, 0, 8));
        }
        lv.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            int n = lv.getItems().size();
            return n == 0 ? 60.0 : Math.min(max, n * LIST_ROW + 8);
        }, lv.getItems()));
        lv.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }

    /** Mesmo pra arvore de cenas: conta as linhas abertas. */
    static void fitTreeHeight(javafx.scene.control.TreeView<?> tv) {
        double max = tv.getPrefHeight() > 0 ? tv.getPrefHeight() : 220;
        javafx.beans.value.ChangeListener<Object> resize = (o, a, b) -> {
            int n = Math.max(0, tv.getExpandedItemCount() - (tv.isShowRoot() ? 0 : 1));
            tv.setPrefHeight(n == 0 ? 40 : Math.min(max, n * LIST_ROW + 8));
        };
        tv.expandedItemCountProperty().addListener(resize);
        tv.rootProperty().addListener(resize);
        resize.changed(null, null, null);
        tv.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }

    /**
     * Cada secao do painel (um titulo laranja e o que vem ate o proximo) vira um cartao com fundo proprio, no lugar
     * das linhas divisorias. O titulo igual ao do painel (ex.: "Combate" dentro de Combate) some.
     */
    static void groupIntoCards(javafx.scene.Node content, String panelName) {
        VBox box = content instanceof ScrollPane sp && sp.getContent() instanceof VBox v ? v
                : content instanceof VBox v2 ? v2 : null;
        if (box == null) return;
        List<javafx.scene.Node> kids = new ArrayList<>(box.getChildren());
        List<VBox> cards = new ArrayList<>();
        VBox current = null;
        for (javafx.scene.Node n : kids) {
            if (n.getStyleClass().contains("section-divider")) continue;
            boolean title = n instanceof Label l && l.getStyleClass().contains("label-section");
            if (title || current == null) {
                current = new VBox(8);
                current.getStyleClass().add("panel-card");
                cards.add(current);
            }
            if (title && ((Label) n).getText().equalsIgnoreCase(panelName) && current.getChildren().isEmpty()) {
                continue; // repete o nome do painel
            }
            current.getChildren().add(n);
        }
        cards.removeIf(c -> c.getChildren().isEmpty());
        box.getChildren().setAll(cards);
        box.setSpacing(10);
    }

    /**
     * Explicacoes longas viram um "ⓘ Como funciona" discreto que abre/fecha o texto: o painel fica limpo e a ajuda
     * continua a um clique. Rotulos de estado (que mudam sozinhos) ficam como estao.
     */
    static void compactHints(javafx.scene.Node node) {
        if (node instanceof ScrollPane sp && sp.getContent() != null) {
            compactHints(sp.getContent());
            return;
        }
        if (node instanceof ListView<?> lv) {
            fitListHeight(lv);
            return;
        }
        if (node instanceof javafx.scene.control.TreeView<?> tv) {
            fitTreeHeight(tv);
            return;
        }
        if (!(node instanceof javafx.scene.layout.Pane pane)) return;
        List<javafx.scene.Node> kids = new ArrayList<>(pane.getChildren());
        for (javafx.scene.Node kid : kids) {
            if (kid instanceof Label l && l.getStyleClass().contains("label-muted") && !l.getStyleClass().contains("live-status")
                    && l.isWrapText() && l.getText() != null && l.getText().length() > 70 && pane instanceof VBox) {
                Label toggle = new Label("ⓘ  Como funciona");
                toggle.getStyleClass().add("hint-toggle");
                toggle.setCursor(javafx.scene.Cursor.HAND);
                l.setVisible(false);
                l.setManaged(false);
                toggle.setOnMouseClicked(e -> {
                    boolean show = !l.isVisible();
                    l.setVisible(show);
                    l.setManaged(show);
                    toggle.setText(show ? "ⓘ  Esconder ajuda" : "ⓘ  Como funciona");
                });
                pane.getChildren().add(pane.getChildren().indexOf(l), toggle);
            } else if (kid instanceof Label l && l.getStyleClass().contains("label-muted")) {
                l.setWrapText(true); // curto: quebra a linha em vez de cortar com "..."
            } else if (kid instanceof Button btn && btn.getMaxWidth() == Double.MAX_VALUE) {
                btn.setWrapText(true); // botao da largura do painel: texto longo quebra em vez de "..."
            } else if (kid instanceof CheckBox cb) {
                cb.setWrapText(true);
                cb.setMaxWidth(Double.MAX_VALUE);
            } else {
                compactHints(kid);
            }
        }
    }

    private StackPane buildMapArea() {
        mapView.setPingHandler(p -> networkService.sendMapPing(p.getX(), p.getY()));
        mapView.setWallHandler(new MapView.WallHandler() {
            @Override
            public void add(com.vttcaseiro.model.Wall w) {
                table.addWall(w);
                if (sceneService.getScene().findWall(w.getId()).isPresent()) {
                    pushUndo(new UndoAction(w.isDoor() ? "Porta" : "Parede", () -> table.removeWall(w)));
                }
            }

            @Override
            public void remove(com.vttcaseiro.model.Wall w) {
                table.removeWall(w);
                if (sceneService.getScene().findWall(w.getId()).isEmpty()) {
                    pushUndo(new UndoAction("Apagar parede", () -> table.addWall(w)));
                }
            }
            @Override
            public void toggleDoor(com.vttcaseiro.model.Wall w) {
                if (!networkService.isGm() && !mapView.isVisibleToMe(w.midX(), w.midY())) {
                    showError("Você não enxerga essa porta.");
                    return;
                }
                table.toggleDoor(w);
            }

            @Override
            public void editWall(com.vttcaseiro.model.Wall w, String label,
                                 java.util.function.Consumer<com.vttcaseiro.model.Wall> change) {
                table.editWall(w, change);
            }

            @Override
            public void placeLight(double x, double y) {
                com.vttcaseiro.model.MapLight light = new com.vttcaseiro.model.MapLight("Tocha", x, y, 20, 20);
                if (!lightDialog(light, true)) return;
                if (table.addLight(light)) pushUndo(new UndoAction("Luz", () -> table.removeLight(light)));
            }

            @Override
            public void editLight(com.vttcaseiro.model.MapLight light, String label,
                                  java.util.function.Consumer<com.vttcaseiro.model.MapLight> change) {
                table.editLight(light, change);
            }

            @Override
            public void editLightDialog(com.vttcaseiro.model.MapLight light) {
                com.vttcaseiro.model.MapLight copy = new com.vttcaseiro.model.MapLight(light.getName(), light.getX(), light.getY(),
                        light.getBrightFeet(), light.getDimFeet());
                if (!lightDialog(copy, false)) return;
                table.editLight(light, l -> {
                    l.setName(copy.getName());
                    l.setBrightFeet(copy.getBrightFeet());
                    l.setDimFeet(copy.getDimFeet());
                });
            }

            @Override
            public void removeLight(com.vttcaseiro.model.MapLight light) {
                table.removeLight(light);
                pushUndo(new UndoAction("Tirar luz", () -> table.addLight(light)));
            }
        });
        StackPane stack = new StackPane(mapView);
        VBox toolbar = buildFloatingToolbar();
        // Quebra em mais linhas so quando o mapa fica estreito.
        ((javafx.scene.layout.FlowPane) toolbar.getChildren().get(0)).prefWrapLengthProperty()
                .bind(stack.widthProperty().subtract(60));
        toolbar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        stack.getChildren().add(toolbar);
        StackPane.setAlignment(toolbar, Pos.TOP_LEFT);
        StackPane.setMargin(toolbar, new Insets(14));
        diceAnimator = new com.vttcaseiro.view.DiceAnimator();
        stack.getChildren().add(diceAnimator);
        StackPane.setAlignment(diceAnimator, Pos.BOTTOM_CENTER);
        StackPane.setMargin(diceAnimator, new Insets(0, 0, 40, 0));
        diceAnimator.setMaxHeight(110);
        javafx.scene.Node hint = buildEmptyMapHint();
        stack.getChildren().add(hint);
        StackPane.setAlignment(hint, Pos.CENTER);
        mapStack = stack;
        pinCard = null;
        mapView.setObjectHandler(new MapView.ObjectHandler() {
            @Override public void changed(com.vttcaseiro.model.MapObject o) { table.updateObject(o); }
            @Override public void remove(com.vttcaseiro.model.MapObject o) {
                table.removeObject(o);
                if (sceneService.getScene().findObject(o.getId()).isEmpty()) {
                    pushUndo(new UndoAction("Apagar objeto", () -> table.addObject(o)));
                }
            }
            @Override public void reorder(com.vttcaseiro.model.MapObject o, boolean toFront) { table.reorderObject(o, toFront); }
            @Override public void duplicate(com.vttcaseiro.model.MapObject o) {
                double c = sceneService.getScene().getGrid().getCellSize();
                com.vttcaseiro.model.MapObject copy = o.duplicate(c / 2, c / 2);
                if (table.addObject(copy)) pushUndo(new UndoAction("Duplicar objeto", () -> table.removeObject(copy)));
            }
        });
        mapView.setObjectEditing(objectEditMode && networkService.isGm());
        mapView.setPinHandler(new MapView.PinHandler() {
            @Override public void create(double x, double y) { onCreatePin(x, y); }
            @Override public void open(com.vttcaseiro.model.MapPin pin) { showPinCard(pin); }
            @Override public void moved(com.vttcaseiro.model.MapPin pin, double x, double y) { table.movePin(pin, x, y); }
            @Override public void edit(com.vttcaseiro.model.MapPin pin) { if (editPinDialog(pin, false)) table.updatePin(pin); }
            @Override public void remove(com.vttcaseiro.model.MapPin pin) {
                table.removePin(pin);
                if (sceneService.getScene().findPin(pin.getId()).isEmpty()) {
                    pushUndo(new UndoAction("Apagar pin", () -> table.addPin(pin)));
                }
            }
        });
        if (!sceneTrail.isEmpty() && networkService.isGm()) {
            Button back = new Button("Voltar para " + sceneTrail.peek(), com.vttcaseiro.view.Icons.icon("back", 14));
            back.getStyleClass().addAll("button-primary", "map-back-button");
            back.setOnAction(e -> {
                String prev = sceneTrail.pop();
                onOpenMapFromMesa(prev);
            });
            back.setTooltip(new Tooltip("Volta pro mapa de onde você veio pelo pin"));
            stack.getChildren().add(back);
            StackPane.setAlignment(back, Pos.BOTTOM_LEFT);
            StackPane.setMargin(back, new Insets(0, 0, 18, 18));
        }
        javafx.animation.PauseTransition fitLater = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
        fitLater.setOnFinished(ev -> {
            // mapa maior que a tela (mapa-mundo): comeca mostrando ele inteiro
            javafx.scene.image.Image bg = mapView.backgroundImage();
            if (bg != null && (mapView.mapWidth() > mapView.getWidth() * 1.3 || mapView.mapHeight() > mapView.getHeight() * 1.3)) {
                mapView.fitToView();
            }
        });
        fitLater.play();
        return stack;
    }

    private VBox buildFloatingToolbar() {
        javafx.scene.layout.FlowPane box = new javafx.scene.layout.FlowPane(6, 6);
        box.setAlignment(Pos.CENTER_LEFT);
        Button addTokenBtn = new Button("+ Token");
        addTokenBtn.getStyleClass().add("tool-button");
        addTokenBtn.setOnAction(e -> onAddToken());
        Button addTokenFromGalleryBtn = new Button("Galeria");
        addTokenFromGalleryBtn.getStyleClass().add("tool-button");
        addTokenFromGalleryBtn.setOnAction(e -> onAddTokenFromGallery());
        Button npcBtn = new Button("NPC");
        npcBtn.getStyleClass().add("tool-button");
        npcBtn.setOnAction(e -> onQuickNpc());
        npcBtn.setTooltip(new Tooltip("NPC rápido: nome, retrato, CA, PV e um ataque -- vira ficha de monstro e token"));
        Button undoBtn = new Button("Desfazer");
        undoBtn.getStyleClass().add("tool-button");
        undoBtn.setOnAction(e -> onUndo());
        Button centerBtn = new Button("Centralizar");
        centerBtn.getStyleClass().add("tool-button");
        centerBtn.setOnAction(e -> mapView.resetView());
        ToggleButton measureBtn = new ToggleButton("Medir");
        measureBtn.getStyleClass().add("tool-button");

        ComboBox<String> templateShapeCombo = new ComboBox<>(
                FXCollections.observableArrayList("Circulo", "Cone", "Linha", "Cubo"));
        templateShapeCombo.setValue("Circulo");
        ToggleButton templateBtn = new ToggleButton("Área");
        templateBtn.getStyleClass().add("tool-button");
        Button clearTemplateBtn = new Button("Limpar");
        clearTemplateBtn.getStyleClass().add("tool-button");

        // Medir e Area sao exclusivos -- os dois usam o mesmo
        // clique-e-arraste com o botao esquerdo no mapa vazio, ligar os
        // dois ao mesmo tempo so confundiria qual ferramenta responde.
        measureBtn.setOnAction(e -> {
            if (measureBtn.isSelected()) {
                templateBtn.setSelected(false);
            }
            mapView.setMeasuring(measureBtn.isSelected());
            mapView.setTemplating(templateBtn.isSelected(), templateShapeCombo.getValue());
        });
        templateBtn.setOnAction(e -> {
            if (templateBtn.isSelected()) {
                measureBtn.setSelected(false);
            }
            mapView.setMeasuring(measureBtn.isSelected());
            mapView.setTemplating(templateBtn.isSelected(), templateShapeCombo.getValue());
        });
        templateShapeCombo.setOnAction(e -> mapView.setTemplateShapeType(templateShapeCombo.getValue()));
        clearTemplateBtn.setOnAction(e -> mapView.clearTemplate());
        Button effectImageBtn = new Button("Imagem");
        effectImageBtn.getStyleClass().add("tool-button");
        effectImageBtn.setOnAction(e -> onEffectImage());

        // --- Neblina de guerra (mestre) ---
        Label fogLabel = new Label("NEBLINA");
        fogLabel.getStyleClass().add("tool-group-label");
        ToggleButton fogRevealBtn = new ToggleButton("Revelar");
        fogRevealBtn.getStyleClass().add("tool-button");
        ToggleButton fogCoverBtn = new ToggleButton("Cobrir");
        fogCoverBtn.getStyleClass().add("tool-button");
        Button fogAllBtn = new Button("Revelar tudo");
        fogAllBtn.getStyleClass().add("tool-button");
        Button fogNoneBtn = new Button("Cobrir tudo");
        fogNoneBtn.getStyleClass().add("tool-button");
        CheckBox fogOn = new CheckBox("Ligada");
        fogOn.setSelected(sceneService.getScene().getFog().isEnabled());
        Runnable applyTools = () -> {
            fogToolMode = fogRevealBtn.isSelected() ? Boolean.TRUE : fogCoverBtn.isSelected() ? Boolean.FALSE : null;
            mapView.setFogTool(fogToolMode, table::editFog);
            mapView.setMeasuring(measureBtn.isSelected());
            mapView.setTemplating(templateBtn.isSelected(), templateShapeCombo.getValue());
        };
        // As ferramentas de arrastar no mapa vazio sao exclusivas.
        fogRevealBtn.setOnAction(e -> {
            if (fogRevealBtn.isSelected()) { fogCoverBtn.setSelected(false); measureBtn.setSelected(false); templateBtn.setSelected(false); }
            applyTools.run();
        });
        fogCoverBtn.setOnAction(e -> {
            if (fogCoverBtn.isSelected()) { fogRevealBtn.setSelected(false); measureBtn.setSelected(false); templateBtn.setSelected(false); }
            applyTools.run();
        });
        measureBtn.addEventHandler(javafx.event.ActionEvent.ACTION, e -> {
            if (measureBtn.isSelected()) { fogRevealBtn.setSelected(false); fogCoverBtn.setSelected(false); applyTools.run(); }
        });
        templateBtn.addEventHandler(javafx.event.ActionEvent.ACTION, e -> {
            if (templateBtn.isSelected()) { fogRevealBtn.setSelected(false); fogCoverBtn.setSelected(false); applyTools.run(); }
        });
        fogAllBtn.setOnAction(e -> {
            if (sceneService.getScene().getGrid().isHex()) table.editFogCells(mapView.allHexCells(), true);
            else table.revealAllFog(mapView.gridCols(), mapView.gridRows());
        });
        fogNoneBtn.setOnAction(e -> table.coverAllFog());
        fogOn.setOnAction(e -> table.setFogEnabled(fogOn.isSelected()));
        sceneService.addListener(new SceneService.SceneChangeListener() {
            @Override public void onFogChanged() { fogOn.setSelected(sceneService.getScene().getFog().isEnabled()); }
            @Override public void onSceneReplaced(SceneModel s) { fogOn.setSelected(s.getFog().isEnabled()); }
        });
        HBox fogBox = toolGroup(fogLabel, fogOn, fogRevealBtn, fogCoverBtn, fogAllBtn, fogNoneBtn);
        // Jogador nao mexe na neblina: os controles somem pra quem nao e mestre.
        Runnable gmOnly = () -> {
            boolean gm = networkService.isGm(); // (a categoria "Neblina" some pra jogador)
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(gmOnly));
        gmOnly.run();

        // --- Desenho livre / texto / borracha (todos veem; cada um apaga os seus, o mestre apaga tudo) ---
        Label drawLabel = new Label("DESENHO");
        drawLabel.getStyleClass().add("tool-group-label");
        ToggleButton penBtn = new ToggleButton("Lápis");
        penBtn.getStyleClass().add("tool-button");
        ToggleButton textBtn = new ToggleButton("Texto");
        textBtn.getStyleClass().add("tool-button");
        ToggleButton eraserBtn = new ToggleButton("Borracha");
        eraserBtn.getStyleClass().add("tool-button");
        javafx.scene.control.ColorPicker colorPicker = new javafx.scene.control.ColorPicker(
                javafx.scene.paint.Color.web("#E2483F"));
        colorPicker.getStyleClass().addAll("button", "tool-color");
        colorPicker.setTooltip(new Tooltip("Cor do lápis e do texto"));
        Button clearDrawBtn = new Button("Limpar");
        clearDrawBtn.getStyleClass().add("tool-button");
        tip(penBtn, "Arraste no mapa para desenhar");
        tip(textBtn, "Clique no mapa para escrever um texto");
        tip(eraserBtn, "Clique num desenho para apagar (os seus; o mestre apaga qualquer um)");
        tip(clearDrawBtn, "Apaga os seus desenhos (o mestre apaga todos)");
        MapView.DrawHandler drawHandler = new MapView.DrawHandler() {
            @Override
            public void add(com.vttcaseiro.model.Drawing d) {
                table.addDrawing(d);
                if (sceneService.getScene().findDrawing(d.getId()).isPresent()) {
                    pushUndo(new UndoAction("Desenho", () -> table.removeDrawing(d)));
                }
            }

            @Override
            public void remove(com.vttcaseiro.model.Drawing d) {
                table.removeDrawing(d);
                if (sceneService.getScene().findDrawing(d.getId()).isEmpty()) {
                    pushUndo(new UndoAction("Apagar desenho", () -> table.addDrawing(d)));
                }
            }
        };
        Runnable applyDraw = () -> mapView.setDrawTool(penBtn.isSelected() ? MapView.TOOL_PEN
                : textBtn.isSelected() ? MapView.TOOL_TEXT : eraserBtn.isSelected() ? MapView.TOOL_ERASER : null,
                drawHandler);
        List<ToggleButton> drawToggles = List.of(penBtn, textBtn, eraserBtn);
        for (ToggleButton t : drawToggles) {
            t.setOnAction(e -> {
                if (t.isSelected()) {
                    for (ToggleButton o : drawToggles) if (o != t) o.setSelected(false);
                    measureBtn.setSelected(false);
                    templateBtn.setSelected(false);
                    fogRevealBtn.setSelected(false);
                    fogCoverBtn.setSelected(false);
                    applyTools.run();
                }
                applyDraw.run();
            });
        }
        for (ToggleButton other : List.of(measureBtn, templateBtn, fogRevealBtn, fogCoverBtn)) {
            other.addEventHandler(javafx.event.ActionEvent.ACTION, e -> {
                if (other.isSelected()) {
                    drawToggles.forEach(t -> t.setSelected(false));
                    applyDraw.run();
                }
            });
        }
        colorPicker.setOnAction(e -> {
            javafx.scene.paint.Color c = colorPicker.getValue();
            mapView.setDrawColor(String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                    (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255)));
        });
        clearDrawBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, networkService.isGm()
                    ? "Apagar TODOS os desenhos desta cena?" : "Apagar os seus desenhos desta cena?");
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
                String me = networkService.getPlayerId();
                List<com.vttcaseiro.model.Drawing> gone = new java.util.ArrayList<>();
                for (com.vttcaseiro.model.Drawing d : sceneService.getScene().getDrawings()) {
                    if (networkService.isGm() || java.util.Objects.equals(d.getOwnerId(), me)) gone.add(d);
                }
                if (table.clearDrawings() > 0) {
                    pushUndo(new UndoAction("Limpar desenhos", () -> gone.forEach(table::addDrawing)));
                }
            });
        });

        // --- Paredes, portas e visao dinamica (mestre) ---
        Label wallLabel = new Label("PAREDES");
        wallLabel.getStyleClass().add("tool-group-label");
        ToggleButton wallBtn = new ToggleButton("Parede");
        wallBtn.getStyleClass().add("tool-button");
        ToggleButton doorBtn = new ToggleButton("Porta");
        doorBtn.getStyleClass().add("tool-button");
        ToggleButton wallEraseBtn = new ToggleButton("Apagar");
        wallEraseBtn.getStyleClass().add("tool-button");
        ToggleButton windowBtn = new ToggleButton("Janela");
        windowBtn.getStyleClass().add("tool-button");
        ToggleButton lightBtn = new ToggleButton("Luz");
        lightBtn.getStyleClass().add("tool-button");
        CheckBox visionOn = new CheckBox("Visão");
        CheckBox darkOn = new CheckBox("Escuro");
        CheckBox dimOn = new CheckBox("Penumbra");
        CheckBox fogVisionOn = new CheckBox("Neblina pela visão");
        CheckBox previewOn = new CheckBox("Prévia");
        tip(wallBtn, "Arraste para desenhar uma parede (gruda nas pontas e na grade). Bloqueia visão e movimento.");
        tip(doorBtn, "Arraste para desenhar uma porta. Qualquer um clica nela para abrir/fechar.");
        tip(wallEraseBtn, "Clique perto de uma parede/porta para apagar");
        tip(windowBtn, "Arraste para desenhar uma janela: deixa ver, não deixa passar.");
        tip(lightBtn, "Clique no mapa pra pôr uma luz fixa (tocha na parede, fogueira, lustre). Botão direito nela: "
                + "acender/apagar, editar, tirar. Botão direito numa porta: trancar ou tornar secreta.");
        tip(visionOn, "Visão dinâmica: cada jogador vê só o que os tokens dele enxergam");
        tip(darkOn, "Cena escura: só enxerga até a visão no escuro / luz do token (botão direito > Visão e luz)");
        tip(previewOn, "Mostra pra você a escuridão que os jogadores veem");
        tip(dimOn, "Cena em penumbra (crepúsculo, luar): todos veem, mais escuro; visão no escuro enxerga como luz plena");
        tip(fogVisionOn, "A neblina se abre sozinha onde os tokens dos jogadores enxergam (e fica aberta)");
        Runnable syncVisionBoxes = () -> {
            visionOn.setSelected(sceneService.getScene().isVisionEnabled());
            darkOn.setSelected(sceneService.getScene().isDark());
            dimOn.setSelected(sceneService.getScene().isDim());
            fogVisionOn.setSelected(sceneService.getScene().isFogByVision());
        };
        syncVisionBoxes.run();
        Runnable applyVision = () -> table.setVisionSettings(visionOn.isSelected(), darkOn.isSelected(),
                dimOn.isSelected(), fogVisionOn.isSelected());
        visionOn.setOnAction(e -> applyVision.run());
        darkOn.setOnAction(e -> {
            if (darkOn.isSelected()) dimOn.setSelected(false);
            applyVision.run();
        });
        dimOn.setOnAction(e -> {
            if (dimOn.isSelected()) darkOn.setSelected(false);
            applyVision.run();
        });
        fogVisionOn.setOnAction(e -> applyVision.run());
        previewOn.setOnAction(e -> mapView.setGmPreview(previewOn.isSelected()));
        sceneService.addListener(new SceneService.SceneChangeListener() {
            @Override public void onWallsChanged() { syncVisionBoxes.run(); }
        });
        ToggleButton pinBtn = new ToggleButton("Colocar pin", com.vttcaseiro.view.Icons.icon("pin", 14));
        pinBtn.getStyleClass().add("tool-button");
        tip(pinBtn, "Clique no mapa pra criar um pin (cidade, loja, perigo...). Pin pode abrir outra cena.");
        Runnable applyWall = () -> {
            mapView.setWallTool(wallBtn.isSelected() ? MapView.TOOL_WALL
                    : doorBtn.isSelected() ? MapView.TOOL_DOOR : windowBtn.isSelected() ? MapView.TOOL_WINDOW
                    : lightBtn.isSelected() ? MapView.TOOL_LIGHT : wallEraseBtn.isSelected() ? MapView.TOOL_WALL_ERASE : null);
            mapView.setPinTool(pinBtn.isSelected());
        };
        // Todas as ferramentas de arrastar no mapa vazio sao exclusivas entre si.
        List<ToggleButton> allTools = List.of(measureBtn, templateBtn, fogRevealBtn, fogCoverBtn,
                penBtn, textBtn, eraserBtn, wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn, pinBtn);
        for (ToggleButton t : allTools) {
            t.addEventHandler(javafx.event.ActionEvent.ACTION, e -> {
                if (t.isSelected()) for (ToggleButton o : allTools) if (o != t) o.setSelected(false);
                applyTools.run();
                applyDraw.run();
                applyWall.run();
            });
        }
        // desenhar fica a vista; as opcoes de visao/luz da cena num menu "Visão"
        javafx.scene.control.MenuButton visionMenu = new javafx.scene.control.MenuButton("Visão",
                com.vttcaseiro.view.Icons.icon("target", 14));
        visionMenu.getStyleClass().add("tool-button");
        visionMenu.setTooltip(new Tooltip("Visão dinâmica, escuro/penumbra, neblina pela visão e prévia do jogador"));
        for (CheckBox cb : List.of(visionOn, darkOn, dimOn, fogVisionOn, previewOn)) {
            javafx.scene.control.CustomMenuItem item = new javafx.scene.control.CustomMenuItem(cb, false);
            cb.getStyleClass().add("menu-check");
            visionMenu.getItems().add(item);
        }
        HBox wallBox = toolGroup(wallLabel, wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn, visionMenu);
        toolHotkeys = List.of(measureBtn, templateBtn, penBtn, textBtn, eraserBtn, fogRevealBtn, fogCoverBtn, wallBtn, doorBtn);
        toolsOff = () -> {
            allTools.forEach(t -> t.setSelected(false));
            applyTools.run();
            applyDraw.run();
            applyWall.run();
        };
        Runnable wallGmOnly = () -> {
            boolean gm = networkService.isGm(); // (a categoria "Paredes" some pra jogador)
            if (!gm) {
                for (ToggleButton t : List.of(wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn)) t.setSelected(false);
                applyWall.run();
            }
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            wallGmOnly.run();
            mapView.redrawWalls();
        }));
        wallGmOnly.run();

        tip(addTokenBtn, "Novo token com imagem do computador");
        tip(addTokenFromGalleryBtn, "Novo token com imagem da galeria da mesa");
        tip(undoBtn, "Desfaz a última mudança num token (Ctrl+Z)");
        tip(centerBtn, "Volta o zoom e centraliza o mapa");
        tip(measureBtn, "Arraste no mapa para medir distância");
        tip(templateBtn, "Arraste no mapa para marcar uma área de efeito");
        tip(clearTemplateBtn, "Apaga a área marcada");
        tip(effectImageBtn, "Põe uma imagem (JPG, PNG ou GIF animado) cobrindo a área marcada -- vira um objeto do mapa "
                + "que todos veem. Cone e linha: use imagem apontando pra direita.");
        tip(fogRevealBtn, "Arraste no mapa para revelar uma área aos jogadores");
        tip(fogCoverBtn, "Arraste no mapa para esconder uma área");
        tip(fogAllBtn, "Revela o mapa inteiro");
        tip(fogNoneBtn, "Cobre o mapa inteiro");
        templateShapeCombo.setTooltip(new Tooltip("Formato da área de efeito"));

        Label targetsLabel = new Label("ALVOS: nenhum");
        targetsLabel.getStyleClass().add("tool-group-label");
        Button clearTargetsBtn = new Button("Limpar");
        clearTargetsBtn.getStyleClass().add("tool-button");
        clearTargetsBtn.setOnAction(e -> mapView.clearTargets());
        tip(clearTargetsBtn, "Tira a mira de todos os tokens");
        targetsLabel.setTooltip(new Tooltip("Ctrl+clique num token (ou botão direito > Mirar) marca o alvo."
                + " Ataques comparam com a CA dele e o dano cai em quem foi acertado."));
        mapView.setTargetListener(() -> {
            List<TokenModel> t = mapView.getTargets();
            targetsLabel.setText(t.isEmpty() ? "ALVOS: nenhum" : "ALVOS: " + String.join(", ",
                    t.stream().map(TokenModel::getName).toList()));
        });

        // --- Pins (mestre) ---
        Label pinHint = new Label("Clique no mapa: novo pin  \u2022  clique num pin: ver  \u2022  arraste: mover"
                + "  \u2022  botão direito: editar");
        pinHint.getStyleClass().add("tool-group-label");
        HBox pinBox = toolGroup(pinBtn, infoIcon(pinHint.getText()));

        // Linha de cima: categorias. So a categoria aberta mostra os botoes dela (embaixo),
        // assim o mapa fica livre e cada coisa tem um lugar facil de achar.
        HBox tokenBox = toolGroup(addTokenBtn, addTokenFromGalleryBtn, npcBtn);
        HBox measureBox = toolGroup(measureBtn, templateShapeCombo, templateBtn, clearTemplateBtn, effectImageBtn);
        HBox drawBox = toolGroup(penBtn, textBtn, eraserBtn, colorPicker, clearDrawBtn);
        drawLabel.setVisible(false);
        drawLabel.setManaged(false);
        fogLabel.setVisible(false);
        fogLabel.setManaged(false);
        wallLabel.setVisible(false);
        wallLabel.setManaged(false);
        Button objFromFile = new Button("Do computador...", com.vttcaseiro.view.Icons.icon("upload", 14));
        objFromFile.getStyleClass().add("tool-button");
        objFromFile.setOnAction(e -> onAddObjectFromFile());
        Button objFromGallery = new Button("Da galeria...");
        objFromGallery.getStyleClass().add("tool-button");
        objFromGallery.setOnAction(e -> new ImageGalleryView(imageAssetService, this::addObjectFromAsset).show());
        ToggleButton objEdit = new ToggleButton("Editar objetos");
        objEdit.getStyleClass().add("tool-button");
        objEdit.setSelected(objectEditMode);
        objEdit.setOnAction(e -> {
            objectEditMode = objEdit.isSelected();
            mapView.setObjectEditing(objectEditMode);
        });
        tip(objFromFile, "Uma imagem (PNG com fundo transparente fica melhor): mesa, arvore, bau, carroca...");
        tip(objEdit, "Ligado: arraste pra mover, Ctrl+roda muda o tamanho, Shift+roda gira, botão direito: mais opções."
                + " Desligado: os objetos viram cenário (cliques passam pro mapa).");
        Label objHint = new Label("Ctrl+roda: tamanho  \u2022  Shift+roda: girar  \u2022  Alt: solta fora da grade");
        objHint.getStyleClass().add("tool-group-label");
        HBox objectBox = toolGroup(objFromFile, objFromGallery, objEdit, infoIcon(objHint.getText()));
        String[][] cats = {{"Tokens", "party"}, {"Medir", "ruler"}, {"Desenho", "brush"}, {"Pins", "pin"},
                {"Objetos", "objects"}, {"Neblina", "cloud"}, {"Paredes", "wall"}};
        HBox[] panels = {tokenBox, measureBox, drawBox, pinBox, objectBox, fogBox, wallBox};
        StackPane panelHolder = new StackPane();
        panelHolder.setAlignment(Pos.CENTER_LEFT);
        for (HBox p : panels) {
            p.setVisible(false);
            p.setManaged(false);
            panelHolder.getChildren().add(p);
        }
        panelHolder.setVisible(false);
        panelHolder.setManaged(false);
        javafx.scene.control.ToggleGroup catGroup = new javafx.scene.control.ToggleGroup();
        ToggleButton[] catButtons = new ToggleButton[cats.length];
        java.util.function.IntConsumer showCat = i -> {
            for (int k = 0; k < panels.length; k++) {
                boolean on = k == i;
                panels[k].setVisible(on);
                panels[k].setManaged(on);
            }
            panelHolder.setVisible(i >= 0);
            panelHolder.setManaged(i >= 0);
        };
        for (int i = 0; i < cats.length; i++) {
            int idx = i;
            ToggleButton cb = new ToggleButton(cats[i][0], com.vttcaseiro.view.Icons.icon(cats[i][1], 14));
            cb.getStyleClass().addAll("tool-button", "tool-category");
            cb.setToggleGroup(catGroup);
            cb.setOnAction(e -> {
                // trocar/fechar categoria desliga a ferramenta que estava ligada (nada fica ativo escondido)
                toolsOff.run();
                showCat.accept(cb.isSelected() ? idx : -1);
            });
            catButtons[i] = cb;
        }
        tip(catButtons[0], "Criar tokens: imagem do computador, da galeria ou NPC rápido");
        tip(catButtons[1], "Régua e áreas de efeito");
        tip(catButtons[2], "Lápis, texto e borracha (todos podem)");
        tip(catButtons[3], "Pins: pontos de informação que podem abrir outro mapa");
        tip(catButtons[4], "Objetos soltos no mapa: móveis, árvores, baús (mestre)");
        tip(catButtons[5], "Neblina de guerra (mestre)");
        tip(catButtons[6], "Paredes, portas e visão (mestre)");
        // Atalho de teclado (1-9) liga uma ferramenta: abre a categoria dela tambem.
        java.util.Map<ToggleButton, Integer> catOf = new java.util.HashMap<>();
        for (ToggleButton t : List.of(measureBtn, templateBtn)) catOf.put(t, 1);
        for (ToggleButton t : List.of(penBtn, textBtn, eraserBtn)) catOf.put(t, 2);
        catOf.put(pinBtn, 3);
        for (ToggleButton t : List.of(fogRevealBtn, fogCoverBtn)) catOf.put(t, 5);
        for (ToggleButton t : List.of(wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn)) catOf.put(t, 6);
        catOf.forEach((t, c) -> t.selectedProperty().addListener((o, was, now) -> {
            if (now && !catButtons[c].isSelected()) {
                catButtons[c].setSelected(true);
                showCat.accept(c);
            }
        }));
        Runnable catGmOnly = () -> {
            boolean gm = networkService.isGm();
            for (int c : new int[]{3, 4, 5, 6}) {
                catButtons[c].setVisible(gm);
                catButtons[c].setManaged(gm);
                if (!gm && catButtons[c].isSelected()) {
                    catButtons[c].setSelected(false);
                    showCat.accept(-1);
                }
            }
            if (!gm) { pinBtn.setSelected(false); applyWall.run(); }
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(catGmOnly));
        catGmOnly.run();

        undoBtn.setGraphic(com.vttcaseiro.view.Icons.icon("undo", 14));
        centerBtn.setGraphic(com.vttcaseiro.view.Icons.icon("center", 14));
        centerBtn.setText("Ver tudo");
        centerBtn.setOnAction(e -> mapView.fitToView());
        tip(centerBtn, "Mostra o mapa inteiro na tela");
        targetsLabel.setGraphic(com.vttcaseiro.view.Icons.icon("target", 13));
        HBox catRow = new HBox(4, catButtons);
        catRow.getStyleClass().add("tool-group");
        HBox targetsGroup = toolGroup(targetsLabel, clearTargetsBtn);
        // alvos so aparecem quando tem alvo marcado (menos coisa na tela)
        Runnable showTargets = () -> {
            boolean any = targetsLabel.getText() != null && !targetsLabel.getText().endsWith("nenhum");
            targetsGroup.setVisible(any);
            targetsGroup.setManaged(any);
        };
        targetsLabel.textProperty().addListener((o, a, b) -> showTargets.run());
        showTargets.run();
        box.getChildren().addAll(catRow, toolGroup(undoBtn, centerBtn), targetsGroup);
        // mapa estreito (painel e chat abertos): so os icones, o nome fica na dica
        java.util.List<javafx.scene.control.Labeled> squeezable = new java.util.ArrayList<>(List.of(catButtons));
        squeezable.add(undoBtn);
        squeezable.add(centerBtn);
        mapHolder.widthProperty().addListener((o, a, w) -> {
            boolean narrow = w.doubleValue() < 1000;
            for (javafx.scene.control.Labeled l : squeezable) {
                l.setContentDisplay(narrow ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                        : javafx.scene.control.ContentDisplay.LEFT);
                if (l.getTooltip() == null) l.setTooltip(new Tooltip(l.getText()));
            }
        });
        VBox toolbar = new VBox(6, box, panelHolder);
        toolbar.getStyleClass().add("map-toolbar");
        toolbar.setFillWidth(false);
        // os "gmOnly" antigos escondiam fogBox/wallBox direto; agora so a categoria aparece/some
        return toolbar;
    }

    /** "ⓘ" pequeno com a dica no passar do mouse (no lugar de uma frase comprida na barra). */
    private static Label infoIcon(String text) {
        Label l = new Label("ⓘ");
        l.getStyleClass().add("tool-info");
        Tooltip t = new Tooltip(text);
        t.setShowDelay(javafx.util.Duration.millis(150));
        Tooltip.install(l, t);
        return l;
    }

    private static HBox toolGroup(javafx.scene.Node... nodes) {
        HBox group = new HBox(nodes);
        group.getStyleClass().add("tool-group");
        for (javafx.scene.Node n : nodes) {
            if (n instanceof javafx.scene.control.Control c) c.setMinWidth(Region.USE_PREF_SIZE);
        }
        return group;
    }

    private static void tip(javafx.scene.control.Control c, String text) {
        c.setTooltip(new Tooltip(text));
    }


    private ScrollPane wrapInScroll(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private Region divider() {
        Region region = new Region();
        region.getStyleClass().add("section-divider");
        VBox.setMargin(region, new Insets(4, 0, 4, 0));
        return region;
    }

    private Tab buildMapTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        box.getChildren().add(sectionLabel("Cenas desta mesa"));
        sceneFolders = com.vttcaseiro.library.SceneFolders.load(mesaRepository.getRoot());
        mapListView = new javafx.scene.control.TreeView<>();
        mapListView.setShowRoot(false);
        mapListView.setPrefHeight(200);
        mapListView.setCellFactory(tv -> new SceneTreeCell());
        refreshMapList();
        mapListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                SceneEntry sel = selectedSceneEntry();
                if (sel != null && !sel.folder()) onOpenMapFromMesa(sel.name());
            }
        });
        mapListView.setOnContextMenuRequested(e -> {
            javafx.scene.control.ContextMenu menu = sceneTreeMenu(selectedSceneEntry());
            menu.show(mapListView, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        Button newFolderButton = new Button("Nova pasta", com.vttcaseiro.view.Icons.icon("open", 14));
        newFolderButton.setTooltip(new Tooltip("Pasta pra organizar as cenas (ex.: Cidade > Taverna). Arraste as cenas pra dentro."));
        newFolderButton.setOnAction(e -> {
            SceneEntry sel = selectedSceneEntry();
            onNewSceneFolder(sel == null ? "" : sel.folder() ? sel.path() : sel.path());
        });
        Label treeHint = new Label("Duplo clique abre pra todos. Botão direito: mostrar rapidinho e voltar, mover, pastas.");
        treeHint.getStyleClass().add("label-muted");
        treeHint.setWrapText(true);
        box.getChildren().addAll(mapListView, new HBox(6, newFolderButton), treeHint);

        Button newSceneButton = new Button("Nova cena (imagem)...", com.vttcaseiro.view.Icons.icon("add", 14));
        newSceneButton.getStyleClass().add("button-primary");
        newSceneButton.setOnAction(e -> onNewScene());
        Button saveSceneButton = new Button("Salvar cena atual na mesa");
        saveSceneButton.setOnAction(e -> onSaveCurrentScene());
        sceneSaveStatus = new Label("A cena é salva sozinha 2 s depois de cada mudança (mestre/offline).");
        sceneSaveStatus.getStyleClass().add("live-status");
        sceneSaveStatus.getStyleClass().add("label-muted");
        sceneSaveStatus.setWrapText(true);
        Button importMapButton = new Button("Importar mapa (Azgaar / Watabou)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importMapButton.setTooltip(new Tooltip("Masmorra do One Page Dungeon (JSON) vira cena com paredes e portas;"
                + " mundo do Azgaar e cidades/vilas do Watabou entram como imagem (PNG/JPG)."));
        importMapButton.setOnAction(e -> onImportGeneratedMap());
        for (Button b : List.of(newSceneButton, importMapButton, saveSceneButton)) b.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(newSceneButton, importMapButton, saveSceneButton, sceneSaveStatus, divider());
        box.getChildren().add(buildSceneQueue());
        box.getChildren().add(divider());

        box.getChildren().add(sectionLabel("Grade da cena atual"));
        ComboBox<String> gridType = new ComboBox<>(FXCollections.observableArrayList(
                "Quadrada", "Hexagonal", "Sem grade"));
        gridTypeCombo = gridType;
        com.vttcaseiro.model.GridSettings currentGrid = sceneService.getScene().getGrid();
        gridType.setValue(currentGrid.isHex() ? "Hexagonal" : currentGrid.isGridless() ? "Sem grade" : "Quadrada");
        gridType.setTooltip(new Tooltip("Hexagonal: tokens encaixam no hexágono e a régua conta passos de hexágono. "
                + "Sem grade: tokens ficam onde você soltar e a régua mede em linha reta."));
        gridType.setOnAction(e -> {
            String t = switch (gridType.getValue()) {
                case "Hexagonal" -> com.vttcaseiro.model.GridSettings.HEX;
                case "Sem grade" -> com.vttcaseiro.model.GridSettings.NONE;
                default -> com.vttcaseiro.model.GridSettings.SQUARE;
            };
            sceneService.setGridType(t);
            if (networkService.isGm()) networkService.sendSceneSwitch(sceneService.getScene()); // jogadores recebem a grade nova
            logEntries.add(LogEntry.system("Grade: " + gridType.getValue() + "."));
        });
        box.getChildren().add(gridType);
        gridSizeField = new TextField(String.valueOf((int) sceneService.getScene().getGrid().getCellSize()));
        Button applyGridButton = new Button("Aplicar");
        applyGridButton.setOnAction(e -> onApplyGridSize(gridSizeField.getText()));
        HBox gridRow = new HBox(6, gridSizeField, applyGridButton);
        box.getChildren().add(gridRow);

        box.getChildren().add(sectionLabel("Pés por célula (pra régua de medição)"));
        feetPerCellField = new TextField(formatFeet(sceneService.getScene().getGrid().getFeetPerCell()));
        Button applyFeetButton = new Button("Aplicar");
        applyFeetButton.setOnAction(e -> onApplyFeetPerCell(feetPerCellField.getText()));
        box.getChildren().add(new HBox(6, feetPerCellField, applyFeetButton));

        box.getChildren().add(sectionLabel("Tamanho do mapa (em casas)"));
        box.getChildren().add(buildMapSizeControls());

        return new Tab("Mapa", wrapInScroll(box));
    }

    /**
     * Tamanho do mapa escolhido pelo mestre: largura x altura em casas (a imagem estica pra caber), ou automatico
     * (o tamanho da imagem). Mostra o tamanho em pes e metros.
     */
    private javafx.scene.Node buildMapSizeControls() {
        com.vttcaseiro.model.GridSettings g = sceneService.getScene().getGrid();
        javafx.scene.control.Spinner<Integer> width = new javafx.scene.control.Spinner<>(1, com.vttcaseiro.model.GridSettings.MAX_MAP_CELLS, 30);
        javafx.scene.control.Spinner<Integer> height = new javafx.scene.control.Spinner<>(1, com.vttcaseiro.model.GridSettings.MAX_MAP_CELLS, 20);
        width.setEditable(true);
        height.setEditable(true);
        width.setPrefWidth(80);
        height.setPrefWidth(80);
        CheckBox auto = new CheckBox("Automático (tamanho da imagem)");
        Label info = new Label();
        info.getStyleClass().add("label-muted");
        info.setWrapText(true);
        Runnable fromScene = () -> {
            com.vttcaseiro.model.GridSettings now = sceneService.getScene().getGrid();
            double cell = Math.max(10, now.getCellSize());
            int w = now.hasCustomMapSize() ? now.getMapWidthCells() : (int) Math.round(mapView.mapWidth() / cell);
            int h = now.hasCustomMapSize() ? now.getMapHeightCells() : (int) Math.round(mapView.mapHeight() / cell);
            width.getValueFactory().setValue(Math.max(1, w));
            height.getValueFactory().setValue(Math.max(1, h));
            auto.setSelected(!now.hasCustomMapSize());
            width.setDisable(auto.isSelected());
            height.setDisable(auto.isSelected());
            double fpc = now.getFeetPerCell() <= 0 ? 5 : now.getFeetPerCell();
            info.setText(w + " × " + h + " casas = " + com.vttcaseiro.view.Distances.feetAndMeters(w * fpc) + " × "
                    + com.vttcaseiro.view.Distances.feetAndMeters(h * fpc));
        };
        auto.setOnAction(e -> {
            width.setDisable(auto.isSelected());
            height.setDisable(auto.isSelected());
        });
        Button keepRatio = new Button("Altura pela proporção da imagem");
        keepRatio.setOnAction(e -> {
            javafx.scene.image.Image bg = mapView.backgroundImage();
            if (bg == null || bg.getWidth() <= 0) {
                showError("Esta cena não tem imagem de mapa.");
                return;
            }
            width.increment(0);
            height.getValueFactory().setValue(Math.max(1, (int) Math.round(width.getValue() * bg.getHeight() / bg.getWidth())));
        });
        Button apply = new Button("Aplicar");
        apply.getStyleClass().add("button-primary");
        apply.setOnAction(e -> {
            if (!networkService.isGm()) {
                showError("Só o mestre muda o tamanho do mapa.");
                return;
            }
            width.increment(0);
            height.increment(0);
            if (auto.isSelected()) sceneService.setMapSizeCells(0, 0);
            else sceneService.setMapSizeCells(width.getValue(), height.getValue());
            if (networkService.isConnected()) networkService.sendSceneSwitch(sceneService.getScene()); // jogadores recebem
            fromScene.run();
            logEntries.add(LogEntry.system(auto.isSelected() ? "Mapa no tamanho da imagem."
                    : "Mapa ajustado para " + width.getValue() + " × " + height.getValue() + " casas."));
        });
        mapSizeRefresh = fromScene;
        javafx.application.Platform.runLater(fromScene);
        return new VBox(6, auto, new HBox(6, new Label("Largura"), width, new Label("Altura"), height), keepRatio, apply, info);
    }

    private Tab buildCharacterTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        box.getChildren().add(sectionLabel("Fichas desta mesa"));
        characterListView = new ListView<>();
        characterListView.setPlaceholder(new Label("Nenhuma ficha ainda. Crie uma nova ou importe um PDF/JSON."));
        characterListView.setPrefHeight(170);
        refreshCharacterList();
        characterListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String name = characterListView.getSelectionModel().getSelectedItem();
                if (name != null) onOpenCharacterFromMesa(name);
            }
        });
        box.getChildren().add(characterListView);

        Button newCharButton = new Button("Nova ficha...", com.vttcaseiro.view.Icons.icon("add", 14));
        newCharButton.getStyleClass().add("button-primary");
        newCharButton.setOnAction(e -> onNewCharacter());
        Button importCharButton = new Button("Importar ficha (PDF ou JSON)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importCharButton.setTooltip(new Tooltip("PDF preenchível (ficha oficial de 5e, D&D Beyond \"Exportar PDF\")"
                + " ou JSON (Customizr, Foundry VTT dnd5e, D&D Beyond)."));
        importCharButton.setOnAction(e -> onImportCharacter());
        Button exportCharButton = new Button("Exportar ficha aberta (JSON)...", com.vttcaseiro.view.Icons.icon("download", 14));
        exportCharButton.setOnAction(e -> onExportCharacter());
        Button saveCharButton = new Button("Salvar ficha aberta (ex.: após trocar retrato)");
        saveCharButton.setOnAction(e -> onSaveLastCharacter());
        importCharButton.setText("Importar...");
        javafx.scene.control.MenuButton moreChar = new javafx.scene.control.MenuButton("Mais");
        javafx.scene.control.MenuItem exportItem = new javafx.scene.control.MenuItem("Exportar ficha aberta (JSON)...");
        exportItem.setOnAction(e -> onExportCharacter());
        javafx.scene.control.MenuItem saveItem = new javafx.scene.control.MenuItem("Salvar ficha aberta agora");
        saveItem.setOnAction(e -> onSaveLastCharacter());
        moreChar.getItems().addAll(exportItem, saveItem);
        javafx.scene.layout.FlowPane charButtons = new javafx.scene.layout.FlowPane(6, 6, newCharButton, importCharButton, moreChar);
        for (javafx.scene.control.Control c : List.of(newCharButton, importCharButton, moreChar)) c.setMinWidth(Region.USE_PREF_SIZE);
        box.getChildren().addAll(charButtons, divider());

        // Tudo que e de preparar a campanha (importar, conteudo pronto, buscar no compendio) fica numa janela propria.
        VBox compendium = new VBox(8);
        compendium.setPadding(new Insets(12));
        compendium.getChildren().add(sectionLabel("Conteúdo pronto (D&D 2024)"));
        Button seedButton = new Button("Carregar conteúdo 2024 pronto (magias, talentos, espécies, "
                + "antecedentes, itens + monstros)");
        seedButton.getStyleClass().add("button-primary");
        seedButton.setWrapText(true);
        seedButton.setOnAction(e -> onLoadSeedContent());
        compendium.getChildren().addAll(seedButton, divider());

        compendium.getChildren().add(sectionLabel("Importar do 5etools"));
        Button importClassesButton = new Button("Importar classes (class-*.json)...");
        importClassesButton.setOnAction(e -> onImportClasses());
        classCountLabel = new Label(classImportService.getClasses().size() + " classe(s) importada(s) nesta mesa.");
        classCountLabel.getStyleClass().add("label-muted");
        compendium.getChildren().addAll(importClassesButton, classCountLabel);

        HBox compendiumImportRow1 = new HBox(6,
                compendiumImportButton("Magias...", compendiumImportService::importSpells),
                compendiumImportButton("Feats...", compendiumImportService::importFeats));
        HBox compendiumImportRow2 = new HBox(6,
                compendiumImportButton("Antecedentes...", compendiumImportService::importBackgrounds),
                compendiumImportButton("Espécies...", compendiumImportService::importSpecies));
        HBox compendiumImportRow3 = new HBox(6,
                compendiumImportButton("Itens...", compendiumImportService::importItems));
        compendiumCountLabel = new Label(compendiumCountText());
        compendiumCountLabel.getStyleClass().add("live-status");
        compendiumCountLabel.getStyleClass().add("label-muted");
        Label importOrderHint = new Label("Ordem recomendada pra antecedente/espécie linkarem com o feat/magia "
                + "que concedem: Magias e Feats primeiro, Antecedentes/Espécies depois. Fora dessa ordem não "
                + "da erro nenhum, só fica sem o link.");
        importOrderHint.getStyleClass().add("label-muted");
        importOrderHint.setWrapText(true);
        Button importMonstersButton = new Button("Monstros (bestiary-*.json)...");
        importMonstersButton.setOnAction(e -> onImportMonsters());
        compendium.getChildren().addAll(compendiumImportRow1, compendiumImportRow2, compendiumImportRow3,
                new HBox(6, importMonstersButton), compendiumCountLabel, importOrderHint);

        box.getChildren().add(sectionLabel("Monstros"));
        mobCountLabel = new Label(mobService.list().size() + " monstro(s) nesta mesa.");
        mobCountLabel.getStyleClass().add("live-status");
        mobCountLabel.getStyleClass().add("label-muted");
        box.getChildren().add(mobCountLabel);

        mobListView = new ListView<>();
        mobListView.setPrefHeight(160);
        mobListView.setPlaceholder(new Label("Nenhum monstro ainda (importe no Compêndio)."));
        refreshMobList();
        mobListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String name = mobListView.getSelectionModel().getSelectedItem();
                if (name != null) onOpenMobFromMesa(name);
            }
        });
        Button mobToMapButton = new Button("Colocar monstro selecionado no mapa");
        mobToMapButton.setOnAction(e -> onPlaceSelectedMob());
        box.getChildren().add(mobToMapButton);
        Label mobHint = new Label("Clique duplo num monstro da lista pra abrir a ficha dele. O token do monstro "
                + "nasce com PV médio e bônus de iniciativa; o PV fica escondido dos jogadores.");
        mobHint.getStyleClass().add("label-muted");
        mobHint.setWrapText(true);
        box.getChildren().addAll(mobListView, mobHint, divider());

        compendium.getChildren().add(sectionLabel("Buscar e anexar na ficha aberta"));
        TextField compendiumSearchField = new TextField();
        compendiumSearchField.setPromptText("Buscar por nome (ex.: \"Bola de Fogo\", \"Aasimar\")...");
        compendiumSearchListView = new ListView<>();
        compendiumSearchListView.setPrefHeight(140);
        compendiumSearchListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CompendiumEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : entry.getName() + " (" + entry.getType() + ")");
            }
        });
        compendiumSearchField.setOnAction(e -> onCompendiumSearch(compendiumSearchField.getText()));
        Button searchButton = new Button("Buscar");
        searchButton.setOnAction(e -> onCompendiumSearch(compendiumSearchField.getText()));
        Button attachButton = new Button("Anexar na ficha aberta");
        attachButton.getStyleClass().add("button-primary");
        attachButton.setOnAction(e -> onAttachSelectedCompendiumEntry());
        HBox.setHgrow(compendiumSearchField, Priority.ALWAYS);
        compendium.getChildren().addAll(new HBox(6, compendiumSearchField, searchButton), compendiumSearchListView,
                attachButton);

        box.getChildren().add(sectionLabel("Compêndio"));
        Label compendiumSummary = new Label();
        compendiumSummary.getStyleClass().addAll("label-muted", "live-status");
        compendiumSummary.textProperty().bind(compendiumCountLabel.textProperty());
        Button openCompendium = new Button("Abrir compêndio...", com.vttcaseiro.view.Icons.icon("journal", 14));
        openCompendium.setTooltip(new Tooltip("Conteúdo pronto 2024, importar do 5etools e buscar algo pra anexar na ficha."));
        openCompendium.setOnAction(e -> showCompendiumWindow(compendium));
        box.getChildren().addAll(compendiumSummary, openCompendium, divider());

        Label hint = new Label("Dica: arraste o retrato (ou a silhueta) da janela "
                + "da ficha para cima do mapa -- isso cria um token vinculado a "
                + "ela, com o nome e o PV que a ficha tiver.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        box.getChildren().add(hint);

        return new Tab("Fichas", wrapInScroll(box));
    }

    private Stage mesaSettingsWindow;

    /** Engrenagem do topo: backup e copias automaticas da mesa (saiu da aba Rede). */
    private void showMesaSettings() {
        if (mesaSettingsWindow == null) {
            VBox settings = new VBox(8);
            settings.setPadding(new Insets(12));
        // --- Backup da mesa inteira ---
            Button exportButton = new Button("Exportar mesa inteira (backup)...");
            exportButton.setTooltip(new Tooltip("Um .zip com fichas, cenas, monstros, imagens, sons, diário e tabelas."
                    + " Importe na tela de escolher mesa (\"Importar backup...\")."));
            exportButton.setOnAction(e -> onExportMesa());
            Label backupHint = new Label("Pra guardar a campanha ou levar pra outro computador.");
            backupHint.getStyleClass().add("label-muted");
            settings.getChildren().addAll(sectionLabel("Backup da mesa"), exportButton, backupHint);

            ComboBox<String> backupEvery = new ComboBox<>();
            for (int m : BACKUP_MINUTES) backupEvery.getItems().add(m == 0 ? "Desligado" : "A cada " + m + " min");
            int current = autoBackupMinutes();
            for (int i = 0; i < BACKUP_MINUTES.length; i++) if (BACKUP_MINUTES[i] == current) backupEvery.getSelectionModel().select(i);
            backupEvery.setTooltip(new Tooltip("Cópia automática da mesa (só quando algo mudou). Ficam as "
                    + com.vttcaseiro.library.AutoBackup.KEEP + " mais novas, na pasta backups da mesa."));
            backupEvery.setOnAction(e -> {
                int i = backupEvery.getSelectionModel().getSelectedIndex();
                if (i < 0) return;
                AppSettings.setDouble("autoBackupMinutes", BACKUP_MINUTES[i]);
                restartAutoBackupTimer();
            });
            Button backupNow = new Button("Fazer backup agora");
            backupNow.setOnAction(e -> runBackup("manual", true));
            Button restoreButton = new Button("Restaurar backup...");
            restoreButton.setTooltip(new Tooltip("Volta a mesa como estava num backup. Cria uma mesa NOVA com a cópia;"
                    + " a atual não é apagada."));
            restoreButton.setOnAction(e -> onRestoreBackup());
            backupStatus = new Label(lastBackupText());
            backupStatus.getStyleClass().add("live-status");
            backupStatus.getStyleClass().add("label-muted");
            backupStatus.setWrapText(true);
            settings.getChildren().addAll(sectionLabel("Backups automáticos"), backupEvery,
                    new javafx.scene.layout.FlowPane(6, 6, backupNow, restoreButton), backupStatus);

            compactHints(settings);
            groupIntoCards(settings, "");
            ScrollPane scroll = new ScrollPane(settings);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("side-panel");
            mesaSettingsWindow = new Stage();
            mesaSettingsWindow.initOwner(stage);
            mesaSettingsWindow.setTitle("Mesa: " + mesaService.getName());
            Scene sc = new Scene(scroll, 420, 460);
            sc.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            mesaSettingsWindow.setScene(sc);
            com.vttcaseiro.view.Icons.applyTo(mesaSettingsWindow);
        }
        if (backupStatus != null) backupStatus.setText(lastBackupText());
        mesaSettingsWindow.show();
        mesaSettingsWindow.toFront();
    }

    private Stage compendiumWindow;

    /** Janela do compendio (criada uma vez; os contadores dentro dela continuam atualizando). */
    private void showCompendiumWindow(VBox content) {
        if (compendiumWindow == null) {
            compactHints(content);
            groupIntoCards(content, "");
            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("side-panel");
            compendiumWindow = new Stage();
            compendiumWindow.initOwner(stage);
            compendiumWindow.setTitle("Compêndio da mesa");
            Scene sc = new Scene(scroll, 460, 640);
            sc.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            compendiumWindow.setScene(sc);
            com.vttcaseiro.view.Icons.applyTo(compendiumWindow);
        }
        compendiumWindow.show();
        compendiumWindow.toFront();
    }

    private Tab buildNetworkTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        box.getChildren().add(sectionLabel("Seu nome"));
        TextField nameField = new TextField(networkService.getPlayerName());
        box.getChildren().addAll(nameField, divider());

        box.getChildren().add(sectionLabel("Hospedar"));
        TextField hostPortField = new TextField("55055");
        PasswordField hostTablePasswordField = new PasswordField();
        hostTablePasswordField.setPromptText("Senha da mesa (opcional)");
        PasswordField hostMasterPasswordField = new PasswordField();
        hostMasterPasswordField.setPromptText("Senha de mestre (opcional)");
        Button hostButton = new Button("Hospedar aqui");
        hostButton.getStyleClass().add("button-primary");
        CheckBox hostTls = new CheckBox("Criptografar (TLS)");
        hostTls.setTooltip(new Tooltip("Conexão criptografada (jogo e navegador). Pra jogar pela internet sem Tailscale. "
                + "Todos precisam marcar TLS ao conectar e conferir o código da mesa."));
        hostPortField.setPrefColumnCount(6);
        hostButton.setMinWidth(Region.USE_PREF_SIZE);
        HBox hostRow = new HBox(6, hostPortField, hostButton);
        hostRow.setAlignment(Pos.CENTER_LEFT);
        // Jogar pelo navegador: aparece quando esta hospedando (porta do jogo + 1).
        Label tlsCode = new Label();
        tlsCode.setWrapText(true);
        tlsCode.setStyle("-fx-text-fill: #8FE0AE; -fx-font-weight: bold;");
        Label webTitle = sectionLabel("Jogadores pelo navegador");
        Label webLinks = new Label();
        webLinks.setWrapText(true);
        webLinks.setStyle("-fx-text-fill: #FFCF7F; -fx-font-weight: bold;");
        Label webHint = new Label("Quem não tem o programa abre este endereço no navegador (PC ou celular, na mesma "
                + "rede, ou pela Tailscale/ZeroTier). Usa a mesma senha da mesa.");
        webHint.getStyleClass().add("label-muted");
        webHint.setWrapText(true);
        Button copyWeb = new Button("Copiar link");
        VBox webBox = new VBox(4, webTitle, webLinks, copyWeb, webHint);
        Runnable refreshWeb = () -> {
            String fp = networkService.getHostFingerprint();
            tlsCode.setVisible(fp != null);
            tlsCode.setManaged(fp != null);
            if (fp != null) {
                tlsCode.setText("Código da mesa (TLS): " + com.vttcaseiro.net.TlsSupport.shortCode(fp)
                        + "\nPasse esse código pros jogadores conferirem na primeira conexão. No navegador, o aviso"
                        + " de \"certificado não confiável\" é esperado (certificado próprio): avançar.");
            }
            int webPort = networkService.getWebPort();
            webBox.setVisible(webPort > 0);
            webBox.setManaged(webPort > 0);
            if (webPort > 0) {
                List<String> links = new java.util.ArrayList<>();
                String scheme = networkService.isTls() ? "https://" : "http://";
                for (String ip : localIpv4Addresses()) links.add(scheme + ip + ":" + webPort);
                if (links.isEmpty()) links.add(scheme + "localhost:" + webPort);
                webLinks.setText(String.join("\n", links));
            }
        };
        copyWeb.setOnAction(e -> {
            String first = webLinks.getText().split("\n")[0];
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(first);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            logEntries.add(LogEntry.system("Link copiado: " + first));
        });
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(refreshWeb));
        refreshWeb.run();
        // --- Ponte: jogar pela internet sem abrir porta ---
        TextField relayField = new TextField(AppSettings.getString("ponte.endereco", ""));
        relayField.setPromptText("endereço da ponte, ex.: meuservidor.com:55070");
        HBox.setHgrow(relayField, Priority.ALWAYS);
        Button relayButton = new Button("Abrir mesa na ponte", com.vttcaseiro.view.Icons.icon("network", 14));
        relayButton.setMinWidth(Region.USE_PREF_SIZE);
        relayButton.setTooltip(new Tooltip("Hospede a mesa primeiro. A ponte é um programa do Customizr rodando num "
                + "servidor com porta aberta (ver README: \"Ponte\"); assim ninguém precisa abrir porta no roteador."));
        Label relayInfo = new Label();
        relayInfo.setWrapText(true);
        relayInfo.setStyle("-fx-text-fill: #8FE0AE;");
        relayInfo.setVisible(false);
        relayInfo.setManaged(false);
        relayButton.setOnAction(e -> onOpenRelay(relayField.getText(), relayInfo));
        HBox relayRow = new HBox(6, relayField, relayButton);
        relayRow.setAlignment(Pos.CENTER_LEFT);
        Label relayHint = new Label("Ponte (opcional): pra jogar pela internet sem abrir porta no roteador nem Tailscale.");
        relayHint.getStyleClass().add("label-muted");
        relayHint.setWrapText(true);
        VBox hostMore = new VBox(8, hostTls, hostTablePasswordField, hostMasterPasswordField, relayHint, relayRow);
        hostMore.setVisible(false);
        hostMore.setManaged(false);
        Label hostMoreToggle = new Label("▸  Senhas, criptografia e ponte");
        hostMoreToggle.getStyleClass().add("hint-toggle");
        hostMoreToggle.setCursor(javafx.scene.Cursor.HAND);
        hostMoreToggle.setOnMouseClicked(e -> {
            boolean show = !hostMore.isVisible();
            hostMore.setVisible(show);
            hostMore.setManaged(show);
            hostMoreToggle.setText((show ? "▾" : "▸") + "  Senhas, criptografia e ponte");
        });
        box.getChildren().addAll(hostRow, hostMoreToggle, hostMore, tlsCode, webBox, relayInfo, divider());

        box.getChildren().add(sectionLabel("Conectar"));
        Button joinButton = new Button("Entrar numa mesa online...", com.vttcaseiro.view.Icons.icon("network", 14));
        joinButton.setTooltip(new Tooltip("Endereço (ou código da ponte) e senha que o mestre passou."));
        joinButton.setOnAction(e -> MesaSelectorView.askJoin(stage).ifPresent(req -> {
            nameField.setText(req.playerName());
            networkService.connectThroughRelay(req.relayCode());
            onConnect(req.playerName(), req.host(), req.port(), req.tablePassword(), req.asGm(), req.masterPassword(), req.tls());
        }));
        box.getChildren().add(joinButton);

        hostButton.setOnAction(e -> onHost(nameField.getText(), hostPortField.getText(),
                hostTablePasswordField.getText(), hostMasterPasswordField.getText(), hostTls.isSelected()));

        box.getChildren().add(divider());
        box.getChildren().add(sectionLabel("Conectados agora"));
        ListView<String> onlineListView = new ListView<>(networkService.getOnlinePlayers());
        onlineListView.setPrefHeight(120);
        onlineListView.setPlaceholder(new Label("Ninguém conectado."));
        box.getChildren().add(onlineListView);

        return new Tab("Rede", wrapInScroll(box));
    }

    /** Tela da Party da mesa (uma so por mesa, ver PartyService) --
     * membros (resolvidos pro nome so se a ficha estiver aberta nesta
     * sessao, mesma limitacao que o drag-and-drop de token pra ficha ja
     * tem) e o inventario compartilhado, com "adicionar/remover membro"
     * e "transferir item pra ficha aberta" ligados ao que ja existia
     * pronto em Party/Inventory, so sem tela ainda. */
    /** Resumo de um membro do grupo, do jeito do sistema da ficha. */
    static String partySummary(CharacterSheet s) {
        com.vttcaseiro.character.GameSystem gs = com.vttcaseiro.character.GameSystem.of(s);
        String hp = "PV " + (s.getHpCurrent() == null ? "?" : s.getHpCurrent()) + "/" + (s.getHpMax() == null ? "?" : s.getHpMax());
        StringBuilder b = new StringBuilder(s.getName()).append("  •  ").append(hp);
        if (gs.isCustom()) {
            b.append("  •  Defesa ").append(s.getArmorClass() == null ? "?" : s.getArmorClass());
            for (String r : gs.resources()) {
                s.findResource(r).ifPresent(x -> b.append("  •  ").append(r).append(' ').append(x.getCurrent()).append('/').append(x.getMax()));
            }
            b.append("  (").append(gs.name()).append(')');
        } else {
            b.append("  •  CA ").append(s.totalArmorClass()).append("  •  Percepção passiva ").append(s.passivePerception());
        }
        return b.toString();
    }

    private Tab buildPartyTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        box.getChildren().add(sectionLabel("Nome da party"));
        TextField partyNameField = new TextField(partyService.getParty().getName());
        Button renamePartyButton = new Button("Renomear");
        renamePartyButton.setOnAction(e -> onRenameParty(partyNameField.getText()));
        box.getChildren().addAll(new HBox(6, partyNameField, renamePartyButton), divider());

        box.getChildren().add(sectionLabel("Membros (ficha precisa estar aberta nesta sessão pra mostrar o nome)"));
        partyMemberListView = new ListView<>();
        partyMemberListView.setPrefHeight(120);
        partyMemberListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String characterId, boolean empty) {
                super.updateItem(characterId, empty);
                if (empty || characterId == null) {
                    setText(null);
                    return;
                }
                Optional<CharacterSheet> open = characterService.findOpen(characterId);
                if (open.isEmpty()) open = characterService.findById(characterId);
                setText(open.map(Main::partySummary)
                        .orElse("(ficha não encontrada nesta mesa -- id " + characterId + ")"));
            }
        });
        refreshPartyMemberList();
        box.getChildren().add(partyMemberListView);
        Button addMemberButton = new Button("+ Adicionar ficha aberta na party");
        Button removeMemberButton = new Button("Remover selecionado da party");
        removeMemberButton.setOnAction(e -> onRemovePartyMember());
        addMemberButton.setOnAction(e -> onAddLastOpenedCharacterToParty());
        box.getChildren().addAll(new HBox(6, addMemberButton, removeMemberButton), divider());

        box.getChildren().add(sectionLabel("Inventário compartilhado (loot comum do grupo)"));
        partyInventoryListView = new ListView<>();
        partyInventoryListView.setPrefHeight(140);
        partyInventoryListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " x" + item.getQuantity());
            }
        });
        refreshPartyInventoryList();
        box.getChildren().add(partyInventoryListView);
        Button addItemButton = new Button("+ Adicionar item...");
        addItemButton.setOnAction(e -> onAddPartyItem());
        Button transferItemButton = new Button("Transferir 1 selecionado pra ficha aberta");
        transferItemButton.setOnAction(e -> onTransferPartyItemToLastOpenedCharacter());
        box.getChildren().add(new HBox(6, addItemButton, transferItemButton));

        Label hint = new Label("Transferir item específico pra outra ficha (não só a última aberta) e "
                + "editar dinheiro da party ainda não tem botão aqui -- ver HANDOFF.md.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        box.getChildren().add(hint);

        return new Tab("Party", wrapInScroll(box));
    }

    /** Rastreador de iniciativa (ordem de combate). A ordem e sempre
     * recalculada a partir dos tokens da cena atual -- maior iniciativa
     * primeiro, quem ainda nao rolou vai pro fim da fila (ver
     * TokenModel.initiative). So existe localmente nesta janela por
     * enquanto: nao viaja pela rede, cada jogador tem a sua propria (ver
     * HANDOFF.md). */
    private Tab buildInitiativeTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        roundLabel = new Label("Sem combate em andamento");
        roundLabel.getStyleClass().add("live-status");
        roundLabel.getStyleClass().add("label-title");
        box.getChildren().addAll(sectionLabel("Combate"), roundLabel);
        initiativeListView = new ListView<>();
        initiativeListView.setPrefHeight(150);
        initiativeListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TokenModel token, boolean empty) {
                super.updateItem(token, empty);
                getStyleClass().remove("initiative-current");
                setStyle("");
                setContextMenu(null);
                if (empty || token == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(initiativeRow(token));
                String color = trackerColorHex(token.getTrackerColor());
                setStyle("-fx-border-color: transparent transparent transparent " + color + "; -fx-border-width: 0 0 0 4;"
                        + (token.getTrackerColor() == null ? "" : " -fx-background-color: " + color + "33;"));
                setContextMenu(initiativeMenu(token));
                boolean current = table.currentTurnToken().map(t -> t == token).orElse(false);
                if (current) getStyleClass().add("initiative-current");
            }
        });
        initiativeListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                onEditInitiative();
            }
        });
        box.getChildren().add(initiativeListView);

        Button rollAllButton = new Button("Rolar iniciativa e começar combate");
        rollAllButton.getStyleClass().add("button-primary");
        rollAllButton.setOnAction(e -> table.rollInitiativeForAllAndStart());
        Button startButton = new Button("Começar com as iniciativas atuais");
        startButton.setOnAction(e -> table.startEncounter());
        Button editButton = new Button("Editar...");
        editButton.setTooltip(new Tooltip("Iniciativa, modificador só deste combatente e cor da linha (ou duplo clique na linha)."));
        editButton.setOnAction(e -> onEditInitiative());
        Button pullButton = new Button("Puxar das fichas");
        pullButton.setTooltip(new Tooltip("Usa a iniciativa que cada jogador rolou na própria ficha (+ o modificador do rastreador)."));
        pullButton.setOnAction(e -> {
            int n = table.pullInitiativesFromSheets();
            if (n > 0) refreshInitiativeList();
        });
        box.getChildren().add(new javafx.scene.layout.FlowPane(6, 6, rollAllButton, startButton, pullButton, editButton));

        Button nextTurnButton = new Button("Próximo turno");
        nextTurnButton.getStyleClass().add("button-primary");
        nextTurnButton.setOnAction(e -> table.nextTurn());
        Button endButton = new Button("Encerrar combate");
        endButton.setOnAction(e -> table.endEncounter());
        box.getChildren().add(new HBox(6, nextTurnButton, endButton));

        box.getChildren().addAll(divider(), sectionLabel("Mestre: grupo inteiro"));
        Button groupSaveButton = new Button("Salvaguarda em grupo...");
        groupSaveButton.getStyleClass().add("button-primary");
        groupSaveButton.setTooltip(new Tooltip("Todos rolam contra uma CD; da pra aplicar o dano (cheio em quem"
                + " falha, metade em quem passa) -- ex.: Bola de Fogo."));
        groupSaveButton.setOnAction(e -> onGroupSave());
        Button shortRestButton = new Button("Descanso curto");
        shortRestButton.setTooltip(new Tooltip("Cada personagem no mapa gasta dados de vida até ficar quase cheio."));
        shortRestButton.setOnAction(e -> table.groupShortRest());
        Button longRestButton = new Button("Descanso longo");
        longRestButton.setTooltip(new Tooltip("Personagens no mapa recuperam PV, dados de vida e espaços de magia."));
        longRestButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Descanso longo pra todos os personagens com token no mapa?");
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK)
                    .ifPresent(b -> table.groupLongRest());
        });
        Button calcButton = new Button("Calculadora de encontro...");
        calcButton.setTooltip(new Tooltip("Dificuldade (2024) dos monstros do mapa (ou dos selecionados) pro nível do grupo."));
        calcButton.setOnAction(e -> onEncounterCalculator());
        box.getChildren().add(new javafx.scene.layout.FlowPane(6, 6, groupSaveButton, shortRestButton, longRestButton, calcButton));
        CheckBox approve = new CheckBox("Aprovar pedidos dos jogadores (dano, cura, efeitos)");
        approve.setTooltip(new Tooltip("Desligado: o pedido de um jogador (ex.: dano no goblin que ele acertou) e aplicado"
                + " direto. Ligado: aparece uma pergunta pra você aprovar ou recusar."));
        approve.setSelected(approveRequests);
        approve.setOnAction(e -> approveRequests = approve.isSelected());
        box.getChildren().add(approve);
        gmOnlyInitiativeButtons = List.of(rollAllButton, startButton, nextTurnButton, endButton,
                groupSaveButton, shortRestButton, longRestButton, calcButton);
        refreshInitiativeList();

        Label hint = new Label("Bolinha da linha: clique troca a cor (cinza, verde, amarelo, vermelho). Botão direito: "
                + "modificador, puxar da ficha, rolar. A ordem segue a iniciativa dos tokens (editar reordena na hora). Rodada e turno "
                + "sincronizam pra todos; só o mestre avança. Efeitos com duração (botão direito no token > "
                + "\"Efeito com duração...\") somem sozinhos quando a rodada vira.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        box.getChildren().add(hint);

        return new Tab("Combate", wrapInScroll(box));
    }

    // ---------------- Diario (anotacoes que o mestre revela) ----------------

    private Tab buildJournalTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        journalList = new ListView<>(journalItems);
        journalList.setPrefHeight(170);
        journalList.setPlaceholder(new Label("Nada no diário ainda."));
        journalList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.model.Handout h, boolean empty) {
                super.updateItem(h, empty);
                if (empty || h == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                boolean gm = networkService.isGm();
                setText(gm ? (h.isRevealed() ? "\u25CF  " : "\u25CB  ") + h.getTitle() : h.getTitle());
                setStyle(gm && h.isRevealed() ? "-fx-text-fill: #FFCF7F;" : "");
                setTooltip(new Tooltip(gm ? (h.isRevealed() ? "Revelada: os jogadores veem" : "Só você vê")
                        : "Clique duplo abre em janela"));
            }
        });
        journalViewer = new VBox(6);
        journalList.getSelectionModel().selectedItemProperty().addListener((o, a, h) -> showHandout(h));
        journalList.setOnMouseClicked(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && h != null) openHandoutWindow(h);
        });

        Button add = new Button("+ Nova");
        add.getStyleClass().add("button-primary");
        add.setOnAction(e -> editHandout(null));
        Button edit = new Button("Editar");
        edit.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h != null) editHandout(h);
        });
        Button reveal = new Button("Revelar / esconder");
        reveal.setTooltip(new Tooltip("Revelar manda a anotação pra todos os jogadores (e pra quem entrar depois)."));
        reveal.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h != null) setHandoutRevealed(h, !h.isRevealed());
        });
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Apagar \"" + h.getTitle() + "\" do diário?");
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
                List<com.vttcaseiro.model.Handout> all = handoutStore.load();
                all.removeIf(x -> x.getId().equals(h.getId()));
                handoutStore.save(all);
                if (h.isRevealed()) networkService.sendHandoutRemove(h.getId());
                refreshJournal();
            });
        });
        javafx.scene.layout.FlowPane gmButtons = new javafx.scene.layout.FlowPane(6, 6, add, edit, reveal, remove);
        Label hint = new Label();
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        gmOnlyJournalControls = List.of(gmButtons);
        Runnable byRole = () -> {
            boolean gm = networkService.isGm();
            gmButtons.setVisible(gm);
            gmButtons.setManaged(gm);
            hint.setText(gm ? "Só você vê as anotações até revelar (\u25CF = revelada). Clique duplo abre em janela."
                    : "Aqui aparecem as anotações que o mestre revelar. Clique duplo abre em janela.");
            refreshJournal();
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            byRole.run();
            // Mestre que acabou de hospedar/conectar: manda o que ja estava revelado.
            if (networkService.isConnected() && networkService.isGm()) {
                for (com.vttcaseiro.model.Handout h : handoutStore.load()) if (h.isRevealed()) networkService.sendHandout(h);
            }
        }));
        byRole.run();
        box.getChildren().addAll(sectionLabel("Diário da mesa"), journalList, gmButtons, divider(), journalViewer, hint,
                divider(), buildTablesSection());
        return new Tab("Diário", wrapInScroll(box));
    }

    private void refreshJournal() {
        if (journalList == null) return;
        String selected = journalList.getSelectionModel().getSelectedItem() == null ? null
                : journalList.getSelectionModel().getSelectedItem().getId();
        if (networkService.isGm()) journalItems.setAll(handoutStore.load());
        else journalItems.setAll(receivedHandouts.values());
        journalList.refresh();
        for (com.vttcaseiro.model.Handout h : journalItems) {
            if (h.getId().equals(selected)) {
                journalList.getSelectionModel().select(h);
                showHandout(h);
                return;
            }
        }
        showHandout(null);
    }

    private javafx.scene.image.Image handoutImage(com.vttcaseiro.model.Handout h) {
        if (h.getImageAssetId() == null || imageAssetService == null) return null;
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                    new java.io.ByteArrayInputStream(imageAssetService.readBytes(h.getImageAssetId())));
            return img.isError() ? null : img;
        } catch (IOException ex) {
            return null; // ainda chegando pela rede
        }
    }

    private void showHandout(com.vttcaseiro.model.Handout h) {
        if (journalViewer == null) return;
        journalViewer.getChildren().clear();
        if (h == null) return;
        Label title = new Label(h.getTitle());
        title.getStyleClass().add("label-title");
        title.setWrapText(true);
        journalViewer.getChildren().add(title);
        javafx.scene.image.Image img = handoutImage(h);
        if (img != null) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
            view.setPreserveRatio(true);
            view.setFitWidth(Math.min(330, img.getWidth()));
            journalViewer.getChildren().add(view);
        } else if (h.getImageAssetId() != null) {
            journalViewer.getChildren().add(new Label("(imagem chegando... selecione de novo em instantes)"));
        }
        Label text = new Label(h.getText());
        text.setWrapText(true);
        journalViewer.getChildren().add(text);
    }

    /** Janela grande (pra imagem de mapa/carta e leitura confortavel). */
    private void openHandoutWindow(com.vttcaseiro.model.Handout h) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #1B1411;");
        Label title = new Label(h.getTitle());
        title.getStyleClass().add("label-title");
        content.getChildren().add(title);
        javafx.scene.image.Image img = handoutImage(h);
        if (img != null) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
            view.setPreserveRatio(true);
            view.setFitWidth(Math.min(760, img.getWidth()));
            content.getChildren().add(view);
        }
        Label text = new Label(h.getText());
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 14px;");
        content.getChildren().add(text);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        javafx.stage.Stage window = new javafx.stage.Stage();
        window.initOwner(stage);
        window.setTitle(h.getTitle());
        Scene scene = new Scene(scroll, 820, 640);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        window.setScene(scene);
        window.show();
    }

    private void editHandout(com.vttcaseiro.model.Handout existing) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(existing == null ? "Nova anotação" : "Editar anotação");
        dialog.setHeaderText(existing != null && existing.isRevealed() ? "Revelada: os jogadores recebem a mudança" : null);
        dialog.initOwner(stage);
        TextField title = new TextField(existing == null ? "" : existing.getTitle());
        title.setPromptText("Carta do Barao");
        javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(existing == null ? "" : existing.getText());
        text.setWrapText(true);
        text.setPrefRowCount(10);
        String[] assetId = {existing == null ? null : existing.getImageAssetId()};
        Label imageInfo = new Label(assetId[0] == null ? "Sem imagem" : "Com imagem");
        imageInfo.getStyleClass().add("label-muted");
        Button chooseImage = new Button("Escolher imagem...");
        chooseImage.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            java.io.File f = chooser.showOpenDialog(stage);
            if (f == null) return;
            try {
                assetId[0] = imageAssetService.importFile(f.toPath());
                imageInfo.setText("Com imagem: " + f.getName());
            } catch (IOException ex) {
                showError("Não foi possível usar a imagem: " + ex.getMessage());
            }
        });
        Button noImage = new Button("Tirar imagem");
        noImage.setOnAction(e -> {
            assetId[0] = null;
            imageInfo.setText("Sem imagem");
        });
        VBox content = new VBox(8, new Label("Título"), title, new Label("Texto"), text,
                new HBox(8, chooseImage, noImage, imageInfo));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (title.getText().isBlank() && text.getText().isBlank()) return;
            List<com.vttcaseiro.model.Handout> all = handoutStore.load();
            com.vttcaseiro.model.Handout h = existing == null ? new com.vttcaseiro.model.Handout()
                    : all.stream().filter(x -> x.getId().equals(existing.getId())).findFirst().orElse(existing);
            h.setTitle(title.getText());
            h.setText(text.getText());
            h.setImageAssetId(assetId[0]);
            if (all.stream().noneMatch(x -> x.getId().equals(h.getId()))) all.add(h);
            handoutStore.save(all);
            if (h.isRevealed()) networkService.sendHandout(h);
            refreshJournal();
            journalItems.stream().filter(x -> x.getId().equals(h.getId())).findFirst()
                    .ifPresent(x -> journalList.getSelectionModel().select(x));
        });
    }

    private void setHandoutRevealed(com.vttcaseiro.model.Handout handout, boolean revealed) {
        List<com.vttcaseiro.model.Handout> all = handoutStore.load();
        for (com.vttcaseiro.model.Handout h : all) {
            if (!h.getId().equals(handout.getId())) continue;
            h.setRevealed(revealed);
            handoutStore.save(all);
            networkService.sendHandout(h);
            if (revealed) announce("O mestre revelou no diário: " + h.getTitle());
            else logEntries.add(LogEntry.system("\"" + h.getTitle() + "\" voltou a ser só do mestre."));
            break;
        }
        refreshJournal();
    }

    /** Dialogo da salvaguarda em grupo (CD, atributo, alvos, dano opcional). */
    private void onGroupSave() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Salvaguarda em grupo");
        dialog.setHeaderText("Todos rolam; o resultado vai pro chat");
        dialog.initOwner(stage);
        ComboBox<com.vttcaseiro.character.Ability> ability = new ComboBox<>(
                FXCollections.observableArrayList(com.vttcaseiro.character.Ability.values()));
        ability.setValue(com.vttcaseiro.character.Ability.DEXTERITY);
        ability.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(com.vttcaseiro.character.Ability a) { return a == null ? "" : a.abbreviationPt(); }
            @Override public com.vttcaseiro.character.Ability fromString(String s) { return null; }
        });
        TextField dc = new TextField("15");
        dc.setPrefColumnCount(4);
        ComboBox<String> who = new ComboBox<>(FXCollections.observableArrayList(
                "Alvos marcados", "Todos os tokens", "Só personagens (fichas)", "Só monstros e outros"));
        who.setValue(mapView.getTargets().isEmpty() ? "Todos os tokens" : "Alvos marcados");
        ComboBox<String> dmgType = new ComboBox<>(FXCollections.observableArrayList("(sem tipo)", "acido",
                "contundente", "frio", "fogo", "energia", "eletrico", "necrotico", "perfurante", "veneno",
                "psiquico", "radiante", "cortante", "trovejante"));
        dmgType.setValue("(sem tipo)");
        TextField damage = new TextField();
        damage.setPromptText("ex.: 8d6 (vazio = sem dano)");
        CheckBox half = new CheckBox("Quem passa leva metade");
        half.setSelected(true);
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Atributo"), ability);
        grid.addRow(1, new Label("CD"), dc);
        grid.addRow(2, new Label("Quem rola"), who);
        grid.addRow(3, new Label("Dano"), damage);
        grid.addRow(4, new Label("Tipo"), dmgType);
        grid.add(half, 1, 5);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            int cd;
            try {
                cd = Integer.parseInt(dc.getText().trim());
            } catch (NumberFormatException ex) {
                showError("CD inválida: " + dc.getText());
                return;
            }
            List<TokenModel> targets = new java.util.ArrayList<>();
            if (who.getValue().startsWith("Alvos")) targets.addAll(mapView.getTargets());
            else for (TokenModel t : sceneService.getScene().getTokens()) {
                boolean pc = t.getCharacterSheetId() != null;
                if (who.getValue().startsWith("Todos") || (pc == who.getValue().startsWith("Só personagens"))) {
                    targets.add(t);
                }
            }
            table.groupSave(ability.getValue(), cd, targets, damage.getText(), half.isSelected(),
                    com.vttcaseiro.character.DamageTypes.find(dmgType.getValue()));
        });
    }

    // ---------------- Macros ----------------

    private void refreshMacroRow() {
        if (macroRow == null) return;
        macroRow.getChildren().clear();
        List<com.vttcaseiro.library.MacroStore.Macro> macros = macroStore.load();
        for (com.vttcaseiro.library.MacroStore.Macro m : macros) {
            Button b = new Button(m.getName());
            b.getStyleClass().addAll("chip", "chip-macro");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setTooltip(new Tooltip(String.join("\n", m.lines()) + "\n\n(botão direito: editar/remover)"));
            b.setOnAction(e -> runMacro(m));
            javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Editar...");
            edit.setOnAction(e -> editMacro(m));
            javafx.scene.control.MenuItem remove = new javafx.scene.control.MenuItem("Remover");
            remove.setOnAction(e -> {
                List<com.vttcaseiro.library.MacroStore.Macro> all = macroStore.load();
                all.removeIf(x -> x.getName().equals(m.getName()));
                macroStore.save(all);
                refreshMacroRow();
            });
            menu.getItems().addAll(edit, remove);
            b.setContextMenu(menu);
            macroRow.getChildren().add(b);
        }
        Button add = new Button("+ Macro");
        add.getStyleClass().add("chip");
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setTooltip(new Tooltip("Cria um botão que roda uma ou mais linhas do chat (ex.: /r 1d20+7)"));
        add.setOnAction(e -> editMacro(null));
        macroRow.getChildren().add(add);
    }

    /** Roda cada linha da macro como se fosse digitada no chat. */
    private void runMacro(com.vttcaseiro.library.MacroStore.Macro macro) {
        for (String line : macro.lines()) onChatInput(line);
    }

    private void editMacro(com.vttcaseiro.library.MacroStore.Macro existing) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(existing == null ? "Nova macro" : "Editar macro");
        dialog.setHeaderText("Uma linha por comando do chat");
        dialog.initOwner(stage);
        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("Ataque furtivo");
        javafx.scene.control.TextArea commands = new javafx.scene.control.TextArea(
                existing == null ? "" : existing.getCommands());
        commands.setPromptText("/r 1d20+7\n/r 1d6+4+3d6\nAtaque furtivo!");
        commands.setPrefRowCount(5);
        Label hint = new Label("Vale tudo que o chat aceita: /r (rolar), /gr (escondida), /w Nome (sussurro), texto.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        VBox content = new VBox(8, new Label("Nome do botão"), name, new Label("Comandos"), commands, hint);
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (name.getText().isBlank() || commands.getText().isBlank()) return;
            List<com.vttcaseiro.library.MacroStore.Macro> all = macroStore.load();
            if (existing != null) all.removeIf(x -> x.getName().equals(existing.getName()));
            all.removeIf(x -> x.getName().equalsIgnoreCase(name.getText().trim()));
            all.add(new com.vttcaseiro.library.MacroStore.Macro(name.getText(), commands.getText()));
            macroStore.save(all);
            refreshMacroRow();
        });
    }

    private VBox buildChatPanel() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.getStyleClass().add("chat-panel");

        Label label = sectionLabel("Chat");

        ListView<LogEntry> chatList = new ListView<>(logEntries);
        chatListView = chatList;
        chatList.setCellFactory(lv -> new ChatListCell(new ChatListCell.ChatActions() {
            @Override
            public javafx.scene.image.Image chatImage(String assetId) {
                return chatImageOf(assetId);
            }

            @Override
            public void openImage(String assetId) {
                openChatImage(assetId);
            }

            @Override
            public void saveImage(String assetId) {
                saveChatImage(assetId);
            }

            @Override
            public void applyToSelectedToken(int total, double multiplier, boolean heal) {
                TokenModel target = mapView.getSelectedToken();
                if (target == null) {
                    showError("Clique num token no mapa pra seleciona-lo e tente de novo.");
                    return;
                }
                if (!table.canControl(target)) {
                    showError("Você não controla " + target.getName() + ".");
                    return;
                }
                int amount = (int) Math.floor(total * multiplier);
                if (heal) tokenActions.heal(target, amount); else tokenActions.damage(target, amount);
            }

            @Override
            public String followUpButton(LogEntry entry) {
                return followUpOf(entry) == null ? null : entry.isCriticalHit() ? "Rolar dano crítico" : "Rolar dano";
            }

            @Override
            public void followUp(LogEntry entry) {
                FollowUp f = followUpOf(entry);
                if (f == null) return;
                boolean crit = entry.isCriticalHit();
                f.roller().roll(crit ? f.damageLabel().replaceFirst("Dano", "Dano CRÍTICO") + (f.damageLabel().contains("Dano") ? "" : " CRÍTICO") : f.damageLabel(),
                        crit ? com.vttcaseiro.dice.DiceRoller.doubleDice(f.expression()) : f.expression(), entry.isPrivate());
            }

            @Override
            public void applyToTargets(int total, double multiplier, boolean heal, String rollText) {
                List<TokenModel> targets = mapView.getTargets();
                if (targets.isEmpty()) {
                    showError("Nenhum alvo marcado. Ctrl+clique nos tokens (ou botão direito > Mirar).");
                    return;
                }
                int amount = (int) Math.floor(total * multiplier);
                if (heal) table.healTargets(targets, amount, null);
                else table.damageTargets(targets, amount, damageTypeIn(rollText), null);
            }
        }));
        VBox.setVgrow(chatList, Priority.ALWAYS);
        chatList.setPlaceholder(new Label("Nada no chat ainda. Role um dado!"));
        logEntries.addListener((ListChangeListener<LogEntry>) c -> {
            if (!logEntries.isEmpty()) {
                chatList.scrollTo(logEntries.size() - 1);
            }
        });

        TextField chatInput = new TextField();
        chatInput.getStyleClass().add("chat-input");
        chatInput.setPromptText("Mensagem ou /r 2d6+3   (/ajuda)");
        chatInput.setTooltip(new Tooltip("/r 2d6+3  rola\n/gr 1d20  rolagem escondida (so voce e o mestre)\n"
                + "/w Nome mensagem  sussurro\n/ajuda  todos os comandos"));
        Button sendButton = new Button("Enviar");
        sendButton.getStyleClass().add("button-primary");
        sendButton.setMinWidth(Region.USE_PREF_SIZE);
        Runnable send = () -> {
            onChatInput(chatInput.getText());
            chatInput.clear();
        };
        chatInput.setOnAction(e -> send.run());
        sendButton.setOnAction(e -> send.run());
        Button imageButton = new Button("", com.vttcaseiro.view.Icons.icon("image", 16));
        imageButton.setMinWidth(Region.USE_PREF_SIZE);
        imageButton.setTooltip(new Tooltip("Mandar uma imagem no chat (JPG, JPEG, PNG ou GIF, até 4 MB). O texto da caixa"
                + " vai junto como legenda. Também dá pra arrastar o arquivo pro chat."));
        imageButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Imagem para o chat");
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            File file = chooser.showOpenDialog(stage);
            if (file == null) return;
            if (sendChatImage(file.toPath(), chatInput.getText())) chatInput.clear();
        });
        chatList.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && e.getDragboard().getFiles().stream()
                    .anyMatch(f -> com.vttcaseiro.view.ImageFiles.accepts(f.getName()))) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        chatList.setOnDragDropped(e -> {
            boolean ok = false;
            if (e.getDragboard().hasFiles()) {
                for (File f : e.getDragboard().getFiles()) {
                    if (com.vttcaseiro.view.ImageFiles.accepts(f.getName())) ok |= sendChatImage(f.toPath(), "");
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        HBox inputRow = new HBox(6, chatInput, imageButton, sendButton);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        // Todos os dados de D&D: clique rola 1, botao direito escolhe quantos.
        javafx.scene.layout.FlowPane diceRow = new javafx.scene.layout.FlowPane(3, 4);
        diceRow.setAlignment(Pos.CENTER_LEFT);
        for (int sides : new int[]{4, 6, 8, 10, 12, 20, 100}) {
            Button b = quickRollButton("d" + sides, "1d" + sides);
            b.setMinWidth(Region.USE_PREF_SIZE);
            diceRow.getChildren().add(b);
        }
        Button advButton = new Button("Vantagem");
        advButton.getStyleClass().add("chip");
        advButton.setOnAction(e -> onRollAdvantage());
        Button disButton = new Button("Desvantagem");
        disButton.getStyleClass().add("chip");
        disButton.setOnAction(e -> onRollDisadvantage());
        hiddenQuickRolls = new CheckBox("Escondida");
        hiddenQuickRolls.setTooltip(new Tooltip("Rolagens daqui e do /r ficam só pra você e o mestre."));
        // FlowPane quebra linha quando o painel do chat e estreito, em vez de
        // espremer os botoes ate virarem "...".
        javafx.scene.layout.FlowPane quickRow = new javafx.scene.layout.FlowPane(6, 4,
                advButton, disButton, hiddenQuickRolls);
        quickRow.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node n : quickRow.getChildren()) {
            if (n instanceof javafx.scene.control.Control c) c.setMinWidth(Region.USE_PREF_SIZE);
        }

        macroRow = new javafx.scene.layout.FlowPane(5, 4);
        macroRow.setAlignment(Pos.CENTER_LEFT);
        refreshMacroRow();

        // macros na mesma linha de Vantagem/Desvantagem: uma linha a menos embaixo do chat
        quickRow.getChildren().add(macroRow);
        box.getChildren().addAll(label, chatList, diceRow, quickRow, inputRow);
        return box;
    }

    /** Guarda a imagem na mesa e manda no chat (offline: so aparece aqui). */
    private boolean sendChatImage(Path file, String caption) {
        if (!com.vttcaseiro.view.ImageFiles.accepts(file.getFileName().toString())) {
            showError("Formato não aceito. Use JPG, JPEG, PNG ou GIF.");
            return false;
        }
        String assetId;
        try {
            assetId = imageAssetService.importFile(file);
        } catch (IOException ex) {
            showError("Não deu pra mandar a imagem: " + ex.getMessage());
            return false;
        }
        String text = caption == null ? "" : caption.trim();
        if (networkService.isConnected()) {
            networkService.sendChatImage(assetId, text, null);
        } else {
            logEntries.add(LogEntry.image(networkService.getPlayerName(), text, assetId, null));
        }
        return true;
    }

    private javafx.scene.image.Image chatImageOf(String assetId) {
        javafx.scene.image.Image cached = chatImages.get(assetId);
        if (cached != null) return cached;
        if (assetId == null || !imageAssetService.exists(assetId)) return null;
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                    new java.io.ByteArrayInputStream(imageAssetService.readBytes(assetId)));
            chatImages.put(assetId, img);
            return img;
        } catch (IOException ex) {
            return null;
        }
    }

    /** Janela com a imagem do chat em tamanho cheio (cabe na tela; maior que ela, rola). */
    private void openChatImage(String assetId) {
        javafx.scene.image.Image img = chatImageOf(assetId);
        if (img == null) return;
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
        view.setPreserveRatio(true);
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        double maxW = screen.getWidth() * 0.85;
        double maxH = screen.getHeight() * 0.85;
        double scale = Math.min(1, Math.min(maxW / Math.max(1, img.getWidth()), maxH / Math.max(1, img.getHeight())));
        view.setFitWidth(img.getWidth() * scale);
        view.setFitHeight(img.getHeight() * scale);
        javafx.scene.control.ScrollPane pane = new javafx.scene.control.ScrollPane(new StackPane(view));
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);
        Stage win = new Stage();
        win.initOwner(stage);
        win.setTitle("Imagem do chat");
        win.setScene(new Scene(pane, img.getWidth() * scale + 4, img.getHeight() * scale + 4));
        win.getScene().setOnKeyPressed(k -> {
            if (k.getCode() == javafx.scene.input.KeyCode.ESCAPE) win.close();
        });
        win.show();
    }

    private void saveChatImage(String assetId) {
        if (assetId == null || !imageAssetService.exists(assetId)) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar imagem");
        String ext = assetId.substring(assetId.lastIndexOf('.'));
        chooser.setInitialFileName("imagem-chat" + ext);
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            java.nio.file.Files.write(file.toPath(), imageAssetService.readBytes(assetId));
        } catch (IOException ex) {
            showError("Não deu pra salvar: " + ex.getMessage());
        }
    }

    private Button quickRollButton(String label, String expression) {
        Button b = new Button(label);
        b.getStyleClass().addAll("chip", "chip-dice");
        b.setOnAction(e -> onRollDice(expression));
        String die = expression.substring(expression.indexOf('d'));
        javafx.scene.control.ContextMenu many = new javafx.scene.control.ContextMenu();
        for (int n = 2; n <= 10; n++) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("Rolar " + n + die);
            String expr = n + die;
            item.setOnAction(e -> onRollDice(expr));
            many.getItems().add(item);
        }
        b.setContextMenu(many);
        b.setTooltip(new Tooltip("Clique: rola 1" + die + "\nBotão direito: rolar vários"));
        return b;
    }

    /** "" (ou so espaco) vira null -- pros campos de senha opcionais da
     * aba Rede: campo vazio = sem senha nenhuma, igual sempre foi. */
    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-section");
        return label;
    }

    // ==================================================================
    // Acoes - cenas (mapas) da mesa
    // ==================================================================

    private void onNewScene() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher imagem da nova cena");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = chooser.showOpenDialog(null);
        if (file == null) {
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog(file.getName().replaceAll("\\.[a-zA-Z0-9]+$", ""));
        nameDialog.setTitle("Nome da cena");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty()) {
            return;
        }

        SceneModel newScene = new SceneModel();
        newScene.setBackgroundImagePath(file.getAbsolutePath());
        Optional<String> bgAsset = importIntoAssetStoreOrWarn(file);
        if (bgAsset.isPresent()) {
            newScene.setBackgroundImageAssetId(bgAsset.get());
        } else {
            newScene.setBackgroundImageData(encodeImageOrWarn(file.getAbsolutePath()));
        }
        try {
            mesaService.saveMap(newScene, name.get());
            sceneService.replaceScene(newScene);
            networkService.sendSceneSwitch(newScene);
            currentMapName = name.get();
            refreshMapList();
            logEntries.add(LogEntry.system("Cena criada: " + newScene.getName()));
        } catch (IllegalArgumentException | IOException ex) {
            showError("Não foi possível salvar a cena: " + ex.getMessage());
        }
    }

    /** Importa mapa de gerador: masmorra do Watabou (JSON) ou imagem do Azgaar/Watabou. */
    private void onImportGeneratedMap() {
        String dungeon = "Watabou - masmorra (One Page Dungeon, .json): cena pronta com paredes e portas";
        String city = "Watabou - cidade, vila, caverna... (imagem .png/.jpg)";
        String world = "Azgaar - mapa do mundo (imagem .png/.jpg + .json opcional com as cidades)";
        javafx.scene.control.ChoiceDialog<String> pick = new javafx.scene.control.ChoiceDialog<>(dungeon, dungeon, city, world);
        pick.initOwner(stage);
        pick.setTitle("Importar mapa");
        pick.setHeaderText("De onde vem o mapa?\n"
                + "No One Page Dungeon: botão direito no mapa > Export > JSON.\n"
                + "No Azgaar: Menu > Export > PNG (e, se quiser as cidades, JSON > Full).\n"
                + "Nos outros geradores do Watabou: exporte como PNG.");
        pick.setContentText("Tipo:");
        Optional<String> choice = pick.showAndWait();
        if (choice.isEmpty()) return;
        try {
            if (choice.get().equals(dungeon)) importWatabouDungeon();
            else importGeneratorImage(choice.get().equals(world));
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível importar o mapa: " + ex.getMessage());
        }
    }

    private void importWatabouDungeon() throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("JSON da masmorra (One Page Dungeon)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        com.vttcaseiro.importer.WatabouDungeon.Result d =
                com.vttcaseiro.importer.WatabouDungeon.parse(java.nio.file.Files.readString(file.toPath()));
        Optional<String> name = askSceneName(d.title());
        if (name.isEmpty()) return;
        SceneModel scene = new SceneModel();
        scene.setBackgroundImageAssetId(mesaRepository.getImageAssetStore().store(d.png(), "masmorra.png"));
        scene.getGrid().setCellSize(d.cellPx());
        scene.getGrid().setFeetPerCell(5);
        scene.getGrid().setVisible(false); // a grade ja esta desenhada na imagem
        scene.setWalls(new java.util.ArrayList<>(d.walls()));
        scene.setPins(new java.util.ArrayList<>(d.pins()));
        scene.setVisionEnabled(true);
        String journal = d.journalText().isBlank() ? null : d.journalText();
        createImportedScene(scene, name.get(), journal == null ? null : d.title(), journal);
        logEntries.add(LogEntry.system("Masmorra importada: " + d.rooms() + " salas, " + d.doors() + " portas, "
                + d.walls().size() + " paredes. Visão ligada; as notas foram pro Diário e viraram pins ocultos (clique pra ler)."));
    }

    private void importGeneratorImage(boolean azgaar) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(azgaar ? "Imagem do mundo (Azgaar)" : "Imagem do mapa (Watabou)");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        com.vttcaseiro.importer.MapImageFit.Fitted img = com.vttcaseiro.importer.MapImageFit.fit(
                java.nio.file.Files.readAllBytes(file.toPath()), file.getName());
        com.vttcaseiro.importer.AzgaarWorld.Result world = null;
        boolean labels = false;
        if (azgaar) {
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION, "Tem o JSON do mesmo mundo (Export > JSON > Full)? "
                    + "Com ele, os reinos e as maiores cidades vão pro Diário, e as cidades podem virar pins clicáveis no mapa "
                    + "(cada pin abre o gerador de cidade do Watabou e pode ligar o mapa da cidade).",
                    new javafx.scene.control.ButtonType("Só o Diário"), new javafx.scene.control.ButtonType("Cidades como pins"),
                    javafx.scene.control.ButtonType.NO);
            ask.initOwner(stage);
            ask.setHeaderText("Cidades do Azgaar (opcional)");
            Optional<javafx.scene.control.ButtonType> b = ask.showAndWait();
            if (b.isPresent() && b.get() != javafx.scene.control.ButtonType.NO) {
                labels = b.get().getText().startsWith("Cidades");
                FileChooser jc = new FileChooser();
                jc.setTitle("JSON do mundo (Azgaar, Full)");
                jc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
                File jf = jc.showOpenDialog(stage);
                if (jf != null) world = com.vttcaseiro.importer.AzgaarWorld.parse(java.nio.file.Files.readString(jf.toPath()));
            }
        }
        String suggested = world != null ? world.mapName() : file.getName().replaceAll("\\.[a-zA-Z0-9]+$", "");
        Optional<String> name = askSceneName(suggested);
        if (name.isEmpty()) return;
        SceneModel scene = new SceneModel();
        scene.setBackgroundImageAssetId(mesaRepository.getImageAssetStore().store(img.bytes(), "mapa" + img.extension()));
        if (azgaar) {
            scene.getGrid().setVisible(false);
            scene.getGrid().setFeetPerCell(5280); // 1 celula ~ 1 milha: a regua vira distancia de viagem
        }
        if (world != null && labels) {
            scene.setPins(world.pins(img.width(), img.height(), 60));
        }
        createImportedScene(scene, name.get(), world == null ? null : world.mapName(), world == null ? null : world.journalText());
        if (img.changed()) {
            logEntries.add(LogEntry.system("Imagem reduzida pra caber na mesa (" + img.width() + "x" + img.height() + ")."));
        }
    }

    private Optional<String> askSceneName(String suggested) {
        TextInputDialog nameDialog = new TextInputDialog(suggested);
        nameDialog.initOwner(stage);
        nameDialog.setTitle("Nome da cena");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome:");
        return nameDialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty());
    }

    private void createImportedScene(SceneModel scene, String name, String journalTitle, String journalText)
            throws IOException {
        if (mapListView != null && mesaService.listMaps().contains(name)) {
            throw new IOException("Já existe uma cena chamada \"" + name + "\".");
        }
        mesaService.saveMap(scene, name);
        sceneService.replaceScene(scene);
        networkService.sendSceneSwitch(scene);
        currentMapName = name;
        refreshMapList();
        logEntries.add(LogEntry.system("Cena criada: " + scene.getName()));
        if (journalText != null && !journalText.isBlank()) {
            List<com.vttcaseiro.model.Handout> all = handoutStore.load();
            all.add(new com.vttcaseiro.model.Handout(journalTitle == null ? name : journalTitle, journalText));
            handoutStore.save(all);
            refreshJournal();
        }
    }

    /** Fila de cenas preparadas: a ordem da sessao, com "próxima cena" num clique. */
    private javafx.scene.Node buildSceneQueue() {
        currentSceneLabel = new Label();
        currentSceneLabel.getStyleClass().add("label-muted");
        ListView<String> queue = new ListView<>();
        queue.setPrefHeight(120);
        queue.setPlaceholder(new Label("Fila vazia: selecione uma cena acima e \"Por na fila\"."));
        Runnable reload = () -> {
            queue.getItems().setAll(sceneQueueStore.load());
            currentSceneLabel.setText("Cena atual: " + (currentMapName != null ? currentMapName
                    : sceneService.getScene().getName()));
        };
        reload.run();
        Button enqueue = new Button("Por na fila");
        enqueue.setOnAction(e -> {
            SceneEntry entry = selectedSceneEntry();
            if (entry == null || entry.folder()) return;
            String sel = entry.name();
            List<String> q = sceneQueueStore.load();
            q.add(sel);
            sceneQueueStore.save(q);
            reload.run();
        });
        Button up = new Button("Subir");
        Button down = new Button("Descer");
        for (Button b : List.of(up, down)) {
            b.setOnAction(e -> {
                int i = queue.getSelectionModel().getSelectedIndex();
                int j = i + (b == up ? -1 : 1);
                List<String> q = sceneQueueStore.load();
                if (i < 0 || j < 0 || j >= q.size()) return;
                java.util.Collections.swap(q, i, j);
                sceneQueueStore.save(q);
                reload.run();
                queue.getSelectionModel().select(j);
            });
        }
        Button removeQ = new Button("Tirar");
        removeQ.setOnAction(e -> {
            int i = queue.getSelectionModel().getSelectedIndex();
            List<String> q = sceneQueueStore.load();
            if (i < 0 || i >= q.size()) return;
            q.remove(i);
            sceneQueueStore.save(q);
            reload.run();
        });
        Button open = new Button("Abrir pra todos");
        open.setOnAction(e -> {
            String sel = queue.getSelectionModel().getSelectedItem();
            if (sel != null) onOpenMapFromMesa(sel);
            reload.run();
        });
        queue.setOnMouseClicked(e -> {
            String sel = queue.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) {
                onOpenMapFromMesa(sel);
                reload.run();
            }
        });
        Button next = new Button("Próxima cena  \u25B6");
        next.getStyleClass().add("button-primary");
        next.setTooltip(new Tooltip("Abre pra todos a cena seguinte da fila (a atual é salva antes)."));
        next.setOnAction(e -> {
            List<String> q = sceneQueueStore.load();
            java.util.Optional<String> nextScene = com.vttcaseiro.service.SceneQueue.next(q, currentMapName);
            if (nextScene.isEmpty()) {
                logEntries.add(LogEntry.system(q.isEmpty() ? "A fila de cenas está vazia." : "Fim da fila de cenas."));
                return;
            }
            onOpenMapFromMesa(nextScene.get());
            reload.run();
            queue.getSelectionModel().select(nextScene.get());
        });
        VBox box = new VBox(6, sectionLabel("Fila de cenas da sessão"), currentSceneLabel, queue,
                new javafx.scene.layout.FlowPane(6, 6, next, open, enqueue, up, down, removeQ));
        return box;
    }

    // ---------------- Som da mesa ----------------

    /** Toca/para conforme a ordem: arquivo local (quem hospeda) ou o link de quem hospeda. */
    private void playAudio(com.vttcaseiro.model.AudioCommand cmd) {
        if (cmd.isStop()) {
            soundPlayer.apply(cmd, null);
            return;
        }
        String uri = null;
        try {
            if (audioStore != null && audioStore.exists(cmd.getAssetId())) {
                uri = audioStore.path(cmd.getAssetId()).toUri().toString();
            } else if (networkService.remoteAudioBase() != null && audioStore != null
                    && com.vttcaseiro.library.AudioAssetStore.isValidId(cmd.getAssetId())) {
                // Baixa uma vez pra pasta de sons desta mesa (fica de cache) e toca de la.
                java.nio.file.Path target = audioStore.path(cmd.getAssetId());
                Thread t = new Thread(() -> {
                    try {
                        networkService.downloadAudio(cmd.getAssetId(), target);
                        Platform.runLater(() -> soundPlayer.apply(cmd, target.toUri().toString()));
                    } catch (IOException ex) {
                        Platform.runLater(() -> logEntries.add(LogEntry.system("Som \"" + cmd.getName()
                                + "\" não baixou de quem hospeda (porta do jogo + 1 liberada?): " + ex.getMessage())));
                    }
                }, "baixar-som");
                t.setDaemon(true);
                t.start();
                return;
            }
        } catch (IOException ignored) {
            uri = null;
        }
        if (uri == null) {
            logEntries.add(LogEntry.system("Som \"" + cmd.getName() + "\" não disponível nesta maquina."));
            return;
        }
        soundPlayer.apply(cmd, uri);
    }

    private void gmAudio(com.vttcaseiro.model.AudioCommand cmd) {
        playAudio(cmd);
        networkService.sendAudio(cmd);
    }

    private Tab buildSoundTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        // --- mestre: biblioteca e controles ---
        ListView<com.vttcaseiro.model.SoundEntry> library = new ListView<>();
        library.setPrefHeight(170);
        library.setPlaceholder(new Label("Nenhum som ainda. \"+ Adicionar sons...\" (mp3, ogg, wav)."));
        Runnable reload = () -> library.getItems().setAll(soundIndex.load());
        reload.run();
        Button add = new Button("+ Adicionar sons...");
        add.getStyleClass().add("button-primary");
        add.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Escolher músicas/sons");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                    "Áudio", "*.mp3", "*.ogg", "*.wav", "*.m4a", "*.aac", "*.webm"));
            List<java.io.File> files = chooser.showOpenMultipleDialog(stage);
            if (files == null || audioStore == null) return;
            List<com.vttcaseiro.model.SoundEntry> all = soundIndex.load();
            for (java.io.File f : files) {
                try {
                    String id = audioStore.importFile(f.toPath());
                    all.removeIf(x -> id.equals(x.getId()));
                    all.add(new com.vttcaseiro.model.SoundEntry(id, f.getName().replaceAll("\\.[^.]+$", "")));
                } catch (IOException ex) {
                    showError(f.getName() + ": " + ex.getMessage());
                }
            }
            soundIndex.save(all);
            reload.run();
        });
        Button music = new Button("Tocar como música");
        Button ambient = new Button("Como ambiente");
        Button sfx = new Button("Efeito (uma vez)");
        for (Button b : List.of(music, ambient, sfx)) {
            b.setOnAction(e -> {
                com.vttcaseiro.model.SoundEntry se = library.getSelectionModel().getSelectedItem();
                if (se == null) return;
                String ch = b == music ? com.vttcaseiro.model.AudioCommand.MUSIC
                        : b == ambient ? com.vttcaseiro.model.AudioCommand.AMBIENT : com.vttcaseiro.model.AudioCommand.SFX;
                gmAudio(com.vttcaseiro.model.AudioCommand.play(ch, se.getId(), se.getName()));
            });
        }
        Button stopMusic = new Button("Parar música");
        stopMusic.setOnAction(e -> gmAudio(com.vttcaseiro.model.AudioCommand.stop(com.vttcaseiro.model.AudioCommand.MUSIC)));
        Button stopAmbient = new Button("Parar ambiente");
        stopAmbient.setOnAction(e -> gmAudio(com.vttcaseiro.model.AudioCommand.stop(com.vttcaseiro.model.AudioCommand.AMBIENT)));
        Button remove = new Button("Tirar da lista");
        remove.setOnAction(e -> {
            com.vttcaseiro.model.SoundEntry se = library.getSelectionModel().getSelectedItem();
            if (se == null) return;
            List<com.vttcaseiro.model.SoundEntry> all = soundIndex.load();
            all.removeIf(x -> se.getId().equals(x.getId()));
            soundIndex.save(all);
            reload.run();
        });
        Label hint = new Label("Música e ambiente ficam em loop e tocam pra todos (quem entra depois também ouve). "
                + "Quem joga pelo programa ou pelo navegador baixa o som do seu computador.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        VBox gmBox = new VBox(6, sectionLabel("Biblioteca de sons (mestre)"), library,
                new javafx.scene.layout.FlowPane(6, 6, add, music, ambient, sfx, stopMusic, stopAmbient, remove), hint, divider());
        Runnable gmOnly = () -> {
            boolean gm = networkService.isGm();
            gmBox.setVisible(gm);
            gmBox.setManaged(gm);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(gmOnly));
        gmOnly.run();
        // --- todos: tocando agora, volume, dados animados ---
        Label playing = new Label();
        playing.setWrapText(true);
        playing.setStyle("-fx-text-fill: #FFCF7F;");
        Runnable refreshPlaying = () -> {
            String m = soundPlayer.nowPlaying(com.vttcaseiro.model.AudioCommand.MUSIC);
            String a = soundPlayer.nowPlaying(com.vttcaseiro.model.AudioCommand.AMBIENT);
            playing.setText("Música: " + (m == null ? "--" : m) + "\nAmbiente: " + (a == null ? "--" : a));
        };
        soundPlayer.setOnChange(() -> Platform.runLater(refreshPlaying));
        refreshPlaying.run();
        VBox volumes = new VBox(4);
        for (String[] ch : new String[][]{{com.vttcaseiro.model.AudioCommand.MUSIC, "Música"},
                {com.vttcaseiro.model.AudioCommand.AMBIENT, "Ambiente"}, {com.vttcaseiro.model.AudioCommand.SFX, "Efeitos"}}) {
            javafx.scene.control.Slider slider = new javafx.scene.control.Slider(0, 1, soundPlayer.volume(ch[0]));
            slider.setPrefWidth(200);
            slider.valueProperty().addListener((o, a, v) -> soundPlayer.setVolume(ch[0], v.doubleValue()));
            Label l = new Label(ch[1]);
            l.setMinWidth(70);
            volumes.getChildren().add(new HBox(8, l, slider));
        }
        CheckBox mute = new CheckBox("Silenciar tudo");
        mute.setOnAction(e -> soundPlayer.setMuted(mute.isSelected()));
        CheckBox dice = new CheckBox("Dados animados no mapa");
        dice.setSelected(AppSettings.getBool("dice.animated", true));
        dice.setOnAction(e -> AppSettings.setBool("dice.animated", dice.isSelected()));
        box.getChildren().addAll(gmBox, sectionLabel("Tocando agora"), playing, sectionLabel("Volume (só nesta maquina)"),
                volumes, mute, divider(), dice);
        return new Tab("Som", wrapInScroll(box));
    }

    // ---------------- NPC rapido ----------------

    private void onQuickNpc() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("NPC rápido");
        dialog.setHeaderText("Vira uma ficha de monstro simples + token no mapa");
        dialog.initOwner(stage);
        TextField name = new TextField();
        name.setPromptText("Taverneiro Bram");
        TextField ac = new TextField("12");
        TextField hp = new TextField("11");
        TextField dex = new TextField("10");
        TextField atkName = new TextField("Porrete");
        TextField atkBonus = new TextField("+3");
        TextField atkDamage = new TextField("1d6+1 contundente");
        TextField notes = new TextField();
        notes.setPromptText("Gentil, deve dinheiro ao Barao");
        String[] portrait = {null};
        Label portraitInfo = new Label("Sem retrato");
        portraitInfo.getStyleClass().add("label-muted");
        Button choose = new Button("Retrato...");
        choose.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            java.io.File f = chooser.showOpenDialog(stage);
            if (f == null) return;
            try {
                portrait[0] = imageAssetService.importFile(f.toPath());
                portraitInfo.setText(f.getName());
            } catch (IOException ex) {
                showError("Retrato: " + ex.getMessage());
            }
        });
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Nome"), name);
        g.addRow(1, new Label("CA"), ac, new Label("PV"), hp, new Label("DES"), dex);
        g.addRow(2, new Label("Ataque"), atkName, new Label("bonus"), atkBonus);
        g.addRow(3, new Label("Dano"), atkDamage);
        g.addRow(4, new Label("Notas"), notes);
        g.addRow(5, choose, portraitInfo);
        dialog.getDialogPane().setContent(g);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            try {
                MobSheet npc = buildQuickNpc(name.getText(), ac.getText(), hp.getText(), dex.getText(),
                        atkName.getText(), atkBonus.getText(), atkDamage.getText(), notes.getText(), portrait[0]);
                mobService.save(npc);
                refreshMobList();
                TokenModel token = com.vttcaseiro.service.TokenLink.tokenFromMob(npc, npc.getName());
                placeAtViewCenter(token);
                addTokenToScene(token);
                logEntries.add(LogEntry.system("NPC criado: " + npc.getName() + " (ficha na lista de monstros)."));
            } catch (IOException | RuntimeException ex) {
                showError("Não foi possível criar o NPC: " + ex.getMessage());
            }
        });
    }

    /** Monta a ficha de monstro do NPC rapido (o ataque no formato que o MobAttack le). */
    static MobSheet buildQuickNpc(String name, String ac, String hp, String dex, String atkName, String atkBonus,
                                  String atkDamage, String notes, String portraitAssetId) {
        MobSheet npc = new MobSheet(name == null || name.isBlank() ? "NPC" : name.strip());
        npc.setArmorClass(parseIntOr(ac, 10));
        npc.setHitPointsAverage(parseIntOr(hp, 4));
        npc.setHitPointsFormula("");
        npc.setDexterity(parseIntOr(dex, 10));
        npc.setCreatureType("NPC");
        String dmg = atkDamage == null ? "" : atkDamage.strip();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d*d\\d+(?:\\s*[+-]\\s*\\d+)?)\\s*(.*)$").matcher(dmg);
        if (atkName != null && !atkName.isBlank()) {
            String bonus = atkBonus == null || atkBonus.isBlank() ? "+0" : atkBonus.strip();
            if (!bonus.startsWith("+") && !bonus.startsWith("-")) bonus = "+" + bonus;
            String hit = m.matches() ? "Hit: (" + m.group(1).replace(" ", "") + ") " + m.group(2) + " damage." : "Hit: " + dmg;
            npc.getActions().add(new com.vttcaseiro.character.CharacterTrait(atkName.strip(),
                    "Attack Roll: " + bonus + ". " + hit, "action"));
        }
        if (notes != null && !notes.isBlank()) {
            npc.getTraits().add(new com.vttcaseiro.character.CharacterTrait("Notas", notes.strip(), "trait"));
        }
        npc.setPortraitImageAssetId(portraitAssetId);
        return npc;
    }

    private static int parseIntOr(String text, int def) {
        try {
            return Integer.parseInt(text == null ? "" : text.strip().replace("+", ""));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void onOpenMapFromMesa(String name) {
        try {
            SceneModel loaded = mesaService.loadMap(name);
            if (imageAssetService.migrateInlineImages(loaded)) {
                // Cena salva no formato antigo (imagens em base64 dentro do
                // .json): converte pra assets e regrava, menor e mais rapida.
                mesaService.saveMap(loaded, name);
                logEntries.add(LogEntry.system("Cena \"" + name + "\" convertida pro formato novo de imagens (arquivo menor)."));
            }
            sceneService.replaceScene(loaded);
            networkService.sendSceneSwitch(loaded);
            currentMapName = name;
            if (currentSceneLabel != null) currentSceneLabel.setText("Cena atual: " + name);
            logEntries.add(LogEntry.system("Cena aberta: " + name));
        } catch (IOException ex) {
            showError("Não foi possível abrir: " + ex.getMessage());
        } catch (RuntimeException ex) {
            showError("Esse arquivo não é uma cena válida: " + ex.getMessage());
        }
    }

    private void onSaveCurrentScene() {
        SceneModel scene = sceneService.getScene();
        String currentName = scene.getName();
        String nameToUse = currentName;
        if (currentName == null || currentName.isBlank() || currentName.equals("Novo mapa")) {
            TextInputDialog dialog = new TextInputDialog("Cena 1");
            dialog.setTitle("Salvar cena");
            dialog.setHeaderText(null);
            dialog.setContentText("Nome da cena:");
            Optional<String> name = dialog.showAndWait();
            if (name.isEmpty()) {
                return;
            }
            nameToUse = name.get();
        }
        try {
            mesaService.saveMap(scene, nameToUse);
            refreshMapList();
            logEntries.add(LogEntry.system("Cena salva: " + scene.getName()));
        } catch (IllegalArgumentException | IOException ex) {
            showError("Não foi possível salvar: " + ex.getMessage());
        }
    }

    private void onApplyGridSize(String text) {
        try {
            double size = Double.parseDouble(text.trim());
            sceneService.setGridCellSize(size);
            if (networkService.isGm() && networkService.isConnected()) networkService.sendSceneSwitch(sceneService.getScene());
            logEntries.add(LogEntry.system("Grade ajustada para " + (int) size + "px por célula."));
        } catch (NumberFormatException ex) {
            showError("Digite um número válido para o tamanho da célula.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onApplyFeetPerCell(String text) {
        try {
            double feet = Double.parseDouble(text.trim());
            sceneService.setGridFeetPerCell(feet);
            logEntries.add(LogEntry.system("Régua ajustada: cada célula agora vale " + formatFeet(feet) + " pés."));
        } catch (NumberFormatException ex) {
            showError("Digite um número válido para pés por célula.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    /** Evita "5.0 pes" feio na tela quando o valor e um numero inteiro
     * (o caso quase sempre -- 5 pes/celula e o padrao do D&D 5e). */
    private static String formatFeet(double feet) {
        return feet == Math.floor(feet) ? String.valueOf((long) feet) : String.valueOf(feet);
    }

    private SceneEntry selectedSceneEntry() {
        var item = mapListView == null ? null : mapListView.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /** Arvore: pastas primeiro (alfabetica), depois as cenas; lembra quais pastas estavam abertas. */
    private void refreshMapList() {
        java.util.Set<String> open = new java.util.HashSet<>();
        if (mapListView.getRoot() != null) collectExpanded(mapListView.getRoot(), open);
        boolean firstTime = mapListView.getRoot() == null;
        javafx.scene.control.TreeItem<SceneEntry> root = new javafx.scene.control.TreeItem<>(new SceneEntry("", "", true));
        fillSceneFolder(root, "", mesaService.listMaps(), open, firstTime);
        mapListView.setRoot(root);
    }

    private static void collectExpanded(javafx.scene.control.TreeItem<SceneEntry> item, java.util.Set<String> out) {
        for (var c : item.getChildren()) {
            if (c.getValue().folder() && c.isExpanded()) out.add(c.getValue().path());
            collectExpanded(c, out);
        }
    }

    private void fillSceneFolder(javafx.scene.control.TreeItem<SceneEntry> parent, String path, List<String> scenes,
                                 java.util.Set<String> open, boolean expandAll) {
        for (String sub : sceneFolders.subfolders(path)) {
            var item = new javafx.scene.control.TreeItem<>(new SceneEntry(
                    com.vttcaseiro.library.SceneFolders.nameOf(sub), sub, true), com.vttcaseiro.view.Icons.icon("open", 14));
            item.setExpanded(expandAll || open.contains(sub));
            fillSceneFolder(item, sub, scenes, open, expandAll);
            parent.getChildren().add(item);
        }
        for (String scene : scenes) {
            if (sceneFolders.folderOf(scene).equals(path)) {
                parent.getChildren().add(new javafx.scene.control.TreeItem<>(new SceneEntry(scene, path, false),
                        com.vttcaseiro.view.Icons.icon("map", 14)));
            }
        }
    }

    private void saveSceneFolders() {
        try {
            sceneFolders.save(mesaRepository.getRoot());
        } catch (IOException ex) {
            showError("Não deu pra salvar as pastas: " + ex.getMessage());
        }
        refreshMapList();
    }

    private void onNewSceneFolder(String parent) {
        TextInputDialog d = new TextInputDialog("Nova pasta");
        d.initOwner(stage);
        d.setTitle("Nova pasta");
        d.setHeaderText(parent.isEmpty() ? null : "Dentro de: " + parent.replace("/", " > "));
        d.setContentText("Nome:");
        d.showAndWait().ifPresent(name -> {
            try {
                sceneFolders.addFolder(parent, name);
                saveSceneFolders();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    /** Menu do botao direito na arvore de cenas. */
    private javafx.scene.control.ContextMenu sceneTreeMenu(SceneEntry entry) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        java.util.function.BiConsumer<String, Runnable> add = (text, action) -> {
            javafx.scene.control.MenuItem mi = new javafx.scene.control.MenuItem(text);
            mi.setOnAction(e -> action.run());
            menu.getItems().add(mi);
        };
        if (entry == null) {
            add.accept("Nova pasta", () -> onNewSceneFolder(""));
            return menu;
        }
        if (!entry.folder()) {
            add.accept("Abrir pra todos", () -> onOpenMapFromMesa(entry.name()));
            add.accept("Mostrar rapidinho e voltar", () -> onPeekScene(entry.name()));
            javafx.scene.control.Menu move = new javafx.scene.control.Menu("Mover para");
            List<String> targets = new ArrayList<>();
            targets.add("");
            targets.addAll(sceneFolders.folders());
            targets.sort(String.CASE_INSENSITIVE_ORDER);
            for (String t : targets) {
                if (t.equals(entry.path())) continue;
                javafx.scene.control.MenuItem mi = new javafx.scene.control.MenuItem(t.isEmpty() ? "(fora das pastas)" : t.replace("/", " > "));
                mi.setOnAction(e -> {
                    sceneFolders.moveScene(entry.name(), t);
                    saveSceneFolders();
                });
                move.getItems().add(mi);
            }
            menu.getItems().add(move);
            return menu;
        }
        add.accept("Nova pasta aqui dentro", () -> onNewSceneFolder(entry.path()));
        add.accept("Renomear pasta...", () -> {
            TextInputDialog d = new TextInputDialog(entry.name());
            d.initOwner(stage);
            d.setTitle("Renomear pasta");
            d.setHeaderText(null);
            d.setContentText("Nome:");
            d.showAndWait().ifPresent(n -> {
                try {
                    sceneFolders.renameFolder(entry.path(), n);
                    saveSceneFolders();
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            });
        });
        add.accept("Apagar pasta (as cenas ficam)", () -> {
            sceneFolders.removeFolder(entry.path());
            saveSceneFolders();
        });
        return menu;
    }

    /**
     * Mostra a cena pra todo mundo por um instante (ex.: o mapa da cidade pra dizer onde fica a taverna) e deixa um
     * botao "Voltar" na barra de cima pra retornar a cena de antes.
     */
    private void onPeekScene(String name) {
        String back = currentMapName;
        if (name.equals(back)) return;
        onOpenMapFromMesa(name);
        if (name.equals(currentMapName) && back != null) {
            sceneToReturnTo = back;
            refreshReturnButton();
        }
    }

    private void refreshReturnButton() {
        if (returnSceneButton == null) return;
        boolean show = sceneToReturnTo != null && networkService.isGm();
        returnSceneButton.setVisible(show);
        returnSceneButton.setManaged(show);
        if (show) returnSceneButton.setText("↩  Voltar para " + sceneToReturnTo);
    }

    /** Linha da arvore: arrastar uma cena e soltar numa pasta (ou no vazio) muda a pasta dela. */
    private final class SceneTreeCell extends javafx.scene.control.TreeCell<SceneEntry> {
        SceneTreeCell() {
            setOnDragDetected(e -> {
                if (getItem() == null || getItem().folder()) return;
                javafx.scene.input.Dragboard db = startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString("cena:" + getItem().name());
                db.setContent(cc);
                e.consume();
            });
            setOnDragOver(e -> {
                if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("cena:")) {
                    e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                String data = e.getDragboard().getString();
                if (data == null || !data.startsWith("cena:")) return;
                String scene = data.substring(5);
                String target = getItem() == null ? "" : getItem().folder() ? getItem().path() : getItem().path();
                sceneFolders.moveScene(scene, target);
                e.setDropCompleted(true);
                e.consume();
                Platform.runLater(Main.this::saveSceneFolders);
            });
        }

        @Override
        protected void updateItem(SceneEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().remove("scene-folder");
                return;
            }
            setText(item.name());
            setGraphic(getTreeItem() == null ? null : getTreeItem().getGraphic());
            if (item.folder()) {
                if (!getStyleClass().contains("scene-folder")) getStyleClass().add("scene-folder");
            } else {
                getStyleClass().remove("scene-folder");
            }
        }
    }

    private void onAddToken() {
        TextInputDialog nameDialog = new TextInputDialog("Token");
        nameDialog.setTitle("Novo token");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome do token:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher imagem do token (cancelar usa uma silhueta padrão)");
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = chooser.showOpenDialog(null);
        String imagePath = file != null ? file.getAbsolutePath() : null;

        TokenModel token = new TokenModel(name.get().trim(), imagePath);
        placeAtViewCenter(token);
        importIntoAssetStoreOrWarn(file).ifPresentOrElse(token::setImageAssetId,
                () -> token.setImageData(encodeImageOrWarn(imagePath)));
        addTokenToScene(token);
    }

    /** Mesmo dialogo de nome do onAddToken, mas a imagem vem da galeria
     * (ver ImageGalleryView) em vez de reabrir o FileChooser -- reusa um
     * asset ja guardado nesta mesa sem precisar escolher o arquivo de
     * novo. */
    private void onAddTokenFromGallery() {
        TextInputDialog nameDialog = new TextInputDialog("Token");
        nameDialog.setTitle("Novo token (da galeria)");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome do token:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }
        new ImageGalleryView(imageAssetService, assetId -> {
            TokenModel token = new TokenModel(name.get().trim(), null);
            placeAtViewCenter(token);
            token.setImageAssetId(assetId);
            addTokenToScene(token);
        }).show();
    }

    /** Centro da area visivel do mapa, pulando casas ja ocupadas. */
    private void placeAtViewCenter(TokenModel token) {
        for (int i = 0; i < 60; i++) {
            javafx.geometry.Point2D p = mapView.suggestTokenPosition(i);
            boolean occupied = sceneService.getScene().getTokens().stream()
                    .anyMatch(t -> Math.abs(t.getX() - p.getX()) < 1 && Math.abs(t.getY() - p.getY()) < 1);
            if (!occupied || i == 59) {
                token.setX(p.getX());
                token.setY(p.getY());
                return;
            }
        }
    }

    private void addTokenToScene(TokenModel token) {
        if (!undoing) undo.record("Adicionar " + token.getName(), null, token);
        if (networkService.isConnected()) {
            networkService.sendAddToken(token);
        } else {
            sceneService.addToken(token);
            logEntries.add(LogEntry.system("Token adicionado: " + token.getName()));
        }
    }

    /** Guarda o arquivo escolhido no ImageAssetStore da mesa (dedup por
     * hash -- ver ImageAssetService) pra ele aparecer na ImageGalleryView
     * da proxima vez, sem precisar escolher o arquivo de novo. Devolve
     * Optional vazio (sem travar a acao que chamou) se o arquivo for nulo
     * (usuario cancelou o FileChooser) ou a importacao falhar por
     * qualquer motivo (imagem grande demais, erro de leitura etc.) -- o
     * token/ficha continua funcionando do jeito antigo (imagePath/
     * imageData), so sem entrar na galeria. */
    private Optional<String> importIntoAssetStoreOrWarn(File file) {
        if (file == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(imageAssetService.importFile(file.toPath()));
        } catch (IOException ex) {
            logEntries.add(LogEntry.error("Imagem não entrou na galeria (mas continua valendo pra esta ficha/token): "
                    + ex.getMessage()));
            return Optional.empty();
        }
    }

    // ==================================================================
    // Acoes - fichas da mesa
    // ==================================================================

    private void onNewCharacter() {
        TextInputDialog nameDialog = new TextInputDialog("Personagem");
        nameDialog.setTitle("Nova ficha");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome do personagem:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty()) {
            return;
        }
        // Sistema da ficha: D&D 5e, Tormenta20 ou Ordem Paranormal (lembra o ultimo escolhido).
        List<com.vttcaseiro.character.GameSystem> systems = com.vttcaseiro.character.GameSystem.all();
        int last = (int) AppSettings.getDouble("ultimoSistema", 0);
        javafx.scene.control.ChoiceDialog<com.vttcaseiro.character.GameSystem> sysDialog =
                new javafx.scene.control.ChoiceDialog<>(systems.get(Math.max(0, Math.min(systems.size() - 1, last))), systems);
        sysDialog.initOwner(stage);
        sysDialog.setTitle("Nova ficha");
        sysDialog.setHeaderText("Qual sistema de RPG?");
        sysDialog.setContentText("Sistema:");
        Optional<com.vttcaseiro.character.GameSystem> system = sysDialog.showAndWait();
        if (system.isEmpty()) return;
        AppSettings.setDouble("ultimoSistema", systems.indexOf(system.get()));
        try {
            CharacterSheet sheet = characterService.create(name.get());
            system.get().initialize(sheet);
            sheet.setPlayerName(networkService.getPlayerName());
            sheet.setOwnerPlayerId(networkService.getPlayerId());
            characterService.save(sheet);
            refreshCharacterList();
            openSheetView(sheet);
            logEntries.add(LogEntry.system("Ficha criada: " + sheet.getName()));
        } catch (IllegalArgumentException | IOException ex) {
            showError("Não foi possível criar a ficha: " + ex.getMessage());
        }
    }

    /** Importa uma ficha de PDF/JSON, mostra o resumo e salva como ficha nova deste jogador. */
    private void onImportCharacter() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar ficha");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ficha (PDF ou JSON)", "*.pdf", "*.json"),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        com.vttcaseiro.importer.SheetImport.Result result;
        try {
            result = com.vttcaseiro.importer.SheetImport.fromFile(file.toPath());
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível importar a ficha: " + ex.getMessage());
            return;
        }
        CharacterSheet sheet = result.sheet();
        String base = sheet.getName();
        int n = 2;
        while (characterService.list().contains(sheet.getName())) sheet.setName(base + " (" + n++ + ")");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setTitle("Importar ficha");
        confirm.setHeaderText("Lido de: " + result.format());
        StringBuilder body = new StringBuilder(result.summary());
        if (!result.notes().isEmpty()) {
            body.append("\n\nConfira:");
            for (String note : result.notes()) body.append("\n- ").append(note);
        }
        confirm.setContentText(body.toString());
        confirm.getDialogPane().setMinWidth(520);
        if (confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        try {
            if (sheet.getPlayerName() == null || sheet.getPlayerName().isBlank()) sheet.setPlayerName(networkService.getPlayerName());
            sheet.setOwnerPlayerId(networkService.getPlayerId());
            characterService.save(sheet);
            refreshCharacterList();
            openSheetView(sheet);
            logEntries.add(LogEntry.system("Ficha importada (" + result.format() + "): " + sheet.getName()));
        } catch (IOException ex) {
            showError("Não foi possível salvar a ficha importada: " + ex.getMessage());
        }
    }

    private void onExportCharacter() {
        if (lastOpenedCharacter == null) {
            showError("Abra uma ficha primeiro (clique duplo na lista).");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar ficha");
        chooser.setInitialFileName(com.vttcaseiro.library.MesaBackup.safeName(lastOpenedCharacter.getName()) + ".json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            java.nio.file.Files.writeString(file.toPath(),
                    com.vttcaseiro.library.LibraryStorage.characterToJson(lastOpenedCharacter));
            logEntries.add(LogEntry.system("Ficha exportada: " + file.getName()));
        } catch (IOException ex) {
            showError("Não foi possível exportar: " + ex.getMessage());
        }
    }

    private void onOpenCharacterFromMesa(String name) {
        try {
            // Se ja estiver carregada (ex.: ligada a um token), usa a MESMA
            // instancia -- duas copias da mesma ficha em memoria divergiriam.
            CharacterSheet loaded = characterService.open(name);
            CharacterSheet sheet = openSheetViews.containsKey(loaded.getId())
                    ? openSheetViews.get(loaded.getId()).getSheet() : loaded;
            openSheetView(sheet);
            logEntries.add(LogEntry.system("Ficha aberta: " + sheet.getName()));
        } catch (IOException ex) {
            showError("Não foi possível abrir: " + ex.getMessage());
        } catch (RuntimeException ex) {
            showError("Esse arquivo não é uma ficha válida: " + ex.getMessage());
        }
    }

    private void onSaveLastCharacter() {
        if (lastOpenedCharacter == null) {
            showError("Nenhuma ficha aberta nesta sessão ainda.");
            return;
        }
        try {
            characterService.save(lastOpenedCharacter);
            refreshCharacterList();
            logEntries.add(LogEntry.system("Ficha salva: " + lastOpenedCharacter.getName()));
            // Se estiver numa mesa em rede, a ficha salva localmente tambem
            // precisa chegar pros outros conectados -- senao "salvar" so
            // valeria na propria maquina, e ninguem mais veria a mudanca.
            networkService.sendCharacterSync(lastOpenedCharacter);
        } catch (IOException ex) {
            showError("Não foi possível salvar: " + ex.getMessage());
        }
    }

    private void refreshCharacterList() {
        characterListView.getItems().setAll(characterService.list());
    }

    private void refreshMobList() {
        mobListView.getItems().setAll(mobService.list());
    }

    private void onOpenMobFromMesa(String name) {
        try {
            MobSheet mob = mobService.open(name);
            openMobView(mob, null);
            logEntries.add(LogEntry.system("Ficha de monstro aberta: " + mob.getName()));
        } catch (IOException ex) {
            showError("Não foi possível abrir: " + ex.getMessage());
        } catch (RuntimeException ex) {
            showError("Esse arquivo não é uma ficha de monstro válida: " + ex.getMessage());
        }
    }

    /** Mesmo padrao de onTraitClicked, mas pra ficha de monstro. */
    private void onMobTraitClicked(MobSheet mob, CharacterTrait trait) {
        String text = mob.getName() + " usa " + trait.toChatText();
        if (networkService.isConnected()) {
            networkService.sendChat(text);
        } else {
            logEntries.add(LogEntry.chat(networkService.getPlayerName(), text));
        }
    }

    // ==================================================================
    // Acoes - party
    // ==================================================================

    private void refreshPartyMemberList() {
        partyMemberListView.getItems().setAll(partyService.getParty().getCharacterIds());
    }

    private void refreshPartyInventoryList() {
        partyInventoryListView.getItems().setAll(partyService.getParty().getSharedInventory().getItems());
    }

    private void onRenameParty(String newName) {
        if (newName == null || newName.isBlank()) {
            showError("O nome da party não pode ser vazio.");
            return;
        }
        partyService.getParty().setName(newName.trim());
        savePartyAndSync("Party renomeada para: " + newName.trim());
    }

    private void onAddLastOpenedCharacterToParty() {
        if (lastOpenedCharacter == null) {
            showError("Abra uma ficha primeiro (clique duplo na lista da aba Fichas).");
            return;
        }
        partyService.addMember(lastOpenedCharacter.getId());
        refreshPartyMemberList();
        savePartyAndSync(lastOpenedCharacter.getName() + " entrou na party.");
    }

    private void onRemovePartyMember() {
        String selected = partyMemberListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selecione um membro da lista primeiro.");
            return;
        }
        partyService.removeMember(selected);
        refreshPartyMemberList();
        savePartyAndSync("Um membro saiu da party.");
    }

    private void onAddPartyItem() {
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Adicionar item ao inventário da party");
        nameDialog.setHeaderText(null);
        nameDialog.setContentText("Nome do item:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }
        TextInputDialog qtyDialog = new TextInputDialog("1");
        qtyDialog.setTitle("Quantidade");
        qtyDialog.setHeaderText(null);
        qtyDialog.setContentText("Quantidade de \"" + name.get() + "\":");
        Optional<String> qtyText = qtyDialog.showAndWait();
        if (qtyText.isEmpty()) {
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(qtyText.get().trim());
        } catch (NumberFormatException ex) {
            showError("Quantidade inválida: " + qtyText.get());
            return;
        }
        try {
            partyService.getParty().getSharedInventory().addItem(name.get().trim(), quantity, 0, 0);
            refreshPartyInventoryList();
            savePartyAndSync(quantity + "x " + name.get().trim() + " adicionado(s) ao loot da party.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onTransferPartyItemToLastOpenedCharacter() {
        InventoryItem selected = partyInventoryListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selecione um item da lista primeiro.");
            return;
        }
        if (lastOpenedCharacter == null) {
            showError("Abra a ficha de quem vai receber o item primeiro.");
            return;
        }
        boolean moved = partyService.getParty().getSharedInventory()
                .transferItemTo(lastOpenedCharacter.getInventory(), selected.getName(), 1);
        if (!moved) {
            showError("Não foi possível transferir (item sem quantidade suficiente?).");
            return;
        }
        refreshPartyInventoryList();
        try {
            characterService.save(lastOpenedCharacter);
            networkService.sendCharacterSync(lastOpenedCharacter);
        } catch (IOException ex) {
            logEntries.add(LogEntry.error("Item transferido, mas não foi possível salvar a ficha: " + ex.getMessage()));
        }
        savePartyAndSync("1x " + selected.getName() + " transferido pra " + lastOpenedCharacter.getName() + ".");
    }

    private void savePartyAndSync(String logMessage) {
        try {
            partyService.save();
        } catch (IOException ex) {
            logEntries.add(LogEntry.error("Não foi possível salvar a party: " + ex.getMessage()));
        }
        networkService.sendPartySync(partyService.getParty());
        logEntries.add(LogEntry.system(logMessage));
    }

    // ==================================================================
    // Acoes - iniciativa
    // ==================================================================

    /** Ordem = iniciativa atual dos tokens (EncounterService.order); quem esta
     * na vez vem do Encounter da cena (sincronizado pela rede). */
    private void refreshInitiativeList() {
        if (initiativeListView == null) {
            return; // ainda construindo a aba na primeira chamada
        }
        initiativeListView.getItems().setAll(EncounterService.order(sceneService.getScene()));
        initiativeListView.refresh();
        if (roundLabel != null) {
            var e = sceneService.getScene().getEncounter();
            roundLabel.setText(e.isActive() ? "Rodada " + e.getRound() : "Sem combate em andamento");
        }
        boolean gm = networkService.isGm();
        // jogador nao ve os botoes do mestre (em vez de ve-los apagados); o cartao "Mestre: grupo inteiro" some todo
        for (Button b : gmOnlyInitiativeButtons) {
            b.setVisible(gm);
            b.setManaged(gm);
            javafx.scene.Node card = b.getParent();
            while (card != null && !card.getStyleClass().contains("panel-card")) card = card.getParent();
            if (card != null && b == gmOnlyInitiativeButtons.get(4)) { // salvaguarda em grupo: cartao so do mestre
                card.setVisible(gm);
                card.setManaged(gm);
            }
        }
    }

    private void onEditInitiative() {
        TokenModel selected = initiativeListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selecione um token da lista primeiro.");
            return;
        }
        editTrackerEntry(selected);
    }

    static final String[] TRACKER_COLOR_NAMES = {"Cinza", "Verde", "Amarelo", "Vermelho"};
    static final String[] TRACKER_COLOR_KEYS = {null, "green", "yellow", "red"};

    static String trackerColorHex(String key) {
        if (key == null) return "#8A8A8A";
        return switch (key) {
            case "green" -> "#3FBE6E";
            case "yellow" -> "#E8C33E";
            case "red" -> "#D9544C";
            default -> "#8A8A8A";
        };
    }

    private static int colorIndex(String key) {
        for (int i = 0; i < TRACKER_COLOR_KEYS.length; i++) {
            if (java.util.Objects.equals(TRACKER_COLOR_KEYS[i], key)) return i;
        }
        return 0;
    }

    /** Linha do rastreador: bolinha da cor (clique troca), iniciativa, nome, modificador e efeitos. */
    private HBox initiativeRow(TokenModel token) {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(7, javafx.scene.paint.Color.web(trackerColorHex(token.getTrackerColor())));
        dot.setStroke(javafx.scene.paint.Color.web("#00000066"));
        dot.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(dot, new Tooltip("Cor: " + TRACKER_COLOR_NAMES[colorIndex(token.getTrackerColor())]
                + " (clique troca: cinza, verde, amarelo, vermelho)"));
        dot.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            String next = TRACKER_COLOR_KEYS[(colorIndex(token.getTrackerColor()) + 1) % TRACKER_COLOR_KEYS.length];
            table.setTrackerColor(token, next);
            refreshInitiativeList();
            e.consume();
        });
        Label init = new Label(token.getInitiative() == null ? "--" : String.valueOf(token.getInitiative()));
        init.setMinWidth(30);
        init.setStyle("-fx-font-weight: bold;");
        Label name = new Label(token.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox row = new HBox(8, dot, init, name);
        row.setAlignment(Pos.CENTER_LEFT);
        int mod = token.getInitiativeModifier();
        if (mod != 0) {
            Label m = new Label((mod > 0 ? "+" : "") + mod);
            m.getStyleClass().add("label-muted");
            Tooltip.install(m, new Tooltip("Modificador do rastreador (já somado na iniciativa)"));
            row.getChildren().add(m);
        }
        StringBuilder fx = new StringBuilder();
        for (var effect : EncounterService.effectsOf(sceneService.getScene(), token.getId())) {
            if (fx.length() > 0) fx.append("  ");
            fx.append("[").append(effect.getName()).append(": ").append(effect.getRemainingRounds()).append(" rd]");
        }
        if (fx.length() > 0) {
            Label effects = new Label(fx.toString());
            effects.getStyleClass().add("label-muted");
            row.getChildren().add(effects);
        }
        return row;
    }

    private javafx.scene.control.ContextMenu initiativeMenu(TokenModel token) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Editar...");
        edit.setOnAction(e -> editTrackerEntry(token));
        javafx.scene.control.MenuItem mod = new javafx.scene.control.MenuItem("Modificador...");
        mod.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog(String.valueOf(token.getInitiativeModifier()));
            d.initOwner(stage);
            d.setTitle("Modificador de iniciativa");
            d.setHeaderText(null);
            d.setContentText("Modificador só de " + token.getName() + " (ex.: 5 ou -2):");
            d.showAndWait().ifPresent(t -> {
                try {
                    table.setInitiativeModifier(token, Integer.parseInt(t.trim().replace("+", "")));
                    refreshInitiativeList();
                } catch (NumberFormatException ex) {
                    showError("Precisa ser um número inteiro: " + t);
                }
            });
        });
        Integer fromSheet = table.linkedSheet(token).map(CharacterSheet::getLastInitiative).orElse(null);
        javafx.scene.control.MenuItem pull = new javafx.scene.control.MenuItem(fromSheet == null
                ? "Puxar da ficha (ainda não rolou)" : "Puxar da ficha (" + fromSheet + ")");
        pull.setDisable(fromSheet == null);
        pull.setOnAction(e -> {
            table.pullInitiativeFromSheet(token);
            refreshInitiativeList();
        });
        javafx.scene.control.MenuItem roll = new javafx.scene.control.MenuItem("Rolar iniciativa");
        roll.setOnAction(e -> {
            tokenActions.rollInitiative(token);
            refreshInitiativeList();
        });
        javafx.scene.control.Menu colors = new javafx.scene.control.Menu("Cor");
        for (int i = 0; i < TRACKER_COLOR_KEYS.length; i++) {
            String key = TRACKER_COLOR_KEYS[i];
            javafx.scene.control.MenuItem c = new javafx.scene.control.MenuItem(TRACKER_COLOR_NAMES[i],
                    new javafx.scene.shape.Circle(6, javafx.scene.paint.Color.web(trackerColorHex(key))));
            c.setOnAction(e -> {
                table.setTrackerColor(token, key);
                refreshInitiativeList();
            });
            colors.getItems().add(c);
        }
        menu.getItems().addAll(edit, mod, pull, roll, colors);
        return menu;
    }

    /** Iniciativa, modificador do rastreador e cor de um combatente. */
    private void editTrackerEntry(TokenModel token) {
        if (!table.canControl(token)) {
            showError("Você não controla " + token.getName() + ".");
            return;
        }
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Rastreador: " + token.getName());
        dialog.setHeaderText(null);
        TextField init = new TextField(token.getInitiative() == null ? "" : String.valueOf(token.getInitiative()));
        init.setPromptText("vazio = sem iniciativa");
        javafx.scene.control.Spinner<Integer> mod = new javafx.scene.control.Spinner<>(-50, 50, token.getInitiativeModifier());
        mod.setEditable(true);
        ComboBox<String> color = new ComboBox<>();
        color.getItems().addAll(TRACKER_COLOR_NAMES);
        color.getSelectionModel().select(colorIndex(token.getTrackerColor()));
        Label modHint = new Label("O modificador soma na rolagem e muda a iniciativa já rolada junto.");
        modHint.getStyleClass().add("label-muted");
        modHint.setWrapText(true);
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Iniciativa"), init);
        grid.addRow(1, new Label("Modificador"), mod);
        grid.addRow(2, new Label("Cor"), color);
        grid.add(modHint, 0, 3, 2, 1);
        grid.setPrefWidth(330);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        mod.increment(0);
        Integer newInit;
        try {
            newInit = init.getText().isBlank() ? null : Integer.valueOf(init.getText().trim().replace("+", ""));
        } catch (NumberFormatException ex) {
            showError("Iniciativa inválida (precisa ser um número inteiro): " + init.getText());
            return;
        }
        // a iniciativa digitada ja e a final: aplica o modificador antes pra ele nao deslocar o numero
        table.setInitiativeModifier(token, mod.getValue());
        token.setInitiative(newInit);
        token.setTrackerColor(TRACKER_COLOR_KEYS[Math.max(0, color.getSelectionModel().getSelectedIndex())]);
        table.publishTokenUpdate(token);
        refreshInitiativeList();
    }

    /** Assinatura dos 5 metodos de import do CompendiumImportService --
     * java.util.function.Function nao serve porque todos eles declaram
     * "throws IOException" (e um JsonSyntaxException em runtime se o
     * arquivo nao for um JSON valido, igual onImportClasses trata). */
    private interface CompendiumPathImporter {
        Compendium.MergeResult importFrom(Path jsonFile) throws IOException;
    }

    /** Fabrica um botao "Importar X (5e.tools)...": abre o FileChooser,
     * roda o importador dado e atualiza o contador + o log. Mesmo
     * tratamento de erro de onImportClasses, so que generico pros 5
     * importadores novos (ver CompendiumImportService). */
    private Button compendiumImportButton(String label, CompendiumPathImporter importer) {
        Button button = new Button(label);
        button.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Escolher arquivo do 5e.tools para \"" + label + "\"");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            File file = chooser.showOpenDialog(null);
            if (file == null) {
                return;
            }
            try {
                Compendium.MergeResult result = importer.importFrom(file.toPath());
                compendiumCountLabel.setText(compendiumCountText());
                logEntries.add(LogEntry.system(label + ": " + result.added().size()
                        + " nova(s), " + result.skipped().size() + " pulada(s) (já existiam)."));
            } catch (IOException ex) {
                showError("Não foi possível importar: " + ex.getMessage());
            } catch (RuntimeException ex) {
                showError("O arquivo não parece ser um JSON válido do 5e.tools para isto: " + ex.getMessage());
            }
        });
        return button;
    }

    private String compendiumCountText() {
        Compendium compendium = compendiumService.getCompendium();
        return compendium.getEntries().size() + " entrada(s) no compêndio desta mesa.";
    }

    private void onCompendiumSearch(String text) {
        compendiumSearchListView.getItems().setAll(compendiumService.search(text == null ? "" : text));
    }

    private void onAttachSelectedCompendiumEntry() {
        if (lastOpenedCharacter == null) {
            showError("Abra ou crie uma ficha primeiro -- o anexo vai pra última ficha aberta nesta sessão.");
            return;
        }
        CompendiumEntry selected = compendiumSearchListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selecione uma entrada na lista de busca primeiro.");
            return;
        }
        compendiumService.attachToSheet(lastOpenedCharacter, selected);
        try {
            characterService.save(lastOpenedCharacter);
            logEntries.add(LogEntry.system(selected.getName() + " anexado em " + lastOpenedCharacter.getName()
                    + ". Reabra a ficha (lista de fichas, duplo clique) para ver o anexo na janela."));
        } catch (IOException ex) {
            showError("Anexado na ficha em memória, mas não foi possível salvar no arquivo: " + ex.getMessage());
        }
    }

    /** Conteudo XPHB/XDMG/XMM que vem dentro do programa (resources/seed):
     * um clique e a mesa tem magias, talentos, especies, antecedentes, itens
     * e o bestiario -- sem precisar baixar nada do 5e.tools. */
    private void onLoadSeedContent() {
        try {
            String compendiumJson = readResource("/seed/compendio.json");
            String bestiaryJson = readResource("/seed/bestiary-xmm.json");
            Compendium.MergeResult entries = compendiumService.importCompendiumJson(compendiumJson);
            var mobs = mobImportService.importFromJson(bestiaryJson);
            var classes = classImportService.importOfficial(this::seedClassFile);
            if (classes.changedSomething()) {
                logEntries.add(LogEntry.system("Classes oficiais: " + classes.classes() + " classe(s) e "
                        + classes.subclasses() + " subclasse(s) novas (Wizards e parceiros do D&D Beyond)."));
            }
            compendiumCountLabel.setText(compendiumCountText());
            mobCountLabel.setText(mobService.list().size() + " monstro(s) nesta mesa.");
            refreshMobList();
            logEntries.add(LogEntry.system("Conteúdo 2024 carregado: " + entries.added().size()
                    + " entrada(s) de compêndio e " + mobs.imported().size() + " monstro(s) novos ("
                    + (entries.skipped().size() + mobs.skipped().size()) + " já existiam"
                    + (compendiumService.lastRefreshedCount() > 0
                        ? "; " + compendiumService.lastRefreshedCount() + " com texto corrigido" : "")
                    + ")."));
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível carregar o conteúdo pronto: " + ex.getMessage());
        }
    }

    /** Arquivo de classe guardado no programa (/seed/classes/), ou null se o programa veio sem ele. */
    private String seedClassFile(String name) {
        try {
            return readResource("/seed/classes/" + name);
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Ao abrir a mesa: poe as classes e subclasses oficiais que faltam (Wizards e parceiros do D&D Beyond). Rapido e
     * sem duplicar; so avisa no chat quando entrou algo novo. Programa sem os arquivos (/seed/classes/) nao faz nada.
     */
    private void syncOfficialClasses() {
        try {
            var result = classImportService.importOfficial(this::seedClassFile);
            if (result.changedSomething()) {
                logEntries.add(LogEntry.system("Classes oficiais nesta mesa: +" + result.classes()
                        + " classe(s) e +" + result.subclasses() + " subclasse(s)."));
            }
        } catch (IOException | RuntimeException ex) {
            logEntries.add(LogEntry.system("Não deu pra carregar as classes oficiais: " + ex.getMessage()));
        }
    }

    private String readResource(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Recurso não encontrado no programa: " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Token de monstro a partir da ficha de monstro selecionada: PV medio,
     * iniciativa pelo mod. de DES, tamanho em celulas pelo tamanho, PV
     * escondido dos jogadores. Repetidos ganham numero ("Goblin 2"). */
    private void onPlaceSelectedMob() {
        String name = mobListView.getSelectionModel().getSelectedItem();
        if (name == null) {
            showError("Selecione um monstro na lista primeiro.");
            return;
        }
        try {
            MobSheet mob = mobService.open(name);
            long same = sceneService.getScene().getTokens().stream()
                    .filter(t -> t.getName() != null && t.getName().startsWith(mob.getName())).count();
            String displayName = same == 0 ? mob.getName() : mob.getName() + " " + (same + 1);
            TokenModel token = com.vttcaseiro.service.TokenLink.tokenFromMob(mob, displayName);
            if (token.getImageAssetId() == null && token.getImageData() != null) {
                try {
                    token.setImageAssetId(imageAssetService.importBase64(token.getImageData()));
                    token.setImageData(null);
                } catch (IOException ex) {
                    // fica embutida mesmo -- funciona, so e mais pesado na rede
                }
            }
            placeAtViewCenter(token);
            addTokenToScene(token);
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível colocar o monstro: " + ex.getMessage());
        }
    }

    private void onImportMonsters() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher arquivo bestiary-*.json do 5e.tools");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        try {
            var result = mobImportService.importFromFile(file.toPath());
            mobCountLabel.setText(mobService.list().size() + " monstro(s) nesta mesa.");
            refreshMobList();
            logEntries.add(LogEntry.system(result.imported().size() + " monstro(s) importado(s), "
                    + result.skipped().size() + " pulado(s) (nome já existia nesta mesa)."));
        } catch (IOException ex) {
            showError("Não foi possível importar: " + ex.getMessage());
        } catch (RuntimeException ex) {
            showError("O arquivo não parece ser um bestiary-*.json válido do 5e.tools: " + ex.getMessage());
        }
    }

    private void onImportClasses() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher arquivo class-*.json do 5etools");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        try {
            var result = classImportService.importFromFile(file.toPath());
            classCountLabel.setText(classImportService.getClasses().size() + " classe(s) importada(s) nesta mesa.");
            logEntries.add(LogEntry.system(result.imported().size() + " classe(s) importada(s), "
                    + result.skipped().size() + " pulada(s). Veja os detalhes no arquivo se algo parecer errado."));
            for (var skipped : result.skipped()) {
                logEntries.add(LogEntry.system("Pulada: " + skipped.className() + " (" + skipped.sourceCode()
                        + ") -- " + skipped.reason()));
            }
        } catch (IOException ex) {
            showError("Não foi possível importar: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            showError("Esse arquivo não parece ser um class-*.json do 5etools: " + ex.getMessage());
        } catch (RuntimeException ex) {
            showError("O arquivo não é um JSON válido: " + ex.getMessage());
        }
    }

    /** O que as janelas de ficha (CharacterSheetView) pedem pro resto do
     * programa: rolar no chat (com rotulo e vantagem/desvantagem), mandar
     * texto, e gravar a ficha (autosave da propria tela). */
    /** Rolagem com rotulo: pela rede (o servidor rola) ou local. */
    private void rollLabeled(String label, String expression, boolean hidden) {
        rollLabeled(label, expression, hidden, null);
    }

    private void rollLabeled(String label, String expression, boolean hidden,
                             java.util.function.Consumer<RollResult> after) {
        if (networkService.isConnected()) {
            expectRoll(label, after);
            networkService.sendRoll(label, expression, "normal", 0, hidden);
            return;
        }
        try {
            RollResult result = DiceRoller.roll(expression);
            addLocalRoll(label, result, hidden);
            if (after != null) after.accept(result);
        } catch (DiceException ex) {
            logEntries.add(LogEntry.error(ex.getMessage()));
        }
    }

    // ---------------- Alvos: ataque contra a CA e dano nos acertados ----------------

    /** Ataque rolado: se ha alvos marcados, compara com a CA de cada um. */
    private java.util.function.Consumer<RollResult> resolveAgainstTargets(String sourceKey, String attacker) {
        List<TokenModel> targets = mapView.getTargets();
        if (targets.isEmpty()) return null;
        return r -> table.resolveAttack(sourceKey, attacker, r.total(), r.naturalD20(), targets);
    }

    /** Rolagem local ou pela rede, com o "depois" (ver CombatFlow). */
    private final com.vttcaseiro.service.CombatFlow.Roller roller = this::rollLabeled;

    private static String withDamageBonus(String label, String expression, List<String> effects) {
        return com.vttcaseiro.service.CombatFlow.withDamageBonus(label, expression, effects);
    }

    private static String damageTypeIn(String label) {
        return com.vttcaseiro.service.CombatFlow.damageTypeIn(label);
    }

    private void rollFromSource(String sourceKey, String label, String expression, boolean hidden) {
        com.vttcaseiro.service.CombatFlow.rollFromSource(table, roller, sourceKey, label, expression, hidden);
    }

    /** Dados rolando por cima do mapa (pode desligar na aba Som). */
    private void animateDice(String detail) {
        if (diceAnimator == null || !AppSettings.getBool("dice.animated", true)) return;
        diceAnimator.show(com.vttcaseiro.view.DiceAnimator.parse(detail), false, false);
    }

    private void addLocalRoll(String label, RollResult result, boolean hidden) {
        animateDice(result.detail());
        String text = (label == null || label.isBlank() ? "" : label + ": ") + result.detail();
        logEntries.add(hidden
                ? LogEntry.hiddenRoll(networkService.getPlayerName(), text, result.total(), result.naturalD20())
                : LogEntry.roll(networkService.getPlayerName(), text, result.total(), result.naturalD20()));
    }

    private final CharacterSheetView.Actions sheetActions = new CharacterSheetView.Actions() {
        @Override
        public void rollD20(CharacterSheet sheet, String label, int modifier, com.vttcaseiro.character.RollKind kind,
                            com.vttcaseiro.character.Ability ability, com.vttcaseiro.character.RollMode chosen,
                            boolean hidden) {
            if (kind == com.vttcaseiro.character.RollKind.ATTACK && label.startsWith("Ataque: ")) {
                String attackName = label.substring(8);
                sheet.getAttacks().stream().filter(a -> a.getName().equals(attackName)).findFirst()
                        .ifPresent(a -> a.damageExpression().ifPresent(expr -> registerFollowUp(sheet.getName() + " - " + label,
                                "Dano: " + a.getName() + " (" + a.getDamageAndType() + ")", expr,
                                (l, x, h) -> this.roll(sheet, l, x, h))));
            }
            rollD20WithAdjustment(sheet.getName() + " - " + label, modifier,
                    sheet.rollAdjustment(kind, ability, chosen), hidden,
                    kind == com.vttcaseiro.character.RollKind.ATTACK
                            ? resolveAgainstTargets("sheet:" + sheet.getId(), sheet.getName()) : null);
        }

        @Override
        public void rollD20Then(CharacterSheet sheet, String label, int modifier, com.vttcaseiro.character.RollKind kind,
                                com.vttcaseiro.character.Ability ability, com.vttcaseiro.character.RollMode chosen,
                                boolean hidden, java.util.function.IntConsumer onNatural) {
            rollD20WithAdjustment(sheet.getName() + " - " + label, modifier, sheet.rollAdjustment(kind, ability, chosen),
                    hidden, r -> {
                        if (onNatural != null && r.naturalD20() != null) onNatural.accept(r.naturalD20());
                    });
        }

        @Override
        public void attack(CharacterSheet sheet, com.vttcaseiro.character.Attack attack,
                           com.vttcaseiro.character.RollMode chosen, boolean hidden) {
            String damageLabel = attack.getName() + (attack.getDamageAndType().isBlank() ? "" : " (" + attack.getDamageAndType() + ")");
            var bonus = attack.attackBonus();
            if (bonus.isEmpty()) {
                // CD de salvaguarda (sem acerto): so o dano
                attack.damageExpression().ifPresent(expr -> this.roll(sheet, "Dano: " + damageLabel, expr, hidden));
                return;
            }
            java.util.function.Consumer<RollResult> againstTargets = resolveAgainstTargets("sheet:" + sheet.getId(), sheet.getName());
            rollD20WithAdjustment(sheet.getName() + " - Ataque: " + attack.getName(), bonus.getAsInt(),
                    sheet.rollAdjustment(com.vttcaseiro.character.RollKind.ATTACK, null, chosen), hidden, r -> {
                        if (againstTargets != null) againstTargets.accept(r);
                        if (r.isCriticalFail()) {
                            announce(sheet.getName() + ": falha crítica com " + attack.getName() + " -- sem dano.");
                            return;
                        }
                        attack.damageExpression().ifPresent(expr -> {
                            if (r.isCriticalHit()) {
                                // critico: todos os dados do dano em dobro (o modificador nao)
                                this.roll(sheet, "Dano CRÍTICO: " + damageLabel, DiceRoller.doubleDice(expr), hidden);
                            } else {
                                this.roll(sheet, "Dano: " + damageLabel, expr, hidden);
                            }
                        });
                    });
        }

        @Override
        public void rollInitiative(CharacterSheet sheet, com.vttcaseiro.character.RollMode chosen, boolean hidden) {
            List<TokenModel> mine = sceneService.getScene().getTokens().stream()
                    .filter(t -> sheet.getId().equals(t.getCharacterSheetId())).toList();
            if (!mine.isEmpty()) {
                // com o token no mapa a iniciativa entra na ordem de combate (com os dados extras da ficha
                // e a vantagem/desvantagem escolhida)
                for (TokenModel t : mine) {
                    recorded("Iniciativa de " + t.getName(), t, () -> table.rollInitiative(t, true, chosen));
                }
                return;
            }
            // sem token: rola aqui e guarda na ficha; o rastreador puxa quando o token entrar
            table.rollSheetInitiative(sheet, chosen, hidden);
            refreshInitiativeList();
        }

        @Override
        public void roll(CharacterSheet sheet, String label, String expression, boolean hidden) {
            if (label.endsWith(" (ataque)")) {
                // ficha de Tormenta20 / Ordem: o dano do mesmo ataque vira o botao do cartao
                String attackName = label.substring(0, label.length() - " (ataque)".length());
                sheet.getAttacks().stream().filter(a -> a.getName().equals(attackName)).findFirst()
                        .ifPresent(a -> a.damageExpression().ifPresent(expr -> registerFollowUp(sheet.getName() + " - " + label,
                                a.getName() + " (dano)", expr, (l, x, h) -> this.roll(sheet, l, x, h))));
            }
            rollFromSource("sheet:" + sheet.getId(), sheet.getName() + " - " + label,
                    withDamageBonus(label, expression, sheet.getEffects()), hidden);
        }

        @Override
        public void spellCast(CharacterSheet sheet, com.vttcaseiro.character.PreparedSpell spell, int slotLevel) {
            String notice = com.vttcaseiro.service.CombatFlow.applySpellEffect(table, sceneService.getScene().getTokens(),
                    mapView.getTargets(), sheet, spell);
            if (notice != null) logEntries.add(LogEntry.system(notice));
        }

        @Override
        public void castSpellEffect(CharacterSheet sheet, com.vttcaseiro.character.PreparedSpell spell, int slotLevel,
                                    String dice, boolean hidden) {
            com.vttcaseiro.service.CombatFlow.castDice(table, roller, mapView.getTargets(), sheet, spell, slotLevel,
                    dice, hidden);
        }

        @Override
        public void chat(CharacterSheet sheet, String text) {
            announce(text);
        }

        @Override
        public void compendium(CharacterSheet sheet, com.vttcaseiro.compendium.CompendiumReference reference) {
            onCompendiumAttachmentClicked(sheet, reference);
        }

        @Override
        public void save(CharacterSheet sheet) throws Exception {
            if (!table.canEditSheet(sheet)) {
                throw new IllegalStateException("ficha de outro jogador -- só o dono e o mestre salvam alterações");
            }
            if (sheet.getOwnerPlayerId() == null && networkService.isConnected() && !networkService.isGm()) {
                sheet.setOwnerPlayerId(networkService.getPlayerId()); // ficha antiga: o jogador assume
            }
            characterService.save(sheet);
            lastOpenedCharacter = sheet;
            refreshCharacterList();
            networkService.sendCharacterSync(sheet);
            table.pushSheetToTokens(sheet);
        }

        @Override
        public void sheetChanged(CharacterSheet sheet) {
            table.pushSheetToTokens(sheet);
        }

        @Override
        public Compendium compendium() {
            return compendiumService.getCompendium();
        }

        @Override
        public List<com.vttcaseiro.content.DndClass> classes() {
            return classImportService.getClasses();
        }
    };

    private static String d20Expression(int modifier) {
        return modifier == 0 ? "1d20" : "1d20" + (modifier > 0 ? "+" + modifier : String.valueOf(modifier));
    }

    private void onTraitClicked(CharacterSheet sheet, CharacterTrait trait) {
        String text = sheet.getName() + " usa " + trait.toChatText();
        if (networkService.isConnected()) {
            networkService.sendChat(text);
        } else {
            logEntries.add(LogEntry.chat(networkService.getPlayerName(), text));
        }
    }

    /** Mesmo padrao de onTraitClicked, mas pra um anexo do compendio
     * (CompendiumReference) -- o texto vem sempre resolvido de novo contra
     * o compendio atual da mesa (ver CompendiumService.chatTextFor), nunca
     * de um texto congelado guardado na ficha. */
    private void onCompendiumAttachmentClicked(CharacterSheet sheet, com.vttcaseiro.compendium.CompendiumReference ref) {
        String text = sheet.getName() + ": " + compendiumService.chatTextFor(ref);
        if (networkService.isConnected()) {
            networkService.sendChat(text);
        } else {
            logEntries.add(LogEntry.chat(networkService.getPlayerName(), text));
        }
    }

    // ==================================================================
    // Acoes - rede
    // ==================================================================

    /** IPs desta maquina na rede (IPv4, sem loopback) pra montar o link do navegador. */
    private static List<String> localIpv4Addresses() {
        List<String> out = new java.util.ArrayList<>();
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) out.add(addr.getHostAddress());
                }
            }
        } catch (java.net.SocketException ignored) {
            // sem lista de interfaces: cai no localhost
        }
        return out;
    }

    /** Abre a mesa hospedada aqui numa ponte (codigo fixo por mesa, guardado em ponte.properties). */
    private void onOpenRelay(String address, Label info) {
        if (!networkService.isConnected() || !networkService.isGm()) {
            showError("Hospede a mesa primeiro (botão \"Hospedar aqui\").");
            return;
        }
        String a = address == null ? "" : address.trim().replaceFirst("^[a-z]+://", "").replaceAll("/+$", "");
        if (a.isEmpty()) {
            showError("Digite o endereço da ponte (ex.: meuservidor.com:55070).");
            return;
        }
        String host = a;
        int port = com.vttcaseiro.relay.RelayServer.DEFAULT_PORT;
        int colon = a.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(a.substring(colon + 1));
                host = a.substring(0, colon);
            } catch (NumberFormatException ex) {
                showError("Porta inválida no endereço da ponte: " + a);
                return;
            }
        }
        java.util.Properties p = new java.util.Properties();
        java.nio.file.Path file = mesaRepository.getRoot().resolve("ponte.properties");
        try {
            if (java.nio.file.Files.exists(file)) {
                try (var r = java.nio.file.Files.newBufferedReader(file)) { p.load(r); }
            }
            if (p.getProperty("codigo") == null || p.getProperty("segredo") == null) {
                p.setProperty("codigo", com.vttcaseiro.relay.RelayHostLink.newCode());
                p.setProperty("segredo", com.vttcaseiro.relay.RelayHostLink.newSecret());
                try (var w = java.nio.file.Files.newBufferedWriter(file)) { p.store(w, "Customizr - código desta mesa na ponte"); }
            }
            String code = p.getProperty("codigo");
            final String relayHost = host;
            final int relayPort = port;
            networkService.openRelay(host, port, code, p.getProperty("segredo"),
                    msg -> Platform.runLater(() -> logEntries.add(LogEntry.system(msg))));
            AppSettings.setString("ponte.endereco", a);
            info.setText("Na ponte! Jogadores: \"Entrar numa mesa online\" com o endereço " + relayHost + ", porta " + relayPort
                    + " e o código " + code + (networkService.isTls() ? " (e \"Mesa criptografada\" marcado)." : ".")
                    + "\nPelo navegador: http://" + relayHost + ":" + relayPort + "/?mesa=" + code);
            info.setVisible(true);
            info.setManaged(true);
        } catch (IOException | IllegalStateException ex) {
            showError("Não foi possível abrir na ponte: " + ex.getMessage());
        }
    }

    private static int autoBackupMinutes() {
        return (int) Math.round(AppSettings.getDouble("autoBackupMinutes", 10));
    }

    private void restartAutoBackupTimer() {
        if (autoBackupTimer != null) autoBackupTimer.stop();
        int minutes = autoBackupMinutes();
        if (minutes <= 0) {
            autoBackupTimer = null;
            if (backupStatus != null) backupStatus.setText("Backup automático desligado. " + lastBackupText());
            return;
        }
        autoBackupTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.minutes(minutes), e -> runBackup("auto", false)));
        autoBackupTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoBackupTimer.play();
    }

    private String lastBackupText() {
        List<com.vttcaseiro.library.AutoBackup.Entry> all = com.vttcaseiro.library.AutoBackup.list(mesaRepository.getRoot());
        if (all.isEmpty()) return "Nenhum backup ainda.";
        return "Último backup: " + describeBackup(all.get(0)) + " (" + all.size() + " guardados).";
    }

    private static String describeBackup(com.vttcaseiro.library.AutoBackup.Entry e) {
        String kind = switch (e.kind()) {
            case "abertura" -> "ao abrir a mesa";
            case "manual" -> "feito na mão";
            case "antes" -> "antes de restaurar";
            default -> "automático";
        };
        return e.when().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ", " + kind
                + ", " + Math.max(1, e.sizeBytes() / 1024) + " KB";
    }

    /** Grava o que esta pendente e faz o zip numa thread separada (o jogo nao trava). */
    private void runBackup(String kind, boolean force) {
        flushSceneAutosave();
        Path root = mesaRepository.getRoot();
        backupExecutor.submit(() -> {
            try {
                Optional<com.vttcaseiro.library.AutoBackup.Entry> made = com.vttcaseiro.library.AutoBackup.create(root, kind, force);
                Platform.runLater(() -> {
                    if (backupStatus != null) backupStatus.setText(lastBackupText());
                    if (force) logEntries.add(LogEntry.system(made.map(m -> "Backup feito: " + describeBackup(m))
                            .orElse("Nada pra copiar ainda.")));
                });
            } catch (IOException | RuntimeException ex) {
                Platform.runLater(() -> logEntries.add(LogEntry.error("Backup automático falhou: " + ex.getMessage())));
            }
        });
    }

    /** Lista os backups; o escolhido vira uma mesa nova (a atual fica como esta). */
    private void onRestoreBackup() {
        Path root = mesaRepository.getRoot();
        List<com.vttcaseiro.library.AutoBackup.Entry> all = com.vttcaseiro.library.AutoBackup.list(root);
        if (all.isEmpty()) {
            showError("Esta mesa ainda não tem backups. Use \"Fazer backup agora\" ou espere o automático.");
            return;
        }
        ListView<com.vttcaseiro.library.AutoBackup.Entry> list = new ListView<>(FXCollections.observableArrayList(all));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.library.AutoBackup.Entry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : describeBackup(e));
            }
        });
        list.getSelectionModel().selectFirst();
        list.setPrefSize(380, 300);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM HH'h'mm"));
        TextField name = new TextField(mesaService.getName() + " (restaurada " + stamp + ")");
        Label hint = new Label("A cópia vira uma mesa nova com esse nome (a atual não muda). Imagens e sons vêm da mesa atual.");
        hint.setWrapText(true);
        hint.getStyleClass().add("label-muted");
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Restaurar backup");
        dialog.setHeaderText("Escolha de quando");
        dialog.getDialogPane().setContent(new VBox(8, list, new Label("Nome da mesa restaurada"), name, hint));
        javafx.scene.control.ButtonType restore = new javafx.scene.control.ButtonType("Restaurar",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType folder = new javafx.scene.control.ButtonType("Abrir pasta",
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(restore, folder, javafx.scene.control.ButtonType.CANCEL);
        Optional<javafx.scene.control.ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty()) return;
        if (answer.get() == folder) {
            try {
                java.awt.Desktop.getDesktop().open(com.vttcaseiro.library.AutoBackup.dir(root).toFile());
            } catch (IOException | RuntimeException ex) {
                showError("Não deu pra abrir a pasta: " + com.vttcaseiro.library.AutoBackup.dir(root));
            }
            return;
        }
        if (answer.get() != restore || list.getSelectionModel().getSelectedItem() == null) return;
        try {
            Path made = com.vttcaseiro.library.AutoBackup.restoreAsNewMesa(list.getSelectionModel().getSelectedItem().file(),
                    root, root.getParent(), name.getText());
            Alert done = new Alert(Alert.AlertType.INFORMATION, "Pronto: a mesa \"" + made.getFileName()
                    + "\" foi criada com a cópia.\n\nPra usar, feche e abra o Customizr e escolha ela na lista de mesas.");
            done.setHeaderText("Backup restaurado");
            done.initOwner(stage);
            done.showAndWait();
        } catch (IOException ex) {
            showError("Não deu pra restaurar: " + ex.getMessage());
        }
    }

    /** Exporta a mesa inteira num .zip (salva a cena aberta antes). */
    private void onExportMesa() {
        flushSceneAutosave();
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Salvar backup da mesa");
        chooser.setInitialFileName(com.vttcaseiro.library.MesaBackup.safeName(mesaService.getName()) + "-"
                + java.time.LocalDate.now() + ".zip");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Backup (.zip)", "*.zip"));
        java.io.File f = chooser.showSaveDialog(stage);
        if (f == null) return;
        try {
            int n = com.vttcaseiro.library.MesaBackup.export(mesaRepository.getRoot(), f.toPath());
            logEntries.add(LogEntry.system("Backup salvo: " + f.getName() + " (" + n + " arquivos)."));
        } catch (IOException ex) {
            showError("Não foi possível exportar: " + ex.getMessage());
        }
    }

    /** Pergunta se confia no codigo da mesa (primeira vez) ou avisa que MUDOU. */
    private boolean confirmTableCode(String hostPort, String fingerprint, String previous) {
        Alert ask = new Alert(previous == null ? Alert.AlertType.CONFIRMATION : Alert.AlertType.WARNING,
                "", javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        ask.initOwner(stage);
        ask.setTitle("Código da mesa");
        ask.setHeaderText(previous == null ? "Primeira vez nesta mesa (" + hostPort + ")"
                : "ATENÇÃO: o código desta mesa MUDOU desde a última vez");
        ask.setContentText((previous == null
                ? "Confira com o mestre: o código dele tem que ser\n\n    "
                : "Se o mestre não gerou um certificado novo, pode ser alguém no meio do caminho.\n\nCódigo novo:\n\n    ")
                + com.vttcaseiro.net.TlsSupport.shortCode(fingerprint) + "\n\nConfiar e conectar?");
        return ask.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.YES).isPresent();
    }

    private void onHost(String name, String portText, String tablePassword, String masterPassword) {
        onHost(name, portText, tablePassword, masterPassword, false);
    }

    private void onHost(String name, String portText, String tablePassword, String masterPassword, boolean tls) {
        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch (NumberFormatException ex) {
            showError("Porta inválida: " + portText);
            return;
        }
        String resolvedName = name.isBlank() ? "Mestre" : name.trim();
        try {
            imageAssetService.migrateInlineImages(sceneService.getScene());
            networkService.host(port, sceneService.getScene(), resolvedName,
                    mesaRepository.getImageAssetStore(), blankToNull(tablePassword),
                    blankToNull(masterPassword), tls, new AppGameListener());
            logEntries.add(LogEntry.system("Servidor aberto." + (tls ? " Criptografado (TLS), código da mesa: "
                    + com.vttcaseiro.net.TlsSupport.shortCode(networkService.getHostFingerprint()) : "")));
        } catch (IllegalStateException | IOException ex) {
            showError("Não foi possível abrir o servidor: " + ex.getMessage());
        }
    }

    private void onConnect(String name, String host, String portText, String tablePassword,
            boolean asGm, String masterPassword) {
        onConnect(name, host, portText, tablePassword, asGm, masterPassword, false);
    }

    private void onConnect(String name, String host, String portText, String tablePassword,
            boolean asGm, String masterPassword, boolean tls) {
        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch (NumberFormatException ex) {
            showError("Porta inválida: " + portText);
            return;
        }
        String resolvedName = name.isBlank() ? "Jogador" : name.trim();
        try {
            networkService.connect(host.trim(), port, resolvedName,
                    mesaRepository.getImageAssetStore(), asGm, blankToNull(tablePassword),
                    asGm ? blankToNull(masterPassword) : null, tls, this::confirmTableCode, new AppGameListener());
            logEntries.add(LogEntry.system("Conectado ao servidor."));
        } catch (IllegalStateException | IOException ex) {
            showError("Não foi possível conectar: " + ex.getMessage());
        }
    }

    /** Recebe as mensagens que chegam da rede (atraves do NetworkService --
     * Main nunca toca em com.vttcaseiro.net diretamente). Roda numa
     * thread de rede, NAO na thread do JavaFX -- por isso cada metodo
     * embrulha a mudanca real em Platform.runLater. */
    private class AppGameListener implements NetworkService.GameListener {
        @Override
        public void onSceneSync(SceneModel scene) {
            Platform.runLater(() -> {
                sceneService.replaceScene(scene);
                logEntries.add(LogEntry.system("Mapa sincronizado com o servidor."));
            });
        }

        @Override
        public void onTokenMoved(String tokenId, double x, double y) {
            Platform.runLater(() -> sceneService.moveToken(tokenId, x, y));
        }

        @Override
        public void onTokenAdded(TokenModel token) {
            Platform.runLater(() -> {
                sceneService.addToken(token);
                logEntries.add(LogEntry.system("Token adicionado: " + token.getName()));
            });
        }

        @Override
        public void onTokenRemoved(String tokenId) {
            Platform.runLater(() -> sceneService.removeToken(tokenId));
        }

        @Override
        public void onRollResult(String player, String detail, int total) {
            Platform.runLater(() -> logEntries.add(LogEntry.roll(player, detail, total, null)));
        }

        @Override
        public void onRollResultLabeled(String player, String label, String detail, int total, Integer naturalD20) {
            onRollResultFull(player, label, detail, total, naturalD20, false);
        }

        @Override
        public void onRollResultFull(String player, String label, String detail, int total,
                                     Integer naturalD20, boolean hidden) {
            String text = label == null || label.isBlank() ? detail : label + ": " + detail;
            Platform.runLater(() -> {
                animateDice(detail);
                logEntries.add(hidden
                        ? LogEntry.hiddenRoll(player, text, total, naturalD20)
                        : LogEntry.roll(player, text, total, naturalD20));
                if (label != null && player != null && player.equals(networkService.getPlayerName())) {
                    java.util.ArrayDeque<java.util.function.Consumer<RollResult>> queue = pendingRolls.get(label);
                    if (queue != null && !queue.isEmpty()) {
                        java.util.function.Consumer<RollResult> after = queue.poll();
                        if (queue.isEmpty()) pendingRolls.remove(label);
                        after.accept(new RollResult(total, detail, naturalD20));
                    }
                }
            });
        }

        @Override
        public void onEncounterSync(com.vttcaseiro.model.Encounter encounter) {
            if (encounter == null) return;
            Platform.runLater(() -> table.applyRemoteEncounter(encounter));
        }

        @Override
        public void onFogSync(com.vttcaseiro.model.Fog fog) {
            if (fog == null) return;
            Platform.runLater(() -> table.applyRemoteFog(fog));
        }

        @Override
        public void onAudio(com.vttcaseiro.model.AudioCommand command) {
            Platform.runLater(() -> playAudio(command));
        }

        @Override
        public void onMapLights(List<com.vttcaseiro.model.MapLight> lights) {
            Platform.runLater(() -> table.applyRemoteLights(lights));
        }

        @Override
        public void onSceneLight(Boolean dim, Boolean fogByVision) {
            Platform.runLater(() -> {
                if (dim != null) sceneService.getScene().setDim(dim);
                if (fogByVision != null) sceneService.getScene().setFogByVision(fogByVision);
            });
        }

        @Override
        public void onWallsSync(List<com.vttcaseiro.model.Wall> walls, Boolean visionOn, Boolean dark) {
            Platform.runLater(() -> table.applyRemoteWalls(walls, visionOn, dark));
        }

        @Override
        public void onPinsSync(List<com.vttcaseiro.model.MapPin> pins) {
            Platform.runLater(() -> table.applyRemotePins(pins));
        }

        @Override
        public void onObjectsSync(List<com.vttcaseiro.model.MapObject> objects) {
            Platform.runLater(() -> table.applyRemoteObjects(objects));
        }

        @Override
        public void onChatImage(String player, String assetId, String caption, String whisperTo) {
            Platform.runLater(() -> logEntries.add(LogEntry.image(player, caption, assetId, whisperTo)));
        }

        @Override
        public void onImageStored(String assetId) {
            Platform.runLater(() -> {
                if (chatListView != null && logEntries.stream().anyMatch(e -> assetId.equals(e.imageAssetId()))) {
                    chatImages.remove(assetId);
                    chatListView.refresh();
                }
                SceneModel sc = sceneService.getScene();
                if (sc.getObjects().stream().anyMatch(o -> assetId.equals(o.getAssetId()))) mapView.redrawObjects();
            });
        }

        @Override
        public void onDrawingAdded(com.vttcaseiro.model.Drawing drawing) {
            Platform.runLater(() -> table.applyRemoteDrawingAdd(drawing));
        }

        @Override
        public void onDrawingRemoved(String id) {
            Platform.runLater(() -> table.applyRemoteDrawingRemove(id));
        }

        @Override
        public void onDrawingsCleared(String ownerId) {
            Platform.runLater(() -> table.applyRemoteDrawingClear(ownerId));
        }

        @Override
        public void onHandoutSync(com.vttcaseiro.model.Handout handout) {
            Platform.runLater(() -> {
                boolean isNew = !receivedHandouts.containsKey(handout.getId());
                receivedHandouts.put(handout.getId(), handout);
                refreshJournal();
                logEntries.add(LogEntry.system((isNew ? "Nova anotação no diário: " : "Anotação atualizada no diário: ")
                        + handout.getTitle() + " (aba Diário)"));
            });
        }

        @Override
        public void onHandoutRemoved(String id) {
            Platform.runLater(() -> {
                receivedHandouts.remove(id);
                refreshJournal();
            });
        }

        @Override
        public void onGmRequest(String player, com.vttcaseiro.model.GmRequest request) {
            Platform.runLater(() -> {
                if (!approveRequests) {
                    table.handleGmRequest(request, player);
                    return;
                }
                Alert ask = new Alert(Alert.AlertType.CONFIRMATION, describeRequest(request, player),
                        javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
                ask.setTitle("Pedido de " + player);
                ask.setHeaderText("Aplicar?");
                ask.initOwner(stage);
                if (ask.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.YES).isPresent()) {
                    table.handleGmRequest(request, player);
                } else {
                    announce("O mestre recusou o pedido de " + player + ".");
                }
            });
        }

        @Override
        public void onMapPing(String player, double x, double y) {
            Platform.runLater(() -> mapView.showPing(x, y, player));
        }

        @Override
        public void onWhisper(String player, String to, String text) {
            Platform.runLater(() -> logEntries.add(LogEntry.whisper(player, to, text)));
        }

        @Override
        public void onTokenUpdated(TokenModel token) {
            if (token == null) return;
            Platform.runLater(() -> sceneService.updateToken(token));
        }

        @Override
        public void onChat(String player, String text) {
            Platform.runLater(() -> {
                if ("servidor".equals(player)) {
                    logEntries.add(LogEntry.system(text));
                } else {
                    logEntries.add(LogEntry.chat(player, text));
                }
            });
        }

        @Override
        public void onCharacterSync(CharacterSheet character) {
            if (character == null) return;
            Platform.runLater(() -> {
                try {
                    // Guarda a ficha recebida na biblioteca da mesa (igual
                    // salvar localmente) e no cache de "fichas abertas" do
                    // service -- assim ela ja aparece na lista da mesa pra
                    // todo mundo, nao so na tela de quem a criou.
                    // storeFromNetwork (e nao save): troca a copia em memoria pela
                    // da rede e nunca recusa por nome -- e a ficha de outro jogador.
                    characterService.storeFromNetwork(character);
                    CharacterSheetView view = openSheetViews.get(character.getId());
                    if (view != null) view.reload(character);
                    refreshCharacterList();
                    if (networkService.isGm()) table.pushSheetToTokens(character);
                    logEntries.add(LogEntry.system("Ficha sincronizada: " + character.getName()));
                } catch (IOException ex) {
                    logEntries.add(LogEntry.error("Não foi possível guardar ficha recebida: " + ex.getMessage()));
                }
            });
        }

        @Override
        public void onMobSync(MobSheet mob) {
            if (mob == null) return;
            Platform.runLater(() -> {
                try {
                    mobService.save(mob);
                    mobCountLabel.setText(mobService.list().size() + " monstro(s) nesta mesa.");
                    refreshMobList();
                    logEntries.add(LogEntry.system("Monstro sincronizado: " + mob.getName()));
                } catch (IOException ex) {
                    logEntries.add(LogEntry.error("Não foi possível guardar monstro recebido: " + ex.getMessage()));
                }
            });
        }

        @Override
        public void onPartySync(Party party) {
            if (party == null) return;
            Platform.runLater(() -> {
                partyService.replaceParty(party);
                refreshPartyMemberList();
                refreshPartyInventoryList();
                logEntries.add(LogEntry.system("Inventário da party sincronizado."));
            });
        }

        @Override
        public void onDisconnected(String reason) {
            // NetworkService ja zerou o client/server e atualizou o status
            // (netStatusLabel esta vinculado a ele, nao precisa mexer aqui) --
            // so falta avisar no chat.
            Platform.runLater(() -> logEntries.add(LogEntry.error("Desconectado: " + reason)));
        }
    }

    // ==================================================================
    // Acoes - dados / chat
    // ==================================================================

    /** Executa o que o ChatCommands entendeu do texto digitado. */
    private void onChatInput(String raw) {
        ChatCommands.Command cmd = ChatCommands.parse(raw);
        switch (cmd.type()) {
            case EMPTY -> { }
            case HELP -> logEntries.add(LogEntry.system(cmd.text()));
            case ERROR -> logEntries.add(LogEntry.error(cmd.text()));
            case ROLL -> onRollDice(cmd.text());
            case HIDDEN_ROLL -> rollLabeled(null, cmd.text(), true);
            case WHISPER -> {
                if (networkService.isConnected()) {
                    networkService.sendWhisper(cmd.target(), cmd.text());
                } else {
                    logEntries.add(LogEntry.whisper(networkService.getPlayerName(), cmd.target(),
                            com.vttcaseiro.dice.InlineRolls.expand(cmd.text())));
                }
            }
            case TEXT, EMOTE -> announce(cmd.text());
            case TABLE -> rollRandomTable(cmd.text(), hiddenQuickRolls != null && hiddenQuickRolls.isSelected());
        }
    }

    // ---------------- Tabelas aleatorias ----------------

    private java.util.Optional<com.vttcaseiro.model.RandomTable> findTable(String name) {
        String key = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).strip();
        com.vttcaseiro.model.RandomTable prefix = null;
        for (com.vttcaseiro.model.RandomTable t : tableStore.load()) {
            String n = java.text.Normalizer.normalize(t.getName(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
            if (n.equals(key)) return java.util.Optional.of(t);
            if (prefix == null && !key.isEmpty() && n.startsWith(key)) prefix = t;
        }
        return java.util.Optional.ofNullable(prefix);
    }

    /** Rola a tabela e anuncia ("Tabela Clima (d6 = 3): Garoa fina"); escondida vai so pro mestre. */
    private void rollRandomTable(String name, boolean hidden) {
        if (name == null || name.isBlank()) {
            List<String> names = tableStore.load().stream().map(com.vttcaseiro.model.RandomTable::getName).toList();
            logEntries.add(LogEntry.system(names.isEmpty() ? "Nenhuma tabela nesta mesa (aba Diário > Tabelas)."
                    : "Tabelas: " + String.join(", ", names) + ".  Use /tabela Nome"));
            return;
        }
        java.util.Optional<com.vttcaseiro.model.RandomTable> table = findTable(name);
        if (table.isEmpty()) {
            logEntries.add(LogEntry.error("Tabela \"" + name + "\" não encontrada. Digite /tabela pra ver a lista."));
            return;
        }
        String result = table.get().roll(n -> java.util.concurrent.ThreadLocalRandom.current().nextInt(1, n + 1));
        String text = "Tabela " + table.get().getName() + " (" + result + ")";
        if (hidden) tablePort.announceGm(text); else announce(text);
    }

    private javafx.scene.Node buildTablesSection() {
        ListView<String> list = new ListView<>();
        list.setPrefHeight(130);
        Runnable reload = () -> list.getItems().setAll(tableStore.load().stream()
                .map(com.vttcaseiro.model.RandomTable::getName).toList());
        reload.run();
        CheckBox hidden = new CheckBox("Só pra mim");
        Button roll = new Button("Rolar");
        roll.getStyleClass().add("button-primary");
        roll.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n != null) rollRandomTable(n, hidden.isSelected());
        });
        list.setOnMouseClicked(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && n != null) rollRandomTable(n, hidden.isSelected());
        });
        Button add = new Button("Nova");
        add.setOnAction(e -> {
            editTable(null);
            reload.run();
        });
        Button edit = new Button("Editar");
        edit.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n != null) findTable(n).ifPresent(t -> {
                editTable(t);
                reload.run();
            });
        });
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n == null) return;
            List<com.vttcaseiro.model.RandomTable> all = tableStore.load();
            all.removeIf(t -> t.getName().equals(n));
            tableStore.save(all);
            reload.run();
        });
        Label hint = new Label("Clique duplo rola. No chat: /tabela Nome. Linhas com faixa: \"1-3: goblins\".");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        VBox box = new VBox(6, sectionLabel("Tabelas aleatórias (mestre)"), list,
                new javafx.scene.layout.FlowPane(6, 6, roll, hidden, add, edit, remove), hint);
        Runnable gmOnly = () -> {
            boolean gm = networkService.isGm();
            box.setVisible(gm);
            box.setManaged(gm);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(gmOnly));
        gmOnly.run();
        return box;
    }

    private void editTable(com.vttcaseiro.model.RandomTable existing) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(existing == null ? "Nova tabela" : "Editar tabela");
        dialog.setHeaderText("Uma linha por resultado (sem faixa, cada linha vale 1 no dado)");
        dialog.initOwner(stage);
        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("Encontros na floresta");
        javafx.scene.control.TextArea entries = new javafx.scene.control.TextArea(existing == null ? "" : existing.getEntries());
        entries.setPromptText("1-3: 2d4 lobos\n4-5: um urso-coruja\n6: um druida perdido");
        entries.setPrefRowCount(10);
        VBox content = new VBox(8, new Label("Nome"), name, new Label("Resultados"), entries);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (name.getText().isBlank() || entries.getText().isBlank()) return;
            List<com.vttcaseiro.model.RandomTable> all = tableStore.load();
            if (existing != null) all.removeIf(t -> t.getId().equals(existing.getId()));
            all.removeIf(t -> t.getName().equalsIgnoreCase(name.getText().strip()));
            all.add(new com.vttcaseiro.model.RandomTable(name.getText(), entries.getText()));
            tableStore.save(all);
        });
    }

    // ---------------- Calculadora de encontro (2024) ----------------

    private void onEncounterCalculator() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Calculadora de encontro (regras 2024)");
        dialog.setHeaderText("Grupo e monstros já vem do mapa; ajuste a vontade");
        dialog.initOwner(stage);
        List<Integer> levels = new java.util.ArrayList<>();
        java.util.Set<String> seenSheets = new java.util.HashSet<>();
        ObservableList<String> monsters = FXCollections.observableArrayList();
        java.util.Map<String, Integer> monsterXp = new java.util.HashMap<>();
        List<TokenModel> pool = mapView.getSelectedTokens().size() > 1 ? mapView.getSelectedTokens()
                : sceneService.getScene().getTokens();
        for (TokenModel t : sceneService.getScene().getTokens()) {
            table.linkedSheet(t).ifPresent(s -> {
                if (seenSheets.add(s.getId())) levels.add(s.getLevel());
            });
        }
        int idx = 0;
        for (TokenModel t : pool) {
            if (t.getMobSheetId() == null) continue;
            java.util.Optional<MobSheet> mob = mobService.findById(t.getMobSheetId());
            if (mob.isEmpty()) continue;
            String label = t.getName() + " (" + mob.get().getExperiencePoints() + " XP)#" + (idx++);
            monsters.add(label);
            monsterXp.put(label, mob.get().getExperiencePoints());
        }
        TextField party = new TextField(levels.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("3, 3, 3, 3"));
        party.setPromptText("níveis separados por virgula: 3, 3, 4, 3");
        ListView<String> monsterList = new ListView<>(monsters);
        monsterList.setPrefHeight(140);
        monsterList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.substring(0, s.lastIndexOf('#')));
            }
        });
        ComboBox<String> bestiary = new ComboBox<>(FXCollections.observableArrayList(mobService.list()));
        bestiary.setEditable(false);
        bestiary.setPromptText("Monstro do bestiário");
        javafx.scene.control.Spinner<Integer> count = new javafx.scene.control.Spinner<>(1, 30, 1);
        count.setPrefWidth(70);
        Label result = new Label();
        result.setWrapText(true);
        result.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #FFCF7F;");
        Runnable recalc = () -> {
            List<Integer> lv = new java.util.ArrayList<>();
            for (String p : party.getText().split("[,;\\s]+")) {
                try {
                    if (!p.isBlank()) lv.add(Integer.parseInt(p.strip()));
                } catch (NumberFormatException ignored) {
                }
            }
            List<Integer> xp = new java.util.ArrayList<>();
            for (String m : monsters) xp.add(monsterXp.getOrDefault(m, 0));
            result.setText(com.vttcaseiro.service.EncounterCalculator.evaluate(lv, xp).summary());
        };
        Button addMob = new Button("Adicionar");
        addMob.setOnAction(e -> {
            String name = bestiary.getValue();
            if (name == null) return;
            try {
                MobSheet mob = mobService.open(name);
                for (int i = 0; i < count.getValue(); i++) {
                    String label = mob.getName() + " (" + mob.getExperiencePoints() + " XP)#" + System.nanoTime() + i;
                    monsters.add(label);
                    monsterXp.put(label, mob.getExperiencePoints());
                }
                recalc.run();
            } catch (IOException ex) {
                showError("Não foi possível abrir o monstro: " + ex.getMessage());
            }
        });
        Button removeMob = new Button("Tirar selecionado");
        removeMob.setOnAction(e -> {
            String sel = monsterList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                monsters.remove(sel);
                recalc.run();
            }
        });
        party.textProperty().addListener((o, a, b) -> recalc.run());
        recalc.run();
        VBox content = new VBox(8, new Label("Níveis do grupo"), party,
                new Label(pool == sceneService.getScene().getTokens() ? "Monstros (do mapa)" : "Monstros (selecionados)"),
                monsterList, new HBox(6, bestiary, count, addMob, removeMob), result,
                new Label("Baixa: desafio leve | Moderada: gasta recursos | Alta: perigoso, pode derrubar alguém."));
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ---------------- Atalhos de teclado ----------------

    private void onMapKey(javafx.scene.input.KeyEvent e) {
        if (stage.getScene() == null || mapView == null) return;
        javafx.scene.Node focus = stage.getScene().getFocusOwner();
        if (focus instanceof javafx.scene.control.TextInputControl || focus instanceof javafx.scene.control.ComboBoxBase) return;
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        List<TokenModel> sel = mapView.getSelectedTokens();
        boolean handled = true;
        switch (e.getCode()) {
            case DELETE, BACK_SPACE -> {
                List<TokenModel> mine = sel.stream().filter(table::canControl).toList();
                if (mine.isEmpty()) break;
                boolean ok = mine.size() == 1;
                if (!ok) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remover " + mine.size() + " tokens do mapa?");
                    confirm.setHeaderText(null);
                    confirm.initOwner(stage);
                    ok = confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isPresent();
                }
                if (ok) {
                    mine.forEach(tokenActions::remove);
                    mapView.clearSelection();
                }
            }
            case T -> sel.forEach(mapView::toggleTarget);
            case R -> {
                double deg = e.isShiftDown() ? -45 : 45;
                for (TokenModel t : sel) if (table.canControl(t)) tokenActions.rotate(t, deg, false);
            }
            case H -> {
                if (!networkService.isGm() || sel.isEmpty()) break;
                boolean allHidden = sel.stream().allMatch(TokenModel::isHiddenFromPlayers);
                sel.forEach(t -> tokenActions.setHidden(t, !allHidden));
            }
            case ESCAPE, DIGIT0, NUMPAD0 -> {
                toolsOff.run();
                mapView.clearSelection();
            }
            case UP -> mapView.nudgeSelection(0, -1);
            case DOWN -> mapView.nudgeSelection(0, 1);
            case LEFT -> mapView.nudgeSelection(-1, 0);
            case RIGHT -> mapView.nudgeSelection(1, 0);
            case F1 -> showShortcuts();
            default -> {
                int n = switch (e.getCode()) {
                    case DIGIT1, NUMPAD1 -> 1; case DIGIT2, NUMPAD2 -> 2; case DIGIT3, NUMPAD3 -> 3;
                    case DIGIT4, NUMPAD4 -> 4; case DIGIT5, NUMPAD5 -> 5; case DIGIT6, NUMPAD6 -> 6;
                    case DIGIT7, NUMPAD7 -> 7; case DIGIT8, NUMPAD8 -> 8; case DIGIT9, NUMPAD9 -> 9;
                    default -> 0;
                };
                if (n > 0 && n <= toolHotkeys.size()) {
                    ToggleButton b = toolHotkeys.get(n - 1);
                    // abre a categoria da ferramenta sozinho; neblina e paredes (5-9) so pro mestre
                    if (n <= 5 || networkService.isGm()) b.fire();
                } else {
                    handled = false;
                }
            }
        }
        if (handled) e.consume();
    }

    private void showShortcuts() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.initOwner(stage);
        help.setTitle("Atalhos do mapa");
        help.setHeaderText("Atalhos (com o foco fora dos campos de texto)");
        help.setContentText("1 Medir   2 Área   3 Lápis   4 Texto   5 Borracha\n"
                + "6 Revelar neblina   7 Cobrir neblina   8 Parede   9 Porta   (mestre: 6-9)\n"
                + "Esc / 0  desliga a ferramenta e limpa a seleção\n\n"
                + "Shift+arrastar no mapa vazio: seleciona vários tokens\n"
                + "Shift+clique: poe/tira um token da seleção\n"
                + "Setas: anda a seleção uma casa   Delete: remove   T: mira   H: esconder/revelar (mestre)\n"
                + "Ctrl+clique: mira   Alt+clique: ping   Ctrl+Z: desfaz   F1: esta ajuda");
        help.showAndWait();
    }

    private void onRollDice(String expression) {
        rollLabeled(null, expression, hiddenQuickRolls != null && hiddenQuickRolls.isSelected());
    }

    private void onRollAdvantage() {
        boolean hidden = hiddenQuickRolls != null && hiddenQuickRolls.isSelected();
        if (networkService.isConnected()) {
            networkService.sendRoll("Vantagem", "1d20", "advantage", 0, hidden);
            return;
        }
        addLocalRoll("Vantagem", DiceRoller.rollWithAdvantage(0), hidden);
    }

    private void onRollDisadvantage() {
        boolean hidden = hiddenQuickRolls != null && hiddenQuickRolls.isSelected();
        if (networkService.isConnected()) {
            networkService.sendRoll("Desvantagem", "1d20", "disadvantage", 0, hidden);
            return;
        }
        addLocalRoll("Desvantagem", DiceRoller.rollWithDisadvantage(0), hidden);
    }

    /** Codifica um arquivo de imagem pra viajar pela rede dentro da
     * propria mensagem (ver ImageCodec). Se nao der (arquivo grande
     * demais, sem permissao de leitura, etc.), avisa no chat e devolve
     * null -- quem chamou continua criando o token/cena mesmo assim, so
     * sem a imagem chegando pros outros jogadores (localmente continua
     * funcionando normal, pelo caminho do arquivo). */
    private String encodeImageOrWarn(String imagePath) {
        if (imagePath == null) {
            return null;
        }
        try {
            return ImageCodec.encodeFile(Path.of(imagePath));
        } catch (IOException ex) {
            logEntries.add(LogEntry.system(
                    "Aviso: " + ex.getMessage() + " A imagem só vai aparecer nesta maquina."));
            return null;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
