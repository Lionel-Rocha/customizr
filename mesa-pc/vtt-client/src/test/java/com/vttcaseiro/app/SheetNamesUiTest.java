package com.vttcaseiro.app;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.repository.MesaRepository;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Listas de Fichas e Monstros mostram o nome de verdade (o arquivo perde acento e virgula: "Drag_o"). */
class SheetNamesUiTest {
    static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = Main.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    void listsShowRealNamesNotFileNames(@TempDir Path dir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(Boolean.getBoolean("vtt.skipUi") || java.awt.GraphicsEnvironment.isHeadless(), "sem tela");
        try { CountDownLatch s = new CountDownLatch(1); Platform.startup(s::countDown); s.await(10, TimeUnit.SECONDS); } catch (IllegalStateException ignored) { }
        Platform.setImplicitExit(false);
        Path mesa = Files.createDirectories(dir.resolve("Campanha"));
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Main main = new Main();
                Stage stage = new Stage();
                Field sf = Main.class.getDeclaredField("stage");
                sf.setAccessible(true);
                sf.set(main, stage);
                Method m = Main.class.getDeclaredMethod("onMesaChosen", MesaRepository.class);
                m.setAccessible(true);
                m.invoke(main, new LibraryStorage(mesa));
                stage.show();
                ((com.vttcaseiro.service.CharacterService) field(main, "characterService")).save(new com.vttcaseiro.character.CharacterSheet("Drakon, o Bardo"));
                ((com.vttcaseiro.service.CharacterService) field(main, "characterService")).save(new com.vttcaseiro.character.CharacterSheet("Lyra"));
                ((com.vttcaseiro.service.MobService) field(main, "mobService")).save(new com.vttcaseiro.mob.MobSheet("Dragão Vermelho Adulto"));
                Method rc = Main.class.getDeclaredMethod("refreshCharacterList"); rc.setAccessible(true); rc.invoke(main);
                Method rm = Main.class.getDeclaredMethod("refreshMobList"); rm.setAccessible(true); rm.invoke(main);
                @SuppressWarnings("unchecked") ListView<String> cl = (ListView<String>) field(main, "characterListView");
                @SuppressWarnings("unchecked") ListView<String> ml = (ListView<String>) field(main, "mobListView");
                Method sl = Main.class.getDeclaredMethod("sheetLabel", String.class); sl.setAccessible(true);
                Method mlab = Main.class.getDeclaredMethod("mobLabel", String.class); mlab.setAccessible(true);
                org.junit.jupiter.api.Assertions.assertTrue(cl.getItems().contains("Drakon_ o Bardo"), "o arquivo continua igual");
                org.junit.jupiter.api.Assertions.assertEquals("Drakon, o Bardo", sl.invoke(main, "Drakon_ o Bardo"));
                org.junit.jupiter.api.Assertions.assertEquals("Lyra", sl.invoke(main, "Lyra"));
                org.junit.jupiter.api.Assertions.assertEquals("Dragão Vermelho Adulto", mlab.invoke(main, "Drag_o Vermelho Adulto"));
                stage.close();
            } catch (Throwable t) { err.set(t); } finally { done.countDown(); }
        });
        done.await(60, TimeUnit.SECONDS);
        if (err.get() != null) throw new AssertionError(err.get());
    }
}
