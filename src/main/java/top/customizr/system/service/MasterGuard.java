package top.customizr.system.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.User;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;

/**
 * Regras de acesso da campanha, como no diagrama ({@code MasterGuard}): so o mestre gerencia;
 * membros (jogador/espectador) leem. Usado pelos servicos de mundo, enciclopedia e imagens.
 */
@Service
public class MasterGuard {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    public MasterGuard(UserRepository userRepository, MembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    public boolean canManage(User user, Campaign campaign) {
        return user != null && campaign != null && campaign.getMaster().getUserId().equals(user.getUserId());
    }

    public void assertCanManage(User user, Campaign campaign) {
        if (!canManage(user, campaign)) {
            throw new AccessDeniedException("Only the campaign master can do this");
        }
    }

    public boolean isMember(User user, Campaign campaign) {
        return canManage(user, campaign)
                || membershipRepository.findByCampaignIdAndUserId(campaign.getId(), user.getUserId()).isPresent();
    }

    public void assertMember(User user, Campaign campaign) {
        if (!isMember(user, campaign)) {
            throw new AccessDeniedException("You are not a member of this campaign");
        }
    }

    public User user(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /** Atalho: usuario logado tem que ser o mestre. Devolve o usuario. */
    public User requireMaster(String username, Campaign campaign) {
        User user = user(username);
        assertCanManage(user, campaign);
        return user;
    }

    /** Atalho: usuario logado tem que ser da campanha. Devolve se ele e o mestre. */
    public boolean requireMember(String username, Campaign campaign) {
        User user = user(username);
        assertMember(user, campaign);
        return canManage(user, campaign);
    }
}
