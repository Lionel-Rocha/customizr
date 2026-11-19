package com.vttcaseiro.net;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.party.Party;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * O lado do jogador: conecta no GameServer, manda as acoes (mover token,
 * rolar dado, chat...) e recebe as atualizacoes de todo mundo via
 * {@link Listener}. Tem um id proprio ({@link #getPlayerId()}) que o
 * servidor usa pra saber quem e dono de qual token -- ESTAVEL entre uma
 * conexao e outra quando passado no construtor (ver com.vttcaseiro.app.
 * AppSettings.getOrCreatePlayerId()), pra reconectar nao fazer perder o
 * controle dos proprios tokens; so e gerado aleatorio aqui dentro se
 * nada for passado. Tem tambem um heartbeat (ping a cada 10s, timeout
 * de 30s sem resposta)
 * pra detectar quando a conexao caiu de verdade -- sem isso, um cabo de
 * rede arrancado no meio do jogo so aparece como "silencio", nunca como
 * um aviso de verdade na tela.
 * <p>
 * IMPORTANTE pra quem for ligar isso na tela (Main.java): os callbacks do
 * Listener rodam numa thread de rede, NAO na thread do JavaFX. Qualquer
 * coisa que mexer em componente de tela dentro de um callback precisa
 * estar dentro de {@code javafx.application.Platform.runLater(() -> ...)}.
 */
public class GameClient {
    private static final Log log = Log.of(GameClient.class);
    private static final long HEARTBEAT_INTERVAL_MS = 10_000;
    private static final long HEARTBEAT_TIMEOUT_MS = 30_000;

    public interface Listener {
        void onSceneSync(SceneModel scene);

        void onTokenMoved(String tokenId, double x, double y);

        void onTokenAdded(TokenModel token);

        void onTokenRemoved(String tokenId);

        void onRollResult(String player, String detail, int total);

        /** Versao com rotulo e d20 natural (critico). O padrao cai na
         * versao antiga, pra quem nao precisar disso. */
        default void onRollResultLabeled(String player, String label, String detail, int total, Integer naturalD20) {
            onRollResult(player, detail, total);
        }

        /** Rolagem completa, com o aviso de escondida (so voce + mestres). */
        default void onRollResultFull(String player, String label, String detail, int total,
                                      Integer naturalD20, boolean hidden) {
            onRollResultLabeled(player, label, detail, total, naturalD20);
        }

        /** Sussurro (so voce e o destinatario veem). */
        default void onWhisper(String player, String to, String text) {
            onChat(player, "(sussurro para " + to + ") " + text);
        }

        /** O mestre mudou a neblina de guerra. */
        default void onMapPing(String player, double x, double y) {
        }

        default void onGmRequest(String player, com.vttcaseiro.model.GmRequest request) {
        }

        default void onDrawingAdded(com.vttcaseiro.model.Drawing drawing) {
        }

        default void onAudio(com.vttcaseiro.model.AudioCommand command) {
        }

        default void onPinsSync(java.util.List<com.vttcaseiro.model.MapPin> pins) {
        }

        default void onObjectsSync(java.util.List<com.vttcaseiro.model.MapObject> objects) {
        }

        /** Uma imagem que faltava chegou (o mapa redesenha o que usava ela). */
        default void onImageStored(String assetId) {
        }

        /** Penumbra e neblina pela visao (chega logo antes do onWallsSync). */
        default void onSceneLight(Boolean dim, Boolean fogByVision) {
        }

        default void onWallsSync(java.util.List<com.vttcaseiro.model.Wall> walls, Boolean visionOn, Boolean dark) {
        }

        default void onDrawingRemoved(String id) {
        }

        default void onDrawingsCleared(String ownerId) {
        }

        default void onHandoutSync(com.vttcaseiro.model.Handout handout) {
        }

        default void onHandoutRemoved(String id) {
        }

        default void onFogSync(com.vttcaseiro.model.Fog fog) {
        }

        /** O mestre mudou rodada/turno/efeitos com duracao. */
        default void onEncounterSync(com.vttcaseiro.model.Encounter encounter) {
        }

        /** Lista de quem esta conectado, com id e papel. */
        default void onPlayers(java.util.List<NetMessage.PlayerInfo> players) {
        }

        /** PV/condicoes/efeitos/iniciativa de um token mudaram. */
        default void onTokenUpdated(TokenModel token) {
        }

        void onChat(String player, String text);

        /** Ficha de personagem (de qualquer jogador) chegou/mudou --
         * inclui o inventario individual dela, porque Inventory vive
         * dentro de CharacterSheet. */
        void onCharacterSync(CharacterSheet character);

        /** Ficha de monstro (criada por qualquer um, geralmente o mestre)
         * chegou/mudou -- inclui o loot dela. */
        void onMobSync(MobSheet mob);

        /** A Party da mesa chegou/mudou -- inclui o inventario
         * COMPARTILHADO do grupo. */
        void onPartySync(Party party);

        /** Chamado no maximo uma vez por conexao: quando o socket cai, o
         * servidor fecha a ligacao, ou o heartbeat nao recebe pong a
         * tempo. Depois disso o GameClient esta morto -- pra reconectar,
         * crie um GameClient novo. */
        void onDisconnected(String reason);

        /** Chamado toda vez que algum jogador entra ou sai da mesa --
         * onlinePlayers e a lista inteira de quem esta conectado agora
         * (ja formatada, com "(mestre)" no nome de quem for), nao so a
         * novidade. */
        void onPresenceUpdate(java.util.List<String> onlinePlayers);
    }

    private final Listener listener;
    /** Id estavel deste jogador. Antes era SEMPRE um UUID novo gerado
     * aqui mesmo, o que quebrava a "retomada de sessao": TokenModel.
     * ownerId guarda esse id, e PlayerSession.owns() compara por ele --
     * entao reconectar com um id diferente fazia o jogador perder o
     * controle dos proprios tokens, mesmo sendo a mesma pessoa na mesma
     * maquina. Agora quem cria o GameClient pode passar um id estavel
     * (ver com.vttcaseiro.app.AppSettings.getOrCreatePlayerId(),
     * guardado em disco e reaproveitado entre uma conexao e outra); sem
     * passar nenhum (construtores de 1 argumento, usados por quem so
     * quer testar o socket em si), continua gerando um UUID novo. */
    private final String playerId;
    private final AtomicBoolean disconnectReported = new AtomicBoolean(false);
    /** Asset store da mesa DESTE jogador -- null se nao tiver mesa por
     * tras (mesmo motivo de GameServer.imageAssetStore ser null em
     * teste manual via socket). */
    private final ImageAssetStore imageAssetStore;
    /** Ids de asset ja mandados nesta conexao -- evita reenviar a MESMA
     * imagem de novo a cada vez que a ficha/token que a usa e
     * sincronizada de novo (por exemplo, toda vez que a ficha e salva).
     * So existe durante a vida desta conexao -- reconectar limpa isso,
     * o que e aceitavel: o pior caso e mandar de novo algo que o outro
     * lado ja tem, nunca deixar de mandar algo que falta. */
    private final Set<String> pushedAssetIds = ConcurrentHashMap.newKeySet();
    private PlayerSession mySession;
    private String tablePassword;
    private String masterPassword;
    private Socket socket;
    private PrintWriter out;
    private Thread listenThread;
    private Timer heartbeatTimer;
    private volatile boolean running = false;
    private volatile String pendingName;
    private volatile boolean pendingGm;
    private volatile long lastPongTime;

    public GameClient(Listener listener) {
        this(listener, null, null);
    }

    public GameClient(Listener listener, ImageAssetStore imageAssetStore) {
        this(listener, imageAssetStore, null);
    }

    /** playerId null = gera um UUID novo (comportamento antigo, usado por
     * quem so quer testar o socket). Pra retomada de sessao funcionar,
     * passe um id estavel -- ver o javadoc do campo acima. */
    public GameClient(Listener listener, ImageAssetStore imageAssetStore, String playerId) {
        this.listener = listener;
        this.imageAssetStore = imageAssetStore;
        this.playerId = playerId != null && !playerId.isBlank() ? playerId : UUID.randomUUID().toString();
    }

    public String getPlayerId() {
        return playerId;
    }

    /** Null ate connect() ser chamado. */
    public PlayerSession getSession() {
        return mySession;
    }

    private boolean tlsEnabled;
    private TlsSupport.TrustPrompt tlsPrompt;

    /** Conectar com criptografia (chame antes de connect). prompt null = aceita codigo novo sem perguntar. */
    public void useTls(TlsSupport.TrustPrompt prompt) {
        this.tlsEnabled = true;
        this.tlsPrompt = prompt;
    }

    /** Codigo da mesa na ponte (null = conexao direta). host/port viram o endereco da ponte. */
    private String relayCode;

    public void useRelay(String code) {
        this.relayCode = code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }

    public void connect(String host, int port, String playerName, boolean isGm) throws IOException {
        connect(host, port, playerName, isGm, null, null);
    }

    /** Igual {@link #connect(String, int, String, boolean)}, mas
     * mandando as senhas de mesa/mestre no "hello" (ver GameServer.
     * handleIncoming, caso "hello") -- null/vazio funciona exatamente
     * como antes (mesa sem senha, pedido de mestre sem senha). */
    public void connect(String host, int port, String playerName, boolean isGm,
            String tablePassword, String masterPassword) throws IOException {
        this.mySession = new PlayerSession(playerId, playerName, isGm);
        this.tablePassword = tablePassword;
        this.masterPassword = masterPassword;
        if (relayCode != null) {
            // Pela ponte: a conexao vem ja ligada ao mestre; o TLS (se tiver) e de ponta a ponta.
            Socket raw = com.vttcaseiro.relay.RelayHostLink.join(host, port, relayCode);
            if (tlsEnabled) {
                javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) TlsSupport.trustAllClient().getSocketFactory()
                        .createSocket(raw, host, port, true);
                TlsSupport.verify(s, "ponte:" + relayCode, tlsPrompt);
                socket = s;
            } else {
                socket = raw;
            }
        } else if (tlsEnabled) {
            javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) TlsSupport.trustAllClient().getSocketFactory()
                    .createSocket(host, port);
            TlsSupport.verify(s, host + ":" + port, tlsPrompt); // confere o codigo ANTES de mandar a senha
            socket = s;
        } else {
            socket = new Socket(host, port);
        }
        out = new PrintWriter(
                new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        running = true;
        lastPongTime = System.currentTimeMillis();
        listenThread = new Thread(() -> listenLoop(playerName, isGm), "game-client-listen");
        listenThread.setDaemon(true);
        listenThread.start();
        startHeartbeat();
    }

    private void startHeartbeat() {
        heartbeatTimer = new Timer("game-client-heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                if (System.currentTimeMillis() - lastPongTime > HEARTBEAT_TIMEOUT_MS) {
                    fail("Sem resposta do servidor por " + (HEARTBEAT_TIMEOUT_MS / 1000) + " segundos.");
                    return;
                }
                send(NetMessage.ping());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void listenLoop(String playerName, boolean isGm) {
        // O hello so sai quando chega o desafio do servidor (ver dispatch,
        // caso "challenge") -- a senha vai como prova HMAC, nunca em texto.
        this.pendingName = playerName;
        this.pendingGm = isGm;
        try (InputStreamReader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)) {
            BoundedLineReader in = new BoundedLineReader(reader);
            String line;
            while (running && (line = in.readLine()) != null) {
                try {
                    dispatch(NetCodec.decode(line));
                } catch (RuntimeException ex) {
                    // Antes, UMA linha invalida (ou um erro no listener) matava
                    // esta thread em silencio: a conexao parecia viva, mas nada
                    // mais chegava. Agora so essa mensagem e descartada.
                    log.warn("Mensagem inválida do servidor ignorada: " + ex.getMessage());
                }
            }
            if (running) {
                fail("O servidor fechou a conexão.");
            }
        } catch (IOException ex) {
            if (running) {
                fail("Conexão perdida: " + ex.getMessage());
            }
        }
    }

    /** Marca a conexao como morta e avisa o Listener exatamente uma vez,
     * mesmo que o heartbeat e a thread de leitura detectem o problema
     * quase ao mesmo tempo (AtomicBoolean em vez de um "if" simples --
     * duas threads diferentes podem chegar aqui juntas). */
    private void fail(String reason) {
        running = false;
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        closeSocketQuietly();
        if (disconnectReported.compareAndSet(false, true)) {
            log.warn("Cliente desconectado: " + reason);
            listener.onDisconnected(reason);
        }
    }

    private void closeSocketQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void dispatch(NetMessage msg) {
        if (msg == null || msg.type == null) return;
        switch (msg.type) {
            case "scene_sync" -> {
                listener.onSceneSync(msg.scene);
                if (msg.scene != null) {
                    requestImageIfMissing(msg.scene.getBackgroundImageAssetId());
                    for (TokenModel token : msg.scene.getTokens()) {
                        requestImageIfMissing(token.getImageAssetId());
                    }
                    for (com.vttcaseiro.model.MapObject o : msg.scene.getObjects()) {
                        requestImageIfMissing(o.getAssetId());
                    }
                }
            }
            case "move_token" -> listener.onTokenMoved(msg.tokenId, msg.x, msg.y);
            case "add_token" -> {
                listener.onTokenAdded(msg.token);
                if (msg.token != null) {
                    requestImageIfMissing(msg.token.getImageAssetId());
                }
            }
            case "remove_token" -> listener.onTokenRemoved(msg.tokenId);
            case "roll_result" -> listener.onRollResultFull(msg.player, msg.label, msg.detail,
                    msg.total == null ? 0 : msg.total, msg.naturalD20, Boolean.TRUE.equals(msg.hidden));
            case "update_token" -> listener.onTokenUpdated(msg.token);
            case "chat" -> {
                if (msg.whisperTo != null && !msg.whisperTo.isBlank()) {
                    listener.onWhisper(msg.player, msg.whisperTo, msg.text);
                } else {
                    listener.onChat(msg.player, msg.text);
                }
            }
            case "character_sync" -> {
                listener.onCharacterSync(msg.character);
                if (msg.character != null) {
                    requestImageIfMissing(msg.character.getPortraitImageAssetId());
                }
            }
            case "mob_sync" -> {
                listener.onMobSync(msg.mob);
                if (msg.mob != null) {
                    requestImageIfMissing(msg.mob.getPortraitImageAssetId());
                }
            }
            case "party_sync" -> listener.onPartySync(msg.party);
            case "image_data" -> {
                if (ImageAssetRelay.storeIncoming(imageAssetStore, msg)) listener.onImageStored(msg.assetId);
            }
            case "image_request" -> respondToImageRequest(msg.assetId);
            case "presence" -> {
                listener.onPresenceUpdate(msg.onlinePlayers != null ? msg.onlinePlayers : java.util.List.of());
                listener.onPlayers(msg.players != null ? msg.players : java.util.List.of());
            }
            case "encounter_sync" -> listener.onEncounterSync(msg.encounter);
            case "fog_sync" -> listener.onFogSync(msg.fog);
            case "audio" -> {
                if (msg.audio != null) listener.onAudio(msg.audio);
            }
            case "walls_sync" -> {
                listener.onSceneLight(msg.sceneDim, msg.fogByVision);
                listener.onWallsSync(msg.walls, msg.visionOn, msg.sceneDark);
            }
            case "pins_sync" -> listener.onPinsSync(msg.pins);
            case "objects_sync" -> {
                listener.onObjectsSync(msg.objects);
                if (msg.objects != null) for (com.vttcaseiro.model.MapObject o : msg.objects) requestImageIfMissing(o.getAssetId());
            }
            case "drawing_add" -> {
                if (msg.drawing != null) listener.onDrawingAdded(msg.drawing);
            }
            case "drawing_remove" -> listener.onDrawingRemoved(msg.text);
            case "drawing_clear" -> listener.onDrawingsCleared(msg.text);
            case "handout_sync" -> {
                if (msg.handout != null) {
                    requestImageIfMissing(msg.handout.getImageAssetId());
                    listener.onHandoutSync(msg.handout);
                }
            }
            case "handout_remove" -> listener.onHandoutRemoved(msg.text);
            case "gm_request" -> {
                if (msg.gmRequest != null) listener.onGmRequest(msg.player, msg.gmRequest);
            }
            case "map_ping" -> {
                if (msg.x != null && msg.y != null) listener.onMapPing(msg.player, msg.x, msg.y);
            }
            case "pong" -> lastPongTime = System.currentTimeMillis();
            case "challenge" -> send(NetMessage.hello(pendingName, playerId, pendingGm, tablePassword,
                    masterPassword, msg.challenge));
            default -> log.warn("Cliente recebeu tipo desconhecido: " + msg.type);
        }
    }

    private synchronized void send(NetMessage message) {
        if (out != null) {
            out.println(NetCodec.encode(message));
        }
    }

    public void sendMoveToken(String tokenId, double x, double y) {
        send(NetMessage.moveToken(tokenId, x, y));
    }

    /** Copia do token sem a imagem em base64 quando ela ja existe como asset
     * (a imagem vai uma vez so, por image_data -- nao em toda mensagem). */
    private TokenModel withoutInlineImage(TokenModel token) {
        if (token == null || token.getImageData() == null || token.getImageAssetId() == null
                || imageAssetStore == null || !imageAssetStore.exists(token.getImageAssetId())) {
            return token;
        }
        TokenModel copy = NetCodec.deepCopy(token, TokenModel.class);
        copy.setImageData(null);
        return copy;
    }

    private SceneModel withoutInlineImages(SceneModel scene) {
        if (scene == null || imageAssetStore == null) return scene;
        boolean bgInline = scene.getBackgroundImageData() != null && scene.getBackgroundImageAssetId() != null
                && imageAssetStore.exists(scene.getBackgroundImageAssetId());
        boolean tokenInline = scene.getTokens().stream().anyMatch(t -> withoutInlineImage(t) != t);
        if (!bgInline && !tokenInline) return scene;
        SceneModel copy = NetCodec.deepCopy(scene, SceneModel.class);
        if (bgInline) copy.setBackgroundImageData(null);
        for (TokenModel t : copy.getTokens()) {
            if (t.getImageAssetId() != null && imageAssetStore.exists(t.getImageAssetId())) t.setImageData(null);
        }
        return copy;
    }

    public void sendAddToken(TokenModel token) {
        send(NetMessage.addToken(withoutInlineImage(token)));
        if (token != null) {
            pushImageAssetIfNew(token.getImageAssetId());
        }
    }

    public void sendUpdateToken(TokenModel token) {
        send(NetMessage.updateToken(withoutInlineImage(token)));
    }

    /** Rolagem com rotulo: mode = null/"normal", "advantage" ou "disadvantage"
     * (nos dois ultimos a expressao e ignorada e vale 1d20 + modifier). */
    public void sendLabeledRoll(String player, String label, String expression, String mode, int modifier) {
        NetMessage m = NetMessage.roll(player, expression);
        m.label = label;
        if (mode != null && !"normal".equals(mode)) {
            m.rollMode = mode;
            m.modifier = modifier;
        }
        send(m);
    }

    public void sendAudio(com.vttcaseiro.model.AudioCommand c) {
        send(NetMessage.audio(c));
    }

    public void sendWalls(SceneModel s) {
        send(NetMessage.wallsSync(s));
    }

    public void sendObjects(SceneModel s) {
        for (com.vttcaseiro.model.MapObject o : s.getObjects()) pushImageAssetIfNew(o.getAssetId());
        send(NetMessage.objectsSync(s.getObjects()));
    }

    public void sendPins(SceneModel s) {
        send(NetMessage.pinsSync(s.getPins()));
    }

    public void sendDoorToggle(String wallId) {
        send(NetMessage.doorToggle(wallId));
    }

    public void sendDrawingAdd(com.vttcaseiro.model.Drawing d) {
        send(NetMessage.drawingAdd(d));
    }

    public void sendDrawingRemove(String id) {
        send(NetMessage.drawingRemove(id));
    }

    public void sendDrawingClear(String ownerId) {
        send(NetMessage.drawingClear(ownerId));
    }

    public void sendHandout(com.vttcaseiro.model.Handout h) {
        pushImageAssetIfNew(h.getImageAssetId());
        send(NetMessage.handoutSync(h));
    }

    public void sendHandoutRemove(String id) {
        send(NetMessage.handoutRemove(id));
    }

    public void sendGmRequest(com.vttcaseiro.model.GmRequest request) {
        send(NetMessage.gmRequest(request));
    }

    public void sendMapPing(double x, double y) {
        send(NetMessage.mapPing(x, y));
    }

    public void sendFog(com.vttcaseiro.model.Fog fog) {
        send(NetMessage.fogSync(fog));
    }

    public void sendEncounter(com.vttcaseiro.model.Encounter encounter) {
        send(NetMessage.encounterSync(encounter));
    }

    public void sendWhisper(String player, String to, String text) {
        NetMessage m = NetMessage.chat(player, text);
        m.whisperTo = to;
        send(m);
    }

    /** Igual sendLabeledRoll, com a opcao de rolar escondido. */
    public void sendRoll(String player, String label, String expression, String mode, int modifier, boolean hidden) {
        NetMessage m = NetMessage.roll(player, expression);
        m.label = label;
        if (mode != null && !"normal".equals(mode)) {
            m.rollMode = mode;
            m.modifier = modifier;
        }
        if (hidden) m.hidden = true;
        send(m);
    }

    public void sendRemoveToken(String tokenId) {
        send(NetMessage.removeToken(tokenId));
    }

    public void sendRoll(String player, String expression) {
        send(NetMessage.roll(player, expression));
    }

    public void sendRollAdvantage(String player, int modifier) {
        send(NetMessage.rollAdvantage(player, modifier));
    }

    public void sendRollDisadvantage(String player, int modifier) {
        send(NetMessage.rollDisadvantage(player, modifier));
    }

    public void sendChat(String player, String text) {
        send(NetMessage.chat(player, text));
    }

    public void sendCharacterSync(CharacterSheet character) {
        send(NetMessage.characterSync(character));
        if (character != null) {
            pushImageAssetIfNew(character.getPortraitImageAssetId());
        }
    }

    public void sendMobSync(MobSheet mob) {
        send(NetMessage.mobSync(mob));
        if (mob != null) {
            pushImageAssetIfNew(mob.getPortraitImageAssetId());
        }
    }

    public void sendPartySync(Party party) {
        send(NetMessage.partySync(party));
    }

    /** So deve ser chamado por quem e mestre -- o servidor confere de
     * novo do lado dele (ver GameServer.handleIncoming, caso
     * "scene_sync"), entao mandar isso sem ser mestre simplesmente nao
     * tem efeito nenhum, nao precisa checar aqui tambem. */
    public void sendSceneSwitch(SceneModel scene) {
        send(NetMessage.sceneSwitch(withoutInlineImages(scene)));
        if (scene != null) {
            pushImageAssetIfNew(scene.getBackgroundImageAssetId());
            for (TokenModel token : scene.getTokens()) {
                pushImageAssetIfNew(token.getImageAssetId());
            }
            for (com.vttcaseiro.model.MapObject o : scene.getObjects()) {
                pushImageAssetIfNew(o.getAssetId());
            }
        }
    }

    /** Manda os bytes de um asset de imagem pra quem esta do outro lado,
     * SE: (1) existe mesmo (assetId nao e null), (2) este jogador tem
     * uma mesa com asset store de verdade, (3) o asset esta guardado
     * localmente, e (4) ainda nao foi mandado nesta mesma conexao. Isso
     * e o que faz uma imagem nova (de um token, ficha, monstro ou mapa)
     * realmente chegar nos outros jogadores -- sem isso, so o ID
     * trafegaria, e o outro lado nunca teria os bytes de verdade. */
    public void pushImageAssetIfNew(String assetId) {
        if (assetId == null || imageAssetStore == null) {
            return;
        }
        if (!pushedAssetIds.add(assetId)) {
            return; // ja mandado nesta conexao
        }
        if (!imageAssetStore.exists(assetId)) {
            return; // nada pra mandar (pode ser um id que chegou da rede, nao criado aqui)
        }
        try {
            send(ImageAssetRelay.encode(imageAssetStore, assetId));
        } catch (IOException ex) {
            log.warn("Não foi possível mandar o asset " + assetId + ": " + ex.getMessage());
        }
    }

    /** Pede pela rede um asset que acabou de chegar referenciado (num
     * token/ficha/monstro/cena) mas que eu ainda nao tenho guardado --
     * o servidor responde direto se tiver, ou repassa o pedido pra quem
     * tiver (ver GameServer, caso "image_request"). */
    private void requestImageIfMissing(String assetId) {
        if (assetId == null || imageAssetStore == null || imageAssetStore.exists(assetId)) {
            return;
        }
        send(NetMessage.imageRequest(assetId));
    }

    /** Alguem pediu um asset que talvez so EU tenha (ver GameServer,
     * caso "image_request" repassado quando o servidor nao tem o asset
     * guardado) -- responde so se eu realmente tiver. */
    private void respondToImageRequest(String assetId) {
        if (assetId == null || imageAssetStore == null || !imageAssetStore.exists(assetId)) {
            return;
        }
        try {
            send(ImageAssetRelay.encode(imageAssetStore, assetId));
        } catch (IOException ex) {
            log.warn("Não foi possível responder ao pedido do asset " + assetId + ": " + ex.getMessage());
        }
    }

    /** Desconexao pedida pelo proprio jogador (ex.: fechou o programa) --
     * nao dispara onDisconnected, porque isso e normal, nao uma falha. */
    public void disconnect() {
        running = false;
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        closeSocketQuietly();
    }
}
