package com.spendsense.onboarding;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIncomeSourceRepository extends JpaRepository<UserIncomeSource, UUID> {
    List<UserIncomeSource> findByUser_Id(UUID userId);
    List<UserIncomeSource> findByUser_IdAndPrimaryTrue(UUID userId);
}
