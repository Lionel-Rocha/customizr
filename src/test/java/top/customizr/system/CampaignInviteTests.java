package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import top.customizr.system.dto.InviteResponse;
import top.customizr.system.dto.UpdateCampaignRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CampaignService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Convite: so o mestre convida, o jogador ve os convites e aceita; so o mestre muda ou apaga a campanha. */
@SpringBootTest
@Transactional
class CampaignInviteTests {

    @Autowired private CampaignService campaignService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;

    private String master;
    private String player;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        User masterUser = userRepository.save(new User(master, "password"));
        userRepository.save(new User(player, "password"));
        campaign = campaignRepository.save(new Campaign("Mina Perdida", masterUser));
    }

    private static UserDetails as(String name) {
        return org.springframework.security.core.userdetails.User.withUsername(name).password("x").roles("USER").build();
    }

    @Test
    void playerSeesTheInviteAcceptsItAndJoins() {
        assertEquals("Convite enviado para " + player, campaignService.invite(campaign.getId(), Role.MASTER, as(master), player));
        assertTrue(campaignService.invite(campaign.getId(), Role.PLAYER, as(master), player).contains("pendente"),
                "nao duplica o convite");

        List<InviteResponse> invites = campaignService.myInvites(as(player));
        assertEquals(1, invites.size());
        assertEquals("Mina Perdida", invites.get(0).campaignName());
        assertEquals(master, invites.get(0).masterName());
        assertEquals(Role.PLAYER, invites.get(0).role(), "ninguem vira mestre por convite");

        campaignService.acceptInvite(campaign.getId(), as(player));
        assertTrue(campaignService.myInvites(as(player)).isEmpty());
        User p = userRepository.findByName(player).orElseThrow();
        assertTrue(membershipRepository.findByCampaignIdAndUserId(campaign.getId(), p.getUserId()).isPresent());
        assertTrue(campaignService.invite(campaign.getId(), Role.PLAYER, as(master), player).contains("já está"));
    }

    @Test
    void declinedInviteGoesAway() {
        campaignService.invite(campaign.getId(), Role.PLAYER, as(master), player);
        campaignService.declineInvite(campaign.getId(), as(player));
        assertTrue(campaignService.myInvites(as(player)).isEmpty());
    }

    @Test
    void onlyTheMasterInvitesChangesOrDeletes() {
        assertThrows(AccessDeniedException.class,
                () -> campaignService.invite(campaign.getId(), Role.PLAYER, as(player), master));
        UpdateCampaignRequest rename = new UpdateCampaignRequest(Optional.of("Roubada"), Optional.empty(), Optional.empty());
        assertThrows(AccessDeniedException.class, () -> campaignService.updateCampaign(rename, campaign.getId(), as(player)));
        assertThrows(AccessDeniedException.class, () -> campaignService.deleteCampaign(campaign.getId(), as(player)));
        assertTrue(campaignRepository.findById(campaign.getId()).isPresent(), "a campanha continua la");
        campaignService.updateCampaign(rename, campaign.getId(), as(master));
        assertEquals("Roubada", campaignRepository.findById(campaign.getId()).orElseThrow().getName());
    }
}
