package top.customizr.system.dto;

import java.util.List;

/** Troca os marcadores do token. roundsLeft vazio = fica ate alguem tirar. */
public record MarkersRequest(List<Item> markers) {

    public record Item(String label, String icon, Integer roundsLeft) {}
}
