package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.party.Party;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Servidor de verdade + clientes de verdade, via loopback. */
class GameServerSecurityTest {

    /** Guarda tudo que chega num cliente. */
    static class Recorder implements GameClient.Listener {
        final List<String> chats = new CopyOnWriteArrayList<>();
        final List<String> rolls = new CopyOnWriteArrayList<>();
        final List<TokenModel> tokenUpdates = new CopyOnWriteArrayList<>();
        final List<TokenModel> tokensAdded = new CopyOnWriteArrayList<>();
        final List<CharacterSheet> sheets = new CopyOnWriteArrayList<>();
        volatile SceneModel scene;

        @Override public void onSceneSync(SceneModel s) { scene = s; }
        @Override public void onTokenMoved(String id, double x, double y) { }
        @Override public void onTokenAdded(TokenModel t) { tokensAdded.add(t); }
        @Override public void onTokenRemoved(String id) { }
        @Override public void onRollResult(String p, String d, int t) { }
        @Override public void onRollResultLabeled(String p, String label, String d, int t, Integer nat) {
            rolls.add(p + "|" + label + "|" + nat);
        }
        @Override public void onTokenUpdated(TokenModel t) { tokenUpdates.add(t); }
        @Override public void onChat(String p, String text) { chats.add(p + ": " + text); }
        @Override public void onCharacterSync(CharacterSheet c) { sheets.add(c); }
        @Override public void onMobSync(MobSheet m) { }
        @Override public void onPartySync(Party p) { }
        @Override public void onDisconnected(String r) { }
        @Override public void onPresenceUpdate(List<String> names) { }
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
    void protocoloProtegido() throws Exception {
        GameServer server = new GameServer(0, new SceneModel(), null, "senha", "mestre");
        server.start();
        int port = server.getPort();
        Recorder gm = new Recorder();
        Recorder player = new Recorder();
        Recorder intruder = new Recorder();
        GameClient gmClient = new GameClient(gm, null, "gm-id");
        GameClient playerClient = new GameClient(player, null, "player-id");
        GameClient otherClient = new GameClient(intruder, null, "other-id");
        try {
            gmClient.connect("localhost", port, "Mestre", true, "senha", "mestre");
            playerClient.connect("localhost", port, "Ana", false, "senha", null);
            otherClient.connect("localhost", port, "Beto", false, "senha", null);
            waitFor(() -> gm.scene != null && player.scene != null && intruder.scene != null, "3 conectados");

            // 1) Conexao crua SEM hello tentando adicionar token: ignorada.
            try (Socket raw = new Socket("localhost", port)) {
                PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(raw.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println(NetCodec.encode(NetMessage.addToken(new TokenModel("Invasor", null))));
                Thread.sleep(300);
            }
            assertTrue(gm.tokensAdded.isEmpty(), "token de conexao sem hello nao pode entrar");

            // 2) Nome falsificado no chat: o servidor troca pelo nome da sessao.
            gmClient.sendChat("Mestre", "oi"); // referencia de ordem
            playerClient.sendChat("Mestre", "sou o mestre, confia");
            waitFor(() -> gm.chats.stream().anyMatch(c -> c.contains("confia")), "chat da Ana");
            assertTrue(gm.chats.stream().anyMatch(c -> c.equals("Ana: sou o mestre, confia")), gm.chats.toString());

            // 3) Token do mestre: jogador nao muda PV, mestre muda e todos recebem.
            TokenModel goblin = new TokenModel("Goblin", null);
            goblin.setMaxHp(7);
            goblin.setCurrentHp(7);
            gmClient.sendAddToken(goblin);
            waitFor(() -> player.tokensAdded.size() == 1, "goblin chegou");
            TokenModel hacked = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(goblin), TokenModel.class);
            hacked.setCurrentHp(0);
            playerClient.sendUpdateToken(hacked);
            waitFor(() -> player.chats.stream().anyMatch(c -> c.contains("não controla")), "recusa");
            assertTrue(gm.tokenUpdates.isEmpty());
            TokenModel hurt = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(goblin), TokenModel.class);
            hurt.setCurrentHp(3);
            hurt.getConditions().add("PRONE");
            gmClient.sendUpdateToken(hurt);
            waitFor(() -> !player.tokenUpdates.isEmpty(), "update do mestre");
            assertEquals(3, player.tokenUpdates.get(0).getCurrentHp());
            assertTrue(player.tokenUpdates.get(0).getConditions().contains("PRONE"));

            // 4) Ficha: primeiro a sincronizar vira dono; outro jogador nao sobrescreve;
            //    quem mandou nao recebe eco.
            CharacterSheet ana = new CharacterSheet("Elora");
            playerClient.sendCharacterSync(ana);
            waitFor(() -> gm.sheets.size() == 1 && intruder.sheets.size() == 1, "ficha da Ana");
            Thread.sleep(200);
            assertTrue(player.sheets.isEmpty(), "sem eco pra quem mandou");
            CharacterSheet forged = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(ana), CharacterSheet.class);
            forged.setName("Roubada");
            otherClient.sendCharacterSync(forged);
            waitFor(() -> intruder.chats.stream().anyMatch(c -> c.contains("pertence a outro jogador")), "recusa ficha");
            assertEquals(1, gm.sheets.size());

            // 4b) Dono da ficha mexe no token da ficha dele mesmo que o mestre o tenha colocado.
            TokenModel anaToken = new TokenModel("Elora", null);
            anaToken.setCharacterSheetId(ana.getId());
            gmClient.sendAddToken(anaToken);
            waitFor(() -> player.tokensAdded.size() == 2, "token da Ana");
            TokenModel anaHurt = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(anaToken), TokenModel.class);
            anaHurt.setCurrentHp(5);
            playerClient.sendUpdateToken(anaHurt);
            waitFor(() -> gm.tokenUpdates.stream().anyMatch(t -> t.getId().equals(anaToken.getId())), "Ana mexe no proprio token");

            // 5) Rolagem com rotulo e d20 natural.
            playerClient.sendLabeledRoll("Mestre", "Elora - Furtividade", "1d20+5", "normal", 0);
            waitFor(() -> !gm.rolls.isEmpty(), "rolagem");
            String roll = gm.rolls.get(0);
            assertTrue(roll.startsWith("Ana|Elora - Furtividade|"), roll);
            assertFalse(roll.endsWith("|null"), "d20 natural presente: " + roll);
        } finally {
            gmClient.disconnect();
            playerClient.disconnect();
            otherClient.disconnect();
            server.stop();
        }
    }

    @Test
    void servidorNaoCompartilhaACenaDaTelaDeQuemHospeda() throws Exception {
        SceneModel hostScene = new SceneModel();
        GameServer server = new GameServer(0, hostScene);
        server.start();
        Recorder gm = new Recorder();
        GameClient gmClient = new GameClient(gm, null, "gm");
        try {
            gmClient.connect("localhost", server.getPort(), "Mestre", true);
            waitFor(() -> gm.scene != null, "conectado");
            gmClient.sendAddToken(new TokenModel("T", null));
            waitFor(() -> gm.tokensAdded.size() == 1, "token");
            assertTrue(hostScene.getTokens().isEmpty(), "o servidor mexe na copia dele, nao na cena da tela");
        } finally {
            gmClient.disconnect();
            server.stop();
        }
    }
}
