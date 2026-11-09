package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {


}
