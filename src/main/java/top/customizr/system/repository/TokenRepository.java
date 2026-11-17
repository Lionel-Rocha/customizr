package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Character;
import top.customizr.system.model.Token;

import java.util.List;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {

    List<Token> findByCharacter(Character character);

    /** Tokens de grupo (caravana) em que a ficha viaja. */
    List<Token> findByMembersContaining(Character character);
}
