package top.customizr.system.dto;

/** Resultado da atualizacao pelo 5etools. */
public record FiveToolsSyncResponse(int officialSources, int unofficialSources, int itemsCreated, int itemsUpdated,
                                    int itemsSkipped, int unofficialItemsLeftOut, boolean includedUnofficial) {}
