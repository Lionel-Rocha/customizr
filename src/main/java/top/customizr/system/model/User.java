package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import top.customizr.system.security.PasswordConverter;

import java.util.UUID;

@Entity
@Table(name="users")
@Getter @Setter
public class User {

    @Id @Setter(AccessLevel.NONE) @Column(name="user_id")
    private UUID userId;
    @Column
    private String name;
    @Column
    @Convert(converter = PasswordConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    protected User() {}

    @PrePersist
    private void ensureId() {
        if (userId == null) {
            userId = UUID.randomUUID();
        }
    }

    public User(String name, String password) {
        this.name = name;
        this.password = password;
        this.userId = UUID.randomUUID();
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", name='" + name + '\'' +
                '}';
    }

}