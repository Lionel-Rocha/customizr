package top.customizr.system.model;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
public class Source {
    @Id
    private UUID id;

    @Getter
    private String name; //nome completo exemplo: Player's Handbook
    @Getter
    private String code; //exemplo: PHB


    protected Source() {}

    public Source(String name, String code) {
        this.id =  UUID.randomUUID();
        this.name = name;
        this.code = code;
    }


}
