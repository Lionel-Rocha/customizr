package top.customizr.system.model;

import java.util.UUID;

public class Player {
    private UUID id;
    private User userOwner;
    private Campaign campaign;
    private String name;

    public Player(User userOwner, Campaign campaign, String name) {
        this.id = UUID.randomUUID();
        this.userOwner = userOwner;
        this.campaign = campaign;
        this.name = name;
    }
}
