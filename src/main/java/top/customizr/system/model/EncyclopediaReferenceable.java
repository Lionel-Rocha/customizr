package top.customizr.system.model;

import java.util.UUID;

/** O que uma entrada da enciclopedia pode referenciar (NPC, pais, cidade, ponto). */
public interface EncyclopediaReferenceable {
    UUID getReferenceId();

    String getDisplayName();
}
