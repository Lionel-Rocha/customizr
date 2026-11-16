package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Spell;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SpellRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class ClassSpellService {
    private final DndClassRepository dndClassRepository;
    private final SpellRepository spellRepository;

    public ClassSpellService(DndClassRepository dndClassRepository, SpellRepository spellRepository) {
        this.dndClassRepository = dndClassRepository;
        this.spellRepository = spellRepository;
    }

    @Transactional
    public String create(String className, String spellName){

        Spell spell = getSpellByName(spellName);
        DndClass dndClass = getClassByName(className);

        dndClass.addAvailableSpell(spell);
        dndClassRepository.save(dndClass);
        return "Spell adicionado à classe";
    }

    private Spell getSpellByName(String name){
        Spell spell = (Spell) spellRepository.findByNameContainingIgnoreCase(name);
        return spell;
    }

    private DndClass getClassByName(String name){
        DndClass dndClass = dndClassRepository.findByClassName(name);
        return dndClass;
    }
}
