package top.customizr.system.dto;

/** maxSize null = sem limite. */
public record CreatePartyRequest(String name, Integer maxSize) {
    public CreatePartyRequest(String name) {
        this(name, null);
    }
}
