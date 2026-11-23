package com.vttcaseiro.view;

import com.vttcaseiro.model.Campaign;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Janela da campanha: o mestre ve as 6 abas (com anotacoes); o jogador nao ve a aba de anotacoes. */
class CampaignViewUiTest {

    private static boolean fxReady;

    @BeforeAll
    static void startFx() {
        if (Boolean.getBoolean("vtt.skipUi") || java.awt.GraphicsEnvironment.isHeadless()) return;
        try {
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            fxReady = started.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            fxReady = true;
        } catch (Throwable ex) {
            fxReady = false;
        }
        if (fxReady) Platform.setImplicitExit(false);
    }

    private static void onFx(Runnable r) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "FX travou");
        if (err.get() != null) throw new AssertionError(err.get());
    }

    private static void snap(javafx.scene.Node n, String file) {
        WritableImage shot = n.snapshot(null, null);
        BufferedImage o = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < o.getHeight(); y++)
            for (int x = 0; x < o.getWidth(); x++) o.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
        try {
            javax.imageio.ImageIO.write(o, "png", new java.io.File(file));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void masterAndPlayerViews() throws Exception {
        Assumptions.assumeTrue(fxReady, "sem tela");
        Campaign c = new Campaign();
        Campaign.PlayerRecord ana = new Campaign.PlayerRecord();
        ana.player = "Ana";
        ana.character = "Lyra";
        c.getPlayers().add(ana);
        c.giveXpToAll(450, "23/11/2026", "Sessão 1");
        Campaign.Npc npc = new Campaign.Npc();
        npc.name = "Bento, o taverneiro";
        npc.visible = true;
        c.getNpcs().add(npc);
        Campaign.Parcel loot = new Campaign.Parcel();
        loot.name = "Baú do ogro";
        loot.gp = 120;
        loot.xp = 450;
        Campaign.ParcelItem sword = new Campaign.ParcelItem();
        sword.name = "Espada Longa +1 da Chama";
        sword.unidentified = true;
        sword.publicName = "espada de lâmina avermelhada";
        loot.items.add(sword);
        c.getParcels().add(loot);
        AtomicReference<Campaign> saved = new AtomicReference<>();
        onFx(() -> {
            CampaignView gm = new CampaignView(null, c, true, saved::set);
            gm.getStage().getScene().getStylesheets().add(CampaignView.class.getResource("/style.css").toExternalForm());
            gm.show();
            assertEquals(8, gm.tabs().getTabs().size());
            assertEquals("Anotações do mestre", gm.tabs().getTabs().get(7).getText());
            String dir = System.getProperty("vtt.snap");
            if (dir != null) {
                snap(gm.getStage().getScene().getRoot(), dir + "/camp-jogadores.png");
                gm.tabs().getSelectionModel().select(1);
                gm.getStage().getScene().getRoot().applyCss();
                gm.getStage().getScene().getRoot().layout();
                snap(gm.getStage().getScene().getRoot(), dir + "/camp-calendario.png");
                gm.tabs().getSelectionModel().select(5);
                gm.getStage().getScene().getRoot().applyCss();
                gm.getStage().getScene().getRoot().layout();
                snap(gm.getStage().getScene().getRoot(), dir + "/camp-tesouro.png");
            }
            gm.getStage().close();

            CampaignView player = new CampaignView(null, c.publicCopy(), false, x -> fail("jogador não salva"));
            player.show();
            assertTrue(player.tabs().getTabs().stream().map(Tab::getText).noneMatch(t -> t.contains("Anotações")));
            player.getStage().close();
        });
    }
}
