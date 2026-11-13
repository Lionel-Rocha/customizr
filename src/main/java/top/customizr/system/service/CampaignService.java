package top.customizr.system.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import top.customizr.system.dto.AddSourceRequest;
import top.customizr.system.dto.UpdateCampaignRequest;
import top.customizr.system.model.*;
import top.customizr.system.model.Character;
import top.customizr.system.repository.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final SourceRepository sourceRepository;
    private final CampaignSourceRepository campaignSourceRepository;
    private final InviteRepository inviteRepository;
    private MembershipRepository membershipRepository;

    public CampaignService(CampaignRepository campaignRepository, UserRepository userRepository, SourceRepository sourceRepository, CampaignSourceRepository campaignSourceRepository, InviteRepository inviteRepository,MembershipRepository membershipRepository) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.sourceRepository = sourceRepository;
        this.campaignSourceRepository = campaignSourceRepository;
        this.inviteRepository = inviteRepository;
        this.membershipRepository = membershipRepository;
    }

    public Campaign createCampaign(String name, String username) {
        //0. pegar o user logado pelo nome (vem do JWT)
        User master = userRepository.findByName(username).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // 1. Instanciar a Entidade usando o Factory Method ou Construtor
        Campaign campaign = new Campaign(name, master);

        // 2. Persistir no banco
        return campaignRepository.save(campaign);
    }


    public List<Campaign> getAllMasterCampaigns(UserDetails loggedUser){
        UUID userId = findUser(loggedUser).getUserId();
        List<Campaign> campaigns = new ArrayList<>(campaignRepository.findByMaster_UserId(userId));
        campaigns.addAll(campaignRepository.findCampaignsByUserIdAndRole(userId, Role.MASTER));
        return campaigns;
    }

    public List<Campaign> getAllPlayerCampaigns(UserDetails loggedUser){
        UUID userId = findUser(loggedUser).getUserId();
        return campaignRepository.findCampaignsByUserIdAndRole(userId, Role.PLAYER);
    }

    public List<Campaign> getAllSpectatorCampaigns(UserDetails loggedUser){
        UUID userId = findUser(loggedUser).getUserId();
        return campaignRepository.findCampaignsByUserIdAndRole(userId, Role.SPECTATOR);
    }

    public List<Campaign> allUserCampaigns(UserDetails loggedUser){
        List<Campaign> spectator = getAllSpectatorCampaigns(loggedUser);
        List<Campaign> player = getAllPlayerCampaigns(loggedUser);
        List<Campaign> master = getAllMasterCampaigns(loggedUser);

        List<Campaign> all = new ArrayList<>();
        Set<UUID> campaignIds = new HashSet<>();
        for (List<Campaign> campaigns : List.of(spectator, player, master)) {
            for (Campaign campaign : campaigns) {
                if (campaignIds.add(campaign.getId())) {
                    all.add(campaign);
                }
            }
        }
        return all;
    }

    private User findUser(UserDetails loggedUser) {
        return userRepository.findByName(loggedUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }

    public Optional<Campaign> readCampaign(String code){
        //1. pegar a campanha pelo id
        Optional<Campaign> campaign = campaignRepository.findCampaignByCode(code);
        //Campaign campaign = campaignRepository.findById(id).orElseThrow();
        return campaign;
    }

    public Campaign updateCampaign(UpdateCampaignRequest request, UUID id){
        //1. pegar a campanha pelo id


        Campaign campaign = campaignRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada"));


        //2. fazer alterações no campo enviado
        request.name().ifPresent(campaign::setName);
        request.master().ifPresent(campaign::setMaster);
        request.code().ifPresent(campaign::setCode);

        return campaignRepository.save(campaign);
    }

    public String deleteCampaign(UUID id){
        //1. pegar campanha pelo id
        Campaign campaign = campaignRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada"));

        //2. drop
        campaignRepository.delete(campaign);
        return "Campanha " + campaign.getName() + " deletada com sucesso.";
    }

    public String addSource(AddSourceRequest request) {
        Campaign campaign = campaignRepository.findById(request.campaignUuid())
                .orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada"));

        Source source = sourceRepository.findById(request.sourceUuid())
                .orElseThrow(() -> new EntityNotFoundException("Livro não encontrado"));

        // Evita erro de UNIQUE se o livro já foi adicionado antes
        if (campaignSourceRepository.existsByCampaignAndSource(campaign, source)) {
            return "O livro " + source.getName() + " já está na campanha " + campaign.getName() + ".";
        }

        CampaignSourceConfig config = new CampaignSourceConfig(campaign, source, true);
        campaignSourceRepository.save(config);

        return "Livro " + source.getName() + " adicionado à campanha " + campaign.getName() + ".";
    }

    public String invite(UUID campaignId, Role role, UserDetails loggedUser, String invitedUsername) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada"));

        // O usuário logado é o mestre que está convidando
        User master = userRepository.findByName(loggedUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuário logado não encontrado"));

        // Verifica se o logado é o mestre da campanha
        if (!campaign.getMaster().getUserId().equals(master.getUserId())) {
            return "Você não pode convidar para essa campanha.";
        }

        // Busca o usuário que será convidado (pelo nome passado no DTO)
        User invitedUser = userRepository.findByName(invitedUsername)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado com o nome: " + invitedUsername));

        Invite invite = new Invite(campaign, invitedUser, role);
        inviteRepository.save(invite);
        return "Convite enviado para " + invitedUser.getName();
    }

    @Transactional
    public String acceptInvite(UUID campaignId, UserDetails loggedUser) {
        // 1. Achar a campanha
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada"));

        String username = loggedUser.getUsername();
        Optional<User> user = userRepository.findByName(username);
        // 2. Verificar se tem um convite pendente com essa campanha e esse player
        Optional<Invite> inviteOpt = inviteRepository.findByCampaignIdAndInvitedUserUserId(campaign.getId(), user.get().getUserId());
        // 3. Se não tiver convite, recusa
        if (inviteOpt.isEmpty()) {
            throw new EntityNotFoundException("Você não possui um convite para esta campanha.");
        }

        // 4. Se tiver, aceita (criar membership e apagar o convite)
        Invite currentInvite = inviteOpt.get();

        // Cria a Membership passando as ENTIDADES (como definimos anteriormente)
        Membership membership = new Membership(campaign.getId(), user.get().getUserId(), currentInvite.getRole());
        membershipRepository.save(membership);

        // Apagar o convite depois de aceito para não duplicar
        inviteRepository.delete(currentInvite);

        return "Convite aceito com sucesso! Bem-vindo à campanha " + campaign.getName() + ".";
    }


}
