package com.vttcaseiro.library;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.Resource;
import com.vttcaseiro.service.TableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Backup (um por dia e copia em outra pasta) e resumo do descanso longo. */
class RestBackupTest {

    @Test
    void keepsTheNewestAndTheLastOfEachRecentDay() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        List<AutoBackup.Entry> all = new ArrayList<>();
        // 30 de hoje (a cada 10 min), 2 de ontem, 1 de 20 dias atras -- do mais novo pro mais velho
        for (int i = 0; i < 30; i++) all.add(entry(today.atTime(23, 0).minusMinutes(10L * i)));
        all.add(entry(today.minusDays(1).atTime(22, 0)));
        all.add(entry(today.minusDays(1).atTime(20, 0)));
        all.add(entry(today.minusDays(20).atTime(20, 0)));
        List<AutoBackup.Entry> gone = AutoBackup.toDelete(all, 20, 14, today);
        assertEquals(10 + 1 + 1, gone.size(), "10 de hoje alem das 20, o mais velho de ontem e o de 20 dias");
        assertFalse(gone.contains(all.get(30)), "o ultimo de ontem fica");
        assertTrue(gone.contains(all.get(31)));
        assertTrue(gone.contains(all.get(32)), "fora dos 14 dias");
    }

    private static AutoBackup.Entry entry(LocalDateTime when) {
        return new AutoBackup.Entry(Path.of("auto-" + when.toString().replace(':', '-') + ".zip"), when, 1, "auto");
    }

    @Test
    void mirrorCopiesTheZipAndAssetsAndImportBringsThemBack(@TempDir Path dir) throws Exception {
        Path mesa = Files.createDirectories(dir.resolve("Campanha"));
        Files.writeString(mesa.resolve("campanha.json"), "{}");
        Files.createDirectories(mesa.resolve("assets/images"));
        Files.writeString(mesa.resolve("assets/images/abc.png"), "img");
        AutoBackup.Entry made = AutoBackup.create(mesa, "manual", true).orElseThrow();
        Path drive = Files.createDirectories(dir.resolve("Drive"));
        Path copy = AutoBackup.mirror(made, mesa, drive, "Campanha");
        assertTrue(Files.exists(copy));
        assertTrue(Files.exists(drive.resolve("Campanha/assets/images/abc.png")), "imagens vao junto");
        Path restored = MesaBackup.importTo(copy, Files.createDirectories(dir.resolve("outroPc")), "Campanha");
        assertTrue(Files.exists(restored.resolve("campanha.json")));
        assertEquals("img", Files.readString(restored.resolve("assets/images/abc.png")), "importar traz as imagens do lado");
    }

    @Test
    void longRestTellsWhatCameBack() {
        CharacterSheet s = new CharacterSheet("Lyra");
        s.setLevel(3);
        s.setHitDie("d8");
        s.setHpMax(24);
        s.setHpCurrent(10);
        s.spendHitDie();
        s.getSpellSlots().setMax(1, 4);
        s.getSpellSlots().setCurrent(1, 1);
        Resource rage = new Resource("Rage", 3, Resource.LONG);
        rage.use();
        s.getResources().add(rage);
        String text = TableService.longRestDetail(s);
        assertTrue(text.startsWith("+14 PV"), text);
        assertTrue(text.contains("3 espaços de magia"), text);
        assertTrue(text.contains("Rage"), text);
        assertEquals("já estava inteiro", TableService.longRestDetail(s));
    }
}
