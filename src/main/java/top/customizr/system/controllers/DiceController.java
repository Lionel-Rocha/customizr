package top.customizr.system.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.RollRequest;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.RollResult;

/** Rolagem avulsa (sem ir pro chat). Pra registrar no chat, use POST /chat/campaign/{id} com "/r ...". */
@RestController
@RequestMapping("/dice")
public class DiceController {

    @PostMapping("/roll")
    public RollResult roll(@RequestBody RollRequest request) {
        try {
            return DiceRoller.roll(request == null ? null : request.expression());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
