package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.Components;
import top.customizr.system.model.Duration;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.Source;
import top.customizr.system.model.Time;
import top.customizr.system.repository.CharacterClassRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpellService {

    private final SpellRepository spellRepository;
    private final SourceRepository sourceRepository;
    private final DndClassRepository dndClassRepository;
    private final CharacterClassRepository characterClassRepository;

    public SpellService(
            SpellRepository spellRepository,
            SourceRepository sourceRepository,
            DndClassRepository dndClassRepository,
            CharacterClassRepository characterClassRepository
    ) {
        this.spellRepository = spellRepository;
        this.sourceRepository = sourceRepository;
        this.dndClassRepository = dndClassRepository;
        this.characterClassRepository = characterClassRepository;
    }

    @Transactional
    public Spell create(
            String name,
            String spellSchool,
            String spellLevel,
            String spellCastingTime,
            int rangeFt,
            String[] spellComponents,
            Duration duration,
            UUID sourceId,
            String description
    ) {
        SpellSchool school = SpellSchool.valueOf(spellSchool);
        SpellLevel level = SpellLevel.valueOf(spellLevel);
        Time castingTime = Time.valueOf(spellCastingTime);
        Source source = findSource(sourceId);

        Set<Components> components = Arrays.stream(spellComponents == null ? new String[0] : spellComponents)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> parseEnum(Components.class, s, "components"))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Components.class)));

        ensureNameAvailable(name, sourceId, null);
        Spell spell = newSpell(
                name, school, level, castingTime, rangeFt, components, duration, source, description
        );
        return spellRepository.save(spell);
    }

    @Transactional
    public Spell read(UUID spellId) {
        return findSpell(spellId);
    }

    @Transactional
    public List<Spell> listAll() {
        return spellRepository.findAll();
    }

    @Transactional
    public List<Spell> search(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("Spell search name is required");
        }
        return spellRepository.findByNameContainingIgnoreCase(name.trim());
    }

    @Transactional
    public Spell update(
            UUID spellId,
            String name,
            SpellSchool school,
            SpellLevel level,
            Time castingTime,
            int rangeFt,
            Set<Components> components,
            Duration duration,
            UUID sourceId,
            String description
    ) {
        Spell spell = findSpell(spellId);
        Source source = findSource(sourceId);
        ensureNameAvailable(name, sourceId, spellId);
        try {
            spell.update(name, school, level, castingTime, rangeFt, components, duration, source, description);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return spellRepository.save(spell);
    }

    @Transactional
    public void delete(UUID spellId) {
        Spell spell = findSpell(spellId);
        if (dndClassRepository.existsByAvailableSpells_SpellId(spellId)
                || characterClassRepository.existsByPreparedSpells_SpellId(spellId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spell is used by a class or character sheet");
        }
        spellRepository.delete(spell);
    }

    private Spell newSpell(
            String name,
            SpellSchool school,
            SpellLevel level,
            Time castingTime,
            int rangeFt,
            Set<Components> components,
            Duration duration,
            Source source,
            String description
    ) {
        try {
            return new Spell(name, school, level, castingTime, rangeFt, components, duration, source, description);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private Spell findSpell(UUID spellId) {
        if (spellId == null) {
            throw badRequest("Spell ID is required");
        }
        return spellRepository.findById(spellId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spell not found"));
    }

    private Source findSource(UUID sourceId) {
        if (sourceId == null) {
            throw badRequest("Source ID is required");
        }
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
    }

    private void ensureNameAvailable(String name, UUID sourceId, UUID currentSpellId) {
        if (name == null || name.isBlank()) {
            throw badRequest("Spell name is required");
        }
        spellRepository.findByNameAndSource_Id(name.trim(), sourceId)
                .filter(existing -> !existing.getSpellId().equals(currentSpellId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "A spell with this name already exists for the source"
                    );
                });
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String field) {
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw badRequest("Invalid " + field);
    }
}
