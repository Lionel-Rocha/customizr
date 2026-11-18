package top.customizr.system.service;

import top.customizr.system.dto.FogRequest;
import top.customizr.system.dto.FogResponse;
import top.customizr.system.dto.InitiativeEntryRequest;
import top.customizr.system.dto.InitiativeResponse;
import top.customizr.system.dto.InitiativeRollRequest;
import top.customizr.system.dto.MoveTokenRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.SceneResponse;
import top.customizr.system.dto.TokenMovedResponse;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.DoorRequest;
import top.customizr.system.dto.MarkersRequest;
import top.customizr.system.dto.CampaignIconRequest;
import top.customizr.system.dto.CampaignIconResponse;
import top.customizr.system.dto.MapObjectRequest;
import top.customizr.system.dto.MapObjectResponse;
import top.customizr.system.dto.PlaceObjectRequest;
import top.customizr.system.dto.TemplateRequest;
import top.customizr.system.dto.TemplateResponse;
import top.customizr.system.dto.TokenPlacementRequest;
import top.customizr.system.dto.VisionRequest;
import top.customizr.system.dto.WallRequest;
import top.customizr.system.dto.WallResponse;
import top.customizr.system.model.Wall;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.InitiativeTracker;
import top.customizr.system.model.Monster;
import top.customizr.system.model.NPC;
import top.customizr.system.model.Scene;
import top.customizr.system.model.Token;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.InitiativeTrackerRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.NpcRepository;
import top.customizr.system.repository.SceneRepository;
import top.customizr.system.repository.TokenRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mesa ao vivo: cenas (mapa, grade, neblina), tokens e iniciativa. O mestre monta tudo; o jogador
 * ve a cena ativa (sem tokens ocultos) e move os tokens dos proprios personagens. Toda mudanca vira
 * evento em {@link LiveEventService}.
 */
@Service
public class LiveTableService {

    private final SceneRepository sceneRepository;
    private final TokenRepository tokenRepository;
    private final InitiativeTrackerRepository trackerRepository;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final NpcRepository npcRepository;
    private final MonsterRepository monsterRepository;
    private final MasterGuard masterGuard;
    private final LiveEventService events;
    private final top.customizr.system.repository.WallRepository wallRepository;
    private final top.customizr.system.repository.AreaTemplateRepository templateRepository;
    private final top.customizr.system.repository.MapObjectRepository mapObjectRepository;
    private final top.customizr.system.repository.ImageAssetRepository imageAssetRepository;
    private final top.customizr.system.repository.CampaignIconRepository iconRepository;

    public LiveTableService(SceneRepository sceneRepository, TokenRepository tokenRepository,
                            InitiativeTrackerRepository trackerRepository, CampaignRepository campaignRepository,
                            CharacterRepository characterRepository, NpcRepository npcRepository,
                            MonsterRepository monsterRepository, MasterGuard masterGuard, LiveEventService events,
                            top.customizr.system.repository.WallRepository wallRepository,
                            top.customizr.system.repository.AreaTemplateRepository templateRepository,
                            top.customizr.system.repository.MapObjectRepository mapObjectRepository,
                            top.customizr.system.repository.ImageAssetRepository imageAssetRepository,
                            top.customizr.system.repository.CampaignIconRepository iconRepository) {
        this.iconRepository = iconRepository;
        this.mapObjectRepository = mapObjectRepository;
        this.imageAssetRepository = imageAssetRepository;
        this.templateRepository = templateRepository;
        this.wallRepository = wallRepository;
        this.sceneRepository = sceneRepository;
        this.tokenRepository = tokenRepository;
        this.trackerRepository = trackerRepository;
        this.campaignRepository = campaignRepository;
        this.characterRepository = characterRepository;
        this.npcRepository = npcRepository;
        this.monsterRepository = monsterRepository;
        this.masterGuard = masterGuard;
        this.events = events;
    }

    // ------------------------------------------------------------------ eventos

    @Transactional
    public SseEmitter subscribe(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, campaign);
        return events.subscribe(campaignId, new LiveEventService.Listener(user.getUserId(), user.getName(),
                masterGuard.canManage(user, campaign)));
    }

    // ------------------------------------------------------------------ cenas

    @Transactional
    public SceneResponse createScene(UUID campaignId, SceneRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        requireBody(r);
        Scene scene = valid(() -> new Scene(campaign, r.name(), r.mapUrl(), r.gridType(), r.gridSizePx(),
                r.feetPerCell(), r.widthPx(), r.heightPx()));
        List<Scene> existing = sceneRepository.findByCampaignOrderByName(campaign);
        scene = sceneRepository.save(scene);
        if (existing.isEmpty()) activate(scene, existing);
        return SceneResponse.from(scene, true);
    }

    /** Mestre: todas as cenas; jogador: so a ativa. */
    @Transactional
    public List<SceneResponse> listScenes(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        return sceneRepository.findByCampaignOrderByName(campaign).stream()
                .filter(s -> master || s.isActive())
                .map(s -> SceneResponse.from(s, master)).toList();
    }

    @Transactional
    public SceneResponse readScene(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        boolean master = masterGuard.requireMember(username, scene.getCampaign());
        if (!master && !scene.isActive()) throw notFound("Scene");
        return SceneResponse.from(scene, master);
    }

    @Transactional
    public SceneResponse updateScene(UUID sceneId, SceneRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        requireBody(r);
        valid(() -> { scene.update(r.name(), r.mapUrl(), r.gridType(), r.gridSizePx(), r.feetPerCell(), r.widthPx(), r.heightPx()); return null; });
        broadcastScene(scene);
        return SceneResponse.from(scene, true);
    }

    @Transactional
    public void deleteScene(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        UUID campaignId = scene.getCampaign().getId();
        sceneRepository.delete(scene);
        events.publish(campaignId, "scene_deleted", Map.of("sceneId", sceneId));
    }

    /** Troca a cena que os jogadores veem. */
    @Transactional
    public SceneResponse activateScene(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        activate(scene, sceneRepository.findByCampaignOrderByName(scene.getCampaign()));
        return SceneResponse.from(scene, true);
    }

    private void activate(Scene scene, List<Scene> others) {
        scene.activate(others);
        broadcastScene(scene);
    }

    @Transactional
    public FogResponse updateFog(UUID sceneId, FogRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        requireBody(r);
        valid(() -> {
            if (Boolean.TRUE.equals(r.clear())) scene.clearFog(false);
            if (Boolean.TRUE.equals(r.revealAll())) scene.clearFog(true);
            if (r.enabled() != null) scene.setFogEnabled(r.enabled());
            if (r.reveal() != null) scene.revealCells(r.reveal(), true);
            if (r.hide() != null) scene.revealCells(r.hide(), false);
            return null;
        });
        FogResponse view = new FogResponse(scene.getId(), scene.isFogEnabled(), java.util.Set.copyOf(scene.getRevealedCells()));
        events.publish(scene.getCampaign().getId(), "fog", view);
        return view;
    }

    // ------------------------------------------------------------------ tokens

    @Transactional
    public TokenResponse addToken(UUID sceneId, TokenRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        requireBody(r);
        UUID campaignId = scene.getCampaign().getId();
        Character character = r.characterId() == null ? null : characterRepository.findById(r.characterId())
                .filter(c -> c.getCampaign().getId().equals(campaignId)).orElseThrow(() -> notFound("Character"));
        NPC npc = r.npcId() == null ? null : npcRepository.findById(r.npcId())
                .filter(n -> n.getCampaign().getId().equals(campaignId)).orElseThrow(() -> notFound("NPC"));
        Monster monster = r.monsterId() == null ? null : monsterRepository.findById(r.monsterId())
                .filter(m -> !m.isCustom() || m.getCampaign().getId().equals(campaignId)) // homebrew de outra campanha nao
                .orElseThrow(() -> notFound("Monster"));
        Token token = valid(() -> scene.addToken(r.name(), r.x() == null ? 0 : r.x(), r.y() == null ? 0 : r.y(),
                r.sizeCells(), r.imageUrl(), Boolean.TRUE.equals(r.hidden()), character, npc, monster));
        sceneRepository.save(scene);
        broadcastToken(token, "token_added");
        return TokenResponse.from(token, true);
    }

    /** Mestre move qualquer token; jogador so os dos proprios personagens (e na cena ativa). */
    @Transactional
    public TokenMovedResponse moveToken(UUID tokenId, MoveTokenRequest r, String username) {
        Token token = token(tokenId);
        Scene scene = token.getScene();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        boolean master = masterGuard.canManage(user, scene.getCampaign());
        if (!master) {
            if (!token.controlledBy(user.getUserId())) {
                throw new AccessDeniedException("You can only move your own character's token");
            }
            if (!scene.isActive()) throw new AccessDeniedException("This scene is not active");
        }
        if (r == null) throw badRequest("Position is required");
        double fromX = token.getX();
        double fromY = token.getY();
        if (!master) {
            // jogador nao atravessa parede nem porta fechada (do centro de onde esta ao centro de onde vai)
            double hw = scene.getGridSizePx() * token.width() / 2.0;
            double hh = scene.getGridSizePx() * token.height() / 2.0;
            if (scene.blockedBetween(fromX + hw, fromY + hh, r.x() + hw, r.y() + hh)) {
                throw badRequest("There is a wall in the way");
            }
        }
        valid(() -> { token.moveTo(r.x(), r.y()); return null; });
        TokenMovedResponse moved = new TokenMovedResponse(scene.getId(), token.getId(), token.getX(), token.getY(),
                scene.distanceFeet(fromX, fromY, token.getX(), token.getY()));
        if (token.isHiddenFromPlayers()) events.publishToMaster(scene.getCampaign().getId(), "token_moved", moved);
        else events.publish(scene.getCampaign().getId(), "token_moved", moved);
        return moved;
    }

    @Transactional
    public TokenResponse updateToken(UUID tokenId, TokenRequest r, String username) {
        Token token = token(tokenId);
        masterGuard.requireMaster(username, token.getScene().getCampaign());
        requireBody(r);
        boolean wasHidden = token.isHiddenFromPlayers();
        valid(() -> {
            token.update(r.name() == null ? token.getName() : r.name(),
                    r.sizeCells() == null ? token.getSizeCells() : r.sizeCells(),
                    r.imageUrl() == null ? token.getImageUrl() : r.imageUrl(),
                    r.hidden() == null ? token.isHidden() : r.hidden(),
                    r.rotation() == null ? token.getRotation() : r.rotation());
            token.setHitPoints(r.hpCurrent(), r.hpMax());
            if (r.x() != null && r.y() != null) token.moveTo(r.x(), r.y());
            return null;
        });
        UUID campaignId = token.getScene().getCampaign().getId();
        if (!wasHidden && token.isHiddenFromPlayers()) {
            // pros jogadores o token "some"
            events.publish(campaignId, "token_removed", Map.of("sceneId", token.getScene().getId(), "tokenId", token.getId()),
                    l -> !l.master());
        }
        broadcastToken(token, "token_updated");
        return TokenResponse.from(token, true);
    }

    @Transactional
    public void removeToken(UUID tokenId, String username) {
        Token token = token(tokenId);
        Scene scene = token.getScene();
        masterGuard.requireMaster(username, scene.getCampaign());
        scene.getTokens().remove(token);
        events.publish(scene.getCampaign().getId(), "token_removed", Map.of("sceneId", scene.getId(), "tokenId", tokenId));
    }

    // ------------------------------------------------------------------ iniciativa

    @Transactional
    public InitiativeResponse initiative(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMember(username, campaign);
        return InitiativeResponse.from(tracker(campaign));
    }

    /** Rola 1d20 + bonus pra cada token pedido (ou todos os visiveis da cena ativa). */
    @Transactional
    public InitiativeResponse rollInitiative(UUID campaignId, InitiativeRollRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        List<Token> tokens;
        if (r != null && r.tokenIds() != null && !r.tokenIds().isEmpty()) {
            tokens = r.tokenIds().stream().map(this::token)
                    .filter(t -> t.getScene().getCampaign().getId().equals(campaignId)).toList();
        } else {
            tokens = sceneRepository.findByCampaignOrderByName(campaign).stream().filter(Scene::isActive)
                    .flatMap(s -> s.getTokens().stream()).filter(t -> !t.isHiddenFromPlayers() && t.getLayer() == top.customizr.system.model.TokenLayer.BATTLE).toList();
        }
        for (Token t : tokens) {
            int bonus = t.initiativeBonus();
            int rolled = DiceRoller.roll("1d20").total() + bonus;
            tracker.add(t.getName(), rolled, bonus, t.getId());
        }
        return broadcastInitiative(tracker);
    }

    @Transactional
    public InitiativeResponse addInitiativeEntry(UUID campaignId, InitiativeEntryRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        requireBody(r);
        InitiativeTracker tracker = tracker(campaign);
        valid(() -> tracker.add(r.name(), r.initiative(), r.bonus() == null ? 0 : r.bonus(), null));
        return broadcastInitiative(tracker);
    }

    @Transactional
    public InitiativeResponse removeInitiativeEntry(UUID campaignId, UUID entryId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        valid(() -> { tracker.remove(entryId); return null; });
        return broadcastInitiative(tracker);
    }

    @Transactional
    public InitiativeResponse nextTurn(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        int roundBefore = tracker.getRound();
        valid(() -> { tracker.next(); return null; });
        tickMarkers(campaign, tracker, tracker.getRound() != roundBefore);
        return broadcastInitiative(tracker);
    }

    /** Fim do combate: limpa a ordem. */
    @Transactional
    public void endCombat(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        trackerRepository.findByCampaign(campaign).ifPresent(t -> {
            campaign.setInitiativeTracker(null);
            trackerRepository.delete(t);
        });
        events.publish(campaignId, "initiative_ended", Map.of("campaignId", campaignId));
    }

    private InitiativeTracker tracker(Campaign campaign) {
        return trackerRepository.findByCampaign(campaign)
                .orElseGet(() -> trackerRepository.save(new InitiativeTracker(campaign)));
    }

    private InitiativeResponse broadcastInitiative(InitiativeTracker tracker) {
        InitiativeResponse view = InitiativeResponse.from(tracker);
        events.publish(tracker.getCampaign().getId(), "initiative", view);
        return view;
    }

    // ------------------------------------------------------------------ utilitarios

    // ------------------------------------------------------------------ paredes, portas e visao

    /** Desenha paredes/portas na cena (varias de uma vez). So o mestre. */
    @Transactional
    public List<WallResponse> addWalls(UUID sceneId, WallRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        if (r == null || r.walls() == null || r.walls().isEmpty()) throw badRequest("At least one wall is required");
        valid(() -> {
            for (WallRequest.Segment w : r.walls()) {
                if (w == null) throw new IllegalArgumentException("Empty wall");
                scene.addWall(w.x1(), w.y1(), w.x2(), w.y2(), w.type());
            }
            return null;
        });
        sceneRepository.flush();
        return broadcastWalls(scene);
    }

    @Transactional
    public List<WallResponse> removeWall(UUID sceneId, UUID wallId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        if (!scene.removeWall(wallId)) throw notFound("Wall");
        return broadcastWalls(scene);
    }

    @Transactional
    public List<WallResponse> clearWalls(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        scene.clearWalls();
        return broadcastWalls(scene);
    }

    /** Abre/fecha porta (jogador tambem, se a cena estiver ativa e a porta nao estiver trancada); trancar so o mestre. */
    @Transactional
    public WallResponse updateDoor(UUID wallId, DoorRequest r, String username) {
        Wall wall = wallRepository.findById(wallId).orElseThrow(() -> notFound("Wall"));
        Scene scene = wall.getScene();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        boolean master = masterGuard.canManage(user, scene.getCampaign());
        if (r == null) throw badRequest("Request body is required");
        if (!master) {
            if (r.locked() != null) throw new AccessDeniedException("Only the master locks doors");
            if (!scene.isActive()) throw new AccessDeniedException("This scene is not active");
            if (wall.isLocked()) throw badRequest("The door is locked");
        }
        valid(() -> {
            if (r.locked() != null) wall.setLocked(r.locked());
            if (r.open() != null) {
                if (wall.isLocked() && r.open()) throw new IllegalArgumentException("The door is locked");
                wall.setOpen(r.open());
            }
            return null;
        });
        broadcastWalls(scene);
        return WallResponse.from(wall);
    }

    /** Liga/desliga a visao dinamica da cena. So o mestre. */
    @Transactional
    public SceneResponse setSceneVision(UUID sceneId, VisionRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        if (r == null || r.enabled() == null) throw badRequest("enabled is required");
        scene.setVisionEnabled(r.enabled());
        broadcastScene(scene);
        return SceneResponse.from(scene, true);
    }

    /** Alcance de visao do token, em pes (null = sem limite). So o mestre. */
    @Transactional
    public TokenResponse setTokenVision(UUID tokenId, VisionRequest r, String username) {
        Token token = token(tokenId);
        masterGuard.requireMaster(username, token.getScene().getCampaign());
        if (r == null) throw badRequest("Request body is required");
        valid(() -> { token.setVisionFt(r.visionFt()); return null; });
        broadcastToken(token, "token_updated");
        return TokenResponse.from(token, true);
    }

    // ------------------------------------------------------------------ altura, camada, grupo, marcadores

    /** Altura (dono ou mestre), camada e fichas do token (so o mestre). */
    @Transactional
    public TokenResponse updatePlacement(UUID tokenId, TokenPlacementRequest r, String username) {
        Token token = token(tokenId);
        Scene scene = token.getScene();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        boolean master = masterGuard.canManage(user, scene.getCampaign());
        if (r == null) throw badRequest("Request body is required");
        if (!master && (r.layer() != null || r.memberIds() != null || r.widthCells() != null || r.heightCells() != null)) {
            throw new AccessDeniedException("Only the master changes layers, sizes and who travels in a token");
        }
        if (!master && !token.controlledBy(user.getUserId())) throw new AccessDeniedException("Not your token");
        boolean wasHidden = token.isHiddenFromPlayers();
        valid(() -> {
            if (r.elevationFt() != null) token.setElevationFt(r.elevationFt());
            if (r.layer() != null) token.setLayer(r.layer());
            if (r.widthCells() != null || r.heightCells() != null) {
                double w = r.widthCells() != null ? r.widthCells() : token.width();
                token.resize(w, r.heightCells() != null ? r.heightCells() : (r.widthCells() != null ? w : token.height()));
            }
            if (r.memberIds() != null) {
                List<Character> members = r.memberIds().stream().map(id -> characterRepository.findById(id)
                        .filter(c -> c.getCampaign().getId().equals(scene.getCampaign().getId()))
                        .orElseThrow(() -> new IllegalArgumentException("Character not found in this campaign: " + id))).toList();
                token.replaceMembers(members);
            }
            return null;
        });
        if (!wasHidden && token.isHiddenFromPlayers()) {
            events.publish(scene.getCampaign().getId(), "token_removed", Map.of("sceneId", scene.getId(), "tokenId", token.getId()),
                    l -> !l.master());
        }
        broadcastToken(token, "token_updated");
        return TokenResponse.from(token, master);
    }

    /** Marcadores do token (mestre ou quem controla o token). */
    @Transactional
    public TokenResponse updateMarkers(UUID tokenId, MarkersRequest r, String username) {
        Token token = token(tokenId);
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, token.getScene().getCampaign());
        boolean master = masterGuard.canManage(user, token.getScene().getCampaign());
        if (!master && !token.controlledBy(user.getUserId())) throw new AccessDeniedException("Not your token");
        if (r == null || r.markers() == null) throw badRequest("markers is required");
        valid(() -> {
            token.replaceMarkers(r.markers().stream().map(m -> {
                if (m == null) throw new IllegalArgumentException("Empty marker");
                return new top.customizr.system.model.TokenMarker(m.label(), m.icon(), m.roundsLeft());
            }).toList());
            return null;
        });
        broadcastToken(token, "token_updated");
        return TokenResponse.from(token, master);
    }

    /**
     * Passou um turno: o token da vez desce a contagem dos marcadores dele. Na virada da rodada, descem tambem
     * os tokens das cenas ativas que nao estao na iniciativa.
     */
    private void tickMarkers(Campaign campaign, InitiativeTracker tracker, boolean newRound) {
        var current = tracker.current();
        java.util.Set<UUID> inInitiative = new java.util.HashSet<>();
        tracker.getEntries().forEach(e -> { if (e.getTokenId() != null) inInitiative.add(e.getTokenId()); });
        List<Token> ticking = new ArrayList<>();
        if (current != null && current.getTokenId() != null) tokenRepository.findById(current.getTokenId()).ifPresent(ticking::add);
        if (newRound) {
            sceneRepository.findByCampaignOrderByName(campaign).stream().filter(Scene::isActive)
                    .flatMap(s -> s.getTokens().stream()).filter(t -> !inInitiative.contains(t.getId())).forEach(ticking::add);
        }
        for (Token t : ticking) {
            if (t.getMarkers().isEmpty()) continue;
            t.tickMarkers();
            broadcastToken(t, "token_updated");
        }
    }

    // ------------------------------------------------------------------ pacote de icones da campanha

    /** Icones da campanha (todos da mesa precisam, pra desenhar condicoes e marcadores). */
    @Transactional
    public List<CampaignIconResponse> listIcons(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMember(username, campaign);
        return iconRepository.findByCampaignOrderByLabel(campaign).stream().map(CampaignIconResponse::from).toList();
    }

    /** Poe (ou troca, se a key ja existir) um icone do pacote. So o mestre. */
    @Transactional
    public CampaignIconResponse putIcon(UUID campaignId, CampaignIconRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (r == null || r.imageId() == null || r.key() == null) throw badRequest("key and imageId are required");
        var image = imageAssetRepository.findById(r.imageId()).orElseThrow(() -> notFound("Image"));
        if (!image.getCampaign().getId().equals(campaignId)) throw notFound("Image");
        var icon = valid(() -> iconRepository.findByCampaignAndKey(campaign, r.key())
                .map(existing -> { existing.replace(r.label(), image); return existing; })
                .orElseGet(() -> new top.customizr.system.model.CampaignIcon(campaign, r.key(), r.label(), image)));
        var saved = iconRepository.save(icon);
        events.publish(campaignId, "icons", Map.of("campaignId", campaignId));
        return CampaignIconResponse.from(saved);
    }

    @Transactional
    public void deleteIcon(UUID iconId, String username) {
        var icon = iconRepository.findById(iconId).orElseThrow(() -> notFound("Icon"));
        masterGuard.requireMaster(username, icon.getCampaign());
        UUID campaignId = icon.getCampaign().getId();
        iconRepository.delete(icon);
        events.publish(campaignId, "icons", Map.of("campaignId", campaignId));
    }

    @Transactional
    public void clearIcons(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        iconRepository.deleteAll(iconRepository.findByCampaignOrderByLabel(campaign));
        events.publish(campaignId, "icons", Map.of("campaignId", campaignId));
    }

    // ------------------------------------------------------------------ biblioteca de objetos (mesa, barril...)

    /** Objetos da campanha (so o mestre monta o cenario). */
    @Transactional
    public List<MapObjectResponse> listObjects(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        return mapObjectRepository.findByCampaignOrderByName(campaign).stream().map(MapObjectResponse::from).toList();
    }

    @Transactional
    public MapObjectResponse addObject(UUID campaignId, MapObjectRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        if (r == null || r.imageId() == null) throw badRequest("imageId is required (upload the image with POST /image)");
        var image = imageAssetRepository.findById(r.imageId()).orElseThrow(() -> notFound("Image"));
        var object = valid(() -> new top.customizr.system.model.MapObject(campaign, r.name(), image, r.widthCells(),
                r.heightCells(), r.layer()));
        return MapObjectResponse.from(mapObjectRepository.save(object));
    }

    @Transactional
    public MapObjectResponse updateObject(UUID objectId, MapObjectRequest r, String username) {
        var object = mapObjectRepository.findById(objectId).orElseThrow(() -> notFound("Object"));
        masterGuard.requireMaster(username, object.getCampaign());
        if (r == null) throw badRequest("Request body is required");
        valid(() -> { object.update(r.name() == null ? object.getName() : r.name(),
                r.widthCells() == null ? object.getWidthCells() : r.widthCells(),
                r.heightCells() == null ? object.getHeightCells() : r.heightCells(),
                r.layer() == null ? object.getLayer() : r.layer()); return null; });
        return MapObjectResponse.from(object);
    }

    @Transactional
    public void deleteObject(UUID objectId, String username) {
        var object = mapObjectRepository.findById(objectId).orElseThrow(() -> notFound("Object"));
        masterGuard.requireMaster(username, object.getCampaign());
        mapObjectRepository.delete(object);
    }

    /** Poe o objeto no mapa: vira um token da camada dele, com a imagem e o tamanho. */
    @Transactional
    public TokenResponse placeObject(UUID sceneId, UUID objectId, PlaceObjectRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        var object = mapObjectRepository.findById(objectId).orElseThrow(() -> notFound("Object"));
        if (!object.getCampaign().getId().equals(scene.getCampaign().getId())) throw notFound("Object");
        Token token = valid(() -> {
            Token t = scene.addToken(object.getName(), r == null || r.x() == null ? 0 : r.x(), r == null || r.y() == null ? 0 : r.y(),
                    null, object.getImage().getUrl(), r != null && Boolean.TRUE.equals(r.hidden()), null, null, null);
            t.resize(object.getWidthCells(), object.getHeightCells());
            t.setLayer(object.getLayer());
            if (r != null && r.rotation() != null) t.update(t.getName(), t.getSizeCells(), t.getImageUrl(), t.isHidden(), r.rotation());
            return t;
        });
        sceneRepository.save(scene);
        broadcastToken(token, "token_added");
        return TokenResponse.from(token, true);
    }

    // ------------------------------------------------------------------ areas de efeito

    /** Coloca uma area no mapa (mestre ou jogador, pra magia de area). */
    @Transactional
    public TemplateResponse addTemplate(UUID sceneId, TemplateRequest r, String username) {
        Scene scene = scene(sceneId);
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        boolean master = masterGuard.canManage(user, scene.getCampaign());
        if (!master && !scene.isActive()) throw new AccessDeniedException("This scene is not active");
        if (r == null || r.shape() == null || r.x() == null || r.y() == null || r.sizeFt() == null) {
            throw badRequest("shape, x, y and sizeFt are required");
        }
        var template = valid(() -> scene.addTemplate(r.shape(), r.x(), r.y(), r.angle() == null ? 0 : r.angle(), r.sizeFt(),
                r.widthFt(), r.label(), r.color(), user.getUserId()));
        sceneRepository.flush(); // grava ja (a area pode ser resolvida no mesmo pedido/transacao)
        broadcastTemplates(scene);
        return TemplateResponse.from(template);
    }

    /** Tira a area (quem colocou ou o mestre). */
    @Transactional
    public void removeTemplate(UUID templateId, String username) {
        var template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Area"));
        Scene scene = template.getScene();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        if (!masterGuard.canManage(user, scene.getCampaign()) && !template.getOwnerId().equals(user.getUserId())) {
            throw new AccessDeniedException("Only who placed the area or the master removes it");
        }
        scene.getTemplates().remove(template);
        broadcastTemplates(scene);
    }

    @Transactional
    public void clearTemplates(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        scene.getTemplates().clear();
        broadcastTemplates(scene);
    }

    private void broadcastTemplates(Scene scene) {
        Map<String, Object> payload = Map.of("sceneId", scene.getId(),
                "templates", scene.getTemplates().stream().map(TemplateResponse::from).toList());
        UUID campaignId = scene.getCampaign().getId();
        events.publishToMaster(campaignId, "templates", payload);
        if (scene.isActive()) events.publish(campaignId, "templates", payload, l -> !l.master());
    }

    private List<WallResponse> broadcastWalls(Scene scene) {
        List<WallResponse> walls = scene.getWalls().stream().map(WallResponse::from).toList();
        Map<String, Object> payload = Map.of("sceneId", scene.getId(), "walls", walls);
        UUID campaignId = scene.getCampaign().getId();
        events.publishToMaster(campaignId, "walls", payload);
        if (scene.isActive()) events.publish(campaignId, "walls", payload, l -> !l.master());
        return walls;
    }

    private void broadcastScene(Scene scene) {
        UUID campaignId = scene.getCampaign().getId();
        SceneResponse forMaster = SceneResponse.from(scene, true);
        events.publishToMaster(campaignId, "scene", forMaster);
        if (scene.isActive()) {
            events.publish(campaignId, "scene", SceneResponse.from(scene, false), l -> !l.master());
        }
    }

    /** Avisa a mesa que o token mudou (PV, condicoes...). */
    public void broadcastTokenChange(Token token) {
        broadcastToken(token, "token_updated");
    }

    /** A ficha mudou (PV, condicoes, descanso): os tokens desse personagem sao redesenhados na mesa. */
    @Transactional
    public void characterChanged(Character character) {
        if (character == null) return;
        tokenRepository.findByCharacter(character).forEach(this::broadcastTokenChange);
        tokenRepository.findByMembersContaining(character).forEach(this::broadcastTokenChange);
    }

    private void broadcastToken(Token token, String type) {
        UUID campaignId = token.getScene().getCampaign().getId();
        events.publishToMaster(campaignId, type, TokenResponse.from(token, true));
        if (!token.isHiddenFromPlayers() && token.getScene().isActive()) {
            events.publish(campaignId, type, TokenResponse.from(token, false), l -> !l.master());
        }
    }

    private <T> T valid(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private void requireBody(Object body) {
        if (body == null) throw badRequest("Request body is required");
    }

    private Campaign campaign(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> notFound("Campaign"));
    }

    private Scene scene(UUID id) {
        return sceneRepository.findById(id).orElseThrow(() -> notFound("Scene"));
    }

    private Token token(UUID id) {
        return tokenRepository.findById(id).orElseThrow(() -> notFound("Token"));
    }

    private ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
