package com.vttcaseiro.service;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Trocar de cena reconstroi o mapa, e a tela nova se inscreve DURANTE o aviso (antes dava ConcurrentModification). */
class SceneListenerTest {

    @Test
    void ouvinteQueSeInscreveDuranteOAvisoNaoQuebraOsOutros() {
        SceneService scenes = new SceneService(new SceneModel());
        AtomicInteger later = new AtomicInteger();
        scenes.addListener(new SceneService.SceneChangeListener() {
            @Override public void onSceneReplaced(SceneModel s) {
                scenes.addListener(new SceneService.SceneChangeListener() { }); // a barra nova do mapa
            }
        });
        scenes.addListener(new SceneService.SceneChangeListener() {
            @Override public void onSceneReplaced(SceneModel s) { later.incrementAndGet(); }
        });
        assertDoesNotThrow(() -> scenes.replaceScene(new SceneModel()));
        assertEquals(1, later.get(), "quem vem depois tambem e avisado");
    }
}
