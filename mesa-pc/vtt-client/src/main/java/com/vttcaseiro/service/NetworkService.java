package com.vttcaseiro.service;

import com.vttcaseiro.app.AppSettings;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.GameClient;
import com.vttcaseiro.net.GameServer;
import com.vttcaseiro.party.Party;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.List;

/**
 * Dono do ciclo de vida da conexao (hospedar / conectar / desconectar).
 * Antes o Main.java chamava {@code new GameServer(...)}/{@code new
 * GameClient(...)} direto e espalhava "if (netClient != null)" em uns
 * dez metodos diferentes. Agora e so um lugar: {@link #isConnected()}
 * pra saber, os metodos {@code sendXxx} ja ignoram a chamada sozinhos
 * quando offline (nao precisa mais checar nulo no chamador), e
 * {@link #statusProperty()} e uma property de JavaFX de verdade -- a
 * tela pode fazer {@code label.textProperty().bind(...)} em vez de ficar
 * chamando {@code setText(...)} manualmente toda vez que algo muda.
 */
public class NetworkService {
    private static final Log log = Log.of(NetworkService.class);

    /** Igual GameClient.Listener -- existe separado pra quem usa este
     * servico nunca precisar importar nada de com.vttcaseiro.net
     * diretamente (a unica porta de entrada/saida da rede e esta classe). */
    public interface GameListener {
        void onSceneSync(SceneModel scene);

        /** So pro mestre: nome da cena que chegou e se e uma cena separada do grupo. */
        default void onSceneView(String sceneKey, boolean side) {
        }

        /** So pro mestre: cena separada que ficou vazia (o jogador voltou ou saiu), pra gravar. */
        default void onSideSceneClosed(String sceneKey, SceneModel scene) {
        }

        /** Lista de jogadores mudou (quem esta onde). */
        default void onPlayersChanged() {
        }

        default void onZonesSync(java.util.List<com.vttcaseiro.model.MapZone> zones) {
        }

        default void onMapPull(String player, double x, double y) {
        }

        default void onPoll(String id, String author, String question, java.util.List<String> options,
                            java.util.List<Integer> counts, Integer myVote) {
        }

        default void onShops(java.util.List<com.vttcaseiro.model.Shop> shops) {
        }

        default void onShopRequest(com.vttcaseiro.net.NetMessage.ShopRequest request) {
        }

        default void onCameraLock(boolean locked, double x, double y, double zoom) {
        }

        default void onZoneEvent(com.vttcaseiro.model.MapZone zone, TokenModel token, String sceneKey, String ownerName) {
        }

        void onTokenMoved(String tokenId, double x, double y);

        void onTokenAdded(TokenModel token);

        void onTokenRemoved(String tokenId);

        void onRollResult(String player, String detail, int total);

        default void onRollResultLabeled(String player, String label, String detail, int total, Integer naturalD20) {
            onRollResult(player, detail, total);
        }

        default void onTokenUpdated(TokenModel token) {
        }

        default void onRollResultFull(String player, String label, String detail, int total,
                                      Integer naturalD20, boolean hidden) {
            onRollResultLabeled(player, label, detail, total, naturalD20);
        }

        default void onWhisper(String player, String to, String text) {
            onChat(player, "(sussurro para " + to + ") " + text);
        }

        default void onEncounterSync(com.vttcaseiro.model.Encounter encounter) {
        }

        default void onFogSync(com.vttcaseiro.model.Fog fog) {
        }

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

        default void onImageStored(String assetId) {
        }

        default void onSceneLight(Boolean dim, Boolean fogByVision) {
        }

        default void onMapLights(java.util.List<com.vttcaseiro.model.MapLight> lights) {
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

        default void onHandoutShow(com.vttcaseiro.model.Handout handout) {
        }

        default void onGroupNote(com.vttcaseiro.model.Handout note) {
        }

        default void onGroupNoteRemoved(String id) {
        }

        void onChat(String player, String text);

        default void onChatImage(String player, String assetId, String caption, String whisperTo) {
        }

        /** Metodo com corpo padrao (nao-obrigatorio) de proposito: quem
         * ja implementava GameListener antes desta ficha/monstro/party
         * sincronizarem pela rede continua compilando sem mudar nada --
         * so precisa sobrescrever se realmente quiser reagir a isso. */
        default void onCharacterSync(CharacterSheet character) {
        }

        default void onMobSync(MobSheet mob) {
        }

        default void onCampaignSync(com.vttcaseiro.model.Campaign campaign) {
        }

        default void onCharacterRemoved(String id) {
        }

        default void onMobRemoved(String id) {
        }

        default void onPartySync(Party party) {
        }

        void onDisconnected(String reason);

        /** Caiu a conexao e o programa esta tentando voltar sozinho (tentativa {@code attempt} de {@code max}). */
        default void onReconnecting(int attempt, int max) {
        }

        /** Voltou sozinho depois de cair. */
        default void onReconnected() {
        }
    }

    private final StringProperty status = new SimpleStringProperty("Offline (tudo local)");
    private final ObservableList<String> onlinePlayers = FXCollections.observableArrayList();
    private GameClient client;
    private GameServer server;
    /** Pagina + WebSocket pra jogar pelo navegador (porta do jogo + 1). */
    private com.vttcaseiro.net.WebGateway webGateway;

    /** Ligacao com a ponte (mesa aberta pela internet sem abrir porta). null = sem ponte. */
    private com.vttcaseiro.relay.RelayHostLink relayLink;
    private String relayAddress;

    /**
     * Abre a mesa (ja hospedada aqui) numa ponte: jogadores entram pelo endereco da ponte + codigo.
     * @throws IOException se a ponte nao respondeu ou recusou o codigo
     */
    public void openRelay(String relayHost, int relayPort, String code, String secret,
                          java.util.function.Consumer<String> statusMessages) throws IOException {
        if (server == null) throw new IllegalStateException("Hospede a mesa primeiro.");
        closeRelay();
        com.vttcaseiro.net.WebGateway web = webGateway;
        com.vttcaseiro.relay.RelayHostLink link = new com.vttcaseiro.relay.RelayHostLink(relayHost, relayPort, code, secret,
                server::acceptTunneled, web == null ? null : web::acceptTunneled, statusMessages);
        link.start();
        relayLink = link;
        relayAddress = relayHost + ":" + relayPort;
    }

    public void closeRelay() {
        if (relayLink != null) relayLink.stop();
        relayLink = null;
        relayAddress = null;
    }

    public String getRelayCode() {
        return relayLink == null ? null : relayLink.getCode();
    }

    public String getRelayAddress() {
        return relayAddress;
    }

    /** Porta do cliente web (0 = desligado). */
    public int getWebPort() {
        return webGateway == null ? 0 : webGateway.getPort();
    }

    private void stopWebGateway() {
        if (webGateway != null) {
            webGateway.stop();
            webGateway = null;
        }
    }
    private String playerName = "Jogador";

    /** A property e da tela: muda na thread do JavaFX (a conexao agora roda em segundo plano). */
    private void setStatus(String text) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            status.set(text);
            return;
        }
        try {
            javafx.application.Platform.runLater(() -> status.set(text));
        } catch (IllegalStateException toolkitOff) {
            status.set(text); // testes sem JavaFX
        }
    }

    public ReadOnlyStringProperty statusProperty() {
        return status;
    }

    /** Quem esta conectado na mesa agora (o proprio jogador incluso),
     * atualizada automaticamente a cada entrada/saida -- liga um ListView
     * direto nela, nao precisa pedir pra atualizar na mao. Vazia quando
     * offline. */
    public ObservableList<String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public boolean isConnected() {
        return client != null;
    }

    public String getPlayerName() {
        return playerName;
    }

    /** @throws IllegalStateException se ja estiver conectado */
    /** Onde o servidor guarda as chaves dos jogadores (pasta da mesa). null = so na memoria. */
    private java.nio.file.Path playerKeysFile;

    public void setPlayerKeysFile(java.nio.file.Path file) {
        this.playerKeysFile = file;
    }

    /** Jogadores que o servidor desta mesa lembra (vazio se nao esta hospedando). */
    public java.util.List<com.vttcaseiro.net.PlayerKeys.Entry> rememberedPlayers() {
        return server == null ? java.util.List.of() : server.playerKeys().list();
    }

    /** Libera um id (o jogador trocou de computador): o proximo que entrar com ele vira o dono. */
    public boolean forgetPlayer(String playerId) {
        if (server != null) return server.playerKeys().forget(playerId);
        if (playerKeysFile == null) return false;
        return new com.vttcaseiro.net.PlayerKeys(playerKeysFile).forget(playerId);
    }

    public void host(int port, SceneModel scene, String playerName, GameListener listener) throws IOException {
        host(port, scene, playerName, null, listener);
    }

    /** Igual {@link #host(int, SceneModel, String, GameListener)}, mas
     * com o ImageAssetStore da mesa de quem hospeda -- sem ele passado,
     * imagens de token/ficha/monstro/mapa simplesmente nao viajam pela
     * rede (ver GameServer/GameClient/ImageAssetRelay). Null e aceito
     * (mesa sem biblioteca de verdade por tras).
     *
     * @throws IllegalStateException se ja estiver conectado
     */
    public void host(int port, SceneModel scene, String playerName, ImageAssetStore imageAssetStore,
            GameListener listener) throws IOException {
        host(port, scene, playerName, imageAssetStore, null, null, listener);
    }

    /** Igual o outro overload, mas com senha de mesa (quem entra precisa
     * saber) e senha de mestre (so quem souber esta e aceito como mestre
     * -- ver GameServer). Null/vazio nos dois = sem senha nenhuma, igual
     * sempre foi. Quem hospeda ja entra como mestre automaticamente, e
     * precisa saber a propria senha de mestre pra isso funcionar (passa
     * a mesma aqui e na conexao que o host faz em si mesmo).
     *
     * @throws IllegalStateException se ja estiver conectado
     */
    public void host(int port, SceneModel scene, String playerName, ImageAssetStore imageAssetStore,
            String tablePassword, String masterPassword, GameListener listener) throws IOException {
        host(port, scene, playerName, imageAssetStore, tablePassword, masterPassword, false, listener);
    }

    /** Codigo da mesa (TLS) de quem hospeda; null sem criptografia. */
    private String hostFingerprint;
    private boolean tls;

    public String getHostFingerprint() {
        return hostFingerprint;
    }

    /** A conexao atual (hospedando ou conectado) e criptografada? */
    public boolean isTls() {
        return tls;
    }

    /** Igual, com {@code tlsOn}: jogo e navegador criptografados (certificado proprio + codigo da mesa). */
    public void host(int port, SceneModel scene, String playerName, ImageAssetStore imageAssetStore,
            String tablePassword, String masterPassword, boolean tlsOn, GameListener listener) throws IOException {
        requireNotConnected();
        this.playerName = playerName;
        server = new GameServer(port, scene, imageAssetStore, tablePassword, masterPassword);
        javax.net.ssl.SSLContext tlsContext = null;
        if (tlsOn) {
            tlsContext = com.vttcaseiro.net.TlsSupport.hostContext();
            server.useTls(tlsContext);
            hostFingerprint = com.vttcaseiro.net.TlsSupport.hostFingerprint();
        } else {
            hostFingerprint = null;
        }
        this.tls = tlsOn;
        server.setPlayerKeysFile(playerKeysFile);
        server.start();
        // A parte do navegador abre ANTES do cliente do proprio mestre: a conexao dele ganha uma porta
        // automatica do Windows, que as vezes caia justamente na "porta do jogo + 1".
        try {
            webGateway = new com.vttcaseiro.net.WebGateway(server.getPort() + 1, server.getPort(), imageAssetStore);
            if (tlsContext != null) webGateway.useTls(tlsContext, true);
            webGateway.start();
        } catch (IOException ex) {
            // Navegador e opcional: sem a porta livre, a mesa funciona igual pelo programa.
            log.warn("Cliente web desligado (porta " + (server.getPort() + 1) + " ocupada): " + ex.getMessage());
            webGateway = null;
        }
        // Id ESTAVEL desta maquina (ver AppSettings.getOrCreatePlayerId) --
        // sem isso, cada vez que o programa fosse reaberto o mestre
        // perderia a propria "posse" dos tokens que ja controlava antes
        // (ownerId guarda esse id, nao o nome).
        client = new GameClient(adapt(listener), imageAssetStore, AppSettings.getOrCreatePlayerId());
        client.setPlayerSecret(AppSettings.getOrCreatePlayerKey());
        if (tlsOn) client.useTls((hp, fp, prev) -> true); // a propria mesa: confia no proprio certificado
        try {
            client.connect("localhost", server.getPort(), playerName, true, tablePassword, masterPassword);
        } catch (IOException | RuntimeException ex) {
            // Nao deixa um servidor orfao segurando a porta.
            client = null;
            stopWebGateway();
            server.stop();
            server = null;
            throw ex;
        }
        setStatus("Hospedando na porta " + server.getPort() + " como " + playerName);
        log.info("Servidor aberto na porta " + server.getPort());
    }

    /** @throws IllegalStateException se ja estiver conectado */
    public void connect(String host, int port, String playerName, GameListener listener) throws IOException {
        connect(host, port, playerName, null, listener);
    }

    /** Igual {@link #connect(String, int, String, GameListener)}, mas
     * com o ImageAssetStore da mesa deste jogador -- ver o overload de
     * host() acima pra explicacao.
     *
     * @throws IllegalStateException se ja estiver conectado
     */
    public void connect(String host, int port, String playerName, ImageAssetStore imageAssetStore,
            GameListener listener) throws IOException {
        connect(host, port, playerName, imageAssetStore, false, null, null, listener);
    }

    /** Igual o outro overload, mas pedindo pra entrar como mestre
     * (isGm) com a senha de mestre da mesa, alem da senha de entrar
     * (ver GameServer) -- pra um jogador comum que so precisa da senha
     * de mesa, deixe isGm=false e masterPassword=null.
     *
     * @throws IllegalStateException se ja estiver conectado
     */
    public void connect(String host, int port, String playerName, ImageAssetStore imageAssetStore,
            boolean isGm, String tablePassword, String masterPassword, GameListener listener) throws IOException {
        connect(host, port, playerName, imageAssetStore, isGm, tablePassword, masterPassword, false, null, listener);
    }

    private String pendingRelayCode;

    /** Proxima conexao vai pela ponte com este codigo (host/porta = endereco da ponte). */
    public void connectThroughRelay(String code) {
        this.pendingRelayCode = code == null || code.isBlank() ? null : code;
    }

    /** Igual, com TLS: {@code prompt} decide se confia no codigo da mesa (primeira vez / codigo mudou). */
    public void connect(String host, int port, String playerName, ImageAssetStore imageAssetStore,
            boolean isGm, String tablePassword, String masterPassword, boolean tlsOn,
            com.vttcaseiro.net.TlsSupport.TrustPrompt prompt, GameListener listener) throws IOException {
        requireNotConnected();
        this.playerName = playerName;
        client = new GameClient(adapt(listener), imageAssetStore, AppSettings.getOrCreatePlayerId());
        client.setPlayerSecret(AppSettings.getOrCreatePlayerKey());
        if (tlsOn) client.useTls(prompt);
        client.useRelay(pendingRelayCode);
        String relayCodeUsed = pendingRelayCode;
        pendingRelayCode = null;
        this.tls = tlsOn;
        this.hostFingerprint = null;
        try {
            client.connect(host, port, playerName, isGm, tablePassword, masterPassword);
        } catch (IOException | RuntimeException ex) {
            // Sem isso, um "conexao recusada" deixava client != null e toda
            // tentativa seguinte dava "Ja esta conectado".
            client = null;
            throw ex;
        }
        this.connectedHost = host;
        this.connectedPort = port;
        this.connectedRelayCode = relayCodeUsed;
        lastConnect = new ConnectArgs(host, port, playerName, imageAssetStore, isGm, tablePassword, masterPassword, tlsOn,
                prompt, listener, relayCodeUsed);
        setStatus("Conectado em " + host + ":" + port + (relayCodeUsed != null ? " (ponte, mesa " + relayCodeUsed + ")" : "")
                + " como " + playerName);
        log.info("Conectado a " + host + ":" + port);
    }

    // ---------------- reconectar sozinho

    /** Tudo que precisa pra conectar de novo do mesmo jeito. */
    private record ConnectArgs(String host, int port, String playerName, ImageAssetStore images, boolean isGm,
                               String tablePassword, String masterPassword, boolean tls,
                               com.vttcaseiro.net.TlsSupport.TrustPrompt prompt, GameListener listener, String relayCode) {
    }

    private volatile ConnectArgs lastConnect;
    private volatile Thread reconnectThread;
    /** Tentativas e espera entre elas: uns 2 minutos no total. */
    static int RECONNECT_ATTEMPTS = 24;
    static long RECONNECT_DELAY_MS = 5000;

    public boolean isReconnecting() {
        return reconnectThread != null;
    }

    private void startReconnect(ConnectArgs a, String reason) {
        setStatus("Reconectando...");
        Thread t = new Thread(() -> {
            try {
                for (int i = 1; i <= RECONNECT_ATTEMPTS; i++) {
                    if (lastConnect != a) return; // desconectou de proposito no meio
                    a.listener().onReconnecting(i, RECONNECT_ATTEMPTS);
                    Thread.sleep(i == 1 ? Math.min(1000, RECONNECT_DELAY_MS) : RECONNECT_DELAY_MS);
                    if (lastConnect != a) return;
                    try {
                        if (a.relayCode() != null) connectThroughRelay(a.relayCode());
                        connect(a.host(), a.port(), a.playerName(), a.images(), a.isGm(), a.tablePassword(),
                                a.masterPassword(), a.tls(), a.prompt(), a.listener());
                        log.info("Reconectado depois de " + i + " tentativa(s).");
                        reconnectThread = null;
                        a.listener().onReconnected();
                        return;
                    } catch (IOException | RuntimeException ex) {
                        log.info("Tentativa " + i + " de reconectar falhou: " + ex.getMessage());
                    }
                }
                lastConnect = null;
                reconnectThread = null;
                setStatus("Offline (conexão perdida)");
                a.listener().onDisconnected(reason + " (não deu pra reconectar)");
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            } finally {
                if (reconnectThread == Thread.currentThread()) reconnectThread = null;
            }
        }, "reconectar");
        t.setDaemon(true);
        reconnectThread = t;
        t.start();
    }

    private void requireNotConnected() {
        if (client != null) {
            throw new IllegalStateException(
                    "Já está conectado. Desconecte (ou feche o programa) antes de hospedar/conectar de novo.");
        }
    }

    private GameClient.Listener adapt(GameListener listener) {
        return new GameClient.Listener() {
            @Override
            public void onSceneSync(SceneModel scene) {
                listener.onSceneSync(scene);
            }

            @Override
            public void onSceneView(String sceneKey, boolean side) {
                listener.onSceneView(sceneKey, side);
            }

            @Override
            public void onSideSceneClosed(String sceneKey, SceneModel scene) {
                listener.onSideSceneClosed(sceneKey, scene);
            }

            @Override
            public void onZonesSync(java.util.List<com.vttcaseiro.model.MapZone> zones) {
                listener.onZonesSync(zones);
            }

            @Override
            public void onMapPull(String player, double x, double y) {
                listener.onMapPull(player, x, y);
            }

            @Override
            public void onPoll(String id, String author, String question, List<String> options, List<Integer> counts,
                               Integer myVote) {
                listener.onPoll(id, author, question, options, counts, myVote);
            }

            @Override
            public void onShops(List<com.vttcaseiro.model.Shop> shops) {
                listener.onShops(shops);
            }

            @Override
            public void onShopRequest(com.vttcaseiro.net.NetMessage.ShopRequest request) {
                listener.onShopRequest(request);
            }

            @Override
            public void onCameraLock(boolean locked, double x, double y, double zoom) {
                listener.onCameraLock(locked, x, y, zoom);
            }

            @Override
            public void onZoneEvent(com.vttcaseiro.model.MapZone zone, TokenModel token, String sceneKey, String ownerName) {
                listener.onZoneEvent(zone, token, sceneKey, ownerName);
            }

            @Override
            public void onTokenMoved(String tokenId, double x, double y) {
                listener.onTokenMoved(tokenId, x, y);
            }

            @Override
            public void onTokenAdded(TokenModel token) {
                listener.onTokenAdded(token);
            }

            @Override
            public void onTokenRemoved(String tokenId) {
                listener.onTokenRemoved(tokenId);
            }

            @Override
            public void onRollResult(String player, String detail, int total) {
                listener.onRollResult(player, detail, total);
            }

            @Override
            public void onRollResultLabeled(String player, String label, String detail, int total, Integer naturalD20) {
                listener.onRollResultLabeled(player, label, detail, total, naturalD20);
            }

            @Override
            public void onTokenUpdated(TokenModel token) {
                listener.onTokenUpdated(token);
            }

            @Override
            public void onRollResultFull(String player, String label, String detail, int total,
                                         Integer naturalD20, boolean hidden) {
                listener.onRollResultFull(player, label, detail, total, naturalD20, hidden);
            }

            @Override
            public void onWhisper(String player, String to, String text) {
                listener.onWhisper(player, to, text);
            }

            @Override
            public void onEncounterSync(com.vttcaseiro.model.Encounter encounter) {
                listener.onEncounterSync(encounter);
            }

            @Override
            public void onFogSync(com.vttcaseiro.model.Fog fog) {
                listener.onFogSync(fog);
            }

            @Override
            public void onMapPing(String player, double x, double y) {
                listener.onMapPing(player, x, y);
            }

            @Override
            public void onGmRequest(String player, com.vttcaseiro.model.GmRequest request) {
                listener.onGmRequest(player, request);
            }

            @Override
            public void onDrawingAdded(com.vttcaseiro.model.Drawing drawing) {
                listener.onDrawingAdded(drawing);
            }

            @Override
            public void onAudio(com.vttcaseiro.model.AudioCommand command) {
                listener.onAudio(command);
            }

            @Override
            public void onSceneLight(Boolean dim, Boolean fogByVision) {
                listener.onSceneLight(dim, fogByVision);
            }

            @Override
            public void onMapLights(java.util.List<com.vttcaseiro.model.MapLight> lights) {
                listener.onMapLights(lights);
            }

            @Override
            public void onWallsSync(java.util.List<com.vttcaseiro.model.Wall> walls, Boolean visionOn, Boolean dark) {
                listener.onWallsSync(walls, visionOn, dark);
            }

            @Override
            public void onPinsSync(java.util.List<com.vttcaseiro.model.MapPin> pins) {
                listener.onPinsSync(pins);
            }

            @Override
            public void onObjectsSync(java.util.List<com.vttcaseiro.model.MapObject> objects) {
                listener.onObjectsSync(objects);
            }

            @Override
            public void onImageStored(String assetId) {
                listener.onImageStored(assetId);
            }

            @Override
            public void onDrawingRemoved(String id) {
                listener.onDrawingRemoved(id);
            }

            @Override
            public void onDrawingsCleared(String ownerId) {
                listener.onDrawingsCleared(ownerId);
            }

            @Override
            public void onHandoutSync(com.vttcaseiro.model.Handout handout) {
                listener.onHandoutSync(handout);
            }

            @Override
            public void onHandoutShow(com.vttcaseiro.model.Handout handout) {
                listener.onHandoutShow(handout);
            }

            @Override
            public void onGroupNote(com.vttcaseiro.model.Handout note) {
                listener.onGroupNote(note);
            }

            @Override
            public void onGroupNoteRemoved(String id) {
                listener.onGroupNoteRemoved(id);
            }

            @Override
            public void onHandoutRemoved(String id) {
                listener.onHandoutRemoved(id);
            }

            @Override
            public void onCampaignSync(com.vttcaseiro.model.Campaign campaign) {
                listener.onCampaignSync(campaign);
            }

            @Override
            public void onCharacterRemoved(String id) {
                listener.onCharacterRemoved(id);
            }

            @Override
            public void onMobRemoved(String id) {
                listener.onMobRemoved(id);
            }

            @Override
            public void onPlayers(List<com.vttcaseiro.net.NetMessage.PlayerInfo> players) {
                java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();
                java.util.Map<String, String> where = new java.util.LinkedHashMap<>();
                for (var p : players) {
                    if (p.playerId != null && !p.gm) ids.put(p.name, p.playerId);
                    if (p.sceneKey != null && !p.gm) where.put(p.name, p.sceneKey);
                }
                playerIdsByName = ids;
                playerSceneByName = where;
                listener.onPlayersChanged();
            }

            @Override
            public void onChat(String player, String text) {
                listener.onChat(player, text);
            }

            @Override
            public void onChatImage(String player, String assetId, String caption, String whisperTo) {
                listener.onChatImage(player, assetId, caption, whisperTo);
            }

            @Override
            public void onCharacterSync(CharacterSheet character) {
                listener.onCharacterSync(character);
            }

            @Override
            public void onMobSync(MobSheet mob) {
                listener.onMobSync(mob);
            }

            @Override
            public void onPartySync(Party party) {
                listener.onPartySync(party);
            }

            @Override
            public void onDisconnected(String reason) {
                client = null;
                stopWebGateway();
                ConnectArgs again = server == null ? lastConnect : null; // quem hospeda nao tem pra onde voltar
                if (server != null) {
                    server.stop();
                    server = null;
                }
                onlinePlayers.clear();
                if (again != null && RECONNECT_ATTEMPTS > 0) {
                    startReconnect(again, reason);
                    return;
                }
                setStatus("Offline (conexão perdida)");
                listener.onDisconnected(reason);
            }

            @Override
            public void onPresenceUpdate(List<String> names) {
                // tratado aqui dentro -- quem usa este servico so precisa
                // de getOnlinePlayers(), nao precisa de mais um metodo pra
                // implementar no proprio GameListener.
                onlinePlayers.setAll(names);
            }
        };
    }

    public void sendMoveToken(String tokenId, double x, double y) {
        if (client != null) client.sendMoveToken(tokenId, x, y);
    }

    public void sendAddToken(TokenModel token) {
        if (client != null) client.sendAddToken(token);
    }

    public void sendUpdateToken(TokenModel token) {
        if (client != null) client.sendUpdateToken(token);
    }

    public void sendLabeledRoll(String label, String expression, String mode, int modifier) {
        sendRoll(label, expression, mode, modifier, false);
    }

    public void sendRoll(String label, String expression, String mode, int modifier, boolean hidden) {
        if (client != null) client.sendRoll(playerName, label, expression, mode, modifier, hidden);
    }

    /** Igual, com "as cegas" (so os mestres veem o resultado). */
    public void sendRoll(String label, String expression, String mode, int modifier, boolean hidden, boolean blind) {
        if (client != null) client.sendRoll(playerName, label, expression, mode, modifier, hidden, blind);
    }

    /** Mensagem do chat com outro nome ("falar como" um personagem ou PdM). */
    public void sendChatAs(String name, String text) {
        if (client == null) return;
        if (name == null || name.isBlank()) client.sendChat(playerName, text);
        else client.sendChatAs(playerName, name, text);
    }

    public void sendEncounter(com.vttcaseiro.model.Encounter encounter) {
        if (client != null) client.sendEncounter(encounter);
    }

    public void sendFog(com.vttcaseiro.model.Fog fog) {
        if (client != null) client.sendFog(fog);
    }

    public void sendAudio(com.vttcaseiro.model.AudioCommand c) {
        if (client != null) client.sendAudio(c);
    }

    /** Onde baixar os sons de quem hospeda (http://host:porta+1), ou null se EU hospedo / offline. */
    public String remoteAudioBase() {
        if (server != null || client == null || connectedHost == null) return null;
        // pela ponte: o som vem pela propria ponte (mesma porta, com o codigo da mesa no cookie)
        if (connectedRelayCode != null) return "http://" + connectedHost + ":" + connectedPort;
        return (tls ? "https://" : "http://") + connectedHost + ":" + (connectedPort + 1);
    }

    /** Codigo da mesa na ponte desta conexao (null = conexao direta). */
    private String connectedRelayCode;

    private String connectedHost;
    private int connectedPort;

    /**
     * Baixa um som de quem hospeda pro arquivo {@code target} (cache local).
     * Com TLS confere o codigo da mesa lembrado (o certificado e proprio, o
     * Java normal recusaria).
     */
    public void downloadAudio(String assetId, java.nio.file.Path target) throws IOException {
        String base = remoteAudioBase();
        if (base == null) throw new IOException("sem mesa remota");
        java.net.URLConnection conn = java.net.URI.create(base + "/audio/" + assetId).toURL().openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(30000);
        if (connectedRelayCode != null) conn.setRequestProperty("Cookie", "customizr_mesa=" + connectedRelayCode);
        if (conn instanceof javax.net.ssl.HttpsURLConnection https) {
            https.setSSLSocketFactory(com.vttcaseiro.net.TlsSupport.trustAllClient().getSocketFactory());
            https.setHostnameVerifier((h, s) -> true);
            https.connect();
            String fp = com.vttcaseiro.net.TlsSupport.fingerprint(https.getServerCertificates()[0]);
            String expected = com.vttcaseiro.net.TlsSupport.known(connectedHost + ":" + connectedPort);
            if (!fp.equals(expected)) throw new IOException("certificado do som não confere com o código da mesa");
        }
        java.nio.file.Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (java.io.InputStream in = conn.getInputStream()) {
            java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // O id do som e o SHA-256 do arquivo: confere, entao ninguem no caminho troca o som (com ou sem ponte/TLS).
        if (assetId.matches("[0-9a-f]{64}.*")) {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(tmp));
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) hex.append(String.format("%02x", b));
                if (!assetId.startsWith(hex.toString())) {
                    java.nio.file.Files.deleteIfExists(tmp);
                    throw new IOException("o som chegou diferente do original (descartado)");
                }
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IOException(ex);
            }
        }
        java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void sendWalls(com.vttcaseiro.model.SceneModel s) {
        if (client != null) client.sendWalls(s);
    }

    public void sendObjects(com.vttcaseiro.model.SceneModel s) {
        if (client != null) client.sendObjects(s);
    }

    public void sendPins(com.vttcaseiro.model.SceneModel s) {
        if (client != null) client.sendPins(s);
    }

    public void sendDoorToggle(String wallId) {
        if (client != null) client.sendDoorToggle(wallId);
    }

    public void sendDrawingAdd(com.vttcaseiro.model.Drawing d) {
        if (client != null) client.sendDrawingAdd(d);
    }

    public void sendDrawingRemove(String id) {
        if (client != null) client.sendDrawingRemove(id);
    }

    public void sendDrawingClear(String ownerId) {
        if (client != null) client.sendDrawingClear(ownerId);
    }

    public void sendHandout(com.vttcaseiro.model.Handout h) {
        if (client != null) client.sendHandout(h);
    }

    public void sendGroupNote(com.vttcaseiro.model.Handout h) {
        if (client != null) client.sendGroupNote(h);
    }

    public void sendGroupNoteRemove(String id) {
        if (client != null) client.sendGroupNoteRemove(id);
    }

    public void sendHandoutShow(com.vttcaseiro.model.Handout h) {
        if (client != null) client.sendHandoutShow(h);
    }

    public void sendHandoutRemove(String id) {
        if (client != null) client.sendHandoutRemove(id);
    }

    public void sendGmRequest(com.vttcaseiro.model.GmRequest request) {
        if (client != null) client.sendGmRequest(request);
    }

    public void sendMapPing(double x, double y) {
        if (client != null) client.sendMapPing(x, y);
    }

    /** Jogadores (nao mestres) conectados: nome -> playerId. */
    private volatile java.util.Map<String, String> playerIdsByName = java.util.Map.of();

    public java.util.Map<String, String> getPlayerIdsByName() {
        return playerIdsByName;
    }

    public void sendWhisper(String to, String text) {
        if (client != null) client.sendWhisper(playerName, to, text);
    }

    /** Esta maquina entrou como mestre (ou esta jogando offline, onde
     * quem usa o programa manda em tudo). */
    public boolean isGm() {
        return client == null || (client.getSession() != null && client.getSession().gm());
    }

    public String getPlayerId() {
        return client != null ? client.getPlayerId() : AppSettings.getOrCreatePlayerId();
    }

    public void sendRemoveToken(String tokenId) {
        if (client != null) client.sendRemoveToken(tokenId);
    }

    public void sendRoll(String expression) {
        if (client != null) client.sendRoll(playerName, expression);
    }

    public void sendRollAdvantage(int modifier) {
        if (client != null) client.sendRollAdvantage(playerName, modifier);
    }

    public void sendRollDisadvantage(int modifier) {
        if (client != null) client.sendRollDisadvantage(playerName, modifier);
    }

    public void sendChat(String text) {
        if (client != null) client.sendChat(playerName, text);
    }

    public void sendChatImage(String assetId, String caption, String whisperTo) {
        if (client != null) client.sendChatImage(playerName, assetId, caption, whisperTo);
    }

    public void sendCharacterSync(CharacterSheet character) {
        if (client != null) client.sendCharacterSync(character);
    }

    public void sendCampaign(com.vttcaseiro.model.Campaign c) {
        if (client != null) client.sendCampaign(c);
    }

    public void sendMobSync(MobSheet mob) {
        if (client != null) client.sendMobSync(mob);
    }

    public void sendPartySync(Party party) {
        if (client != null) client.sendPartySync(party);
    }

    /** So tem efeito de verdade se quem chamar for o mestre -- o servidor
     * confere isso do lado dele (ver GameServer), entao um jogador comum
     * chamando isto nao faz nada acontecer pra ninguem. */
    public void sendSceneSwitch(SceneModel scene) {
        if (client != null) client.sendSceneSwitch(scene);
    }

    /** Mestre abre a cena {@code name} pro grupo. */
    public void sendSceneOpen(SceneModel scene, String name) {
        if (client != null) client.sendSceneOpen(scene, name);
    }

    /** Grupo dividido: leva o jogador (pelo nome) pra cena (null = volta pro grupo). */
    public void sendSplitScene(String playerName, String sceneKey, SceneModel scene) {
        sendSplitScene(playerName, sceneKey, scene, null);
    }

    /** Igual, levando um token junto (portal). */
    public void sendSplitScene(String playerName, String sceneKey, SceneModel scene, TokenModel token) {
        String id = playerIdsByName.get(playerName);
        if (client != null && id != null) client.sendSplitScene(id, sceneKey, scene, token);
    }

    public void sendZonesSync(java.util.List<com.vttcaseiro.model.MapZone> zones) {
        if (client != null) client.sendZonesSync(zones);
    }

    public void sendMapPull(double x, double y) {
        if (client != null) client.sendMapPull(x, y);
    }

    public void sendChatLanguage(String text, String language) {
        if (client != null) client.sendChatLanguage(playerName, text, language);
    }

    public void sendPoll(String id, String question, List<String> options) {
        if (client != null) client.sendPoll(id, question, options);
    }

    public void sendPollVote(String id, int option) {
        if (client != null) client.sendPollVote(id, option);
    }

    public void sendShops(List<com.vttcaseiro.model.Shop> shops) {
        if (client != null) client.sendShops(shops);
    }

    public void sendShopRequest(com.vttcaseiro.net.NetMessage.ShopRequest r) {
        if (client != null) client.sendShopRequest(r);
    }

    public void sendCameraLock(boolean locked, double x, double y, double zoom) {
        if (client != null) client.sendCameraLock(locked, x, y, zoom);
    }

    public void sendGmView(String sceneKey) {
        if (client != null) client.sendGmView(sceneKey);
    }

    /** Cena separada que o mestre esta olhando (null = a do grupo). */
    public String getSideViewKey() {
        return client == null ? null : client.getSideViewKey();
    }

    /** Jogadores fora da cena do grupo: nome -> cena (so o mestre sabe). */
    public java.util.Map<String, String> getPlayerScenes() {
        return playerSceneByName;
    }

    private volatile java.util.Map<String, String> playerSceneByName = java.util.Map.of();

    /** Desconexao pedida pelo jogador -- nao dispara onDisconnected (isso
     * e so pra falha inesperada), entao quem chamou isto e que decide o
     * que fazer na tela depois. */
    public void disconnect() {
        lastConnect = null; // saiu porque quis: nao tenta voltar
        Thread r = reconnectThread;
        if (r != null) r.interrupt();
        closeRelay();
        if (client != null) {
            client.disconnect();
            client = null;
        }
        stopWebGateway();
        if (server != null) {
            server.stop();
            server = null;
        }
        tls = false;
        hostFingerprint = null;
        connectedRelayCode = null;
        setStatus("Offline (tudo local)");
        onlinePlayers.clear();
    }
}
