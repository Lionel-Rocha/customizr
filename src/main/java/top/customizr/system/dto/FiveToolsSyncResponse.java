package top.customizr.system.dto;

/**
 * Resultado da atualizacao pelo 5etools. Classes: {@code classesImported} novas, {@code subclassesAdded} subclasses
 * que entraram (tambem nas classes que ja existiam), {@code classEntriesLeftOut} classes/subclasses de fora
 * (World of Warcraft, Unearthed Arcana).
 */
public record FiveToolsSyncResponse(int officialSources, int unofficialSources, int itemsCreated, int itemsUpdated,
                                    int itemsSkipped, int unofficialItemsLeftOut, boolean includedUnofficial,
                                    int classesImported, int subclassesAdded, int classEntriesLeftOut) {}
