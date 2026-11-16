package top.customizr.system.controllers;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.ChatMessageRequest;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.service.ChatService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/campaign/{campaignId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse post(
            @PathVariable UUID campaignId,
            @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return chatService.post(campaignId, request, loggedUser.getUsername());
    }

    @GetMapping("/campaign/{campaignId}")
    public List<ChatMessageResponse> history(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UserDetails loggedUser
    ) {
        return chatService.history(campaignId, before, limit, loggedUser.getUsername());
    }
}
