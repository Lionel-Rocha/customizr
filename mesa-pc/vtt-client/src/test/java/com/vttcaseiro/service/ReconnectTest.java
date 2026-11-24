package com.vttcaseiro.service;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.net.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** A conexao cai e o programa volta sozinho; desconectar de proposito nao tenta voltar. */
class ReconnectTest {

    static class Rec implements NetworkService.GameListener {
        final AtomicInteger scenes = new AtomicInteger();
        final AtomicInteger reconnecting = new AtomicInteger();
        final AtomicInteger reconnected = new AtomicInteger();
        final AtomicReference<String> disconnected = new AtomicReference<>();

        @Override public void onSceneSync(SceneModel scene) { scenes.incrementAndGet(); }
        @Override public void onTokenMoved(String tokenId, double x, double y) { }
        @Override public void onTokenAdded(TokenModel token) { }
        @Override public void onTokenRemoved(String tokenId) { }
        @Override public void onRollResult(String player, String detail, int total) { }
        @Override public void onChat(String player, String text) { }
        @Override public void onDisconnected(String reason) { disconnected.set(reason); }
        @Override public void onReconnecting(int attempt, int max) { reconnecting.incrementAndGet(); }
        @Override public void onReconnected() { reconnected.incrementAndGet(); }
    }

    private final int attempts = NetworkService.RECONNECT_ATTEMPTS;
    private final long delay = NetworkService.RECONNECT_DELAY_MS;

    @AfterEach
    void restore() {
        NetworkService.RECONNECT_ATTEMPTS = attempts;
        NetworkService.RECONNECT_DELAY_MS = delay;
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 8000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void comesBackWhenTheServerReturns() throws Exception {
        NetworkService.RECONNECT_ATTEMPTS = 60;
        NetworkService.RECONNECT_DELAY_MS = 100;
        GameServer first = new GameServer(0, new SceneModel());
        first.start();
        int port = first.getPort();
        NetworkService ns = new NetworkService();
        Rec rec = new Rec();
        GameServer second = null;
        try {
            ns.connect("localhost", port, "Ana", rec);
            waitFor(() -> rec.scenes.get() > 0, "entrou");
            first.stop(); // a internet "caiu"
            waitFor(() -> rec.reconnecting.get() > 0, "comecou a tentar");
            assertNull(rec.disconnected.get(), "ainda nao desistiu");
            second = new GameServer(port, new SceneModel());
            second.start();
            waitFor(() -> rec.reconnected.get() == 1, "voltou");
            assertTrue(ns.isConnected());
            waitFor(() -> rec.scenes.get() > 1, "recebeu a mesa de novo");
            assertNull(rec.disconnected.get());
        } finally {
            ns.disconnect();
            if (second != null) second.stop();
            first.stop();
        }
    }

    @Test
    void givesUpAfterTheAttemptsAndLeavingOnPurposeDoesNotRetry() throws Exception {
        NetworkService.RECONNECT_ATTEMPTS = 3;
        NetworkService.RECONNECT_DELAY_MS = 50;
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        NetworkService ns = new NetworkService();
        Rec rec = new Rec();
        try {
            ns.connect("localhost", server.getPort(), "Ana", rec);
            waitFor(() -> rec.scenes.get() > 0, "entrou");
            server.stop();
            waitFor(() -> rec.disconnected.get() != null, "desistiu depois das tentativas");
            assertEquals(3, rec.reconnecting.get());
            assertFalse(ns.isConnected());
        } finally {
            ns.disconnect();
            server.stop();
        }

        GameServer again = new GameServer(0, new SceneModel());
        again.start();
        NetworkService ns2 = new NetworkService();
        Rec rec2 = new Rec();
        try {
            ns2.connect("localhost", again.getPort(), "Beto", rec2);
            waitFor(() -> rec2.scenes.get() > 0, "entrou");
            ns2.disconnect(); // saiu porque quis
            Thread.sleep(300);
            assertEquals(0, rec2.reconnecting.get(), "nao tenta voltar");
        } finally {
            again.stop();
        }
    }
}
