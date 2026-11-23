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
            log.warn("Não foi possível listar cenas: " + ex.getMessage());
            return List.of();
        }
    }

    /** @throws IllegalArgumentException se o nome for vazio */
    public void saveMap(SceneModel scene, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome da cena não pode ser vazio.");
        }
        scene.setName(name.trim());
        repository.saveMap(scene, scene.getName());
        log.info("Cena salva: " + scene.getName());
    }

    public SceneModel loadMap(String name) throws IOException {
        return repository.loadMap(name);
    }

    /** Apaga a cena salva (o arquivo). Pins de outras cenas que abriam ela ficam so como informacao. */
    public void deleteMap(String name) throws IOException {
        if (name == null || !listMaps().contains(name)) throw new IOException("Cena não encontrada: " + name);
        repository.deleteMap(name);
        relinkPins(name, null);
        log.info("Cena apagada: " + name);
    }

    /**
     * Troca o nome da cena (o arquivo). Pins de outras cenas que abriam ela passam a abrir o nome novo.
     *
     * @return o nome com que ela ficou na lista (acentos e simbolos viram "_" no arquivo)
     * @throws IllegalArgumentException nome vazio ou ja usado por outra cena
     */
    public String renameMap(String oldName, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome da cena não pode ser vazio.");
        String target = MesaRepository.fileNameOf(newName.trim());
        if (target.equals(oldName)) return oldName;
        if (!listMaps().contains(oldName)) throw new IOException("Cena não encontrada: " + oldName);
        if (listMaps().contains(target)) throw new IllegalArgumentException("Já existe uma cena chamada \"" + target + "\".");
        SceneModel scene = repository.loadMap(oldName);
        scene.setName(newName.trim());
        repository.saveMap(scene, target);
        repository.deleteMap(oldName);
        relinkPins(oldName, target);
        log.info("Cena renomeada: " + oldName + " -> " + target);
        return target;
    }

    /** Copia a cena com outro nome (mesmo mapa, tokens, paredes...). */
    public String duplicateMap(String name, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome da cena não pode ser vazio.");
        String target = MesaRepository.fileNameOf(newName.trim());
        if (listMaps().contains(target)) throw new IllegalArgumentException("Já existe uma cena chamada \"" + target + "\".");
        SceneModel scene = repository.loadMap(name);
        scene.setName(newName.trim());
        repository.saveMap(scene, target);
        return target;
    }

    /** Pins que abriam {@code from} passam a abrir {@code to} (null = viram so informacao). */
    private void relinkPins(String from, String to) {
        for (String other : listMaps()) {
            try {
                SceneModel s = repository.loadMap(other);
                boolean changed = false;
                for (com.vttcaseiro.model.MapPin p : s.getPins()) {
                    if (from.equals(p.getLinkedScene())) {
                        p.setLinkedScene(to);
                        changed = true;
                    }
                }
                if (changed) repository.saveMap(s, other);
            } catch (IOException | RuntimeException ex) {
                log.warn("Não deu pra atualizar os pins de " + other + ": " + ex.getMessage());
            }
        }
    }
}
