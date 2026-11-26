package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.party.Party;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Grupo: o bau e de todos, quem esta no grupo o mestre decide. */
class PartyRulesNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Party> parties = new CopyOnWriteArrayList<>();
        @Override public void onPartySync(Party p) { parties.add(p); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void playersChangeTheChestButNotWhoIsInTheGroup() throws Exception {
        GameServer server = new GameServer(0, new SceneModel(), null, "senha", "mestre");
        server.start();
        Rec gm = new Rec(), ana = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm"), anaC = new GameClient(ana, null, "ana");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true, "senha", "mestre");
            anaC.connect("localhost", server.getPort(), "Ana", false, "senha", null);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            Party party = new Party("Os Corvos");
            party.addMember("ficha-elora");
            party.addMember("ficha-borin");
            gmC.sendPartySync(party);
            waitFor(() -> ana.parties.size() == 1, "grupo do mestre chega");

            // a Ana guarda uma corda no bau, mas tambem tenta tirar o Borin e renomear o grupo
            Party edited = NetCodec.deepCopy(ana.parties.get(0), Party.class);
            edited.getSharedInventory().addItem("Corda (15 m)", 1, 5, 100);
            edited.removeMember("ficha-borin");
            edited.setName("Grupo da Ana");
            anaC.sendPartySync(edited);
            waitFor(() -> gm.parties.size() >= 1 && ana.parties.size() >= 2, "volta pra todos");

            Party seen = gm.parties.get(gm.parties.size() - 1);
            assertTrue(seen.getSharedInventory().findByName("Corda (15 m)").isPresent(), "o bau mudou");
            assertEquals(List.of("ficha-elora", "ficha-borin"), seen.getCharacterIds(), "o Borin continua no grupo");
            assertEquals("Os Corvos", seen.getName(), "o nome e do mestre");
            Party back = ana.parties.get(ana.parties.size() - 1);
            assertEquals(2, back.getCharacterIds().size(), "a Ana recebe de volta como ficou de verdade");

            // o mestre muda quem esta no grupo normalmente
            Party gmEdit = NetCodec.deepCopy(seen, Party.class);
            gmEdit.removeMember("ficha-borin");
            gmC.sendPartySync(gmEdit);
            waitFor(() -> ana.parties.get(ana.parties.size() - 1).getCharacterIds().size() == 1, "mestre tira o Borin");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }
}
