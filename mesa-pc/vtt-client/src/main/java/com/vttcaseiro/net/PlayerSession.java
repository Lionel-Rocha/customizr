package com.vttcaseiro.net;

/**
 * Quem e um jogador numa conexao: o id estavel (gerado uma vez por
 * conexao, usado pra saber quem e dono de qual token -- nunca o nome,
 * que pode repetir ou mudar), o nome que aparece pros outros, e se e o
 * mestre. E um "record" (imutavel) porque a identidade de uma sessao nao
 * muda no meio da conexao -- se o jogador quiser outro nome, reconecta.
 * <p>
 * Antes desses tres dados viverem juntos, cada lado (ClientHandler no
 * servidor, GameClient no cliente) guardava os tres campos soltos e
 * repetia a mesma logica de "quem controla o que" em mais de um lugar.
 * Agora e uma classe so, usada nos dois lados.
 */
public record PlayerSession(String playerId, String playerName, boolean gm, boolean spectator) {

    /** Mestre ou jogador (o espectador e o outro construtor). */
    public PlayerSession(String playerId, String playerName, boolean gm) {
        this(playerId, playerName, gm, false);
    }

    /** O mestre controla qualquer token. Um jogador comum so controla o
     * que e dele (dono = este playerId). Um token sem dono nenhum
     * (tokenOwnerId == null) so o mestre controla. Espectador nao controla nada. */
    public boolean owns(String tokenOwnerId) {
        if (spectator) return false;
        return gm || (playerId != null && playerId.equals(tokenOwnerId));
    }

    /** Texto pronto pra mostrar numa lista de "quem esta conectado". */
    public String displayName() {
        return gm ? playerName + " (mestre)" : spectator ? playerName + " (espectador)" : playerName;
    }
}
