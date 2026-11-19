package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.party.Party;

/**
 * Uma mensagem do protocolo. E um "envelope" simples -- todos os campos
 * existem na mesma classe e cada tipo de mensagem so usa os que precisa
 * (os outros ficam null). Mais simples de serializar com o Gson (nao
 * precisa de adapter nenhum, um {@code gson.toJson(msg)} direto ja
 * funciona) e mais facil de estender depois.
 * <p>
 * Se um dia isso crescer muito (muitos tipos, muitos campos so usados por
 * um tipo cada), vale trocar por uma classe por tipo de mensagem com um
 * adapter de tipo polimorfico do Gson -- mas pro tamanho desse projeto,
 * essa simplicidade vale mais que a pureza.
 */
public class NetMessage {
    /** Versao do protocolo. Sobe quando um campo muda de significado (nao
     * so quando um campo novo e ADICIONADO, isso e sempre seguro). Um
     * cliente/servidor de versao diferente ainda consegue ler o que
     * entende e ignorar o resto -- o Gson simplesmente deixa campos
     * desconhecidos de lado -- mas pelo menos da pra registrar/alertar a
     * diferenca em vez de falhar calado. */
    /** 2 = update_token, rolagem com rotulo, sussurro/escondida, dono de
     * ficha/token, encounter_sync. O servidor recusa versao menor que
     * {@link #MIN_PROTOCOL_VERSION} com uma mensagem clara (antes aceitava
     * qualquer coisa e o cliente antigo so "nao via" metade da mesa). */
    public static final int PROTOCOL_VERSION = 3;
    public static final int MIN_PROTOCOL_VERSION = 3;

    public String type;

    // hello / chat / roll / roll_result
    public String player;
    public String text;

    // so hello
    public String playerId;
    public Boolean isGm;
    public Integer protocolVersion;
    /** Senha da mesa (pra entrar) -- null/vazio e tratado como "mesa sem
     * senha" (ver GameServer.passwordMatches), nao como senha errada. */
    public String password;
    /** So preenchido quando isGm=true -- senha SEPARADA da de entrar na
     * mesa, pra confirmar quem pode controlar como mestre (CA/mapas
     * globais, mover token de qualquer um). Sem ela correta, o servidor
     * aceita a conexao normalmente mas rebaixa pra jogador comum -- ver
     * GameServer, caso "hello". */
    public String masterPassword;

    // challenge (servidor -> cliente, primeira mensagem) / hello (resposta):
    // a senha nunca viaja, so a prova HMAC dela sobre o desafio (PasswordProof).
    public String challenge;
    public String passwordProof;
    public String masterProof;

    // move_token / remove_token
    public String tokenId;
    public Double x;
    /** "gm_request": pedido de um jogador pro mestre (dano/cura/salvaguarda num token alheio). */
    public com.vttcaseiro.model.GmRequest gmRequest;
    /** "objects_sync": objetos soltos da cena (sem os ocultos pra quem nao e mestre). */
    public java.util.List<com.vttcaseiro.model.MapObject> objects;

    /** "pins_sync": pins da cena (o servidor tira os ocultos pra quem nao e mestre). */
    public java.util.List<com.vttcaseiro.model.MapPin> pins;

    /** "walls_sync": paredes + visao da cena. */
    public java.util.List<com.vttcaseiro.model.Wall> walls;
    public Boolean visionOn;
    public Boolean sceneDark;
    /** Cena em penumbra e neblina revelada pela visao (vao junto com as paredes). */
    public Boolean sceneDim;
    public Boolean fogByVision;
    /** Luzes fixas do mapa (vao junto com as paredes). */
    public java.util.List<com.vttcaseiro.model.MapLight> lights;
    /** "audio": musica/ambiente/efeito do mestre. */
    public com.vttcaseiro.model.AudioCommand audio;
    /** "drawing_add". */
    public com.vttcaseiro.model.Drawing drawing;
    /** "handout_sync" (entrada do diario revelada). */
    public com.vttcaseiro.model.Handout handout;
    public Double y;

    // add_token
    public TokenModel token;

    // scene_sync
    public SceneModel scene;

    // roll
    public String expression;
    /** null = rolagem normal (usa "expression"); "advantage"/"disadvantage"
     * ignora "expression" e usa "modifier" com DiceRoller.rollWithAdvantage/
     * Disadvantage no servidor. */
    public String rollMode;
    public Integer modifier;

    // roll_result
    public String detail;
    public Integer total;

    // roll / roll_result: rotulo da rolagem ("Elora - Furtividade") e o d20
    // natural (pra marcar critico/falha critica no chat). Campos opcionais:
    // um cliente antigo que nao manda/le eles continua funcionando.
    public String label;
    public Integer naturalD20;

    // chat / roll / roll_result privados (todos opcionais):
    //  whisperTo = nome do destinatario de um sussurro ("mestre" = todos os
    //              mestres); o servidor so entrega pra quem mandou + ele.
    //  hidden    = rolagem escondida: so quem rolou e os mestres veem.
    public String whisperTo;
    public Boolean hidden;

    // encounter_sync -- rodada/turno/efeitos com duracao (so o mestre manda).
    public com.vttcaseiro.model.Encounter encounter;

    // fog_sync -- neblina de guerra (so o mestre manda).
    public com.vttcaseiro.model.Fog fog;

    // presence -- alem dos nomes, id e papel de cada um (o mestre precisa do
    // id pra "Dar controle a..." um token/ficha).
    public java.util.List<PlayerInfo> players;

    public static class PlayerInfo {
        public String playerId;
        public String name;
        public boolean gm;

        public PlayerInfo() {
        }

        public PlayerInfo(String playerId, String name, boolean gm) {
            this.playerId = playerId;
            this.name = name;
            this.gm = gm;
        }
    }

    // presence
    public java.util.List<String> onlinePlayers;

    // character_sync -- ficha de personagem inteira (inclui o inventario
    // individual dela, ver CharacterSheet.getInventory()). O id da ficha
    // (CharacterSheet.getId()) e quem decide se isso e uma ficha nova ou
    // a atualizacao de uma ja conhecida -- ver GameServer.sharedCharacters.
    public CharacterSheet character;

    // mob_sync -- ficha de monstro inteira (inclui o loot dela).
    public MobSheet mob;

    // party_sync -- a Party da mesa (inclui o inventario COMPARTILHADO do
    // grupo, ver Party.getSharedInventory()). So existe uma Party por
    // mesa, entao nao precisa de id pra saber qual e qual.
    public Party party;

    // image_data / image_request -- ver com.vttcaseiro.library.ImageAssetStore.
    // assetId e o hash+extensao (ex.: "ab12...ef.png"); assetBase64 so
    // existe em image_data (image_request so pede, nao manda nada).
    public String assetId;
    public String assetBase64;

    public NetMessage() {
    }

    public static NetMessage hello(String player, String playerId, boolean isGm) {
        return hello(player, playerId, isGm, null, null);
    }

    public static NetMessage hello(String player, String playerId, boolean isGm, String password, String masterPassword) {
        NetMessage m = new NetMessage();
        m.type = "hello";
        m.player = player;
        m.playerId = playerId;
        m.isGm = isGm;
        // Compatibilidade de assinatura: a senha em texto NAO vai mais na
        // mensagem -- quem chama passa a prova pelo hello(..., challenge).
        m.protocolVersion = PROTOCOL_VERSION;
        return m;
    }

    /** Hello com as provas das senhas sobre o desafio recebido. */
    public static NetMessage hello(String player, String playerId, boolean isGm, String password,
                                   String masterPassword, String challenge) {
        NetMessage m = hello(player, playerId, isGm);
        m.passwordProof = PasswordProof.prove(password, challenge);
        m.masterProof = PasswordProof.prove(masterPassword, challenge);
        return m;
    }

    public static NetMessage challenge(String challenge) {
        NetMessage m = new NetMessage();
        m.type = "challenge";
        m.challenge = challenge;
        m.protocolVersion = PROTOCOL_VERSION;
        return m;
    }

    public static NetMessage presence(java.util.List<String> onlinePlayers) {
        NetMessage m = new NetMessage();
        m.type = "presence";
        m.onlinePlayers = onlinePlayers;
        return m;
    }

    public static NetMessage ping() {
        NetMessage m = new NetMessage();
        m.type = "ping";
        return m;
    }

    public static NetMessage pong() {
        NetMessage m = new NetMessage();
        m.type = "pong";
        return m;
    }

    public static NetMessage sceneSync(SceneModel scene) {
        NetMessage m = new NetMessage();
        m.type = "scene_sync";
        m.scene = scene;
        return m;
    }

    public static NetMessage moveToken(String tokenId, double x, double y) {
        NetMessage m = new NetMessage();
        m.type = "move_token";
        m.tokenId = tokenId;
        m.x = x;
        m.y = y;
        return m;
    }

    public static NetMessage addToken(TokenModel token) {
        NetMessage m = new NetMessage();
        m.type = "add_token";
        m.token = token;
        return m;
    }

    /** Estado do token (PV, condicoes, efeitos, iniciativa...) sem posicao. */
    public static NetMessage updateToken(TokenModel token) {
        NetMessage m = new NetMessage();
        m.type = "update_token";
        m.token = token;
        return m;
    }

    public static NetMessage encounterSync(com.vttcaseiro.model.Encounter encounter) {
        NetMessage m = new NetMessage();
        m.type = "encounter_sync";
        m.encounter = encounter;
        return m;
    }

    public static NetMessage objectsSync(java.util.List<com.vttcaseiro.model.MapObject> objects) {
        NetMessage m = new NetMessage();
        m.type = "objects_sync";
        m.objects = new java.util.ArrayList<>(objects);
        return m;
    }

    public static NetMessage pinsSync(java.util.List<com.vttcaseiro.model.MapPin> pins) {
        NetMessage m = new NetMessage();
        m.type = "pins_sync";
        m.pins = new java.util.ArrayList<>(pins);
        return m;
    }

    public static NetMessage wallsSync(SceneModel s) {
        NetMessage m = new NetMessage();
        m.type = "walls_sync";
        m.walls = new java.util.ArrayList<>(s.getWalls());
        m.visionOn = s.isVisionEnabled();
        m.sceneDark = s.isDark();
        m.sceneDim = s.isDim();
        m.fogByVision = s.isFogByVision();
        m.lights = new java.util.ArrayList<>(s.getLights());
        return m;
    }

    public static NetMessage doorToggle(String wallId) {
        NetMessage m = new NetMessage();
        m.type = "door_toggle";
        m.text = wallId;
        return m;
    }

    public static NetMessage audio(com.vttcaseiro.model.AudioCommand c) {
        NetMessage m = new NetMessage();
        m.type = "audio";
        m.audio = c;
        return m;
    }

    public static NetMessage drawingAdd(com.vttcaseiro.model.Drawing d) {
        NetMessage m = new NetMessage();
        m.type = "drawing_add";
        m.drawing = d;
        return m;
    }

    public static NetMessage drawingRemove(String id) {
        NetMessage m = new NetMessage();
        m.type = "drawing_remove";
        m.text = id;
        return m;
    }

    /** ownerId null = apagar todos. */
    public static NetMessage drawingClear(String ownerId) {
        NetMessage m = new NetMessage();
        m.type = "drawing_clear";
        m.text = ownerId;
        return m;
    }

    public static NetMessage handoutSync(com.vttcaseiro.model.Handout h) {
        NetMessage m = new NetMessage();
        m.type = "handout_sync";
        m.handout = h;
        return m;
    }

    public static NetMessage handoutRemove(String id) {
        NetMessage m = new NetMessage();
        m.type = "handout_remove";
        m.text = id;
        return m;
    }

    public static NetMessage gmRequest(com.vttcaseiro.model.GmRequest request) {
        NetMessage m = new NetMessage();
        m.type = "gm_request";
        m.gmRequest = request;
        return m;
    }

    /** "Olha aqui!" no mapa: um circulo pulsante que todo mundo ve. */
    public static NetMessage mapPing(double x, double y) {
        NetMessage m = new NetMessage();
        m.type = "map_ping";
        m.x = x;
        m.y = y;
        return m;
    }

    public static NetMessage fogSync(com.vttcaseiro.model.Fog fog) {
        NetMessage m = new NetMessage();
        m.type = "fog_sync";
        m.fog = fog;
        return m;
    }

    public static NetMessage removeToken(String tokenId) {
        NetMessage m = new NetMessage();
        m.type = "remove_token";
        m.tokenId = tokenId;
        return m;
    }

    public static NetMessage roll(String player, String expression) {
        NetMessage m = new NetMessage();
        m.type = "roll";
        m.player = player;
        m.expression = expression;
        return m;
    }

    public static NetMessage rollAdvantage(String player, int modifier) {
        NetMessage m = new NetMessage();
        m.type = "roll";
        m.player = player;
        m.rollMode = "advantage";
        m.modifier = modifier;
        return m;
    }

    public static NetMessage rollDisadvantage(String player, int modifier) {
        NetMessage m = new NetMessage();
        m.type = "roll";
        m.player = player;
        m.rollMode = "disadvantage";
        m.modifier = modifier;
        return m;
    }

    public static NetMessage rollResult(String player, String detail, int total) {
        NetMessage m = new NetMessage();
        m.type = "roll_result";
        m.player = player;
        m.detail = detail;
        m.total = total;
        return m;
    }

    public static NetMessage chat(String player, String text) {
        NetMessage m = new NetMessage();
        m.type = "chat";
        m.player = player;
        m.text = text;
        return m;
    }

    /** Imagem no chat: os bytes vao antes por image_data; aqui so o id e a legenda. */
    public static NetMessage chatImage(String player, String caption, String assetId, String whisperTo) {
        NetMessage m = chat(player, caption == null ? "" : caption);
        m.assetId = assetId;
        m.whisperTo = whisperTo;
        return m;
    }

    public static NetMessage characterSync(CharacterSheet character) {
        NetMessage m = new NetMessage();
        m.type = "character_sync";
        m.character = character;
        return m;
    }

    public static NetMessage mobSync(MobSheet mob) {
        NetMessage m = new NetMessage();
        m.type = "mob_sync";
        m.mob = mob;
        return m;
    }

    public static NetMessage partySync(Party party) {
        NetMessage m = new NetMessage();
        m.type = "party_sync";
        m.party = party;
        return m;
    }

    /** Mesmo tipo "scene_sync" usado pra mandar a cena atual pra quem
     * acabou de entrar -- reusado aqui pro MESTRE avisar quem ja esta
     * conectado que a cena/mapa trocou no meio da sessao (ver
     * GameServer.handleIncoming, caso "scene_sync" recebido de um
     * cliente em vez de so mandado pra um). */
    public static NetMessage sceneSwitch(SceneModel scene) {
        return sceneSync(scene);
    }

    public static NetMessage imageData(String assetId, String base64) {
        NetMessage m = new NetMessage();
        m.type = "image_data";
        m.assetId = assetId;
        m.assetBase64 = base64;
        return m;
    }

    public static NetMessage imageRequest(String assetId) {
        NetMessage m = new NetMessage();
        m.type = "image_request";
        m.assetId = assetId;
        return m;
    }
}
