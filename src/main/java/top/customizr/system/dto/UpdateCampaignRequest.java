package top.customizr.system.dto;

import top.customizr.system.model.User;

import java.util.Optional;
import java.util.UUID;

public record UpdateCampaignRequest(
        Optional<String> name,
        Optional<User> master,
        Optional<String> code
) {
}
