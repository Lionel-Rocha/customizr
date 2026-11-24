package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.character.prerequisite.Prerequisite;
import com.vttcaseiro.character.prerequisite.PrerequisiteAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cofre de personagens: as fichas do JOGADOR guardadas no computador dele, fora de qualquer mesa. Leva o mesmo
 * personagem pra mesas diferentes (de mestres diferentes) sem ninguem importar arquivo. Cada ficha fica numa pasta
 * com o JSON e as imagens que ela usa (retrato, paginas do modelo de PDF) -- os ids das imagens sao o hash do
 * conteudo, entao chegam iguais em qualquer mesa.
 */
public class CharacterVault {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Prerequisite.class, new PrerequisiteAdapter())
            .create();

    /** Uma ficha do cofre (pra lista). */
    public record Entry(String id, String name, String summary, Path folder) {
    }

    private final Path root;

    public CharacterVault(Path root) {
        this.root = root;
    }

    /** O cofre de quem esta usando este computador. */
    public static CharacterVault mine() {
        return new CharacterVault(Path.of(System.getProperty("user.home"), ".vttcaseiro", "cofre"));
    }

    public Path getRoot() {
        return root;
    }

    /** Guarda (ou atualiza) a ficha e as imagens dela. {@code images} le os bytes de um id de imagem (null = sem). */
    public Path save(CharacterSheet sheet, ImageReader images) throws IOException {
        Path folder = folderFor(sheet.getId());
        if (folder == null) folder = root.resolve(safe(sheet.getName()) + "-" + sheet.getId().substring(0, Math.min(8, sheet.getId().length())));
        Files.createDirectories(folder.resolve("imagens"));
        AtomicFiles.writeString(folder.resolve("ficha.json"), GSON.toJson(sheet));
        if (images != null) {
            for (String id : imageIds(sheet)) {
                Path f = folder.resolve("imagens").resolve(ImageAssetStore.safeAssetId(id));
                if (Files.exists(f)) continue;
                try {
                    byte[] b = images.read(id);
                    if (b != null) AtomicFiles.writeBytes(f, b);
                } catch (IOException ignored) {
                    // imagem que esta mesa nao tem: a ficha vai sem ela
                }
            }
        }
        return folder;
    }

    /** Esta ficha (pelo id) ja esta no cofre? */
    public boolean contains(String sheetId) {
        return folderFor(sheetId) != null;
    }

    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.filter(Files::isDirectory).sorted().toList()) {
                CharacterSheet s = read(d);
                if (s == null) continue;
                String cls = s.getClassName() == null || s.getClassName().isBlank() ? "" : s.getClassName() + " ";
                out.add(new Entry(s.getId(), s.getName(), (cls + "nível " + s.getLevel()).strip(), d));
            }
        } catch (IOException ignored) {
            // cofre ilegivel: lista vazia
        }
        return out;
    }

    /** A ficha e as imagens dela (id -> bytes) pra levar pra uma mesa. */
    public CharacterSheet load(Entry e, java.util.Map<String, byte[]> imagesOut) throws IOException {
        CharacterSheet s = read(e.folder());
        if (s == null) throw new IOException("A ficha do cofre está estragada: " + e.folder());
        Path imgs = e.folder().resolve("imagens");
        if (imagesOut != null && Files.isDirectory(imgs)) {
            try (Stream<Path> files = Files.list(imgs)) {
                for (Path f : files.toList()) imagesOut.put(f.getFileName().toString(), Files.readAllBytes(f));
            }
        }
        return s;
    }

    public void delete(Entry e) throws IOException {
        if (!e.folder().startsWith(root)) return;
        try (Stream<Path> walk = Files.walk(e.folder())) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    /** Le os bytes de uma imagem pelo id. */
    public interface ImageReader {
        byte[] read(String assetId) throws IOException;
    }

    static Set<String> imageIds(CharacterSheet sheet) {
        Set<String> ids = new LinkedHashSet<>();
        if (sheet.getPortraitImageAssetId() != null) ids.add(sheet.getPortraitImageAssetId());
        if (sheet.getTemplate() != null) {
            for (SheetTemplate.Page p : sheet.getTemplate().getPages()) if (p.assetId != null) ids.add(p.assetId);
        }
        ids.removeIf(id -> !ImageAssetStore.isValidAssetId(id));
        return ids;
    }

    private Path folderFor(String id) {
        if (id == null || !Files.isDirectory(root)) return null;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.filter(Files::isDirectory).toList()) {
                CharacterSheet s = read(d);
                if (s != null && id.equals(s.getId())) return d;
            }
        } catch (IOException ignored) {
            // sem cofre
        }
        return null;
    }

    private static CharacterSheet read(Path folder) {
        Path f = folder.resolve("ficha.json");
        if (!Files.exists(f)) return null;
        try {
            return GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), CharacterSheet.class);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static String safe(String name) {
        String s = name == null ? "ficha" : name.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        return s.isEmpty() ? "ficha" : s.length() > 40 ? s.substring(0, 40) : s;
    }
}
