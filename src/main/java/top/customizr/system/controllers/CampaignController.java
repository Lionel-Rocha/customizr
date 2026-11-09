package top.customizr.system.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.customizr.system.dto.CreateCampaignRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.service.CampaignService;

import java.util.UUID;

@RestController
@RequestMapping("campaign")
public class CampaignController {

    private CampaignService campaignService;

    public CampaignController(CampaignService campaignService){
        this.campaignService = campaignService;
    }

    @PostMapping("/create")
    public Campaign create(@RequestBody CreateCampaignRequest request){
        return campaignService.createCampaign(
                request.name(),
                request.uuid()
        );
    }

}
