package top.customizr.system.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** /mesa abre a tela de jogo (arquivo estatico em static/mesa/index.html). */
@Controller
public class MesaPageController {

    @GetMapping({"/mesa", "/mesa/"})
    public String mesa() {
        return "redirect:/mesa/index.html";
    }
}
