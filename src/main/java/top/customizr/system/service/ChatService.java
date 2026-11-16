package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ChatMessageRequest;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ChatMessage;
import top.customizr.system.model.ChatMessageType;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.InlineRolls;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.ChatMessageRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Chat da campanha com historico. Comandos: {@code /r 1d20+5 Ataque} (rolagem), {@code /me acao},
 * {@code /w Nome mensagem} (sussurro) e {@code [[1d8+3]]} no meio do texto.
 */
@Service
public class ChatService {

    public static final int DEFAULT_PAGE = 50;
    public static final int MAX_PAGE = 200;

    private final ChatMessageRepository chatMessageRepository;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            CampaignRepository campaignRepository,
            CharacterRepository characterRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.campaignRepository = campaignRepository;
        this.characterRepository = characterRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChatMessageResponse post(UUID campaignId, ChatMessageRequest request, String username) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw badRequest("Message is empty");
        }
        String text = InlineRolls.sanitize(request.text().strip());
        if (text.length() > ChatMessage.MAX_LENGTH) {
            throw badRequest("Message is too long (max " + ChatMessage.MAX_LENGTH + " characters)");
        }
        Campaign campaign = findCampaign(campaignId);
        User author = findUser(username);
        requireCampaignMember(campaign, author);
        String speaker = speakerName(campaign, author, request.characterId());

        ChatMessage message;
        try {
            message = buildMessage(campaign, author, speaker, text);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    /** Mensagens em ordem de envio. {@code before} = pagina anterior (mais antigas que esse instante). */
    @Transactional
    public List<ChatMessageResponse> history(UUID campaignId, LocalDateTime before, Integer limit, String username) {
        Campaign campaign = findCampaign(campaignId);
        User reader = findUser(username);
        requireCampaignMember(campaign, reader);
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(MAX_PAGE, limit));
        List<ChatMessage> page = before == null
                ? chatMessageRepository.findByCampaignOrderByCreatedAtDesc(campaign, PageRequest.of(0, size))
                : chatMessageRepository.findByCampaignAndCreatedAtBeforeOrderByCreatedAtDesc(campaign, before, PageRequest.of(0, size));
        List<ChatMessageResponse> out = new ArrayList<>();
        for (ChatMessage message : page) {
            if (message.isVisibleTo(reader)) {
                out.add(ChatMessageResponse.from(message));
            }
        }
        Collections.reverse(out);
        return out;
    }

    private ChatMessage buildMessage(Campaign campaign, User author, String speaker, String text) {
        String lower = text.toLowerCase();
        if (lower.startsWith("/r ") || lower.startsWith("/roll ")) {
            String rest = text.substring(text.indexOf(' ') + 1).strip();
            int space = rest.indexOf(' ');
            String expression = space < 0 ? rest : rest.substring(0, space);
            String label = space < 0 ? "" : rest.substring(space + 1).strip();
            RollResult roll = DiceRoller.roll(expression);
            String content = (label.isEmpty() ? "" : label + ": ") + roll.detail()
                    + (roll.isCriticalHit() ? " (CRITICAL!)" : roll.isCriticalFail() ? " (critical fail)" : "");
            return new ChatMessage(campaign, author, speaker, ChatMessageType.ROLL, content, null, roll);
        }
        if (lower.startsWith("/me ")) {
            return new ChatMessage(campaign, author, speaker, ChatMessageType.EMOTE,
                    InlineRolls.expand(text.substring(4).strip()), null, null);
        }
        if (lower.startsWith("/w ")) {
            String rest = text.substring(3).strip();
            User recipient = findWhisperRecipient(campaign, rest);
            String body = rest.substring(recipient.getName().length()).strip();
            if (body.isEmpty()) {
                throw new IllegalArgumentException("Whisper is empty");
            }
            return new ChatMessage(campaign, author, speaker, ChatMessageType.WHISPER,
                    InlineRolls.expand(body), recipient, null);
        }
        return new ChatMessage(campaign, author, speaker, ChatMessageType.MESSAGE, InlineRolls.expand(text), null, null);
    }

    /** "/w Nome Composto oi" -> procura o nome mais longo da campanha que comeca o texto. */
    private User findWhisperRecipient(Campaign campaign, String rest) {
        List<User> members = new ArrayList<>();
        members.add(campaign.getMaster());
        membershipRepository.findByCampaignId(campaign.getId())
                .forEach(membership -> userRepository.findById(membership.getUserId()).ifPresent(members::add));
        User best = null;
        for (User user : members) {
            String name = user.getName();
            boolean matches = rest.regionMatches(true, 0, name, 0, name.length())
                    && (rest.length() == name.length() || java.lang.Character.isWhitespace(rest.charAt(name.length())));
            if (matches && (best == null || name.length() > best.getName().length())) {
                best = user;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Nobody with that name in this campaign");
        }
        return best;
    }

    private String speakerName(Campaign campaign, User author, UUID characterId) {
        if (characterId == null) {
            return null;
        }
        top.customizr.system.model.Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        boolean master = campaign.getMaster().getUserId().equals(author.getUserId());
        if (!character.getCampaign().getId().equals(campaign.getId())
                || (!master && !character.getOwner().getUserId().equals(author.getUserId()))) {
            throw new AccessDeniedException("You cannot speak as this character");
        }
        return character.getName();
    }

    private void requireCampaignMember(Campaign campaign, User user) {
        if (!campaign.getMaster().getUserId().equals(user.getUserId())
                && membershipRepository.findByCampaignIdAndUserId(campaign.getId(), user.getUserId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this campaign");
        }
    }

    private Campaign findCampaign(UUID campaignId) {
        if (campaignId == null) {
            throw badRequest("Campaign ID is required");
        }
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private User findUser(String username) {
        return userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
