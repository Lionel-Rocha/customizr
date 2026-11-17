package top.customizr.system.dto;

import top.customizr.system.model.GridType;
import top.customizr.system.model.InitiativeEntry;
import top.customizr.system.model.InitiativeTracker;
import top.customizr.system.model.Scene;
import top.customizr.system.model.Token;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pedidos e respostas da mesa ao vivo. */
public final class LiveDtos {

    private LiveDtos() {}

    public record SceneRequest(String name, String mapUrl, GridType gridType, Integer gridSizePx, Integer feetPerCell,
                               Integer widthPx, Integer heightPx) {}

    /** enabled liga/desliga; reveal/hide = casas [coluna, linha]; clear = cobre tudo; revealAll = tira a neblina. */
    public record FogRequest(Boolean enabled, List<int[]> reveal, List<int[]> hide, Boolean clear, Boolean revealAll) {}

    public record TokenRequest(String name, Double x, Double y, Integer sizeCells, String imageUrl, Boolean hidden,
                               UUID characterId, UUID npcId, UUID monsterId, Integer rotation,
                               Integer hpCurrent, Integer hpMax) {}

    public record MoveRequest(double x, double y) {}

    public record InitiativeRollRequest(List<UUID> tokenIds) {}

    public record InitiativeEntryRequest(String name, int initiative, Integer bonus) {}

    public record TokenView(UUID id, String name, double x, double y, int sizeCells, String imageUrl, boolean hidden,
                            int rotation, UUID characterId, UUID ownerId, UUID npcId, UUID monsterId,
                            Integer hpCurrent, Integer hpMax) {
        /** PV do personagem vem da ficha; dos outros, do token (jogador nao ve PV de monstro). */
        public static TokenView from(Token t, boolean master) {
            Integer hp = t.getCharacter() != null ? t.getCharacter().getSheet().getHpCurrent() : t.getHpCurrent();
            Integer max = t.getCharacter() != null ? t.getCharacter().getSheet().getHpMax() : t.getHpMax();
            boolean showHp = master || t.getCharacter() != null;
            return new TokenView(t.getId(), t.getName(), t.getX(), t.getY(), t.getSizeCells(), t.getImageUrl(),
                    t.isHidden(), t.getRotation(),
                    t.getCharacter() == null ? null : t.getCharacter().getCharacterId(),
                    t.getCharacter() == null ? null : t.getCharacter().getOwner().getUserId(),
                    master && t.getNpc() != null ? t.getNpc().getId() : null,
                    master && t.getMonster() != null ? t.getMonster().getId() : null,
                    showHp ? hp : null, showHp ? max : null);
        }
    }

    public record SceneView(UUID id, String name, String mapUrl, GridType gridType, int gridSizePx, int feetPerCell,
                            Integer widthPx, Integer heightPx, boolean fogEnabled, boolean active,
                            Set<String> revealedCells, List<TokenView> tokens) {
        /** Jogador nao recebe tokens ocultos. */
        public static SceneView from(Scene s, boolean master) {
            return new SceneView(s.getId(), s.getName(), s.getMapUrl(), s.getGridType(), s.getGridSizePx(),
                    s.getFeetPerCell(), s.getWidthPx(), s.getHeightPx(), s.isFogEnabled(), s.isActive(),
                    Set.copyOf(s.getRevealedCells()),
                    s.getTokens().stream().filter(t -> master || !t.isHidden()).map(t -> TokenView.from(t, master)).toList());
        }
    }

    public record TokenMoved(UUID sceneId, UUID tokenId, double x, double y, Integer distanceFeet) {}

    public record FogView(UUID sceneId, boolean fogEnabled, Set<String> revealedCells) {}

    public record InitiativeView(int round, int turnIndex, UUID currentEntryId, List<EntryView> entries) {
        public static InitiativeView from(InitiativeTracker t) {
            return new InitiativeView(t.getRound(), t.getTurnIndex(), t.current() == null ? null : t.current().getId(),
                    t.getEntries().stream().map(EntryView::from).toList());
        }
    }

    public record EntryView(UUID id, String name, int initiative, int bonus, UUID tokenId) {
        static EntryView from(InitiativeEntry e) {
            return new EntryView(e.getId(), e.getName(), e.getInitiative(), e.getBonus(), e.getTokenId());
        }
    }
}
