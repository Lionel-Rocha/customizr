package com.vttcaseiro.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Backups automaticos da mesa, guardados dentro dela em {@code backups/}: uma copia .zip a cada tanto
 * tempo (so se algo mudou desde a ultima), mantendo as {@link #KEEP} mais novas e, alem delas, a ultima de cada
 * dia dos ultimos {@link #KEEP_DAYS} dias (da pra voltar pra "como estava ontem"). Opcionalmente uma copia vai pra
 * outra pasta ({@link #mirror}), por exemplo a do Google Drive, com as imagens e sons junto.
 * <p>
 * As imagens e os sons ({@code assets/}) ficam de fora: eles nunca mudam depois de guardados (o nome e o
 * hash do conteudo), entao copiar em todo backup so encheria o disco. Restaurar copia esses arquivos da
 * mesa atual. Restaurar sempre cria uma mesa NOVA -- nunca apaga a que esta aberta.
 */
public final class AutoBackup {
    public static final String DIR = "backups";
    public static final int KEEP = 20;
    public static final int KEEP_DAYS = 14;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Um backup da lista: arquivo, quando, tamanho e motivo ("auto", "abertura", "manual"). */
    public record Entry(Path file, LocalDateTime when, long sizeBytes, String kind) {
    }

    private AutoBackup() {
    }

    public static Path dir(Path mesaRoot) {
        return mesaRoot.resolve(DIR);
    }

    /** Entra no backup automatico? (fora: a propria pasta de backups e as imagens/sons) */
    static boolean included(String relative) {
        return !relative.startsWith(DIR + "/") && !relative.startsWith("assets/") && !relative.endsWith(".part");
    }

    /** Ultima mudanca em algum arquivo da mesa (sem contar backups e assets); 0 se nao tem nada. */
    static long lastChange(Path mesaRoot) throws IOException {
        Path root = mesaRoot.toAbsolutePath().normalize();
        long max = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String rel = root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
                if (included(rel)) max = Math.max(max, Files.getLastModifiedTime(p).toMillis());
            }
        }
        return max;
    }

    /** Backups da mesa, do mais novo pro mais velho. */
    public static List<Entry> list(Path mesaRoot) {
        return listDir(dir(mesaRoot));
    }

    /** Backups (nossos .zip) de uma pasta qualquer, do mais novo pro mais velho. */
    static List<Entry> listDir(Path dir) {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                if (!name.endsWith(".zip")) continue;
                int dash = name.indexOf('-');
                if (dash <= 0) continue;
                try {
                    LocalDateTime when = LocalDateTime.parse(name.substring(dash + 1, name.length() - 4), STAMP);
                    out.add(new Entry(f, when, Files.size(f), name.substring(0, dash)));
                } catch (DateTimeParseException | IOException ignored) {
                    // arquivo que nao e nosso: fica fora da lista
                }
            }
        } catch (IOException ignored) {
            return out;
        }
        out.sort(Comparator.comparing(Entry::when).reversed());
        return out;
    }

    /**
     * Faz um backup se algo mudou desde o ultimo (ou {@code force}). {@code kind} vai no nome do arquivo.
     * Apaga os mais velhos alem de {@link #KEEP}. Vazio = nada mudou.
     */
    public static synchronized Optional<Entry> create(Path mesaRoot, String kind, boolean force) throws IOException {
        String safeKind = kind == null || !kind.matches("[a-z]{1,20}") ? "auto" : kind;
        List<Entry> existing = list(mesaRoot);
        long changed = lastChange(mesaRoot);
        if (changed == 0) return Optional.empty();
        if (!force && !existing.isEmpty()
                && changed <= Files.getLastModifiedTime(existing.get(0).file()).toMillis()) {
            return Optional.empty();
        }
        Files.createDirectories(dir(mesaRoot));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        // nunca mais velho que o ultimo (dois no mesmo segundo: o novo fica 1 s depois)
        if (!existing.isEmpty() && !now.isAfter(existing.get(0).when())) now = existing.get(0).when().plusSeconds(1);
        Path zip = dir(mesaRoot).resolve(safeKind + "-" + now.format(STAMP) + ".zip");
        while (Files.exists(zip)) {
            now = now.plusSeconds(1);
            zip = dir(mesaRoot).resolve(safeKind + "-" + now.format(STAMP) + ".zip");
        }
        MesaBackup.export(mesaRoot, zip, AutoBackup::included);
        prune(mesaRoot, KEEP);
        return Optional.of(new Entry(zip, now, Files.size(zip), safeKind));
    }

    static void prune(Path mesaRoot, int keep) throws IOException {
        pruneDir(dir(mesaRoot), keep);
    }

    static void pruneDir(Path dir, int keep) throws IOException {
        for (Entry e : toDelete(listDir(dir), keep, KEEP_DAYS, java.time.LocalDate.now())) Files.deleteIfExists(e.file());
    }

    /**
     * Quais apagar: ficam as {@code keep} mais novas e a ultima de cada dia dos ultimos {@code keepDays} dias.
     * {@code all} vem do mais novo pro mais velho.
     */
    static List<Entry> toDelete(List<Entry> all, int keep, int keepDays, java.time.LocalDate today) {
        java.util.Set<java.time.LocalDate> daysKept = new java.util.HashSet<>();
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            java.time.LocalDate day = e.when().toLocalDate();
            boolean recentDay = !day.isBefore(today.minusDays(keepDays - 1L));
            boolean firstOfDay = daysKept.add(day); // a lista vem do mais novo: o primeiro do dia e o ultimo feito nele
            if (i < keep || (recentDay && firstOfDay)) continue;
            out.add(e);
        }
        return out;
    }

    /**
     * Copia o backup pra outra pasta ({@code mirrorRoot/<mesa>/}), com as imagens e sons que ainda nao estao la
     * (eles nunca mudam: so copia os novos). Apaga la os velhos com a mesma regra daqui.
     */
    public static Path mirror(Entry made, Path mesaRoot, Path mirrorRoot, String mesaName) throws IOException {
        Path dir = mirrorRoot.resolve(MesaBackup.safeName(mesaName == null || mesaName.isBlank() ? "mesa" : mesaName));
        Files.createDirectories(dir);
        Path out = dir.resolve(made.file().getFileName().toString());
        Files.copy(made.file(), out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        Path assets = mesaRoot.resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    Path to = dir.resolve(mesaRoot.relativize(p).toString()).normalize();
                    if (!to.startsWith(dir) || Files.exists(to)) continue;
                    Files.createDirectories(to.getParent());
                    Files.copy(p, to, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        pruneDir(dir, KEEP);
        return out;
    }

    /**
     * Restaura o backup como uma mesa nova {@code baseFolder/name}, com as imagens e sons da mesa atual
     * (que o backup automatico nao copia).
     */
    public static Path restoreAsNewMesa(Path backupZip, Path currentMesaRoot, Path baseFolder, String name) throws IOException {
        Path target = MesaBackup.importTo(backupZip, baseFolder, name);
        Path assets = currentMesaRoot.resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    Path out = target.resolve(currentMesaRoot.relativize(p).toString()).normalize();
                    if (!out.startsWith(target) || Files.exists(out)) continue;
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return target;
    }
}
