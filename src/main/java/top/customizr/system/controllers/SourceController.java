package top.customizr.system.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import top.customizr.system.dto.SourceRequest;
import top.customizr.system.dto.SourceResponse;
import top.customizr.system.model.Source;
import top.customizr.system.repository.SourceRepository;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/source")
public class SourceController {

    private final SourceRepository sourceRepository;

    public SourceController(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @PostMapping("/create")
    public ResponseEntity<String> createSource(@RequestBody SourceRequest request, @AuthenticationPrincipal UserDetails loggedUser){
        if (!loggedUser.isCredentialsNonExpired()){
            return ResponseEntity.status(403).body("Usuário impossibilitado de fazer alterações.");
        }

        Source source = new Source(request.name(), request.code());
        sourceRepository.save(source);
        String response = "Source " + source.getName() + " criado com sucesso";
        return ResponseEntity.status(200).body(response);
    }

    @GetMapping("/{code}")
    public Optional<Source> getSource(@PathVariable String code){
        Optional<Source> requestedSource = sourceRepository.getSourceByCode(code);
        return requestedSource;
    }

    @GetMapping("/all")
    public List<SourceResponse> listSources(){
        return sourceRepository.findAll().stream()
                .map(SourceResponse::from)
                .toList();
    }

}
