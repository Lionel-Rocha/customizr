package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.ClassSpellRequest;
import top.customizr.system.service.ClassSpellService;

@RestController
@RequestMapping("/class-spell")
public class ClassSpellController {

    private final ClassSpellService classSpellService;

    public ClassSpellController(ClassSpellService classSpellService) {
        this.classSpellService = classSpellService;
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    public String addSpellToClass(@RequestBody ClassSpellRequest request) {
        classSpellService.create(request.className(), request.spellName());
        return "Adicionado " + request.spellName() + " à classe " + request.className() + ".";
    }

}
