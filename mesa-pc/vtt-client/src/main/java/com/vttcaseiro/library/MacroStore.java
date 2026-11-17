package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.app.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Macros pessoais: um botao com nome que roda uma ou mais linhas do chat
 * ("/r 1d20+7", "/r 3d6+4", "Ataque furtivo!"). Fica em {@code macros.json}
 * na pasta da mesa, nesta maquina -- cada jogador tem as suas.
 */
public class MacroStore {
    private static final Log log = Log.of(MacroStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Uma macro: nome do botao + linhas do chat (uma por linha). */
    public static class Macro {
        private String name = "";
        private String commands = "";

        public Macro() {
        }

        public Macro(String name, String commands) {
            setName(name);
            setCommands(commands);
        }

        public String getName() { return name == null ? "" : name; }
        public void setName(String name) { this.name = name == null ? "" : name.trim(); }

        public String getCommands() { return commands == null ? "" : commands; }
        public void setCommands(String commands) { this.commands = commands == null ? "" : commands; }

        /** As linhas nao vazias, na ordem. */
        public List<String> lines() {
            List<String> out = new ArrayList<>();
            for (String line : getCommands().split("\\R")) {
                if (!line.isBlank()) out.add(line.trim());
            }
            return out;
        }
    }

    private final Path file;

    public MacroStore(Path mesaRoot) {
        this.file = mesaRoot.resolve("macros.json");
    }

    public List<Macro> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<Macro> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Macro>>() { }.getType());
            if (list == null) return new ArrayList<>();
            list.removeIf(m -> m == null || m.getName().isBlank());
            return new ArrayList<>(list);
        } catch (IOException | RuntimeException ex) {
            log.warn("Nao foi possivel ler as macros: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(List<Macro> macros) {
        try {
            AtomicFiles.writeString(file, GSON.toJson(macros));
        } catch (IOException ex) {
            log.warn("Nao foi possivel salvar as macros: " + ex.getMessage());
        }
    }
}
