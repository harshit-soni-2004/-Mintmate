package com.spendsense.auth.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, String> {

    Optional<EmailOtp> findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String email, String purpose);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    @Modifying
    @Query("DELETE FROM EmailOtp o WHERE o.expiresAt < :cutoff OR (o.consumedAt IS NOT NULL AND o.consumedAt < :cutoff)")
    int deleteOldOtps(@Param("cutoff") Instant cutoff);
}
