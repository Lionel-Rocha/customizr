package com.vttcaseiro.model;

/** Um som da biblioteca da mesa: id do arquivo (hash) + nome pra mostrar. */
public class SoundEntry {
    private String id;
    private String name;

    public SoundEntry() {
    }

    public SoundEntry(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name == null || name.isBlank() ? "(som)" : name; }

    @Override
    public String toString() {
        return getName();
    }
}
