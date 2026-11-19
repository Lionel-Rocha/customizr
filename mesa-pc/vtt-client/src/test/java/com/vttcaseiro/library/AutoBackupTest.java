package com.vttcaseiro.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Backups automaticos: so quando muda, sem as imagens, guarda os mais novos, restaura numa mesa nova. */
class AutoBackupTest {

    private static Path mesa(Path base) throws Exception {
        Path root = Files.createDirectories(base.resolve("Campanha"));
        Files.createDirectories(root.resolve("fichas"));
        Files.writeString(root.resolve("fichas/Elora.json"), "{\"name\":\"Elora\",\"hp\":20}");
        Files.createDirectories(root.resolve("assets/images"));
        Files.write(root.resolve("assets/images/" + "a".repeat(64) + ".png"), new byte[]{1, 2, 3});
        return root;
    }

    /** Joga a data dos arquivos da mesa pro passado (pra "nada mudou depois do backup" ser verdade). */
    private static void age(Path root, long millisAgo) throws Exception {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (p.toString().contains(AutoBackup.DIR)) continue;
                Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis() - millisAgo));
            }
        }
    }

    @Test
    void onlyWhenSomethingChangedAndWithoutAssets(@TempDir Path base) throws Exception {
        Path root = mesa(base);
        age(root, 60_000);
        Optional<AutoBackup.Entry> first = AutoBackup.create(root, "abertura", false);
        assertTrue(first.isPresent());
        assertEquals("abertura", first.get().kind());
        try (ZipFile zip = new ZipFile(first.get().file().toFile())) {
            assertNotNull(zip.getEntry("fichas/Elora.json"));
            assertNull(zip.getEntry("assets/images/" + "a".repeat(64) + ".png"), "imagens ficam de fora");
            assertNotNull(zip.getEntry(MesaBackup.MARKER));
        }
        assertTrue(AutoBackup.create(root, "auto", false).isEmpty(), "nada mudou: nao copia de novo");
        assertTrue(AutoBackup.create(root, "manual", true).isPresent(), "na mao copia mesmo assim");

        Files.writeString(root.resolve("fichas/Elora.json"), "{\"name\":\"Elora\",\"hp\":3}");
        Files.setLastModifiedTime(root.resolve("fichas/Elora.json"), FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertTrue(AutoBackup.create(root, "auto", false).isPresent(), "mudou: copia");
        List<AutoBackup.Entry> all = AutoBackup.list(root);
        assertEquals(3, all.size());
        assertFalse(all.get(0).when().isBefore(all.get(2).when()), "mais novo primeiro");

        // o backup manual (exportar mesa) nao carrega os automaticos
        Path manual = base.resolve("export.zip");
        MesaBackup.export(root, manual);
        try (ZipFile zip = new ZipFile(manual.toFile())) {
            assertTrue(zip.stream().noneMatch(e -> e.getName().startsWith(AutoBackup.DIR + "/")));
            assertNotNull(zip.getEntry("assets/images/" + "a".repeat(64) + ".png"), "o manual leva as imagens");
        }
    }

    @Test
    void keepsOnlyTheNewest(@TempDir Path base) throws Exception {
        Path root = mesa(base);
        for (int i = 0; i < AutoBackup.KEEP + 4; i++) AutoBackup.create(root, "auto", true);
        assertEquals(AutoBackup.KEEP, AutoBackup.list(root).size());
        Files.writeString(AutoBackup.dir(root).resolve("leia-me.txt"), "nao e backup");
        Files.writeString(AutoBackup.dir(root).resolve("estranho.zip"), "x");
        assertEquals(AutoBackup.KEEP, AutoBackup.list(root).size(), "arquivo de fora nao entra na lista");
        assertEquals("auto", AutoBackup.create(root, "../../hack", true).orElseThrow().kind(), "motivo estranho vira auto");
    }

    @Test
    void restoreMakesANewMesaWithTheOldDataAndCurrentImages(@TempDir Path base) throws Exception {
        Path root = mesa(base);
        AutoBackup.Entry before = AutoBackup.create(root, "auto", true).orElseThrow();
        Files.writeString(root.resolve("fichas/Elora.json"), "{\"name\":\"Elora\",\"hp\":0}");
        Files.write(root.resolve("assets/images/" + "b".repeat(64) + ".gif"), new byte[]{9});

        Path restored = AutoBackup.restoreAsNewMesa(before.file(), root, base, "Campanha (restaurada)");
        assertEquals(base.resolve("Campanha (restaurada)").toAbsolutePath().normalize(), restored);
        assertTrue(Files.readString(restored.resolve("fichas/Elora.json")).contains("\"hp\":20"), "dados de antes");
        assertTrue(Files.exists(restored.resolve("assets/images/" + "a".repeat(64) + ".png")), "imagens da mesa atual");
        assertTrue(Files.exists(restored.resolve("assets/images/" + "b".repeat(64) + ".gif")));
        assertFalse(Files.exists(restored.resolve(AutoBackup.DIR)), "a copia nao herda a pasta de backups");
        assertTrue(Files.readString(root.resolve("fichas/Elora.json")).contains("\"hp\":0"), "a mesa atual nao muda");
        assertThrows(java.io.IOException.class,
                () -> AutoBackup.restoreAsNewMesa(before.file(), root, base, "Campanha (restaurada)"), "nome repetido");
    }
}
