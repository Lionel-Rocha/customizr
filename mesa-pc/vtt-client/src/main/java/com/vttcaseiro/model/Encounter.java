package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O combate em andamento numa cena: rodada, de quem e o turno e os efeitos
 * com duracao ("Bencao: 10 rodadas"). Vive dentro da {@link SceneModel}
 * (entao e salvo com a cena) e viaja pela rede inteiro em "encounter_sync".
 * A ORDEM nao e guardada: e sempre a iniciativa atual dos tokens (ver
 * EncounterService.order), entao editar uma iniciativa ja reordena.
 */
public class Encounter {

    /** Efeito que acaba sozinho depois de N rodadas. */
    public static class TimedEffect {
        private String id = UUID.randomUUID().toString();
        private String tokenId;
        private String name;
        private int remainingRounds;

        public TimedEffect() {
        }

        public TimedEffect(String tokenId, String name, int rounds) {
            this.tokenId = tokenId;
            this.name = name;
            this.remainingRounds = rounds;
        }

        public String getId() { return id; }
        public String getTokenId() { return tokenId; }
        public String getName() { return name; }
        public int getRemainingRounds() { return remainingRounds; }
        public void setRemainingRounds(int r) { this.remainingRounds = r; }
    }

    private boolean active;
    private int round;
    private String currentTokenId;
    private List<TimedEffect> timedEffects = new ArrayList<>();

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public String getCurrentTokenId() { return currentTokenId; }
    public void setCurrentTokenId(String id) { this.currentTokenId = id; }

    public List<TimedEffect> getTimedEffects() {
        if (timedEffects == null) timedEffects = new ArrayList<>();
        return timedEffects;
    }
}
