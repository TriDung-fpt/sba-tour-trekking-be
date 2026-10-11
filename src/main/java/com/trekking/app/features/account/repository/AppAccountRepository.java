package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AppAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppAccountRepository extends JpaRepository<AppAccount, Long> {
    // Normalize persisted legacy email values during lookup; request validation belongs to the validation layer.
    @Query("select a from AppAccount a where lower(trim(a.email)) = :email")
    Optional<AppAccount> findByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AppAccount a where lower(trim(a.email)) = :email")
    Optional<AppAccount> lockByEmail(@Param("email") String email);

    Optional<AppAccount> findByGoogleSubject(String googleSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AppAccount a where a.googleSubject = :subject")
    Optional<AppAccount> lockByGoogleSubject(@Param("subject") String subject);
}
