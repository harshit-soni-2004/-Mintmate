package com.spendsense.assistant.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    Optional<PendingAction> findByIdAndUser_Id(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PendingAction a SET a.status = :toStatus, a.confirmedAt = :now WHERE a.id = :id AND a.user.id = :userId AND a.status = :fromStatus AND a.expiresAt > :now")
    int transitionToConfirmed(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("fromStatus") PendingActionStatus fromStatus,
            @Param("toStatus") PendingActionStatus toStatus,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PendingAction a SET a.status = :toStatus, a.executedAt = :now WHERE a.id = :id AND a.user.id = :userId AND a.status = :fromStatus")
    int transitionToExecuted(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("fromStatus") PendingActionStatus fromStatus,
            @Param("toStatus") PendingActionStatus toStatus,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PendingAction a SET a.status = :toStatus, a.failureInformation = :failureInfo WHERE a.id = :id AND a.user.id = :userId")
    int transitionToFailed(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("toStatus") PendingActionStatus toStatus,
            @Param("failureInfo") String failureInfo
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PendingAction a SET a.status = :toStatus WHERE a.id = :id AND a.user.id = :userId AND a.status = :fromStatus")
    int transitionStatus(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("fromStatus") PendingActionStatus fromStatus,
            @Param("toStatus") PendingActionStatus toStatus
    );
}
