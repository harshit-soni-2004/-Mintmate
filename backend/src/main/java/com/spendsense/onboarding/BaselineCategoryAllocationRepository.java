package com.spendsense.onboarding;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BaselineCategoryAllocationRepository extends JpaRepository<BaselineCategoryAllocation, UUID> {
    List<BaselineCategoryAllocation> findByUser_Id(UUID userId);
}
