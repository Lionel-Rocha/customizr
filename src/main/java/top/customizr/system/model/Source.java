package top.customizr.system.model;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
public class Source {
    @Id
    @Getter
    private UUID id;

    @Getter
    private String name; //nome completo exemplo: Player's Handbook
    @Getter
    private String code; //exemplo: PHB

    /**
     * Material oficial da Wizards of the Coast? true / false; null = nao se sabe (fonte criada a mao).
     * Preenchido pela sincronizacao com o 5etools (autor do livro ou aventura).
     */
    @Getter
    private Boolean official;


    protected Source() {}

    public Source(String name, String code) {
        this.id =  UUID.randomUUID();
        this.name = name;
        this.code = code;
    }

    public Source(String name, String code, Boolean official) {
        this(name, code);
        this.official = official;
    }

    /** Atualiza nome e se e oficial (vindo da lista de livros do 5etools). */
    public void describe(String name, Boolean official) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        this.official = official;
    }
}
