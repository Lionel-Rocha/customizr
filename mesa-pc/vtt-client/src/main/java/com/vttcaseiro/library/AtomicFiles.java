package com.vttcaseiro.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gravacao "tudo ou nada": escreve num arquivo temporario na MESMA pasta e
 * so depois troca pelo arquivo de verdade (rename atomico). Se o programa
 * travar ou faltar luz no meio, o arquivo antigo continua inteiro -- antes,
 * gravar direto no .json final podia deixar uma ficha/cena pela metade e
 * ilegivel.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void writeString(Path target, String content) throws IOException {
        writeBytes(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(Path target, byte[] bytes) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, "." + target.getFileName() + ".", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                // Sistema de arquivos sem rename atomico (raro): troca normal.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
