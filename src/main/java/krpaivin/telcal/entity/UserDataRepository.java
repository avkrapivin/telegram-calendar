package krpaivin.telcal.entity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDataRepository extends JpaRepository<UserData, String> {
    Optional<UserData> findByUserId(String userId);
}
