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
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.CardDeck> deckStore;
    private com.vttcaseiro.library.JsonListStore<String> sceneQueueStore;
    private com.vttcaseiro.library.AudioAssetStore audioStore;
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.SoundEntry> soundIndex;
    /** Anotacoes so minhas (ficam nesta maquina) e as do grupo (o mestre guarda pra mandar de novo na proxima sessao). */
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.Handout> myNotesStore;
    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.Handout> groupNotesStore;
    private final java.util.Map<String, com.vttcaseiro.model.Handout> groupNotes = new java.util.LinkedHashMap<>();
    private final ObservableList<com.vttcaseiro.model.Handout> playerNoteItems = FXCollections.observableArrayList();
    private ListView<com.vttcaseiro.model.Handout> playerNotesList;
    private Label playerNoteViewer;
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
    private double sidePanelDivider = 0.72;
    private VBox chatColumn;
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
        this.campaign = com.vttcaseiro.model.Campaign.load(mesa.getRoot());
        wireCampaign(); // a campanha vai pra mesa assim que o mestre hospedar, mesmo sem abrir a janela
        syncOfficialClasses();
        this.table = new TableService(sceneService, characterService, tablePort);
        this.table.setMobLookup(id -> mobService.findById(id).orElse(null));
        this.macroStore = new com.vttcaseiro.library.MacroStore(mesa.getRoot());
        this.handoutStore = new com.vttcaseiro.library.HandoutStore(mesa.getRoot());
        this.templateStore = new com.vttcaseiro.library.TemplateStore(mesa.getRoot());
        this.tableStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "tabelas.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.RandomTable>>() { }.getType());
        if (!tableStore.exists()) tableStore.save(new java.util.ArrayList<>(com.vttcaseiro.model.RandomTable.samples()));
        this.deckStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "baralhos.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.CardDeck>>() { }.getType());
        this.shopStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "lojas.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.Shop>>() { }.getType());
        this.shopsWorking = null;
        try {
            this.audioStore = new com.vttcaseiro.library.AudioAssetStore(mesa.getRoot());
        } catch (IOException ex) {
            this.audioStore = null;
        }
        this.soundIndex = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "sons.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.SoundEntry>>() { }.getType());
        this.myNotesStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "minhas-notas.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.Handout>>() { }.getType());
        this.groupNotesStore = new com.vttcaseiro.library.JsonListStore<>(mesa.getRoot(), "notas-do-grupo.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.Handout>>() { }.getType());
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

        Scene scene = new Scene(root, 1366, 860);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        // Ctrl+Z desfaz a ultima acao no mapa (campos de texto continuam
        // com o proprio Ctrl+Z, que eles consomem antes deste atalho).
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onMapKey);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onSidebarKey);
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
        sessionStartIndex = logEntries.size(); // o resumo da sessao comeca aqui (o historico de antes fica fora)
        logEntries.add(LogEntry.system("Mesa \"" + mesaService.getName()
                + "\" aberta. Crie uma cena ou uma ficha para começar."));
        if (compendiumService.getCompendium().getEntries().isEmpty() && history.isEmpty()) {
            // so na primeira vez que a mesa abre (depois disso o historico do chat ja tem a dica)
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
                if (mapView != null) mapView.release(); // libera a memoria de video do mapa que sai
                Platform.runLater(Main.this::refreshSceneStrip);
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
                scheduleAuras();
            }

            @Override
            public void onTokenMoved(TokenModel token) {
                mapView.rebuildTokens();
                scheduleSceneAutosave();
                scheduleAuras();
                if (table != null) table.revealFogByVision();   // so faz algo no mestre, com a opcao ligada
            }

            @Override
            public void onTokenRemoved(TokenModel token) {
                mapView.rebuildTokens();
                refreshInitiativeList();
                scheduleSceneAutosave();
                scheduleAuras();
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
            if (!undoing && !networkService.isConnected()) {
                for (com.vttcaseiro.model.MapZone z : com.vttcaseiro.service.ZoneRules.entered(sceneService.getScene(), token, old[0], old[1])) {
                    if (z.isOnce()) z.setTriggered(true);
                    onZoneEntered(z, token, null, null);
                }
            }
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

    /** Linhas do chat "Dano aplicado: ..." que ainda dao pra desfazer (pela linha, nao pelo texto). */
    private final java.util.Map<LogEntry, Runnable> hpUndos = new java.util.IdentityHashMap<>();

    /**
     * Depois de aplicar dano/cura nos alvos: uma linha no chat (so nesta tela) com o antes e depois de cada token e o
     * botao "Desfazer" (tambem no Ctrl+Z).
     */
    private void registerHpUndo(java.util.Map<TokenModel, Integer[]> before, String what) {
        List<String> parts = new ArrayList<>();
        java.util.Map<TokenModel, Integer[]> changed = new java.util.LinkedHashMap<>();
        before.forEach((t, hp) -> {
            if (java.util.Objects.equals(hp[0], t.getCurrentHp()) && java.util.Objects.equals(hp[2], t.getTempHp())) return;
            changed.put(t, hp);
            parts.add(t.getName() + " " + (hp[0] == null ? "?" : hp[0]) + " → " + (t.getCurrentHp() == null ? "?" : t.getCurrentHp()));
        });
        if (changed.isEmpty()) return;
        LogEntry line = LogEntry.system(what + " aplicado: " + String.join(", ", parts) + " PV.");
        Runnable[] undoRef = new Runnable[1];
        undoRef[0] = () -> {
            if (hpUndos.remove(line) == null) return; // ja desfeito
            undoOrder.removeIf(u -> u.undo() == undoRef[0]);
            changed.forEach((t, hp) -> sceneService.getScene().findToken(t.getId())
                    .ifPresent(live -> table.setHp(live, hp[0], hp[1], hp[2])));
            logEntries.add(LogEntry.system(what + " desfeito: " + String.join(", ", changed.keySet().stream()
                    .map(TokenModel::getName).toList()) + " voltou aos PV de antes."));
            if (chatListView != null) chatListView.refresh();
        };
        hpUndos.put(line, undoRef[0]);
        pushUndo(new UndoAction(what + " nos alvos", undoRef[0]));
        logEntries.add(line);
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
        @Override public void sendZones(SceneModel s) { networkService.sendZonesSync(s.getZones()); }
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
        public void viewAs(TokenModel token) {
            Main.this.viewAs(java.util.Set.of(token.getId()), token.getName());
        }

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
        @Override public void editAuras(TokenModel token) {
            showAurasDialog(token);
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
        public void setSize(TokenModel token, double width, double height) {
            recorded("Tamanho de " + token.getName(), token, () -> table.setSize(token, width, height));
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
            File file = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
        if (mob.usesTemplate()) {
            openTemplateMob(mob, token);
            return;
        }
        String name = token != null && token.getName() != null ? token.getName() : mob.getName();
        new MobSheetView(mob, name, mobActionsFor(mob, token), trait -> onMobTraitClicked(mob, trait)).show();
    }

    /** Rolagens da ficha de monstro (acerto contra os alvos, critico, dano), pela janela ou pela barra do token. */
    private MobSheetView.Actions mobActionsFor(MobSheet mob, TokenModel token) {
        String name = token != null && token.getName() != null ? token.getName() : mob.getName();
        String mobKey = "mob:" + (token != null ? token.getId() : mob.getId());
        return new MobSheetView.Actions() {
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
        if (rollsOverNetwork()) {
            expectRoll(title, after);
            sendModeRoll(title, d20Expression(finalMod), mode, finalMod, hidden);
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
        if (sheet.usesTemplate()) {
            openTemplateCharacter(sheet);
            return;
        }
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
        sceneStrip.getStyleClass().add("scene-strip");
        sceneStrip.setAlignment(Pos.CENTER_LEFT);
        playersStrip.getStyleClass().add("players-strip");
        playersStrip.setAlignment(Pos.CENTER);
        refreshSceneStrip();
        refreshPlayersStrip();
        networkService.getOnlinePlayers().addListener((javafx.collections.ListChangeListener<String>) c ->
                Platform.runLater(this::refreshPlayersStrip));
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            refreshSceneStrip();
            refreshPlayersStrip();
        }));

        Button hint = new Button("Atalhos (F1)");
        hint.getStyleClass().add("button-flat");
        hint.setStyle("-fx-font-size: 12px;");
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
        gear.setTooltip(new Tooltip("Configurações: tema, backup e cópias automáticas"));
        gear.setOnAction(e -> showMesaSettings());
        Button campaignButton = new Button("Campanha", com.vttcaseiro.view.Icons.icon("journal", 14));
        campaignButton.getStyleClass().add("button-flat");
        campaignButton.setTooltip(new Tooltip("Gerenciador de campanha: jogadores (XP, ouro), calendário e clima, PdMs, "
                + "aventuras, sessões e anotações. O que você marcar como visível os jogadores veem."));
        campaignButton.setOnAction(e -> openCampaign());
        cameraLockButton = new ToggleButton(null, com.vttcaseiro.view.Icons.icon("eye", 14));
        cameraLockButton.getStyleClass().addAll("button-flat", "camera-lock-button");
        cameraLockButton.setTooltip(new Tooltip("Guiar a câmera: os jogadores veem o mesmo pedaço do mapa que você (zoom e "
                + "posição) e não mexem na vista até você soltar. Bom pra narrar. Shift+Alt+clique: \"olhem aqui\" "
                + "(leva a tela de todos pro ponto, sem travar)."));
        cameraLockButton.setOnAction(e -> sendCameraNow());
        Runnable cameraButtonByRole = () -> {
            boolean show = networkService.isGm() && networkService.isConnected();
            cameraLockButton.setVisible(show);
            cameraLockButton.setManaged(show);
            if (!show) cameraLockButton.setSelected(false);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(cameraButtonByRole));
        cameraButtonByRole.run();
        Button tvButton = new Button("TV", com.vttcaseiro.view.Icons.icon("image", 14));
        tvButton.getStyleClass().add("button-flat");
        tvButton.setTooltip(new Tooltip("Tela da mesa pra TV ou segundo monitor (jogo presencial): mostra o mapa como os "
                + "jogadores veem e segue a sua câmera. Clique duplo nela = tela cheia."));
        tvButton.setOnAction(e -> openTvWindow());
        Runnable tvByRole = () -> {
            boolean show = !networkService.isConnected() || networkService.isGm();
            tvButton.setVisible(show);
            tvButton.setManaged(show);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(tvByRole));
        tvByRole.run();
        bar.getChildren().addAll(brand, sep, title, sceneStrip, spacer, returnSceneButton, cameraLockButton, tvButton,
                playersStrip, hint, campaignButton, gear, netStatusLabel);
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
        List<File> files = com.vttcaseiro.view.ImagePicker.openMultiple(chooser, stage);
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
        File file = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
            com.vttcaseiro.view.SafeImage.Loaded l = com.vttcaseiro.view.SafeImage.read(imageAssetService.readBytes(assetId), 2048, 2048L * 2048L);
            javafx.scene.image.Image img = l == null ? null : l.image();
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
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
        if (preparing()) pin.setHidden(true);
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
            case "rotulo" -> "Rótulo (só o texto no mapa)";
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
        // tema: o Theme poe na janela (folha no proprio DialogPane venceria o tema escolhido)

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
        // rotulo: so o texto no mapa; escolhe o tamanho da letra (em pixels do mapa) e o estilo
        javafx.scene.control.Spinner<Integer> labelSize = new javafx.scene.control.Spinner<>(6, 400, (int) Math.round(pin.getLabelSize()), 2);
        labelSize.setEditable(true);
        labelSize.setPrefWidth(90);
        labelSize.setTooltip(new Tooltip("Altura da letra no mapa (cresce com o zoom, como se estivesse desenhada)."));
        ComboBox<String> labelStyle = new ComboBox<>(FXCollections.observableArrayList("Título (reino, região)", "Lugar (cidade, rio)"));
        labelStyle.getSelectionModel().select(com.vttcaseiro.model.MapPin.STYLE_TITLE.equals(pin.getLabelStyle()) ? 0 : 1);
        HBox labelOpts = new HBox(6, new Label("Letra"), labelSize, labelStyle);
        labelOpts.setAlignment(Pos.CENTER_LEFT);
        Runnable showLabelOpts = () -> {
            boolean on = com.vttcaseiro.model.MapPin.LABEL.equals(kind.getValue());
            labelOpts.setVisible(on);
            labelOpts.setManaged(on);
        };
        kind.valueProperty().addListener((o, a, b) -> showLabelOpts.run());
        showLabelOpts.run();
        HBox kindRow = new HBox(10, kind, labelOpts);
        kindRow.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(1, new Label("Tipo"), kindRow);
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
        if (pin.isLabel()) {
            pin.setLabelSize(labelSize.getValue());
            pin.setLabelStyle(labelStyle.getSelectionModel().getSelectedIndex() == 0
                    ? com.vttcaseiro.model.MapPin.STYLE_TITLE : com.vttcaseiro.model.MapPin.STYLE_PLACE);
        }
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
        File file = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
            Button del = new Button("Apagar", com.vttcaseiro.view.Icons.icon("trash", 13));
            del.setTooltip(new Tooltip("Apaga este pin (dá pra desfazer com Ctrl+Z). Pra mover, arraste o pin no mapa."));
            del.setOnAction(e -> {
                mapStack.getChildren().remove(pinCard);
                pinCard = null;
                table.removePin(pin);
                if (sceneService.getScene().findPin(pin.getId()).isEmpty()) {
                    pushUndo(new UndoAction("Apagar pin", () -> table.addPin(pin)));
                }
            });
            actions.getChildren().add(del);
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
            // cena escondida: o jogador ve o aviso "o mestre esta preparando" do proprio mapa, nao este cartao
            card.setVisible(empty && !(sc.isHiddenFromPlayers() && !gm));
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
        mainSplit = new SplitPane();
        mainSplit.getStyleClass().add("main-split");
        mainSplit.getItems().addAll(mapHolder, sidePanel);
        SplitPane.setResizableWithParent(sidePanel, false);
        SplitPane.setResizableWithParent(collapsedSidebar, false);
        mainSplit.setDividerPositions(sidePanelDivider);
        openPanel("Chat");
        return mainSplit;
    }

    /**
     * Barra da direita: UMA so, com abas agrupadas (Mesa, Combate, Personagens, Mundo, Som, Rede). Cada aba mostra
     * uma secao por vez; as com mais de uma (Personagens: Fichas e Grupo; Mundo: Cenas e Diario) tem um seletor
     * pequeno em cima. Recolhe numa faixa fina de icones e o mapa fica com quase a tela toda.
     */
    private void buildSidePanel() {
        String[] names = {"Mapa", "Fichas", "Party", "Combate", "Diário", "Som", "Rede"};
        Tab[] tabs = {buildMapTab(), buildCharacterTab(), buildPartyTab(), buildInitiativeTab(), buildJournalTab(),
                buildSoundTab(), buildNetworkTab()};
        for (int i = 0; i < tabs.length; i++) {
            javafx.scene.Node content = tabs[i].getContent();
            tabs[i].setContent(null);
            compactHints(content);
            groupIntoCards(content, tabs[i].getText());
            content.setVisible(false);
            panelContents.put(names[i], content);
            sidePanelBody.getChildren().add(content);
        }
        chatColumn = buildChatPanel();
        chatColumn.setMinWidth(260); // a fileira de dados pedia 421 px e empurrava a barra pra fora da tela
        chatColumn.setVisible(false);
        panelContents.put("Chat", chatColumn);
        sidePanelBody.getChildren().add(chatColumn);

        sidePanelTitle = new Label();
        sidePanelTitle.getStyleClass().add("side-panel-title");
        sectionSwitch.getStyleClass().add("section-switch");
        sectionSwitch.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // o nome da aba ja esta na propria aba: aqui so o seletor de secoes (quando a aba tem mais de uma)
        sidePanelTitle.setVisible(false);
        sidePanelTitle.setManaged(false);
        HBox titleRow = new HBox(8, sidePanelTitle, sectionSwitch);
        titleRow.setAlignment(Pos.CENTER);
        titleRow.getStyleClass().add("side-panel-header");
        sectionRow = titleRow;
        HBox.setHgrow(sectionSwitch, Priority.ALWAYS);
        sectionSwitch.setMaxWidth(Double.MAX_VALUE);

        HBox tabsBar = new HBox(2);
        tabsBar.getStyleClass().add("sidebar-tabs");
        tabsBar.setAlignment(Pos.CENTER_LEFT);
        VBox collapsedIcons = new VBox(4);
        collapsedIcons.getStyleClass().add("sidebar-tabs-collapsed");
        collapsedIcons.setAlignment(Pos.TOP_CENTER);
        int n = 0;
        for (var g : SIDEBAR_GROUPS.entrySet()) {
            String group = g.getKey();
            n++;
            String tip = SIDEBAR_LABELS.get(group) + " (Alt+" + n + "): " + SIDEBAR_TIPS.get(group);
            ToggleButton b = new ToggleButton(SIDEBAR_LABELS.get(group), tabFace(group, 18, false));
            b.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
            b.getStyleClass().add("sidebar-tab");
            b.setTooltip(new Tooltip(tip));
            b.setOnAction(e -> togglePanel(group));
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            railButtons.put(group, b);
            tabsBar.getChildren().add(b);
            Button c = new Button(null, tabFace(group, 18, true));
            c.getStyleClass().addAll("button-flat", "sidebar-tab");
            c.setTooltip(new Tooltip(tip));
            c.setOnAction(e -> openPanel(lastSection.getOrDefault(group, SIDEBAR_GROUPS.get(group)[0])));
            collapsedIcons.getChildren().add(c);
        }
        Button collapse = new Button("»");
        collapse.getStyleClass().addAll("button-flat", "sidebar-collapse");
        collapse.setTooltip(new Tooltip("Recolher a barra (o mapa fica maior)"));
        collapse.setOnAction(e -> closePanel());
        tabsBar.getChildren().add(collapse);
        // avisos nas abas: mensagem nova no chat, combate rolando
        logEntries.addListener((javafx.collections.ListChangeListener<LogEntry>) ch -> {
            boolean seeingChat = mainSplit != null && mainSplit.getItems().contains(sidePanel) && "Chat".equals(openPanelName);
            if (seeingChat) return;
            while (ch.next()) if (ch.wasAdded()) unreadChat += ch.getAddedSize();
            Platform.runLater(this::refreshTabBadges);
        });
        Button expand = new Button("«");
        expand.getStyleClass().addAll("button-flat", "sidebar-collapse");
        expand.setTooltip(new Tooltip("Abrir a barra"));
        expand.setOnAction(e -> openPanel(openPanelName != null ? openPanelName : "Chat"));
        collapsedIcons.getChildren().add(0, expand);

        VBox.setVgrow(sidePanelBody, Priority.ALWAYS);
        sidePanelBody.setMinWidth(0);
        sidePanel = new VBox(tabsBar, titleRow, sidePanelBody);
        sidePanel.getStyleClass().add("side-panel");
        sidePanel.setMinWidth(300);
        sidePanel.setPrefWidth(380);
        collapsedSidebar = collapsedIcons;
        collapsedSidebar.setMinWidth(46);
        collapsedSidebar.setMaxWidth(46);
        // ja com os cartoes montados: esconde o que e so do mestre, e de novo quando mudar o papel (conectou/hospedou)
        refreshInitiativeList();
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(this::refreshInitiativeList));
    }

    /** Nome curto que aparece embaixo do icone da aba. */
    static final java.util.Map<String, String> SIDEBAR_LABELS = java.util.Map.of("Mesa", "Mesa", "Combate", "Combate",
            "Personagens", "Fichas", "Mundo", "Mundo", "Som", "Som", "Rede", "Rede");

    private HBox sectionRow;
    private int unreadChat;
    /** Avisos de cada aba (aberta e recolhida). */
    private final java.util.Map<String, List<Label>> tabBadges = new java.util.HashMap<>();

    /** Icone da aba com o aviso (numero ou bolinha) no canto. */
    private javafx.scene.Node tabFace(String group, double size, boolean collapsed) {
        Label badge = new Label();
        badge.getStyleClass().add("tab-badge");
        badge.setVisible(false);
        badge.setMouseTransparent(true);
        tabBadges.computeIfAbsent(group, k -> new ArrayList<>()).add(badge);
        StackPane face = new StackPane(com.vttcaseiro.view.Icons.icon(SIDEBAR_ICONS.get(group), size), badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        StackPane.setMargin(badge, new Insets(-6, collapsed ? -8 : -12, 0, 0));
        face.setMinSize(size + 6, size + 2);
        return face;
    }

    /** Numero de mensagens novas na Mesa; bolinha no Combate quando ha combate rolando. */
    void refreshTabBadges() {
        boolean seeingChat = mainSplit != null && mainSplit.getItems().contains(sidePanel) && "Chat".equals(openPanelName);
        if (seeingChat) unreadChat = 0;
        for (Label l : tabBadges.getOrDefault("Mesa", List.of())) {
            l.setText(unreadChat > 99 ? "99+" : String.valueOf(unreadChat));
            l.setVisible(unreadChat > 0);
        }
        boolean combat = sceneService != null && sceneService.getScene().getEncounter().isActive();
        for (Label l : tabBadges.getOrDefault("Combate", List.of())) {
            l.setText("");
            l.getStyleClass().remove("tab-badge-dot");
            l.getStyleClass().add("tab-badge-dot");
            l.setVisible(combat);
        }
    }

    /** Alt+1..6: abre a aba (Alt+ a aba que ja esta aberta recolhe a barra). */
    private void onSidebarKey(javafx.scene.input.KeyEvent e) {
        if (!e.isAltDown() || e.isControlDown()) return;
        int i = switch (e.getCode()) {
            case DIGIT1, NUMPAD1 -> 0; case DIGIT2, NUMPAD2 -> 1; case DIGIT3, NUMPAD3 -> 2;
            case DIGIT4, NUMPAD4 -> 3; case DIGIT5, NUMPAD5 -> 4; case DIGIT6, NUMPAD6 -> 5;
            default -> -1;
        };
        if (i < 0 || i >= SIDEBAR_GROUPS.size()) return;
        togglePanel(new ArrayList<>(SIDEBAR_GROUPS.keySet()).get(i));
        e.consume();
    }

    /** Abas da barra da direita e as secoes de cada uma (a primeira abre por padrao). */
    static final java.util.Map<String, String[]> SIDEBAR_GROUPS = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, String> SIDEBAR_ICONS = new java.util.HashMap<>();
    static final java.util.Map<String, String> SIDEBAR_TIPS = new java.util.HashMap<>();

    static {
        SIDEBAR_GROUPS.put("Mesa", new String[]{"Chat"});
        SIDEBAR_GROUPS.put("Combate", new String[]{"Combate"});
        SIDEBAR_GROUPS.put("Personagens", new String[]{"Fichas", "Party"});
        SIDEBAR_GROUPS.put("Mundo", new String[]{"Mapa", "Diário"});
        SIDEBAR_GROUPS.put("Som", new String[]{"Som"});
        SIDEBAR_GROUPS.put("Rede", new String[]{"Rede"});
        SIDEBAR_ICONS.put("Mesa", "send");
        SIDEBAR_ICONS.put("Combate", "combat");
        SIDEBAR_ICONS.put("Personagens", "party");
        SIDEBAR_ICONS.put("Mundo", "map");
        SIDEBAR_ICONS.put("Som", "sound");
        SIDEBAR_ICONS.put("Rede", "network");
        SIDEBAR_TIPS.put("Mesa", "chat e rolagens");
        SIDEBAR_TIPS.put("Combate", "iniciativa, testes em grupo e descanso");
        SIDEBAR_TIPS.put("Personagens", "fichas, monstros, compêndio e o grupo");
        SIDEBAR_TIPS.put("Mundo", "cenas, grade e o diário");
        SIDEBAR_TIPS.put("Som", "música e sons ambiente");
        SIDEBAR_TIPS.put("Rede", "hospedar ou entrar numa mesa");
    }

    /** Nome que aparece pra cada secao. */
    static String sectionTitle(String section) {
        return switch (section) {
            case "Party" -> "Grupo";
            case "Mapa" -> "Cenas";
            default -> section;
        };
    }

    static String groupOf(String section) {
        for (var g : SIDEBAR_GROUPS.entrySet()) {
            for (String s : g.getValue()) if (s.equals(section)) return g.getKey();
        }
        return null;
    }

    private VBox collapsedSidebar;
    private final HBox sectionSwitch = new HBox(2);
    private final java.util.Map<String, String> lastSection = new java.util.HashMap<>();

    private void togglePanel(String group) {
        String current = openPanelName == null ? null : groupOf(openPanelName);
        if (group.equals(current) && mainSplit.getItems().contains(sidePanel)) closePanel();
        else openPanel(lastSection.getOrDefault(group, SIDEBAR_GROUPS.get(group)[0]));
    }

    /** Mostra a secao (abre a barra se estava recolhida). */
    void openPanel(String name) {
        if (!panelContents.containsKey(name)) return;
        String group = groupOf(name);
        openPanelName = name;
        if (group != null) lastSection.put(group, name);
        panelContents.forEach((k, v) -> v.setVisible(k.equals(name)));
        sidePanelTitle.setText(group == null ? sectionTitle(name) : group);
        // secoes da mesma aba: seletor pequeno
        sectionSwitch.getChildren().clear();
        String[] sections = group == null ? new String[0] : SIDEBAR_GROUPS.get(group);
        sectionSwitch.setVisible(sections.length > 1);
        sectionSwitch.setManaged(sections.length > 1);
        if (sectionRow != null) {
            sectionRow.setVisible(sections.length > 1);
            sectionRow.setManaged(sections.length > 1);
        }
        if (sections.length > 1) {
            javafx.scene.control.ToggleGroup tg = new javafx.scene.control.ToggleGroup();
            for (String sec : sections) {
                ToggleButton t = new ToggleButton(sectionTitle(sec));
                t.getStyleClass().add("section-switch-button");
                t.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(t, Priority.ALWAYS);
                t.setToggleGroup(tg);
                t.setSelected(sec.equals(name));
                t.setOnAction(e -> openPanel(sec));
                sectionSwitch.getChildren().add(t);
            }
        }
        if (!mainSplit.getItems().contains(sidePanel)) {
            mainSplit.getItems().remove(collapsedSidebar);
            mainSplit.getItems().add(sidePanel);
            mainSplit.setDividerPositions(sidePanelDivider);
        }
        refreshRail();
    }

    /** Recolhe a barra numa faixa de icones (o mapa fica com quase a tela toda). */
    private void closePanel() {
        if (mainSplit.getItems().contains(sidePanel)) {
            double[] pos = mainSplit.getDividerPositions();
            if (pos.length > 0) sidePanelDivider = pos[0];
            mainSplit.getItems().remove(sidePanel);
            mainSplit.getItems().add(collapsedSidebar);
            Platform.runLater(() -> {
                double w = mainSplit.getWidth();
                if (w > 0) mainSplit.setDividerPositions(1 - 48 / w);
            });
        }
        refreshRail();
    }

    private void refreshRail() {
        refreshTabBadges();
        boolean open = mainSplit != null && mainSplit.getItems().contains(sidePanel);
        String group = openPanelName == null ? null : groupOf(openPanelName);
        railButtons.forEach((k, b) -> b.setSelected(open && k.equals(group)));
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
            return n == 0 ? 68.0 : Math.min(max, n * LIST_ROW + 8);
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
            boolean title = (n instanceof Label l && l.getStyleClass().contains("label-section"))
                    || n.getStyleClass().contains("label-section-row") || n.getStyleClass().contains("panel-section");
            if (title || current == null) {
                current = new VBox(8);
                current.getStyleClass().add("panel-card");
                cards.add(current);
            }
            if (title && n instanceof Label tl && tl.getText().equalsIgnoreCase(panelName) && current.getChildren().isEmpty()) {
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
            public void addZone(double x, double y, double w, double h) {
                com.vttcaseiro.model.MapZone z = new com.vttcaseiro.model.MapZone(x, y, w, h, com.vttcaseiro.model.MapZone.TRAP);
                if (!editZoneDialog(z, true)) return;
                table.addZone(z);
                pushUndo(new UndoAction("Zona", () -> table.removeZone(z)));
                scheduleSceneAutosave();
            }

            @Override
            public void editZone(com.vttcaseiro.model.MapZone z) {
                if (editZoneDialog(z, false)) {
                    table.zoneChanged();
                    scheduleSceneAutosave();
                }
            }

            @Override
            public void removeZone(com.vttcaseiro.model.MapZone z) {
                table.removeZone(z);
                pushUndo(new UndoAction("Apagar zona", () -> table.addZone(z)));
                scheduleSceneAutosave();
            }

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
        toolbar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        toolbar.setPickOnBounds(false);
        stack.getChildren().add(toolbar);
        StackPane.setAlignment(toolbar, Pos.TOP_LEFT);
        StackPane.setMargin(toolbar, new Insets(10));
        // aviso discreto do modo do mestre (Preparar / Construir), no alto do mapa
        modeChip.getStyleClass().add("mode-chip");
        modeChip.setMouseTransparent(true);
        stack.getChildren().add(modeChip);
        StackPane.setAlignment(modeChip, Pos.BOTTOM_CENTER);
        StackPane.setMargin(modeChip, new Insets(0, 0, 14, 0));
        refreshModeChip();
        // fila de turnos: cartoes no alto do mapa, so durante o combate
        turnQueue.getStyleClass().add("turn-queue");
        turnQueue.setAlignment(Pos.CENTER);
        turnQueue.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        turnQueue.setPickOnBounds(false);
        stack.getChildren().add(turnQueue);
        // canto de cima a direita: longe da faixa de ferramentas e da caixa de subferramentas
        StackPane.setAlignment(turnQueue, Pos.TOP_RIGHT);
        StackPane.setMargin(turnQueue, new Insets(10, 10, 0, 110));
        refreshTurnQueue();
        diceAnimator = new com.vttcaseiro.view.DiceAnimator();
        stack.getChildren().add(diceAnimator);
        StackPane.setAlignment(diceAnimator, Pos.BOTTOM_CENTER);
        StackPane.setMargin(diceAnimator, new Insets(0, 0, 40, 0));
        diceAnimator.setMaxHeight(110);
        actionBar.getStyleClass().add("action-bar");
        actionBar.setAlignment(Pos.CENTER);
        actionBar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        actionBar.setVisible(false);
        actionBar.setManaged(false);
        stack.getChildren().add(actionBar);
        StackPane.setAlignment(actionBar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(actionBar, new Insets(0, 120, 52, 120));
        mapView.setSelectionListener(this::refreshActionBar);
        reconnectBanner.getStyleClass().add("reconnect-banner");
        reconnectBanner.setMouseTransparent(true);
        reconnectBanner.setVisible(false);
        stack.getChildren().add(reconnectBanner);
        StackPane.setAlignment(reconnectBanner, Pos.CENTER);
        perspectiveBar.getStyleClass().add("perspective-bar");
        perspectiveBar.setAlignment(Pos.CENTER);
        perspectiveBar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        perspectiveBar.setVisible(false);
        perspectiveBar.setManaged(false);
        stack.getChildren().add(perspectiveBar);
        StackPane.setAlignment(perspectiveBar, Pos.TOP_CENTER);
        StackPane.setMargin(perspectiveBar, new Insets(10, 0, 0, 0));
        cameraBanner.getStyleClass().add("camera-banner");
        cameraBanner.setMouseTransparent(true);
        cameraBanner.setVisible(false);
        stack.getChildren().add(cameraBanner);
        StackPane.setAlignment(cameraBanner, Pos.TOP_CENTER);
        StackPane.setMargin(cameraBanner, new Insets(10, 0, 0, 0));
        mapView.setPullHandler(p -> {
            if (!networkService.isGm() || !networkService.isConnected()) return false;
            networkService.sendMapPull(p.getX(), p.getY());
            mapView.centerOn(p.getX(), p.getY(), true);
            logEntries.add(LogEntry.system("Você chamou a atenção de todos pra esse ponto do mapa."));
            return true;
        });
        mapView.setViewListener(() -> {
            scheduleCameraSend();
            syncTvView();
        });
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
        if (networkService.isGm() && sceneService.getScene().isHiddenFromPlayers()) {
            // cena escondida (preparando): faixa pro mestre lembrar e liberar num clique
            Label hiddenNote = new Label("Cena escondida dos jogadores (eles veem só um aviso)",
                    com.vttcaseiro.view.Icons.icon("hidden", 14));
            Button show = new Button("Mostrar aos jogadores");
            show.getStyleClass().add("button-primary");
            show.setOnAction(e -> setSceneHidden(false));
            HBox curtainBar = new HBox(10, hiddenNote, show);
            curtainBar.setAlignment(Pos.CENTER);
            curtainBar.getStyleClass().add("curtain-bar");
            curtainBar.setStyle("-fx-background-color: -c-surface; -fx-border-color: -c-accent; -fx-border-radius: 8;"
                    + " -fx-background-radius: 8; -fx-padding: 6 12 6 12;");
            curtainBar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            curtainBar.setPickOnBounds(false);
            stack.getChildren().add(curtainBar);
            StackPane.setAlignment(curtainBar, Pos.TOP_CENTER);
            StackPane.setMargin(curtainBar, new Insets(10, 0, 0, 0));
        }
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
        ToggleButton rectBtn = new ToggleButton("Retângulo");
        rectBtn.getStyleClass().add("tool-button");
        ToggleButton circleBtn = new ToggleButton("Círculo");
        circleBtn.getStyleClass().add("tool-button");
        ToggleButton arrowBtn = new ToggleButton("Seta");
        arrowBtn.getStyleClass().add("tool-button");
        javafx.scene.control.ColorPicker colorPicker = new javafx.scene.control.ColorPicker(
                javafx.scene.paint.Color.web("#E2483F"));
        colorPicker.getStyleClass().addAll("button", "tool-color");
        colorPicker.setTooltip(new Tooltip("Cor do lápis e do texto"));
        Button clearDrawBtn = new Button("Limpar");
        clearDrawBtn.getStyleClass().add("tool-button");
        tip(penBtn, "Arraste no mapa para desenhar");
        tip(textBtn, "Clique no mapa para escrever um texto");
        tip(eraserBtn, "Clique num desenho para apagar (os seus; o mestre apaga qualquer um)");
        tip(rectBtn, "Arraste de um canto ao outro pra marcar uma área");
        tip(circleBtn, "Arraste do centro até a borda");
        tip(arrowBtn, "Arraste de onde pra onde (mostrar um caminho, uma direção)");
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
                : textBtn.isSelected() ? MapView.TOOL_TEXT : eraserBtn.isSelected() ? MapView.TOOL_ERASER
                : rectBtn.isSelected() ? MapView.TOOL_RECT : circleBtn.isSelected() ? MapView.TOOL_CIRCLE
                : arrowBtn.isSelected() ? MapView.TOOL_ARROW : null,
                drawHandler);
        List<ToggleButton> drawToggles = List.of(penBtn, textBtn, eraserBtn, rectBtn, circleBtn, arrowBtn);
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
        ToggleButton zoneBtn = new ToggleButton("Zona");
        zoneBtn.getStyleClass().add("tool-button");
        CheckBox visionOn = new CheckBox("Visão");
        CheckBox darkOn = new CheckBox("Escuro");
        CheckBox dimOn = new CheckBox("Penumbra");
        CheckBox fogVisionOn = new CheckBox("Neblina pela visão");
        CheckBox previewOn = new CheckBox("Prévia");
        tip(wallBtn, "Arraste para desenhar uma parede (gruda nas pontas e na grade). Bloqueia visão e movimento.");
        tip(doorBtn, "Arraste para desenhar uma porta. Qualquer um clica nela para abrir/fechar.");
        tip(wallEraseBtn, "Clique perto de uma parede/porta para apagar");
        tip(windowBtn, "Arraste para desenhar uma janela: deixa ver, não deixa passar.");
        tip(zoneBtn, "Arraste um retângulo: armadilha, portal pra outra cena, área de dano, terreno difícil ou aviso. "
                + "Com a ferramenta ligada, clique numa zona pra editar (botão direito: apagar).");
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
                    : lightBtn.isSelected() ? MapView.TOOL_LIGHT : wallEraseBtn.isSelected() ? MapView.TOOL_WALL_ERASE
                    : zoneBtn.isSelected() ? MapView.TOOL_ZONE : null);
            mapView.setPinTool(pinBtn.isSelected());
        };
        // Todas as ferramentas de arrastar no mapa vazio sao exclusivas entre si.
        List<ToggleButton> allTools = List.of(measureBtn, templateBtn, fogRevealBtn, fogCoverBtn,
                penBtn, textBtn, eraserBtn, rectBtn, circleBtn, arrowBtn, wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn, zoneBtn, pinBtn);
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
        HBox wallBox = toolGroup(wallLabel, wallBtn, doorBtn, windowBtn, lightBtn, zoneBtn, wallEraseBtn, visionMenu);
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
                for (ToggleButton t : List.of(wallBtn, doorBtn, windowBtn, lightBtn, wallEraseBtn, zoneBtn)) t.setSelected(false);
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
        HBox drawBox = toolGroup(penBtn, rectBtn, circleBtn, arrowBtn, textBtn, eraserBtn, colorPicker, clearDrawBtn);
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
            toolFlyTitle.setText(i >= 0 ? cats[i][0] : "");
            toolFlyHeader.setVisible(i >= 0);
            toolFlyHeader.setManaged(i >= 0);
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
        refreshToolKeyTips = () -> {
            java.util.function.Function<String, String> k = a -> Keybindings.label(Keybindings.keyFor(a));
            tip(catButtons[1], "Régua e áreas de efeito (teclas " + k.apply("tool.1") + " e " + k.apply("tool.2") + ")");
            tip(catButtons[2], "Lápis, texto e borracha, todos podem (teclas " + k.apply("tool.3") + ", " + k.apply("tool.4")
                    + " e " + k.apply("tool.5") + ")");
            tip(catButtons[5], "Neblina de guerra, mestre (teclas " + k.apply("tool.6") + " e " + k.apply("tool.7") + ")");
            tip(catButtons[6], "Paredes, portas e visão, mestre (teclas " + k.apply("tool.8") + " e " + k.apply("tool.9")
                    + "). Esc desliga a ferramenta");
        };
        tip(catButtons[3], "Pins: pontos de informação que podem abrir outro mapa");
        tip(catButtons[4], "Objetos soltos no mapa: móveis, árvores, baús (mestre)");
        refreshToolKeyTips.run();
        // Atalho de teclado (1-9) liga uma ferramenta: abre a categoria dela tambem.
        java.util.Map<ToggleButton, Integer> catOf = new java.util.HashMap<>();
        for (ToggleButton t : List.of(measureBtn, templateBtn)) catOf.put(t, 1);
        for (ToggleButton t : List.of(penBtn, textBtn, eraserBtn, rectBtn, circleBtn, arrowBtn)) catOf.put(t, 2);
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
        VBox catRow = new VBox(4, catButtons);
        catRow.getStyleClass().addAll("tool-group", "tool-column");
        HBox targetsGroup = toolGroup(targetsLabel, clearTargetsBtn);
        // alvos so aparecem quando tem alvo marcado (menos coisa na tela)
        Runnable showTargets = () -> {
            boolean any = targetsLabel.getText() != null && !targetsLabel.getText().endsWith("nenhum");
            targetsGroup.setVisible(any);
            targetsGroup.setManaged(any);
        };
        targetsLabel.textProperty().addListener((o, a, b) -> showTargets.run());
        showTargets.run();
        // faixa vertical na borda do mapa: so icones; o nome (e a tecla) fica na dica
        VBox editGroup = new VBox(4, undoBtn, centerBtn);
        editGroup.getStyleClass().addAll("tool-group", "tool-column");
        java.util.List<javafx.scene.control.Labeled> iconOnly = new java.util.ArrayList<>(List.of(catButtons));
        iconOnly.add(undoBtn);
        iconOnly.add(centerBtn);
        for (javafx.scene.control.Labeled l : iconOnly) {
            if (l.getTooltip() == null) l.setTooltip(new Tooltip(l.getText()));
            else if (!l.getTooltip().getText().startsWith(l.getText())) {
                l.getTooltip().setText(l.getText() + ": " + l.getTooltip().getText());
            }
            l.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        }
        // modos do mestre: o jeito de trabalhar (e nao "camadas"): jogar, preparar, construir
        javafx.scene.control.ToggleGroup modes = new javafx.scene.control.ToggleGroup();
        ToggleButton play = modeButton("dice", "Jogar: mexer tokens, rolar, conduzir a cena", modes);
        ToggleButton prepare = modeButton("objects", "Preparar: o que você colocar (tokens, pins) nasce escondido "
                + "dos jogadores; objetos ficam editáveis", modes);
        ToggleButton build = modeButton("wall", "Construir: paredes, portas, luzes e neblina", modes);
        play.setOnAction(e -> {
            play.setSelected(true);
            gmMode = "jogar";
            objectEditMode = false;
            mapView.setObjectEditing(false);
            toolsOff.run();
            for (ToggleButton c : catButtons) c.setSelected(false);
            showCat.accept(-1);
            refreshModeChip();
        });
        prepare.setOnAction(e -> {
            prepare.setSelected(true);
            gmMode = "preparar";
            objectEditMode = true;
            mapView.setObjectEditing(true);
            refreshModeChip();
        });
        build.setOnAction(e -> {
            build.setSelected(true);
            gmMode = "construir";
            objectEditMode = false;
            mapView.setObjectEditing(false);
            toolsOff.run();
            catButtons[6].setSelected(true);
            showCat.accept(6);
            refreshModeChip();
        });
        (switch (gmMode) {
            case "preparar" -> prepare;
            case "construir" -> build;
            default -> play;
        }).setSelected(true);
        Label modeTitle = new Label("Modo");
        modeTitle.getStyleClass().add("tool-column-title");
        VBox modeGroup = new VBox(4, modeTitle, play, prepare, build);
        modeGroup.setFillWidth(true);
        modeGroup.getStyleClass().addAll("tool-group", "tool-column", "mode-group");
        Runnable modesGmOnly = () -> {
            boolean gm = networkService.isGm();
            modeGroup.setVisible(gm);
            modeGroup.setManaged(gm);
            if (!gm) gmMode = "jogar";
            refreshModeChip();
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(modesGmOnly));
        modesGmOnly.run();
        VBox column = new VBox(8, catRow, editGroup, modeGroup);
        column.setFillWidth(true); // os tres grupos com a mesma largura, icones no meio
        for (VBox g : List.of(catRow, editGroup, modeGroup)) {
            g.setMaxWidth(Double.MAX_VALUE);
            g.setAlignment(Pos.TOP_CENTER);
        }
        for (javafx.scene.Node b : catRow.getChildren()) if (b instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        for (javafx.scene.Node b : editGroup.getChildren()) if (b instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        Button flyClose = new Button("✕");
        flyClose.getStyleClass().addAll("button-flat", "tool-fly-close");
        flyClose.setTooltip(new Tooltip("Fechar (Esc)"));
        flyClose.setOnAction(e -> {
            toolsOff.run();
            for (ToggleButton c : catButtons) c.setSelected(false);
            showCat.accept(-1);
        });
        toolFlyTitle.getStyleClass().add("tool-fly-title");
        Region flyGrow = new Region();
        HBox.setHgrow(flyGrow, Priority.ALWAYS);
        toolFlyHeader.getChildren().setAll(toolFlyTitle, flyGrow, flyClose);
        toolFlyHeader.setAlignment(Pos.CENTER_LEFT);
        toolFlyHeader.setVisible(false);
        toolFlyHeader.setManaged(false);
        VBox fly = new VBox(4, toolFlyHeader, panelHolder);
        fly.getStyleClass().add("tool-fly");
        fly.visibleProperty().bind(panelHolder.visibleProperty());
        fly.managedProperty().bind(panelHolder.managedProperty());
        VBox side = new VBox(6, fly, targetsGroup);
        side.setFillWidth(false);
        side.setPickOnBounds(false);
        // o fundo escuro envolve so a coluna de icones; a caixa de subferramentas tem o dela (nao vira um bloco
        // cobrindo o mapa quando abre)
        column.getStyleClass().add("map-toolbar");
        HBox row = new HBox(8, column, side);
        row.setPickOnBounds(false);
        row.setFillHeight(false);
        row.setAlignment(Pos.TOP_LEFT);
        VBox toolbar = new VBox(6, row);
        toolbar.setFillWidth(false);
        // os "gmOnly" antigos escondiam fogBox/wallBox direto; agora so a categoria aparece/some
        return toolbar;
    }

    /** Modo do mestre: "jogar", "preparar" ou "construir" (fica entre trocas de cena). */
    private String gmMode = "jogar";
    private final Label modeChip = new Label();

    private final Label toolFlyTitle = new Label();
    private final HBox toolFlyHeader = new HBox(6);

    private ToggleButton modeButton(String icon, String tip, javafx.scene.control.ToggleGroup group) {
        String name = tip.substring(0, tip.indexOf(':'));
        ToggleButton b = new ToggleButton(name, com.vttcaseiro.view.Icons.icon(icon, 16));
        b.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().addAll("tool-button", "mode-button");
        b.setToggleGroup(group);
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    /** No modo Preparar, o que o mestre coloca nasce escondido dos jogadores. */
    boolean preparing() {
        return "preparar".equals(gmMode) && networkService.isGm();
    }

    private void refreshModeChip() {
        String text = !networkService.isGm() ? "" : switch (gmMode) {
            case "preparar" -> "Preparando: o que você colocar nasce escondido";
            case "construir" -> "Construindo: paredes, portas, luz e neblina";
            default -> "";
        };
        modeChip.setText(text);
        modeChip.setVisible(!text.isEmpty());
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


    /**
     * Botoes em duas colunas da mesma largura (o painel fica alinhado em vez de uma fileira torta que quebra onde
     * da). Botao escondido (managed=false) nao deixa buraco.
     */
    static javafx.scene.layout.TilePane buttonGrid(Button... buttons) {
        return buttonGrid(2, buttons);
    }

    static javafx.scene.layout.TilePane buttonGrid(int cols, Button... buttons) {
        javafx.scene.layout.TilePane grid = new javafx.scene.layout.TilePane(6, 6);
        grid.setPrefColumns(cols);
        grid.getStyleClass().add("button-grid");
        grid.prefTileWidthProperty().bind(javafx.beans.binding.Bindings.max(60,
                grid.widthProperty().subtract(6 * cols - 2).divide(cols).subtract(0.5)));
        grid.setTileAlignment(Pos.CENTER_LEFT);
        for (Button b : buttons) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinWidth(0);
            b.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            if (b.getTooltip() == null && b.getText() != null) b.setTooltip(new Tooltip(b.getText()));
        }
        grid.getChildren().addAll(buttons);
        return grid;
    }

    /** Botao so com icone pro cabecalho de uma secao (o nome vai na dica). */
    static Button iconButton(String icon, String tip, Runnable action) {
        Button b = new Button(null, com.vttcaseiro.view.Icons.icon(icon, 14));
        b.getStyleClass().add("panel-icon-button");
        b.setTooltip(new Tooltip(tip));
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Deixa um botao que ja existe so com o icone (o texto vira a dica e continua no botao pra quem procura). */
    static <B extends javafx.scene.control.ButtonBase> B iconize(B b) {
        String text = b.getText();
        if (b.getTooltip() == null) b.setTooltip(new Tooltip(text));
        else if (text != null && !b.getTooltip().getText().startsWith(text))
            b.getTooltip().setText(text + ": " + b.getTooltip().getText());
        b.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        b.getStyleClass().remove("button-primary");
        b.getStyleClass().add("panel-icon-button");
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    /** Titulo da secao com icones a direita (abre um cartao novo como um titulo normal). */
    javafx.scene.Node sectionHeader(String title, javafx.scene.Node... right) {
        Label l = sectionLabel(title);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(4, l, grow);
        row.getChildren().addAll(right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("label-section-row");
        return row;
    }

    /** Lista vazia: texto amigavel no meio em vez de uma caixa escura muda. */
    static Label emptyHint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-hint");
        l.setWrapText(true);
        l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return l;
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
        VBox scenesBox = box;

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
        Label treeHint = new Label("Duplo clique abre pra todos. Botão direito: abrir escondida, mostrar rapidinho e voltar, "
                + "renomear, duplicar, mover, apagar.");
        treeHint.getStyleClass().add("label-muted");
        treeHint.setWrapText(true);

        Button newSceneButton = new Button("Nova cena (imagem)...", com.vttcaseiro.view.Icons.icon("add", 14));
        newSceneButton.getStyleClass().add("button-primary");
        newSceneButton.setOnAction(e -> onNewScene());
        Button saveSceneButton = new Button("Salvar cena atual na mesa");
        saveSceneButton.setOnAction(e -> onSaveCurrentScene());
        sceneSaveStatus = new Label("A cena é salva sozinha 2 s depois de cada mudança (mestre/offline).");
        sceneSaveStatus.getStyleClass().add("live-status");
        sceneSaveStatus.getStyleClass().add("label-muted");
        sceneSaveStatus.setWrapText(true);
        Button importMapButton = new Button("Importar mapa (Azgaar, Watabou, Dungeondraft)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importMapButton.setTooltip(new Tooltip("Masmorra do One Page Dungeon (JSON) e Universal VTT (.dd2vtt) viram cena com "
                + "paredes e portas; mundo do Azgaar e cidades/vilas do Watabou entram como imagem (PNG/JPG)."));
        importMapButton.setOnAction(e -> onImportGeneratedMap());
        Button regionButton = new Button("Região de um mundo (Azgaar)...", com.vttcaseiro.view.Icons.icon("map", 14));
        regionButton.setTooltip(new Tooltip("Abre a região em volta de uma cidade de um mundo do Azgaar como cena nova, "
                + "ampliada e com os pins de lá (o mundo vem de Importar mapa > \"Azgaar - mundo salvo (.map)\")."));
        regionButton.setOnAction(e -> onAzgaarRegion());
        Button worldEditButton = new Button("Editar mundo (Azgaar)...", com.vttcaseiro.view.Icons.icon("edit", 14));
        worldEditButton.setTooltip(new Tooltip("Troca nomes e cores dos reinos e nomes/população das cidades de um mundo "
                + "do Azgaar: o mapa é redesenhado e os pins, rótulos e facções acompanham."));
        worldEditButton.setOnAction(e -> onEditWorld());
        Button configButton = new Button("Configurar cena...", com.vttcaseiro.view.Icons.icon("tune", 14));
        configButton.setTooltip(new Tooltip("Grade (quadrada, hexagonal, sem grade), tamanho da casa, pés por casa e tamanho do mapa"));
        configButton.setOnAction(e -> showSceneConfig());
        saveSceneButton.setGraphic(com.vttcaseiro.view.Icons.icon("save", 14));
        // cabecalho enxuto: o titulo e os icones (o nome de cada um fica na dica)
        for (Button b : List.of(newSceneButton, importMapButton, regionButton, worldEditButton, newFolderButton, saveSceneButton, configButton)) {
            if (b.getTooltip() == null) b.setTooltip(new Tooltip(b.getText()));
            else b.getTooltip().setText(b.getText() + ": " + b.getTooltip().getText());
            b.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            b.getStyleClass().add("panel-icon-button");
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        Label scenesTitle = sectionLabel("Cenas desta mesa");
        Region headerGrow = new Region();
        HBox.setHgrow(headerGrow, Priority.ALWAYS);
        HBox scenesHeader = new HBox(4, scenesTitle, headerGrow, newSceneButton, importMapButton, regionButton, worldEditButton, newFolderButton,
                saveSceneButton, configButton);
        scenesHeader.setAlignment(Pos.CENTER_LEFT);
        scenesHeader.getStyleClass().add("label-section-row");
        box.getChildren().addAll(scenesHeader, mapListView, treeHint, sceneSaveStatus);
        box.getChildren().add(buildSceneQueue());

        // grade, pes e tamanho: janela "Configurar cena" (montada uma vez; os campos continuam vivos)
        VBox config = new VBox(8);
        config.setPadding(new Insets(14));
        sceneConfigBox = config;
        box = config;
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

        return new Tab("Mapa", wrapInScroll(scenesBox));
    }

    /**
     * Tamanho do mapa escolhido pelo mestre: largura x altura em casas (a imagem estica pra caber), ou automatico
     * (o tamanho da imagem). Mostra o tamanho em pes e metros.
     */
    private VBox sceneConfigBox;
    private Stage sceneConfigStage;

    /** Janela "Configurar cena": grade, tamanho da casa, pes por casa e tamanho do mapa da cena aberta. */
    void showSceneConfig() {
        if (sceneConfigStage == null) {
            sceneConfigStage = new Stage();
            sceneConfigStage.initOwner(stage);
            sceneConfigStage.setTitle("Configurar cena");
            sceneConfigBox.getStyleClass().add("side-panel"); // fundo do tema (nao branco)
            ScrollPane sp = new ScrollPane(sceneConfigBox);
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            sp.getStyleClass().add("side-panel");
            Scene sc = new Scene(sp, 420, 460);
            sceneConfigStage.setScene(sc);
        }
        if (mapSizeRefresh != null) mapSizeRefresh.run();
        sceneConfigStage.setTitle("Configurar cena: " + (currentMapName != null ? currentMapName : "cena atual"));
        sceneConfigStage.show();
        sceneConfigStage.toFront();
    }

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

        characterListView = new ListView<>();
        characterListView.setPlaceholder(emptyHint("Nenhuma ficha ainda.\nCrie com + ou importe um PDF/JSON."));
        characterListView.setPrefHeight(170);
        refreshCharacterList();
        characterListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String name = characterListView.getSelectionModel().getSelectedItem();
                if (name != null) onOpenCharacterFromMesa(name);
            }
        });
        characterListView.setOnContextMenuRequested(e -> {
            String name = characterListView.getSelectionModel().getSelectedItem();
            if (name == null) return;
            characterMenu(name).show(characterListView, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        characterListView.setOnKeyPressed(e -> {
            String name = characterListView.getSelectionModel().getSelectedItem();
            if (name == null) return;
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) onDeleteCharacter(name);
            else if (e.getCode() == javafx.scene.input.KeyCode.F2) onRenameCharacter(name);
            else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) onOpenCharacterFromMesa(name);
        });
        characterListView.setTooltip(new Tooltip("Clique duplo abre. Botão direito: renomear, duplicar, apagar."));

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
        javafx.scene.control.MenuItem templatesItem = new javafx.scene.control.MenuItem("Modelos de ficha (PDF editável)...");
        templatesItem.setOnAction(e -> showTemplateManager());
        javafx.scene.control.MenuItem toVaultItem = new javafx.scene.control.MenuItem("Guardar ficha aberta no meu cofre");
        toVaultItem.setOnAction(e -> {
            if (lastOpenedCharacter == null) showError("Abra uma ficha primeiro (clique duplo na lista).");
            else saveToVault(lastOpenedCharacter);
        });
        javafx.scene.control.MenuItem fromVaultItem = new javafx.scene.control.MenuItem("Trazer do meu cofre...");
        fromVaultItem.setOnAction(e -> onBringFromVault());
        moreChar.getItems().addAll(exportItem, saveItem, templatesItem, new javafx.scene.control.SeparatorMenuItem(),
                toVaultItem, fromVaultItem);
        moreChar.setGraphic(com.vttcaseiro.view.Icons.icon("more", 14));
        moreChar.setTooltip(new Tooltip("Exportar, salvar agora, modelos de ficha"));
        importCharButton.setText("Importar ficha (PDF ou JSON)...");
        box.getChildren().addAll(sectionHeader("Fichas desta mesa", iconize(newCharButton), iconize(importCharButton),
                iconize(moreChar)), characterListView);

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

        mobCountLabel = new Label(mobService.list().size() + " monstro(s) nesta mesa.");
        mobCountLabel.getStyleClass().add("live-status");
        mobCountLabel.getStyleClass().add("label-muted");

        mobListView = new ListView<>();
        mobListView.setPrefHeight(160);
        mobListView.setPlaceholder(emptyHint("Nenhum monstro ainda.\nCrie com + ou importe no Compêndio."));
        refreshMobList();
        mobListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String name = mobListView.getSelectionModel().getSelectedItem();
                if (name != null) onOpenMobFromMesa(name);
            }
        });
        mobListView.setOnContextMenuRequested(e -> {
            String name = mobListView.getSelectionModel().getSelectedItem();
            if (name == null) return;
            mobMenu(name).show(mobListView, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        mobListView.setOnKeyPressed(e -> {
            String name = mobListView.getSelectionModel().getSelectedItem();
            if (name == null) return;
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) onDeleteMob(name);
            else if (e.getCode() == javafx.scene.input.KeyCode.F2) onRenameMob(name);
            else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) onOpenMobFromMesa(name);
        });
        mobListView.setTooltip(new Tooltip("Clique duplo abre. Botão direito: editar, duplicar, renomear, apagar."));
        Button newMobButton = new Button("Novo monstro / NPC...", com.vttcaseiro.view.Icons.icon("add", 14));
        newMobButton.setTooltip(new Tooltip("Cria um monstro ou NPC do zero (CA, PV, atributos, ataques...)."));
        newMobButton.setOnAction(e -> onNewMob());
        Button mobToMapButton = new Button("Colocar o escolhido no mapa", com.vttcaseiro.view.Icons.icon("target", 14));
        mobToMapButton.setOnAction(e -> onPlaceSelectedMob());
        box.getChildren().addAll(sectionHeader("Monstros e PdMs", iconize(newMobButton), iconize(mobToMapButton)),
                mobCountLabel);
        Label mobHint = new Label("Clique duplo num monstro da lista pra abrir a ficha dele; botão direito pra "
                + "editar, duplicar (ex.: \"Goblin chefe\"), renomear ou apagar. O token do monstro "
                + "nasce com PV médio e bônus de iniciativa; o PV fica escondido dos jogadores.");
        mobHint.getStyleClass().add("label-muted");
        mobHint.setWrapText(true);
        box.getChildren().addAll(mobListView, mobHint, divider());

        compendium.getChildren().add(sectionLabel("Buscar e anexar na ficha aberta"));
        TextField compendiumSearchField = new TextField();
        compendiumSearchField.setPromptText("Buscar por nome (ex.: \"Fireball\", \"Aasimar\")...");
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

        Label compendiumSummary = new Label();
        compendiumSummary.getStyleClass().addAll("label-muted", "live-status");
        compendiumSummary.textProperty().bind(compendiumCountLabel.textProperty());
        Button openCompendium = new Button("Abrir compêndio...", com.vttcaseiro.view.Icons.icon("journal", 14));
        openCompendium.setTooltip(new Tooltip("Conteúdo pronto 2024, importar do 5etools e buscar algo pra anexar na ficha."));
        openCompendium.setOnAction(e -> showCompendiumWindow(compendium));
        box.getChildren().addAll(sectionHeader("Compêndio", iconize(openCompendium)), compendiumSummary, divider());
        Button shopsButton = new Button("Lojas (comprar e vender)", com.vttcaseiro.view.Icons.icon("loja", 14));
        shopsButton.setMaxWidth(Double.MAX_VALUE);
        shopsButton.setTooltip(new Tooltip("Mestre: cria lojas e itens com preço e estoque e abre pros jogadores. Jogador: "
                + "compra e vende com a sua ficha (o dinheiro e o item mudam sozinhos)."));
        shopsButton.setOnAction(e -> openShops());
        box.getChildren().addAll(sectionLabel("Lojas"), shopsButton);

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
    // ---------------- modelos de ficha (PDF editavel)

    private com.vttcaseiro.library.TemplateStore templateStore;
    private static final String MOB_EDITOR = "D&D 5e (editor de monstro)";
    private static final String IMPORT_TEMPLATE = "Importar modelo de PDF editável...";
    private final java.util.Map<String, com.vttcaseiro.view.TemplateSheetView> templateViews = new java.util.HashMap<>();

    /**
     * Escolher o modelo de uma ficha nova: as opcoes prontas ({@code builtIns}), os modelos de PDF da mesa daquele
     * tipo e "Importar modelo de PDF...". Devolve a opcao escolhida (um SheetTemplate se for de PDF) ou null.
     */
    private Object chooseModel(String kind, List<Object> builtIns, String title) {
        List<Object> options = new ArrayList<>(builtIns);
        options.addAll(templateStore.list(kind));
        options.add(IMPORT_TEMPLATE);
        String lastKey = "ultimoModelo." + kind;
        String last = AppSettings.getString(lastKey, "");
        Object initial = options.stream().filter(o -> o.toString().equals(last)).findFirst().orElse(options.get(0));
        javafx.scene.control.ChoiceDialog<Object> d = new javafx.scene.control.ChoiceDialog<>(initial, options);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText("Qual modelo de ficha?\nModelo de PDF: a ficha fica igual ao PDF (a ficha oficial do sistema, "
                + "por exemplo), com os campos editáveis.");
        d.setContentText("Modelo:");
        Optional<Object> choice = d.showAndWait();
        if (choice.isEmpty()) return null;
        if (IMPORT_TEMPLATE.equals(choice.get())) {
            return importTemplate(kind).orElse(null);
        }
        AppSettings.setString(lastKey, choice.get().toString());
        return choice.get();
    }

    /** Escolhe um PDF editavel e transforma em modelo da mesa. */
    private Optional<com.vttcaseiro.character.SheetTemplate> importTemplate(String kind) {
        FileChooser fc = new FileChooser();
        fc.setTitle("PDF editável da ficha");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return Optional.empty();
        try {
            if (file.length() > 60L * 1024 * 1024) throw new IOException("PDF grande demais (mais de 60 MB).");
            byte[] pdf = java.nio.file.Files.readAllBytes(file.toPath());
            String suggested = file.getName().replaceFirst("(?i)\\.pdf$", "");
            Optional<String> name = askNewName("Nome do modelo", suggested);
            if (name.isEmpty()) return Optional.empty();
            var t = com.vttcaseiro.importer.PdfTemplateReader.read(pdf, name.get(), kind, mesaRepository.getImageAssetStore());
            if (!editTemplateRoles(t)) return Optional.empty();
            templateStore.save(t, pdf);
            logEntries.add(LogEntry.system("Modelo de ficha importado: " + t.getName() + " (" + t.getFields().size()
                    + " campos, " + t.getPages().size() + " página(s))."));
            return Optional.of(t);
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra usar esse PDF como modelo: " + ex.getMessage());
            return Optional.empty();
        }
    }

    /** Quais campos do PDF sao o nome, os PV, a CA... (o programa adivinha; o mestre confere). */
    private boolean editTemplateRoles(com.vttcaseiro.character.SheetTemplate t) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Campos do modelo " + t.getName());
        d.setHeaderText("Quais campos do PDF o programa usa no mapa (token, rastreador)? Já tentei adivinhar; confira.");
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        List<String> names = new ArrayList<>();
        names.add("(nenhum)");
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (var f : t.getFields()) if ("texto".equals(f.type)) unique.add(f.name);
        names.addAll(unique);
        String[][] roles = {{com.vttcaseiro.character.SheetTemplate.ROLE_NAME, "Nome"},
                {com.vttcaseiro.character.SheetTemplate.ROLE_HP, "PV atuais"},
                {com.vttcaseiro.character.SheetTemplate.ROLE_HP_MAX, "PV máximos"},
                {com.vttcaseiro.character.SheetTemplate.ROLE_AC, "CA / Defesa"},
                {com.vttcaseiro.character.SheetTemplate.ROLE_INIT, "Iniciativa"},
                {com.vttcaseiro.character.SheetTemplate.ROLE_SPEED, "Deslocamento"}};
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(6);
        java.util.Map<String, ComboBox<String>> boxes = new java.util.LinkedHashMap<>();
        int r = 0;
        for (String[] role : roles) {
            ComboBox<String> c = new ComboBox<>(FXCollections.observableArrayList(names));
            String cur = t.getRoles().get(role[0]);
            c.setValue(cur == null ? "(nenhum)" : cur);
            c.setPrefWidth(320);
            boxes.put(role[0], c);
            g.addRow(r++, new Label(role[1]), c);
        }
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("Ficha de personagem", "Ficha de monstro / PdM"));
        kind.setValue(com.vttcaseiro.character.SheetTemplate.MOB.equals(t.getKind()) ? "Ficha de monstro / PdM" : "Ficha de personagem");
        g.addRow(r, new Label("Tipo"), kind);
        d.getDialogPane().setContent(g);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return false;
        t.getRoles().clear();
        boxes.forEach((role, c) -> {
            if (c.getValue() != null && !c.getValue().equals("(nenhum)")) t.getRoles().put(role, c.getValue());
        });
        t.setKind(kind.getValue().startsWith("Ficha de monstro") ? com.vttcaseiro.character.SheetTemplate.MOB
                : com.vttcaseiro.character.SheetTemplate.CHARACTER);
        return true;
    }

    private void showTemplateManager() {
        Stage w = new Stage();
        w.initOwner(stage);
        w.setTitle("Modelos de ficha (PDF editável)");
        ListView<com.vttcaseiro.character.SheetTemplate> list = new ListView<>();
        Runnable reload = () -> list.getItems().setAll(templateStore.list());
        reload.run();
        list.setPlaceholder(emptyHint("Nenhum modelo. Importe o PDF editável da ficha do seu sistema."));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.character.SheetTemplate t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.getName() + "  •  "
                        + (com.vttcaseiro.character.SheetTemplate.MOB.equals(t.getKind()) ? "monstro/PdM" : "personagem")
                        + "  •  " + t.getFields().size() + " campos" + (templateStore.pdf(t.getId()) == null ? "  •  sem o PDF" : ""));
            }
        });
        Button importChar = new Button("Importar PDF (personagem)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importChar.setOnAction(e -> { importTemplate(com.vttcaseiro.character.SheetTemplate.CHARACTER); reload.run(); });
        Button importMob = new Button("Importar PDF (monstro/PdM)...", com.vttcaseiro.view.Icons.icon("upload", 14));
        importMob.setOnAction(e -> { importTemplate(com.vttcaseiro.character.SheetTemplate.MOB); reload.run(); });
        Button roles = new Button("Campos do mapa...");
        roles.setOnAction(e -> {
            var t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            if (editTemplateRoles(t)) {
                try {
                    templateStore.save(t, null);
                } catch (IOException ex) {
                    showError("Não deu pra salvar: " + ex.getMessage());
                }
                reload.run();
            }
        });
        Button rename = new Button("Renomear...");
        rename.setOnAction(e -> {
            var t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            askNewName("Renomear modelo", t.getName()).ifPresent(n -> {
                t.setName(n);
                try {
                    templateStore.save(t, null);
                } catch (IOException ex) {
                    showError("Não deu pra salvar: " + ex.getMessage());
                }
                reload.run();
            });
        });
        Button del = new Button("Apagar", com.vttcaseiro.view.Icons.icon("trash", 14));
        del.setOnAction(e -> {
            var t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            if (!confirmDelete("Apagar o modelo \"" + t.getName() + "\"?\n\nAs fichas já feitas com ele continuam funcionando "
                    + "(o modelo vai junto dentro de cada ficha); só não dá mais pra criar fichas novas nem exportar o PDF.")) return;
            try {
                templateStore.delete(t.getId());
            } catch (IOException ex) {
                showError("Não deu pra apagar: " + ex.getMessage());
            }
            reload.run();
        });
        Label hint = new Label("Use o PDF \"preenchível\" da ficha (o que dá pra digitar dentro no leitor de PDF). Cada página "
                + "vira a imagem de fundo da ficha e cada campo vira um espaço de digitar no mesmo lugar. Os jogadores "
                + "veem a ficha igual, mesmo sem ter o PDF.");
        hint.setWrapText(true);
        hint.getStyleClass().add("label-muted");
        javafx.scene.layout.FlowPane buttons = new javafx.scene.layout.FlowPane(6, 6, importChar, importMob, roles, rename, del);
        VBox box = new VBox(8, list, buttons, hint);
        VBox.setVgrow(list, Priority.ALWAYS);
        box.setPadding(new Insets(12));
        Scene sc = new Scene(box, 620, 420);
        w.setScene(sc);
        w.show();
    }

    private void createTemplateCharacter(String name, com.vttcaseiro.character.SheetTemplate t) {
        try {
            CharacterSheet sheet = characterService.create(name);
            sheet.setTemplate(t);
            sheet.setTemplateValues(t.defaults());
            String nameField = t.getRoles().get(com.vttcaseiro.character.SheetTemplate.ROLE_NAME);
            if (nameField != null) sheet.getTemplateValues().put(nameField, name);
            sheet.setPlayerName(networkService.getPlayerName());
            sheet.setOwnerPlayerId(networkService.getPlayerId());
            com.vttcaseiro.service.TemplateSync.toCharacter(sheet);
            characterService.save(sheet);
            refreshCharacterList();
            openSheetView(sheet);
            logEntries.add(LogEntry.system("Ficha criada com o modelo " + t.getName() + ": " + sheet.getName()));
        } catch (IllegalArgumentException | IOException ex) {
            showError("Não foi possível criar a ficha: " + ex.getMessage());
        }
    }

    private void createTemplateMob(com.vttcaseiro.character.SheetTemplate t) {
        Optional<String> name = askNewName("Novo monstro / PdM (" + t.getName() + ")", "Novo monstro");
        if (name.isEmpty()) return;
        MobSheet mob = new MobSheet(name.get());
        mob.setTemplate(t);
        mob.setTemplateValues(t.defaults());
        String nameField = t.getRoles().get(com.vttcaseiro.character.SheetTemplate.ROLE_NAME);
        if (nameField != null) mob.getTemplateValues().put(nameField, name.get());
        com.vttcaseiro.service.TemplateSync.toMob(mob);
        if (mobService.list().contains(com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName()))) {
            mob.setName(mobService.freeCopyName(mob.getName()));
        }
        try {
            mobService.save(mob);
            refreshMobList();
            refreshMobCount();
            openMobView(mob, null);
            logEntries.add(LogEntry.system("Monstro criado com o modelo " + t.getName() + ": " + mob.getName()));
        } catch (IOException ex) {
            showError("Não deu pra salvar: " + ex.getMessage());
        }
    }

    /** "Exportar PDF preenchido" (so se esta maquina tem o PDF do modelo). */
    private Runnable exportFilledPdf(com.vttcaseiro.character.SheetTemplate t, java.util.Map<String, String> values, String fileName) {
        byte[] pdf = templateStore.pdf(t.getId());
        if (pdf == null) return null;
        return () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Salvar PDF preenchido");
            fc.setInitialFileName(com.vttcaseiro.library.MesaBackup.safeName(fileName) + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            File out = fc.showSaveDialog(stage);
            if (out == null) return;
            try {
                List<String> skipped = new ArrayList<>();
                java.nio.file.Files.write(out.toPath(), com.vttcaseiro.importer.PdfTemplateReader.fill(pdf, values, skipped));
                logEntries.add(LogEntry.system("PDF salvo: " + out.getName()
                        + (skipped.isEmpty() ? "" : " (" + skipped.size() + " campo(s) ficaram em branco: o PDF não aceita essas letras)")));
            } catch (IOException | RuntimeException ex) {
                showError("Não deu pra salvar o PDF: " + ex.getMessage());
            }
        };
    }

    private void openTemplateCharacter(CharacterSheet sheet) {
        var existing = templateViews.get(sheet.getId());
        if (existing != null && existing.getStage().isShowing()) {
            com.vttcaseiro.service.TemplateSync.fromCharacter(sheet);
            existing.reload(sheet.getTemplate(), sheet.getTemplateValues());
            existing.show();
            return;
        }
        com.vttcaseiro.service.TemplateSync.fromCharacter(sheet);
        boolean canEdit = !networkService.isConnected() || networkService.isGm()
                || java.util.Objects.equals(sheet.getOwnerPlayerId(), networkService.getPlayerId());
        javafx.animation.PauseTransition save = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
        save.setOnFinished(e -> {
            com.vttcaseiro.service.TemplateSync.toCharacter(sheet);
            try {
                characterService.save(sheet);
                networkService.sendCharacterSync(sheet);
                refreshCharacterList();
                if (table != null) table.pushSheetToTokens(sheet);
            } catch (IOException ex) {
                showError("Não deu pra salvar a ficha: " + ex.getMessage());
            }
        });
        var view = new com.vttcaseiro.view.TemplateSheetView(stage, sheet.getName() + " — " + sheet.getTemplate().getName(),
                sheet.getTemplate(), sheet.getTemplateValues(), imageAssetService, canEdit,
                new com.vttcaseiro.view.TemplateSheetView.Actions() {
                    @Override public void changed() { save.playFromStart(); }
                    @Override public void roll(String label, int modifier) {
                        rollLabeled(sheet.getName() + " - " + label, "1d20" + (modifier >= 0 ? "+" : "") + modifier, false);
                    }
                    @Override public Runnable exportPdf() {
                        return exportFilledPdf(sheet.getTemplate(), sheet.getTemplateValues(), sheet.getName());
                    }
                });
        view.setOnClosed(() -> {
            if (save.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                save.stop();
                save.getOnFinished().handle(null);
            }
            templateViews.remove(sheet.getId());
        });
        templateViews.put(sheet.getId(), view);
        view.show();
    }

    private void openTemplateMob(MobSheet mob, TokenModel token) {
        String key = "mob:" + mob.getId();
        var existing = templateViews.get(key);
        if (existing != null && existing.getStage().isShowing()) {
            existing.show();
            return;
        }
        boolean canEdit = !networkService.isConnected() || networkService.isGm();
        javafx.animation.PauseTransition save = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
        save.setOnFinished(e -> {
            String before = com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName());
            com.vttcaseiro.service.TemplateSync.toMob(mob);
            try {
                mobService.save(mob);
                String after = com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName());
                if (!after.equals(before)) mesaService.getRepository().deleteMob(before);
                networkService.sendMobSync(mob);
                refreshMobList();
            } catch (IOException ex) {
                showError("Não deu pra salvar o monstro: " + ex.getMessage());
            }
        });
        String who = token != null && token.getName() != null ? token.getName() : mob.getName();
        var view = new com.vttcaseiro.view.TemplateSheetView(stage, who + " — " + mob.getTemplate().getName(),
                mob.getTemplate(), mob.getTemplateValues(), imageAssetService, canEdit,
                new com.vttcaseiro.view.TemplateSheetView.Actions() {
                    @Override public void changed() { save.playFromStart(); }
                    @Override public void roll(String label, int modifier) {
                        rollLabeled(who + " - " + label, "1d20" + (modifier >= 0 ? "+" : "") + modifier, true);
                    }
                    @Override public Runnable exportPdf() {
                        return exportFilledPdf(mob.getTemplate(), mob.getTemplateValues(), who);
                    }
                });
        view.setOnClosed(() -> {
            if (save.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                save.stop();
                save.getOnFinished().handle(null);
            }
            templateViews.remove(key);
        });
        templateViews.put(key, view);
        view.show();
    }

    // ---------------- modulos

    private void onExportModule() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Exportar módulo");
        d.setHeaderText("Marque o que vai no módulo.");
        javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("Exportar...",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, javafx.scene.control.ButtonType.CANCEL);
        TextField name = new TextField(mesaService.getName());
        name.setPromptText("nome do módulo");
        List<javafx.scene.control.CheckBox> scenes = checks(mesaService.listMaps());
        List<javafx.scene.control.CheckBox> mobs = checks(mobService.list());
        List<javafx.scene.control.CheckBox> chars = checks(characterService.list());
        javafx.scene.control.CheckBox handouts = new javafx.scene.control.CheckBox("Diário (todos os textos e imagens)");
        javafx.scene.control.CheckBox camp = new javafx.scene.control.CheckBox(
                "Campanha: PdMs, aventuras, tesouro e encontros prontos (sem jogadores, sessões e anotações)");
        VBox box = new VBox(8, new Label("Nome"), name,
                group("Cenas", scenes), group("Monstros", mobs), group("Fichas (PdMs)", chars), handouts, camp);
        box.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setPrefSize(520, 520);
        d.getDialogPane().setContent(sp);
        if (d.showAndWait().filter(b -> b == ok).isEmpty()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Salvar módulo");
        fc.setInitialFileName(com.vttcaseiro.library.MesaBackup.safeName(name.getText().isBlank() ? "modulo" : name.getText())
                + com.vttcaseiro.library.ModuleService.EXTENSION);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Módulo do Customizr",
                "*" + com.vttcaseiro.library.ModuleService.EXTENSION));
        File file = fc.showSaveDialog(stage);
        if (file == null) return;
        try {
            var sel = new com.vttcaseiro.library.ModuleService.Selection(selected(scenes), selected(mobs), selected(chars),
                    handouts.isSelected(), camp.isSelected());
            var r = com.vttcaseiro.library.ModuleService.export(mesaRepository, handoutStore, campaign, sel,
                    name.getText().trim(), file.toPath());
            logEntries.add(LogEntry.system("Módulo exportado (" + file.getName() + "): " + r.text() + "."));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra exportar o módulo: " + ex.getMessage());
        }
    }

    private void onImportModule() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Importar módulo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Módulo do Customizr",
                "*" + com.vttcaseiro.library.ModuleService.EXTENSION, "*.zip"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        try {
            var r = com.vttcaseiro.library.ModuleService.importInto(mesaRepository, handoutStore, campaign, file.toPath());
            pushCampaign();
            refreshMapList();
            refreshMobList();
            refreshMobCount();
            refreshCharacterList();
            refreshJournal();
            if (campaignView != null && campaignView.isShowing()) campaignView.reload(campaign);
            logEntries.add(LogEntry.system("Módulo importado: " + r.text() + "."));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra importar o módulo: " + ex.getMessage());
        }
    }

    private static List<javafx.scene.control.CheckBox> checks(List<String> names) {
        List<javafx.scene.control.CheckBox> out = new ArrayList<>();
        for (String n : names) out.add(new javafx.scene.control.CheckBox(n));
        return out;
    }

    private static List<String> selected(List<javafx.scene.control.CheckBox> boxes) {
        return boxes.stream().filter(javafx.scene.control.CheckBox::isSelected).map(javafx.scene.control.CheckBox::getText).toList();
    }

    /** Titulo + "marcar tudo" + as caixas. */
    private static javafx.scene.Node group(String title, List<javafx.scene.control.CheckBox> boxes) {
        javafx.scene.control.CheckBox all = new javafx.scene.control.CheckBox(title + " (" + boxes.size() + ") — marcar tudo");
        all.setStyle("-fx-font-weight: bold;");
        all.setDisable(boxes.isEmpty());
        all.selectedProperty().addListener((o, a, b) -> boxes.forEach(c -> c.setSelected(b)));
        VBox items = new VBox(3);
        items.setPadding(new Insets(0, 0, 0, 22));
        items.getChildren().addAll(boxes);
        return new VBox(4, all, items);
    }

    // ---------------- auras

    private boolean aurasPending;

    /** Recalcula as auras uma vez depois da leva de movimentos (nao a cada evento). */
    private void scheduleAuras() {
        if (aurasPending || table == null) return;
        aurasPending = true;
        Platform.runLater(() -> {
            aurasPending = false;
            table.recomputeAuras();
        });
    }

    private void showAurasDialog(TokenModel token) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Auras de " + token.getName());
        d.setHeaderText("Efeito que vale pra quem estiver perto. Quem entra no raio ganha, quem sai perde.");
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        ListView<com.vttcaseiro.model.Aura> list = new ListView<>();
        for (com.vttcaseiro.model.Aura a : token.getAuras()) {
            list.getItems().add(new com.vttcaseiro.model.Aura(a.getEffect(), a.getRadiusFeet(), a.getTarget(), a.isIncludeSelf()));
        }
        list.setPrefHeight(120);
        list.setPlaceholder(emptyHint("Nenhuma aura."));
        TextField effect = new TextField();
        effect.setPromptText("ex.: Aura de Proteção [salv+3]  ou  Bênção");
        javafx.scene.control.Spinner<Integer> radius = new javafx.scene.control.Spinner<>(0, com.vttcaseiro.model.Aura.MAX_RADIUS_FEET, 10, 5);
        radius.setEditable(true);
        radius.setPrefWidth(90);
        ComboBox<String> target = new ComboBox<>();
        target.getItems().addAll("aliados", "inimigos", "todos");
        target.setValue("aliados");
        javafx.scene.control.CheckBox self = new javafx.scene.control.CheckBox("vale pra ele também");
        self.setSelected(true);
        Button add = new Button("Adicionar", com.vttcaseiro.view.Icons.icon("add", 14));
        add.setOnAction(e -> {
            if (effect.getText().isBlank()) return;
            list.getItems().add(new com.vttcaseiro.model.Aura(effect.getText(), radius.getValue(), target.getValue(), self.isSelected()));
            effect.clear();
        });
        Button remove = new Button("Tirar a selecionada");
        remove.setOnAction(e -> list.getItems().remove(list.getSelectionModel().getSelectedItem()));
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Efeito"), effect);
        g.addRow(1, new Label("Raio (pés)"), radius);
        g.addRow(2, new Label("Vale pra"), new HBox(8, target, self));
        javafx.scene.layout.GridPane.setHgrow(effect, Priority.ALWAYS);
        Label how = new Label("Aliados = mesmo lado (jogadores com jogadores, monstros com monstros). O efeito usa o "
                + "mesmo formato dos efeitos: [ca+1], [salv+3], [ataque+1d4], [vant:salv]...");
        how.setWrapText(true);
        how.getStyleClass().add("label-muted");
        VBox box = new VBox(8, list, remove, g, add, how);
        box.setPrefWidth(480);
        d.getDialogPane().setContent(box);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        table.setAuras(token, new ArrayList<>(list.getItems()));
        logEntries.add(LogEntry.system(list.getItems().isEmpty() ? "Auras de " + token.getName() + " desligadas."
                : token.getName() + " agora tem " + list.getItems().size() + " aura(s)."));
    }

    // ---------------- gerenciador de campanha

    private com.vttcaseiro.model.Campaign campaign = new com.vttcaseiro.model.Campaign();
    private com.vttcaseiro.view.CampaignView campaignView;
    private final javafx.animation.PauseTransition campaignSave = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
    private boolean campaignWired;

    /** Mestre (ou offline): edita. Jogador conectado: ve a parte publica que o mestre mandou. */
    private boolean editsCampaign() {
        return !networkService.isConnected() || networkService.isGm();
    }

    private void openCampaign() {
        wireCampaign();
        if (campaignView != null && campaignView.isShowing()) {
            campaignView.show();
            return;
        }
        campaignView = new com.vttcaseiro.view.CampaignView(stage, campaign, editsCampaign(), c -> campaignSave.playFromStart())
                .withOnlinePlayers(() -> new ArrayList<>(networkService.getOnlinePlayers()));
        if (editsCampaign()) campaignView.withGmActions(new CampaignActions());
        campaignView.show();
    }

    /** Uma vez: gravar/mandar depois de parar de digitar, e mandar a campanha quando o mestre entra na rede. */
    private void wireCampaign() {
        if (campaignWired) return;
        campaignWired = true;
        campaignSave.setOnFinished(e -> pushCampaign());
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            if (networkService.isConnected() && networkService.isGm()) {
                networkService.sendCampaign(campaign);
                if (shopStore != null) networkService.sendShops(workingShops());
            }
        }));
    }

    /** Parcelas, itens nao identificados e encontros prontos: o que a Campanha pede pro resto do programa. */
    private class CampaignActions implements com.vttcaseiro.view.CampaignView.GmActions {
        private static final String GROUP = "Inventário do grupo";

        /** A ficha (a aberta na janela, se estiver) pelo nome da lista. */
        private CharacterSheet sheetNamed(String name) throws IOException {
            CharacterSheetView v = openViewFor(name);
            return v != null ? v.getSheet() : characterService.open(name);
        }

        private void saveSheet(CharacterSheet sheet) {
            try {
                characterService.save(sheet);
                networkService.sendCharacterSync(sheet);
                CharacterSheetView v = openSheetViews.get(sheet.getId());
                if (v != null) v.reload(sheet);
            } catch (IOException ex) {
                showError("Não deu pra salvar a ficha " + sheet.getName() + ": " + ex.getMessage());
            }
        }

        private void saveParty() {
            try {
                partyService.save();
                networkService.sendPartySync(partyService.getParty());
                refreshPartyInventoryList();
            } catch (IOException ex) {
                showError("Não deu pra salvar o grupo: " + ex.getMessage());
            }
        }

        @Override
        public void deliver(com.vttcaseiro.model.Campaign.Parcel parcel) {
            List<String> names = characterService.list();
            javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
            d.initOwner(campaignView.getStage());
            d.setTitle("Entregar tesouro");
            d.setHeaderText(parcel.name.isBlank() ? null : parcel.name);
            javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("Entregar",
                    javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            d.getDialogPane().getButtonTypes().addAll(ok, javafx.scene.control.ButtonType.CANCEL);
            ComboBox<String> itemsTo = new ComboBox<>();
            itemsTo.getItems().add(GROUP);
            itemsTo.getItems().addAll(names);
            itemsTo.setValue(GROUP);
            VBox coinBoxes = new VBox(4);
            List<javafx.scene.control.CheckBox> coinChecks = new ArrayList<>();
            for (String n : names) {
                javafx.scene.control.CheckBox c = new javafx.scene.control.CheckBox(n);
                coinChecks.add(c);
                coinBoxes.getChildren().add(c);
            }
            Label coinHint = new Label("Nenhum marcado: as moedas vão junto com os itens.");
            coinHint.getStyleClass().add("label-muted");
            long players = campaign.getPlayers().stream().filter(p -> p.visible).count();
            javafx.scene.control.CheckBox xp = new javafx.scene.control.CheckBox(parcel.xp <= 0 ? "Sem XP nesta parcela"
                    : "Dividir " + parcel.xp + " XP entre os " + players + " jogadores da Campanha");
            xp.setSelected(parcel.xp > 0 && players > 0);
            xp.setDisable(parcel.xp <= 0 || players == 0);
            VBox box = new VBox(8, new Label("Itens vão para"), itemsTo, new Label("Dividir as moedas entre"),
                    coinBoxes, coinHint, xp);
            box.setPrefWidth(420);
            d.getDialogPane().setContent(new ScrollPane(box));
            if (d.showAndWait().filter(b -> b == ok).isEmpty()) return;
            try {
                java.util.Map<String, CharacterSheet> touched = new java.util.LinkedHashMap<>();
                java.util.function.Function<String, com.vttcaseiro.service.TreasureService.Target> target = n -> {
                    if (GROUP.equals(n)) return new com.vttcaseiro.service.TreasureService.Target("o grupo",
                            partyService.getParty().getSharedInventory());
                    try {
                        CharacterSheet sh = touched.computeIfAbsent(n, k -> {
                            try {
                                return sheetNamed(k);
                            } catch (IOException ex) {
                                throw new java.io.UncheckedIOException(ex);
                            }
                        });
                        return new com.vttcaseiro.service.TreasureService.Target(sh.getName(), sh.getInventory());
                    } catch (java.io.UncheckedIOException ex) {
                        throw ex;
                    }
                };
                var to = target.apply(itemsTo.getValue());
                List<com.vttcaseiro.service.TreasureService.Target> coins = new ArrayList<>();
                for (javafx.scene.control.CheckBox c : coinChecks) if (c.isSelected()) coins.add(target.apply(c.getText()));
                String summary = com.vttcaseiro.service.TreasureService.deliver(campaign, parcel, to, coins,
                        xp.isSelected(), com.vttcaseiro.view.CampaignView.todayText());
                touched.values().forEach(this::saveSheet);
                if (GROUP.equals(itemsTo.getValue()) || coins.stream().anyMatch(t -> t.label().equals("o grupo"))) saveParty();
                pushCampaign();
                campaignView.reload(campaign);
                announce(summary);
            } catch (RuntimeException ex) {
                showError("Não deu pra entregar: " + ex.getMessage());
            }
        }

        @Override
        public void identify(com.vttcaseiro.model.Campaign.Secret secret) {
            int changed = 0;
            List<com.vttcaseiro.inventory.Inventory> group = List.of(partyService.getParty().getSharedInventory());
            if (com.vttcaseiro.service.TreasureService.holds(group.get(0), secret.id)) {
                changed += com.vttcaseiro.service.TreasureService.identify(campaign, secret.id, group);
                saveParty();
            }
            for (String n : characterService.list()) {
                try {
                    CharacterSheet sh = sheetNamed(n);
                    if (!com.vttcaseiro.service.TreasureService.holds(sh.getInventory(), secret.id)) continue;
                    changed += com.vttcaseiro.service.TreasureService.identify(campaign, secret.id, List.of(sh.getInventory()));
                    saveSheet(sh);
                } catch (IOException | RuntimeException ex) {
                    // ficha ilegivel: pula
                }
            }
            secret.identified = true;
            pushCampaign();
            announce("Item identificado: \"" + secret.publicName + "\" é " + secret.trueName + "."
                    + (changed == 0 ? " (ninguém está com ele agora)" : ""));
        }

        @Override
        public void timeAdvanced(long from, long to) {
            var cal = campaign.getCalendar();
            var due = cal.due(from, to);
            if (!due.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var ev : due) {
                    ev.done = true;
                    String line = cal.dateTextOf(ev.day) + " " + String.format("%02d:%02d", ev.minute / 60, ev.minute % 60)
                            + " — " + ev.title;
                    sb.append(line).append(ev.notes.isBlank() ? "" : "\n   " + ev.notes).append('\n');
                    if (ev.visible) announce("Calendário: " + line);
                    else logEntries.add(LogEntry.system("Evento (só você vê): " + line));
                }
                pushCampaign();
                Alert a = new Alert(Alert.AlertType.INFORMATION, sb.toString().trim());
                a.initOwner(campaignView != null && campaignView.isShowing() ? campaignView.getStage() : stage);
                a.setTitle("Eventos do calendário");
                a.setHeaderText(due.size() == 1 ? "Chegou um evento marcado" : "Chegaram " + due.size() + " eventos marcados");
                a.show();
            }
            if (cal.lightFollowsClock && table != null) {
                SceneModel sc = sceneService.getScene();
                String period = cal.lightPeriod();
                boolean dark = period.equals("noite");
                boolean dim = period.equals("penumbra");
                if (sc.isDark() != dark || sc.isDim() != dim) {
                    table.setVisionSettings(sc.isVisionEnabled(), dark, dim, sc.isFogByVision());
                    logEntries.add(LogEntry.system("Luz da cena: " + (dark ? "noite (escuro)" : dim ? "penumbra" : "dia (claro)")
                            + ", " + cal.timeText() + "."));
                }
            }
        }

        @Override
        public com.vttcaseiro.model.Campaign.PreparedEncounter captureSelection() {
            List<TokenModel> sel = mapView.getSelectedTokens();
            if (sel.isEmpty()) {
                showError("Selecione os tokens do encontro no mapa primeiro (Shift+clique ou arraste uma caixa).");
                return null;
            }
            double minX = sel.stream().mapToDouble(TokenModel::getX).min().orElse(0);
            double minY = sel.stream().mapToDouble(TokenModel::getY).min().orElse(0);
            var enc = new com.vttcaseiro.model.Campaign.PreparedEncounter();
            enc.name = sel.size() == 1 ? sel.get(0).getName() : sel.get(0).getName() + " e outros";
            com.google.gson.Gson gson = new com.google.gson.Gson();
            for (TokenModel t : sel) {
                TokenModel copy = gson.fromJson(gson.toJson(t), TokenModel.class);
                copy.setX(t.getX() - minX); // posicao relativa ao canto do grupo
                copy.setY(t.getY() - minY);
                copy.setInitiative(null);
                enc.tokens.add(copy);
            }
            logEntries.add(LogEntry.system("Encontro pronto salvo: " + sel.size() + " token(s)."));
            return enc;
        }

        @Override
        public void deploy(com.vttcaseiro.model.Campaign.PreparedEncounter enc, boolean hidden, boolean rollInitiative) {
            javafx.geometry.Point2D anchor = mapView.suggestTokenPosition(0);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            int n = 0;
            for (TokenModel saved : enc.tokens) {
                TokenModel t = gson.fromJson(gson.toJson(saved), TokenModel.class);
                t.assignNewId();
                t.setX(anchor.getX() + saved.getX());
                t.setY(anchor.getY() + saved.getY());
                t.setHiddenFromPlayers(hidden || saved.isHiddenFromPlayers());
                t.setInitiative(null);
                boolean online = networkService.isConnected();
                // online, a iniciativa vai junto na mensagem de adicionar; offline, rola depois que o token existe
                if (rollInitiative && online) table.rollInitiative(t, true);
                addTokenToScene(t);
                if (rollInitiative && !online) table.rollInitiative(t, true);
                n++;
            }
            logEntries.add(LogEntry.system("Encontro \"" + enc.name + "\" no mapa: " + n + " token(s)"
                    + (hidden ? ", escondidos dos jogadores" : "") + (rollInitiative ? ", iniciativa rolada" : "") + "."));
        }
    }

    private void pushCampaign() {
        if (!editsCampaign()) return;
        try {
            campaign.save(mesaRepository.getRoot());
        } catch (IOException ex) {
            showError("Não deu pra salvar a campanha: " + ex.getMessage());
        }
        if (networkService.isConnected()) networkService.sendCampaign(campaign);
    }

    private void showMesaSettings() {
        if (mesaSettingsWindow == null) {
            VBox settings = new VBox(8);
            settings.setPadding(new Insets(12));
            ComboBox<String> themePicker = Theme.picker();
            Label themeHint = new Label("Muda as cores do programa todo na hora (fica salvo neste computador).");
            themeHint.getStyleClass().add("label-muted");
            themeHint.setWrapText(true);
            settings.getChildren().addAll(sectionLabel("Aparência"), themePicker, themeHint);
            javafx.scene.control.ComboBox<String> scaleBox = new javafx.scene.control.ComboBox<>();
            double uiScaleNow;
            try {
                uiScaleNow = Double.parseDouble(AppSettings.getString("ui.scale", "1"));
            } catch (RuntimeException ex) {
                uiScaleNow = 1;
            }
            for (double sc : UI_SCALES) {
                scaleBox.getItems().add(Math.round(sc * 100) + "%" + (sc == 1.0 ? " (normal)" : ""));
                if (Math.abs(sc - uiScaleNow) < 0.01) scaleBox.getSelectionModel().select(scaleBox.getItems().size() - 1);
            }
            Label scaleHint = new Label("Letra e botões maiores ou menores. Vale na próxima vez que o programa abrir.");
            scaleHint.getStyleClass().add("label-muted");
            scaleHint.setWrapText(true);
            scaleBox.setOnAction(e -> {
                int i = scaleBox.getSelectionModel().getSelectedIndex();
                if (i < 0) return;
                AppSettings.setString("ui.scale", String.valueOf(UI_SCALES[i]));
                scaleHint.setText("Guardado: " + scaleBox.getValue() + ". Feche e abra o programa pra ver.");
            });
            HBox scaleRow = new HBox(8, new Label("Tamanho da interface"), scaleBox);
            scaleRow.setAlignment(Pos.CENTER_LEFT);
            settings.getChildren().addAll(scaleRow, scaleHint);
            Button keysButton = new Button("Atalhos do teclado...", com.vttcaseiro.view.Icons.icon("tune", 14));
            keysButton.setOnAction(e -> showKeybindings());
            settings.getChildren().addAll(sectionLabel("Teclado"), keysButton);
        // --- Backup da mesa inteira ---
            Button exportButton = new Button("Exportar mesa inteira (backup)...");
            exportButton.setTooltip(new Tooltip("Um .zip com fichas, cenas, monstros, imagens, sons, diário e tabelas."
                    + " Importe na tela de escolher mesa (\"Importar backup...\")."));
            exportButton.setOnAction(e -> onExportMesa());
            Label backupHint = new Label("Pra guardar a campanha ou levar pra outro computador.");
            backupHint.getStyleClass().add("label-muted");
            javafx.scene.control.CheckBox flavor = new javafx.scene.control.CheckBox(
                    "Nomes sortidos pros monstros (\"Goblin Feroz\" em vez de \"Goblin 2\")");
            flavor.setSelected(AppSettings.getBool("monstros.nomesSortidos", true));
            flavor.selectedProperty().addListener((o, a, b) -> AppSettings.setBool("monstros.nomesSortidos", b));
            settings.getChildren().addAll(sectionLabel("Monstros"), flavor);
            settings.getChildren().addAll(sectionLabel("Backup da mesa"), exportButton, backupHint);
            Button exportModule = new Button("Exportar módulo...", com.vttcaseiro.view.Icons.icon("download", 14));
            exportModule.setOnAction(e -> onExportModule());
            Button importModule = new Button("Importar módulo...", com.vttcaseiro.view.Icons.icon("upload", 14));
            importModule.setOnAction(e -> onImportModule());
            Label moduleHint = new Label("Módulo: só as partes que você escolher (cenas, monstros, fichas de PdM, diário, "
                    + "PdMs/aventuras/tesouro/encontros da Campanha), com as imagens. Entra na mesa aberta sem apagar nada.");
            moduleHint.setWrapText(true);
            moduleHint.getStyleClass().add("label-muted");
            settings.getChildren().addAll(sectionLabel("Módulos (levar conteúdo pra outra mesa)"),
                    new HBox(6, exportModule, importModule), moduleHint);

            ComboBox<String> backupEvery = new ComboBox<>();
            for (int m : BACKUP_MINUTES) backupEvery.getItems().add(m == 0 ? "Desligado" : "A cada " + m + " min");
            int current = autoBackupMinutes();
            for (int i = 0; i < BACKUP_MINUTES.length; i++) if (BACKUP_MINUTES[i] == current) backupEvery.getSelectionModel().select(i);
            backupEvery.setTooltip(new Tooltip("Cópia automática da mesa (só quando algo mudou). Ficam as "
                    + com.vttcaseiro.library.AutoBackup.KEEP + " mais novas e a última de cada dia dos últimos "
                    + com.vttcaseiro.library.AutoBackup.KEEP_DAYS + " dias, na pasta backups da mesa."));
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
            Label mirrorLabel = new Label();
            mirrorLabel.setWrapText(true);
            mirrorLabel.getStyleClass().add("label-muted");
            Runnable showMirror = () -> {
                String dir = AppSettings.getString("backupMirrorDir", "");
                mirrorLabel.setText(dir.isBlank() ? "Cópia em outra pasta: desligada." : "Cópia também em: " + dir);
            };
            showMirror.run();
            Button mirrorButton = new Button("Copiar também pra outra pasta...");
            mirrorButton.setTooltip(new Tooltip("Cada backup vai também pra uma pasta sua (ex.: a do Google Drive ou um pen drive),"
                    + " com as imagens e sons. Se o computador der problema, a mesa está lá: tela de mesas > Importar backup."));
            mirrorButton.setOnAction(e -> {
                javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
                chooser.setTitle("Pasta pra cópia dos backups");
                java.io.File f = chooser.showDialog(mesaSettingsWindow);
                if (f == null) return;
                AppSettings.setString("backupMirrorDir", f.getAbsolutePath());
                showMirror.run();
                runBackup("manual", true);
            });
            Button mirrorOff = new Button("Parar a cópia");
            mirrorOff.setOnAction(e -> {
                AppSettings.setString("backupMirrorDir", "");
                showMirror.run();
            });
            settings.getChildren().addAll(sectionLabel("Backups automáticos"), backupEvery,
                    new javafx.scene.layout.FlowPane(6, 6, backupNow, restoreButton), backupStatus,
                    new javafx.scene.layout.FlowPane(6, 6, mirrorButton, mirrorOff), mirrorLabel);

            compactHints(settings);
            groupIntoCards(settings, "");
            ScrollPane scroll = new ScrollPane(settings);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("side-panel");
            mesaSettingsWindow = new Stage();
            mesaSettingsWindow.initOwner(stage);
            mesaSettingsWindow.setTitle("Mesa: " + mesaService.getName());
            Scene sc = new Scene(scroll, 420, 560);
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
        webLinks.setStyle("-fx-text-fill: -c-accent-text; -fx-font-weight: bold;");
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
        javafx.scene.Node playit = buildPlayitSection();
        box.getChildren().addAll(hostRow, hostMoreToggle, hostMore, playit, tlsCode, webBox, relayInfo, divider());

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
        onlineListView.setPlaceholder(emptyHint("Ninguém conectado."));
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

        box.getChildren().add(sectionLabel("Nome do grupo"));
        TextField partyNameField = new TextField(partyService.getParty().getName());
        partyNameField.setOnAction(e -> onRenameParty(partyNameField.getText()));
        Button renamePartyButton = new Button("Salvar o nome", com.vttcaseiro.view.Icons.icon("save", 14));
        renamePartyButton.setOnAction(e -> onRenameParty(partyNameField.getText()));
        HBox.setHgrow(partyNameField, Priority.ALWAYS);
        HBox nameRow = new HBox(6, partyNameField, iconize(renamePartyButton));
        nameRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(nameRow);

        partyMemberListView = new ListView<>();
        partyMemberListView.setPlaceholder(emptyHint("Ninguém no grupo ainda.\nAbra uma ficha e use + aqui."));
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
        Button addMemberButton = new Button("Pôr no grupo a ficha aberta", com.vttcaseiro.view.Icons.icon("add", 14));
        Button removeMemberButton = new Button("Tirar do grupo o escolhido", com.vttcaseiro.view.Icons.icon("trash", 14));
        removeMemberButton.setOnAction(e -> onRemovePartyMember());
        addMemberButton.setOnAction(e -> onAddLastOpenedCharacterToParty());
        box.getChildren().addAll(sectionHeader("Membros", iconize(addMemberButton), iconize(removeMemberButton)),
                partyMemberListView);

        partyInventoryListView = new ListView<>();
        partyInventoryListView.setPlaceholder(emptyHint("Nada no baú do grupo."));
        partyInventoryListView.setPrefHeight(140);
        partyInventoryListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " x" + item.getQuantity());
            }
        });
        refreshPartyInventoryList();
        Button addItemButton = new Button("Adicionar item...", com.vttcaseiro.view.Icons.icon("add", 14));
        addItemButton.setOnAction(e -> onAddPartyItem());
        Button transferItemButton = new Button("Passar 1 do escolhido pra ficha aberta", com.vttcaseiro.view.Icons.icon("send", 14));
        transferItemButton.setOnAction(e -> onTransferPartyItemToLastOpenedCharacter());
        Button splitCoinsButton = new Button("Dividir as moedas do baú entre os membros", com.vttcaseiro.view.Icons.icon("party", 14));
        splitCoinsButton.setOnAction(e -> onSplitPartyCoins());
        partyCoinsLabel = new Label();
        partyCoinsLabel.getStyleClass().add("label-muted");
        partyCoinsLabel.setWrapText(true);
        box.getChildren().addAll(sectionHeader("Baú do grupo", iconize(addItemButton), iconize(transferItemButton),
                iconize(splitCoinsButton)), partyInventoryListView, partyCoinsLabel);
        Button xpButton = new Button("Dar XP...", com.vttcaseiro.view.Icons.icon("spark", 14));
        xpButton.setOnAction(e -> onGiveXp());
        Button milestoneButton = new Button("Subir por marco", com.vttcaseiro.view.Icons.icon("next", 14));
        milestoneButton.setTooltip(new Tooltip("Avisa a todos que o grupo sobe de nível (pra quem joga sem XP)."));
        milestoneButton.setOnAction(e -> onMilestone());
        Label xpHint = new Label("XP: pra cada membro ou dividido, com aviso de quem já pode subir de nível. "
                + "Marco: avisa que o grupo sobe de nível.");
        xpHint.getStyleClass().add("label-muted");
        xpHint.setWrapText(true);
        HBox xpRow = new HBox(8, xpButton, milestoneButton);
        partyXpControls = List.of(xpRow, splitCoinsButton);
        refreshPartyInventoryList();

        Label hint = new Label("Membros: + põe no grupo a última ficha que você abriu (o nome aparece quando a ficha "
                + "está nesta mesa). Baú: o loot de todo mundo; a seta passa 1 unidade do item escolhido pra última "
                + "ficha aberta.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        box.getChildren().add(hint);
        box.getChildren().addAll(sectionLabel("Experiência (mestre)"), xpRow, xpHint);
        Runnable xpByRole = () -> {
            boolean gm = networkService.isGm();
            for (javafx.scene.Node n : List.of(xpRow, xpHint, splitCoinsButton)) showNode(n, gm);
            javafx.scene.Node label = box.getChildren().get(box.getChildren().indexOf(xpRow) - 1);
            showNode(label, gm);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(xpByRole));
        xpByRole.run();

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

        roundLabel = new Label("Sem combate");
        roundLabel.getStyleClass().add("live-status");
        roundLabel.getStyleClass().add("combat-status");
        Button pullButton = iconButton("download", "Puxar das fichas: usa a iniciativa que cada jogador rolou na própria "
                + "ficha (+ o modificador do rastreador)", () -> {
            int n = table.pullInitiativesFromSheets();
            if (n > 0) refreshInitiativeList();
        });
        Button editButton = iconButton("edit", "Editar a linha escolhida: iniciativa, modificador e cor (ou duplo clique)",
                this::onEditInitiative);
        Region statusGrow = new Region();
        HBox.setHgrow(statusGrow, Priority.ALWAYS);
        turnClockPanel = new Label();
        turnClockPanel.getStyleClass().add("turn-clock");
        showNode(turnClockPanel, false);
        Button timerButton = iconButton("timer", "Relógio do turno: quanto tempo cada um tem pra jogar (só avisa, não pula a vez)",
                () -> { });
        timerButton.setOnAction(e -> turnTimerMenu().show(timerButton, javafx.geometry.Side.BOTTOM, 0, 0));
        turnTimerButton = timerButton;
        HBox status = new HBox(4, roundLabel, turnClockPanel, statusGrow, timerButton, pullButton, editButton);
        status.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(status);
        initiativeListView = new ListView<>();
        initiativeListView.setPlaceholder(emptyHint("Ninguém na iniciativa ainda.\nColoque tokens no mapa e role."));
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

        // sem combate: rolar e comecar / comecar com as atuais; em combate: proximo turno / encerrar
        Button rollAllButton = new Button("Rolar e começar", com.vttcaseiro.view.Icons.icon("dice", 14));
        rollAllButton.setTooltip(new Tooltip("Rola a iniciativa de todos os tokens do mapa e começa o combate"));
        startCombatButton = rollAllButton;
        rollAllButton.setOnAction(e -> table.rollInitiativeForAllAndStart());
        Button startButton = new Button("Começar já");
        startButton.setTooltip(new Tooltip("Começa com as iniciativas que já estão na lista (sem rolar de novo)"));
        startButton.setOnAction(e -> table.startEncounter());
        Button nextTurnButton = new Button("Próximo turno", com.vttcaseiro.view.Icons.icon("next", 14));
        nextTurnPanelButton = nextTurnButton;
        nextTurnButton.setOnAction(e -> table.nextTurn());
        Button endButton = new Button("Encerrar");
        endButton.setTooltip(new Tooltip("Encerrar combate"));
        endButton.setOnAction(e -> table.endEncounter());
        combatIdleButtons = List.of(rollAllButton, startButton);
        combatActiveButtons = List.of(nextTurnButton, endButton);
        box.getChildren().add(buttonGrid(rollAllButton, startButton, nextTurnButton, endButton));
        combatToolButtons = List.of(pullButton, editButton, turnTimerButton);

        box.getChildren().add(sectionLabel("Mestre: grupo inteiro"));
        Button groupSaveButton = new Button("Salvaguarda...");
        groupSaveButton.setTooltip(new Tooltip("Todos rolam contra uma CD; da pra aplicar o dano (cheio em quem"
                + " falha, metade em quem passa) -- ex.: Fireball."));
        groupSaveButton.setOnAction(e -> onGroupSave());
        groupSaveButton.setGraphic(com.vttcaseiro.view.Icons.icon("spark", 14));
        Button shortRestButton = new Button("Descanso curto");
        shortRestButton.setTooltip(new Tooltip("Quem você escolher gasta dados de vida até ficar quase cheio e recupera "
                + "o que volta no descanso curto."));
        shortRestButton.setOnAction(e -> chooseRestSheets(false).ifPresent(table::groupShortRest));
        Button longRestButton = new Button("Descanso longo");
        longRestButton.setTooltip(new Tooltip("Quem você escolher recupera PV, dados de vida, espaços de magia e recursos; "
                + "o chat mostra o que cada um recuperou."));
        longRestButton.setOnAction(e -> chooseRestSheets(true).ifPresent(table::groupLongRest));
        Button calcButton = new Button("Dificuldade...", com.vttcaseiro.view.Icons.icon("perigo", 14));
        calcButton.setTooltip(new Tooltip("Calculadora de encontro: dificuldade (2024) dos monstros do mapa (ou dos "
                + "selecionados) pro nível do grupo."));
        calcButton.setOnAction(e -> onEncounterCalculator());
        groupSaveButton.setTooltip(new Tooltip("Salvaguarda em grupo: " + groupSaveButton.getTooltip().getText()));
        Button gmScreenButton = new Button("Escudo do mestre", com.vttcaseiro.view.Icons.icon("party", 14));
        gmScreenButton.setTooltip(new Tooltip("O grupo todo de uma vez: PV, CA, Percepção/Intuição/Investigação passivas, "
                + "CD de magia, idiomas e condições"));
        gmScreenButton.setOnAction(e -> showGmScreen());
        box.getChildren().add(buttonGrid(groupSaveButton, shortRestButton, longRestButton, calcButton, gmScreenButton));
        gmCombatCard = groupSaveButton;
        CheckBox groupBox = new CheckBox("Agrupar monstros iguais");
        groupBox.setTooltip(new Tooltip("Monstros da mesma ficha (4 Goblins) rolam uma iniciativa só, aparecem numa linha "
                + "e jogam no mesmo turno. Vale na próxima \"Rolar e começar\"."));
        groupBox.setSelected(sceneService.getScene().getEncounter().isGroupMonsters());
        groupBox.setOnAction(e -> {
            table.setGroupMonsters(groupBox.isSelected());
            refreshInitiativeList();
        });
        groupMonstersBox = groupBox;
        box.getChildren().add(groupBox);
        CheckBox approve = new CheckBox("Aprovar pedidos dos jogadores");
        approve.setTooltip(new Tooltip("Pedidos de dano, cura e efeitos. Desligado: o pedido de um jogador (ex.: dano no goblin que ele acertou) e aplicado"
                + " direto. Ligado: aparece uma pergunta pra você aprovar ou recusar."));
        approve.setSelected(approveRequests);
        approve.setOnAction(e -> approveRequests = approve.isSelected());
        box.getChildren().add(approve);

        refreshInitiativeList();

        Label hint = new Label("Ícones ao lado da rodada: puxar a iniciativa das fichas e editar a linha escolhida. "
                + "Bolinha da linha: clique troca a cor (cinza, verde, amarelo, vermelho). Botão direito: "
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
        journalList.setPlaceholder(emptyHint("Nada no diário ainda."));
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
                String to = h.getRecipients().isEmpty() ? "" : "  \u2192 " + String.join(", ", h.getRecipients());
                setText(gm ? (h.isRevealed() ? "\u25CF  " : "\u25CB  ") + h.getTitle() + to : h.getTitle());
                setStyle(gm && h.isRevealed() ? "-fx-text-fill: -c-accent-text;" : "");
                setTooltip(new Tooltip(gm ? (h.isRevealed() ? (h.getRecipients().isEmpty() ? "Revelada: todos os jogadores veem"
                        : "Revelada só pra " + String.join(", ", h.getRecipients())) : "Só você vê")
                        : "Clique duplo abre em janela"));
            }
        });
        journalViewer = new VBox(6);
        journalList.getSelectionModel().selectedItemProperty().addListener((o, a, h) -> showHandout(h));
        journalList.setOnMouseClicked(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && h != null) openHandoutWindow(h);
        });

        Button add = new Button("Nova anotação", com.vttcaseiro.view.Icons.icon("add", 14));
        add.setOnAction(e -> editHandout(null));
        Button edit = new Button("Editar a escolhida", com.vttcaseiro.view.Icons.icon("edit", 14));
        edit.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h != null) editHandout(h);
        });
        Button reveal = new Button("Revelar / esconder", com.vttcaseiro.view.Icons.icon("eye", 14));
        reveal.setTooltip(new Tooltip("Revelar manda a anotação pra todos os jogadores (e pra quem entrar depois)."));
        reveal.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h != null) setHandoutRevealed(h, !h.isRevealed());
        });
        Button sendImage = new Button("Mandar imagem pra...", com.vttcaseiro.view.Icons.icon("image", 14));
        sendImage.setTooltip(new Tooltip("Escolhe uma imagem (bilhete, mapa, retrato), pra quem vai, e ela abre na tela deles. "
                + "Fica guardada no Diário de cada um."));
        sendImage.setOnAction(e -> sendImageToPlayers());
        Button show = new Button("Abrir na tela deles", com.vttcaseiro.view.Icons.icon("send", 14));
        show.setTooltip(new Tooltip("Revela a escolhida (se ainda não estava) e já abre na tela de quem recebe."));
        show.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = journalList.getSelectionModel().getSelectedItem();
            if (h != null) showHandoutToPlayers(h);
        });
        Button remove = new Button("Apagar", com.vttcaseiro.view.Icons.icon("trash", 14));
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
        HBox gmButtons = new HBox(4, iconize(add), iconize(sendImage), iconize(edit), iconize(reveal), iconize(show), iconize(remove));
        gmButtons.setAlignment(Pos.CENTER_RIGHT);
        Label hint = new Label();
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        gmOnlyJournalControls = List.of(gmButtons);
        Runnable byRole = () -> {
            boolean gm = networkService.isGm();
            gmButtons.setVisible(gm);
            gmButtons.setManaged(gm);
            hint.setText(gm ? "Só você vê as anotações até revelar (\u25CF = revelada, \u2192 = só pra esses jogadores). "
                    + "Clique duplo abre em janela."
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
        box.getChildren().addAll(sectionHeader("Diário da mesa", gmButtons), journalList, journalViewer, hint,
                buildPlayerNotesSection(), buildTablesSection(), buildDecksSection());
        return new Tab("Diário", wrapInScroll(box));
    }

    // ---------------- anotacoes dos jogadores (so minhas ou do grupo)

    private javafx.scene.Node buildPlayerNotesSection() {
        playerNotesList = new ListView<>(playerNoteItems);
        playerNotesList.setPrefHeight(130);
        playerNotesList.setPlaceholder(emptyHint("Nenhuma anotação ainda.\nUse + pra escrever (só pra você ou pro grupo)."));
        playerNotesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.model.Handout h, boolean empty) {
                super.updateItem(h, empty);
                if (empty || h == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                boolean shared = groupNotes.containsKey(h.getId());
                setText(shared ? (h.getAuthor() == null ? "?" : h.getAuthor()) + ": " + h.getTitle() : "(só eu) " + h.getTitle());
                setTooltip(new Tooltip(shared ? "Compartilhada com o grupo" : "Só você vê (fica neste computador)"));
            }
        });
        playerNoteViewer = new Label();
        playerNoteViewer.setWrapText(true);
        playerNotesList.getSelectionModel().selectedItemProperty().addListener((o, a, h) ->
                playerNoteViewer.setText(h == null ? "" : h.getText()));
        Button add = new Button("Nova anotação", com.vttcaseiro.view.Icons.icon("add", 14));
        add.setOnAction(e -> editPlayerNote(null));
        Button edit = new Button("Editar a escolhida", com.vttcaseiro.view.Icons.icon("edit", 14));
        edit.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = playerNotesList.getSelectionModel().getSelectedItem();
            if (h == null) return;
            if (!canEditPlayerNote(h)) {
                showError("Essa anotação é de " + h.getAuthor() + ": só quem escreveu (ou o mestre) muda.");
                return;
            }
            editPlayerNote(h);
        });
        Button remove = new Button("Apagar a escolhida", com.vttcaseiro.view.Icons.icon("trash", 14));
        remove.setOnAction(e -> {
            com.vttcaseiro.model.Handout h = playerNotesList.getSelectionModel().getSelectedItem();
            if (h == null) return;
            if (!canEditPlayerNote(h)) {
                showError("Essa anotação é de " + h.getAuthor() + ": só quem escreveu (ou o mestre) apaga.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Apagar \"" + h.getTitle() + "\"?");
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            confirm.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> deletePlayerNote(h));
        });
        Label hint = new Label("Suas anotações da campanha. \"Só eu\" fica neste computador; compartilhada aparece no Diário "
                + "de todo mundo (e de quem entrar depois).");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        if (networkService.isGm() && groupNotesStore != null) {
            for (com.vttcaseiro.model.Handout h : groupNotesStore.load()) groupNotes.put(h.getId(), h);
        }
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            // mestre que acabou de hospedar/conectar: manda de novo o que o grupo tinha compartilhado
            if (networkService.isConnected() && networkService.isGm() && groupNotesStore != null) {
                for (com.vttcaseiro.model.Handout h : groupNotesStore.load()) networkService.sendGroupNote(h);
            }
            refreshPlayerNotes();
        }));
        refreshPlayerNotes();
        VBox box = new VBox(8, sectionHeader("Anotações dos jogadores", iconize(add), iconize(edit), iconize(remove)),
                playerNotesList, playerNoteViewer, hint);
        box.getStyleClass().add("panel-section");
        return box;
    }

    boolean canEditPlayerNote(com.vttcaseiro.model.Handout h) {
        if (!groupNotes.containsKey(h.getId())) return true; // so minha
        return networkService.isGm() || java.util.Objects.equals(h.getAuthor(), networkService.getPlayerName());
    }

    void refreshPlayerNotes() {
        if (playerNotesList == null) return;
        String selected = playerNotesList.getSelectionModel().getSelectedItem() == null ? null
                : playerNotesList.getSelectionModel().getSelectedItem().getId();
        List<com.vttcaseiro.model.Handout> all = new ArrayList<>(myNotesStore == null ? List.of() : myNotesStore.load());
        all.addAll(groupNotes.values());
        playerNoteItems.setAll(all);
        playerNotesList.refresh();
        for (com.vttcaseiro.model.Handout h : playerNoteItems) {
            if (h.getId().equals(selected)) {
                playerNotesList.getSelectionModel().select(h);
                playerNoteViewer.setText(h.getText());
                return;
            }
        }
        playerNoteViewer.setText("");
    }

    void editPlayerNote(com.vttcaseiro.model.Handout existing) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(existing == null ? "Nova anotação" : "Editar anotação");
        dialog.setHeaderText(null);
        dialog.initOwner(stage);
        TextField title = new TextField(existing == null ? "" : existing.getTitle());
        title.setPromptText("Pistas sobre o culto");
        javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(existing == null ? "" : existing.getText());
        text.setWrapText(true);
        text.setPrefRowCount(10);
        boolean wasShared = existing != null && groupNotes.containsKey(existing.getId());
        CheckBox share = new CheckBox("Compartilhar com o grupo");
        share.setSelected(wasShared);
        share.setDisable(!networkService.isConnected() && !wasShared);
        if (!networkService.isConnected()) share.setTooltip(new Tooltip("Conecte numa mesa pra compartilhar."));
        VBox content = new VBox(8, new Label("Título"), title, new Label("Texto"), text, share);
        content.setPrefWidth(440);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (title.getText().isBlank() && text.getText().isBlank()) return;
            savePlayerNote(existing, title.getText(), text.getText(), share.isSelected());
        });
    }

    /** Guarda (so minha) ou manda pro grupo; mudar de um pro outro tira do lugar antigo. */
    void savePlayerNote(com.vttcaseiro.model.Handout existing, String title, String text, boolean shared) {
        com.vttcaseiro.model.Handout h = existing == null ? new com.vttcaseiro.model.Handout() : existing;
        h.setTitle(title);
        h.setText(text);
        boolean wasShared = existing != null && groupNotes.containsKey(existing.getId());
        List<com.vttcaseiro.model.Handout> mine = myNotesStore.load();
        if (shared && networkService.isConnected()) {
            if (!wasShared) {
                mine.removeIf(x -> x.getId().equals(h.getId()));
                myNotesStore.save(mine);
            }
            networkService.sendGroupNote(h); // volta do servidor carimbada com o autor
            logEntries.add(LogEntry.system("Anotação compartilhada com o grupo: " + h.getTitle()));
        } else {
            if (wasShared) {
                groupNotes.remove(h.getId());
                networkService.sendGroupNoteRemove(h.getId());
                h.setAuthor(null);
                if (networkService.isGm()) groupNotesStore.save(new ArrayList<>(groupNotes.values()));
            }
            mine.removeIf(x -> x.getId().equals(h.getId()));
            mine.add(h);
            myNotesStore.save(mine);
        }
        refreshPlayerNotes();
    }

    void deletePlayerNote(com.vttcaseiro.model.Handout h) {
        if (groupNotes.remove(h.getId()) != null) {
            networkService.sendGroupNoteRemove(h.getId());
            if (networkService.isGm()) groupNotesStore.save(new ArrayList<>(groupNotes.values()));
        } else {
            List<com.vttcaseiro.model.Handout> mine = myNotesStore.load();
            mine.removeIf(x -> x.getId().equals(h.getId()));
            myNotesStore.save(mine);
        }
        refreshPlayerNotes();
    }

    /** Anotacao do grupo chegou (nova ou mudada). */
    void onGroupNoteReceived(com.vttcaseiro.model.Handout h) {
        boolean isNew = !groupNotes.containsKey(h.getId());
        groupNotes.put(h.getId(), h);
        if (networkService.isGm() && groupNotesStore != null) groupNotesStore.save(new ArrayList<>(groupNotes.values()));
        if (isNew && h.getAuthor() != null && !h.getAuthor().equals(networkService.getPlayerName())) {
            logEntries.add(LogEntry.system(h.getAuthor() + " compartilhou no Diário: " + h.getTitle()));
        }
        refreshPlayerNotes();
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
            // imagem do diario: ate 4096 de lado (da pra ler um mapa de mao), sem estourar a placa de video
            com.vttcaseiro.view.SafeImage.Loaded l = com.vttcaseiro.view.SafeImage.read(
                    imageAssetService.readBytes(h.getImageAssetId()), 4096, 4096L * 4096L);
            return l == null ? null : l.image();
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
        content.setStyle("-fx-background-color: -c-bg;");
        Label title = new Label(h.getTitle());
        title.getStyleClass().add("label-title");
        content.getChildren().add(title);
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView();
        view.setPreserveRatio(true);
        java.util.function.Predicate<javafx.scene.image.Image> put = img -> {
            if (img == null) return false;
            view.setImage(img);
            view.setFitWidth(Math.min(760, img.getWidth()));
            return true;
        };
        if (h.getImageAssetId() != null) {
            content.getChildren().add(view);
            if (!put.test(handoutImage(h))) {
                // a imagem ainda esta chegando pela rede: tenta de novo por uns segundos
                Label wait = new Label("(imagem chegando...)");
                wait.getStyleClass().add("label-muted");
                content.getChildren().add(wait);
                javafx.animation.Timeline retry = new javafx.animation.Timeline();
                retry.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), e -> {
                    if (put.test(handoutImage(h))) {
                        content.getChildren().remove(wait);
                        retry.stop();
                    }
                }));
                retry.setCycleCount(40);
                retry.setOnFinished(e -> wait.setText("(a imagem não chegou; abra de novo pelo Diário)"));
                retry.play();
            }
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
        editHandout(existing, null, null, false);
    }

    /** Escolhe a imagem e abre o dialogo ja com ela, marcado pra abrir na tela de quem recebe. */
    private void sendImageToPlayers() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        java.io.File f = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
        if (f == null) return;
        try {
            String asset = imageAssetService.importFile(f.toPath());
            String title = f.getName().replaceFirst("\\.[^.]+$", "");
            editHandout(null, asset, title, true);
        } catch (IOException ex) {
            showError("Não foi possível usar a imagem: " + ex.getMessage());
        }
    }

    /** Revela (se precisar) e abre na tela de quem recebe. */
    private void showHandoutToPlayers(com.vttcaseiro.model.Handout handout) {
        List<com.vttcaseiro.model.Handout> all = handoutStore.load();
        for (com.vttcaseiro.model.Handout h : all) {
            if (!h.getId().equals(handout.getId())) continue;
            h.setRevealed(true);
            handoutStore.save(all);
            networkService.sendHandoutShow(h);
            logEntries.add(LogEntry.system("Mostrando \"" + h.getTitle() + "\" pra "
                    + (h.getRecipients().isEmpty() ? "todos os jogadores" : String.join(", ", h.getRecipients())) + "."));
            break;
        }
        refreshJournal();
    }

    /** Nomes pra "pra quem": quem esta conectado (menos eu) e quem ja estava na lista. */
    List<String> handoutRecipientChoices(com.vttcaseiro.model.Handout existing) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        String me = networkService.getPlayerName();
        for (String n : networkService.getOnlinePlayers()) if (n != null && !n.equals(me)) names.add(n);
        if (existing != null) names.addAll(existing.getRecipients());
        return new ArrayList<>(names);
    }

    private void editHandout(com.vttcaseiro.model.Handout existing, String presetAsset, String presetTitle, boolean presetShow) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(existing == null ? (presetAsset != null ? "Mandar imagem" : "Nova anotação") : "Editar anotação");
        dialog.setHeaderText(existing != null && existing.isRevealed() ? "Revelada: os jogadores recebem a mudança" : null);
        dialog.initOwner(stage);
        TextField title = new TextField(existing == null ? (presetTitle == null ? "" : presetTitle) : existing.getTitle());
        title.setPromptText("Carta do Barao");
        javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(existing == null ? "" : existing.getText());
        text.setWrapText(true);
        text.setPrefRowCount(10);
        String[] assetId = {existing == null ? presetAsset : existing.getImageAssetId()};
        Label imageInfo = new Label(assetId[0] == null ? "Sem imagem" : "Com imagem");
        imageInfo.getStyleClass().add("label-muted");
        Button chooseImage = new Button("Escolher imagem...");
        chooseImage.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            java.io.File f = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
        // pra quem vai: todos, ou so alguns (cada um ve so o que e dele)
        List<String> names = handoutRecipientChoices(existing);
        javafx.scene.control.ToggleGroup toWhom = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton toAll = new javafx.scene.control.RadioButton("Todos os jogadores");
        javafx.scene.control.RadioButton toSome = new javafx.scene.control.RadioButton("Só pra:");
        toAll.setToggleGroup(toWhom);
        toSome.setToggleGroup(toWhom);
        javafx.scene.layout.FlowPane picks = new javafx.scene.layout.FlowPane(10, 6);
        List<CheckBox> pickBoxes = new ArrayList<>();
        for (String n : names) {
            CheckBox cb = new CheckBox(n);
            cb.setSelected(existing != null && existing.getRecipients().contains(n));
            pickBoxes.add(cb);
            picks.getChildren().add(cb);
        }
        if (names.isEmpty()) {
            Label none = new Label("(ninguém conectado agora)");
            none.getStyleClass().add("label-muted");
            picks.getChildren().add(none);
            toSome.setDisable(true);
        }
        boolean some = existing != null && !existing.getRecipients().isEmpty();
        (some ? toSome : toAll).setSelected(true);
        picks.disableProperty().bind(toSome.selectedProperty().not());
        CheckBox showNow = new CheckBox("Revelar e abrir agora na tela de quem recebe");
        showNow.setSelected(presetShow);
        VBox content = new VBox(8, new Label("Título"), title, new Label("Texto"), text,
                new HBox(8, chooseImage, noImage, imageInfo), new Label("Pra quem"), new HBox(12, toAll, toSome), picks, showNow);
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
            List<String> chosen = new ArrayList<>();
            if (toSome.isSelected()) for (CheckBox cb : pickBoxes) if (cb.isSelected()) chosen.add(cb.getText());
            h.setRecipients(chosen);
            if (showNow.isSelected()) h.setRevealed(true);
            if (all.stream().noneMatch(x -> x.getId().equals(h.getId()))) all.add(h);
            handoutStore.save(all);
            if (showNow.isSelected()) {
                networkService.sendHandoutShow(h);
                logEntries.add(LogEntry.system("Mostrando \"" + h.getTitle() + "\" pra "
                        + (chosen.isEmpty() ? "todos os jogadores" : String.join(", ", chosen)) + "."));
            } else if (h.isRevealed()) {
                networkService.sendHandout(h);
            }
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
                // PV de antes de quem eu mexo aqui (os outros viram pedido pro mestre): da pra desfazer
                java.util.Map<TokenModel, Integer[]> before = new java.util.LinkedHashMap<>();
                for (TokenModel t : targets) {
                    if (networkService.isGm() || table.canControl(t)) {
                        before.put(t, new Integer[]{t.getCurrentHp(), t.getMaxHp(), t.getTempHp()});
                    }
                }
                if (heal) table.healTargets(targets, amount, null);
                else table.damageTargets(targets, amount, damageTypeIn(rollText), null);
                registerHpUndo(before, heal ? "Cura" : "Dano");
            }

            @Override
            public Runnable undoFor(LogEntry entry) {
                return hpUndos.get(entry);
            }

            @Override
            public com.vttcaseiro.view.ChatListCell.PollView pollFor(LogEntry entry) {
                PollState p = pollByEntry.get(entry);
                return p == null ? null : new com.vttcaseiro.view.ChatListCell.PollView(p.question, p.options, p.counts, p.myVote);
            }

            @Override
            public void vote(LogEntry entry, int option) {
                PollState p = pollByEntry.get(entry);
                if (p == null) return;
                if (networkService.isConnected()) {
                    networkService.sendPollVote(p.id, option);
                } else {
                    if (p.myVote != null && p.myVote < p.counts.size()) p.counts.set(p.myVote, p.counts.get(p.myVote) - 1);
                    p.myVote = option;
                    p.counts.set(option, p.counts.get(option) + 1);
                    if (chatListView != null) chatListView.refresh();
                }
            }
        }));
        VBox.setVgrow(chatList, Priority.ALWAYS);
        chatList.setPlaceholder(emptyHint("Nada no chat ainda. Role um dado!"));
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
            File file = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
        // um seletor so: com que nome falo e quem ve as minhas rolagens
        chatModeButton = new javafx.scene.control.MenuButton();
        chatModeButton.getStyleClass().addAll("chip", "chat-mode");
        chatModeButton.setTooltip(new Tooltip("Falar como (você, seu personagem ou um PdM) e quem vê as suas rolagens "
                + "(vale pra todas: chat, ficha, ataques)"));
        chatModeButton.setOnShowing(e -> fillChatModeMenu());
        fillChatModeMenu();
        refreshChatModeButton();
        // FlowPane quebra linha quando o painel do chat e estreito, em vez de
        // espremer os botoes ate virarem "...".
        javafx.scene.layout.FlowPane quickRow = new javafx.scene.layout.FlowPane(6, 4,
                advButton, disButton, chatModeButton);
        quickRow.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node n : quickRow.getChildren()) {
            if (n instanceof javafx.scene.control.Control c) c.setMinWidth(Region.USE_PREF_SIZE);
        }

        macroRow = new javafx.scene.layout.FlowPane(5, 4);
        macroRow.setAlignment(Pos.CENTER_LEFT);
        refreshMacroRow();

        // macros na mesma linha de Vantagem/Desvantagem: uma linha a menos embaixo do chat
        quickRow.getChildren().add(macroRow);
        Button summaryButton = new Button("Resumo", com.vttcaseiro.view.Icons.icon("journal", 13));
        summaryButton.setTooltip(new Tooltip("Resumo da sessão: junta o que aconteceu no chat desde que a mesa abriu "
                + "(cenas, combates, críticos, dano, quem caiu, tesouro) num texto pra copiar ou guardar em Campanha > Sessões."));
        summaryButton.setOnAction(e -> showSessionSummary());
        quickRow.getChildren().add(summaryButton);
        Button statsButton = new Button("Dados", com.vttcaseiro.view.Icons.icon("dice", 13));
        statsButton.setTooltip(new Tooltip("Estatísticas do d20: rolagens, média, 20 e 1 naturais de cada um (sessão e histórico)"));
        statsButton.setOnAction(e -> showDiceStats());
        quickRow.getChildren().add(statsButton);
        box.getChildren().addAll(chatList, diceRow, quickRow, inputRow); // o titulo "Mesa" da barra ja diz o que e
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
            com.vttcaseiro.view.SafeImage.Loaded l = com.vttcaseiro.view.SafeImage.read(
                    imageAssetService.readBytes(assetId), 2048, 2048L * 2048L);
            if (l == null) return null;
            javafx.scene.image.Image img = l.image();
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
        File file = com.vttcaseiro.view.ImagePicker.open(chooser, null);
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
            networkService.sendSceneOpen(newScene, name.get());
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
        String world = "Azgaar - só a imagem (.png/.jpg, com o JSON opcional pras cidades)";
        String uvtt = "Dungeondraft / Universal VTT (.dd2vtt, .uvtt, .df2vtt): mapa com paredes, portas e luzes";
        String saved = "Azgaar - mundo (.map do Save ou JSON \"Full\" do Export): reinos viram facções, cidades e marcadores viram pins";
        javafx.scene.control.ChoiceDialog<String> pick = new javafx.scene.control.ChoiceDialog<>(saved, saved, dungeon, city, world, uvtt);
        pick.initOwner(stage);
        pick.setTitle("Importar mapa");
        pick.setHeaderText("De onde vem o mapa?\n"
                + "No One Page Dungeon: botão direito no mapa > Export > JSON.\n"
                + "No Azgaar: Export > JSON > \"Full\" (o programa desenha o mapa sozinho), ou Save (o .map) com o PNG do mesmo mundo.\n"
                + "Nos outros geradores do Watabou: exporte como PNG.\n"
                + "No Dungeondraft: File > Export > Universal VTT (imagem em PNG).");
        pick.setContentText("Tipo:");
        Optional<String> choice = pick.showAndWait();
        if (choice.isEmpty()) return;
        try {
            if (choice.get().equals(saved)) importAzgaarMap();
            else if (choice.get().equals(dungeon)) importWatabouDungeon();
            else if (choice.get().equals(uvtt)) importUniversalVtt();
            else importGeneratorImage(choice.get().equals(world));
        } catch (IOException | RuntimeException ex) {
            showError("Não foi possível importar o mapa: " + ex.getMessage());
        }
    }

    /** Mapa do Dungeondraft (ou outro editor) no formato Universal VTT: imagem + grade + paredes + portas + luzes. */
    private void importUniversalVtt() throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Mapa Universal VTT (Dungeondraft)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Universal VTT", "*.dd2vtt", "*.uvtt", "*.df2vtt", "*.json"),
                new FileChooser.ExtensionFilter("Todos", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        if (file.length() > 200L * 1024 * 1024) throw new IOException("Arquivo grande demais (mais de 200 MB).");
        com.vttcaseiro.importer.UniversalVtt.Result m =
                com.vttcaseiro.importer.UniversalVtt.parse(java.nio.file.Files.readString(file.toPath()));
        String base = file.getName().replaceFirst("\\.[^.]+$", "");
        Optional<String> name = askSceneName(base);
        if (name.isEmpty()) return;
        SceneModel scene = new SceneModel();
        scene.setBackgroundImageAssetId(mesaRepository.getImageAssetStore().store(m.image(), "mapa" + m.extension()));
        scene.getGrid().setCellSize(m.cellPx());
        scene.getGrid().setFeetPerCell(5);
        scene.getGrid().setVisible(false); // o Dungeondraft ja desenha a grade (ou nao) na imagem
        scene.setWalls(new java.util.ArrayList<>(m.walls()));
        scene.setLights(new java.util.ArrayList<>(m.lights()));
        scene.setVisionEnabled(!m.walls().isEmpty());
        createImportedScene(scene, name.get(), null, null);
        logEntries.add(LogEntry.system("Mapa Universal VTT importado: " + m.widthCells() + " × " + m.heightCells()
                + " casas, " + (m.walls().size() - m.doors()) + " paredes, " + m.doors() + " portas, "
                + m.lights().size() + " luzes." + (m.walls().isEmpty() ? "" : " Visão ligada.")));
    }

    // ---------------- mundo salvo do Azgaar (.map)

    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.SavedWorld> worldStore() {
        return new com.vttcaseiro.library.JsonListStore<>(mesaRepository.getRoot(), "mundos.json",
                new com.google.gson.reflect.TypeToken<List<com.vttcaseiro.model.SavedWorld>>() { }.getType());
    }

    /**
     * Mundo salvo do Azgaar: o .map traz os reinos, os povoados, os marcadores, as culturas e as religioes; a imagem
     * vem do PNG exportado do mesmo mundo. Reinos viram faccoes, povoados e marcadores viram pins, o resto vai pro
     * Diario. O mundo fica guardado na mesa pra abrir regioes dele depois.
     */
    private void importAzgaarMap() throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Mundo do Azgaar (.map do Save, ou JSON do Export > JSON > Full)");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Mundo do Azgaar (.map ou .json)", "*.map", "*.json", "*.gz"),
                new FileChooser.ExtensionFilter("Todos", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        if (file.length() > 400L * 1024 * 1024) throw new IOException("Arquivo grande demais (mais de 400 MB).");
        boolean json;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            byte[] head = in.readNBytes(64);
            String h = new String(head, java.nio.charset.StandardCharsets.UTF_8).stripLeading();
            json = h.startsWith("{") || h.startsWith("﻿{");
        }
        boolean isJson = json;
        inBackground("Lendo o mundo do Azgaar", () -> isJson
                ? com.vttcaseiro.importer.AzgaarJson.read(new java.io.InputStreamReader(
                        new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(file.toPath())), java.nio.charset.StandardCharsets.UTF_8))
                : new com.vttcaseiro.importer.AzgaarJson.Result(
                        com.vttcaseiro.importer.AzgaarMap.read(java.nio.file.Files.readAllBytes(file.toPath())), null),
                r -> continueAzgaarImport(file, isJson, r));
    }

    /** Segunda parte da importacao (o mundo ja lido): o que trazer, de onde vem a imagem, e cria a cena. */
    private void continueAzgaarImport(File file, boolean isJson, com.vttcaseiro.importer.AzgaarJson.Result r) throws IOException {
        com.vttcaseiro.importer.AzgaarMap.World world = r.world();
        boolean canDraw = r.geometry() != null;
        CheckBox factions = new CheckBox("Reinos viram facções na Campanha (" + world.states().size() + ")");
        factions.setSelected(!world.states().isEmpty());
        CheckBox burgs = new CheckBox("Cidades viram pins no mapa (as " + Math.min(60, world.burgs().size()) + " maiores)");
        burgs.setSelected(true);
        CheckBox markers = new CheckBox("Marcadores viram pins escondidos (" + world.markers().size() + ")");
        markers.setSelected(!world.markers().isEmpty());
        CheckBox journal = new CheckBox("Reinos, culturas, religiões e maiores cidades no Diário");
        journal.setSelected(true);
        TextField sceneName = new TextField(world.mapName());
        javafx.scene.control.ToggleGroup imgGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton drawRelief = new javafx.scene.control.RadioButton("Desenhar o mapa aqui (relevo e biomas)");
        javafx.scene.control.RadioButton drawPolitical = new javafx.scene.control.RadioButton("Desenhar o mapa aqui (político: cor de cada reino)");
        javafx.scene.control.RadioButton usePng = new javafx.scene.control.RadioButton("Usar o PNG exportado do mesmo mundo");
        for (javafx.scene.control.RadioButton rb : List.of(drawRelief, drawPolitical, usePng)) rb.setToggleGroup(imgGroup);
        (canDraw ? drawRelief : usePng).setSelected(true);
        drawRelief.setDisable(!canDraw);
        drawPolitical.setDisable(!canDraw);
        CheckBox namesAsLabels = new CheckBox("Nomes de reinos e cidades como rótulos da mesa (arrastar, renomear, esconder)");
        namesAsLabels.setTooltip(new Tooltip("O desenho sai sem os nomes; cada nome vira um rótulo que você move, edita ou "
                + "esconde dos jogadores. Clicar no nome de uma cidade abre o cartão dela."));
        namesAsLabels.disableProperty().bind(usePng.selectedProperty());
        Label imgHint = new Label(canDraw ? "O desenho vem dos dados do JSON: nítido até numa região de perto."
                : "O .map não traz o desenho do mapa: escolha o PNG exportado do mesmo mundo (Export > PNG). "
                + "Com o JSON \"Full\" o programa desenha sozinho.");
        imgHint.getStyleClass().add("label-muted");
        imgHint.setWrapText(true);
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Mundo do Azgaar");
        d.setHeaderText(world.mapName() + ": " + world.states().size() + " reinos, " + world.burgs().size() + " povoados, "
                + world.markers().size() + " marcadores");
        burgs.disableProperty().bind(namesAsLabels.selectedProperty().and(usePng.selectedProperty().not()));
        VBox content = new VBox(8, new Label("Nome da cena"), sceneName, new Label("Imagem"), drawRelief, drawPolitical, usePng,
                imgHint, namesAsLabels, new Label("O que trazer"), factions, burgs, markers, journal);
        content.setPrefWidth(480);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        String name = sceneName.getText().strip().isEmpty() ? world.mapName() : sceneName.getText().strip();
        if (mesaService.listMaps().contains(name)) throw new IOException("Já existe uma cena chamada \"" + name + "\".");

        File png = null;
        if (usePng.isSelected()) {
            FileChooser ic = new FileChooser();
            ic.setTitle("Imagem do mesmo mundo (no Azgaar: Export > PNG)");
            ic.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            png = com.vttcaseiro.view.ImagePicker.open(ic, stage);
            if (png == null) return;
        }
        File pngFile = png;
        com.vttcaseiro.importer.AzgaarRender.Style style = drawPolitical.isSelected()
                ? com.vttcaseiro.importer.AzgaarRender.Style.POLITICO : com.vttcaseiro.importer.AzgaarRender.Style.RELEVO;
        boolean bringFactions = factions.isSelected(), bringBurgs = burgs.isSelected(), bringMarkers = markers.isSelected(),
                bringJournal = journal.isSelected();
        boolean labels = pngFile == null && canDraw && namesAsLabels.isSelected();
        int[] renderSize = com.vttcaseiro.importer.AzgaarRender.outSize(world.width(), world.height(), 4096);
        inBackground(pngFile == null ? "Desenhando o mapa" : "Preparando a imagem", () -> {
            byte[] bytes;
            String fileName;
            if (pngFile != null) {
                bytes = java.nio.file.Files.readAllBytes(pngFile.toPath());
                fileName = pngFile.getName();
            } else {
                bytes = com.vttcaseiro.importer.AzgaarRender.png(world, r.geometry(), 0, 0, world.width(), world.height(),
                        renderSize[0], renderSize[1], style, !labels);
                fileName = "mundo.png";
            }
            com.vttcaseiro.importer.MapImageFit.Fitted img = com.vttcaseiro.importer.MapImageFit.fit(bytes, fileName);
            // guarda o mundo pra abrir regioes depois (o JSON compactado: ele e grande)
            java.nio.file.Path dir = mesaRepository.getRoot().resolve("mundos");
            java.nio.file.Files.createDirectories(dir);
            String base = com.vttcaseiro.library.MesaBackup.safeName(name) + "-" + System.currentTimeMillis();
            String stored = isJson ? base + ".json.gz" : base + ".map";
            if (isJson) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.toPath());
                     java.io.OutputStream out = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(dir.resolve(stored)))) {
                    in.transferTo(out);
                }
            } else {
                java.nio.file.Files.copy(file.toPath(), dir.resolve(stored));
            }
            return new Object[]{img, "mundos/" + stored};
        }, res -> {
            com.vttcaseiro.importer.MapImageFit.Fitted img = (com.vttcaseiro.importer.MapImageFit.Fitted) res[0];
            String storedFile = (String) res[1];
            SceneModel scene = new SceneModel();
            String assetId = mesaRepository.getImageAssetStore().store(img.bytes(), "mundo" + img.extension());
            scene.setBackgroundImageAssetId(assetId);
            scene.getGrid().setVisible(false);
            double mapPxPerImagePx = world.width() > 0 ? world.width() / (double) img.width() : 1;
            scene.getGrid().setFeetPerCell(Math.max(1, Math.round(scene.getGrid().getCellSize() * mapPxPerImagePx * world.feetPerPixel())));
            List<com.vttcaseiro.model.MapPin> pins = new ArrayList<>();
            if (labels) {
                pins.addAll(com.vttcaseiro.importer.AzgaarEditor.labelPins(world, r.geometry(), 0, 0, world.width(), world.height(),
                        renderSize[0], renderSize[1], img.width()));
            }
            if ((bringBurgs && !labels) || bringMarkers) {
                pins.addAll(world.pins(img.width(), img.height(), bringBurgs && !labels ? 60 : 0, bringMarkers));
            }
            scene.setPins(pins);
            createImportedScene(scene, name, bringJournal ? world.mapName() : null, bringJournal ? world.journalText() : null);

            int addedFactions = 0;
            if (bringFactions && editsCampaign()) {
                java.util.Set<String> have = new java.util.HashSet<>();
                for (com.vttcaseiro.model.Campaign.Faction f : campaign.getFactions()) have.add(f.name);
                for (com.vttcaseiro.model.Campaign.Faction f : world.factions()) {
                    if (have.add(f.name)) {
                        campaign.getFactions().add(f);
                        addedFactions++;
                    }
                }
                if (addedFactions > 0) {
                    pushCampaign();
                    if (campaignView != null && campaignView.isShowing()) campaignView.reload(campaign);
                }
            }
            var store = worldStore();
            List<com.vttcaseiro.model.SavedWorld> worlds = store.load();
            com.vttcaseiro.model.SavedWorld saved = new com.vttcaseiro.model.SavedWorld(name, storedFile, assetId, name);
            saved.setStyle(pngFile != null ? com.vttcaseiro.model.SavedWorld.STYLE_PNG : style.name());
            saved.setLabelsAsPins(labels);
            worlds.add(saved);
            store.save(worlds);
            logEntries.add(LogEntry.system("Mundo do Azgaar importado: " + world.states().size() + " reinos"
                    + (addedFactions > 0 ? " (" + addedFactions + " facções novas na Campanha)" : "") + ", "
                    + world.burgs().size() + " povoados, " + world.markers().size() + " marcadores. "
                    + "Pra jogar de perto: Mundo > Cenas > \"Região de um mundo\"."
                    + (img.changed() ? " Imagem reduzida pra caber na mesa (" + img.width() + "x" + img.height() + ")." : "")));
        });
    }

    /** Trabalho pesado (ler/desenhar o mundo) fora da tela; o resto continua na tela quando acabar. */
    private <T> void inBackground(String what, java.util.concurrent.Callable<T> work, IoConsumer<T> done) {
        logEntries.add(LogEntry.system(what + "..."));
        stage.getScene().setCursor(javafx.scene.Cursor.WAIT);
        Thread t = new Thread(() -> {
            try {
                T value = work.call();
                Platform.runLater(() -> {
                    stage.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
                    try {
                        done.accept(value);
                    } catch (IOException | RuntimeException ex) {
                        showError("Não deu pra terminar: " + ex.getMessage());
                    }
                });
            } catch (Exception | OutOfMemoryError ex) {
                Platform.runLater(() -> {
                    stage.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
                    showError(what + " falhou: " + (ex instanceof OutOfMemoryError ? "memória insuficiente (arquivo grande demais)"
                            : ex.getMessage()));
                });
            }
        }, "azgaar");
        t.setDaemon(true);
        t.start();
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    /** Le o mundo guardado com as edicoes do mestre aplicadas. */
    private com.vttcaseiro.importer.AzgaarJson.Result readSavedWorld(com.vttcaseiro.model.SavedWorld sw) throws IOException {
        return com.vttcaseiro.importer.AzgaarEditor.apply(readSavedWorldRaw(sw), sw.getEdits());
    }

    /** Le o mundo guardado como veio do Azgaar: o JSON compactado (com o desenho) ou o .map. */
    private com.vttcaseiro.importer.AzgaarJson.Result readSavedWorldRaw(com.vttcaseiro.model.SavedWorld sw) throws IOException {
        java.nio.file.Path p = mesaRepository.getRoot().resolve(sw.getMapFile());
        if (sw.getMapFile().endsWith(".json.gz")) {
            try (java.io.Reader in = new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(
                    new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(p))), java.nio.charset.StandardCharsets.UTF_8)) {
                return com.vttcaseiro.importer.AzgaarJson.read(in);
            }
        }
        return new com.vttcaseiro.importer.AzgaarJson.Result(com.vttcaseiro.importer.AzgaarMap.read(java.nio.file.Files.readAllBytes(p)), null);
    }

    /** Abre a regiao em volta de um povoado de um mundo guardado como cena nova (com os pins de la e a regua certa). */
    void onAzgaarRegion() {
        com.vttcaseiro.model.SavedWorld world = pickSavedWorld("Região de um mundo");
        if (world == null) return;
        inBackground("Lendo o mundo " + world.getName(), () -> readSavedWorld(world), r -> regionDialog(world, r));
    }

    /** Escolhe um mundo guardado (o ultimo, se so tem um). null = nenhum ou cancelou. */
    private com.vttcaseiro.model.SavedWorld pickSavedWorld(String title) {
        List<com.vttcaseiro.model.SavedWorld> worlds = worldStore().load();
        if (worlds.isEmpty()) {
            showError("Nenhum mundo do Azgaar nesta mesa ainda. Importe um: Importar mapa > \"Azgaar - mundo\".");
            return null;
        }
        com.vttcaseiro.model.SavedWorld sw = worlds.get(worlds.size() - 1);
        if (worlds.size() > 1) {
            javafx.scene.control.ChoiceDialog<com.vttcaseiro.model.SavedWorld> pick =
                    new javafx.scene.control.ChoiceDialog<>(sw, worlds);
            pick.initOwner(stage);
            pick.setTitle(title);
            pick.setHeaderText(null);
            pick.setContentText("Mundo:");
            Optional<com.vttcaseiro.model.SavedWorld> chosen = pick.showAndWait();
            if (chosen.isEmpty()) return null;
            sw = chosen.get();
        }
        return sw;
    }

    /** Grava o mundo (achado pelo arquivo) com as mudancas. */
    private void saveWorld(com.vttcaseiro.model.SavedWorld sw) {
        var store = worldStore();
        List<com.vttcaseiro.model.SavedWorld> worlds = store.load();
        for (int i = 0; i < worlds.size(); i++) {
            if (java.util.Objects.equals(worlds.get(i).getMapFile(), sw.getMapFile())) {
                worlds.set(i, sw);
                store.save(worlds);
                return;
            }
        }
        worlds.add(sw);
        store.save(worlds);
    }

    // ---------------- editor do mundo do Azgaar

    void onEditWorld() {
        com.vttcaseiro.model.SavedWorld world = pickSavedWorld("Editar mundo");
        if (world == null) return;
        inBackground("Lendo o mundo " + world.getName(), () -> readSavedWorldRaw(world), raw -> worldEditorDialog(world, raw));
    }

    /** Janela do editor: reinos (nome, nome completo, cor) e cidades (nome, populacao). Salvar redesenha e atualiza. */
    private void worldEditorDialog(com.vttcaseiro.model.SavedWorld sw, com.vttcaseiro.importer.AzgaarJson.Result raw) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.vttcaseiro.model.WorldEdits edits = gson.fromJson(gson.toJson(sw.getEdits()), com.vttcaseiro.model.WorldEdits.class);
        com.vttcaseiro.importer.AzgaarMap.World w = raw.world();
        boolean canColor = raw.geometry() != null;
        boolean[] loading = {false};

        // ---- reinos
        ListView<com.vttcaseiro.importer.AzgaarMap.State> stateList = new ListView<>(FXCollections.observableArrayList(w.states()));
        stateList.setPrefWidth(240);
        stateList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.vttcaseiro.importer.AzgaarMap.State st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) { setText(null); return; }
                var se = edits.states().get(st.id());
                setText((se != null && se.name != null ? se.name : st.name()) + (se != null ? "  •" : ""));
            }
        });
        TextField sName = new TextField();
        TextField sFull = new TextField();
        javafx.scene.control.ColorPicker sColor = new javafx.scene.control.ColorPicker();
        sColor.setDisable(!canColor);
        Label sOrig = new Label();
        sOrig.getStyleClass().add("label-muted");
        sOrig.setWrapText(true);
        Runnable saveState = () -> {
            var st = stateList.getSelectionModel().getSelectedItem();
            if (st == null || loading[0]) return;
            var se = new com.vttcaseiro.model.WorldEdits.StateEdit();
            String n = sName.getText().strip(), f = sFull.getText().strip();
            se.name = n.isEmpty() || n.equals(st.name()) ? null : n;
            String origFull = st.fullName() == null ? "" : st.fullName();
            se.fullName = f.equals(origFull) ? null : f;
            String origColor = canColor && st.id() < raw.geometry().stateColors().length ? raw.geometry().stateColors()[st.id()] : null;
            String hex = String.format("#%02x%02x%02x", (int) Math.round(sColor.getValue().getRed() * 255),
                    (int) Math.round(sColor.getValue().getGreen() * 255), (int) Math.round(sColor.getValue().getBlue() * 255));
            se.color = !canColor || hex.equalsIgnoreCase(origColor == null ? "" : origColor) ? null : hex;
            if (se.name == null && se.fullName == null && se.color == null) edits.states().remove(st.id());
            else edits.states().put(st.id(), se);
            stateList.refresh();
        };
        stateList.getSelectionModel().selectedItemProperty().addListener((o, a, st) -> {
            if (st == null) return;
            loading[0] = true;
            var se = edits.states().get(st.id());
            sName.setText(se != null && se.name != null ? se.name : st.name());
            sFull.setText(se != null && se.fullName != null ? se.fullName : st.fullName() == null ? "" : st.fullName());
            String c = se != null && se.color != null ? se.color
                    : canColor && st.id() < raw.geometry().stateColors().length ? raw.geometry().stateColors()[st.id()] : null;
            try {
                sColor.setValue(c == null ? javafx.scene.paint.Color.GRAY : javafx.scene.paint.Color.web(c));
            } catch (IllegalArgumentException ex) {
                sColor.setValue(javafx.scene.paint.Color.GRAY);
            }
            sOrig.setText("No Azgaar: " + st.title() + (st.form() == null || st.form().isBlank() ? "" : " (" + st.form() + ")"));
            loading[0] = false;
        });
        sName.textProperty().addListener((o, a, b) -> saveState.run());
        sFull.textProperty().addListener((o, a, b) -> saveState.run());
        sColor.valueProperty().addListener((o, a, b) -> saveState.run());
        javafx.scene.layout.GridPane sForm = new javafx.scene.layout.GridPane();
        sForm.setHgap(8);
        sForm.setVgap(8);
        sForm.addRow(0, new Label("Nome"), sName);
        sForm.addRow(1, new Label("Nome completo"), sFull);
        sForm.addRow(2, new Label("Cor"), sColor);
        sForm.add(sOrig, 1, 3);
        if (!canColor) {
            Label noColor = new Label("Cor: só em mundo importado do JSON \"Full\" (o desenho é feito aqui).");
            noColor.getStyleClass().add("label-muted");
            noColor.setWrapText(true);
            sForm.add(noColor, 1, 4);
        }
        HBox.setHgrow(sForm, Priority.ALWAYS);
        HBox statesPane = new HBox(12, stateList, sForm);
        statesPane.setPadding(new Insets(10));

        // ---- cidades
        List<com.vttcaseiro.importer.AzgaarMap.Burg> allBurgs = new ArrayList<>(w.burgs());
        allBurgs.sort(java.util.Comparator.comparing(com.vttcaseiro.importer.AzgaarMap.Burg::capital)
                .thenComparingDouble(com.vttcaseiro.importer.AzgaarMap.Burg::population).reversed());
        javafx.collections.transformation.FilteredList<com.vttcaseiro.importer.AzgaarMap.Burg> shown =
                new javafx.collections.transformation.FilteredList<>(FXCollections.observableArrayList(allBurgs));
        TextField search = new TextField();
        search.setPromptText("Procurar cidade...");
        ListView<com.vttcaseiro.importer.AzgaarMap.Burg> burgList = new ListView<>(shown);
        burgList.setPrefWidth(240);
        burgList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.vttcaseiro.importer.AzgaarMap.Burg b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setText(null); return; }
                var be = edits.burgs().get(b.id());
                setText((be != null && be.name != null ? be.name : b.name()) + (b.capital() ? " (capital)" : "") + (be != null ? "  •" : ""));
            }
        });
        search.textProperty().addListener((o, a, q) -> {
            String t = q == null ? "" : q.strip().toLowerCase(java.util.Locale.ROOT);
            shown.setPredicate(b -> {
                if (t.isEmpty()) return true;
                var be = edits.burgs().get(b.id());
                String n = be != null && be.name != null ? be.name : b.name();
                return n.toLowerCase(java.util.Locale.ROOT).contains(t) || b.name().toLowerCase(java.util.Locale.ROOT).contains(t);
            });
        });
        TextField bName = new TextField();
        TextField bPop = new TextField();
        bPop.setPromptText("habitantes");
        Label bOrig = new Label();
        bOrig.getStyleClass().add("label-muted");
        bOrig.setWrapText(true);
        Runnable saveBurg = () -> {
            var b = burgList.getSelectionModel().getSelectedItem();
            if (b == null || loading[0]) return;
            var be = new com.vttcaseiro.model.WorldEdits.BurgEdit();
            String n = bName.getText().strip();
            be.name = n.isEmpty() || n.equals(b.name()) ? null : n;
            try {
                double pop = Double.parseDouble(bPop.getText().strip().replace(".", "").replace(",", "."));
                be.population = Math.round(pop) == Math.round(b.population()) ? null : Math.max(0, pop);
            } catch (NumberFormatException ex) {
                be.population = null;
            }
            if (be.name == null && be.population == null) edits.burgs().remove(b.id());
            else edits.burgs().put(b.id(), be);
            burgList.refresh();
        };
        burgList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) return;
            loading[0] = true;
            var be = edits.burgs().get(b.id());
            bName.setText(be != null && be.name != null ? be.name : b.name());
            bPop.setText(String.valueOf(Math.round(be != null && be.population != null ? be.population : b.population())));
            String st = w.stateName(b.state());
            bOrig.setText("No Azgaar: " + b.name() + (st != null ? ", " + st : "") + " (~" + Math.round(b.population()) + " hab.)");
            loading[0] = false;
        });
        bName.textProperty().addListener((o, a, b) -> saveBurg.run());
        bPop.textProperty().addListener((o, a, b) -> saveBurg.run());
        javafx.scene.layout.GridPane bForm = new javafx.scene.layout.GridPane();
        bForm.setHgap(8);
        bForm.setVgap(8);
        bForm.addRow(0, new Label("Nome"), bName);
        bForm.addRow(1, new Label("População"), bPop);
        bForm.add(bOrig, 1, 2);
        HBox.setHgrow(bForm, Priority.ALWAYS);
        VBox burgLeft = new VBox(6, search, burgList);
        VBox.setVgrow(burgList, Priority.ALWAYS);
        HBox burgsPane = new HBox(12, burgLeft, bForm);
        burgsPane.setPadding(new Insets(10));

        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane(
                new javafx.scene.control.Tab("Reinos (" + w.states().size() + ")", statesPane),
                new javafx.scene.control.Tab("Cidades (" + w.burgs().size() + ")", burgsPane));
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefSize(640, 420);
        if (!w.states().isEmpty()) stateList.getSelectionModel().select(0);
        if (!allBurgs.isEmpty()) burgList.getSelectionModel().select(0);

        String redraw = sw.isDrawn() && raw.geometry() != null
                ? "Ao salvar, o mapa" + (sw.getRegions().isEmpty() ? "" : " e as " + sw.getRegions().size() + " regiões abertas dele")
                + " são redesenhados; pins, rótulos e facções com o nome antigo passam pro novo."
                : "Ao salvar, pins, rótulos e facções com o nome antigo passam pro novo. A imagem deste mundo veio de um PNG"
                + " (ou foi importada antes desta versão): ela não é redesenhada.";
        Label hint = new Label(redraw);
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        Button resetAll = new Button("Voltar tudo ao original do Azgaar");
        resetAll.setOnAction(e -> {
            edits.states().clear();
            edits.burgs().clear();
            var st = stateList.getSelectionModel().getSelectedItem();
            var b = burgList.getSelectionModel().getSelectedItem();
            stateList.getSelectionModel().clearSelection();
            burgList.getSelectionModel().clearSelection();
            if (st != null) stateList.getSelectionModel().select(st);
            if (b != null) burgList.getSelectionModel().select(b);
            stateList.refresh();
            burgList.refresh();
        });
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Editar mundo: " + sw.getName());
        d.setHeaderText("Mudanças ficam guardadas na mesa; o arquivo do Azgaar continua igual.");
        VBox content = new VBox(8, tabs, hint, resetAll);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        ((Button) d.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK)).setText("Salvar");
        if (d.showAndWait().filter(bt -> bt == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        applyWorldEdits(sw, raw, edits);
    }

    /** Uma cena do mundo redesenhada (a imagem nova) ou so com os pins atualizados (img null). */
    private record WorldRedraw(String sceneName, com.vttcaseiro.importer.MapImageFit.Fitted img, boolean main) {
    }

    /** Salva as edicoes, redesenha o mundo e as regioes (se foram desenhados aqui) e passa os nomes novos pra mesa. */
    private void applyWorldEdits(com.vttcaseiro.model.SavedWorld sw, com.vttcaseiro.importer.AzgaarJson.Result raw,
                                 com.vttcaseiro.model.WorldEdits edits) {
        var before = com.vttcaseiro.importer.AzgaarEditor.apply(raw, sw.getEdits());
        var after = com.vttcaseiro.importer.AzgaarEditor.apply(raw, edits);
        sw.setEdits(edits);
        saveWorld(sw);
        boolean redraw = sw.isDrawn() && after.geometry() != null;
        inBackground(redraw ? "Redesenhando o mundo " + sw.getName() : "Atualizando o mundo " + sw.getName(), () -> {
            List<WorldRedraw> out = new ArrayList<>();
            var aw = after.world();
            if (redraw) {
                int[] rs = com.vttcaseiro.importer.AzgaarRender.outSize(aw.width(), aw.height(), 4096);
                byte[] png = com.vttcaseiro.importer.AzgaarRender.png(aw, after.geometry(), 0, 0, aw.width(), aw.height(), rs[0], rs[1],
                        com.vttcaseiro.importer.AzgaarRender.Style.valueOf(sw.getStyle()), !sw.isLabelsAsPins());
                out.add(new WorldRedraw(sw.getSceneName(), com.vttcaseiro.importer.MapImageFit.fit(png, "mundo.png"), true));
                for (var reg : sw.getRegions()) {
                    int[] size = com.vttcaseiro.importer.AzgaarRender.outSize(reg.w, reg.h, com.vttcaseiro.importer.AzgaarRegion.OUT_SIDE);
                    byte[] rp = com.vttcaseiro.importer.AzgaarRender.png(aw, after.geometry(), reg.x, reg.y, reg.w, reg.h, size[0], size[1],
                            com.vttcaseiro.importer.AzgaarRender.Style.valueOf(reg.style == null ? "RELEVO" : reg.style), !reg.labelsAsPins);
                    out.add(new WorldRedraw(reg.sceneName, com.vttcaseiro.importer.MapImageFit.fit(rp, "regiao.png"), false));
                }
            } else {
                out.add(new WorldRedraw(sw.getSceneName(), null, true));
            }
            return out;
        }, list -> {
            int pins = 0, scenes = 0;
            for (WorldRedraw wr : list) {
                int[] changed = {0};
                boolean ok = updateSavedScene(wr.sceneName(), scene -> {
                    if (wr.img() != null) {
                        String asset = mesaRepository.getImageAssetStore().store(wr.img().bytes(), (wr.main() ? "mundo" : "regiao") + wr.img().extension());
                        scene.setBackgroundImageAssetId(asset);
                        if (wr.main()) sw.setImageAssetId(asset);
                    }
                    changed[0] = com.vttcaseiro.importer.AzgaarEditor.renamePins(scene.getPins(), before.world(), after.world());
                });
                if (ok) {
                    scenes++;
                    pins += changed[0];
                }
            }
            if (redraw) saveWorld(sw);
            int factionsChanged = 0;
            if (editsCampaign()) {
                factionsChanged = com.vttcaseiro.importer.AzgaarEditor.renameFactions(campaign.getFactions(), before.world(), after.world());
                if (factionsChanged > 0) {
                    pushCampaign();
                    if (campaignView != null && campaignView.isShowing()) campaignView.reload(campaign);
                }
            }
            logEntries.add(LogEntry.system("Mundo " + sw.getName() + " atualizado: " + scenes + (scenes == 1 ? " cena" : " cenas")
                    + (redraw ? " redesenhada" + (scenes == 1 ? "" : "s") : "") + ", " + pins + " pins/rótulos renomeados"
                    + (factionsChanged > 0 ? ", " + factionsChanged + " facções" : "") + "."));
        });
    }

    /**
     * Mexe numa cena guardada pelo nome: se for a aberta agora, muda ao vivo (e manda pra todos); senao abre o
     * arquivo, muda e grava. false = a cena nao existe mais (apagada ou renomeada).
     */
    private boolean updateSavedScene(String name, IoConsumer<SceneModel> change) throws IOException {
        if (name == null || !mesaService.listMaps().contains(name)) return false;
        if (name.equals(currentMapName)) {
            SceneModel scene = sceneService.getScene();
            change.accept(scene);
            mesaService.saveMap(scene, name);
            sceneService.replaceScene(scene);
            networkService.sendSceneOpen(scene, name);
            return true;
        }
        SceneModel scene = mesaService.loadMap(name);
        change.accept(scene);
        mesaService.saveMap(scene, name);
        return true;
    }

    private void regionDialog(com.vttcaseiro.model.SavedWorld sw, com.vttcaseiro.importer.AzgaarJson.Result r) {
        com.vttcaseiro.importer.AzgaarMap.World w = r.world();
        ComboBox<String> placeBox = new ComboBox<>();
        placeBox.setEditable(true);
        placeBox.setPrefWidth(280);
        List<com.vttcaseiro.importer.AzgaarMap.Burg> list = new ArrayList<>(w.burgs());
        list.sort(java.util.Comparator.comparing(com.vttcaseiro.importer.AzgaarMap.Burg::capital)
                .thenComparingDouble(com.vttcaseiro.importer.AzgaarMap.Burg::population).reversed());
        for (var b : list) placeBox.getItems().add(b.name());
        for (var m : w.markers()) {
            String t = com.vttcaseiro.importer.AzgaarMap.markerTitle(m) + " (marcador)";
            if (!placeBox.getItems().contains(t)) placeBox.getItems().add(t);
        }
        if (!placeBox.getItems().isEmpty()) placeBox.getSelectionModel().select(0);
        javafx.scene.control.Spinner<Integer> radius = new javafx.scene.control.Spinner<>(1, 5000, 25, 5);
        radius.setEditable(true);
        Label unit = new Label("de raio (" + w.distanceUnit() + ")");
        ComboBox<String> styleBox = new ComboBox<>(FXCollections.observableArrayList("Relevo e biomas", "Político"));
        styleBox.getSelectionModel().select(0);
        boolean canDraw = r.geometry() != null;
        CheckBox regionLabels = new CheckBox("Nomes como rótulos da mesa");
        regionLabels.setSelected(sw.isLabelsAsPins());
        regionLabels.setTooltip(new Tooltip("O desenho sai sem nomes; cada nome vira um rótulo que dá pra mover, editar ou esconder."));
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Região de " + sw.getName());
        d.setHeaderText(canDraw ? "A região em volta de um lugar vira cena nova, desenhada de perto, com os pins de lá."
                : "A região em volta de um lugar vira cena nova, recortada da imagem do mundo, com os pins de lá.");
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.addRow(0, new Label("Em volta de"), placeBox);
        g.addRow(1, new Label("Tamanho"), new HBox(6, radius, unit));
        if (canDraw) {
            g.addRow(2, new Label("Desenho"), styleBox);
            g.add(regionLabels, 1, 3);
        }
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        String place = placeBox.getEditor().getText() == null ? "" : placeBox.getEditor().getText().strip();
        double cx = 0, cy = 0;
        boolean found = false;
        for (var b : w.burgs()) if (b.name().equals(place)) { cx = b.x(); cy = b.y(); found = true; break; }
        if (!found) {
            for (var m : w.markers()) {
                if ((com.vttcaseiro.importer.AzgaarMap.markerTitle(m) + " (marcador)").equals(place)) { cx = m.x(); cy = m.y(); found = true; break; }
            }
        }
        if (!found) {
            showError("Não achei \"" + place + "\" nesse mundo. Escolha um lugar da lista.");
            return;
        }
        Optional<String> name = askSceneName(place.replace(" (marcador)", "") + " (região)");
        if (name.isEmpty()) return;
        double perPixel = w.distanceScale() > 0 ? w.distanceScale() : 3;
        double[] rect = com.vttcaseiro.importer.AzgaarRegion.square(cx, cy, radius.getValue() / perPixel, w.width(), w.height());
        com.vttcaseiro.importer.AzgaarRender.Style style = styleBox.getSelectionModel().getSelectedIndex() == 1
                ? com.vttcaseiro.importer.AzgaarRender.Style.POLITICO : com.vttcaseiro.importer.AzgaarRender.Style.RELEVO;
        int r2 = radius.getValue();
        boolean labels = canDraw && regionLabels.isSelected();
        int[] size = com.vttcaseiro.importer.AzgaarRender.outSize(rect[2], rect[3], com.vttcaseiro.importer.AzgaarRegion.OUT_SIDE);
        inBackground(canDraw ? "Desenhando a região" : "Recortando a região", () -> {
            byte[] png;
            if (canDraw) {
                png = com.vttcaseiro.importer.AzgaarRender.png(w, r.geometry(), rect[0], rect[1], rect[2], rect[3], size[0], size[1], style, !labels);
            } else {
                png = com.vttcaseiro.importer.AzgaarRegion.crop(imageAssetService.readBytes(sw.getImageAssetId()),
                        w.width(), w.height(), rect).png();
            }
            return com.vttcaseiro.importer.MapImageFit.fit(png, "regiao.png");
        }, img -> {
            SceneModel scene = new SceneModel();
            scene.setBackgroundImageAssetId(mesaRepository.getImageAssetStore().store(img.bytes(), "regiao" + img.extension()));
            scene.getGrid().setVisible(false);
            double mapPxPerImagePx = rect[2] / img.width();
            scene.getGrid().setFeetPerCell(Math.max(1, Math.round(scene.getGrid().getCellSize() * mapPxPerImagePx * w.feetPerPixel())));
            List<com.vttcaseiro.model.MapPin> pins = new ArrayList<>();
            if (labels) {
                pins.addAll(com.vttcaseiro.importer.AzgaarEditor.labelPins(w, r.geometry(), rect[0], rect[1], rect[2], rect[3],
                        size[0], size[1], img.width()));
            }
            pins.addAll(w.pinsIn(rect[0], rect[1], rect[2], rect[3], img.width(), img.height(), labels ? 0 : 80, true));
            scene.setPins(pins);
            createImportedScene(scene, name.get(), null, null);
            if (canDraw) {
                com.vttcaseiro.model.SavedWorld.Region reg = new com.vttcaseiro.model.SavedWorld.Region();
                reg.sceneName = name.get();
                reg.x = rect[0];
                reg.y = rect[1];
                reg.w = rect[2];
                reg.h = rect[3];
                reg.style = style.name();
                reg.labelsAsPins = labels;
                sw.getRegions().add(reg);
                saveWorld(sw);
            }
            logEntries.add(LogEntry.system("Região de " + sw.getName() + " aberta: " + (2 * r2) + " " + w.distanceUnit()
                    + " de lado, " + scene.getPins().size() + " pins."));
        });
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
        File file = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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
        networkService.sendSceneOpen(scene, name);
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
        queue.setPlaceholder(emptyHint("Fila vazia: selecione uma cena acima e \"Por na fila\"."));
        Runnable reload = () -> {
            queue.getItems().setAll(sceneQueueStore.load());
            refreshSceneStrip();
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
        // fila compacta: icones (o nome fica na dica); "proxima" tambem esta na barra de cima
        Object[][] icons = {{next, "next"}, {open, "send"}, {enqueue, "add"}, {up, "upload"}, {down, "download"},
                {removeQ, "trash"}};
        for (Object[] it : icons) {
            Button b = (Button) it[0];
            String text = b.getText().replace("▶", "").strip();
            if (b.getTooltip() == null) b.setTooltip(new Tooltip(text));
            else b.getTooltip().setText(text + ": " + b.getTooltip().getText());
            b.setGraphic(com.vttcaseiro.view.Icons.icon((String) it[1], 14));
            b.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            b.getStyleClass().add("panel-icon-button");
        }
        VBox box = new VBox(6, sectionLabel("Fila de cenas da sessão"), queue, new HBox(4, next, open, enqueue, up, down, removeQ));
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

    // ---------------- lista de musicas (uma atras da outra)

    private final List<com.vttcaseiro.model.SoundEntry> playlist = new ArrayList<>();
    private int playlistIndex;
    private boolean playlistWired;

    private HBox playlistRow(Button play, CheckBox shuffle) {
        play.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(play, Priority.ALWAYS);
        shuffle.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(8, play, shuffle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Comeca a lista: toca a primeira; quando cada uma acaba (no programa do mestre), manda a proxima pra todos. */
    void startPlaylist(List<com.vttcaseiro.model.SoundEntry> tracks, boolean shuffle) {
        if (tracks.isEmpty()) {
            showError("Nenhum som na biblioteca ainda.");
            return;
        }
        if (!playlistWired) {
            playlistWired = true;
            soundPlayer.setOnTrackEnd(ch -> Platform.runLater(() -> {
                if (com.vttcaseiro.model.AudioCommand.MUSIC.equals(ch) && !playlist.isEmpty()) playNextInPlaylist();
            }));
        }
        playlist.clear();
        playlist.addAll(tracks);
        if (shuffle) java.util.Collections.shuffle(playlist);
        playlistIndex = -1;
        playNextInPlaylist();
        logEntries.add(LogEntry.system("Lista de músicas: " + playlist.size() + " faixa(s)" + (shuffle ? ", em ordem aleatória" : "") + "."));
    }

    private void playNextInPlaylist() {
        if (playlist.isEmpty()) return;
        playlistIndex = (playlistIndex + 1) % playlist.size(); // acabou a lista: volta pra primeira
        com.vttcaseiro.model.SoundEntry se = playlist.get(playlistIndex);
        gmAudio(com.vttcaseiro.model.AudioCommand.playOnce(com.vttcaseiro.model.AudioCommand.MUSIC, se.getId(), se.getName()));
    }

    // ---------------- estatisticas dos dados

    /** Tabela por pessoa (desta sessao e de todo o historico do chat da mesa), com botao de mandar pro chat. */
    void showDiceStats() {
        List<LogEntry> session = new ArrayList<>(logEntries.subList(Math.min(sessionStartIndex, logEntries.size()), logEntries.size()));
        List<LogEntry> history;
        try {
            history = new com.vttcaseiro.library.ChatLogStore(mesaRepository.getRoot()).loadLast(200_000);
        } catch (RuntimeException ex) {
            history = new ArrayList<>(logEntries);
        }
        List<DiceStats.Line> now = DiceStats.of(session);
        List<DiceStats.Line> all = DiceStats.of(history);
        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane(
                new javafx.scene.control.Tab("Esta sessão", statsTable(now)),
                new javafx.scene.control.Tab("Todo o histórico", statsTable(all)));
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Estatísticas dos dados");
        d.setHeaderText("Só o d20 (ataques, testes, salvaguardas). Rolagem escondida fica de fora.");
        javafx.scene.control.ButtonType toChat = new javafx.scene.control.ButtonType("Mandar a da sessão pro chat",
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(toChat, javafx.scene.control.ButtonType.CLOSE);
        d.getDialogPane().setContent(tabs);
        d.getDialogPane().setPrefSize(560, 360);
        d.showAndWait().filter(b -> b == toChat).ifPresent(b -> announce(DiceStats.summary(now, "Dados da sessão")));
    }

    private javafx.scene.Node statsTable(List<DiceStats.Line> lines) {
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(16);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        String[] head = {"Quem", "Rolagens", "Média", "20 naturais", "1 naturais"};
        for (int c = 0; c < head.length; c++) {
            Label h = new Label(head[c]);
            h.getStyleClass().add("label-section");
            g.add(h, c, 0);
        }
        int row = 1;
        for (DiceStats.Line l : lines) {
            g.addRow(row++, new Label(l.who()), new Label(String.valueOf(l.rolls())),
                    new Label(String.format(java.util.Locale.ROOT, "%.1f", l.average()).replace('.', ',')),
                    new Label(String.valueOf(l.crits())), new Label(String.valueOf(l.fumbles())));
        }
        if (lines.isEmpty()) g.add(emptyHint("Nenhum d20 rolado ainda."), 0, 1, 5, 1);
        return new ScrollPane(g);
    }

    // ---------------- resumo da sessao

    private int sessionStartIndex;

    /** Junta o chat desta sessao num resumo e mostra pra copiar ou guardar em Campanha > Sessoes. */
    void showSessionSummary() {
        List<LogEntry> entries = new ArrayList<>(logEntries.subList(Math.min(sessionStartIndex, logEntries.size()), logEntries.size()));
        String text = SessionSummary.build(entries, java.time.LocalDate.now(),
                networkService.isGm() || !networkService.isConnected());
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(text);
        area.setWrapText(true);
        area.setPrefSize(560, 420);
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Resumo da sessão");
        d.setHeaderText("Feito a partir do chat desde que a mesa abriu. Dá pra editar antes de copiar ou guardar.");
        javafx.scene.control.ButtonType copy = new javafx.scene.control.ButtonType("Copiar", javafx.scene.control.ButtonBar.ButtonData.LEFT);
        javafx.scene.control.ButtonType keep = new javafx.scene.control.ButtonType("Guardar em Campanha > Sessões",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().add(copy);
        if (editsCampaign()) d.getDialogPane().getButtonTypes().add(keep);
        d.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        Button copyBtn = (Button) d.getDialogPane().lookupButton(copy);
        copyBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume(); // nao fecha
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString(area.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            copyBtn.setText("Copiado!");
        });
        d.getDialogPane().setContent(area);
        d.showAndWait().filter(b -> b == keep).ifPresent(b -> {
            com.vttcaseiro.model.Campaign.SessionLog s = new com.vttcaseiro.model.Campaign.SessionLog();
            s.number = campaign.nextSessionNumber();
            s.date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            s.summary = area.getText();
            campaign.getSessions().add(s);
            pushCampaign();
            if (campaignView != null && campaignView.isShowing()) campaignView.reload(campaign);
            logEntries.add(LogEntry.system("Resumo guardado como Sessão " + s.number + " em Campanha > Sessões."));
        });
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
        library.setPlaceholder(emptyHint("Nenhum som ainda.\nUse + pra trazer mp3, ogg ou wav."));
        Runnable reload = () -> library.getItems().setAll(soundIndex.load());
        reload.run();
        Button add = new Button("Adicionar sons...", com.vttcaseiro.view.Icons.icon("add", 14));
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
        library.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        Button playlistButton = new Button("Tocar em sequência", com.vttcaseiro.view.Icons.icon("next", 14));
        playlistButton.setTooltip(new Tooltip("Toca as músicas escolhidas (Ctrl+clique pra escolher várias; nenhuma ou "
                + "uma = a lista toda) uma atrás da outra, como música, pra todos. Parar música encerra a lista."));
        CheckBox shuffleBox = new CheckBox("Aleatório");
        playlistButton.setOnAction(e -> {
            List<com.vttcaseiro.model.SoundEntry> chosen = new ArrayList<>(library.getSelectionModel().getSelectedItems());
            if (chosen.size() < 2) chosen = new ArrayList<>(library.getItems());
            startPlaylist(chosen, shuffleBox.isSelected());
        });
        Button music = new Button("Música", com.vttcaseiro.view.Icons.icon("sound", 14));
        music.setTooltip(new Tooltip("Toca o escolhido como música (em loop, pra todos)"));
        Button ambient = new Button("Ambiente");
        ambient.setTooltip(new Tooltip("Toca o escolhido como som ambiente (em loop, junto com a música)"));
        Button sfx = new Button("Efeito");
        sfx.setTooltip(new Tooltip("Toca o escolhido uma vez (porta, trovão, grito)"));
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
        stopMusic.setOnAction(e -> {
            playlist.clear(); // parar a musica encerra a lista
            gmAudio(com.vttcaseiro.model.AudioCommand.stop(com.vttcaseiro.model.AudioCommand.MUSIC));
        });
        Button stopAmbient = new Button("Parar ambiente");
        stopAmbient.setOnAction(e -> gmAudio(com.vttcaseiro.model.AudioCommand.stop(com.vttcaseiro.model.AudioCommand.AMBIENT)));
        Button remove = new Button("Tirar o escolhido da lista", com.vttcaseiro.view.Icons.icon("trash", 14));
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
        Label playAs = new Label("Tocar o escolhido como");
        playAs.getStyleClass().add("label-muted");
        VBox gmBox = new VBox(8, sectionHeader("Sons da mesa", iconize(add), iconize(remove)), library, playAs,
                buttonGrid(3, music, ambient, sfx), buttonGrid(stopMusic, stopAmbient), playlistRow(playlistButton, shuffleBox), hint);
        gmBox.getStyleClass().add("panel-section");
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
        playing.setStyle("-fx-text-fill: -c-accent-text;");
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
            slider.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(slider, Priority.ALWAYS);
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
        box.getChildren().addAll(gmBox, sectionLabel("Tocando agora"), playing, sectionLabel("Volume (só nesta máquina)"),
                volumes, mute, dice);
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
            java.io.File f = com.vttcaseiro.view.ImagePicker.open(chooser, stage);
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

    /** Esconde/mostra a cena atual dos jogadores (mestre preparando). Manda pra mesa e grava. */
    private void setSceneHidden(boolean hidden) {
        SceneModel scene = sceneService.getScene();
        if (scene.isHiddenFromPlayers() == hidden) return;
        scene.setHiddenFromPlayers(hidden);
        if (networkService.isConnected()) networkService.sendSceneSwitch(scene);
        if (currentMapName != null) {
            try {
                mesaService.saveMap(scene, currentMapName);
            } catch (IOException | IllegalArgumentException ex) {
                showError("Não deu pra salvar a cena: " + ex.getMessage());
            }
        }
        mapHolder.getChildren().setAll(buildMapArea()); // faixa "escondida" aparece/some
        refreshSceneStrip();
        logEntries.add(LogEntry.system(hidden ? "Cena escondida dos jogadores: eles veem só um aviso até você mostrar."
                : "Cena liberada pros jogadores."));
    }

    /** Abre a cena so pro mestre preparar: os jogadores ficam com o aviso "o mestre esta preparando". */
    private void onOpenMapHidden(String name) {
        openMapFromMesa(name, true);
    }

    /** Abre a cena pra todos (tira a cortina, se a cena estava escondida). */
    private void onOpenMapFromMesa(String name) {
        openMapFromMesa(name, false);
    }

    private void openMapFromMesa(String name, boolean hidden) {
        try {
            SceneModel loaded = mesaService.loadMap(name);
            if (loaded.isHiddenFromPlayers() != hidden) {
                loaded.setHiddenFromPlayers(hidden);
                mesaService.saveMap(loaded, name);
            }
            if (imageAssetService.migrateInlineImages(loaded)) {
                // Cena salva no formato antigo (imagens em base64 dentro do
                // .json): converte pra assets e regrava, menor e mais rapida.
                mesaService.saveMap(loaded, name);
                logEntries.add(LogEntry.system("Cena \"" + name + "\" convertida pro formato novo de imagens (arquivo menor)."));
            }
            flushSceneAutosave(); // o que mudou na cena de antes vai pro disco antes de trocar
            if (mapView.hasPerspective()) endViewAs();
            sceneService.replaceScene(loaded);
            networkService.sendSceneOpen(loaded, name);
            currentMapName = name;
            groupMapName = name;
            sideViewing = false;
            if (currentSceneLabel != null) currentSceneLabel.setText("Cena atual: " + name);
            logEntries.add(LogEntry.system(hidden ? "Cena aberta escondida (preparando): " + name : "Cena aberta: " + name));
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
            add.accept("Abrir escondida (preparar)", () -> onOpenMapHidden(entry.name()));
            if (entry.name().equals(currentMapName)) {
                boolean hiddenNow = sceneService.getScene().isHiddenFromPlayers();
                add.accept(hiddenNow ? "Mostrar aos jogadores" : "Esconder dos jogadores", () -> setSceneHidden(!hiddenNow));
            }
            add.accept("Mostrar rapidinho e voltar", () -> onPeekScene(entry.name()));
            add.accept("Renomear...", () -> onRenameScene(entry.name()));
            add.accept("Duplicar...", () -> onDuplicateScene(entry.name()));
            add.accept("Apagar...", () -> onDeleteScene(entry.name()));
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
        File file = com.vttcaseiro.view.ImagePicker.open(chooser, null);
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

    // ---------------- enquetes no chat

    /** Uma enquete: o servidor conta os votos; offline conta aqui. */
    private static final class PollState {
        String id;
        String author;
        String question;
        List<String> options;
        List<Integer> counts;
        Integer myVote;
        LogEntry entry;
    }

    private final java.util.Map<String, PollState> pollsById = new java.util.HashMap<>();
    private final java.util.Map<LogEntry, PollState> pollByEntry = new java.util.IdentityHashMap<>();

    /** /votar Pergunta? | A | B: cria a enquete (online o servidor guarda e conta). */
    void startPoll(String rest) {
        List<String> parts = com.vttcaseiro.service.ChatCommands.pollParts(rest);
        if (parts == null) {
            logEntries.add(LogEntry.error("Use: /votar Pergunta? | Opção 1 | Opção 2   (de 2 a 10 opções)"));
            return;
        }
        String id = java.util.UUID.randomUUID().toString();
        List<String> options = parts.subList(1, parts.size());
        if (networkService.isConnected()) {
            networkService.sendPoll(id, parts.get(0), new ArrayList<>(options));
        } else {
            List<Integer> zero = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) zero.add(0);
            upsertPoll(id, networkService.getPlayerName(), parts.get(0), options, zero, null);
        }
    }

    /** Enquete nova vira uma linha no chat; voto novo so atualiza a linha. */
    void upsertPoll(String id, String author, String question, List<String> options, List<Integer> counts, Integer myVote) {
        PollState p = pollsById.get(id);
        if (p == null) {
            p = new PollState();
            p.id = id;
            p.entry = LogEntry.chat(author == null ? "?" : author, "enquete");
            pollsById.put(id, p);
            pollByEntry.put(p.entry, p);
            p.author = author;
            p.question = question == null ? "" : question;
            p.options = new ArrayList<>(options);
            p.counts = new ArrayList<>(counts);
            p.myVote = myVote;
            logEntries.add(p.entry);
            return;
        }
        p.counts = new ArrayList<>(counts);
        p.myVote = myVote;
        if (chatListView != null) chatListView.refresh();
    }

    // ---------------- lojas

    private com.vttcaseiro.library.JsonListStore<com.vttcaseiro.model.Shop> shopStore;
    private List<com.vttcaseiro.model.Shop> shopsWorking;
    private List<com.vttcaseiro.model.Shop> receivedShops = new ArrayList<>();
    private com.vttcaseiro.view.ShopWindow shopWindow;

    /** Mestre (ou sozinho): edita e vende direto; jogador online: so as lojas abertas, e o pedido vai pro mestre. */
    private boolean runsShops() {
        return !networkService.isConnected() || networkService.isGm();
    }

    private List<com.vttcaseiro.model.Shop> workingShops() {
        if (shopsWorking == null) shopsWorking = new ArrayList<>(shopStore.load());
        return shopsWorking;
    }

    void openShops() {
        if (shopWindow == null || !shopWindow.isShowing()) {
            shopWindow = new com.vttcaseiro.view.ShopWindow(stage, new com.vttcaseiro.view.ShopWindow.Actions() {
                @Override
                public List<com.vttcaseiro.model.Shop> shops() {
                    return runsShops() ? new ArrayList<>(workingShops()) : new ArrayList<>(receivedShops);
                }

                @Override
                public boolean isGm() {
                    return runsShops();
                }

                @Override
                public List<CharacterSheet> sheets() {
                    return shopSheets();
                }

                @Override
                public void shopsChanged(List<com.vttcaseiro.model.Shop> shops) {
                    shopsWorking = new ArrayList<>(shops);
                    shopStore.save(shopsWorking);
                    if (networkService.isConnected() && networkService.isGm()) networkService.sendShops(shopsWorking);
                }

                @Override
                public void buy(com.vttcaseiro.model.Shop shop, com.vttcaseiro.model.Shop.Item item, int qty, CharacterSheet sheet) {
                    shopTrade(shop.getId(), item.getName(), qty, sheet, false, networkService.getPlayerName());
                }

                @Override
                public void sell(com.vttcaseiro.model.Shop shop, com.vttcaseiro.inventory.InventoryItem item, int qty, CharacterSheet sheet) {
                    shopTrade(shop.getId(), item.getName(), qty, sheet, true, networkService.getPlayerName());
                }
            });
        }
        shopWindow.show();
    }

    /** Fichas que aparecem na loja: as minhas (jogador) ou as do grupo no mapa e da party (mestre/sozinho). */
    private List<CharacterSheet> shopSheets() {
        java.util.LinkedHashMap<String, CharacterSheet> out = new java.util.LinkedHashMap<>();
        if (runsShops()) {
            for (CharacterSheet s : partySheets()) out.put(s.getId(), s);
            if (lastOpenedCharacter != null) out.putIfAbsent(lastOpenedCharacter.getId(), lastOpenedCharacter);
        } else {
            for (String name : characterService.list()) {
                try {
                    CharacterSheet s = characterService.open(name);
                    if (networkService.getPlayerId().equals(s.getOwnerPlayerId())) {
                        CharacterSheetView v = openSheetViews.get(s.getId());
                        out.put(s.getId(), v != null ? v.getSheet() : s);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // ficha estragada: fica de fora
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** Compra/venda: quem roda a loja aplica na hora; jogador online manda o pedido pro mestre. */
    private void shopTrade(String shopId, String item, int qty, CharacterSheet sheet, boolean sell, String who) {
        if (!runsShops()) {
            com.vttcaseiro.net.NetMessage.ShopRequest r = new com.vttcaseiro.net.NetMessage.ShopRequest();
            r.shopId = shopId;
            r.item = item;
            r.quantity = qty;
            r.sheetId = sheet.getId();
            r.sell = sell;
            networkService.sendShopRequest(r);
            logEntries.add(LogEntry.system("Pedido enviado pro mestre: " + (sell ? "vender " : "comprar ") + qty + "x " + item + "."));
            return;
        }
        com.vttcaseiro.model.Shop shop = workingShops().stream().filter(s -> s.getId().equals(shopId)).findFirst().orElse(null);
        if (shop == null) {
            showError("Essa loja não existe mais.");
            return;
        }
        long before = sheet.getInventory().getMoney().totalInCopper();
        String err = sell ? com.vttcaseiro.service.ShopService.sell(shop, item, qty, sheet)
                : com.vttcaseiro.service.ShopService.buy(shop, item, qty, sheet);
        if (err != null) {
            if (who != null && !who.equals(networkService.getPlayerName()) && networkService.isConnected()) {
                onChatInput("/w \"" + who + "\" " + err);
            } else {
                showError(err);
            }
            return;
        }
        long diff = Math.abs(sheet.getInventory().getMoney().totalInCopper() - before);
        saveTradedSheet(sheet);
        shopStore.save(workingShops());
        if (networkService.isConnected()) networkService.sendShops(workingShops());
        announce(sheet.getName() + (sell ? " vendeu " : " comprou ") + qty + "x " + item + " em " + shop.getName() + " por "
                + com.vttcaseiro.service.ShopService.coins(diff) + ".");
        if (shopWindow != null && shopWindow.isShowing()) shopWindow.refresh();
    }

    private void saveTradedSheet(CharacterSheet sheet) {
        try {
            characterService.save(sheet);
            networkService.sendCharacterSync(sheet);
            CharacterSheetView v = openSheetViews.get(sheet.getId());
            if (v != null && v.getSheet() != sheet) v.reload(sheet);
            else if (v != null) v.reload(sheet);
        } catch (IOException ex) {
            showError("Não deu pra salvar a ficha " + sheet.getName() + ": " + ex.getMessage());
        }
    }

    /** Mestre recebe o pedido de um jogador e aplica (dinheiro e item na ficha dele). */
    private void handleShopRequest(com.vttcaseiro.net.NetMessage.ShopRequest r) {
        if (!networkService.isGm() || r.sheetId == null) return;
        CharacterSheet sheet = characterService.findOpen(r.sheetId).or(() -> characterService.findById(r.sheetId)).orElse(null);
        if (sheet == null) {
            onChatInput("/w \"" + r.player + "\" A loja não achou a sua ficha (ela está nesta mesa?).");
            return;
        }
        CharacterSheetView v = openSheetViews.get(sheet.getId());
        if (v != null) sheet = v.getSheet();
        shopTrade(r.shopId, r.item, Math.max(1, Math.min(999, r.quantity)), sheet, r.sell, r.player);
    }

    // ---------------- escudo do mestre

    /** Fichas do grupo: as ligadas a tokens na cena e as da party. */
    private List<CharacterSheet> partySheets() {
        java.util.LinkedHashMap<String, CharacterSheet> out = new java.util.LinkedHashMap<>();
        java.util.List<String> ids = new ArrayList<>();
        for (TokenModel t : sceneService.getScene().getTokens()) if (t.getCharacterSheetId() != null) ids.add(t.getCharacterSheetId());
        ids.addAll(partyService.getParty().getCharacterIds());
        for (String id : ids) {
            if (id == null || out.containsKey(id)) continue;
            CharacterSheetView v = openSheetViews.get(id);
            CharacterSheet s = v != null ? v.getSheet() : characterService.findOpen(id).or(() -> characterService.findById(id)).orElse(null);
            if (s != null) out.put(id, s);
        }
        return new ArrayList<>(out.values());
    }

    private Stage gmScreenStage;

    /** O grupo todo numa tabela: PV, CA, passivas, CD de magia, idiomas e condicoes. */
    void showGmScreen() {
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(14);
        g.setVgap(6);
        g.setPadding(new Insets(12));
        String[] head = {"Personagem", "PV", "CA", "Percepção", "Intuição", "Investigação", "CD magia", "Idiomas", "Condições"};
        for (int c = 0; c < head.length; c++) {
            Label h = new Label(head[c]);
            h.getStyleClass().add("label-section");
            g.add(h, c, 0);
        }
        List<CharacterSheet> sheets = partySheets();
        int row = 1;
        for (CharacterSheet s : sheets) {
            TokenModel tk = sceneService.getScene().getTokens().stream().filter(t -> s.getId().equals(t.getCharacterSheetId())).findFirst().orElse(null);
            String hp = (s.getHpCurrent() == null ? "?" : s.getHpCurrent()) + "/" + (s.getHpMax() == null ? "?" : s.getHpMax())
                    + (s.getHpTemp() > 0 ? " (+" + s.getHpTemp() + ")" : "");
            String conds = tk == null ? "" : String.join(", ", tk.getConditions().stream().map(this::conditionName).toList());
            String[] cells = {s.getName() + (s.getPlayerName() == null || s.getPlayerName().isBlank() ? "" : " (" + s.getPlayerName() + ")"),
                    hp, String.valueOf(s.totalArmorClass()), String.valueOf(s.passivePerception()),
                    String.valueOf(10 + s.skillBonus(com.vttcaseiro.character.Skill.INSIGHT)),
                    String.valueOf(10 + s.skillBonus(com.vttcaseiro.character.Skill.INVESTIGATION)),
                    s.getSpellcastingAbility() == null ? "-" : String.valueOf(s.spellSaveDc()),
                    String.join(", ", s.getLanguages()), conds};
            for (int c = 0; c < cells.length; c++) {
                Label l = new Label(cells[c]);
                l.setWrapText(c >= 7);
                if (c >= 7) l.setMaxWidth(220);
                if (c == 0) l.setStyle("-fx-font-weight: bold;");
                g.add(l, c, row);
            }
            row++;
        }
        if (sheets.isEmpty()) g.add(new Label("Nenhum personagem: ponha os tokens das fichas no mapa ou na party."), 0, 1, 9, 1);
        Button refresh = new Button("Atualizar", com.vttcaseiro.view.Icons.icon("undo", 14));
        refresh.setOnAction(e -> showGmScreen());
        Label tip = new Label("Grupo = fichas com token nesta cena + as da party. Passivas = 10 + perícia.");
        tip.getStyleClass().add("label-muted");
        HBox bottom = new HBox(10, refresh, tip);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(0, 12, 12, 12));
        VBox root = new VBox(new ScrollPane(g), bottom);
        if (gmScreenStage == null) {
            gmScreenStage = new Stage();
            gmScreenStage.initOwner(stage);
            gmScreenStage.setTitle("Escudo do mestre");
            com.vttcaseiro.view.Icons.applyTo(gmScreenStage);
        }
        gmScreenStage.setScene(new Scene(root, 1000, Math.min(700, 140 + 34 * Math.max(1, sheets.size()))));
        gmScreenStage.show();
        gmScreenStage.toFront();
    }

    private String conditionName(String key) {
        try {
            return com.vttcaseiro.model.Condition.valueOf(key).label();
        } catch (RuntimeException ex) {
            return key;
        }
    }

    // ---------------- copiar e colar tokens (Ctrl+C / Ctrl+V)

    private final List<TokenModel> tokenClipboard = new ArrayList<>();

    /** Copia os tokens escolhidos que eu controlo (o mestre copia qualquer um). */
    void copyTokens() {
        List<TokenModel> mine = mapView.getSelectedTokens().stream().filter(table::canControl).toList();
        if (mine.isEmpty()) {
            logEntries.add(LogEntry.system("Escolha um token (ou vários, com Shift) pra copiar."));
            return;
        }
        tokenClipboard.clear();
        for (TokenModel t : mine) tokenClipboard.add(com.vttcaseiro.service.UndoService.copy(t));
        logEntries.add(LogEntry.system(mine.size() == 1 ? mine.get(0).getName() + " copiado. Ctrl+V cola."
                : mine.size() + " tokens copiados. Ctrl+V cola."));
    }

    /**
     * Cola os tokens copiados uma casa ao lado (cada Ctrl+V cria mais), com numero no nome ("Goblin 2", "Goblin 3").
     * So o mestre cola (jogador criaria token a vontade); offline sempre.
     */
    void pasteTokens() {
        if (tokenClipboard.isEmpty()) return;
        if (networkService.isConnected() && !networkService.isGm()) {
            showError("Só o mestre cola tokens.");
            return;
        }
        double cell = Math.max(10, sceneService.getScene().getGrid().getCellSize());
        List<TokenModel> pasted = new ArrayList<>();
        for (TokenModel src : tokenClipboard) {
            TokenModel t = com.vttcaseiro.service.UndoService.copy(src);
            t.regenerateId();
            t.setName(nextCopyName(src.getName()));
            t.setX(src.getX() + cell * src.getWidthCells());
            t.setInitiative(null);
            src.setX(t.getX()); // o proximo Ctrl+V vai mais uma casa pro lado
            addTokenToScene(t);
            pasted.add(t);
        }
        logEntries.add(LogEntry.system(pasted.size() == 1 ? "Colado: " + pasted.get(0).getName() + "."
                : pasted.size() + " tokens colados."));
    }

    /** "Goblin" -> "Goblin 2"; "Goblin 2" -> o proximo numero livre na cena. */
    String nextCopyName(String name) {
        String base = name == null ? "Token" : name.replaceAll("\\s+\\d+$", "").strip();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (TokenModel t : sceneService.getScene().getTokens()) if (t.getName() != null) used.add(t.getName().strip());
        int n = 2;
        while (used.contains(base + " " + n)) n++;
        return base + " " + n;
    }

    private void addTokenToScene(TokenModel token) {
        if (preparing() && !undoing) token.setHiddenFromPlayers(true);
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
        // Modelo da ficha: D&D 5e, Tormenta20, Ordem Paranormal ou um modelo de PDF editavel da mesa.
        List<com.vttcaseiro.character.GameSystem> systems = com.vttcaseiro.character.GameSystem.all();
        Object model = chooseModel(com.vttcaseiro.character.SheetTemplate.CHARACTER, new ArrayList<>(systems), "Nova ficha");
        if (model == null) return;
        if (model instanceof com.vttcaseiro.character.SheetTemplate t) {
            createTemplateCharacter(name.get(), t);
            return;
        }
        Optional<com.vttcaseiro.character.GameSystem> system = Optional.of((com.vttcaseiro.character.GameSystem) model);
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

    // ---------------- cofre de personagens (fichas do jogador, fora das mesas)

    private final com.vttcaseiro.library.CharacterVault vault = com.vttcaseiro.library.CharacterVault.mine();

    /** Guarda a ficha no cofre deste computador (com retrato e imagens do modelo). */
    void saveToVault(CharacterSheet sheet) {
        try {
            boolean had = vault.contains(sheet.getId());
            vault.save(sheet, imageAssetService::readBytes);
            logEntries.add(LogEntry.system(sheet.getName() + (had ? " atualizada" : " guardada") + " no seu cofre. "
                    + "Em outra mesa: Fichas > Mais > Trazer do meu cofre."));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra guardar no cofre: " + ex.getMessage());
        }
    }

    /** A ficha que ja esta no cofre acompanha cada salvamento (o personagem evolui junto em todas as mesas). */
    private void updateVaultCopy(CharacterSheet sheet) {
        try {
            if (vault.contains(sheet.getId())) vault.save(sheet, imageAssetService::readBytes);
        } catch (IOException | RuntimeException ignored) {
            // cofre e um extra: falhar aqui nao atrapalha salvar na mesa
        }
    }

    /** Lista do cofre: trazer uma ficha pra esta mesa (online, como ficha sua) ou apagar do cofre. */
    void onBringFromVault() {
        List<com.vttcaseiro.library.CharacterVault.Entry> entries = vault.list();
        if (entries.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Seu cofre está vazio.\n\nAbra uma ficha e use Fichas > Mais > "
                    + "\"Guardar ficha aberta no meu cofre\" (ou botão direito na ficha > Guardar no meu cofre).");
            a.initOwner(stage);
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        ListView<com.vttcaseiro.library.CharacterVault.Entry> list = new ListView<>(
                javafx.collections.FXCollections.observableArrayList(entries));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.library.CharacterVault.Entry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : e.name() + "  ·  " + e.summary());
            }
        });
        list.setPrefSize(380, 260);
        list.getSelectionModel().selectFirst();
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Meu cofre de personagens");
        d.setHeaderText("Suas fichas guardadas neste computador. Trazer copia a ficha pra esta mesa.");
        javafx.scene.control.ButtonType bring = new javafx.scene.control.ButtonType("Trazer pra esta mesa",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType remove = new javafx.scene.control.ButtonType("Apagar do cofre",
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(bring, remove, javafx.scene.control.ButtonType.CANCEL);
        d.getDialogPane().setContent(list);
        Optional<javafx.scene.control.ButtonType> r = d.showAndWait();
        com.vttcaseiro.library.CharacterVault.Entry chosen = list.getSelectionModel().getSelectedItem();
        if (r.isEmpty() || chosen == null) return;
        if (r.get() == remove) {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Apagar \"" + chosen.name() + "\" do seu cofre? As mesas "
                    + "onde ela já está continuam com a ficha.");
            c.initOwner(stage);
            c.setHeaderText(null);
            if (c.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
            try {
                vault.delete(chosen);
            } catch (IOException ex) {
                showError("Não deu pra apagar: " + ex.getMessage());
            }
            return;
        }
        if (r.get() == bring) bringFromVault(chosen);
    }

    void bringFromVault(com.vttcaseiro.library.CharacterVault.Entry chosen) {
        try {
            java.util.Map<String, byte[]> images = new java.util.LinkedHashMap<>();
            CharacterSheet sheet = vault.load(chosen, images);
            for (var img : images.entrySet()) imageAssetService.storeBytes(img.getValue(), img.getKey());
            if (characterService.findById(sheet.getId()).isPresent()) {
                Alert c = new Alert(Alert.AlertType.CONFIRMATION, sheet.getName() + " já está nesta mesa. Trocar pela "
                        + "versão do cofre?");
                c.initOwner(stage);
                c.setHeaderText(null);
                if (c.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
            }
            if (networkService.isConnected() && !networkService.isGm()) {
                sheet.setOwnerPlayerId(networkService.getPlayerId()); // na mesa dos outros, a ficha e sua
                if (sheet.getPlayerName() == null || sheet.getPlayerName().isBlank()) sheet.setPlayerName(networkService.getPlayerName());
            }
            characterService.save(sheet);
            if (networkService.isConnected()) networkService.sendCharacterSync(sheet);
            refreshCharacterList();
            CharacterSheetView open = openSheetViews.get(sheet.getId());
            if (open != null) open.reload(sheet);
            else openSheetView(sheet);
            logEntries.add(LogEntry.system(sheet.getName() + " chegou do seu cofre."));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra trazer do cofre: " + ex.getMessage());
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

    // ---------------- apagar / renomear / duplicar (fichas, monstros, cenas)

    private Optional<String> askNewName(String title, String current) {
        TextInputDialog d = new TextInputDialog(current);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText("Nome:");
        return d.showAndWait().map(String::strip).filter(x -> !x.isEmpty());
    }

    /** "Nome 2", "Nome 3"... o primeiro que nao esta na lista (parenteses e acentos viram "_" no arquivo). */
    private static String freeName(String name, List<String> taken) {
        int n = 2;
        while (taken.contains(com.vttcaseiro.repository.MesaRepository.fileNameOf(name + " " + n))) n++;
        return name + " " + n;
    }

    private boolean confirmDelete(String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        a.initOwner(stage);
        a.setTitle("Apagar");
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isPresent();
    }

    private javafx.scene.control.ContextMenu characterMenu(String name) {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Abrir");
        open.setOnAction(e -> onOpenCharacterFromMesa(name));
        javafx.scene.control.MenuItem ren = new javafx.scene.control.MenuItem("Renomear... (F2)");
        ren.setOnAction(e -> onRenameCharacter(name));
        javafx.scene.control.MenuItem dup = new javafx.scene.control.MenuItem("Duplicar...");
        dup.setOnAction(e -> onDuplicateCharacter(name));
        javafx.scene.control.MenuItem del = new javafx.scene.control.MenuItem("Apagar... (Delete)");
        del.setOnAction(e -> onDeleteCharacter(name));
        javafx.scene.control.MenuItem vaultItem = new javafx.scene.control.MenuItem("Guardar no meu cofre");
        vaultItem.setOnAction(e -> {
            try {
                CharacterSheet loaded = characterService.open(name);
                saveToVault(openSheetViews.containsKey(loaded.getId()) ? openSheetViews.get(loaded.getId()).getSheet() : loaded);
            } catch (IOException | RuntimeException ex) {
                showError("Não deu pra abrir a ficha: " + ex.getMessage());
            }
        });
        m.getItems().addAll(open, ren, dup, vaultItem);
        if (networkService.isGm() || !networkService.isConnected()) {
            boolean hidden = false;
            try {
                CharacterSheetView v = openViewFor(name);
                hidden = (v != null ? v.getSheet() : characterService.open(name)).isHiddenFromPlayers();
            } catch (IOException | RuntimeException ignored) {
                // ficha ilegivel: o item so aparece como "esconder"
            }
            boolean now = hidden;
            javafx.scene.control.MenuItem vis = new javafx.scene.control.MenuItem(
                    now ? "Mostrar aos jogadores" : "Esconder dos jogadores (só o mestre vê)");
            vis.setOnAction(e -> onSetCharacterHidden(name, !now));
            m.getItems().add(vis);
        }
        m.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), del);
        return m;
    }

    private void onSetCharacterHidden(String name, boolean hidden) {
        try {
            CharacterSheetView v = openViewFor(name);
            CharacterSheet sheet = v != null ? v.getSheet() : characterService.open(name);
            sheet.setHiddenFromPlayers(hidden);
            characterService.save(sheet);
            networkService.sendCharacterSync(sheet);
            logEntries.add(LogEntry.system(hidden
                    ? "Ficha escondida: \"" + sheet.getName() + "\" não vai mais pros jogadores (só pro dono, se tiver)."
                    : "Ficha visível aos jogadores: " + sheet.getName()));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra mudar: " + ex.getMessage());
        }
    }

    /** A ficha aberta numa janela com esse nome de arquivo (ou null). */
    private CharacterSheetView openViewFor(String fileName) {
        for (CharacterSheetView v : openSheetViews.values()) {
            if (fileName.equals(com.vttcaseiro.repository.MesaRepository.fileNameOf(v.getSheet().getName()))) return v;
        }
        return null;
    }

    private void onRenameCharacter(String name) {
        CharacterSheetView view = openViewFor(name);
        String current = view != null ? view.getSheet().getName() : name;
        askNewName("Renomear ficha", current).ifPresent(n -> {
            try {
                CharacterSheet sheet = characterService.rename(name, n, view != null ? view.getSheet() : null);
                if (view != null) view.reload(sheet);
                refreshCharacterList();
                networkService.sendCharacterSync(sheet);
                logEntries.add(LogEntry.system("Ficha renomeada: " + current + " → " + sheet.getName()));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra renomear: " + ex.getMessage());
            }
        });
    }

    private void onDuplicateCharacter(String name) {
        askNewName("Duplicar ficha", freeName(name, characterService.list())).ifPresent(n -> {
            try {
                CharacterSheet copy = characterService.duplicate(name, n);
                refreshCharacterList();
                logEntries.add(LogEntry.system("Ficha duplicada: " + copy.getName()));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra duplicar: " + ex.getMessage());
            }
        });
    }

    private void onDeleteCharacter(String name) {
        if (!confirmDelete("Apagar a ficha \"" + name + "\" desta mesa?\n\nNão dá pra desfazer (os backups "
                + "automáticos da mesa ainda têm a ficha). Tokens dela continuam no mapa.")) return;
        try {
            CharacterSheetView view = openViewFor(name);
            if (view != null) {
                openSheetViews.values().remove(view);
                view.close();
            }
            characterService.delete(name);
            if (lastOpenedCharacter != null
                    && name.equals(com.vttcaseiro.repository.MesaRepository.fileNameOf(lastOpenedCharacter.getName()))) {
                lastOpenedCharacter = null;
            }
            refreshCharacterList();
            logEntries.add(LogEntry.system("Ficha apagada: " + name));
        } catch (IOException ex) {
            showError("Não deu pra apagar: " + ex.getMessage());
        }
    }

    private javafx.scene.control.ContextMenu mobMenu(String name) {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Abrir ficha");
        open.setOnAction(e -> onOpenMobFromMesa(name));
        javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Editar...");
        edit.setOnAction(e -> onEditMob(name));
        javafx.scene.control.MenuItem dup = new javafx.scene.control.MenuItem("Duplicar (clonar)...");
        dup.setOnAction(e -> onDuplicateMob(name));
        javafx.scene.control.MenuItem ren = new javafx.scene.control.MenuItem("Renomear... (F2)");
        ren.setOnAction(e -> onRenameMob(name));
        javafx.scene.control.MenuItem place = new javafx.scene.control.MenuItem("Colocar no mapa");
        place.setOnAction(e -> onPlaceSelectedMob());
        javafx.scene.control.MenuItem del = new javafx.scene.control.MenuItem("Apagar... (Delete)");
        del.setOnAction(e -> onDeleteMob(name));
        m.getItems().addAll(open, edit, dup, ren, place);
        boolean hidden = false;
        try {
            hidden = mobService.open(name).isHiddenFromPlayers();
        } catch (IOException | RuntimeException ignored) {
            // arquivo ilegivel
        }
        boolean now = hidden;
        javafx.scene.control.MenuItem vis = new javafx.scene.control.MenuItem(
                now ? "Mostrar a ficha aos jogadores" : "Esconder a ficha dos jogadores (só o mestre vê)");
        vis.setOnAction(e -> {
            try {
                MobSheet mob = mobService.open(name);
                mob.setHiddenFromPlayers(!now);
                mobService.save(mob);
                networkService.sendMobSync(mob);
                logEntries.add(LogEntry.system((!now ? "Ficha do monstro escondida dos jogadores: "
                        : "Ficha do monstro visível aos jogadores: ") + mob.getName()));
            } catch (IOException | RuntimeException ex) {
                showError("Não deu pra mudar: " + ex.getMessage());
            }
        });
        m.getItems().addAll(vis, new javafx.scene.control.SeparatorMenuItem(), del);
        return m;
    }

    private void refreshMobCount() {
        if (mobCountLabel != null) mobCountLabel.setText(mobService.list().size() + " monstro(s) nesta mesa.");
    }

    private void onNewMob() {
        Object model = chooseModel(com.vttcaseiro.character.SheetTemplate.MOB, List.of(MOB_EDITOR), "Novo monstro / PdM");
        if (model == null) return;
        if (model instanceof com.vttcaseiro.character.SheetTemplate t) {
            createTemplateMob(t);
            return;
        }
        MobSheet mob = new MobSheet("Novo monstro");
        if (!com.vttcaseiro.view.MobEditor.edit(stage, mob, "Novo monstro / NPC")) return;
        if (mobService.list().contains(com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName()))) {
            mob.setName(mobService.freeCopyName(mob.getName()));
        }
        try {
            mobService.save(mob);
            refreshMobList();
            refreshMobCount();
            mobListView.getSelectionModel().select(com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName()));
            logEntries.add(LogEntry.system("Monstro criado: " + mob.getName()));
            openMobView(mob, null);
        } catch (IOException ex) {
            showError("Não deu pra salvar: " + ex.getMessage());
        }
    }

    private void onEditMob(String name) {
        try {
            MobSheet mob = mobService.open(name);
            String before = mob.getName();
            if (!com.vttcaseiro.view.MobEditor.edit(stage, mob, "Editar " + before)) return;
            String target = com.vttcaseiro.repository.MesaRepository.fileNameOf(mob.getName());
            if (!target.equals(name) && mobService.list().contains(target)) {
                showError("Já existe um monstro chamado \"" + target + "\". Escolha outro nome.");
                return;
            }
            mobService.save(mob);
            if (!target.equals(name)) mesaService.getRepository().deleteMob(name);
            refreshMobList();
            refreshMobCount();
            logEntries.add(LogEntry.system("Monstro editado: " + mob.getName()));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra editar: " + ex.getMessage());
        }
    }

    private void onDuplicateMob(String name) {
        askNewName("Duplicar monstro", mobService.freeCopyName(name)).ifPresent(n -> {
            try {
                MobSheet copy = mobService.duplicate(name, n);
                refreshMobList();
                refreshMobCount();
                mobListView.getSelectionModel().select(com.vttcaseiro.repository.MesaRepository.fileNameOf(copy.getName()));
                logEntries.add(LogEntry.system("Monstro duplicado: " + copy.getName() + " (botão direito > Editar pra mudar)"));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra duplicar: " + ex.getMessage());
            }
        });
    }

    private void onRenameMob(String name) {
        askNewName("Renomear monstro", name).ifPresent(n -> {
            try {
                MobSheet mob = mobService.rename(name, n);
                refreshMobList();
                logEntries.add(LogEntry.system("Monstro renomeado: " + name + " → " + mob.getName()));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra renomear: " + ex.getMessage());
            }
        });
    }

    private void onDeleteMob(String name) {
        if (!confirmDelete("Apagar o monstro \"" + name + "\" desta mesa?\n\nNão dá pra desfazer. Tokens dele que já "
                + "estão no mapa continuam.")) return;
        try {
            mobService.delete(name);
            refreshMobList();
            refreshMobCount();
            logEntries.add(LogEntry.system("Monstro apagado: " + name));
        } catch (IOException ex) {
            showError("Não deu pra apagar: " + ex.getMessage());
        }
    }

    private void onRenameScene(String name) {
        askNewName("Renomear cena", name).ifPresent(n -> {
            try {
                String target = mesaService.renameMap(name, n);
                sceneFolders.renameScene(name, target);
                if (name.equals(currentMapName)) {
                    currentMapName = target;
                    sceneService.getScene().setName(n);
                    if (currentSceneLabel != null) currentSceneLabel.setText("Cena atual: " + target);
                }
                if (name.equals(sceneToReturnTo)) sceneToReturnTo = target;
                saveSceneFolders();
                logEntries.add(LogEntry.system("Cena renomeada: " + name + " → " + target));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra renomear: " + ex.getMessage());
            }
        });
    }

    private void onDuplicateScene(String name) {
        askNewName("Duplicar cena", freeName(name, mesaService.listMaps())).ifPresent(n -> {
            try {
                String target = mesaService.duplicateMap(name, n);
                String folder = sceneFolders.folderOf(name);
                if (!folder.isEmpty()) sceneFolders.moveScene(target, folder);
                saveSceneFolders();
                logEntries.add(LogEntry.system("Cena duplicada: " + target));
            } catch (IllegalArgumentException | IOException ex) {
                showError("Não deu pra duplicar: " + ex.getMessage());
            }
        });
    }

    private void onDeleteScene(String name) {
        boolean current = name.equals(currentMapName);
        if (!confirmDelete("Apagar a cena \"" + name + "\"?\n\nNão dá pra desfazer (os backups automáticos da mesa "
                + "ainda têm ela)." + (current ? "\nEla é a cena aberta agora: continua na tela até você trocar, "
                + "mas não fica mais salva." : ""))) return;
        try {
            mesaService.deleteMap(name);
            sceneFolders.moveScene(name, ""); // tira da pasta
            if (current) {
                currentMapName = null;
                if (currentSceneLabel != null) currentSceneLabel.setText("Cena atual: (não salva)");
            }
            if (name.equals(sceneToReturnTo)) {
                sceneToReturnTo = null;
                refreshReturnButton();
            }
            saveSceneFolders();
            logEntries.add(LogEntry.system("Cena apagada: " + name));
        } catch (IOException ex) {
            showError("Não deu pra apagar: " + ex.getMessage());
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
        if (partyCoinsLabel != null) {
            partyCoinsLabel.setText("Moedas no baú: " + com.vttcaseiro.service.CoinSplit.describe(
                    partyService.getParty().getSharedInventory().getMoney()));
        }
    }

    private Label partyCoinsLabel;
    private List<javafx.scene.Node> partyXpControls = List.of();

    /** Fichas dos membros do grupo (as abertas primeiro, que sao as vivas). */
    List<CharacterSheet> partySheetsForXp() {
        List<CharacterSheet> out = new ArrayList<>();
        for (String id : partyService.getParty().getCharacterIds()) {
            Optional<CharacterSheet> s = characterService.findOpen(id);
            if (s.isEmpty()) s = characterService.findById(id);
            s.ifPresent(out::add);
        }
        if (out.isEmpty()) {
            // sem grupo montado: as fichas dos tokens de jogador no mapa
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (TokenModel t : sceneService.getScene().getTokens()) {
                if (t.getOwnerId() == null || t.getOwnerId().isBlank()) continue;
                table.linkedSheet(t).ifPresent(s -> {
                    if (seen.add(s.getId())) out.add(s);
                });
            }
        }
        return out;
    }

    /** Divide as moedas do bau igualmente entre os membros (a sobra fica no bau). */
    void onSplitPartyCoins() {
        if (!networkService.isGm()) {
            showError("Só o mestre divide as moedas do baú.");
            return;
        }
        List<CharacterSheet> members = new ArrayList<>();
        for (String id : partyService.getParty().getCharacterIds()) {
            Optional<CharacterSheet> s = characterService.findOpen(id);
            if (s.isEmpty()) s = characterService.findById(id);
            s.ifPresent(members::add);
        }
        if (members.isEmpty()) {
            showError("Ninguém no grupo ainda (Membros, acima).");
            return;
        }
        String r = com.vttcaseiro.service.CoinSplit.split(partyService.getParty().getSharedInventory().getMoney(),
                members.stream().map(m -> m.getInventory().getMoney()).toList());
        if (r.isEmpty()) {
            showError("Não tem moeda no baú pra dividir (ou dá menos de uma pra cada).");
            return;
        }
        for (CharacterSheet m : members) sheetEdited(m);
        refreshPartyInventoryList();
        savePartyAndSync("Moedas do baú divididas entre " + members.size() + ": " + r + ".");
        announce("Moedas do baú divididas entre " + members.size() + ": " + r + ".");
    }

    /** XP pro grupo: valor pra cada um ou dividido; avisa quem pode subir de nivel e anota na Campanha. */
    void onGiveXp() {
        List<CharacterSheet> sheets = partySheetsForXp();
        if (sheets.isEmpty()) {
            showError("Ninguém pra receber XP: monte o grupo (Party > Membros) ou ponha os tokens dos jogadores no mapa.");
            return;
        }
        int monsterXp = 0;
        for (TokenModel t : sceneService.getScene().getTokens()) {
            if (t.getMobSheetId() == null) continue;
            monsterXp += mobService.findById(t.getMobSheetId()).map(MobSheet::getExperiencePoints).orElse(0);
        }
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Dar XP");
        d.setHeaderText(null);
        javafx.scene.control.Spinner<Integer> amount = new javafx.scene.control.Spinner<>(0, 1_000_000, Math.max(0, monsterXp), 50);
        amount.setEditable(true);
        amount.setPrefWidth(120);
        javafx.scene.control.ToggleGroup how = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton split = new javafx.scene.control.RadioButton("Dividir entre os escolhidos");
        javafx.scene.control.RadioButton each = new javafx.scene.control.RadioButton("Cada um recebe esse valor");
        split.setToggleGroup(how);
        each.setToggleGroup(how);
        split.setSelected(true);
        List<CheckBox> boxes = new ArrayList<>();
        javafx.scene.layout.FlowPane who = new javafx.scene.layout.FlowPane(10, 6);
        for (CharacterSheet s : sheets) {
            CheckBox cb = new CheckBox(s.getName() + " (" + s.getExperiencePoints() + " XP)");
            cb.setSelected(true);
            boxes.add(cb);
            who.getChildren().add(cb);
        }
        TextField reason = new TextField();
        reason.setPromptText("Motivo (ex.: Covil dos goblins)");
        CheckBox record = new CheckBox("Anotar também na Campanha > Jogadores (pelo nome do personagem)");
        record.setSelected(true);
        Label monsters = new Label(monsterXp > 0 ? "Começa com a XP dos monstros no mapa: " + monsterXp + "." : "");
        monsters.getStyleClass().add("label-muted");
        VBox content = new VBox(8, new HBox(8, new Label("XP"), amount), monsters, new HBox(12, split, each),
                new Label("Pra quem"), who, reason, record);
        content.setPrefWidth(480);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
        List<CharacterSheet> chosen = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) if (boxes.get(i).isSelected()) chosen.add(sheets.get(i));
        int value = amount.getValue() == null ? 0 : amount.getValue();
        giveXp(chosen, value, split.isSelected(), reason.getText(), record.isSelected());
    }

    /** Aplica a XP (fichas, chat e, se pedido, a Campanha). */
    void giveXp(List<CharacterSheet> chosen, int value, boolean split, String reason, boolean record) {
        List<com.vttcaseiro.service.XpService.Award> awards = com.vttcaseiro.service.XpService.award(chosen, value, split);
        if (awards.isEmpty()) return;
        for (CharacterSheet s : chosen) sheetEdited(s);
        if (record && campaign != null) {
            boolean any = false;
            for (com.vttcaseiro.service.XpService.Award a : awards) {
                for (com.vttcaseiro.model.Campaign.PlayerRecord p : campaign.getPlayers()) {
                    if (p.character != null && p.character.strip().equalsIgnoreCase(a.name().strip())) {
                        p.xp.add(new com.vttcaseiro.model.Campaign.Entry(com.vttcaseiro.view.CampaignView.todayText(),
                                a.gained(), reason == null || reason.isBlank() ? "XP" : reason.strip()));
                        any = true;
                    }
                }
            }
            if (any) {
                pushCampaign();
                if (campaignView != null && campaignView.isShowing()) campaignView.reload(campaign);
            }
        }
        announce(com.vttcaseiro.service.XpService.summary(awards, reason));
    }

    /** Quem descansa: as fichas do mapa e do grupo, todas marcadas (vazio = cancelou). */
    Optional<List<CharacterSheet>> chooseRestSheets(boolean longRest) {
        java.util.LinkedHashMap<String, CharacterSheet> all = new java.util.LinkedHashMap<>();
        for (CharacterSheet s : table.sheetsOnScene()) all.putIfAbsent(s.getId(), s);
        for (CharacterSheet s : partySheets()) all.putIfAbsent(s.getId(), s);
        if (all.isEmpty()) {
            showError("Ninguém pra descansar: ponha os tokens no mapa ou monte o grupo (Party > Membros).");
            return Optional.empty();
        }
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle(longRest ? "Descanso longo" : "Descanso curto");
        d.setHeaderText(longRest ? "Quem faz o descanso longo?" : "Quem faz o descanso curto?");
        List<CheckBox> boxes = new ArrayList<>();
        VBox list = new VBox(6);
        for (CharacterSheet s : all.values()) {
            String hp = s.getHpCurrent() == null || s.getHpMax() == null ? "" : "  (" + s.getHpCurrent() + "/" + s.getHpMax() + " PV, "
                    + s.hitDiceRemaining() + " dados de vida)";
            CheckBox cb = new CheckBox(s.getName() + hp);
            cb.setSelected(true);
            boxes.add(cb);
            list.getChildren().add(cb);
        }
        Label hint = new Label(longRest ? "PV, dados de vida, espaços de magia, recursos e -1 de exaustão. O chat mostra o que cada um recuperou."
                : "Gasta dados de vida enquanto faltar PV e recupera os recursos de descanso curto.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        VBox content = new VBox(10, list, hint);
        content.setPrefWidth(420);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return Optional.empty();
        List<CharacterSheet> chosen = new ArrayList<>();
        int i = 0;
        for (CharacterSheet s : all.values()) if (boxes.get(i++).isSelected()) chosen.add(s);
        return chosen.isEmpty() ? Optional.empty() : Optional.of(chosen);
    }

    /** Marco: o grupo sobe de nivel (sem XP). */
    void onMilestone() {
        List<CharacterSheet> sheets = partySheetsForXp();
        int next = sheets.stream().mapToInt(CharacterSheet::getLevel).max().orElse(0) + 1;
        javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog("");
        d.initOwner(stage);
        d.setTitle("Subir por marco");
        d.setHeaderText(sheets.isEmpty() ? "O grupo sobe de nível." : "O grupo sobe pro nível " + Math.min(20, next) + ".");
        d.setContentText("Motivo (opcional):");
        d.showAndWait().ifPresent(r -> announce("Marco! " + (sheets.isEmpty() ? "O grupo sobe de nível" : "O grupo sobe pro nível "
                + Math.min(20, next)) + (r.isBlank() ? "." : ": " + r.strip() + ".")
                + " Subam o nível nas fichas."));
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
    // ---------------- barra de cima: fila de cenas e jogadores

    private final HBox sceneStrip = new HBox(4);
    private final HBox playersStrip = new HBox(-6);
    /** Quantas cenas da fila aparecem na barra (depois da atual). */
    static final int SCENE_STRIP_MAX = 4;

    /**
     * A fila de cenas da sessao na barra de cima (so o mestre): a atual (com o olho: os jogadores veem ou nao) e as
     * proximas da fila. Clicar abre pra todos; botao direito: abrir escondida, tirar da fila. "▶" passa pra proxima.
     */
    void refreshSceneStrip() {
        sceneStrip.getChildren().clear();
        boolean gm = networkService.isGm();
        sceneStrip.setVisible(gm);
        sceneStrip.setManaged(gm);
        if (!gm || sceneService == null || sceneQueueStore == null) return;
        boolean hidden = sceneService.getScene().isHiddenFromPlayers();
        String currentName = currentMapName != null ? currentMapName
                : (sceneService.getScene().getName() == null || sceneService.getScene().getName().isBlank()
                ? "Cena sem nome" : sceneService.getScene().getName());
        if (sideViewing && networkService.isConnected()) {
            // olhando a cena de um jogador separado: deixa claro e da o caminho de volta
            List<String> there = new ArrayList<>();
            networkService.getPlayerScenes().forEach((p, k) -> { if (k.equals(currentMapName)) there.add(p); });
            Label side = new Label("Cena separada: " + currentName + (there.isEmpty() ? "" : " (" + String.join(", ", there) + ")"),
                    com.vttcaseiro.view.Icons.icon("party", 13));
            side.getStyleClass().addAll("scene-chip", "scene-chip-current", "scene-chip-side");
            side.setTooltip(new Tooltip("Você está vendo a cena de quem foi separado do grupo. O grupo continua na outra cena."));
            Button back = new Button("Voltar pro grupo");
            back.getStyleClass().add("button-flat");
            back.setOnAction(e -> {
                flushSceneAutosave();
                networkService.sendGmView(null);
            });
            sceneStrip.getChildren().addAll(side, back);
            return;
        }
        Label current = new Label(currentName, com.vttcaseiro.view.Icons.icon(hidden ? "hidden" : "eye", 13));
        current.getStyleClass().addAll("scene-chip", "scene-chip-current");
        if (hidden) current.getStyleClass().add("scene-chip-hidden");
        current.setTooltip(new Tooltip(hidden ? "Cena atual, escondida dos jogadores (eles veem o aviso)"
                : "Cena atual: é a que os jogadores estão vendo"));
        current.setOnMouseClicked(e -> {
            javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem toggle = new javafx.scene.control.MenuItem(hidden ? "Mostrar aos jogadores" : "Esconder dos jogadores");
            toggle.setOnAction(a -> setSceneHidden(!hidden));
            javafx.scene.control.MenuItem config = new javafx.scene.control.MenuItem("Configurar cena (grade, tamanho)...");
            config.setOnAction(a -> showSceneConfig());
            javafx.scene.control.MenuItem list = new javafx.scene.control.MenuItem("Ver em Mundo > Cenas");
            list.setOnAction(a -> openPanel("Mapa"));
            m.getItems().addAll(toggle, config, weatherMenu(), list);
            m.show(current, javafx.geometry.Side.BOTTOM, 0, 4);
        });
        sceneStrip.getChildren().add(current);
        List<String> queue = sceneQueueStore.load();
        int at = currentMapName == null ? -1 : queue.indexOf(currentMapName);
        List<String> upcoming = new ArrayList<>();
        for (int i = at + 1; i < queue.size() && upcoming.size() < SCENE_STRIP_MAX; i++) upcoming.add(queue.get(i));
        for (String name : upcoming) {
            Label chip = new Label(name);
            chip.getStyleClass().add("scene-chip");
            chip.setTooltip(new Tooltip("Próxima na fila. Clique: abrir pra todos. Botão direito: mais opções."));
            chip.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
                    javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Abrir pra todos");
                    open.setOnAction(a -> { onOpenMapFromMesa(name); refreshSceneStrip(); });
                    javafx.scene.control.MenuItem prep = new javafx.scene.control.MenuItem("Abrir escondida (preparar)");
                    prep.setOnAction(a -> { onOpenMapHidden(name); refreshSceneStrip(); });
                    javafx.scene.control.MenuItem drop = new javafx.scene.control.MenuItem("Tirar da fila");
                    drop.setOnAction(a -> {
                        List<String> q = sceneQueueStore.load();
                        q.remove(name);
                        sceneQueueStore.save(q);
                        refreshSceneStrip();
                    });
                    m.getItems().addAll(open, prep, drop);
                    m.show(chip, e.getScreenX(), e.getScreenY());
                } else {
                    onOpenMapFromMesa(name);
                    refreshSceneStrip();
                }
            });
            sceneStrip.getChildren().add(chip);
        }
        int rest = queue.size() - (at + 1) - upcoming.size();
        if (rest > 0) {
            Label more = new Label("+" + rest);
            more.getStyleClass().add("scene-more");
            sceneStrip.getChildren().add(more);
        }
        if (!upcoming.isEmpty()) {
            Button next = new Button(null, com.vttcaseiro.view.Icons.icon("next", 13));
            next.getStyleClass().addAll("button-flat", "scene-next");
            next.setTooltip(new Tooltip("Próxima cena: abre pra todos a seguinte da fila"));
            next.setOnAction(e -> {
                java.util.Optional<String> n = com.vttcaseiro.service.SceneQueue.next(sceneQueueStore.load(), currentMapName);
                n.ifPresent(this::onOpenMapFromMesa);
                refreshSceneStrip();
            });
            sceneStrip.getChildren().add(next);
        }
        Button add = new Button(null, com.vttcaseiro.view.Icons.icon("add", 13));
        add.getStyleClass().addAll("button-flat", "scene-next");
        add.setTooltip(new Tooltip(queue.isEmpty() ? "Montar a fila de cenas da sessão (Mundo > Cenas)"
                : "Pôr mais cenas na fila (Mundo > Cenas)"));
        add.setOnAction(e -> openPanel("Mapa"));
        sceneStrip.getChildren().add(add);
    }

    /** Bolinhas dos conectados, cada um com a sua cor (a mesma sempre pro mesmo nome). */
    void refreshPlayersStrip() {
        playersStrip.getChildren().clear();
        List<String> online = new ArrayList<>(networkService.getOnlinePlayers());
        boolean show = networkService.isConnected() && !online.isEmpty();
        playersStrip.setVisible(show);
        playersStrip.setManaged(show);
        if (!show) return;
        for (String who : online) {
            boolean gm = who.endsWith("(mestre)");
            String name = who.replaceAll("\\s*\\(mestre\\)$", "").trim();
            StackPane dot = new StackPane();
            dot.getStyleClass().add("player-dot");
            dot.setStyle("-fx-background-color: " + playerColor(name) + ";");
            Label initial = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            initial.getStyleClass().add("player-dot-initial");
            dot.getChildren().add(initial);
            if (gm) dot.getStyleClass().add("player-dot-gm");
            String away = gm ? null : networkService.getPlayerScenes().get(name);
            if (away != null) dot.getStyleClass().add("player-dot-away");
            Tooltip.install(dot, new Tooltip(away == null ? who : who + " -- separado, em: " + away));
            if (!gm && networkService.isGm()) {
                dot.setCursor(javafx.scene.Cursor.HAND);
                dot.setOnMouseClicked(e -> splitMenu(name, away).show(dot, javafx.geometry.Side.BOTTOM, 0, 4));
            }
            playersStrip.getChildren().add(dot);
        }
    }

    // ---------------- camera guiada pelo mestre

    /** Submenu "Clima no mapa": chuva, tempestade, neve, neblina, cinzas (pra todos). */
    javafx.scene.control.Menu weatherMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Clima no mapa");
        String[][] opts = {{"", "Nenhum"}, {"chuva", "Chuva"}, {"tempestade", "Tempestade (com raios)"}, {"neve", "Neve"},
                {"neblina", "Neblina"}, {"cinzas", "Cinzas e brasas"}};
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        String now = sceneService.getScene().getWeather();
        for (String[] o : opts) {
            javafx.scene.control.RadioMenuItem it = new javafx.scene.control.RadioMenuItem(o[1]);
            it.setToggleGroup(group);
            it.setSelected(o[0].equals(now));
            it.setOnAction(e -> setSceneWeather(o[0]));
            menu.getItems().add(it);
        }
        return menu;
    }

    /** Muda o clima da cena aberta: aqui, na TV e pra todos (vai com a cena). */
    void setSceneWeather(String kind) {
        sceneService.getScene().setWeather(kind);
        mapView.setWeather(sceneService.getScene().getWeather());
        if (tvView != null) tvView.setWeather(sceneService.getScene().getWeather());
        if (networkService.isGm() && networkService.isConnected()) networkService.sendSceneSwitch(sceneService.getScene());
        scheduleSceneAutosave();
        logEntries.add(LogEntry.system(kind.isEmpty() ? "Clima do mapa desligado." : "Clima no mapa: " + kind + "."));
    }

    // ---------------- tela da mesa pra TV (jogo presencial)

    private Stage tvStage;
    private MapView tvView;
    private final javafx.animation.PauseTransition tvRefresh = new javafx.animation.PauseTransition(javafx.util.Duration.millis(60));
    private boolean tvListening;

    /**
     * Segunda janela com o mapa como os jogadores veem (visao do grupo, escuro, neblina, sem o oculto), seguindo a
     * camera do mestre. Arraste pra TV/segundo monitor; clique duplo (ou F11) liga a tela cheia.
     */
    void openTvWindow() {
        if (tvStage != null) {
            tvStage.toFront();
            return;
        }
        tvStage = new Stage();
        tvStage.setTitle("Customizr - Tela da mesa");
        com.vttcaseiro.view.Icons.applyTo(tvStage);
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #000000;");
        Scene sc = new Scene(root, 1280, 720);
        sc.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        tvStage.setScene(sc);
        buildTvView(root);
        sc.setOnKeyPressed(k -> {
            if (k.getCode() == javafx.scene.input.KeyCode.F11) tvStage.setFullScreen(!tvStage.isFullScreen());
        });
        root.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) tvStage.setFullScreen(!tvStage.isFullScreen());
        });
        tvStage.setFullScreenExitHint("Esc sai da tela cheia");
        tvStage.setOnHidden(e -> {
            if (tvView != null) tvView.release();
            tvView = null;
            tvStage = null;
        });
        if (!tvListening) {
            tvListening = true;
            tvRefresh.setOnFinished(e -> refreshTv());
            sceneService.addListener(new SceneService.SceneChangeListener() {
                @Override public void onTokenAdded(TokenModel t) { tvRefresh.playFromStart(); }
                @Override public void onTokenMoved(TokenModel t) { tvRefresh.playFromStart(); }
                @Override public void onTokenRemoved(TokenModel t) { tvRefresh.playFromStart(); }
                @Override public void onTokenUpdated(TokenModel t) { tvRefresh.playFromStart(); }
                @Override public void onFogChanged() { tvRefresh.playFromStart(); }
                @Override public void onWallsChanged() { tvRefresh.playFromStart(); }
                @Override public void onDrawingsChanged() { tvRefresh.playFromStart(); }
                @Override public void onPinsChanged() { tvRefresh.playFromStart(); }
                @Override public void onObjectsChanged() { tvRefresh.playFromStart(); }
                @Override public void onGridChanged() { tvRefresh.playFromStart(); }
                @Override public void onSceneReplaced(SceneModel s) {
                    Platform.runLater(() -> { if (tvStage != null) buildTvView((StackPane) tvStage.getScene().getRoot()); });
                }
            });
        }
        tvStage.show();
        Platform.runLater(this::syncTvView);
        logEntries.add(LogEntry.system("Tela da mesa aberta: arraste a janela pra TV e dê clique duplo pra tela cheia. "
                + "Ela mostra o que os jogadores veem e segue a sua câmera."));
    }

    private void buildTvView(StackPane root) {
        if (tvView != null) tvView.release();
        tvView = new MapView(sceneService.getScene(), imageAssetService);
        tvView.setPlayerScreen(true);
        tvView.setCameraLocked(true); // quem manda na camera e o mestre
        root.getChildren().setAll(tvView);
        Platform.runLater(this::syncTvView);
    }

    private void refreshTv() {
        if (tvView == null) return;
        tvView.rebuildTokens();
        tvView.redrawWalls();
        tvView.redrawDrawings();
        tvView.redrawPins();
        tvView.redrawObjects();
        tvView.redrawZones();
        tvView.redrawFog();
        tvView.updateVision();
    }

    /** A TV mostra o mesmo pedaco do mapa que o mestre esta olhando. */
    private void syncTvView() {
        if (tvView == null || mapView == null) return;
        double[] c = mapView.viewCenter();
        tvView.applyView(c[0], c[1], mapView.getZoom());
    }

    private ToggleButton cameraLockButton;
    private final Label cameraBanner = new Label("O mestre está guiando a câmera");
    private final javafx.animation.PauseTransition cameraThrottle = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));

    /** Vista do mestre mudou: com a camera travada, manda (no maximo umas 8 vezes por segundo). */
    private void scheduleCameraSend() {
        if (cameraLockButton == null || !cameraLockButton.isSelected()) return;
        cameraThrottle.setOnFinished(e -> sendCameraNow());
        if (cameraThrottle.getStatus() != javafx.animation.Animation.Status.RUNNING) cameraThrottle.playFromStart();
    }

    private void sendCameraNow() {
        if (cameraLockButton == null || !networkService.isGm() || !networkService.isConnected()) return;
        double[] c = mapView.viewCenter();
        networkService.sendCameraLock(cameraLockButton.isSelected(), c[0], c[1], mapView.getZoom());
        cameraLockButton.getStyleClass().remove("camera-lock-on");
        if (cameraLockButton.isSelected()) cameraLockButton.getStyleClass().add("camera-lock-on");
    }

    // ---------------- barra de acoes do token escolhido

    private final HBox actionBar = new HBox(6);
    private String actionBarKey;
    /** Mais que isso na barra vai pro menu "Mais". */
    static final int ACTION_BAR_MAX = 8;

    /** Um token so escolhido, que eu controlo: os ataques e magias da ficha dele a um clique. */
    void refreshActionBar() {
        if (table == null || characterService == null || mobService == null) return; // mesa ainda carregando
        List<TokenModel> sel = mapView.getSelectedTokens();
        TokenModel t = sel.size() == 1 ? sel.get(0) : null;
        CharacterSheet sheet = null;
        MobSheet mob = null;
        if (t != null && table.canControl(t)) {
            if (t.getCharacterSheetId() != null) {
                sheet = characterService.findOpen(t.getCharacterSheetId())
                        .or(() -> characterService.findById(t.getCharacterSheetId())).orElse(null);
            } else if (t.getMobSheetId() != null && networkService.isGm()) {
                mob = mobService.findById(t.getMobSheetId()).orElse(null);
            }
        }
        List<Object[]> items = new ArrayList<>(); // {icone, texto, dica, acao(MouseEvent)}
        if (sheet != null && !sheet.usesTemplate()) {
            CharacterSheet s = sheet;
            for (com.vttcaseiro.character.Attack a : s.getAttacks()) {
                if (a.attackBonus().isEmpty() && a.damageExpression().isEmpty()) continue;
                String bonus = a.attackBonus().isPresent() ? " " + signed(a.attackBonus().getAsInt()) : "";
                items.add(new Object[]{"combat", a.getName() + bonus,
                        a.getName() + (a.getDamageAndType().isBlank() ? "" : ": " + a.getDamageAndType())
                                + " -- acerto e dano juntos (alvos marcados: compara com a CA)",
                        (java.util.function.Consumer<javafx.scene.input.MouseEvent>) e ->
                                sheetActions.attack(s, a, modeFrom(e), false)});
            }
            List<com.vttcaseiro.character.PreparedSpell> spells = new ArrayList<>(s.getSpells());
            spells.sort(java.util.Comparator.comparingInt(com.vttcaseiro.character.PreparedSpell::getLevel));
            for (com.vttcaseiro.character.PreparedSpell sp : spells) {
                if (sp.getDice().isBlank() && !sp.isSpellAttack() && sp.getSaveAbility().isBlank()) continue;
                items.add(new Object[]{"spark", sp.getName() + (sp.getLevel() > 0 ? " (" + sp.getLevel() + ")" : ""),
                        sp.getName() + (sp.getLevel() > 0 ? " -- gasta um espaço de nível " + sp.getLevel() + " ou maior" : " -- truque"),
                        (java.util.function.Consumer<javafx.scene.input.MouseEvent>) e -> {
                            if (com.vttcaseiro.view.CharacterSheetView.castSpell(s, sp, sheetActions, stage, false)) sheetEdited(s);
                        }});
            }
        } else if (mob != null && !mob.usesTemplate()) {
            MobSheetView.Actions acts = mobActionsFor(mob, t);
            String name = t.getName() != null ? t.getName() : mob.getName();
            for (com.vttcaseiro.character.CharacterTrait tr : mob.getActions()) {
                String desc = tr.getDescription() == null ? "" : tr.getDescription();
                var bonus = com.vttcaseiro.mob.MobAttack.attackBonus(desc);
                var dmg = com.vttcaseiro.mob.MobAttack.damage(desc);
                if (bonus.isEmpty() && dmg.isEmpty()) continue;
                String type = com.vttcaseiro.mob.MobAttack.damageType(desc);
                String typeText = type == null ? "" : " (" + com.vttcaseiro.character.DamageTypes.namePt(type) + ")";
                items.add(new Object[]{"combat", tr.getName() + (bonus.isPresent() ? " " + signed(bonus.getAsInt()) : ""),
                        tr.getName() + (dmg.isPresent() ? ": " + dmg.get() + typeText : "") + " -- rolagem escondida, como na ficha",
                        (java.util.function.Consumer<javafx.scene.input.MouseEvent>) e -> {
                            if (bonus.isPresent()) {
                                acts.attack(name + " - Ataque: " + tr.getName(), bonus.getAsInt(),
                                        name + " - Dano: " + tr.getName() + typeText, dmg.orElse(null), modeFrom(e), true);
                            } else {
                                acts.roll(name + " - Dano: " + tr.getName() + typeText, dmg.get(), true);
                            }
                        }});
            }
        }
        boolean show = t != null && (sheet != null || mob != null);
        StringBuilder key = new StringBuilder(show ? t.getId() : "");
        for (Object[] it : items) key.append('|').append(it[1]);
        if (java.util.Objects.equals(key.toString(), actionBarKey)) return;
        actionBarKey = key.toString();
        actionBar.getChildren().clear();
        actionBar.setVisible(show);
        actionBar.setManaged(show);
        if (!show) return;
        Label who = new Label(t.getName());
        who.getStyleClass().add("action-bar-name");
        actionBar.getChildren().add(who);
        javafx.scene.control.MenuButton more = new javafx.scene.control.MenuButton("Mais");
        more.getStyleClass().add("action-bar-button");
        for (int i = 0; i < items.size(); i++) {
            Object[] it = items.get(i);
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<javafx.scene.input.MouseEvent> act = (java.util.function.Consumer<javafx.scene.input.MouseEvent>) it[3];
            if (i < ACTION_BAR_MAX) {
                Button b = new Button((String) it[1], com.vttcaseiro.view.Icons.icon((String) it[0], 13));
                b.getStyleClass().add("action-bar-button");
                b.setTooltip(new Tooltip(it[2] + "\nShift+clique: vantagem · Ctrl+clique: desvantagem"));
                b.setOnMouseClicked(e -> {
                    if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) act.accept(e);
                });
                actionBar.getChildren().add(b);
            } else {
                javafx.scene.control.MenuItem mi = new javafx.scene.control.MenuItem((String) it[1]);
                mi.setOnAction(e -> act.accept(null));
                more.getItems().add(mi);
            }
        }
        if (!more.getItems().isEmpty()) actionBar.getChildren().add(more);
        if (items.isEmpty()) {
            Label none = new Label("Sem ataques na ficha");
            none.getStyleClass().add("label-muted");
            actionBar.getChildren().add(none);
        }
        Button init = new Button(null, com.vttcaseiro.view.Icons.icon("dice", 13));
        init.getStyleClass().addAll("action-bar-button", "action-bar-icon");
        init.setTooltip(new Tooltip("Rolar iniciativa"));
        init.setOnAction(e -> tokenActions.rollInitiative(t));
        Button open = new Button(null, com.vttcaseiro.view.Icons.icon("sheet", 13));
        open.getStyleClass().addAll("action-bar-button", "action-bar-icon");
        open.setTooltip(new Tooltip("Abrir a ficha"));
        open.setOnAction(e -> tokenActions.openSheet(t));
        actionBar.getChildren().addAll(init, open);
    }

    private static com.vttcaseiro.character.RollMode modeFrom(javafx.scene.input.MouseEvent e) {
        if (e == null) return com.vttcaseiro.character.RollMode.NORMAL;
        if (e.isShiftDown()) return com.vttcaseiro.character.RollMode.ADVANTAGE;
        if (e.isControlDown()) return com.vttcaseiro.character.RollMode.DISADVANTAGE;
        return com.vttcaseiro.character.RollMode.NORMAL;
    }

    private static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }

    /** A ficha mudou fora da janela dela (magia pela barra): salva, sincroniza e atualiza a janela aberta. */
    private void sheetEdited(CharacterSheet sheet) {
        sheetActions.sheetChanged(sheet);
        try {
            sheetActions.save(sheet);
        } catch (Exception ex) {
            logEntries.add(LogEntry.error("Não deu pra salvar a ficha: " + ex.getMessage()));
        }
        CharacterSheetView view = openSheetViews.get(sheet.getId());
        if (view != null) view.reload(sheet);
    }

    /** Area da magia: a que veio na ficha, ou a do compendio com o mesmo nome (fichas antigas). */
    com.vttcaseiro.service.SpellArea spellAreaOf(com.vttcaseiro.character.PreparedSpell spell) {
        com.vttcaseiro.service.SpellArea a = com.vttcaseiro.service.SpellArea.of(spell);
        if (a != null || compendiumService == null || spell.getName() == null || spell.getName().isBlank()) return a;
        try {
            for (com.vttcaseiro.compendium.CompendiumEntry e : compendiumService.search(spell.getName())) {
                if (!spell.getName().equalsIgnoreCase(e.getName())) continue;
                a = com.vttcaseiro.service.SpellArea.parse(e.getSpellRange());
                if (a == null) a = com.vttcaseiro.service.SpellArea.parse(e.getFullText());
                if (a != null) return a;
            }
        } catch (RuntimeException ignored) {
            // compendio vazio ou estragado: sem area
        }
        return null;
    }

    /** Mestre esta olhando uma cena separada (de um jogador sozinho), nao a do grupo. */
    private boolean sideViewing;
    /** Nome da cena do grupo (pra saber de onde um portal sai mesmo olhando uma cena separada). */
    private String groupMapName;

    /** Estou olhando a cena onde a zona disparou? (null = a do grupo) */
    private boolean viewingScene(String sceneKey) {
        return sceneKey == null ? !sideViewing : sideViewing && sceneKey.equals(currentMapName);
    }

    /**
     * Um token entrou numa zona (o servidor avisa; offline, o proprio programa). Armadilha: aviso com o botao de
     * rolar a salvaguarda. Portal: leva o jogador dono do token pra cena de destino, com o token. Aviso: o texto pro
     * mestre (e pro jogador, se marcado).
     */
    void onZoneEntered(com.vttcaseiro.model.MapZone zone, TokenModel token, String sceneKey, String owner) {
        if (!networkService.isGm()) return;
        boolean here = viewingScene(sceneKey);
        com.vttcaseiro.model.MapZone local = here ? sceneService.getScene().findZone(zone.getId()).orElse(null) : null;
        if (local != null && zone.isOnce()) {
            local.setTriggered(true);
            mapView.redrawZones();
            scheduleSceneAutosave();
        }
        TokenModel live = here ? sceneService.getScene().findToken(token.getId()).orElse(token) : token;
        String where = sceneKey == null ? "" : " (cena separada \"" + sceneKey + "\")";
        String who = live.getName() + (owner == null ? "" : " (" + owner + ")");
        if (zone.isTellPlayer() && owner != null && !zone.getText().isBlank()) {
            onChatInput("/w \"" + owner + "\" " + zone.getText());
        }
        switch (zone.getKind()) {
            case com.vttcaseiro.model.MapZone.PORTAL -> {
                String target = zone.getTargetScene();
                if (target == null) {
                    logEntries.add(LogEntry.system(who + " entrou no portal \"" + zone.label() + "\", que não leva a lugar nenhum (edite a zona)."));
                } else if (!networkService.isConnected()) {
                    logEntries.add(LogEntry.system(who + " entrou no portal pra \"" + target + "\". (Jogando sozinho: abra a cena quando quiser.)"));
                } else if (owner == null) {
                    logEntries.add(LogEntry.system(live.getName() + " entrou no portal pra \"" + target + "\" (token sem jogador: fica onde está)."));
                } else {
                    String from = sceneKey != null ? sceneKey : groupMapName;
                    sendPlayerThroughPortal(owner, target, live, from);
                }
            }
            case com.vttcaseiro.model.MapZone.NOTE -> logEntries.add(LogEntry.system("Aviso \"" + zone.label() + "\"" + where
                    + ": " + who + " entrou. " + zone.getText()));
            case com.vttcaseiro.model.MapZone.SOUND -> {
                com.vttcaseiro.model.AudioCommand cmd = zoneSound(zone);
                if (cmd != null) {
                    if (com.vttcaseiro.model.AudioCommand.MUSIC.equals(cmd.getChannel())) playlist.clear(); // a zona manda na musica agora
                    gmAudio(cmd);
                }
                logEntries.add(LogEntry.system("Som \"" + zone.label() + "\"" + where + ": " + who + " entrou"
                        + (cmd == null ? "." : cmd.isStop() ? " (parou o som)." : " (" + zone.getSoundName() + ").")
                        + (zone.getText().isBlank() ? "" : " " + zone.getText())));
            }
            default -> {
                // armadilha
                String detail = "Salvaguarda de " + zone.getAbility() + " CD " + zone.getDc()
                        + (zone.getDamage().isBlank() ? "" : ", " + zone.getDamage()
                        + (zone.getDamageType().isBlank() ? "" : " de " + zone.getDamageType())
                        + (zone.isHalfOnSave() ? " (metade se passar)" : ""));
                logEntries.add(LogEntry.system("Armadilha \"" + zone.label() + "\"" + where + ": " + who + " entrou. " + detail));
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.initOwner(stage);
                a.setTitle("Armadilha");
                a.setHeaderText(who + " entrou em \"" + zone.label() + "\"" + where);
                a.setContentText(detail + (zone.getText().isBlank() ? "" : "\n\n" + zone.getText())
                        + (here ? "" : "\n\nAbra essa cena pra rolar a salvaguarda pelo programa."));
                javafx.scene.control.ButtonType roll = new javafx.scene.control.ButtonType("Rolar salvaguarda",
                        javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
                javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("Só avisar",
                        javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
                if (here) a.getButtonTypes().setAll(roll, ok);
                else a.getButtonTypes().setAll(ok);
                a.setOnHidden(e -> {
                    if (a.getResult() == roll) table.trapSave(zone, live);
                });
                a.show(); // nao trava a mesa
            }
        }
    }

    /** O que a zona de som toca (null = nada: efeito sem som escolhido). */
    static com.vttcaseiro.model.AudioCommand zoneSound(com.vttcaseiro.model.MapZone zone) {
        String ch = zone.getSoundChannel();
        if (zone.getSoundId() == null) {
            return com.vttcaseiro.model.AudioCommand.SFX.equals(ch) ? null : com.vttcaseiro.model.AudioCommand.stop(ch);
        }
        return com.vttcaseiro.model.AudioCommand.play(ch, zone.getSoundId(), zone.getSoundName());
    }

    /** Portal: o jogador vai pra cena de destino e o token dele vai junto (aparece perto do portal de volta). */
    private void sendPlayerThroughPortal(String owner, String target, TokenModel token, String fromScene) {
        try {
            SceneModel scene = !sideViewing && target.equals(currentMapName) ? sceneService.getScene() : mesaService.loadMap(target);
            imageAssetService.migrateInlineImages(scene);
            TokenModel moving = com.vttcaseiro.service.UndoService.copy(token);
            double[] at = com.vttcaseiro.service.ZoneRules.arrivalPoint(scene, fromScene, moving);
            if (at != null) {
                moving.setX(at[0]);
                moving.setY(at[1]);
            }
            networkService.sendSplitScene(owner, target, scene, moving);
            logEntries.add(LogEntry.system(owner + " passou pelo portal pra \"" + target + "\"."));
        } catch (IOException | RuntimeException ex) {
            showError("O portal leva pra \"" + target + "\", mas não deu pra abrir essa cena: " + ex.getMessage());
        }
    }

    /** Janela de criar/editar uma zona. Devolve true se confirmou (a zona ja vem alterada). */
    boolean editZoneDialog(com.vttcaseiro.model.MapZone z, boolean creating) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle(creating ? "Nova zona" : "Editar zona");
        d.setHeaderText(null);
        String[] labels = {"Armadilha", "Portal / escada pra outra cena", "Área de dano (começo do turno)",
                "Terreno difícil", "Aviso", "Som (efeito, música ou ambiente)"};
        javafx.scene.control.ComboBox<String> kind = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(labels));
        int k = java.util.Arrays.asList(com.vttcaseiro.model.MapZone.KINDS).indexOf(z.getKind());
        kind.getSelectionModel().select(Math.max(0, k));
        TextField name = new TextField(z.getName());
        name.setPromptText("Ex.: Fosso com estacas");
        javafx.scene.control.ComboBox<String> ability = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList("FOR", "DES", "CON", "INT", "SAB", "CAR"));
        ability.getSelectionModel().select(z.getAbility());
        javafx.scene.control.Spinner<Integer> dc = new javafx.scene.control.Spinner<>(1, 40, z.getDc());
        dc.setEditable(true);
        dc.setPrefWidth(80);
        TextField damage = new TextField(z.getDamage());
        damage.setPromptText("2d6");
        damage.setPrefWidth(90);
        TextField damageType = new TextField(z.getDamageType());
        damageType.setPromptText("perfurante");
        CheckBox half = new CheckBox("Metade do dano se passar");
        half.setSelected(z.isHalfOnSave());
        javafx.scene.control.ComboBox<String> target = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(mesaService.listMaps()));
        target.setEditable(false);
        if (z.getTargetScene() != null) target.getSelectionModel().select(z.getTargetScene());
        javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(z.getText());
        text.setPrefRowCount(3);
        text.setWrapText(true);
        text.setPromptText("O que acontece (só o mestre vê, a não ser que marque abaixo)");
        CheckBox tell = new CheckBox("Mandar esse texto pro jogador que entrar");
        tell.setSelected(z.isTellPlayer());
        CheckBox once = new CheckBox("Dispara uma vez só (depois fica desarmada)");
        once.setSelected(z.isOnce());
        CheckBox rearm = new CheckBox("Armar de novo");
        rearm.setVisible(z.isTriggered());
        rearm.setManaged(z.isTriggered());
        CheckBox visible = new CheckBox("Jogadores veem a zona no mapa");
        visible.setSelected(z.isVisibleToPlayers());
        List<com.vttcaseiro.model.SoundEntry> sounds = new ArrayList<>();
        try {
            if (soundIndex != null) sounds.addAll(soundIndex.load());
        } catch (RuntimeException ignored) {
            // biblioteca de sons estragada: so da pra parar
        }
        javafx.scene.control.ComboBox<String> sound = new javafx.scene.control.ComboBox<>();
        sound.getItems().add("(parar o que está tocando)");
        int soundSel = 0;
        for (int i = 0; i < sounds.size(); i++) {
            sound.getItems().add(sounds.get(i).getName());
            if (sounds.get(i).getId().equals(z.getSoundId())) soundSel = i + 1;
        }
        if (z.getSoundId() != null && soundSel == 0) {
            sound.getItems().add(z.getSoundName().isBlank() ? "(som que saiu da biblioteca)" : z.getSoundName());
            soundSel = sound.getItems().size() - 1;
        }
        sound.getSelectionModel().select(soundSel);
        String[] channels = {com.vttcaseiro.model.AudioCommand.SFX, com.vttcaseiro.model.AudioCommand.MUSIC,
                com.vttcaseiro.model.AudioCommand.AMBIENT};
        javafx.scene.control.ComboBox<String> channel = new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections
                .observableArrayList("Efeito (toca uma vez)", "Trocar a música", "Trocar o ambiente (em loop)"));
        channel.getSelectionModel().select(Math.max(0, java.util.Arrays.asList(channels).indexOf(z.getSoundChannel())));
        CheckBox playersOnly = new CheckBox("Só com token de jogador (monstro andando não dispara)");
        playersOnly.setSelected(z.isPlayersOnly());
        Label soundHint = new Label(sounds.isEmpty() ? "Traga sons na aba Som pra escolher aqui." : "Toca pra todos, como na aba Som.");
        soundHint.getStyleClass().add("label-muted");

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(10));
        int row = 0;
        g.addRow(row++, new Label("Tipo"), kind);
        g.addRow(row++, new Label("Nome"), name);
        HBox save = new HBox(6, ability, new Label("CD"), dc);
        save.setAlignment(Pos.CENTER_LEFT);
        Label saveL = new Label("Salvaguarda");
        g.addRow(row++, saveL, save);
        HBox dmg = new HBox(6, damage, damageType);
        Label dmgL = new Label("Dano");
        g.addRow(row++, dmgL, dmg);
        g.add(half, 1, row++);
        Label targetL = new Label("Leva pra");
        g.addRow(row++, targetL, target);
        Label soundL = new Label("Som");
        g.addRow(row++, soundL, sound);
        Label channelL = new Label("Como");
        g.addRow(row++, channelL, channel);
        g.add(soundHint, 1, row++);
        g.addRow(row++, new Label("Texto"), text);
        g.add(tell, 1, row++);
        g.add(once, 1, row++);
        g.add(rearm, 1, row++);
        g.add(visible, 1, row++);
        g.add(playersOnly, 1, row++);
        Runnable byKind = () -> {
            String kk = com.vttcaseiro.model.MapZone.KINDS[Math.max(0, kind.getSelectionModel().getSelectedIndex())];
            boolean trap = kk.equals(com.vttcaseiro.model.MapZone.TRAP);
            boolean portal = kk.equals(com.vttcaseiro.model.MapZone.PORTAL);
            boolean dmgZone = kk.equals(com.vttcaseiro.model.MapZone.DAMAGE);
            boolean terrain = kk.equals(com.vttcaseiro.model.MapZone.TERRAIN);
            for (javafx.scene.Node n : List.of(saveL, save, half)) { n.setVisible(trap); n.setManaged(trap); }
            for (javafx.scene.Node n : List.of(dmgL, dmg)) { n.setVisible(trap || dmgZone); n.setManaged(trap || dmgZone); }
            for (javafx.scene.Node n : List.of(targetL, target)) { n.setVisible(portal); n.setManaged(portal); }
            boolean soundZone = kk.equals(com.vttcaseiro.model.MapZone.SOUND);
            for (javafx.scene.Node n : List.of(soundL, sound, channelL, channel, soundHint)) {
                n.setVisible(soundZone);
                n.setManaged(soundZone);
            }
            playersOnly.setVisible(!terrain && !dmgZone);
            playersOnly.setManaged(!terrain && !dmgZone);
            once.setVisible(!terrain && !dmgZone);
            once.setManaged(!terrain && !dmgZone);
            if (creating) {
                visible.setSelected(terrain);
                once.setSelected(trap);
                playersOnly.setSelected(soundZone);
            }
            d.getDialogPane().getScene().getWindow().sizeToScene();
        };
        kind.setOnAction(e -> byKind.run());
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        d.setOnShown(e -> byKind.run());
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return false;
        z.setKind(com.vttcaseiro.model.MapZone.KINDS[Math.max(0, kind.getSelectionModel().getSelectedIndex())]);
        z.setName(name.getText());
        z.setAbility(ability.getValue());
        z.setDc(dc.getValue());
        z.setDamage(damage.getText());
        z.setDamageType(damageType.getText());
        z.setHalfOnSave(half.isSelected());
        z.setTargetScene(target.getValue());
        z.setText(text.getText());
        z.setTellPlayer(tell.isSelected());
        z.setOnce(once.isSelected());
        if (rearm.isSelected()) z.setTriggered(false);
        z.setVisibleToPlayers(visible.isSelected());
        z.setPlayersOnly(playersOnly.isSelected());
        z.setSoundChannel(channels[Math.max(0, channel.getSelectionModel().getSelectedIndex())]);
        int si = sound.getSelectionModel().getSelectedIndex();
        if (si <= 0) {
            z.setSoundId(null);
            z.setSoundName("");
        } else if (si <= sounds.size()) {
            z.setSoundId(sounds.get(si - 1).getId());
            z.setSoundName(sounds.get(si - 1).getName());
        } // senao: o som antigo que saiu da biblioteca fica como estava
        return true;
    }

    /** Menu da bolinha de um jogador (mestre): levar pra outra cena, trazer de volta, ver a cena dele. */
    javafx.scene.control.ContextMenu splitMenu(String player, String away) {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        javafx.scene.control.Menu send = new javafx.scene.control.Menu("Levar " + player + " pra cena...");
        List<String> maps;
        try {
            maps = mesaService.listMaps();
        } catch (RuntimeException ex) {
            maps = List.of();
        }
        for (String map : maps) {
            if (map.equals(away)) continue;
            javafx.scene.control.MenuItem it = new javafx.scene.control.MenuItem(map);
            it.setOnAction(e -> sendPlayerToScene(player, map));
            send.getItems().add(it);
        }
        if (send.getItems().isEmpty()) send.setDisable(true);
        m.getItems().add(send);
        javafx.scene.control.MenuItem asHim = new javafx.scene.control.MenuItem("Ver o mapa como " + player);
        asHim.setDisable(away != null && !away.equals(currentMapName));
        asHim.setOnAction(e -> viewAsPlayer(player));
        m.getItems().add(asHim);
        if (away != null) {
            javafx.scene.control.MenuItem back = new javafx.scene.control.MenuItem("Trazer " + player + " de volta pro grupo");
            back.setOnAction(e -> networkService.sendSplitScene(player, null, null));
            javafx.scene.control.MenuItem look = new javafx.scene.control.MenuItem("Ver a cena de " + player + " (" + away + ")");
            look.setOnAction(e -> {
                flushSceneAutosave();
                networkService.sendGmView(away);
            });
            m.getItems().addAll(back, look);
        }
        return m;
    }

    // ---------------- ver como um jogador

    private final HBox perspectiveBar = new HBox(8);
    /** "Conexao caiu. Reconectando..." no meio do mapa. */
    private final Label reconnectBanner = new Label();

    /** Mestre ve o mapa (visao, escuro, neblina, ocultos) como o jogador ve, pelos tokens dele. */
    void viewAsPlayer(String player) {
        String id = networkService.getPlayerIdsByName().get(player);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TokenModel t : sceneService.getScene().getTokens()) {
            if (id != null && id.equals(t.getOwnerId())) ids.add(t.getId());
            else if (id != null && t.getCharacterSheetId() != null && characterService.findById(t.getCharacterSheetId())
                    .map(s -> id.equals(s.getOwnerPlayerId())).orElse(false)) ids.add(t.getId());
        }
        if (ids.isEmpty()) {
            showError(player + " não tem token nesta cena.");
            return;
        }
        viewAs(ids, player);
    }

    /** Ver como estes tokens (ex.: "Ver como" no menu do token). */
    void viewAs(java.util.Set<String> tokenIds, String label) {
        mapView.setPerspective(tokenIds);
        perspectiveBar.getChildren().clear();
        Label l = new Label("Vendo como " + label + (sceneService.getScene().isVisionEnabled() ? ""
                : " (cena sem visão dinâmica: só some o que está oculto)"), com.vttcaseiro.view.Icons.icon("eye", 13));
        l.getStyleClass().add("perspective-label");
        Button back = new Button("Voltar à visão do mestre");
        back.getStyleClass().add("button-primary");
        back.setOnAction(e -> endViewAs());
        perspectiveBar.getChildren().addAll(l, back);
        perspectiveBar.setVisible(true);
        perspectiveBar.setManaged(true);
    }

    void endViewAs() {
        mapView.setPerspective(null);
        perspectiveBar.setVisible(false);
        perspectiveBar.setManaged(false);
    }

    /** Grupo dividido: o jogador vai sozinho pra cena {@code map} (a cena aberta segue com o resto do grupo). */
    void sendPlayerToScene(String player, String map) {
        try {
            SceneModel scene = map.equals(currentMapName) ? sceneService.getScene() : mesaService.loadMap(map);
            if (map.equals(currentMapName)) flushSceneAutosave();
            imageAssetService.migrateInlineImages(scene);
            networkService.sendSplitScene(player, map, scene);
            logEntries.add(LogEntry.system(player + " foi pra cena \"" + map + "\" (o resto do grupo continua aqui)."));
        } catch (IOException | RuntimeException ex) {
            showError("Não deu pra abrir a cena \"" + map + "\": " + ex.getMessage());
        }
    }

    static final String[] PLAYER_COLORS = {"#7F77DD", "#1D9E75", "#D85A30", "#D4537E", "#378ADD", "#639922", "#BA7517",
            "#E24B4A", "#5DCAA5", "#AFA9EC"};

    static String playerColor(String name) {
        return PLAYER_COLORS[Math.floorMod(name == null ? 0 : name.toLowerCase().hashCode(), PLAYER_COLORS.length)];
    }

    // ---------------- fila de turnos (cartoes sobre o mapa)

    private final HBox turnQueue = new HBox(6);
    /** Quantos cartoes mostrar no maximo (o resto vira "+N"). */
    static final int TURN_QUEUE_MAX = 10;

    /**
     * A ordem do combate como fila de cartoes no alto do mapa: retrato, nome, PV (se pode ver) e condicoes; o da
     * vez maior. Clicar centraliza o token. "Proximo turno" fica no fim (mestre ou dono de quem esta na vez).
     */
    void refreshTurnQueue() {
        if (sceneService == null) return;
        updateTurnClock();
        refreshTabBadges();
        SceneModel sc = sceneService.getScene();
        com.vttcaseiro.model.Encounter enc = sc.getEncounter();
        turnQueue.getChildren().clear();
        // um cartao por grupo (agrupar monstros iguais): o primeiro do grupo representa os outros
        List<TokenModel> order = EncounterService.groupedOrder(sc).stream().map(g -> g.get(0))
                .filter(t -> t.getInitiative() != null).toList();
        boolean show = enc.isActive() && !order.isEmpty();
        turnQueue.setVisible(show);
        turnQueue.setManaged(show);
        if (!show) return;
        Label round = new Label("Rodada " + enc.getRound());
        round.getStyleClass().add("turn-round");
        turnQueue.getChildren().add(round);
        if (turnTimer.running()) turnQueue.getChildren().add(turnClock);
        // comeca em quem esta na vez, e da a volta
        int cur = 0;
        for (int i = 0; i < order.size(); i++) if (order.get(i).getId().equals(enc.getCurrentTokenId())) cur = i;
        int shown = Math.min(order.size(), TURN_QUEUE_MAX);
        for (int k = 0; k < shown; k++) {
            TokenModel t = order.get((cur + k) % order.size());
            turnQueue.getChildren().add(turnCard(t, k == 0));
        }
        if (order.size() > shown) {
            Label more = new Label("+" + (order.size() - shown));
            more.getStyleClass().add("turn-more");
            turnQueue.getChildren().add(more);
        }
        TokenModel current = order.get(cur);
        if (networkService.isGm() || table.canControl(current)) {
            Button next = new Button(null, com.vttcaseiro.view.Icons.icon("next", 16));
            next.getStyleClass().addAll("button-primary", "turn-next");
            next.setTooltip(new Tooltip("Próximo turno"));
            next.setOnAction(e -> table.nextTurn());
            turnQueue.getChildren().add(next);
        }
    }

    // ---------------- relogio do turno

    private final com.vttcaseiro.service.TurnTimer turnTimer = new com.vttcaseiro.service.TurnTimer();
    /** Relogio na fila de turnos (sobre o mapa) e no painel de combate. */
    private final Label turnClock = new Label();
    private Label turnClockPanel;
    private Button turnTimerButton;
    private javafx.animation.Timeline turnClockTick;

    /** Combate mudou: recomeca o relogio se a vez mudou e liga/desliga o tique. */
    void updateTurnClock() {
        com.vttcaseiro.model.Encounter enc = sceneService.getScene().getEncounter();
        turnTimer.update(enc, System.currentTimeMillis());
        if (turnClock.getStyleClass().isEmpty() || !turnClock.getStyleClass().contains("turn-clock")) {
            turnClock.getStyleClass().add("turn-clock");
            turnClock.setOnMouseClicked(e -> {
                if (networkService.isGm()) table.setTurnTimerPaused(!sceneService.getScene().getEncounter().isTimerPaused());
            });
        }
        turnClock.setTooltip(new Tooltip(networkService.isGm() ? "Tempo do turno. Clique pausa/continua." : "Tempo do turno"));
        if (turnTimer.running() && turnClockTick == null) {
            turnClockTick = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(250), e -> tickTurnClock()));
            turnClockTick.setCycleCount(javafx.animation.Animation.INDEFINITE);
            turnClockTick.play();
        } else if (!turnTimer.running() && turnClockTick != null) {
            turnClockTick.stop();
            turnClockTick = null;
        }
        tickTurnClock();
    }

    void tickTurnClock() {
        long now = System.currentTimeMillis();
        boolean on = turnTimer.running();
        String text = on ? com.vttcaseiro.service.TurnTimer.format(turnTimer.leftMs(now)) + (turnTimer.paused() ? " (pausado)" : "") : "";
        String state = on ? turnTimer.state(now) : "ok";
        for (Label l : new Label[]{turnClock, turnClockPanel}) {
            if (l == null) continue;
            l.setText(text);
            l.getStyleClass().removeAll("turn-clock-warn", "turn-clock-over");
            if (!"ok".equals(state)) l.getStyleClass().add("turn-clock-" + state);
        }
        if (turnClockPanel != null) showNode(turnClockPanel, on);
        if (turnTimer.checkExpired(now)) {
            com.vttcaseiro.model.Encounter enc = sceneService.getScene().getEncounter();
            TokenModel cur = sceneService.getScene().findToken(enc.getCurrentTokenId()).orElse(null);
            if (cur != null && (networkService.isGm() || table.canControl(cur))) {
                logEntries.add(LogEntry.system("Acabou o tempo do turno de " + cur.getName() + "."));
            }
        }
    }

    /** Menu do mestre: tempo por turno, pausar e reiniciar. */
    javafx.scene.control.ContextMenu turnTimerMenu() {
        javafx.scene.control.ContextMenu m = new javafx.scene.control.ContextMenu();
        com.vttcaseiro.model.Encounter enc = sceneService.getScene().getEncounter();
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        boolean known = false;
        for (int s : new int[]{0, 30, 60, 90, 120, 180, 300}) {
            javafx.scene.control.RadioMenuItem it = new javafx.scene.control.RadioMenuItem(com.vttcaseiro.service.TurnTimer.label(s));
            it.setToggleGroup(group);
            it.setSelected(enc.getTurnSeconds() == s);
            known |= enc.getTurnSeconds() == s;
            it.setOnAction(e -> table.setTurnTimer(s));
            m.getItems().add(it);
        }
        javafx.scene.control.RadioMenuItem custom = new javafx.scene.control.RadioMenuItem(known ? "Outro tempo..."
                : "Outro tempo (" + com.vttcaseiro.service.TurnTimer.label(enc.getTurnSeconds()) + ")...");
        custom.setToggleGroup(group);
        custom.setSelected(!known);
        custom.setOnAction(e -> {
            javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(String.valueOf(Math.max(10, enc.getTurnSeconds())));
            d.initOwner(stage);
            d.setTitle("Relógio do turno");
            d.setHeaderText(null);
            d.setContentText("Segundos por turno:");
            d.showAndWait().ifPresent(v -> {
                try {
                    table.setTurnTimer(Integer.parseInt(v.strip()));
                } catch (NumberFormatException ex) {
                    showError("Use um número de segundos (ex.: 45).");
                }
            });
        });
        m.getItems().addAll(custom, new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem pause = new javafx.scene.control.MenuItem(enc.isTimerPaused() ? "Continuar o relógio" : "Pausar o relógio");
        pause.setDisable(enc.getTurnSeconds() <= 0);
        pause.setOnAction(e -> table.setTurnTimerPaused(!enc.isTimerPaused()));
        javafx.scene.control.MenuItem restart = new javafx.scene.control.MenuItem("Reiniciar o tempo deste turno");
        restart.setDisable(enc.getTurnSeconds() <= 0 || !enc.isActive());
        restart.setOnAction(e -> table.restartTurnTimer());
        m.getItems().addAll(pause, restart);
        return m;
    }

    /** "Goblin 3" -> "Goblin" (nome do grupo na iniciativa). */
    static String groupName(TokenModel t) {
        String n = t.getName() == null ? "" : t.getName();
        return n.replaceAll("\\s*#?\\d+$", "").strip();
    }

    private javafx.scene.Node turnCard(TokenModel t, boolean current) {
        double size = current ? 46 : 34;
        StackPane face = new StackPane();
        face.setMinSize(size, size);
        face.setMaxSize(size, size);
        javafx.scene.shape.Circle bg = new javafx.scene.shape.Circle(size / 2);
        bg.getStyleClass().add("turn-face");
        face.getChildren().add(bg);
        javafx.scene.image.Image img = turnFaceImage(t);
        if (img != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setClip(new javafx.scene.shape.Circle(size / 2, size / 2, size / 2));
            face.getChildren().add(iv);
        } else {
            String n = t.getName() == null || t.getName().isBlank() ? "?" : t.getName().trim();
            Label initial = new Label(n.substring(0, 1).toUpperCase());
            initial.getStyleClass().add("turn-initial");
            face.getChildren().add(initial);
        }
        Label name = new Label(t.getName() + "  " + t.getInitiative());
        name.getStyleClass().add("turn-name");
        name.setMaxWidth(current ? 130 : 96);
        VBox info = new VBox(3, name);
        if (table.canSeeHp(t) && t.getMaxHp() != null && t.getMaxHp() > 0) {
            int hp = t.getCurrentHp() == null ? t.getMaxHp() : t.getCurrentHp();
            double frac = Math.max(0, Math.min(1, hp / (double) t.getMaxHp()));
            javafx.scene.layout.StackPane bar = new javafx.scene.layout.StackPane();
            bar.getStyleClass().add("turn-hp");
            bar.setMinSize(current ? 110 : 80, 5);
            bar.setMaxSize(current ? 110 : 80, 5);
            Region fill = new Region();
            fill.getStyleClass().add(frac > 0.5 ? "turn-hp-ok" : frac > 0.25 ? "turn-hp-mid" : "turn-hp-low");
            fill.setMaxWidth((current ? 110 : 80) * frac);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            bar.getChildren().add(fill);
            info.getChildren().add(bar);
        }
        if (!t.getConditions().isEmpty() || !t.getEffects().isEmpty()) {
            Label cond = new Label(String.join(", ", conditionNames(t)));
            cond.getStyleClass().add("turn-cond");
            cond.setMaxWidth(current ? 130 : 96);
            info.getChildren().add(cond);
        }
        if (t.isHiddenFromPlayers()) face.setOpacity(0.55); // escondido: so o mestre ve, meio apagado
        HBox card = new HBox(6, face, info);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add(current ? "turn-card-current" : "turn-card");
        card.setUserData(t.getId());
        card.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(card, new Tooltip(t.getName() + (current ? " — na vez" : "") + "\nClique pra ver no mapa"));
        card.setOnMouseClicked(e -> mapView.centerOnToken(t));
        return card;
    }

    private List<String> conditionNames(TokenModel t) {
        List<String> out = new ArrayList<>();
        for (String c : t.getConditions()) {
            try {
                out.add(com.vttcaseiro.model.Condition.valueOf(c).label());
            } catch (IllegalArgumentException ex) {
                out.add(c);
            }
        }
        for (String e : t.getEffects()) out.add(com.vttcaseiro.character.EffectModifiers.nameOf(e));
        return out;
    }

    private final java.util.Map<String, javafx.scene.image.Image> turnFaces = new java.util.HashMap<>();

    private javafx.scene.image.Image turnFaceImage(TokenModel t) {
        String id = t.getImageAssetId();
        if (id == null || imageAssetService == null || !imageAssetService.exists(id)) return null;
        return turnFaces.computeIfAbsent(id, k -> {
            try {
                var l = com.vttcaseiro.view.SafeImage.read(imageAssetService.readBytes(k), 128, 128L * 128L);
                return l == null ? null : l.image();
            } catch (IOException ex) {
                return null;
            }
        });
    }

    private Button startCombatButton;
    private Button nextTurnPanelButton;
    private CheckBox groupMonstersBox;
    /** Linha da iniciativa -> quantos jogam junto (so os grupos com mais de 1). */
    private final java.util.Map<String, Integer> initiativeGroupSize = new java.util.HashMap<>();
    private List<Button> combatIdleButtons = List.of();
    private List<Button> combatActiveButtons = List.of();
    private List<Button> combatToolButtons = List.of();
    private Button gmCombatCard;

    private static void showNode(javafx.scene.Node n, boolean show) {
        n.setVisible(show);
        n.setManaged(show);
    }

    private void refreshInitiativeList() {
        refreshTurnQueue();
        if (initiativeListView == null) {
            return; // ainda construindo a aba na primeira chamada
        }
        // com "agrupar monstros", cada grupo vira uma linha (o primeiro do grupo, com "x4")
        initiativeGroupSize.clear();
        List<TokenModel> rows = new ArrayList<>();
        for (List<TokenModel> g : EncounterService.groupedOrder(sceneService.getScene())) {
            rows.add(g.get(0));
            if (g.size() > 1) initiativeGroupSize.put(g.get(0).getId(), g.size());
        }
        initiativeListView.getItems().setAll(rows);
        if (groupMonstersBox != null) groupMonstersBox.setSelected(sceneService.getScene().getEncounter().isGroupMonsters());
        initiativeListView.refresh();
        if (roundLabel != null) {
            var e = sceneService.getScene().getEncounter();
            roundLabel.setText(e.isActive() ? "Rodada " + e.getRound() : "Sem combate");
            roundLabel.getStyleClass().remove("combat-status-active");
            if (e.isActive()) roundLabel.getStyleClass().add("combat-status-active");
            // um destaque por vez: sem combate, "comecar"; em combate, "proximo turno"
            if (startCombatButton != null && nextTurnPanelButton != null) {
                startCombatButton.getStyleClass().remove("button-primary");
                nextTurnPanelButton.getStyleClass().remove("button-primary");
                (e.isActive() ? nextTurnPanelButton : startCombatButton).getStyleClass().add("button-primary");
            }
        }
        boolean gm = networkService.isGm();
        boolean active = sceneService.getScene().getEncounter().isActive();
        // jogador nao ve os botoes do mestre (em vez de ve-los apagados); e o mestre so ve os da hora
        for (Button b : combatIdleButtons) showNode(b, gm && !active);
        for (Button b : combatActiveButtons) showNode(b, gm && active);
        for (Button b : combatToolButtons) showNode(b, gm);
        if (gmCombatCard != null) {
            javafx.scene.Node card = gmCombatCard.getParent();
            while (card != null && !card.getStyleClass().contains("panel-card")) card = card.getParent();
            if (card != null) showNode(card, gm); // cartao "Mestre: grupo inteiro" some todo pro jogador
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
        Integer together = initiativeGroupSize.get(token.getId());
        Label name = new Label(together == null ? token.getName() : groupName(token) + "  ×" + together);
        if (together != null) Tooltip.install(name, new Tooltip(together + " monstros iguais jogando no mesmo turno"));
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
            String displayName;
            if (same == 0) {
                displayName = mob.getName();
            } else if (AppSettings.getBool("monstros.nomesSortidos", true)) {
                displayName = com.vttcaseiro.service.FlavorNames.pick(mob.getName(),
                        sceneService.getScene().getTokens().stream().map(TokenModel::getName).toList(), new java.util.Random());
            } else {
                displayName = mob.getName() + " " + (same + 1);
            }
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
        if (rollsOverNetwork()) {
            expectRoll(label, after);
            sendModeRoll(label, expression, "normal", 0, hidden);
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
        if (networkService.isConnected() && "eu".equals(rollMode)) {
            label = (label == null || label.isBlank() ? "" : label + " ") + "(só você)";
        }
        hidden = hidden || !"publica".equals(rollMode);
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
            com.vttcaseiro.service.SpellArea area = spellAreaOf(spell);
            if (area != null && mapView != null && sceneService.getScene().getGrid().getCellSize() > 1) {
                // magia de area: a forma fica presa no cursor; o clique marca quem esta dentro e ai rola
                double px = area.feet() / Math.max(0.1, sceneService.getScene().getGrid().getFeetPerCell())
                        * sceneService.getScene().getGrid().getCellSize();
                double[] origin = sceneService.getScene().getTokens().stream()
                        .filter(t -> sheet.getId().equals(t.getCharacterSheetId())).findFirst()
                        .map(t -> com.vttcaseiro.service.ZoneRules.center(sceneService.getScene(), t, t.getX(), t.getY()))
                        .orElse(null);
                logEntries.add(LogEntry.system(spell.getName() + ": posicione a área (" + area.label() + ") no mapa e clique. "
                        + "Esc ou botão direito: sem área (usa os alvos marcados)."));
                mapView.armArea(area.shape(), px, origin, inside -> {
                    mapView.setTargets(inside);
                    logEntries.add(LogEntry.system(spell.getName() + ": " + (inside.isEmpty() ? "ninguém dentro da área."
                            : inside.size() + " dentro da área (" + String.join(", ", inside.stream().map(TokenModel::getName).toList()) + ").")));
                    com.vttcaseiro.service.CombatFlow.castDice(table, roller, inside, sheet, spell, slotLevel, dice, hidden);
                }, () -> com.vttcaseiro.service.CombatFlow.castDice(table, roller, mapView.getTargets(), sheet, spell,
                        slotLevel, dice, hidden));
                return;
            }
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
            updateVaultCopy(sheet);
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

    public static final String PLAYIT_DOWNLOAD = "https://playit.gg/download";

    /**
     * Passo a passo do playit.gg: um tunel que roda no PC do mestre e da um endereco publico pra mesa, sem abrir
     * porta no roteador e sem servidor de ponte. O mestre cola aqui o endereco que o playit mostrou; o programa testa
     * e mostra o que passar pros jogadores.
     */
    private javafx.scene.Node buildPlayitSection() {
        Label steps = new Label("""
                1. Baixe e abra o playit.gg (programa grátis; crie a conta quando ele pedir).
                2. No site do playit: "Add Tunnel" > tipo "TCP" > porta local 55055 (a de "Hospedar aqui").
                3. Clique em "Hospedar aqui" (de preferência com senha da mesa).
                4. Cole abaixo o endereço que o playit mostrou pro túnel (ex.: algo.ply.gg:12345).
                O playit precisa ficar aberto enquanto vocês jogam.""");
        steps.setWrapText(true);
        Button download = new Button("Baixar o playit.gg", com.vttcaseiro.view.Icons.icon("download", 14));
        download.setOnAction(e -> getHostServices().showDocument(PLAYIT_DOWNLOAD));
        TextField address = new TextField(AppSettings.getString("playit.endereco", ""));
        address.setPromptText("endereço do túnel, ex.: algo.ply.gg:12345");
        HBox.setHgrow(address, Priority.ALWAYS);
        Button test = new Button("Testar e mostrar pros jogadores");
        test.setMinWidth(Region.USE_PREF_SIZE);
        Label result = new Label();
        result.setWrapText(true);
        result.setVisible(false);
        result.setManaged(false);
        Button copy = new Button("Copiar endereço");
        copy.setVisible(false);
        copy.setManaged(false);
        test.setOnAction(e -> {
            String[] hp = com.vttcaseiro.view.MesaSelectorView.splitHostPort(address.getText());
            if (hp == null) {
                showError("Cole o endereço completo que o playit mostrou, com a porta no fim (ex.: algo.ply.gg:12345).");
                return;
            }
            if (!networkService.isConnected() || !networkService.isGm()) {
                showError("Clique em \"Hospedar aqui\" primeiro: o túnel do playit leva os jogadores até a mesa hospedada.");
                return;
            }
            AppSettings.setString("playit.endereco", hp[0] + ":" + hp[1]);
            test.setDisable(true);
            result.setText("Testando o túnel...");
            result.setStyle("");
            result.setVisible(true);
            result.setManaged(true);
            Thread t = new Thread(() -> {
                String problem = testTunnel(hp[0], Integer.parseInt(hp[1]));
                Platform.runLater(() -> {
                    test.setDisable(false);
                    String forPlayers = "Jogadores: \"Entrar numa mesa online\" > endereço " + hp[0] + ", porta " + hp[1]
                            + (networkService.isTls() ? " (e marcar \"Mesa criptografada\")" : "") + " e a senha da mesa.";
                    if (problem == null) {
                        result.setText("Túnel funcionando! " + forPlayers);
                        result.setStyle("-fx-text-fill: #8FE0AE;");
                        logEntries.add(LogEntry.system("playit.gg: túnel funcionando em " + hp[0] + ":" + hp[1] + "."));
                    } else {
                        result.setText(problem + "\n(Se o playit já mostra o túnel como ativo, pode tentar mesmo assim. "
                                + forPlayers + ")");
                        result.setStyle("-fx-text-fill: -c-danger;");
                    }
                    copy.setVisible(true);
                    copy.setManaged(true);
                });
            }, "testar-playit");
            t.setDaemon(true);
            t.start();
        });
        copy.setOnAction(e -> {
            String[] hp = com.vttcaseiro.view.MesaSelectorView.splitHostPort(address.getText());
            if (hp == null) return;
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(hp[0] + ":" + hp[1]);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            logEntries.add(LogEntry.system("Endereço copiado: " + hp[0] + ":" + hp[1] + " (o jogador pode colar tudo no campo Endereço)."));
        });
        HBox row = new HBox(6, address, test);
        row.setAlignment(Pos.CENTER_LEFT);
        Label safety = new Label("Pelo túnel, qualquer pessoa com o endereço chega na tela de senha: use senha da mesa.");
        safety.getStyleClass().add("label-muted");
        safety.setWrapText(true);
        VBox body = new VBox(8, steps, download, row, result, copy, safety);
        body.setVisible(false);
        body.setManaged(false);
        Label toggle = new Label("▸  Jogar pela internet com playit.gg (sem abrir porta)");
        toggle.getStyleClass().add("hint-toggle");
        toggle.setCursor(javafx.scene.Cursor.HAND);
        toggle.setOnMouseClicked(e -> {
            boolean show = !body.isVisible();
            body.setVisible(show);
            body.setManaged(show);
            toggle.setText((show ? "▾" : "▸") + "  Jogar pela internet com playit.gg (sem abrir porta)");
        });
        return new VBox(8, toggle, body);
    }

    /**
     * Abre uma conexao pelo endereco publico do tunel e confere se quem atende e a mesa (o servidor manda o desafio
     * de senha logo que alguem conecta). Devolve null se deu certo, ou o problema explicado.
     */
    static String testTunnel(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 8000);
            s.setSoTimeout(6000);
            java.io.InputStream in = s.getInputStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) return "O túnel conectou, mas a mesa não respondeu. O playit está apontando pra porta 55055?";
            String first = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            if (first.contains("\"type\"")) return null; // mensagem do servidor da mesa
            return "Alguém respondeu nesse endereço, mas não parece a mesa (com TLS ligado isso é normal).";
        } catch (java.net.UnknownHostException ex) {
            return "Endereço não encontrado: " + host + ". Confira se copiou certo do playit.";
        } catch (java.net.SocketTimeoutException ex) {
            return "O túnel não respondeu a tempo. O playit está aberto e com o túnel ativo?";
        } catch (java.io.IOException ex) {
            return "Não deu pra conectar pelo túnel (" + ex.getMessage() + "). O playit está aberto e o túnel aponta "
                    + "pra porta 55055?";
        }
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
            showError(relayErrorText(host, port, ex));
        }
    }

    /** Erro da ponte explicado (o "Connection refused" sozinho nao dizia o que fazer). */
    static String relayErrorText(String host, int port, Exception ex) {
        String where = host + ":" + port;
        boolean local = host.equalsIgnoreCase("localhost") || host.startsWith("127.") || host.equals("::1");
        if (ex instanceof java.net.ConnectException) {
            return "Não tem ponte rodando em " + where + " (a conexão foi recusada).\n\n"
                    + "A ponte é um programa separado (pasta Customizr-Ponte) que precisa estar ligado num servidor "
                    + "com porta aberta pra internet — um VPS, por exemplo. Veja o LEIA-ME.md dessa pasta."
                    + (local ? "\n\nVocê digitou este próprio computador: aí a ponte teria que estar rodando aqui "
                    + "(rodar-ponte.cmd), e mesmo assim os jogadores de fora não alcançam sem porta aberta." : "")
                    + "\n\nSem servidor? Dá pra jogar sem ponte: \"Hospedar aqui\" e passar seu IP e porta "
                    + "(precisa liberar a porta 55055 no roteador) ou usar Tailscale/ZeroTier.";
        }
        if (ex instanceof java.net.SocketTimeoutException || ex instanceof java.net.NoRouteToHostException) {
            return "A ponte em " + where + " não respondeu. Confira o endereço e se a porta " + port
                    + " está liberada no firewall do servidor.";
        }
        if (ex instanceof java.net.UnknownHostException) {
            return "Endereço da ponte não encontrado: " + host + ". Confira se digitou certo.";
        }
        return "Não foi possível abrir na ponte (" + where + "): " + ex.getMessage();
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
                String mirrorDir = AppSettings.getString("backupMirrorDir", "");
                if (made.isPresent() && !mirrorDir.isBlank()) {
                    try {
                        com.vttcaseiro.library.AutoBackup.mirror(made.get(), root, Path.of(mirrorDir), mesaService.getName());
                    } catch (IOException | RuntimeException ex) {
                        Platform.runLater(() -> logEntries.add(LogEntry.error("Backup feito, mas a cópia pra " + mirrorDir
                                + " falhou: " + ex.getMessage())));
                    }
                }
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
        if (connecting) {
            showError("Já estou tentando conectar; espere um pouco.");
            return;
        }
        connecting = true;
        logEntries.add(LogEntry.system("Conectando em " + host.trim() + ":" + port + "..."));
        // Fora da thread da tela: endereco errado, firewall ou espera da ponte nao congelam mais o programa.
        Thread t = new Thread(() -> {
            String error = null;
            try {
                networkService.connect(host.trim(), port, resolvedName,
                        mesaRepository.getImageAssetStore(), asGm, blankToNull(tablePassword),
                        asGm ? blankToNull(masterPassword) : null, tls, this::confirmTableCodeFromAnyThread,
                        new AppGameListener());
            } catch (IllegalStateException | IOException ex) {
                error = ex.getMessage();
            } catch (RuntimeException ex) {
                error = "erro inesperado: " + ex;
            }
            String failure = error;
            Platform.runLater(() -> {
                connecting = false;
                if (failure == null) logEntries.add(LogEntry.system("Conectado ao servidor."));
                else showError("Não foi possível conectar: " + failure);
            });
        }, "conectar-mesa");
        t.setDaemon(true);
        t.start();
    }

    private volatile boolean connecting;

    /** A pergunta do codigo da mesa (TLS) e uma janela: roda na thread da tela e a conexao espera a resposta. */
    private boolean confirmTableCodeFromAnyThread(String hostPort, String fingerprint, String previous) {
        if (Platform.isFxApplicationThread()) return confirmTableCode(hostPort, fingerprint, previous);
        java.util.concurrent.FutureTask<Boolean> ask = new java.util.concurrent.FutureTask<>(
                () -> confirmTableCode(hostPort, fingerprint, previous));
        Platform.runLater(ask);
        try {
            return ask.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException ex) {
            return false;
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
                if (mapView.hasPerspective()) endViewAs();
                sceneService.replaceScene(scene);
                logEntries.add(LogEntry.system("Mapa sincronizado com o servidor."));
            });
        }

        @Override
        public void onSceneView(String sceneKey, boolean side) {
            Platform.runLater(() -> {
                flushSceneAutosave();
                if (sceneKey != null) currentMapName = sceneKey;
                sideViewing = side;
                if (!side) groupMapName = sceneKey;
                if (currentSceneLabel != null && currentMapName != null) currentSceneLabel.setText("Cena atual: " + currentMapName);
                refreshSceneStrip();
            });
        }

        @Override
        public void onSideSceneClosed(String sceneKey, SceneModel scene) {
            if (sceneKey == null || scene == null) return;
            Platform.runLater(() -> {
                try {
                    mesaService.saveMap(scene, sceneKey);
                    logEntries.add(LogEntry.system("Cena separada \"" + sceneKey + "\" ficou vazia e foi salva."));
                } catch (IOException | RuntimeException ex) {
                    logEntries.add(LogEntry.error("Não deu pra salvar a cena \"" + sceneKey + "\": " + ex.getMessage()));
                }
            });
        }

        @Override
        public void onMapPull(String player, double x, double y) {
            Platform.runLater(() -> {
                mapView.centerOn(x, y, true);
                mapView.showPing(x, y, player);
                logEntries.add(LogEntry.system((player == null ? "O mestre" : player) + " chamou sua atenção pra um ponto do mapa."));
            });
        }

        @Override
        public void onCameraLock(boolean locked, double x, double y, double zoom) {
            Platform.runLater(() -> {
                if (networkService.isGm()) return;
                boolean was = mapView.isCameraLocked();
                mapView.setCameraLocked(locked);
                cameraBanner.setVisible(locked);
                if (locked) mapView.applyView(x, y, zoom);
                if (locked != was) logEntries.add(LogEntry.system(locked ? "O mestre está guiando a câmera."
                        : "Câmera liberada: você pode mexer na vista de novo."));
            });
        }

        @Override
        public void onPoll(String id, String author, String question, List<String> options, List<Integer> counts,
                           Integer myVote) {
            Platform.runLater(() -> upsertPoll(id, author, question, options, counts, myVote));
        }

        @Override
        public void onShops(List<com.vttcaseiro.model.Shop> shops) {
            Platform.runLater(() -> {
                receivedShops = new ArrayList<>(shops);
                if (shopWindow != null && shopWindow.isShowing()) shopWindow.refresh();
            });
        }

        @Override
        public void onShopRequest(com.vttcaseiro.net.NetMessage.ShopRequest r) {
            Platform.runLater(() -> handleShopRequest(r));
        }

        @Override
        public void onZonesSync(List<com.vttcaseiro.model.MapZone> zones) {
            Platform.runLater(() -> {
                sceneService.getScene().setZones(zones);
                mapView.redrawZones();
            });
        }

        @Override
        public void onZoneEvent(com.vttcaseiro.model.MapZone zone, TokenModel token, String sceneKey, String ownerName) {
            Platform.runLater(() -> onZoneEntered(zone, token, sceneKey, ownerName));
        }

        @Override
        public void onPlayersChanged() {
            Platform.runLater(() -> {
                refreshPlayersStrip();
                refreshSceneStrip();
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
                // a imagem do mapa e as dos tokens chegam DEPOIS da cena (quem entra na mesa recebe a cena primeiro):
                // sem redesenhar aqui, o jogador ficava com o mapa vazio
                if (assetId.equals(sc.getBackgroundImageAssetId())) mapView.backgroundArrived(assetId);
                if (sc.getTokens().stream().anyMatch(t -> assetId.equals(t.getImageAssetId()))) mapView.rebuildTokens();
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
                boolean mine = !handout.getRecipients().isEmpty();
                logEntries.add(LogEntry.system((isNew ? (mine ? "O mestre te mandou: " : "Nova anotação no diário: ")
                        : "Anotação atualizada no diário: ") + handout.getTitle() + " (aba Diário)"));
            });
        }

        @Override
        public void onGroupNote(com.vttcaseiro.model.Handout note) {
            Platform.runLater(() -> onGroupNoteReceived(note));
        }

        @Override
        public void onGroupNoteRemoved(String id) {
            Platform.runLater(() -> {
                if (groupNotes.remove(id) == null) return;
                if (networkService.isGm() && groupNotesStore != null) groupNotesStore.save(new ArrayList<>(groupNotes.values()));
                refreshPlayerNotes();
            });
        }

        @Override
        public void onHandoutShow(com.vttcaseiro.model.Handout handout) {
            Platform.runLater(() -> {
                if (networkService.isGm()) return;
                if (journalList != null) {
                    journalItems.stream().filter(x -> x.getId().equals(handout.getId())).findFirst()
                            .ifPresent(x -> journalList.getSelectionModel().select(x));
                }
                openHandoutWindow(handout);
            });
        }

        @Override
        public void onCampaignSync(com.vttcaseiro.model.Campaign c) {
            Platform.runLater(() -> {
                if (networkService.isGm() && !networkService.isConnected()) return;
                campaign = c;
                if (campaignView != null && campaignView.isShowing()) campaignView.reload(c);
            });
        }

        @Override
        public void onCharacterRemoved(String id) {
            Platform.runLater(() -> {
                if (networkService.isGm()) return; // o mestre nunca perde ficha por isso
                try {
                    CharacterSheetView view = openSheetViews.remove(id);
                    if (view != null) view.close();
                    if (characterService.removeById(id) != null) refreshCharacterList();
                } catch (IOException ex) {
                    logEntries.add(LogEntry.error("Não deu pra tirar a ficha escondida: " + ex.getMessage()));
                }
            });
        }

        @Override
        public void onMobRemoved(String id) {
            Platform.runLater(() -> {
                if (networkService.isGm()) return;
                try {
                    if (mobService.removeById(id) != null) {
                        refreshMobList();
                        refreshMobCount();
                    }
                } catch (IOException ex) {
                    logEntries.add(LogEntry.error("Não deu pra tirar o monstro escondido: " + ex.getMessage()));
                }
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
                    var tview = templateViews.get(character.getId());
                    if (tview != null && character.usesTemplate()) {
                        tview.reload(character.getTemplate(), character.getTemplateValues());
                    }
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
            Platform.runLater(() -> {
                reconnectBanner.setVisible(false);
                logEntries.add(LogEntry.error("Desconectado: " + reason));
            });
        }

        @Override
        public void onReconnecting(int attempt, int max) {
            Platform.runLater(() -> {
                reconnectBanner.setText("Conexão caiu. Reconectando... (tentativa " + attempt + " de " + max + ")");
                reconnectBanner.setVisible(true);
                if (attempt == 1) logEntries.add(LogEntry.system("A conexão caiu. Tentando voltar sozinho..."));
            });
        }

        @Override
        public void onReconnected() {
            Platform.runLater(() -> {
                reconnectBanner.setVisible(false);
                logEntries.add(LogEntry.system("Reconectado! A mesa voltou."));
            });
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
            case TEXT, EMOTE -> say(cmd.text());
            case TABLE -> rollRandomTable(cmd.text(), gmOnlyMode());
            case LANGUAGE -> {
                if (networkService.isConnected()) networkService.sendChatLanguage(cmd.text(), cmd.target());
                else logEntries.add(LogEntry.chat(networkService.getPlayerName(), "[" + cmd.target() + "] " + cmd.text()));
            }
            case POLL -> startPoll(cmd.text());
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
        String result = table.get().rollExpanded(n -> java.util.concurrent.ThreadLocalRandom.current().nextInt(1, n + 1),
                this::findTable);
        String text = "Tabela " + table.get().getName() + " (" + result + ")";
        if (hidden) tablePort.announceGm(text); else announce(text);
    }

    // ---------------- baralhos de cartas (mestre)

    /** Secao "Baralhos" no Diario: criar (comum, taro, de imagens, de texto), comprar, dar a um jogador, embaralhar. */
    private javafx.scene.Node buildDecksSection() {
        ListView<com.vttcaseiro.model.CardDeck> list = new ListView<>();
        list.setPrefHeight(110);
        list.setPlaceholder(emptyHint("Nenhum baralho.\nUse + pra criar um (comum, tarô, de imagens ou de texto)."));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.vttcaseiro.model.CardDeck d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.getName() + "  ·  " + d.remaining() + " de " + d.getCards().size() + " no monte");
            }
        });
        Runnable reload = () -> {
            int sel = list.getSelectionModel().getSelectedIndex();
            list.getItems().setAll(deckStore.load());
            if (!list.getItems().isEmpty()) list.getSelectionModel().select(Math.max(0, Math.min(sel, list.getItems().size() - 1)));
        };
        reload.run();
        java.util.function.Consumer<com.vttcaseiro.model.CardDeck> persist = changed -> {
            List<com.vttcaseiro.model.CardDeck> all = deckStore.load();
            all.removeIf(d -> d.getId().equals(changed.getId()));
            all.add(Math.min(Math.max(0, list.getSelectionModel().getSelectedIndex()), all.size()), changed);
            deckStore.save(all);
            reload.run();
        };
        javafx.scene.control.MenuButton add = new javafx.scene.control.MenuButton("Novo baralho", com.vttcaseiro.view.Icons.icon("add", 14));
        javafx.scene.control.MenuItem std = new javafx.scene.control.MenuItem("Baralho comum (52 + coringas)");
        std.setOnAction(e -> addDeck(com.vttcaseiro.model.CardDeck.standard(), reload));
        javafx.scene.control.MenuItem tarot = new javafx.scene.control.MenuItem("Tarô (22 arcanos maiores)");
        tarot.setOnAction(e -> addDeck(com.vttcaseiro.model.CardDeck.tarot(), reload));
        javafx.scene.control.MenuItem fromImages = new javafx.scene.control.MenuItem("De imagens (uma carta por imagem)...");
        fromImages.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Imagens das cartas");
            chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files == null || files.isEmpty()) return;
            List<com.vttcaseiro.model.CardDeck.Card> cards = new ArrayList<>();
            for (File f : files) {
                try {
                    String id = imageAssetService.importFile(f.toPath());
                    cards.add(new com.vttcaseiro.model.CardDeck.Card(f.getName().replaceAll("\\.[^.]+$", ""), "", id));
                } catch (IOException ex) {
                    showError(f.getName() + ": " + ex.getMessage());
                }
            }
            if (cards.isEmpty()) return;
            TextInputDialog name = new TextInputDialog("Cartas");
            name.initOwner(stage);
            name.setHeaderText(null);
            name.setContentText("Nome do baralho:");
            addDeck(new com.vttcaseiro.model.CardDeck(name.showAndWait().orElse("Cartas"), cards), reload);
        });
        javafx.scene.control.MenuItem fromText = new javafx.scene.control.MenuItem("De texto (uma carta por linha)...");
        fromText.setOnAction(e -> {
            javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
            d.initOwner(stage);
            d.setTitle("Novo baralho");
            d.setHeaderText("Uma carta por linha. \"Nome: texto\" põe um texto na carta.");
            TextField name = new TextField();
            name.setPromptText("Eventos da estrada");
            javafx.scene.control.TextArea lines = new javafx.scene.control.TextArea();
            lines.setPromptText("Emboscada: 1d4 bandidos\nTempestade: viagem mais lenta\nMercador perdido");
            lines.setPrefRowCount(10);
            d.getDialogPane().setContent(new VBox(8, new Label("Nome"), name, new Label("Cartas"), lines));
            d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
            d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
                com.vttcaseiro.model.CardDeck deck = com.vttcaseiro.model.CardDeck.fromLines(name.getText(), lines.getText());
                if (deck.getCards().isEmpty()) showError("Escreva pelo menos uma carta.");
                else addDeck(deck, reload);
            });
        });
        add.getItems().addAll(std, tarot, fromImages, fromText);
        add.setTooltip(new Tooltip("Novo baralho"));
        Button shuffle = new Button("Embaralhar (junta todas)", com.vttcaseiro.view.Icons.icon("undo", 14));
        shuffle.setOnAction(e -> {
            com.vttcaseiro.model.CardDeck d = list.getSelectionModel().getSelectedItem();
            if (d == null) return;
            d.shuffle(new java.util.Random());
            persist.accept(d);
            announce("O mestre embaralhou " + d.getName() + ".");
        });
        Button remove = new Button("Apagar o baralho", com.vttcaseiro.view.Icons.icon("trash", 14));
        remove.setOnAction(e -> {
            com.vttcaseiro.model.CardDeck d = list.getSelectionModel().getSelectedItem();
            if (d == null) return;
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Apagar o baralho \"" + d.getName() + "\"?");
            c.initOwner(stage);
            c.setHeaderText(null);
            if (c.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isEmpty()) return;
            List<com.vttcaseiro.model.CardDeck> all = deckStore.load();
            all.removeIf(x -> x.getId().equals(d.getId()));
            deckStore.save(all);
            reload.run();
        });
        Button show = new Button("Comprar", com.vttcaseiro.view.Icons.icon("eye", 14));
        show.setTooltip(new Tooltip("Compra a de cima e mostra a todos no chat"));
        show.getStyleClass().add("button-primary");
        show.setOnAction(e -> drawCard(list.getSelectionModel().getSelectedItem(), null, false, persist));
        Button secret = new Button("Só pra mim", com.vttcaseiro.view.Icons.icon("hidden", 14));
        secret.setTooltip(new Tooltip("Compra a de cima e só você vê"));
        secret.setOnAction(e -> drawCard(list.getSelectionModel().getSelectedItem(), null, true, persist));
        Button give = new Button("Dar a um jogador...", com.vttcaseiro.view.Icons.icon("send", 14));
        javafx.scene.control.ContextMenu giveMenu = new javafx.scene.control.ContextMenu();
        give.setOnAction(e -> {
            javafx.scene.control.ContextMenu give2 = giveMenu;
            give2.getItems().clear();
            List<String> names = new ArrayList<>(networkService.getPlayerIdsByName().keySet());
            if (names.isEmpty()) {
                javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem("Nenhum jogador conectado");
                none.setDisable(true);
                give2.getItems().add(none);
            }
            for (String p : names) {
                javafx.scene.control.MenuItem it = new javafx.scene.control.MenuItem(p);
                it.setOnAction(a -> drawCard(list.getSelectionModel().getSelectedItem(), p, true, persist));
                give2.getItems().add(it);
            }
            give2.show(give, javafx.geometry.Side.BOTTOM, 0, 4);
        });
        give.setTooltip(new Tooltip("Compra e manda só pra um jogador (sussurro)"));
        javafx.scene.layout.TilePane actions = buttonGrid(show, secret, give);
        Label hint = new Label("A carta comprada sai do monte até embaralhar de novo. \"Comprar\" põe a carta no "
                + "chat (com a imagem, se tiver); \"Só pra mim\" só você vê; \"Dar a...\" manda só pro jogador.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        VBox box = new VBox(8, sectionHeader("Baralhos", iconize(add), iconize(shuffle), iconize(remove)), list, actions, hint);
        box.getStyleClass().add("panel-section");
        Runnable gmOnly = () -> {
            boolean gm = networkService.isGm();
            box.setVisible(gm);
            box.setManaged(gm);
        };
        networkService.statusProperty().addListener((o, a, b) -> Platform.runLater(gmOnly));
        gmOnly.run();
        return box;
    }

    private void addDeck(com.vttcaseiro.model.CardDeck deck, Runnable reload) {
        List<com.vttcaseiro.model.CardDeck> all = deckStore.load();
        all.add(deck);
        deckStore.save(all);
        reload.run();
        logEntries.add(LogEntry.system("Baralho criado: " + deck.getName() + " (" + deck.getCards().size() + " cartas)."));
    }

    /** Compra a carta de cima: mostra a todos, so pro mestre, ou so pro jogador {@code to}. */
    private void drawCard(com.vttcaseiro.model.CardDeck deck, String to, boolean secret,
                          java.util.function.Consumer<com.vttcaseiro.model.CardDeck> persist) {
        if (deck == null) {
            showError("Escolha um baralho na lista.");
            return;
        }
        com.vttcaseiro.model.CardDeck.Card card = deck.draw();
        if (card == null) {
            showError("O monte de \"" + deck.getName() + "\" acabou. Use Embaralhar.");
            return;
        }
        persist.accept(deck);
        String text = "Carta de " + deck.getName() + ": " + card.getName() + (card.getText().isBlank() ? "" : " -- " + card.getText());
        if (to != null) {
            if (card.getImageAssetId() != null && networkService.isConnected()) networkService.sendChatImage(card.getImageAssetId(), text, to);
            else onChatInput("/w \"" + to + "\" " + text);
            logEntries.add(LogEntry.system("Você deu a " + to + ": " + card.getName() + "."));
        } else if (secret) {
            logEntries.add(card.getImageAssetId() != null
                    ? LogEntry.image(networkService.getPlayerName(), text + " (só você vê)", card.getImageAssetId(), networkService.getPlayerName())
                    : LogEntry.system(text + " (só você vê)"));
        } else if (card.getImageAssetId() != null) {
            if (networkService.isConnected()) networkService.sendChatImage(card.getImageAssetId(), text, null);
            else logEntries.add(LogEntry.image(networkService.getPlayerName(), text, card.getImageAssetId(), null));
        } else {
            announce(text);
        }
    }

    private javafx.scene.Node buildTablesSection() {
        ListView<String> list = new ListView<>();
        list.setPrefHeight(130);
        Runnable reload = () -> list.getItems().setAll(tableStore.load().stream()
                .map(com.vttcaseiro.model.RandomTable::getName).toList());
        reload.run();
        CheckBox hidden = new CheckBox("Só pra mim");
        Button roll = new Button("Rolar a escolhida", com.vttcaseiro.view.Icons.icon("dice", 14));
        roll.getStyleClass().add("button-primary");
        roll.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(roll, Priority.ALWAYS);
        hidden.setMinWidth(Region.USE_PREF_SIZE);
        roll.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n != null) rollRandomTable(n, hidden.isSelected());
        });
        list.setOnMouseClicked(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && n != null) rollRandomTable(n, hidden.isSelected());
        });
        Button add = new Button("Nova tabela", com.vttcaseiro.view.Icons.icon("add", 14));
        add.setOnAction(e -> {
            editTable(null);
            reload.run();
        });
        Button edit = new Button("Editar a escolhida", com.vttcaseiro.view.Icons.icon("edit", 14));
        edit.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n != null) findTable(n).ifPresent(t -> {
                editTable(t);
                reload.run();
            });
        });
        Button remove = new Button("Apagar a escolhida", com.vttcaseiro.view.Icons.icon("trash", 14));
        remove.setOnAction(e -> {
            String n = list.getSelectionModel().getSelectedItem();
            if (n == null) return;
            List<com.vttcaseiro.model.RandomTable> all = tableStore.load();
            all.removeIf(t -> t.getName().equals(n));
            tableStore.save(all);
            reload.run();
        });
        Label hint = new Label("Clique duplo rola. No chat: /tabela Nome. Linhas com faixa: \"1-3: goblins\". "
                + "Dados no resultado já saem rolados (\"2d4 lobos\" vira \"2d4 [5] lobos\") e [[Outra tabela]] sorteia a outra.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        HBox rollRow = new HBox(8, roll, hidden);
        rollRow.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, sectionHeader("Tabelas aleatórias", iconize(add), iconize(edit), iconize(remove)), list,
                rollRow, hint);
        box.getStyleClass().add("panel-section");
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
        entries.setPromptText("1-3: 2d4 lobos\n4-5: um urso-coruja\n6: um druida perdido com [[Tesouro rapido]]");
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
        result.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -c-accent-text;");
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
        if (e.isShortcutDown() && !e.isAltDown() && e.getCode() == javafx.scene.input.KeyCode.C) {
            copyTokens();
            e.consume();
            return;
        }
        if (e.isShortcutDown() && !e.isAltDown() && e.getCode() == javafx.scene.input.KeyCode.V) {
            pasteTokens();
            e.consume();
            return;
        }
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        List<TokenModel> sel = mapView.getSelectedTokens();
        boolean handled = true;
        String action = Keybindings.actionFor(e.getCode());
        if (action != null && e.getCode() != javafx.scene.input.KeyCode.ESCAPE) {
            switch (action) {
                case "target" -> sel.forEach(mapView::toggleTarget);
                case "rotate" -> {
                    double deg = e.isShiftDown() ? -45 : 45;
                    for (TokenModel t : sel) if (table.canControl(t)) tokenActions.rotate(t, deg, false);
                }
                case "hide" -> {
                    if (networkService.isGm() && !sel.isEmpty()) {
                        boolean allHidden = sel.stream().allMatch(TokenModel::isHiddenFromPlayers);
                        sel.forEach(t -> tokenActions.setHidden(t, !allHidden));
                    }
                }
                case "tools.off" -> {
                    toolsOff.run();
                    mapView.clearSelection();
                }
                case "help" -> showShortcuts();
                default -> {
                    int n = Integer.parseInt(action.substring("tool.".length()));
                    if (n <= toolHotkeys.size()) {
                        // abre a categoria da ferramenta sozinho; neblina e paredes (6-9) so pro mestre
                        if (n <= 5 || networkService.isGm()) toolHotkeys.get(n - 1).fire();
                    }
                }
            }
            e.consume();
            return;
        }
        if (e.getCode() == javafx.scene.input.KeyCode.SPACE && mapView.addRulerWaypoint()) {
            e.consume(); // Espaco medindo/arrastando: parada na regua
            return;
        }
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
            case ESCAPE -> {
                toolsOff.run();
                mapView.clearSelection();
            }
            case UP -> mapView.nudgeSelection(0, -1);
            case DOWN -> mapView.nudgeSelection(0, 1);
            case LEFT -> mapView.nudgeSelection(-1, 0);
            case RIGHT -> mapView.nudgeSelection(1, 0);
            default -> handled = false;
        }
        if (handled) e.consume();
    }

    private void showShortcuts() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.initOwner(stage);
        help.setTitle("Atalhos do mapa");
        help.setHeaderText("Atalhos (com o foco fora dos campos de texto)");
        StringBuilder keys = new StringBuilder();
        for (var a : Keybindings.ACTIONS.entrySet()) {
            keys.append(Keybindings.label(Keybindings.keyFor(a.getKey()))).append("  ").append(a.getValue()).append('\n');
        }
        help.setContentText(keys + "Esc  desliga a ferramenta e limpa a seleção\n\n"
                + "Shift+arrastar no mapa vazio: seleciona vários tokens\n"
                + "Shift+clique: poe/tira um token da seleção\n"
                + "Setas: anda a seleção uma casa   Delete: remove\n"
                + "Ctrl+clique: mira   Alt+clique: ping   Shift+Alt+clique: olhem aqui (mestre)   Ctrl+Z: desfaz");
        javafx.scene.control.ButtonType change = new javafx.scene.control.ButtonType("Mudar atalhos...",
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        help.getButtonTypes().setAll(change, javafx.scene.control.ButtonType.OK);
        help.showAndWait().filter(b -> b == change).ifPresent(b -> showKeybindings());
    }

    private Runnable refreshToolKeyTips = () -> { };

    /** Janela de trocar as teclas: clica no botao da acao e aperta a tecla nova. */
    void showKeybindings() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Atalhos do teclado");
        d.setHeaderText("Clique numa tecla e aperte a nova. Esc, setas e Delete são fixos.");
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        java.util.Map<String, Button> buttons = new java.util.LinkedHashMap<>();
        Runnable paint = () -> buttons.forEach((a, b) -> b.setText(Keybindings.label(Keybindings.keyFor(a))));
        int row = 0;
        for (var a : Keybindings.ACTIONS.entrySet()) {
            Button key = new Button();
            key.setMinWidth(90);
            key.setFocusTraversable(false);
            key.setOnAction(e -> {
                key.setText("aperte...");
                key.requestFocus();
                key.setOnKeyPressed(k -> {
                    k.consume();
                    key.setOnKeyPressed(null);
                    if (k.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        paint.run();
                        return;
                    }
                    if (Keybindings.reserved(k.getCode())) {
                        showError("A tecla " + k.getCode().getName() + " é fixa (ou não pode ser atalho). Escolha outra.");
                        paint.run();
                        return;
                    }
                    String lost = Keybindings.set(a.getKey(), k.getCode());
                    if (lost != null) logEntries.add(LogEntry.system("A tecla " + Keybindings.label(k.getCode())
                            + " saiu de \"" + Keybindings.ACTIONS.get(lost) + "\" (ficou sem tecla)."));
                    paint.run();
                });
            });
            buttons.put(a.getKey(), key);
            g.addRow(row++, new Label(a.getValue()), key);
        }
        paint.run();
        javafx.scene.control.ButtonType reset = new javafx.scene.control.ButtonType("Voltar ao padrão",
                javafx.scene.control.ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(reset, javafx.scene.control.ButtonType.CLOSE);
        Button resetBtn = (Button) d.getDialogPane().lookupButton(reset);
        resetBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume(); // nao fecha
            Keybindings.resetAll();
            paint.run();
        });
        d.getDialogPane().setContent(g);
        d.showAndWait();
        refreshToolKeyTips.run();
    }

    private void onRollDice(String expression) {
        rollLabeled(null, expression, false);
    }

    private void onRollAdvantage() {
        if (rollsOverNetwork()) {
            sendModeRoll("Vantagem", "1d20", "advantage", 0, false);
            return;
        }
        addLocalRoll("Vantagem", DiceRoller.rollWithAdvantage(0), false);
    }

    private void onRollDisadvantage() {
        if (rollsOverNetwork()) {
            sendModeRoll("Desvantagem", "1d20", "disadvantage", 0, false);
            return;
        }
        addLocalRoll("Desvantagem", DiceRoller.rollWithDisadvantage(0), false);
    }

    // ---------------- modo de rolagem e "falar como" (seletor do chat)

    /** "publica", "mestre" (so voce e o mestre), "eu" (so voce: rola aqui), "cegas" (so o mestre ve). */
    private String rollMode = "publica";
    /** Nome com que as mensagens digitadas saem (null = o seu). */
    private String speakAs;
    private javafx.scene.control.MenuButton chatModeButton;

    /** Rola pela rede? (offline ou "so eu": rola aqui mesmo) */
    private boolean rollsOverNetwork() {
        return networkService.isConnected() && !"eu".equals(rollMode);
    }

    private void sendModeRoll(String label, String expression, String advMode, int modifier, boolean hidden) {
        networkService.sendRoll(label, expression, advMode, modifier, hidden || "mestre".equals(rollMode),
                "cegas".equals(rollMode));
    }

    /** Rolagem que so o mestre (e quem rolou) deve ver, pelo modo escolhido. */
    private boolean gmOnlyMode() {
        return "mestre".equals(rollMode) || "cegas".equals(rollMode) || "eu".equals(rollMode);
    }

    static String rollModeLabel(String mode) {
        return switch (mode) {
            case "mestre" -> "só pro mestre";
            case "eu" -> "só eu";
            case "cegas" -> "às cegas";
            default -> "pública";
        };
    }

    /** Texto do botao: "Lyra · pública". */
    private void refreshChatModeButton() {
        if (chatModeButton == null) return;
        String who = speakAs == null ? "Você" : speakAs;
        chatModeButton.setText(who + " · " + rollModeLabel(rollMode));
        chatModeButton.getStyleClass().remove("chat-mode-secret");
        if (!"publica".equals(rollMode)) chatModeButton.getStyleClass().add("chat-mode-secret");
    }

    /** Monta o menu na hora de abrir (os personagens e PdMs mudam durante o jogo). */
    private void fillChatModeMenu() {
        chatModeButton.getItems().clear();
        javafx.scene.control.ToggleGroup who = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.MenuItem whoTitle = new javafx.scene.control.MenuItem("Falar como");
        whoTitle.setDisable(true);
        chatModeButton.getItems().add(whoTitle);
        List<String> names = new ArrayList<>();
        names.add(null);
        boolean gm = !networkService.isConnected() || networkService.isGm();
        for (String n : characterService.list()) {
            try {
                CharacterSheet s = characterService.open(n);
                if (gm || java.util.Objects.equals(s.getOwnerPlayerId(), networkService.getPlayerId())) names.add(s.getName());
            } catch (IOException | RuntimeException ignored) {
                // ficha ilegivel
            }
        }
        if (gm) {
            for (TokenModel t : sceneService.getScene().getTokens()) {
                if (t.getMobSheetId() != null && !names.contains(t.getName())) names.add(t.getName());
            }
        }
        for (String n : names) {
            javafx.scene.control.RadioMenuItem it = new javafx.scene.control.RadioMenuItem(
                    n == null ? "Você (" + networkService.getPlayerName() + ")" : n);
            it.setToggleGroup(who);
            it.setSelected(java.util.Objects.equals(n, speakAs));
            it.setOnAction(e -> {
                speakAs = n;
                refreshChatModeButton();
            });
            chatModeButton.getItems().add(it);
        }
        chatModeButton.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem rollTitle = new javafx.scene.control.MenuItem("Rolagens");
        rollTitle.setDisable(true);
        chatModeButton.getItems().add(rollTitle);
        javafx.scene.control.ToggleGroup modes = new javafx.scene.control.ToggleGroup();
        String[][] opts = {{"publica", "Pública (todos veem)"}, {"mestre", "Só pro mestre (você e o mestre)"},
                {"eu", "Só eu (rola aqui, ninguém mais vê)"}, {"cegas", "Às cegas (só o mestre vê o resultado)"}};
        for (String[] o : opts) {
            javafx.scene.control.RadioMenuItem it = new javafx.scene.control.RadioMenuItem(o[1]);
            it.setToggleGroup(modes);
            it.setSelected(o[0].equals(rollMode));
            it.setOnAction(e -> {
                rollMode = o[0];
                refreshChatModeButton();
            });
            chatModeButton.getItems().add(it);
        }
    }

    /** Mensagem digitada no chat: sai com o nome escolhido em "falar como". */
    private void say(String text) {
        if (speakAs == null) {
            announce(text);
            return;
        }
        if (networkService.isConnected()) {
            networkService.sendChatAs(speakAs, text);
        } else {
            logEntries.add(LogEntry.chat(speakAs, com.vttcaseiro.dice.InlineRolls.expand(text)));
        }
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

    /** Tamanhos da interface oferecidos nas configuracoes (1 = o do Windows). */
    static final double[] UI_SCALES = {0.9, 1.0, 1.1, 1.25, 1.5, 1.75};

    /**
     * Tamanho da letra e da interface escolhido nas configuracoes: multiplica a escala do Windows (precisa ser antes
     * do JavaFX abrir, por isso vale na proxima vez que o programa abrir).
     */
    static void applyUiScale() {
        double chosen;
        try {
            chosen = Double.parseDouble(AppSettings.getString("ui.scale", "1"));
        } catch (RuntimeException ex) {
            chosen = 1;
        }
        if (Math.abs(chosen - 1) < 0.01 || System.getProperty("glass.win.uiScale") != null) return;
        double system = 1;
        try {
            system = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (Throwable ignored) {
            // sem tela pra perguntar: usa 1
        }
        String v = String.valueOf(Math.round(system * chosen * 100) / 100.0);
        System.setProperty("glass.win.uiScale", v);
        System.setProperty("glass.gtk.uiScale", v);
    }

    public static void main(String[] args) {
        // O JavaFX reserva so 512 MB de memoria de video por padrao; mapa grande + tokens + troca de cena passavam
        // disso e a tela congelava ("Texture ... is null"). E so um teto: a placa usa o que tiver.
        if (System.getProperty("prism.maxvram") == null) System.setProperty("prism.maxvram", "2G");
        applyUiScale();
        launch(args);
    }
}
