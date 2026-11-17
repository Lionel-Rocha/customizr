package top.customizr.system.dto;

import top.customizr.system.model.Token;
import java.util.UUID;

public record TokenResponse(UUID id, UUID sceneId, String name, double x, double y, int sizeCells, String imageUrl, boolean hidden,
                            int rotation, UUID characterId, UUID ownerId, UUID npcId, UUID monsterId,
                            Integer hpCurrent, Integer hpMax, java.util.Set<top.customizr.system.model.Condition> conditions,
                            Integer armorClass) {

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
                java.util.Set.copyOf(t.activeConditions()), showHp ? t.armorClass() : null);
    }
}
