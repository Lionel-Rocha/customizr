package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pastas da lista de cenas do mestre (ex.: "Cidade", "Cidade/Taverna"), guardadas em {@code pastas-cenas.json} na
 * mesa. So organiza a lista: as cenas continuam onde estao, cada uma lembra a pasta dela. Caminho "" = raiz.
 */
public final class SceneFolders {
    public static final String FILE = "pastas-cenas.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_FOLDERS = 500;

    private List<String> folders = new ArrayList<>();
    private Map<String, String> sceneFolder = new LinkedHashMap<>();

    public static SceneFolders load(Path mesaRoot) {
        Path f = mesaRoot.resolve(FILE);
        try {
            if (Files.exists(f)) {
                SceneFolders s = GSON.fromJson(Files.readString(f), SceneFolders.class);
                if (s != null) {
                    if (s.folders == null) s.folders = new ArrayList<>();
                    if (s.sceneFolder == null) s.sceneFolder = new LinkedHashMap<>();
                    s.folders.removeIf(p -> p == null || !validPath(p));
                    return s;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // arquivo estragado: comeca sem pastas (as cenas continuam todas na raiz)
        }
        return new SceneFolders();
    }

    public void save(Path mesaRoot) throws IOException {
        AtomicFiles.writeString(mesaRoot.resolve(FILE), GSON.toJson(this));
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\") && name.strip().length() <= 60
                && !name.strip().equals(".") && !name.strip().equals("..");
    }

    private static boolean validPath(String path) {
        if (path.isEmpty()) return false;
        for (String part : path.split("/", -1)) if (!validName(part)) return false;
        return true;
    }

    public static String parentOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    public static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public List<String> folders() {
        return List.copyOf(folders);
    }

    /** Subpastas diretas de {@code parent} ("" = raiz), em ordem alfabetica. */
    public List<String> subfolders(String parent) {
        return folders.stream().filter(p -> parentOf(p).equals(parent))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Pasta da cena ("" se esta na raiz ou se a pasta dela sumiu). */
    public String folderOf(String scene) {
        String f = sceneFolder.get(scene);
        return f != null && folders.contains(f) ? f : "";
    }

    /** Cria {@code parent/name}; devolve o caminho. */
    public String addFolder(String parent, String name) {
        if (!validName(name)) throw new IllegalArgumentException("Nome de pasta inválido (sem \"/\", até 60 letras).");
        if (!parent.isEmpty() && !folders.contains(parent)) throw new IllegalArgumentException("Pasta não existe: " + parent);
        if (folders.size() >= MAX_FOLDERS) throw new IllegalArgumentException("Pastas demais.");
        String path = parent.isEmpty() ? name.strip() : parent + "/" + name.strip();
        if (folders.stream().anyMatch(p -> p.equalsIgnoreCase(path))) throw new IllegalArgumentException("Já existe essa pasta.");
        folders.add(path);
        return path;
    }

    /** Renomeia a pasta (e leva junto as subpastas e as cenas). Devolve o caminho novo. */
    public String renameFolder(String path, String newName) {
        if (!folders.contains(path)) throw new IllegalArgumentException("Pasta não existe: " + path);
        if (!validName(newName)) throw new IllegalArgumentException("Nome de pasta inválido (sem \"/\", até 60 letras).");
        String parent = parentOf(path);
        String target = parent.isEmpty() ? newName.strip() : parent + "/" + newName.strip();
        if (target.equals(path)) return path;
        if (folders.stream().anyMatch(p -> p.equalsIgnoreCase(target))) throw new IllegalArgumentException("Já existe essa pasta.");
        List<String> renamed = new ArrayList<>();
        for (String p : folders) renamed.add(moved(p, path, target));
        folders = renamed;
        sceneFolder.replaceAll((scene, f) -> moved(f, path, target));
        return target;
    }

    private static String moved(String p, String from, String to) {
        if (p.equals(from)) return to;
        if (p.startsWith(from + "/")) return to + p.substring(from.length());
        return p;
    }

    /** Apaga a pasta e as subpastas; as cenas dela sobem pra pasta de cima (nenhuma cena e apagada). */
    public void removeFolder(String path) {
        String parent = parentOf(path);
        folders.removeIf(p -> p.equals(path) || p.startsWith(path + "/"));
        sceneFolder.replaceAll((scene, f) -> f.equals(path) || f.startsWith(path + "/") ? parent : f);
        sceneFolder.values().removeIf(String::isEmpty);
    }

    /** Poe a cena na pasta ("" = raiz). */
    public void moveScene(String scene, String folder) {
        if (!folder.isEmpty() && !folders.contains(folder)) throw new IllegalArgumentException("Pasta não existe: " + folder);
        if (folder.isEmpty()) sceneFolder.remove(scene);
        else sceneFolder.put(scene, folder);
    }

    /** A cena mudou de nome: a pasta vai junto. */
    public void renameScene(String oldName, String newName) {
        String f = sceneFolder.remove(oldName);
        if (f != null) sceneFolder.put(newName, f);
    }
}
