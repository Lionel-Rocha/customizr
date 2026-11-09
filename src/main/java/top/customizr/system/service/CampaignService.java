package top.customizr.system.service;

import org.springframework.stereotype.Service;
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

        // 1. Regra de negócio: Gerar código único



        String code = generateUniqueCode();

        // 2. Instanciar a Entidade usando o Factory Method ou Construtor
        Campaign campaign = new Campaign(name, master, code);

        // 3. Persistir no banco
        return campaignRepository.save(campaign);
    }

    private String generateUniqueCode() {
        // lógica para gerar 8 caracteres, verificar se já existe no banco, etc.
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
