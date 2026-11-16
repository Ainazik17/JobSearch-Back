package job.search.kg.repo;

import job.search.kg.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Получатели рассылки: все незаблокированные */
    @org.springframework.data.jpa.repository.Query("SELECT u.telegramId FROM User u WHERE u.isBanned = false AND u.telegramId IS NOT NULL")
    java.util.List<Long> findBroadcastRecipients();

    /** Получатели рассылки с активным тарифом */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.user.telegramId FROM Subscription s WHERE s.isActive = true AND s.endDate > :now AND s.user.isBanned = false")
    java.util.List<Long> findProRecipients(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    Optional<User> findByTelegramId(Long telegramId);

    Long countByReferrer(User referrer);

    Optional<User> findByReferralCode(String referralCode);

    boolean existsByTelegramId(Long telegramId);

    boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);
}
