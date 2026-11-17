package top.customizr.system.controllers;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.*;
import top.customizr.system.model.User;
import top.customizr.system.repository.UserRepository;

import java.util.UUID;

@RestController
@RequestMapping("user")
public class UserController {

    private UserRepository userRepository;

    public UserController(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    /** Quem esta logado (a tela da mesa usa pra saber quais tokens sao seus). */
    @GetMapping("/me")
    public User me(@org.springframework.security.core.annotation.AuthenticationPrincipal
                   org.springframework.security.core.userdetails.UserDetails loggedUser) {
        return userRepository.findByName(loggedUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado."));
    }

    @PostMapping("/create")
    public User create(@RequestBody User user){
        userRepository.save(user);
        return user;
    }

    @GetMapping("/{userId}")
    public User getUser(@PathVariable UUID userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado."));
        return user;
    }
}
