package community.store.user.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBankingDetailRepository extends JpaRepository<UserBankingDetail, Long> {

    Optional<UserBankingDetail> findByUserGuid(String userGuid);

    boolean existsByUserGuid(String userGuid);
}
