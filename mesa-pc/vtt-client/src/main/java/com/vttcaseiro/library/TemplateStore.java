package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vttcaseiro.character.SheetTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Modelos de ficha (PDF editavel) da mesa: {@code modelos/<id>.json} (o modelo) e {@code modelos/<id>.pdf}
 * (o PDF original, pra exportar a ficha preenchida).
 */
public class TemplateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dir;

    public TemplateStore(Path mesaRoot) {
        this.dir = mesaRoot.resolve("modelos");
    }

    public List<SheetTemplate> list() {
        List<SheetTemplate> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    SheetTemplate t = GSON.fromJson(Files.readString(p), SheetTemplate.class);
                    if (t != null) out.add(t.sanitize());
                } catch (IOException | RuntimeException ignored) {
                    // arquivo ilegivel: pula
                }
            });
        } catch (IOException ignored) {
            // sem pasta
        }
        out.sort(Comparator.comparing(SheetTemplate::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<SheetTemplate> list(String kind) {
        return list().stream().filter(t -> t.getKind().equals(kind)).toList();
    }

    public Optional<SheetTemplate> find(String id) {
        return list().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public void save(SheetTemplate t, byte[] pdf) throws IOException {
        Files.createDirectories(dir);
        String id = safeId(t.getId());
        AtomicFiles.writeString(dir.resolve(id + ".json"), GSON.toJson(t));
        if (pdf != null) AtomicFiles.writeBytes(dir.resolve(id + ".pdf"), pdf);
    }

    /** O PDF original do modelo (null se esta maquina nao tem: ex. o jogador recebeu so a ficha pela rede). */
    public byte[] pdf(String id) {
        if (id == null) return null;
        Path p = dir.resolve(safeId(id) + ".pdf");
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    public void delete(String id) throws IOException {
        String s = safeId(id);
        Files.deleteIfExists(dir.resolve(s + ".json"));
        Files.deleteIfExists(dir.resolve(s + ".pdf"));
    }

    /** So letras, numeros e "-" (o id vira nome de arquivo). */
    static String safeId(String id) {
        String s = id == null ? "" : id.replaceAll("[^A-Za-z0-9-]", "");
        if (s.isEmpty()) throw new IllegalArgumentException("Modelo sem id.");
        return s;
    }
}
