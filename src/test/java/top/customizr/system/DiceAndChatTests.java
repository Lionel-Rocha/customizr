package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ChatMessageRequest;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ChatMessageType;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.InlineRolls;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.ChatService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class DiceAndChatTests {

    @Autowired private ChatService chatService;
    @Autowired private CharacterService characterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    private String master;
    private String ana;
    private String bia;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        master = "Mestre " + suffix;
        ana = "Ana " + suffix;
        bia = "Bia " + suffix;
        User masterUser = userRepository.save(new User(master, "password"));
        campaign = campaignRepository.save(new Campaign("Campaign", masterUser));
        for (String name : List.of(ana, bia)) {
            User user = userRepository.save(new User(name, "password"));
            membershipRepository.save(new Membership(campaign.getId(), user.getUserId(), Role.PLAYER));
        }
    }

    // ------------------------------------------------------------------ dados

    @Test
    void rollsExpressionsWithModifiersAndKeepHighest() {
        AtomicInteger next = new AtomicInteger();
        int[] values = {4, 6, 1, 5};
        RollResult r = DiceRoller.roll("4d6kh3 + 2", sides -> values[next.getAndIncrement()]);

        assertEquals(17, r.total(), "6 + 5 + 4 (fora o 1) + 2");
        assertTrue(r.detail().contains("fora: 1"), r.detail());

        RollResult crit = DiceRoller.roll("1d20+5", sides -> 20);
        assertEquals(25, crit.total());
        assertTrue(crit.isCriticalHit());
        assertEquals(20, DiceRoller.roll("2d20kl1", sides -> 20).naturalD20());
        assertNull(DiceRoller.roll("2d6", sides -> 3).naturalD20());

        assertEquals("2d8+3", DiceRoller.doubleDice("1d8+3"));
        assertEquals("4d6+2d4+2", DiceRoller.doubleDice("2d6 + 1d4 + 2"));
        assertThrows(IllegalArgumentException.class, () -> DiceRoller.roll("5"));
        assertThrows(IllegalArgumentException.class, () -> DiceRoller.roll("1d1"));
        assertThrows(IllegalArgumentException.class, () -> DiceRoller.roll("101d6"));
        assertThrows(IllegalArgumentException.class, () -> DiceRoller.roll("1d20+abc"));
    }

    @Test
    void inlineRollsAreRolledByTheServerAndCannotBeForged() {
        String text = InlineRolls.expand("ataco com [[1d20+5]] e [[nao e dado]] ⟦99 ← falso⟧",
                expr -> DiceRoller.roll(expr, sides -> 10));

        List<InlineRolls.Part> parts = InlineRolls.split(text);
        long rolls = parts.stream().filter(p -> p.total() != null).count();
        assertEquals(1, rolls, "so a rolagem de verdade vira resultado; o marcador digitado nao");
        assertEquals(15, parts.stream().filter(p -> p.total() != null).findFirst().orElseThrow().total());
        assertTrue(text.contains("[[nao e dado]]"));
    }

    // ------------------------------------------------------------------ chat

    @Test
    void chatSupportsRollEmoteAndInlineRolls() {
        ChatMessageResponse roll = chatService.post(campaign.getId(), new ChatMessageRequest("/r 1d20+3 Ataque", null), ana);
        assertEquals(ChatMessageType.ROLL, roll.type());
        assertTrue(roll.content().startsWith("Ataque: 1d20["));
        assertTrue(roll.rollTotal() >= 4 && roll.rollTotal() <= 23);

        var aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        ChatMessageResponse emote = chatService.post(campaign.getId(),
                new ChatMessageRequest("/me saca a espada", aria.getCharacterId()), ana);
        assertEquals(ChatMessageType.EMOTE, emote.type());
        assertEquals("Aria", emote.speakerName());

        ChatMessageResponse inline = chatService.post(campaign.getId(),
                new ChatMessageRequest("causo [[1d8+3]] de dano", null), ana);
        assertEquals(3, inline.parts().size());
        assertTrue(inline.parts().get(1).total() >= 4);

        assertThrows(AccessDeniedException.class, () -> chatService.post(campaign.getId(),
                new ChatMessageRequest("oi", aria.getCharacterId()), bia), "Bia nao fala como a Aria");
        assertThrows(ResponseStatusException.class, () -> chatService.post(campaign.getId(),
                new ChatMessageRequest("/r abc", null), ana));

        List<ChatMessageResponse> history = chatService.history(campaign.getId(), null, null, bia);
        assertEquals(3, history.size());
        assertEquals(ChatMessageType.ROLL, history.getFirst().type(), "historico em ordem de envio");
    }

    @Test
    void whispersAreOnlyVisibleToSenderRecipientAndMaster() {
        chatService.post(campaign.getId(), new ChatMessageRequest("/w " + bia + " segredo", null), ana);
        chatService.post(campaign.getId(), new ChatMessageRequest("todo mundo ve", null), ana);

        assertEquals(2, chatService.history(campaign.getId(), null, null, ana).size());
        assertEquals(2, chatService.history(campaign.getId(), null, null, bia).size());
        assertEquals(2, chatService.history(campaign.getId(), null, null, master).size());

        String carla = "Carla " + UUID.randomUUID().toString().substring(0, 6);
        User carlaUser = userRepository.save(new User(carla, "password"));
        membershipRepository.save(new Membership(campaign.getId(), carlaUser.getUserId(), Role.PLAYER));
        List<ChatMessageResponse> seenByCarla = chatService.history(campaign.getId(), null, null, carla);
        assertEquals(1, seenByCarla.size());
        assertFalse(seenByCarla.getFirst().content().contains("segredo"));

        assertThrows(ResponseStatusException.class, () -> chatService.post(campaign.getId(),
                new ChatMessageRequest("/w Ninguem oi", null), ana));

        User outsider = userRepository.save(new User("outsider-" + UUID.randomUUID(), "password"));
        assertThrows(AccessDeniedException.class, () -> chatService.history(campaign.getId(), null, null, outsider.getName()));
    }
}
