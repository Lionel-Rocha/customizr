package top.customizr.system.dto;

import top.customizr.system.model.Token;
import java.util.UUID;

public record TokenResponse(UUID id, String name, double x, double y, int sizeCells, String imageUrl, boolean hidden,
                            int rotation, UUID characterId, UUID ownerId, UUID npcId, UUID monsterId,
                            Integer hpCurrent, Integer hpMax) {

    /** PV do personagem vem da ficha; dos outros, do token (jogador nao ve PV de monstro/NPC). */
    public static TokenResponse from(Token t, boolean master) {
        Integer hp = t.getCharacter() != null ? t.getCharacter().getSheet().getHpCurrent() : t.getHpCurrent();
        Integer max = t.getCharacter() != null ? t.getCharacter().getSheet().getHpMax() : t.getHpMax();
        boolean showHp = master || t.getCharacter() != null;
        return new TokenResponse(t.getId(), t.getName(), t.getX(), t.getY(), t.getSizeCells(), t.getImageUrl(),
                t.isHidden(), t.getRotation(),
                t.getCharacter() == null ? null : t.getCharacter().getCharacterId(),
                t.getCharacter() == null ? null : t.getCharacter().getOwner().getUserId(),
                master && t.getNpc() != null ? t.getNpc().getId() : null,
                master && t.getMonster() != null ? t.getMonster().getId() : null,
                showHp ? hp : null, showHp ? max : null);
    }
}
