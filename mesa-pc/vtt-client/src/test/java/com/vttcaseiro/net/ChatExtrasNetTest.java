package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Idioma no chat (embaralhado pra quem nao conhece), enquete contada no servidor e lojas/pedidos de loja. */
class ChatExtrasNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> polls = new CopyOnWriteArrayList<>();
        volatile List<Shop> shops;
        final List<NetMessage.ShopRequest> requests = new CopyOnWriteArrayList<>();

        @Override public void onPoll(String id, String author, String q, List<String> opts, List<Integer> counts, Integer mine) {
            polls.add(q + "|" + counts + "|" + mine);
        }
        @Override public void onShops(List<Shop> s) { shops = s; }
        @Override public void onShopRequest(NetMessage.ShopRequest r) { requests.add(r); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void languagePollAndShops() throws Exception {
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
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");

            // --- idioma: a Lyra (da Ana) fala Elvish; o Beto nao
            CharacterSheet lyra = new CharacterSheet("Lyra");
            lyra.setOwnerPlayerId("ana");
            lyra.getLanguages().add("Elvish");
            anaC.sendCharacterSync(lyra);
            CharacterSheet kael = new CharacterSheet("Kael");
            kael.setOwnerPlayerId("beto");
            betoC.sendCharacterSync(kael);
            waitFor(() -> gm.sheets.size() >= 2, "fichas chegaram");
            gmC.sendChatLanguage("Mestre", "O tesouro esta na torre.", "Elvish");
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("[Elvish] O tesouro esta na torre.")), "Ana le");
            waitFor(() -> beto.chats.stream().anyMatch(c -> c.startsWith("Mestre: [Elvish]")), "Beto recebe");
            assertFalse(beto.chats.stream().anyMatch(c -> c.contains("tesouro")), "Beto ve embaralhado");

            // --- enquete: o servidor conta, cada um sabe o proprio voto
            gmC.sendPoll("p1", "Por onde?", List.of("Floresta", "Estrada"));
            waitFor(() -> !ana.polls.isEmpty() && !beto.polls.isEmpty(), "enquete chegou");
            anaC.sendPollVote("p1", 1);
            betoC.sendPollVote("p1", 1);
            waitFor(() -> gm.polls.stream().anyMatch(p -> p.equals("Por onde?|[0, 2]|null")), "dois votos na Estrada");
            waitFor(() -> ana.polls.stream().anyMatch(p -> p.endsWith("|1")), "Ana sabe que votou na Estrada");
            anaC.sendPollVote("p1", 0);
            waitFor(() -> gm.polls.stream().anyMatch(p -> p.equals("Por onde?|[1, 1]|null")), "Ana trocou o voto");

            // --- lojas: jogador so recebe as abertas; pedido vai pro mestre com o nome de quem pediu
            Shop open = new Shop("Ferreiro");
            open.setOpen(true);
            Shop closed = new Shop("Secreta");
            gmC.sendShops(List.of(open, closed));
            waitFor(() -> ana.shops != null && ana.shops.size() == 1, "Ana ve so a aberta");
            assertEquals("Ferreiro", ana.shops.get(0).getName());
            NetMessage.ShopRequest r = new NetMessage.ShopRequest();
            r.shopId = open.getId();
            r.item = "Longsword";
            r.sheetId = lyra.getId();
            anaC.sendShopRequest(r);
            waitFor(() -> !gm.requests.isEmpty(), "pedido chegou no mestre");
            assertEquals("Ana", gm.requests.get(0).player);
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
