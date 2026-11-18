package top.customizr.system.dto;

import top.customizr.system.model.CampaignIcon;

import java.util.UUID;

public record CampaignIconResponse(UUID id, String key, String label, String imageUrl) {

    public static CampaignIconResponse from(CampaignIcon i) {
        return new CampaignIconResponse(i.getId(), i.getKey(), i.getLabel(), i.getImage().getUrl());
    }
}
