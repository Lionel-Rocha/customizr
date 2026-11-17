package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/** warnings: o que nao deu pra ligar (classe/item/monstro que nao existe neste servidor etc.). */
public record CampaignImportResponse(UUID campaignId, String name, String code, int characters, int npcs, int worlds,
                                     int encyclopediaEntries, int images, List<String> warnings) {}
