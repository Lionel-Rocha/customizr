package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.util.List;

/**
 * A mesa atual (nome + cenas salvas). Fichas moram no {@link CharacterService}
 * -- separei os dois porque sao duas areas de responsabilidade diferentes,
 * mesmo os dois conversando com o mesmo {@link MesaRepository} por baixo.
 */
public class MesaService {
    private static final Log log = Log.of(MesaService.class);

    private final MesaRepository repository;

    public MesaService(MesaRepository repository) {
        this.repository = repository;
    }

    public String getName() {
        return repository.getName();
    }

    public MesaRepository getRepository() {
        return repository;
    }

    public List<String> listMaps() {
        try {
            return repository.listMaps();
        } catch (IOException ex) {
            log.warn("Nao foi possivel listar cenas: " + ex.getMessage());
            return List.of();
        }
    }

    /** @throws IllegalArgumentException se o nome for vazio */
    public void saveMap(SceneModel scene, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome da cena nao pode ser vazio.");
        }
        scene.setName(name.trim());
        repository.saveMap(scene, scene.getName());
        log.info("Cena salva: " + scene.getName());
    }

    public SceneModel loadMap(String name) throws IOException {
        return repository.loadMap(name);
    }
}
