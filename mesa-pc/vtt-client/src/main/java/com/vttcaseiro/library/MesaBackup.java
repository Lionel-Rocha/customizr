package com.vttcaseiro.library;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup da mesa inteira num arquivo .zip so (fichas, cenas, monstros,
 * imagens, sons, diario, tabelas, macros...): pra guardar a campanha ou
 * levar pra outro computador. Importar cria uma mesa NOVA (nunca sobrescreve
 * uma existente).
 */
public final class MesaBackup {
    public static final String MARKER = "vtt-caseiro-backup.txt";
    private static final long MAX_TOTAL = 4L * 1024 * 1024 * 1024;

    private MesaBackup() {
    }

    /** Compacta a pasta da mesa em {@code zip}. Devolve quantos arquivos foram. */
    public static int export(Path mesaRoot, Path zip) throws IOException {
        // o backup manual leva tudo menos os backups automaticos (senao cada zip carregaria os anteriores)
        return export(mesaRoot, zip, rel -> !rel.startsWith(AutoBackup.DIR + "/"));
    }

    /** Igual, so com os arquivos (caminho relativo com "/") que {@code include} aceitar. */
    public static int export(Path mesaRoot, Path zip, java.util.function.Predicate<String> include) throws IOException {
        Path root = mesaRoot.toAbsolutePath().normalize();
        Path target = zip.toAbsolutePath().normalize();
        int count = 0;
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream fos = Files.newOutputStream(tmp); ZipOutputStream out = new ZipOutputStream(fos);
             Stream<Path> walk = Files.walk(root)) {
            out.putNextEntry(new ZipEntry(MARKER));
            out.write(("Backup do Customizr\nMesa: " + root.getFileName() + "\nData: " + LocalDateTime.now() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                Path abs = p.toAbsolutePath().normalize();
                if (abs.equals(target) || abs.equals(tmp) || abs.getFileName().toString().endsWith(".part")) continue;
                String name = root.relativize(abs).toString().replace('\\', '/');
                if (!include.test(name)) continue;
                out.putNextEntry(new ZipEntry(name));
                Files.copy(abs, out);
                out.closeEntry();
                count++;
            }
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    /** Nome de pasta seguro a partir do que o usuario digitou. */
    public static String safeName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("^\\.+", "");
        return n.isBlank() ? "mesa-importada" : n;
    }

    /**
     * Descompacta o backup numa mesa nova {@code baseFolder/name}. Recusa se a
     * pasta ja existe, se o zip nao e backup do VTT, ou se algum arquivo
     * tentar sair da pasta (zip malicioso com "../").
     */
    public static Path importTo(Path zip, Path baseFolder, String name) throws IOException {
        Path target = baseFolder.resolve(safeName(name)).toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Ja existe uma mesa chamada \"" + target.getFileName() + "\".");
        if (!isBackup(zip)) throw new IOException("Esse arquivo nao e um backup de mesa do Customizr.");
        Files.createDirectories(target);
        long total = 0;
        try (InputStream fis = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(fis)) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory() || MARKER.equals(e.getName())) continue;
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) throw new IOException("Backup invalido (arquivo fora da pasta): " + e.getName());
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_TOTAL) throw new IOException("Backup grande demais.");
                        os.write(buf, 0, n);
                    }
                }
            }
        } catch (IOException ex) {
            deleteQuietly(target);
            throw ex;
        }
        return target;
    }

    public static boolean isBackup(Path zip) {
        try (InputStream fis = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(fis)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) if (MARKER.equals(e.getName())) return true;
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : all) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }
}
