package top.customizr.system.controllers;

import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.DndClassResponse;
import top.customizr.system.dto.ImportClassesResponse;
import top.customizr.system.service.ClassService;

import java.util.List;

@RestController
@RequestMapping("/class")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportClassesResponse importClasses(@RequestBody JsonNode payload) {
        return classService.importClasses(payload);
    }

    @GetMapping("/all")
    public List<DndClassResponse> listClasses() {
        return classService.getAllClasses();
    }
}
