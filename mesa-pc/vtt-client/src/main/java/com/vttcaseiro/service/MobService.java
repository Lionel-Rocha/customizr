package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Espelha CharacterService, so que para MobSheet -- mesma logica de "dono
 * do que esta aberto nesta sessao + ponte com o MesaRepository", so que
 * pra monstros/NPCs em vez de fichas de jogador. Separado de proposito em
 * vez de generalizar os dois services num so: as regras de negocio de
 * criar um monstro (sem trait de exemplo, CR inicial padrao) sao
 * diferentes das de criar uma ficha de jogador.
 */
public class MobService {
    private static final Log log = Log.of(MobService.class);

    private final MesaRepository repository;
    private final Map<String, MobSheet> openMobs = new LinkedHashMap<>();

    public MobService(MesaRepository repository) {
        this.repository = repository;
    }

    /**
     * @throws IllegalArgumentException se o nome for vazio
     * @throws IOException              se nao conseguir salvar
     */
    public MobSheet create(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do monstro nao pode ser vazio.");
        }
        MobSheet mob = new MobSheet(name.trim());
        save(mob);
        return mob;
    }

    public void save(MobSheet mob) throws IOException {
        repository.saveMob(mob, mob.getName());
        openMobs.put(mob.getId(), mob);
        log.info("Monstro salvo: " + mob.getName());
    }

    public MobSheet open(String name) throws IOException {
        MobSheet mob = repository.loadMob(name);
        openMobs.put(mob.getId(), mob);
        return mob;
    }

    public Optional<MobSheet> findOpen(String mobId) {
        return Optional.ofNullable(openMobs.get(mobId));
    }

    /** Pelo id: cache primeiro, depois os arquivos da mesa (mesmo jeito do
     * CharacterService.findById). */
    public Optional<MobSheet> findById(String mobId) {
        if (mobId == null) return Optional.empty();
        Optional<MobSheet> open = findOpen(mobId);
        if (open.isPresent()) return open;
        for (String name : list()) {
            try {
                MobSheet mob = open(name);
                if (mobId.equals(mob.getId())) return Optional.of(mob);
            } catch (IOException | RuntimeException ignored) {
                // arquivo ilegivel: pula
            }
        }
        return Optional.empty();
    }

    public List<String> list() {
        try {
            return repository.listMobs();
        } catch (IOException ex) {
            log.warn("Nao foi possivel listar monstros: " + ex.getMessage());
            return List.of();
        }
    }
}
