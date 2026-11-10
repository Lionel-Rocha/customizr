package top.customizr.system.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import top.customizr.system.dto.UpdateCampaignRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.UserRepository;

import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;

    public CampaignService(CampaignRepository campaignRepository, UserRepository userRepository) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
    }

    public Campaign createCampaign(String name, UUID uuid) {
        //0. pegar o user pelo uuid
        User master = userRepository.findById(uuid).orElseThrow();

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

}
