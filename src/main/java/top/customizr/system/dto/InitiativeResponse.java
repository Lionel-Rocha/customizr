package top.customizr.system.dto;

import top.customizr.system.model.InitiativeEntry;
import top.customizr.system.model.InitiativeTracker;
import java.util.List;
import java.util.UUID;

public record InitiativeResponse(int round, int turnIndex, UUID currentEntryId, List<Entry> entries) {

    public static InitiativeResponse from(InitiativeTracker t) {
        return new InitiativeResponse(t.getRound(), t.getTurnIndex(), t.current() == null ? null : t.current().getId(),
                t.getEntries().stream().map(Entry::from).toList());
    }

    public record Entry(UUID id, String name, int initiative, int bonus, UUID tokenId) {
        static Entry from(InitiativeEntry e) {
            return new Entry(e.getId(), e.getName(), e.getInitiative(), e.getBonus(), e.getTokenId());
        }
    }
}
