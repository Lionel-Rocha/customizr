package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vttcaseiro.app.Log;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista de objetos num arquivo JSON da mesa (tabelas aleatorias, fila de
 * cenas...). Gravacao atomica; arquivo ausente ou estragado = lista vazia.
 */
public class JsonListStore<T> {
    private static final Log log = Log.of(JsonListStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Type listType;

    public JsonListStore(Path mesaRoot, String fileName, Type listType) {
        this.file = mesaRoot.resolve(fileName);
        this.listType = listType;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    public List<T> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<T> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), listType);
            if (list == null) return new ArrayList<>();
            list.removeIf(java.util.Objects::isNull);
            return new ArrayList<>(list);
        } catch (IOException | RuntimeException ex) {
            log.warn("Não foi possível ler " + file.getFileName() + ": " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(List<T> items) {
        try {
            AtomicFiles.writeString(file, GSON.toJson(items));
        } catch (IOException ex) {
            log.warn("Não foi possível salvar " + file.getFileName() + ": " + ex.getMessage());
        }
    }
}
