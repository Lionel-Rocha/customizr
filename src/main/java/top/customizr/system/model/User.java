package top.customizr.system.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import top.customizr.system.repository.UserRepository;

import java.util.UUID;

@Entity
@Table(name="users")
@Getter @Setter
public class User {

    @Id @Setter(AccessLevel.NONE) @Column
    private UUID userId;
    @Column
    private String name;
    @Column @Enumerated(EnumType.STRING)
    private Role role;

    protected User() {}

    public User(Role role, String name) {
        this.role = role;
        this.name = name;
        this.userId = UUID.randomUUID();
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", role=" + role +
                ", name='" + name + '\'' +
                '}';
    }
}