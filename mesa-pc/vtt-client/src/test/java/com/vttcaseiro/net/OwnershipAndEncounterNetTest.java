package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.model.Encounter;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipAndEncounterNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Encounter> encounters = new CopyOnWriteArrayList<>();
        volatile List<NetMessage.PlayerInfo> players = List.of();

        @Override public void onEncounterSync(Encounter e) { encounters.add(e); }
        @Override public void onPlayers(List<NetMessage.PlayerInfo> p) { players = p; }
    }

    private static void waitFor(BooleanSupplier cond, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timeout esperando: " + what);
    }

    @Test
    void clienteDeProtocoloAntigoERecusadoComAviso() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        try (Socket s = new Socket("localhost", server.getPort())) {
            NetMessage hello = NetMessage.hello("Velho", "v", false);
            hello.protocolVersion = 1;
            new PrintWriter(new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)
                    .println(NetCodec.encode(hello));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            assertEquals("challenge", NetCodec.decode(line).type, "primeiro vem o desafio da senha");
            line = in.readLine();
            assertNotNull(line);
            assertTrue(NetCodec.decode(line).text.contains("desatualizado"), line);
            assertNull(in.readLine(), "servidor fechou a conexao depois do aviso");
        } finally {
            server.stop();
        }
    }

    @Test
    void encontroDonoGravadoNaFichaEListaDeJogadores() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec beto = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.players.size() == 3, "lista de jogadores com id");
            assertTrue(gm.players.stream().anyMatch(p -> "ana".equals(p.playerId) && "Ana".equals(p.name) && !p.gm));

            // Encontro: so o mestre manda; os outros recebem.
            Encounter e = new Encounter();
            e.setActive(true);
            e.setRound(3);
            gmC.sendEncounter(e);
            waitFor(() -> ana.encounters.size() == 1 && beto.encounters.size() == 1, "encontro chegou");
            assertEquals(3, ana.encounters.get(0).getRound());
            betoC.sendEncounter(new Encounter());
            waitFor(() -> beto.chats.stream().anyMatch(c -> c.contains("Só o mestre")), "recusa");
            assertEquals(1, ana.encounters.size());

            // Ficha com dono gravado = Ana: mesmo se o Beto sincronizar primeiro, e recusado.
            CharacterSheet sheet = new CharacterSheet("Elora");
            sheet.setOwnerPlayerId("ana");
            betoC.sendCharacterSync(sheet);
            waitFor(() -> beto.chats.stream().anyMatch(c -> c.contains("pertence a outro jogador")), "recusa ficha");
            anaC.sendCharacterSync(sheet);
            waitFor(() -> gm.sheets.size() == 1, "ficha da Ana aceita");

            // Mestre passa o token pro Beto (ownerId muda pelo update_token do mestre).
            TokenModel t = new TokenModel("Lobo", null);
            gmC.sendAddToken(t);
            waitFor(() -> beto.tokensAdded.size() == 1, "token");
            TokenModel given = NetCodec.deepCopy(t, TokenModel.class);
            given.setOwnerId("beto");
            gmC.sendUpdateToken(given);
            waitFor(() -> beto.tokenUpdates.stream().anyMatch(u -> "beto".equals(u.getOwnerId())), "controle passado");
            TokenModel moved = NetCodec.deepCopy(t, TokenModel.class);
            moved.setCurrentHp(3);
            betoC.sendUpdateToken(moved);
            waitFor(() -> gm.tokenUpdates.stream().anyMatch(u -> Integer.valueOf(3).equals(u.getCurrentHp())), "Beto agora controla");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
