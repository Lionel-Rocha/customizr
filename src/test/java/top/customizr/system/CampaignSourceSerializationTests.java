package top.customizr.system;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CampaignSourceConfig;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CampaignSourceSerializationTests {

    @Test
    void campaignSourceConfigSerializesItsSourceDetailsWithoutTheCampaignBackReference() {
        Campaign campaign = new Campaign("Test campaign", new User("master", "password"));
        Source source = new Source("Player's Handbook", "PHB");
        CampaignSourceConfig config = new CampaignSourceConfig(campaign, source, true);

        var json = JsonMapper.builder().build().valueToTree(config);

        assertEquals(source.getId().toString(), json.path("source").path("id").asString());
        assertEquals("Player's Handbook", json.path("source").path("name").asString());
        assertEquals("PHB", json.path("source").path("code").asString());
        assertEquals(true, json.path("enabled").asBoolean());
        assertFalse(json.has("campaign"));
    }
}
