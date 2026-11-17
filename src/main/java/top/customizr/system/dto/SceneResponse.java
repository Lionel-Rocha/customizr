package top.customizr.system.dto;

import top.customizr.system.model.GridType;
import top.customizr.system.model.Scene;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SceneResponse(UUID id, String name, String mapUrl, GridType gridType, int gridSizePx, int feetPerCell,
                            Integer widthPx, Integer heightPx, boolean fogEnabled, boolean active,
                            Set<String> revealedCells, List<TokenResponse> tokens, boolean visionEnabled,
                            List<WallResponse> walls) {

    /** Jogador nao recebe tokens ocultos. */
    public static SceneResponse from(Scene s, boolean master) {
        return new SceneResponse(s.getId(), s.getName(), s.getMapUrl(), s.getGridType(), s.getGridSizePx(),
                s.getFeetPerCell(), s.getWidthPx(), s.getHeightPx(), s.isFogEnabled(), s.isActive(),
                Set.copyOf(s.getRevealedCells()),
                s.getTokens().stream().filter(t -> master || !t.isHidden()).map(t -> TokenResponse.from(t, master)).toList(),
                s.isVisionEnabled(), s.getWalls().stream().map(WallResponse::from).toList());
    }
}
