package top.customizr.system.dto;

/** SHORT gasta {@code hitDice} dados de vida; LONG recupera tudo. */
public record RestRequest(RestType type, int hitDice) {

    public enum RestType { SHORT, LONG }
}
