package com.spendsense.onboarding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFinancialProfileRepository extends JpaRepository<UserFinancialProfile, UUID> {
    Optional<UserFinancialProfile> findByUser_Id(UUID userId);
}
