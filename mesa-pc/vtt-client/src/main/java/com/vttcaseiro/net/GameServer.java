package com.vttcaseiro.net;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.dice.DiceException;
import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.party.Party;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * O servidor caseiro: roda na maquina de quem hospeda (normalmente o
 * mestre), guarda o {@link SceneModel} que vale pra todo mundo, aceita
 * conexao de cada jogador numa thread por cliente ({@link ClientHandler})
 * e retransmite ("broadcast") toda mudanca pra todo mundo conectado.
 * <p>
 * Cada conexao vira uma {@link PlayerSession} assim que o "hello" chega.
 * Mover/remover um token e validado com {@code ClientHandler.ownsToken}
 * (que delega pra {@code PlayerSession.owns}): o mestre controla
 * qualquer token, um jogador comum so o que e dele. Toda vez que alguem
 * entra ou sai, todo mundo recebe a lista atualizada de quem esta
 * conectado agora ({@code presence}).
 * <p>
 * Testado de verdade via loopback (ver NetSmokeTest.java e o relato no
 * HANDOFF.md) -- diferente da parte de tela (JavaFX), isso aqui rodou e
 * funcionou antes de chegar em voces.
 */
public class GameServer {
    private static final Log log = Log.of(GameServer.class);

    private final int port;
    /** Nao e mais final: o mestre pode trocar de cena no meio da sessao
     * (caso "scene_sync" recebido de um cliente em handleIncoming), e
     * todo mundo ja conectado precisa ficar sabendo -- nao so quem entra
     * depois. */
    private SceneModel scene;
    /**
     * Grupo dividido: {@link #scene} e a cena da mensagem que esta sendo tratada agora (ver {@link #enter}); a do
     * grupo fica em {@code groupScene} e as separadas (um jogador sozinho no porao) em {@code sideScenes}, pelo nome.
     */
    private SceneModel groupScene;
    private String groupKey;
    private final Map<String, SceneModel> sideScenes = new java.util.LinkedHashMap<>();
    private String ctxKey;
    /** Enquetes abertas: id -> pergunta, opcoes, quem criou e o voto de cada jogador (pelo id). */
    private static final class PollState {
        String author;
        String question;
        List<String> options;
        final Map<String, Integer> votes = new java.util.LinkedHashMap<>();
    }

    private final Map<String, PollState> polls = new java.util.LinkedHashMap<>();
    /** Lojas abertas pelo mestre (quem entra depois recebe). */
    private volatile List<com.vttcaseiro.model.Shop> sharedShops = List.of();

    private NetMessage pollFor(String id, PollState p, ClientHandler c) {
        List<Integer> counts = new java.util.ArrayList<>();
        for (int i = 0; i < p.options.size(); i++) counts.add(0);
        p.votes.values().forEach(v -> { if (v >= 0 && v < counts.size()) counts.set(v, counts.get(v) + 1); });
        Integer mine = c.getSession() == null ? null : p.votes.get(c.getSession().playerId());
        return NetMessage.poll(id, p.author, p.question, p.options, counts, mine);
    }

    private void broadcastPoll(String id, PollState p) {
        for (ClientHandler c : clients) if (c.getSession() != null) c.send(pollFor(id, p, c));
    }

    /** Este cliente conhece o idioma? (mestre sempre; jogador pelas fichas dele) */
    private boolean knowsLanguage(ClientHandler c, String language) {
        if (c.isGm() || c.getSession() == null) return true;
        String me = c.getSession().playerId();
        for (CharacterSheet s : sharedCharacters.values()) {
            String owner = characterOwners.get(s.getId());
            if (owner == null) owner = s.getOwnerPlayerId();
            if (me.equals(owner) && com.vttcaseiro.service.LanguageScramble.knows(s.getLanguages(), language)) return true;
        }
        return false;
    }

    /** Camera travada pelo mestre, por cena ("" = a do grupo): quem entra depois ja chega travado. */
    private final Map<String, NetMessage> cameraLocks = new java.util.HashMap<>();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    /** Musica e ambiente tocando agora (quem entra depois ouve o mesmo). */
    private final java.util.Map<String, com.vttcaseiro.model.AudioCommand> playingAudio = new java.util.LinkedHashMap<>();
    /** Entradas do diario que o mestre revelou (quem entra depois recebe). */
    private final java.util.Map<String, com.vttcaseiro.model.Handout> sharedHandouts = new java.util.LinkedHashMap<>();
    private final Object lock = new Object();

    /** Ultima versao conhecida de cada ficha de personagem/monstro e da
     * Party desta mesa, guardada aqui pra quem ENTRAR DEPOIS receber o
     * estado atual -- sem isso, um jogador que conecta tarde nunca veria
     * as fichas/monstros que ja estavam sincronizados antes dele chegar.
     * Chave e o id da propria ficha/monstro (CharacterSheet.getId()/
     * MobSheet.getId()), nao o nome -- dois personagens podem ter o
     * mesmo nome, nunca o mesmo id. */
    private final Map<String, CharacterSheet> sharedCharacters = new ConcurrentHashMap<>();
    /** Campanha completa (so o mestre manda); jogador recebe a publicCopy(). */
    private volatile com.vttcaseiro.model.Campaign sharedCampaign;
    private final Map<String, MobSheet> sharedMobs = new ConcurrentHashMap<>();
    /** id da ficha -> playerId de quem a sincronizou primeiro. */
    private final Map<String, String> characterOwners = new ConcurrentHashMap<>();
    /** Limite de tentativas de senha por IP (ver LoginGuard). */
    private final LoginGuard loginGuard = new LoginGuard();
    private volatile Party sharedParty;

    /** Asset store da mesa de quem hospeda -- null quando o servidor e
     * aberto sem uma mesa de verdade por tras (ver o main() no fim do
     * arquivo, usado so pra teste manual via socket). Com ele presente,
     * o servidor guarda toda imagem que passa por ele e consegue
     * reenviar pra quem entra depois, sem depender de quem mandou a
     * imagem originalmente ainda estar conectado. */
    private final ImageAssetStore imageAssetStore;

    /** Senha pra ENTRAR na mesa -- null/vazio = mesa sem senha (qualquer
     * um entra, igual sempre foi). */
    private final String tablePassword;
    /** Senha SEPARADA pra ser aceito como MESTRE -- null/vazio = qualquer
     * um que peça isGm=true e aceito como mestre (comportamento antigo,
     * sem checagem nenhuma). Com uma senha configurada, quem mandar
     * isGm=true sem ela certa ainda entra na mesa, so que como jogador
     * comum (ver handleIncoming, caso "hello") -- nunca kickado so por
     * errar essa senha, kickado so por errar a de entrar na mesa. */
    private final String masterPassword;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;

    public GameServer(int port, SceneModel scene) {
        this(port, scene, null, null, null);
    }

    public GameServer(int port, SceneModel scene, ImageAssetStore imageAssetStore) {
        this(port, scene, imageAssetStore, null, null);
    }

    public GameServer(int port, SceneModel scene, ImageAssetStore imageAssetStore,
            String tablePassword, String masterPassword) {
        this.port = port;
        // Copia propria: quem hospeda continua mexendo na SceneModel da
        // tela (thread do JavaFX); se o servidor usasse o MESMO objeto, todo
        // add_token entraria duas vezes na lista do mestre (uma pelo
        // servidor, outra quando o broadcast volta) e as duas threads
        // mexeriam na mesma ArrayList ao mesmo tempo.
        this.scene = scene == null ? new SceneModel() : NetCodec.deepCopy(scene, SceneModel.class);
        this.groupScene = this.scene;
        this.imageAssetStore = imageAssetStore;
        this.tablePassword = tablePassword;
        this.masterPassword = masterPassword;
    }

    /** null/vazio sempre "bate" com qualquer coisa -- e assim que uma
     * mesa sem senha continua aceitando todo mundo, e uma tentativa de
     * mestre sem senha de mestre configurada continua sendo aceita como
     * mestre (comportamento antigo preservado quando a senha nao e
     * usada). */
    private static boolean passwordMatches(String expected, String given) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(given);
    }

    /** Abre a porta e comeca a aceitar conexoes numa thread separada
     * (nao bloqueia quem chamou). */
    /** TLS: chame antes de start(). null = sem criptografia (como sempre foi). */
    private javax.net.ssl.SSLContext tls;

    public void useTls(javax.net.ssl.SSLContext context) {
        this.tls = context;
    }

    public boolean isTls() {
        return tls != null;
    }

    public void start() throws IOException {
        serverSocket = tls == null ? new ServerSocket(port) : tls.getServerSocketFactory().createServerSocket(port);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "game-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /** Quem esta conectado agora, com sessao ja identificada (quem ainda
     * nao mandou "hello" nao entra na lista). */
    public List<PlayerSession> getConnectedSessions() {
        return clients.stream()
                .map(ClientHandler::getSession)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                Thread t = new Thread(handler, "client-" + socket.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            } catch (IOException ex) {
                if (running) {
                    log.error("Erro aceitando conexão", ex);
                }
                // se running==false, e o stop() fechando o socket de proposito -- nao e erro
            }
        }
    }

    /**
     * Jogador que chegou pela ponte (ver relay.RelayHostLink): a conexao ja esta ligada a ele.
     * Com TLS, o aperto de mao acontece aqui (de ponta a ponta: a ponte nao ve o conteudo).
     */
    public void acceptTunneled(Socket raw, String realIp) {
        if (!running) {
            com.vttcaseiro.relay.RelayServer.closeQuietly(raw);
            return;
        }
        try {
            Socket socket = raw;
            if (tls != null) {
                javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) tls.getSocketFactory()
                        .createSocket(raw, raw.getInetAddress().getHostAddress(), raw.getPort(), true);
                ssl.setUseClientMode(false);
                socket = ssl;
            }
            ClientHandler handler = new ClientHandler(socket, this, realIp == null ? "?" : realIp);
            clients.add(handler);
            Thread t = new Thread(handler, "client-ponte-" + realIp);
            t.setDaemon(true);
            t.start();
        } catch (IOException ex) {
            com.vttcaseiro.relay.RelayServer.closeQuietly(raw);
        }
    }

    void removeClient(ClientHandler handler) {
        clients.remove(handler);
        if (handler.getSession() != null) {
            synchronized (lock) {
                pruneSides(); // jogador que estava sozinho numa cena saiu: a cena volta pro mestre gravar
            }
            broadcastPresence();
        }
    }

    /** Passa a tratar a cena {@code key} (null = a do grupo). */
    private void enter(String key) {
        SceneModel side = key == null ? null : sideScenes.get(key);
        ctxKey = side == null ? null : key;
        scene = side == null ? groupScene : side;
    }

    /** O cliente esta na cena que esta sendo tratada agora? */
    private boolean inScene(ClientHandler c) {
        return java.util.Objects.equals(c.sideScene, ctxKey);
    }

    /** Cena separada sem ninguem: sai do servidor e vai pros mestres gravarem no disco. */
    private void pruneSides() {
        for (String key : new java.util.ArrayList<>(sideScenes.keySet())) {
            boolean used = clients.stream().anyMatch(c -> key.equals(c.sideScene));
            if (used) continue;
            SceneModel s = sideScenes.remove(key);
            NetMessage m = NetMessage.sideSceneClosed(key, s);
            for (ClientHandler c : clients) if (c.isGm()) c.send(m);
        }
        enter(null);
    }

    /** Lista de cenas separadas agora: nome da cena -> quem esta nela (pro teste e pro mestre). */
    public Map<String, List<String>> splitState() {
        synchronized (lock) {
            Map<String, List<String>> out = new java.util.LinkedHashMap<>();
            for (String key : sideScenes.keySet()) {
                List<String> who = new java.util.ArrayList<>();
                for (ClientHandler c : clients) {
                    if (key.equals(c.sideScene) && c.getSession() != null) who.add(c.getSession().playerName());
                }
                out.put(key, who);
            }
            return out;
        }
    }

    /** Chamado pelo ClientHandler quando chega uma mensagem desse cliente
     * (exceto "ping", que o proprio ClientHandler responde sem passar por
     * aqui). */
    void handleIncoming(NetMessage msg, ClientHandler from) {
        synchronized (lock) {
            // Antes do "hello" aceito (senha conferida), a conexao nao pode
            // fazer NADA -- senao bastava pular o hello pra mexer em token,
            // ficha ou cena de uma mesa com senha.
            if (from.getSession() == null && !"hello".equals(msg.type)) {
                log.warn("Mensagem \"" + msg.type + "\" antes do hello -- ignorada.");
                return;
            }
            // O nome de quem fala/rola e SEMPRE o da sessao, nunca o que o
            // cliente escreveu no campo "player" (senao qualquer um rola
            // "como" outra pessoa).
            if (from.getSession() != null && ("chat".equals(msg.type) || "roll".equals(msg.type)
                    || "map_ping".equals(msg.type) || "gm_request".equals(msg.type))) {
                msg.player = from.getSession().playerName();
            }
            if (from.sideScene != null && !sideScenes.containsKey(from.sideScene)) from.sideScene = null;
            enter(from.sideScene);
            try {
            switch (msg.type) {
                case "move_token" -> {
                    Optional<TokenModel> token = findToken(msg.tokenId);
                    if (token.isEmpty()) {
                        log.warn("move_token para um id que não existe: " + msg.tokenId);
                    } else if (!from.ownsToken(token.get().getOwnerId()) && !ownsLinkedSheet(from, token.get())) {
                        // Dono do token OU da ficha ligada move (igual o update_token).
                        from.send(NetMessage.chat("servidor", "Você não controla esse token."));
                        // Devolve a posicao certa: senao o token fica fora do lugar so na tela de quem tentou.
                        from.send(NetMessage.moveToken(token.get().getId(), token.get().getX(), token.get().getY()));
                    } else if (msg.x == null || msg.y == null) {
                        log.warn("move_token sem posição");
                    } else {
                        double oldX = token.get().getX();
                        double oldY = token.get().getY();
                        token.get().setX(msg.x);
                        token.get().setY(msg.y);
                        routeTokenChange(msg, token.get().getId());
                        zoneEvents(token.get(), oldX, oldY);
                    }
                }
                case "add_token" -> {
                    if (msg.token != null) {
                        if (msg.token.getOwnerId() == null && !from.isGm()) {
                            msg.token.setOwnerId(from.getSession() != null ? from.getSession().playerId() : null);
                        }
                        if (!from.isGm()) msg.token.setHiddenFromPlayers(false);
                        scene.addToken(msg.token);
                        routeTokenChange(msg, msg.token.getId());
                    }
                }
                case "update_token" -> {
                    // PV, condicoes, efeitos, iniciativa, nome, tamanho --
                    // tudo do token MENOS posicao (essa vai por move_token)
                    // e dono (so quem ja controla pode mudar, e nunca pra
                    // tirar o controle do mestre).
                    Optional<TokenModel> token = msg.token == null ? Optional.empty() : findToken(msg.token.getId());
                    if (token.isEmpty()) {
                        log.warn("update_token para um id que não existe");
                    } else if (!from.ownsToken(token.get().getOwnerId()) && !ownsLinkedSheet(from, token.get())) {
                        from.send(NetMessage.chat("servidor", "Você não controla esse token."));
                    } else {
                        boolean wasHidden = token.get().isHiddenFromPlayers();
                        token.get().copyStateFrom(msg.token);
                        if (from.isGm()) {
                            token.get().setOwnerId(msg.token.getOwnerId()); // "Dar controle a..."
                        } else {
                            token.get().setHiddenFromPlayers(wasHidden); // so o mestre esconde/revela
                        }
                        msg.token = token.get();
                        // Oculto/revelado, dono novo, luz nova... quem ve o que pode mudar.
                        routeTokenChange(msg, token.get().getId());
                    }
                }
                case "remove_token" -> {
                    Optional<TokenModel> token = findToken(msg.tokenId);
                    if (token.isPresent() && from.ownsToken(token.get().getOwnerId())) {
                        scene.removeToken(token.get());
                        broadcast(msg);
                        for (ClientHandler c : clients) c.knownTokens.remove(msg.tokenId);
                        resyncAll(); // sem esse token, o dono dele pode deixar de ver outros
                    } else if (token.isPresent()) {
                        from.send(NetMessage.chat("servidor", "Você não controla esse token."));
                    }
                }
                case "roll" -> {
                    try {
                        RollResult result;
                        int mod = msg.modifier == null ? 0 : msg.modifier;
                        if ("advantage".equals(msg.rollMode)) {
                            result = DiceRoller.rollWithAdvantage(mod);
                        } else if ("disadvantage".equals(msg.rollMode)) {
                            result = DiceRoller.rollWithDisadvantage(mod);
                        } else {
                            result = DiceRoller.roll(msg.expression);
                        }
                        NetMessage out = NetMessage.rollResult(msg.player, result.detail(), result.total());
                        out.label = msg.label;
                        out.naturalD20 = result.naturalD20();
                        if (Boolean.TRUE.equals(msg.blind)) {
                            // As cegas: so os mestres veem o resultado; quem rolou fica sabendo que rolou.
                            out.hidden = true;
                            for (ClientHandler c : clients) if (c.isGm()) c.send(out);
                            if (!from.isGm()) {
                                from.send(NetMessage.chat("servidor", "Você rolou às cegas" + (msg.label == null ? "" : " ("
                                        + msg.label + ")") + ": só o mestre vê o resultado."));
                            }
                        } else if (Boolean.TRUE.equals(msg.hidden)) {
                            // Rolagem escondida: quem rolou + mestres.
                            out.hidden = true;
                            sendToGmsAnd(out, from);
                        } else {
                            broadcast(out);
                        }
                    } catch (DiceException ex) {
                        broadcast(NetMessage.chat("servidor",
                                msg.player + " tentou rolar \"" + msg.expression + "\": " + ex.getMessage()));
                    }
                }
                case "map_ping" -> {
                    if (!from.isGm()) msg.pull = null; // so o mestre puxa a tela dos outros
                    broadcastExcept(msg, from);
                }
                case "camera_lock" -> {
                    if (!from.isGm() || msg.x == null || msg.y == null) break;
                    String k = ctxKey == null ? "" : ctxKey;
                    if (Boolean.TRUE.equals(msg.locked)) cameraLocks.put(k, msg);
                    else cameraLocks.remove(k);
                    broadcastExcept(msg, from);
                }
                case "audio" -> {
                    if (!from.isGm() || msg.audio == null) break;
                    com.vttcaseiro.model.AudioCommand c = msg.audio;
                    if (c.loops()) {
                        if (c.isStop()) playingAudio.remove(c.getChannel());
                        else playingAudio.put(c.getChannel(), c);
                    }
                    broadcastExcept(msg, from);
                }
                case "objects_sync" -> {
                    if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe nos objetos do mapa."));
                        break;
                    }
                    scene.setObjects(msg.objects);
                    for (ClientHandler c : clients) {
                        if (c != from && c.getSession() != null && inScene(c) && !curtain(c)) c.send(NetMessage.objectsSync(scene.visibleObjects(c.isGm())));
                    }
                }
                case "pins_sync" -> {
                    if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe nos pins."));
                        break;
                    }
                    scene.setPins(msg.pins);
                    for (ClientHandler c : clients) {
                        if (c != from && c.getSession() != null && inScene(c) && !curtain(c)) c.send(NetMessage.pinsSync(scene.visiblePins(c.isGm())));
                    }
                }
                case "zones_sync" -> {
                    if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe nas zonas."));
                        break;
                    }
                    scene.setZones(msg.zones);
                    for (ClientHandler c : clients) {
                        if (c != from && c.getSession() != null && inScene(c) && !curtain(c)) c.send(NetMessage.zonesSync(scene.visibleZones(c.isGm())));
                    }
                }
                case "walls_sync" -> {
                    if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe nas paredes."));
                        break;
                    }
                    scene.setWalls(msg.walls);
                    if (msg.visionOn != null) scene.setVisionEnabled(msg.visionOn);
                    if (msg.sceneDark != null) scene.setDark(msg.sceneDark);
                    if (msg.sceneDim != null) scene.setDim(msg.sceneDim);
                    if (msg.fogByVision != null) scene.setFogByVision(msg.fogByVision);
                    if (msg.lights != null) scene.setLights(msg.lights);
                    broadcastExcept(msg, from);
                    resyncAll();
                }
                case "door_toggle" -> {
                    // Qualquer um abre porta; o servidor alterna e manda o estado pra todos.
                    // Trancada e secreta: so o mestre (o jogador nem sabe que a secreta e porta).
                    scene.findWall(msg.text).filter(com.vttcaseiro.model.Wall::isDoor).ifPresent(w -> {
                        if (w.gmOnly() && !from.isGm()) {
                            from.send(NetMessage.chat("servidor", w.isLocked() ? "A porta está trancada." : "Não há porta aí."));
                            return;
                        }
                        w.setOpen(!w.isOpen());
                        broadcast(NetMessage.wallsSync(scene));
                        resyncAll();
                    });
                }
                case "drawing_add" -> {
                    if (msg.drawing == null) break;
                    // O dono e quem mandou (o cliente nao escolhe), e o tamanho tem limite.
                    msg.drawing.setOwnerId(from.getSession().playerId());
                    msg.drawing.setPoints(msg.drawing.getPoints());
                    msg.drawing.setText(msg.drawing.getText());
                    if (scene.addDrawing(msg.drawing)) {
                        broadcastExcept(msg, from);
                    } else {
                        from.send(NetMessage.chat("servidor", "A cena já tem desenhos demais."));
                    }
                }
                case "drawing_remove" -> {
                    Optional<com.vttcaseiro.model.Drawing> d = scene.findDrawing(msg.text);
                    if (d.isPresent() && (from.isGm()
                            || java.util.Objects.equals(d.get().getOwnerId(), from.getSession().playerId()))) {
                        scene.removeDrawing(msg.text);
                        broadcastExcept(msg, from);
                    }
                }
                case "drawing_clear" -> {
                    // Mestre pode apagar tudo; jogador so os proprios desenhos.
                    String owner = from.isGm() ? msg.text : from.getSession().playerId();
                    if (scene.clearDrawings(owner) > 0 || owner == null) {
                        broadcastExcept(NetMessage.drawingClear(owner), from);
                    }
                }
                case "handout_sync" -> {
                    if (!from.isGm() || msg.handout == null || msg.handout.getId() == null) break;
                    if (msg.handout.isRevealed()) {
                        sharedHandouts.put(msg.handout.getId(), msg.handout);
                        broadcastExcept(msg, from);
                    } else if (sharedHandouts.remove(msg.handout.getId()) != null) {
                        broadcastExcept(NetMessage.handoutRemove(msg.handout.getId()), from);
                    }
                }
                case "handout_remove" -> {
                    if (from.isGm() && msg.text != null && sharedHandouts.remove(msg.text) != null) {
                        broadcastExcept(msg, from);
                    }
                }
                case "gm_request" -> {
                    // Vai pra UM mestre so (o primeiro conectado): se fosse pra
                    // todos, dois mestres aplicariam o mesmo dano duas vezes.
                    Optional<ClientHandler> gm = clients.stream()
                            .filter(c -> c != from && c.getSession() != null && c.isGm()).findFirst();
                    if (msg.gmRequest == null) {
                        // nada
                    } else if (gm.isPresent()) {
                        gm.get().send(msg);
                    } else {
                        from.send(NetMessage.chat("servidor",
                                "Nenhum mestre conectado pra aplicar no token de outro jogador."));
                    }
                }
                case "fog_sync" -> {
                    if (msg.fog != null && from.isGm()) {
                        scene.setFog(msg.fog);
                        broadcastExcept(msg, from);
                    } else if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe na neblina."));
                    }
                }
                case "encounter_sync" -> {
                    if (msg.encounter != null && from.isGm()) {
                        scene.setEncounter(msg.encounter);
                        broadcastExcept(msg, from);
                    } else if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre controla os turnos."));
                    }
                }
                case "chat" -> {
                    // imagem no chat: so um id de asset valido (nada de caminho de arquivo)
                    if (msg.assetId != null && !com.vttcaseiro.library.ImageAssetStore.isValidAssetId(msg.assetId)) {
                        msg.assetId = null;
                    }
                    msg.assetBase64 = null;
                    if (msg.text == null) msg.text = "";
                    // "falar como": mestre fala como qualquer PdM; jogador so como personagem dele, e todo mundo ve
                    // quem esta falando ("Lyra (Ana)"). Nome que nao e dele: sai com o nome da conta.
                    if (msg.speakAs != null && !msg.speakAs.isBlank()) {
                        String as = msg.speakAs.strip();
                        if (as.length() > 40) as = as.substring(0, 40);
                        if (from.isGm()) msg.player = as;
                        else if (ownsCharacterNamed(from, as)) msg.player = as + " (" + from.getSession().playerName() + ")";
                    }
                    msg.speakAs = null;
                    // [[1d20+5]] no texto: quem rola e o servidor (e ninguem forja o resultado).
                    msg.text = com.vttcaseiro.dice.InlineRolls.expand(msg.text);
                    if (msg.language != null && !msg.language.isBlank() && (msg.whisperTo == null || msg.whisperTo.isBlank())) {
                        // idioma do jogo: quem conhece le; os outros recebem embaralhado (o texto certo nem sai)
                        String lang = msg.language.strip();
                        if (lang.length() > 30) lang = lang.substring(0, 30);
                        String original = msg.text;
                        for (ClientHandler c : clients) {
                            if (c.getSession() == null) continue;
                            boolean reads = c == from || knowsLanguage(c, lang);
                            NetMessage copy = NetMessage.chat(msg.player, "[" + lang + "] "
                                    + (reads ? original : com.vttcaseiro.service.LanguageScramble.scramble(original, lang)));
                            copy.language = lang;
                            c.send(copy);
                        }
                    } else if (msg.whisperTo == null || msg.whisperTo.isBlank()) {
                        broadcast(msg);
                    } else {
                        deliverWhisper(msg, from);
                    }
                }
                case "character_sync" -> {
                    if (msg.character != null && msg.character.getId() != null) {
                        String sheetId = msg.character.getId();
                        String playerId = from.getSession().playerId();
                        String declared = msg.character.getOwnerPlayerId();
                        String known = characterOwners.get(sheetId);
                        // Dono = o que ja se sabe; senao o gravado na ficha; senao
                        // quem sincronizou primeiro. So o dono e o mestre alteram.
                        String owner = known != null ? known : (declared != null ? declared : playerId);
                        if (!owner.equals(playerId) && !from.isGm()) {
                            from.send(NetMessage.chat("servidor",
                                    "A ficha \"" + msg.character.getName() + "\" pertence a outro jogador."));
                            break;
                        }
                        // O mestre pode passar a ficha pra outro jogador (ownerPlayerId novo).
                        characterOwners.put(sheetId, from.isGm() && declared != null ? declared : owner);
                        sharedCharacters.put(sheetId, msg.character);
                        // Nao devolve pra quem mandou: a janela dele ja tem
                        // essa versao (e pode ter mais edicoes por cima).
                        String line = NetCodec.encode(msg);
                        String gone = NetCodec.encode(NetMessage.characterRemove(sheetId));
                        for (ClientHandler c : clients) {
                            if (c == from || c.getSession() == null) continue;
                            // escondida agora: quem nao pode ver apaga a copia que ja tinha (some da lista dele)
                            c.sendEncoded(canSeeCharacter(c, msg.character) ? line : gone);
                        }
                        resyncAll(); // dono novo da ficha = olhos novos na visao dinamica
                    }
                }
                case "mob_sync" -> {
                    if (msg.mob != null && msg.mob.getId() != null) {
                        sharedMobs.put(msg.mob.getId(), msg.mob);
                        String line = NetCodec.encode(msg);
                        String gone = NetCodec.encode(NetMessage.mobRemove(msg.mob.getId()));
                        for (ClientHandler c : clients) {
                            if (c.getSession() == null) continue;
                            c.sendEncoded(c.isGm() || !msg.mob.isHiddenFromPlayers() ? line : gone);
                        }
                    }
                }
                case "campaign_sync" -> {
                    if (!from.isGm()) {
                        from.send(NetMessage.chat("servidor", "Só o mestre mexe na campanha."));
                        break;
                    }
                    if (msg.campaign != null) {
                        sharedCampaign = msg.campaign.sanitize();
                        String full = NetCodec.encode(NetMessage.campaignSync(sharedCampaign));
                        String pub = NetCodec.encode(NetMessage.campaignSync(sharedCampaign.publicCopy()));
                        for (ClientHandler c : clients) {
                            if (c != from && c.getSession() != null) c.sendEncoded(c.isGm() ? full : pub);
                        }
                    }
                }
                case "party_sync" -> {
                    if (msg.party != null) {
                        sharedParty = msg.party;
                        broadcast(msg);
                    }
                }
                case "image_data" -> {
                    // Guarda localmente (dedup automatico -- ver
                    // ImageAssetStore) e repassa pra todo mundo, pra quem
                    // ja esta conectado tambem receber a imagem nova.
                    ImageAssetRelay.storeIncoming(imageAssetStore, msg);
                    broadcast(msg);
                }
                case "image_request" -> {
                    if (imageAssetStore != null && imageAssetStore.exists(msg.assetId)) {
                        try {
                            from.send(ImageAssetRelay.encode(imageAssetStore, msg.assetId));
                        } catch (IOException ex) {
                            log.warn("Não foi possível reenviar asset " + msg.assetId + ": " + ex.getMessage());
                        }
                    } else {
                        // O servidor nao tem essa imagem guardada ainda --
                        // repassa o pedido pra todo mundo, na esperanca de
                        // que quem criou o token/ficha originalmente ainda
                        // esteja conectado e responda com "image_data"
                        // (que ai sim o servidor guarda e distribui).
                        broadcast(msg);
                    }
                }
                case "scene_sync" -> {
                    // Recebido de um CLIENTE (nao mandado por este servidor
                    // pra alguem) so pode significar uma coisa: o mestre
                    // trocou de mapa/cena e esta avisando todo mundo. So o
                    // mestre pode fazer isso -- um jogador comum trocando a
                    // cena de todo mundo seria bagunca.
                    if (msg.scene == null || !from.isGm()) break;
                    if (Boolean.TRUE.equals(msg.sideView) && from.sideScene != null) {
                        // mestre mexeu na cena separada que esta olhando (grade, tamanho...): so quem esta nela recebe
                        msg.scene.setHiddenFromPlayers(false);
                        sideScenes.put(from.sideScene, msg.scene);
                        enter(from.sideScene);
                        for (ClientHandler c : clients) {
                            if (c != from && c.getSession() != null && inScene(c)) c.send(NetMessage.sceneSwitch(sceneFor(c)));
                        }
                        break;
                    }
                    SceneModel next = msg.scene;
                    boolean merged = false;
                    if (msg.sceneKey != null && sideScenes.containsKey(msg.sceneKey)) {
                        // abriu pro grupo a cena onde alguem ja estava sozinho: o grupo vai pra la (com o que mudou nela)
                        next = sideScenes.remove(msg.sceneKey);
                        merged = true;
                        for (ClientHandler c : clients) if (msg.sceneKey.equals(c.sideScene)) c.sideScene = null;
                    }
                    if (merged) next.setHiddenFromPlayers(msg.scene.isHiddenFromPlayers());
                    from.sideScene = null;
                    groupScene = next;
                    groupKey = msg.sceneKey;
                    enter(null);
                    for (ClientHandler c : clients) {
                        if (c.getSession() == null || c.sideScene != null) continue;
                        NetMessage m = NetMessage.sceneSwitch(sceneFor(c));
                        if (c.isGm()) {
                            m.sceneKey = groupKey;
                            m.sideView = false;
                        }
                        c.send(m);
                    }
                    pruneSides();
                    if (merged) broadcastPresence();
                }
                case "hello" -> {
                    if (from.getSession() != null) {
                        break; // hello repetido na mesma conexao: ignora
                    }
                    int version = msg.protocolVersion == null ? 1 : msg.protocolVersion;
                    if (version < NetMessage.MIN_PROTOCOL_VERSION) {
                        from.send(NetMessage.chat("servidor", "Seu programa esta desatualizado (protocolo " + version
                                + ", a mesa usa " + NetMessage.PROTOCOL_VERSION + "). Atualize o VTT e conecte de novo."));
                        from.close();
                        break;
                    }
                    String ip = from.remoteAddress();
                    // Conexao local (a ponte do navegador, ou o proprio host) nao entra no
                    // limite por IP: a ponte faz o limite pelo IP REAL de cada navegador.
                    boolean local = ip.startsWith("127.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
                    if (!local && loginGuard.isBlocked(ip)) {
                        from.send(NetMessage.chat("servidor", "Muitas tentativas de senha erradas. Espere alguns "
                                + "minutos e tente de novo."));
                        from.close();
                        break;
                    }
                    if (!PasswordProof.matches(tablePassword, from.getChallenge(), msg.passwordProof)) {
                        if (!local) loginGuard.failed(ip);
                        // Pelo playit.gg (tunel no PC do mestre) todo mundo chega como 127.0.0.1 e fica fora do limite
                        // por IP: uma espera em cada senha errada deixa adivinhar a senha lenta demais.
                        else sleepQuietly(LOCAL_WRONG_PASSWORD_DELAY_MS);
                        from.send(NetMessage.chat("servidor", "Senha da mesa incorreta -- conexão encerrada."));
                        from.close();
                        break;
                    }
                    boolean claimsGm = Boolean.TRUE.equals(msg.isGm);
                    boolean gmAccepted = claimsGm && PasswordProof.matches(masterPassword, from.getChallenge(), msg.masterProof);
                    if (claimsGm && !gmAccepted && !local) loginGuard.failed(ip);
                    if (!claimsGm || gmAccepted) loginGuard.succeeded(ip);
                    if (claimsGm && !gmAccepted) {
                        log.warn(msg.player + " tentou entrar como mestre com senha de mestre incorreta -- "
                                + "entrou como jogador comum.");
                    }
                    PlayerSession session = new PlayerSession(msg.playerId, msg.player, gmAccepted);
                    from.setSession(session);
                    from.send(NetMessage.sceneSync(sceneFor(from)));
                    NetMessage lock = cameraLocks.get("");
                    if (lock != null && !gmAccepted && !curtain(from)) from.send(lock);
                    // Quem acabou de entrar precisa receber o estado atual de
                    // tudo que ja foi sincronizado antes dele chegar -- senao
                    // so veria fichas/monstros/party sincronizados DEPOIS da
                    // entrada dele.
                    sharedCharacters.values().stream().filter(sheet -> canSeeCharacter(from, sheet))
                            .forEach(sheet -> from.send(NetMessage.characterSync(sheet)));
                    sharedMobs.values().stream().filter(mob -> from.isGm() || !mob.isHiddenFromPlayers())
                            .forEach(mob -> from.send(NetMessage.mobSync(mob)));
                    sharedHandouts.values().forEach(h -> from.send(NetMessage.handoutSync(h)));
                    com.vttcaseiro.model.Campaign camp = sharedCampaign;
                    if (camp != null) from.send(NetMessage.campaignSync(from.isGm() ? camp : camp.publicCopy()));
                    playingAudio.values().forEach(a -> from.send(NetMessage.audio(a)));
                    if (!sharedShops.isEmpty()) from.send(NetMessage.shopsSync(sharedShops));
                    polls.forEach((id, p) -> from.send(pollFor(id, p, from)));
                    if (sharedParty != null) {
                        from.send(NetMessage.partySync(sharedParty));
                    }
                    sendKnownImageAssets(from);
                    broadcast(NetMessage.chat("servidor", session.playerName() + " entrou na mesa"
                            + (session.gm() ? " como mestre." : ".")));
                    broadcastPresence();
                }
                case "split_scene" -> splitScene(msg, from);
                case "poll" -> {
                    if (msg.text == null || msg.pollOptions == null || msg.pollOptions.size() < 2 || polls.size() > 200) break;
                    PollState p = new PollState();
                    p.author = from.getSession().playerName();
                    p.question = msg.label == null ? "" : msg.label.length() > 200 ? msg.label.substring(0, 200) : msg.label;
                    p.options = new java.util.ArrayList<>();
                    for (String o : msg.pollOptions.subList(0, Math.min(10, msg.pollOptions.size()))) {
                        p.options.add(o == null ? "" : o.length() > 80 ? o.substring(0, 80) : o);
                    }
                    polls.put(msg.text, p);
                    broadcastPoll(msg.text, p);
                }
                case "poll_vote" -> {
                    PollState p = msg.text == null ? null : polls.get(msg.text);
                    if (p == null || msg.pollVote == null || msg.pollVote < 0 || msg.pollVote >= p.options.size()) break;
                    p.votes.put(from.getSession().playerId(), msg.pollVote); // um voto por pessoa (da pra trocar)
                    broadcastPoll(msg.text, p);
                }
                case "shops_sync" -> {
                    if (!from.isGm()) break;
                    List<com.vttcaseiro.model.Shop> open = new java.util.ArrayList<>();
                    if (msg.shops != null) for (com.vttcaseiro.model.Shop s : msg.shops) if (s != null && s.isOpen()) open.add(s);
                    sharedShops = open;
                    NetMessage out = NetMessage.shopsSync(open);
                    for (ClientHandler c : clients) if (c != from && c.getSession() != null) c.send(out);
                }
                case "shop_request" -> {
                    if (msg.shopRequest == null) break;
                    msg.shopRequest.player = from.getSession().playerName();
                    boolean any = false;
                    for (ClientHandler c : clients) {
                        if (c.isGm() && c != from) {
                            c.send(msg);
                            any = true;
                        }
                    }
                    if (!any && !from.isGm()) from.send(NetMessage.chat("servidor", "Nenhum mestre conectado pra atender a loja."));
                }
                case "gm_view" -> {
                    if (!from.isGm()) break;
                    String key = msg.sceneKey != null && sideScenes.containsKey(msg.sceneKey) ? msg.sceneKey : null;
                    from.sideScene = key;
                    enter(key);
                    NetMessage m = NetMessage.sceneSwitch(sceneFor(from));
                    m.sceneKey = key == null ? groupKey : key;
                    m.sideView = key != null;
                    from.send(m);
                    pruneSides();
                    broadcastPresence();
                }
                default -> log.warn("Tipo de mensagem desconhecido: " + msg.type);
            }
            } finally {
                enter(null);
            }
        }
    }

    /** Manda pro jogador que acabou de entrar toda imagem que o servidor
     * ja tem guardada e que algum objeto conhecido (cena, tokens, fichas,
     * monstros) referencia -- sem isso, quem entra tarde veria os
     * "placeholders" (silhueta padrao) em tudo, mesmo com o resto do
     * estado (ficha, token, mapa) ja sincronizado certinho. */
    private void sendKnownImageAssets(ClientHandler from) {
        if (imageAssetStore == null) {
            return;
        }
        Set<String> assetIds = new LinkedHashSet<>();
        boolean sceneVisible = !curtain(from); // cena escondida: nem a imagem do mapa vai
        if (sceneVisible && scene.getBackgroundImageAssetId() != null) {
            assetIds.add(scene.getBackgroundImageAssetId());
        }
        for (TokenModel token : sceneVisible ? scene.getTokens() : List.<TokenModel>of()) {
            if (token.getImageAssetId() != null) {
                assetIds.add(token.getImageAssetId());
            }
        }
        for (CharacterSheet sheet : sharedCharacters.values()) {
            if (!canSeeCharacter(from, sheet)) continue;
            if (sheet.getTemplate() != null) {
                for (com.vttcaseiro.character.SheetTemplate.Page p : sheet.getTemplate().getPages()) {
                    if (p.assetId != null) assetIds.add(p.assetId);
                }
            }
            if (sheet.getPortraitImageAssetId() != null) {
                assetIds.add(sheet.getPortraitImageAssetId());
            }
        }
        for (MobSheet mob : sharedMobs.values()) {
            if (mob.isHiddenFromPlayers() && !from.isGm()) continue;
            if (mob.getTemplate() != null) {
                for (com.vttcaseiro.character.SheetTemplate.Page p : mob.getTemplate().getPages()) {
                    if (p.assetId != null) assetIds.add(p.assetId);
                }
            }
            if (mob.getPortraitImageAssetId() != null) {
                assetIds.add(mob.getPortraitImageAssetId());
            }
        }
        for (String assetId : assetIds) {
            if (imageAssetStore.exists(assetId)) {
                try {
                    from.send(ImageAssetRelay.encode(imageAssetStore, assetId));
                } catch (IOException ex) {
                    log.warn("Não foi possível reenviar asset " + assetId + " pro novo jogador: " + ex.getMessage());
                }
            }
        }
    }

    private void broadcastPresence() {
        List<String> names = getConnectedSessions().stream()
                .map(PlayerSession::displayName)
                .collect(Collectors.toList());
        NetMessage presence = NetMessage.presence(names);
        presence.players = getConnectedSessions().stream()
                .map(s -> new NetMessage.PlayerInfo(s.playerId(), s.playerName(), s.gm()))
                .collect(Collectors.toList());
        NetMessage forGm = NetMessage.presence(names);
        forGm.players = new java.util.ArrayList<>();
        for (ClientHandler c : clients) {
            PlayerSession s = c.getSession();
            if (s == null) continue;
            NetMessage.PlayerInfo info = new NetMessage.PlayerInfo(s.playerId(), s.playerName(), s.gm());
            info.sceneKey = c.sideScene;
            forGm.players.add(info);
        }
        String pub = NetCodec.encode(presence);
        String gm = NetCodec.encode(forGm);
        for (ClientHandler c : clients) c.sendEncoded(c.isGm() ? gm : pub);
    }

    private Optional<TokenModel> findToken(String id) {
        if (id == null) return Optional.empty();
        return scene.getTokens().stream().filter(t -> id.equals(t.getId())).findFirst();
    }

    /** Token entrou em zonas da cena atual: cada mestre conectado fica sabendo (esteja olhando qual cena for). */
    private void zoneEvents(TokenModel token, double oldX, double oldY) {
        List<com.vttcaseiro.model.MapZone> hit = com.vttcaseiro.service.ZoneRules.entered(scene, token, oldX, oldY);
        if (hit.isEmpty()) return;
        String owner = null;
        for (ClientHandler c : clients) {
            if (c.getSession() != null && !c.isGm() && controls(c, token)) owner = c.getSession().playerName();
        }
        for (com.vttcaseiro.model.MapZone z : hit) {
            if (z.isOnce()) z.setTriggered(true);
            NetMessage ev = NetMessage.zoneEvent(z, token, ctxKey, owner);
            for (ClientHandler c : clients) if (c.isGm()) c.send(ev);
        }
    }

    /** Mestre leva um jogador pra outra cena (ou de volta pro grupo). */
    private void splitScene(NetMessage msg, ClientHandler from) {
        if (!from.isGm()) {
            from.send(NetMessage.chat("servidor", "Só o mestre separa o grupo."));
            return;
        }
        ClientHandler target = null;
        for (ClientHandler c : clients) {
            if (c.getSession() != null && !c.isGm() && java.util.Objects.equals(c.getSession().playerId(), msg.playerId)) target = c;
        }
        if (target == null) {
            from.send(NetMessage.chat("servidor", "Esse jogador não está conectado."));
            return;
        }
        String key = msg.sceneKey;
        if (key != null && key.equals(groupKey)) key = null; // "levar pra cena do grupo" = voltar pro grupo
        if (key != null && !sideScenes.containsKey(key)) {
            if (msg.scene == null) {
                from.send(NetMessage.chat("servidor", "Cena não encontrada."));
                return;
            }
            msg.scene.setHiddenFromPlayers(false);
            sideScenes.put(key, msg.scene);
        }
        TokenModel carried = msg.token;
        if (carried != null) {
            // portal: o token sai da cena onde estava...
            enter(target.sideScene);
            findToken(carried.getId()).ifPresent(t -> {
                scene.removeToken(t);
                NetMessage gone = NetMessage.removeToken(t.getId());
                for (ClientHandler c : clients) {
                    if (c.getSession() != null && inScene(c) && (c.isGm() || c.knownTokens.remove(t.getId()))) c.send(gone);
                }
            });
        }
        target.sideScene = key;
        enter(key);
        if (carried != null && findToken(carried.getId()).isEmpty()) {
            // ...e chega na de destino (quem ja estava la ve chegar)
            scene.addToken(carried);
            NetMessage arrived = NetMessage.addToken(carried);
            for (ClientHandler c : clients) {
                if (c == target || c.getSession() == null || !inScene(c)) continue;
                if (c.isGm()) c.send(arrived);
                else resync(c);
            }
        }
        target.send(NetMessage.sceneSwitch(sceneFor(target)));
        NetMessage lock = cameraLocks.get(key == null ? "" : key);
        target.send(lock != null ? lock : NetMessage.cameraLock(false, 0, 0, 1)); // a camera da cena nova
        target.send(NetMessage.chat("servidor", key == null ? "Você voltou pra cena do grupo."
                : "O mestre levou você pra outra cena (o resto do grupo não vê o que acontece aqui)."));
        pruneSides();
        broadcastPresence();
    }

    /** "mestre"/"gm"/"dm" = todos os mestres conectados. */
    static boolean meansGm(String name) {
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        return n.equals("mestre") || n.equals("gm") || n.equals("dm");
    }

    /** Sussurro: so quem mandou e o(s) destinatario(s) recebem. Ninguem com
     * esse nome = avisa so quem mandou (nada vaza pra mesa). */
    private void deliverWhisper(NetMessage msg, ClientHandler from) {
        String target = msg.whisperTo.trim();
        List<ClientHandler> targets = clients.stream()
                .filter(c -> c != from && c.getSession() != null)
                .filter(c -> meansGm(target) ? c.isGm() : c.getSession().playerName().equalsIgnoreCase(target))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            from.send(NetMessage.chat("servidor", "Ninguém conectado chamado \"" + target
                    + "\" -- o sussurro não foi entregue."));
            return;
        }
        from.send(msg);
        for (ClientHandler c : targets) c.send(msg);
    }

    /** Pros mestres conectados + {@code also} (quem originou), sem repetir. */
    private void sendToGmsAnd(NetMessage msg, ClientHandler also) {
        for (ClientHandler c : clients) {
            if (c == also || c.isGm()) c.send(msg);
        }
    }

    /** Um jogador comum pode mexer no PV/condicoes do token da PROPRIA
     * ficha mesmo que o token tenha sido colocado no mapa pelo mestre. */
    private boolean ownsLinkedSheet(ClientHandler from, TokenModel token) {
        String sheetId = token.getCharacterSheetId();
        return sheetId != null && from.getSession() != null
                && from.getSession().playerId() != null
                && from.getSession().playerId().equals(characterOwners.get(sheetId));
    }

    /** Mensagem sobre um token: escondido = so mestres recebem. */
    private void broadcastToken(NetMessage msg, boolean hidden) {
        if (!hidden) {
            broadcast(msg);
            return;
        }
        String line = NetCodec.encode(msg);
        for (ClientHandler c : clients) {
            if (c.isGm() && inScene(c)) c.sendEncoded(line);
        }
    }

    /** A cena como ESSE cliente pode ver: jogador nao recebe token escondido. */
    /** A cena como ESTE cliente pode ver: sem tokens ocultos nem os que ele nao enxerga. */
    private SceneModel sceneFor(ClientHandler c) {
        java.util.Set<String> visible = visibleIds(c);
        c.knownTokens.clear();
        c.knownTokens.addAll(visible);
        if (curtain(c)) {
            // mestre preparando: o jogador so fica sabendo que tem uma cena escondida (nem o nome vai)
            SceneModel blank = new SceneModel();
            blank.setName("");
            blank.setHiddenFromPlayers(true);
            return blank;
        }
        boolean hiddenPins = scene.getPins().stream().anyMatch(com.vttcaseiro.model.MapPin::isHidden)
                || scene.getObjects().stream().anyMatch(com.vttcaseiro.model.MapObject::isHidden)
                || scene.getZones().stream().anyMatch(z -> !z.isVisibleToPlayers());
        if (c.isGm() || (visible.size() == scene.getTokens().size() && !hiddenPins)) return scene;
        SceneModel copy = NetCodec.deepCopy(scene, SceneModel.class);
        copy.getTokens().removeIf(t -> !visible.contains(t.getId()));
        copy.getPins().removeIf(com.vttcaseiro.model.MapPin::isHidden); // pin secreto nem sai do servidor
        copy.getObjects().removeIf(com.vttcaseiro.model.MapObject::isHidden);
        copy.getZones().removeIf(z -> !z.isVisibleToPlayers()); // armadilha secreta nem sai do servidor
        return copy;
    }

    private boolean controls(ClientHandler c, TokenModel t) {
        return c.ownsToken(t.getOwnerId()) || ownsLinkedSheet(c, t);
    }

    /**
     * Ids dos tokens que este cliente pode RECEBER agora: mestre ve tudo;
     * jogador nunca ve o oculto; com visao dinamica, so o que os tokens dele
     * enxergam (paredes, escuro, luz -- ver VisionCheck). Assim quem fucar
     * nos dados do programa nao acha o monstro atras da parede.
     */
    private java.util.Set<String> visibleIds(ClientHandler c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (curtain(c)) return out; // cena escondida: nenhum token sai pro jogador
        for (TokenModel t : scene.getTokens()) {
            if (c.isGm() || !t.isHiddenFromPlayers()) out.add(t.getId());
        }
        // condicao Invisivel: os outros jogadores nem recebem o token (o dono e o mestre continuam vendo)
        if (!c.isGm()) out.removeIf(id -> scene.findToken(id)
                .map(t -> t.hasCondition(com.vttcaseiro.model.Condition.INVISIBLE) && !controls(c, t)).orElse(false));
        if (c.isGm() || !scene.isVisionEnabled()) return out;
        List<TokenModel> mine = new java.util.ArrayList<>();
        for (TokenModel t : scene.getTokens()) if (out.contains(t.getId()) && controls(c, t)) mine.add(t);
        com.vttcaseiro.vision.VisionCheck eyes = com.vttcaseiro.vision.VisionCheck.of(scene, mine);
        out.removeIf(id -> scene.findToken(id).map(t -> !mine.contains(t) && !eyes.canSee(t)).orElse(true));
        return out;
    }

    /** Manda pro jogador so a diferenca: o que apareceu (add) e o que sumiu (remove). */
    private void resync(ClientHandler c) {
        if (c.getSession() == null || c.isGm()) return;
        java.util.Set<String> now = visibleIds(c);
        for (String id : new java.util.ArrayList<>(c.knownTokens)) {
            if (!now.contains(id)) c.send(NetMessage.removeToken(id));
        }
        for (String id : now) {
            if (!c.knownTokens.contains(id)) findToken(id).ifPresent(t -> c.send(NetMessage.addToken(t)));
        }
        c.knownTokens.clear();
        c.knownTokens.addAll(now);
    }

    /** Espera depois de senha errada vinda de conexao local (tunel do playit.gg, ponte do navegador). */
    static final long LOCAL_WRONG_PASSWORD_DELAY_MS = 1500;

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void resyncAll() {
        for (ClientHandler c : clients) if (inScene(c)) resync(c);
    }

    /**
     * Um token mudou (moveu, entrou, mudou luz/dono/oculto): o mestre recebe a
     * mensagem; cada jogador recebe so se ja via e continua vendo (se passou a
     * ver, o resync manda o token inteiro; se deixou, manda remove).
     */
    private void routeTokenChange(NetMessage msg, String tokenId) {
        String encoded = NetCodec.encode(msg);
        for (ClientHandler c : clients) {
            if (c.getSession() == null || !inScene(c)) continue;
            if (c.isGm()) {
                c.sendEncoded(encoded);
                continue;
            }
            boolean before = c.knownTokens.contains(tokenId);
            resync(c);
            if (before && c.knownTokens.contains(tokenId)) c.sendEncoded(encoded);
        }
    }

    private void broadcastExcept(NetMessage msg, ClientHandler except) {
        String line = NetCodec.encode(msg);
        boolean sceneMsg = SCENE_TYPES.contains(msg.type);
        for (ClientHandler client : clients) {
            if (client != except && !(sceneMsg && (curtain(client) || !inScene(client)))) client.sendEncoded(line);
        }
    }

    /** Codifica UMA vez e enfileira pra todos (nao espera a rede de ninguem
     * -- ver ClientHandler). */
    private void broadcast(NetMessage msg) {
        String line = NetCodec.encode(msg);
        boolean sceneMsg = SCENE_TYPES.contains(msg.type);
        for (ClientHandler client : clients) {
            if (!(sceneMsg && (curtain(client) || !inScene(client)))) client.sendEncoded(line);
        }
    }

    /** Mensagens que mostram o que tem na cena (nao vao pro jogador enquanto ela esta escondida). */
    private static final java.util.Set<String> SCENE_TYPES = java.util.Set.of("move_token", "add_token", "update_token",
            "remove_token", "objects_sync", "pins_sync", "walls_sync", "door_toggle", "drawing_add", "drawing_remove",
            "drawing_clear", "fog_sync", "encounter_sync", "map_ping", "camera_lock");

    /** Este cliente esta atras da cortina (cena escondida e ele nao e o mestre)? */
    private boolean curtain(ClientHandler c) {
        return scene != null && scene.isHiddenFromPlayers() && !c.isGm();
    }

    /** O jogador tem uma ficha com esse nome? (pra falar como ela no chat) */
    private boolean ownsCharacterNamed(ClientHandler c, String name) {
        if (c.getSession() == null) return false;
        String me = c.getSession().playerId();
        for (CharacterSheet s : sharedCharacters.values()) {
            if (s.getName() == null || !s.getName().strip().equalsIgnoreCase(name)) continue;
            String owner = characterOwners.get(s.getId());
            if (owner == null) owner = s.getOwnerPlayerId();
            if (me.equals(owner)) return true;
        }
        return false;
    }

    /** Ficha escondida: so o mestre e o dono recebem. */
    private boolean canSeeCharacter(ClientHandler c, CharacterSheet sheet) {
        if (c.isGm() || !sheet.isHiddenFromPlayers()) return true;
        String owner = characterOwners.get(sheet.getId());
        if (owner == null) owner = sheet.getOwnerPlayerId();
        return owner != null && c.getSession() != null && owner.equals(c.getSession().playerId());
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (ClientHandler client : clients) {
            client.abort();
        }
        clients.clear();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 55055;
        GameServer server = new GameServer(port, new SceneModel());
        server.start();
        System.out.println("Servidor rodando na porta " + server.getPort() + ". Ctrl+C para parar.");
        Thread.currentThread().join();
    }
}
