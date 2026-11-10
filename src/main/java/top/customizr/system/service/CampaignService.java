package top.customizr.system.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import top.customizr.system.dto.AddSourceRequest;
import top.customizr.system.dto.UpdateCampaignRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignSourceConfig;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CampaignSourceRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final SourceRepository sourceRepository;
    private final CampaignSourceRepository campaignSourceRepository;

    public CampaignService(CampaignRepository campaignRepository, UserRepository userRepository, SourceRepository sourceRepository, CampaignSourceRepository campaignSourceRepository) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.sourceRepository = sourceRepository;
        this.campaignSourceRepository = campaignSourceRepository;
    }

    public Campaign createCampaign(String name, String username) {
        //0. pegar o user logado pelo nome (vem do JWT)
        User master = userRepository.findByName(username).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // 1. Instanciar a Entidade usando o Factory Method ou Construtor
        Campaign campaign = new Campaign(name, master);

        // 2. Persistir no banco
        return campaignRepository.save(campaign);
    }


    public Campaign readCampaign(UUID id){
        //1. pegar a campanha pelo id
        Campaign campaign = campaignRepository.findById(id).orElseThrow();
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

}
