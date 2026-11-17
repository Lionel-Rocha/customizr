package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.customizr.system.dto.LiveDtos;
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

    public LiveTableService(SceneRepository sceneRepository, TokenRepository tokenRepository,
                            InitiativeTrackerRepository trackerRepository, CampaignRepository campaignRepository,
                            CharacterRepository characterRepository, NpcRepository npcRepository,
                            MonsterRepository monsterRepository, MasterGuard masterGuard, LiveEventService events) {
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
    public LiveDtos.SceneView createScene(UUID campaignId, LiveDtos.SceneRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        requireBody(r);
        Scene scene = valid(() -> new Scene(campaign, r.name(), r.mapUrl(), r.gridType(), r.gridSizePx(),
                r.feetPerCell(), r.widthPx(), r.heightPx()));
        List<Scene> existing = sceneRepository.findByCampaignOrderByName(campaign);
        scene = sceneRepository.save(scene);
        if (existing.isEmpty()) activate(scene, existing);
        return LiveDtos.SceneView.from(scene, true);
    }

    /** Mestre: todas as cenas; jogador: so a ativa. */
    @Transactional
    public List<LiveDtos.SceneView> listScenes(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        boolean master = masterGuard.requireMember(username, campaign);
        return sceneRepository.findByCampaignOrderByName(campaign).stream()
                .filter(s -> master || s.isActive())
                .map(s -> LiveDtos.SceneView.from(s, master)).toList();
    }

    @Transactional
    public LiveDtos.SceneView readScene(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        boolean master = masterGuard.requireMember(username, scene.getCampaign());
        if (!master && !scene.isActive()) throw notFound("Scene");
        return LiveDtos.SceneView.from(scene, master);
    }

    @Transactional
    public LiveDtos.SceneView updateScene(UUID sceneId, LiveDtos.SceneRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        requireBody(r);
        valid(() -> { scene.update(r.name(), r.mapUrl(), r.gridType(), r.gridSizePx(), r.feetPerCell(), r.widthPx(), r.heightPx()); return null; });
        broadcastScene(scene);
        return LiveDtos.SceneView.from(scene, true);
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
    public LiveDtos.SceneView activateScene(UUID sceneId, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        activate(scene, sceneRepository.findByCampaignOrderByName(scene.getCampaign()));
        return LiveDtos.SceneView.from(scene, true);
    }

    private void activate(Scene scene, List<Scene> others) {
        scene.activate(others);
        broadcastScene(scene);
    }

    @Transactional
    public LiveDtos.FogView updateFog(UUID sceneId, LiveDtos.FogRequest r, String username) {
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
        LiveDtos.FogView view = new LiveDtos.FogView(scene.getId(), scene.isFogEnabled(), java.util.Set.copyOf(scene.getRevealedCells()));
        events.publish(scene.getCampaign().getId(), "fog", view);
        return view;
    }

    // ------------------------------------------------------------------ tokens

    @Transactional
    public LiveDtos.TokenView addToken(UUID sceneId, LiveDtos.TokenRequest r, String username) {
        Scene scene = scene(sceneId);
        masterGuard.requireMaster(username, scene.getCampaign());
        requireBody(r);
        UUID campaignId = scene.getCampaign().getId();
        Character character = r.characterId() == null ? null : characterRepository.findById(r.characterId())
                .filter(c -> c.getCampaign().getId().equals(campaignId)).orElseThrow(() -> notFound("Character"));
        NPC npc = r.npcId() == null ? null : npcRepository.findById(r.npcId())
                .filter(n -> n.getCampaign().getId().equals(campaignId)).orElseThrow(() -> notFound("NPC"));
        Monster monster = r.monsterId() == null ? null : monsterRepository.findById(r.monsterId())
                .orElseThrow(() -> notFound("Monster"));
        Token token = valid(() -> scene.addToken(r.name(), r.x() == null ? 0 : r.x(), r.y() == null ? 0 : r.y(),
                r.sizeCells(), r.imageUrl(), Boolean.TRUE.equals(r.hidden()), character, npc, monster));
        sceneRepository.save(scene);
        broadcastToken(token, "token_added");
        return LiveDtos.TokenView.from(token, true);
    }

    /** Mestre move qualquer token; jogador so os dos proprios personagens (e na cena ativa). */
    @Transactional
    public LiveDtos.TokenMoved moveToken(UUID tokenId, LiveDtos.MoveRequest r, String username) {
        Token token = token(tokenId);
        Scene scene = token.getScene();
        User user = masterGuard.user(username);
        masterGuard.assertMember(user, scene.getCampaign());
        boolean master = masterGuard.canManage(user, scene.getCampaign());
        if (!master) {
            if (token.getCharacter() == null || !token.getCharacter().getOwner().getUserId().equals(user.getUserId())) {
                throw new AccessDeniedException("You can only move your own character's token");
            }
            if (!scene.isActive()) throw new AccessDeniedException("This scene is not active");
        }
        if (r == null) throw badRequest("Position is required");
        double fromX = token.getX();
        double fromY = token.getY();
        valid(() -> { token.moveTo(r.x(), r.y()); return null; });
        LiveDtos.TokenMoved moved = new LiveDtos.TokenMoved(scene.getId(), token.getId(), token.getX(), token.getY(),
                scene.distanceFeet(fromX, fromY, token.getX(), token.getY()));
        if (token.isHidden()) events.publishToMaster(scene.getCampaign().getId(), "token_moved", moved);
        else events.publish(scene.getCampaign().getId(), "token_moved", moved);
        return moved;
    }

    @Transactional
    public LiveDtos.TokenView updateToken(UUID tokenId, LiveDtos.TokenRequest r, String username) {
        Token token = token(tokenId);
        masterGuard.requireMaster(username, token.getScene().getCampaign());
        requireBody(r);
        boolean wasHidden = token.isHidden();
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
        if (!wasHidden && token.isHidden()) {
            // pros jogadores o token "some"
            events.publish(campaignId, "token_removed", Map.of("sceneId", token.getScene().getId(), "tokenId", token.getId()),
                    l -> !l.master());
        }
        broadcastToken(token, "token_updated");
        return LiveDtos.TokenView.from(token, true);
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
    public LiveDtos.InitiativeView initiative(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMember(username, campaign);
        return LiveDtos.InitiativeView.from(tracker(campaign));
    }

    /** Rola 1d20 + bonus pra cada token pedido (ou todos os visiveis da cena ativa). */
    @Transactional
    public LiveDtos.InitiativeView rollInitiative(UUID campaignId, LiveDtos.InitiativeRollRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        List<Token> tokens;
        if (r != null && r.tokenIds() != null && !r.tokenIds().isEmpty()) {
            tokens = r.tokenIds().stream().map(this::token)
                    .filter(t -> t.getScene().getCampaign().getId().equals(campaignId)).toList();
        } else {
            tokens = sceneRepository.findByCampaignOrderByName(campaign).stream().filter(Scene::isActive)
                    .flatMap(s -> s.getTokens().stream()).filter(t -> !t.isHidden()).toList();
        }
        for (Token t : tokens) {
            int bonus = t.initiativeBonus();
            int rolled = DiceRoller.roll("1d20").total() + bonus;
            tracker.add(t.getName(), rolled, bonus, t.getId());
        }
        return broadcastInitiative(tracker);
    }

    @Transactional
    public LiveDtos.InitiativeView addInitiativeEntry(UUID campaignId, LiveDtos.InitiativeEntryRequest r, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        requireBody(r);
        InitiativeTracker tracker = tracker(campaign);
        valid(() -> tracker.add(r.name(), r.initiative(), r.bonus() == null ? 0 : r.bonus(), null));
        return broadcastInitiative(tracker);
    }

    @Transactional
    public LiveDtos.InitiativeView removeInitiativeEntry(UUID campaignId, UUID entryId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        valid(() -> { tracker.remove(entryId); return null; });
        return broadcastInitiative(tracker);
    }

    @Transactional
    public LiveDtos.InitiativeView nextTurn(UUID campaignId, String username) {
        Campaign campaign = campaign(campaignId);
        masterGuard.requireMaster(username, campaign);
        InitiativeTracker tracker = tracker(campaign);
        valid(() -> { tracker.next(); return null; });
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

    private LiveDtos.InitiativeView broadcastInitiative(InitiativeTracker tracker) {
        LiveDtos.InitiativeView view = LiveDtos.InitiativeView.from(tracker);
        events.publish(tracker.getCampaign().getId(), "initiative", view);
        return view;
    }

    // ------------------------------------------------------------------ utilitarios

    private void broadcastScene(Scene scene) {
        UUID campaignId = scene.getCampaign().getId();
        LiveDtos.SceneView forMaster = LiveDtos.SceneView.from(scene, true);
        events.publishToMaster(campaignId, "scene", forMaster);
        if (scene.isActive()) {
            events.publish(campaignId, "scene", LiveDtos.SceneView.from(scene, false), l -> !l.master());
        }
    }

    private void broadcastToken(Token token, String type) {
        UUID campaignId = token.getScene().getCampaign().getId();
        events.publishToMaster(campaignId, type, LiveDtos.TokenView.from(token, true));
        if (!token.isHidden() && token.getScene().isActive()) {
            events.publish(campaignId, type, LiveDtos.TokenView.from(token, false), l -> !l.master());
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
