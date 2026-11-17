package top.customizr.system.dto;

import top.customizr.system.model.Token;
import java.util.UUID;

public record TokenResponse(UUID id, UUID sceneId, String name, double x, double y, int sizeCells, String imageUrl, boolean hidden,
                            int rotation, UUID characterId, UUID ownerId, UUID npcId, UUID monsterId,
                            Integer hpCurrent, Integer hpMax, java.util.Set<top.customizr.system.model.Condition> conditions,
                            Integer armorClass, Integer visionFt, top.customizr.system.model.TokenLayer layer,
                            Integer elevationFt, java.util.List<Marker> markers, java.util.List<Member> members,
                            double widthCells, double heightCells, Integer exhaustion) {

    public record Marker(String label, String icon, Integer roundsLeft) {}

    /** Ficha que viaja no token (caravana): PV so pro mestre e pro dono da ficha... aqui vai pra todos da mesa,
     * como o PV dos personagens nos tokens. */
    public record Member(UUID characterId, String name, UUID ownerId, Integer hpCurrent, Integer hpMax) {}


    /**
     * PV do personagem vem da ficha; dos outros, do token (jogador nao ve PV de monstro/NPC).
     * Condicoes todo mundo ve (estao no mapa); CA de monstro/NPC so o mestre.
     */
    public static TokenResponse from(Token t, boolean master) {
        Integer hp = t.getCharacter() != null ? t.getCharacter().getSheet().getHpCurrent() : t.getHpCurrent();
        Integer max = t.getCharacter() != null ? t.getCharacter().getSheet().getHpMax() : t.getHpMax();
        boolean showHp = master || t.getCharacter() != null;
        return new TokenResponse(t.getId(), t.getScene().getId(), t.getName(), t.getX(), t.getY(), t.getSizeCells(), t.getImageUrl(),
                t.isHidden(), t.getRotation(),
                t.getCharacter() == null ? null : t.getCharacter().getCharacterId(),
                t.getCharacter() == null ? null : t.getCharacter().getOwner().getUserId(),
                master && t.getNpc() != null ? t.getNpc().getId() : null,
                master && t.getMonster() != null ? t.getMonster().getId() : null,
                showHp ? hp : null, showHp ? max : null,
                java.util.Set.copyOf(t.activeConditions()), showHp ? t.armorClass() : null, t.getVisionFt(), t.getLayer(),
                t.getElevationFt(),
                t.getMarkers().stream().map(m -> new Marker(m.getLabel(), m.getIcon(), m.getRoundsLeft())).toList(),
                t.getMembers().stream().map(c -> new Member(c.getCharacterId(), c.getName(), c.getOwner().getUserId(),
                        c.getSheet().getHpCurrent(), c.getSheet().getHpMax())).toList(),
                t.width(), t.height(),
                t.getCharacter() != null && t.getCharacter().getSheet().getExhaustion() > 0 ? t.getCharacter().getSheet().getExhaustion() : null);
    }
}
