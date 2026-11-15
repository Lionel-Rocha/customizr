package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.SpellCreationRequest;
import top.customizr.system.model.Duration;
import top.customizr.system.model.Components;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.Time;
import top.customizr.system.service.SpellService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/spell")
public class SpellController {

    private final SpellService spellService;

    public SpellController(SpellService spellService) {
        this.spellService = spellService;
    }

    @PostMapping("/create")
    public ResponseEntity<Spell> createSpell(@RequestBody SpellCreationRequest request) {
        Spell spell = spellService.create(
                request.name(),
                parseEnum(SpellSchool.class, request.spellSchool(), "spellSchool").name(),
                parseEnum(SpellLevel.class, request.level(), "level").name(),
                parseEnum(Time.class, request.castingTime(), "castingTime").name(),
                request.rangeFt(),
                parseComponents(request.components()),
                toDuration(request),
                request.sourceId(),
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(spell);
    }

    @GetMapping("/{id}")
    public Spell getSpell(@PathVariable("id") UUID id) {
        return spellService.read(id);
    }

    @GetMapping("/all")
    public List<Spell> allSpells() {
        return spellService.listAll();
    }

    @GetMapping("/search")
    public List<Spell> search(@RequestParam String name) {
        return spellService.search(name);
    }

    @PutMapping("/update/{id}")
    public Spell updateSpell(@PathVariable("id") UUID id, @RequestBody SpellCreationRequest request) {
        Set<Components> components = Arrays.stream(parseComponents(request.components()))
                .map(Components::valueOf)
                .collect(Collectors.toSet());
        return spellService.update(
                id,
                request.name(),
                parseEnum(SpellSchool.class, request.spellSchool(), "spellSchool"),
                parseEnum(SpellLevel.class, request.level(), "level"),
                parseEnum(Time.class, request.castingTime(), "castingTime"),
                request.rangeFt(),
                components,
                toDuration(request),
                request.sourceId(),
                request.description()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpell(@PathVariable("id") UUID id) {
        spellService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String[] parseComponents(String[] components) {
        return Arrays.stream(components == null ? new String[0] : components)
                .map(component -> parseEnum(Components.class, component, "components").name())
                .distinct()
                .toArray(String[]::new);
    }

    private Duration toDuration(SpellCreationRequest request) {
        if (request.duration() == null) {
            throw badRequest("duration is required");
        }
        try {
            return request.duration().toModel();
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required");
        }
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }
        throw badRequest("Invalid " + field);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
