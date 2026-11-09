package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import java.util.UUID;

@Entity
@Table(name="user")
public class User {

    @Column @Getter @Id
    private final UUID userId;
    @Column @Getter @Setter
    private String name;
    @Column @Getter @Setter
    private Role role;


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