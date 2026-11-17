package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.model.Handout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Diario do mestre: {@code handouts.json} na pasta da mesa. */
public class HandoutStore {
    private static final Log log = Log.of(HandoutStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public HandoutStore(Path mesaRoot) {
        this.file = mesaRoot.resolve("handouts.json");
    }

    public List<Handout> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<Handout> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Handout>>() { }.getType());
            if (list == null) return new ArrayList<>();
            list.removeIf(h -> h == null || h.getId() == null);
            return new ArrayList<>(list);
        } catch (IOException | RuntimeException ex) {
            log.warn("Nao foi possivel ler o diario: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(List<Handout> handouts) {
        try {
            AtomicFiles.writeString(file, GSON.toJson(handouts));
        } catch (IOException ex) {
            log.warn("Nao foi possivel salvar o diario: " + ex.getMessage());
        }
    }
}
