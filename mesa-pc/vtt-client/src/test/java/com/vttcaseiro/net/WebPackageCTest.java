package com.vttcaseiro.net;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Resource;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.GmRequest;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Navegador com as regras do programa: alvos, magias, recursos e o mestre pelo navegador. */
class WebPackageCTest {

    static class Gm extends Priority3NetTest.Rec {
        final List<GmRequest> requests = new CopyOnWriteArrayList<>();

        @Override public void onGmRequest(String player, GmRequest r) { requests.add(r); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 6000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    private static String challengeOf(List<String> got) {
        return com.google.gson.JsonParser.parseString(got.stream().filter(s -> s.contains("\"challenge\"")).findFirst()
                .orElseThrow()).getAsJsonObject().get("challenge").getAsString();
    }

    @Test
    void jogadorEMestrePeloNavegadorComAsRegrasDoPrograma() throws Exception {
        SceneModel scene = new SceneModel();
        TokenModel elora = new TokenModel("Elora", null);
        elora.setCharacterSheetId("ficha-elora");
        MobSheet gobSheet = new MobSheet("Goblin");
        gobSheet.setArmorClass(15);
        TokenModel goblin = new TokenModel("Goblin", null);
        goblin.setMobSheetId(gobSheet.getId());
        goblin.setMaxHp(20);
        goblin.setCurrentHp(20);
        goblin.setX(300);
        scene.addToken(elora);
        scene.addToken(goblin);
        GameServer server = new GameServer(0, scene, null, null, "mestre");
        server.start();
        WebGateway gateway = new WebGateway(0, server.getPort(), null);
        gateway.start();
        Gm gm = new Gm();
        GameClient gmC = new GameClient(gm, null, "gm");
        WebGatewayTest.WsClient ana = null;
        WebGatewayTest.WsClient webGm = null;
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true, null, "mestre");
            waitFor(() -> gm.scene != null, "mestre entrou");

            ana = new WebGatewayTest.WsClient(gateway.getPort());
            final WebGatewayTest.WsClient a = ana;
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("\"challenge\"")), "desafio");
            ana.send("{\"type\":\"hello\",\"player\":\"Ana\",\"playerId\":\"web-ana\",\"isGm\":false,\"protocolVersion\":3}");
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("\"scene_sync\"")), "Ana entrou");

            CharacterSheet sheet = new CharacterSheet("Elora");
            java.lang.reflect.Field id = CharacterSheet.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(sheet, "ficha-elora");
            sheet.setOwnerPlayerId("web-ana");
            sheet.setLevel(3);
            sheet.setCharisma(16);
            sheet.setSpellcastingAbility(Ability.CHARISMA);
            sheet.getSpellSlots().setMax(1, 1);
            sheet.getSpellSlots().setCurrent(1, 1);
            Attack rapier = new Attack();
            rapier.setName("Rapieira");
            rapier.setAttackBonusOrDc("+40");
            rapier.setDamageAndType("1d8+3 perfurante");
            sheet.getAttacks().add(rapier);
            PreparedSpell hands = new PreparedSpell(1, "Burning Hands");
            hands.setDice("3d6");
            hands.setDamageType("fire");
            hands.setSaveAbility("DEXTERITY");
            hands.setHalfOnSave(true);
            sheet.getSpells().add(hands);
            sheet.getResources().add(new Resource("Furia", 3, Resource.LONG));
            gmC.sendMobSync(gobSheet);
            gmC.sendCharacterSync(sheet);
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("\"spells\"")), "ficha com magias chegou no navegador");

            // Ataque com alvo: compara a CA do goblin (sem vazar a CA pro jogador).
            ana.send("{\"type\":\"web_roll\",\"sheetId\":\"ficha-elora\",\"rollKind\":\"attack\",\"index\":0,\"mode\":\"normal\","
                    + "\"targets\":[\"" + goblin.getId() + "\"]}");
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("Elora ataca")), "ataque resolvido contra a CA");
            Thread.sleep(200);
            assertTrue(a.got.stream().noneMatch(s -> s.contains("vs CA")), "CA do monstro nao chega no jogador");
            boolean hit = a.got.stream().anyMatch(s -> s.contains("Goblin: acertou") || s.contains("ACERTO CRÍTICO"));

            // Dano: cai em quem foi acertado -- o goblin nao e dela, entao vira pedido pro mestre.
            ana.send("{\"type\":\"web_roll\",\"sheetId\":\"ficha-elora\",\"rollKind\":\"damage\",\"index\":0}");
            if (hit) {
                waitFor(() -> gm.requests.stream().anyMatch(r -> "damage".equals(r.getKind())), "pedido de dano no mestre");
                assertEquals("piercing", gm.requests.get(0).getDamageType());
            }

            // Magia de area com alvo: o mestre recebe a salvaguarda com a CD da ficha.
            ana.send("{\"type\":\"web_cast\",\"sheetId\":\"ficha-elora\",\"index\":0,\"slot\":1,\"targets\":[\"" + goblin.getId() + "\"]}");
            waitFor(() -> gm.requests.stream().anyMatch(r -> "save".equals(r.getKind())), "salvaguarda pedida");
            GmRequest save = gm.requests.stream().filter(r -> "save".equals(r.getKind())).findFirst().orElseThrow();
            assertEquals(sheet.spellSaveDc(), save.getDc());
            assertEquals("DEXTERITY", save.getAbility());
            waitFor(() -> gm.sheets.stream().anyMatch(s -> s.getSpellSlots().getCurrent(1) == 0), "espaco gasto chegou no mestre");
            ana.send("{\"type\":\"web_cast\",\"sheetId\":\"ficha-elora\",\"index\":0,\"slot\":1,\"targets\":[]}");
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("não tem espaco de magia")), "sem espaco: recusa");

            // Recurso: gasta e liga o efeito.
            ana.send("{\"type\":\"web_resource\",\"sheetId\":\"ficha-elora\",\"name\":\"Furia\"}");
            waitFor(() -> gm.sheets.stream().anyMatch(s -> s.getEffects().contains("Furia")
                    && s.findResource("Furia").map(r -> r.getCurrent() == 2).orElse(false)), "Furia usada");

            // Mestre pelo navegador: dano no goblin com as regras do programa.
            webGm = new WebGatewayTest.WsClient(gateway.getPort());
            final WebGatewayTest.WsClient g = webGm;
            waitFor(() -> g.got.stream().anyMatch(s -> s.contains("\"challenge\"")), "desafio do mestre web");
            webGm.send("{\"type\":\"hello\",\"player\":\"Mestra\",\"playerId\":\"web-gm\",\"isGm\":true,\"protocolVersion\":3,"
                    + "\"masterProof\":\"" + PasswordProof.prove("mestre", challengeOf(g.got)) + "\"}");
            waitFor(() -> g.got.stream().anyMatch(s -> s.contains("\"presence\"") && s.contains("web-gm") && s.contains("\"gm\":true")),
                    "mestre web aceito");
            Thread.sleep(200);
            webGm.send("{\"type\":\"web_gm\",\"action\":\"damage\",\"tokenId\":\"" + goblin.getId() + "\",\"amount\":5}");
            waitFor(() -> gm.tokenUpdates.stream().anyMatch(t -> t.getId().equals(goblin.getId()) && t.getCurrentHp() != null
                    && t.getCurrentHp() <= 15), "dano do mestre web chegou");
            ana.send("{\"type\":\"web_gm\",\"action\":\"damage\",\"tokenId\":\"" + goblin.getId() + "\",\"amount\":5}");
            waitFor(() -> a.got.stream().anyMatch(s -> s.contains("Só o mestre")), "jogador nao usa acao de mestre");
        } finally {
            if (ana != null) ana.close();
            if (webGm != null) webGm.close();
            gmC.disconnect();
            gateway.stop();
            server.stop();
        }
    }
}
