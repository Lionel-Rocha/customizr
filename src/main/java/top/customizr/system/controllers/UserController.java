package top.customizr.system.controllers;

import org.springframework.web.bind.annotation.*;
import top.customizr.system.model.User;
import top.customizr.system.repository.UserRepository;

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

}
