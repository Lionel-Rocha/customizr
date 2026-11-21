package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Spell;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SpellRepository;

@Service
public class ClassSpellService {
    private final DndClassRepository dndClassRepository;
    private final SpellRepository spellRepository;

    public ClassSpellService(DndClassRepository dndClassRepository, SpellRepository spellRepository) {
        this.dndClassRepository = dndClassRepository;
        this.spellRepository = spellRepository;
    }

    @Transactional
    public String create(String className, String spellName) {
        String normalizedClassName = normalizeRequired(className, "Class name is required");
        String normalizedSpellName = normalizeRequired(spellName, "Spell name is required");

        var matchingSpells = spellRepository.findAllByNameIgnoreCase(normalizedSpellName);
        if (matchingSpells.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Magia não encontrada");
        }
        if (matchingSpells.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Spell name is ambiguous; use a spell name unique across sources"
            );
        }
        Spell spell = matchingSpells.getFirst();

        DndClass dndClass = dndClassRepository.findByClassName(normalizedClassName);
        if (dndClass == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe não encontrada");
        }

        dndClass.addAvailableSpell(spell);
        dndClassRepository.save(dndClass);
        return "Spell adicionado à classe";
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
