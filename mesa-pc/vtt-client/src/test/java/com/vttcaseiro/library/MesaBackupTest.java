package com.vttcaseiro.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MesaBackupTest {

    @Test
    void exportaEImportaAMesaInteira(@TempDir Path dir) throws Exception {
        Path base = Files.createDirectories(dir.resolve("mesas"));
        Path mesa = Files.createDirectories(base.resolve("Cripta"));
        new LibraryStorage(mesa); // cria a estrutura de pastas de uma mesa de verdade
        Files.writeString(mesa.resolve("handouts.json"), "[{\"title\":\"Carta\"}]");
        Files.createDirectories(mesa.resolve("assets/audio"));
        Files.write(mesa.resolve("assets/audio/a.wav"), new byte[]{1, 2, 3});
        Path zip = dir.resolve("cripta.zip");

        int n = MesaBackup.export(mesa, zip);
        assertTrue(n >= 2);
        assertTrue(MesaBackup.isBackup(zip));

        Path imported = MesaBackup.importTo(zip, base, "Cripta (copia)");
        assertEquals("[{\"title\":\"Carta\"}]", Files.readString(imported.resolve("handouts.json")));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(imported.resolve("assets/audio/a.wav")));
        assertFalse(Files.exists(imported.resolve(MesaBackup.MARKER)), "o marcador nao vira arquivo da mesa");

        assertThrows(IOException.class, () -> MesaBackup.importTo(zip, base, "Cripta"), "nunca sobrescreve");
        assertEquals("a-b-c", MesaBackup.safeName("a/b\\c"));
        assertEquals("mesa-importada", MesaBackup.safeName("  "));
    }

    @Test
    void recusaZipQueNaoEBackupOuTentaSairDaPasta(@TempDir Path dir) throws Exception {
        Path base = Files.createDirectories(dir.resolve("mesas"));
        Path notBackup = dir.resolve("fotos.zip");
        try (OutputStream o = Files.newOutputStream(notBackup); ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry("foto.png"));
            z.write(1);
            z.closeEntry();
        }
        assertThrows(IOException.class, () -> MesaBackup.importTo(notBackup, base, "x"));

        Path evil = dir.resolve("malicioso.zip");
        try (OutputStream o = Files.newOutputStream(evil); ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry(MesaBackup.MARKER));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("../../fora.txt"));
            z.write("hack".getBytes());
            z.closeEntry();
        }
        IOException ex = assertThrows(IOException.class, () -> MesaBackup.importTo(evil, base, "y"));
        assertTrue(ex.getMessage().contains("fora da pasta"));
        assertFalse(Files.exists(dir.resolve("fora.txt")));
        assertFalse(Files.exists(base.resolve("y")), "importacao falha nao deixa mesa pela metade");
    }
}
