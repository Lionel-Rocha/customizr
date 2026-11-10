package top.customizr.system.model;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Source {
    @Id
    private UUID id;

    private String name; //nome completo exemplo: Player's Handbook
    private String code; //exemplo: PHB

    public Source(String name, String code) {
        this.id =  UUID.randomUUID();
        this.name = name;
        this.code = code;
    }
}
