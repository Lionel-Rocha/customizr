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
