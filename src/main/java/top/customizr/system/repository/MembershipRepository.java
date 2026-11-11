package top.customizr.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.customizr.system.model.Membership;

import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
}
