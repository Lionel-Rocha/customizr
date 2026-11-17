package com.vttcaseiro.net;

import com.vttcaseiro.model.Fog;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Camada do mestre, neblina e senha por desafio, com servidor e clientes reais. */
class Priority3NetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> removed = new CopyOnWriteArrayList<>();
        final List<Fog> fogs = new CopyOnWriteArrayList<>();

        @Override public void onTokenRemoved(String id) { removed.add(id); }
        @Override public void onFogSync(Fog fog) { fogs.add(fog); }
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
    void tokenOcultoNaoChegaNoJogadorAteSerRevelado() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            TokenModel dragon = new TokenModel("Dragao", null);
            dragon.setHiddenFromPlayers(true);
            gmC.sendAddToken(dragon);
            waitFor(() -> gm.tokensAdded.size() == 1, "mestre recebe o oculto");
            gmC.sendMoveToken(dragon.getId(), 70, 70);
            Thread.sleep(300);
            assertTrue(ana.tokensAdded.isEmpty(), "jogador nao recebe token oculto");

            // Quem entra depois tambem nao recebe na cena inicial.
            lateC.connect("localhost", server.getPort(), "Atrasado", false);
            waitFor(() -> late.scene != null, "atrasado conectou");
            assertTrue(late.scene.getTokens().isEmpty(), "cena do jogador vem sem o oculto");

            // Jogador nao consegue revelar.
            TokenModel hack = NetCodec.deepCopy(dragon, TokenModel.class);
            hack.setHiddenFromPlayers(false);
            anaC.sendUpdateToken(hack);
            Thread.sleep(300);
            assertTrue(ana.tokensAdded.isEmpty());

            // Mestre revela: jogadores recebem o token inteiro.
            TokenModel shown = NetCodec.deepCopy(dragon, TokenModel.class);
            shown.setHiddenFromPlayers(false);
            gmC.sendUpdateToken(shown);
            waitFor(() -> ana.tokensAdded.size() == 1 && late.tokensAdded.size() == 1, "revelado");
            assertEquals("Dragao", ana.tokensAdded.get(0).getName());

            // Mestre esconde de novo: jogadores recebem remove.
            gmC.sendUpdateToken(dragon); // dragon ainda tem hidden=true
            waitFor(() -> ana.removed.contains(dragon.getId()), "sumiu pro jogador");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }

    @Test
    void neblinaSoDoMestre() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");
            Fog fog = new Fog();
            fog.setEnabled(true);
            fog.setArea(0, 0, 2, 1, true);
            gmC.sendFog(fog);
            waitFor(() -> ana.fogs.size() == 1, "neblina chegou");
            assertTrue(ana.fogs.get(0).isRevealed(2, 1));
            assertFalse(ana.fogs.get(0).isRevealed(3, 1));
            anaC.sendFog(new Fog());
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Só o mestre mexe na neblina")), "recusa");
            assertTrue(gm.fogs.isEmpty(), "jogador nao altera a neblina de ninguem");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }

    @Test
    void senhaViajaComoProvaENaoEmTexto() throws Exception {
        GameServer server = new GameServer(0, new SceneModel(), null, "segredo", null);
        server.start();
        try (Socket raw = new Socket("localhost", server.getPort())) {
            BufferedReader in = new BufferedReader(new InputStreamReader(raw.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(raw.getOutputStream(), StandardCharsets.UTF_8), true);
            NetMessage challenge = NetCodec.decode(in.readLine());
            assertEquals("challenge", challenge.type);
            // Cliente antigo mandando a senha em texto: nao serve mais.
            NetMessage plain = NetMessage.hello("Velho", "v", false);
            plain.password = "segredo";
            out.println(NetCodec.encode(plain));
            assertTrue(NetCodec.decode(in.readLine()).text.contains("Senha da mesa incorreta"));
        }
        // Cliente atual com a senha certa entra; o hello so leva a prova.
        NetMessage hello = NetMessage.hello("Ana", "ana", false, "segredo", null, "desafio-x");
        assertNull(hello.password);
        assertNotNull(hello.passwordProof);
        assertFalse(NetCodec.encode(hello).contains("segredo"), "a senha nao aparece na mensagem");

        Rec ana = new Rec();
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            anaC.connect("localhost", server.getPort(), "Ana", false, "segredo", null);
            waitFor(() -> ana.scene != null, "senha certa entra");
        } finally {
            anaC.disconnect();
            server.stop();
        }
    }

    @Test
    void cincoErrosBloqueiamOIpPorUmTempo() {
        AtomicLong now = new AtomicLong(1_000_000);
        LoginGuard guard = new LoginGuard(now::get);
        for (int i = 0; i < LoginGuard.MAX_FAILURES - 1; i++) guard.failed("1.2.3.4");
        assertFalse(guard.isBlocked("1.2.3.4"));
        guard.failed("1.2.3.4");
        assertTrue(guard.isBlocked("1.2.3.4"));
        assertFalse(guard.isBlocked("5.6.7.8"), "outro IP nao e afetado");
        now.addAndGet(LoginGuard.BLOCK_MS + 1);
        assertFalse(guard.isBlocked("1.2.3.4"), "bloqueio expira");
        assertTrue(PasswordProof.matches(null, "x", null), "mesa sem senha aceita");
        assertFalse(PasswordProof.matches("a", "x", PasswordProof.prove("b", "x")));
        assertFalse(PasswordProof.matches("a", "x", PasswordProof.prove("a", "outro-desafio")), "prova nao serve em outra conexao");
    }
}
