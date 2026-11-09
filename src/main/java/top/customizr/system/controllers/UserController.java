package top.customizr.system.controllers;

import org.springframework.web.bind.annotation.*;
import top.customizr.system.model.User;

@RestController
@RequestMapping("user")
public class UserController {

    @PostMapping("/create")
    public void create(@RequestBody User user){
        System.out.println("CREATE " + user);
    }

}
