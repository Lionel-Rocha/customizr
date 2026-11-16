package top.customizr.system.dto;

/** type: DAMAGE (gasta PV temporario primeiro), HEALING (ate o maximo) ou TEMPORARY (fica o maior). */
public record HitPointChangeRequest(HitPointChangeType type, int amount) {

    public enum HitPointChangeType {
        DAMAGE,
        HEALING,
        TEMPORARY
    }
}
