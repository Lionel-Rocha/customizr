package top.customizr.system.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import top.customizr.system.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter @Setter
public class User {

    @Id @Setter(AccessLevel.NONE) @Column(name="user_id")
    private UUID userId;
    @Column
    private String name;

    protected User() {}

    public User(String name) {
        this.name = name;
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