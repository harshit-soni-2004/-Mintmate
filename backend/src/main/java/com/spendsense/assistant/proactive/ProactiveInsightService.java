package com.spendsense.assistant.proactive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ProactiveInsightService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveInsightService.class);
    private static final Duration DEFAULT_EXPIRATION = Duration.ofDays(14);

    private final ProactiveInsightRepository proactiveInsightRepository;
    private final ProactiveInsightDetector proactiveInsightDetector;
    private final ProactiveSignificanceFilter proactiveSignificanceFilter;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public ProactiveInsightService(
            ProactiveInsightRepository proactiveInsightRepository,
            ProactiveInsightDetector proactiveInsightDetector,
            ProactiveSignificanceFilter proactiveSignificanceFilter,
            UserRepository userRepository,
            CurrentUser currentUser,
            ObjectMapper objectMapper
    ) {
        this.proactiveInsightRepository = proactiveInsightRepository;
        this.proactiveInsightDetector = proactiveInsightDetector;
        this.proactiveSignificanceFilter = proactiveSignificanceFilter;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ProactiveInsightDto> getActiveInsights() {
        SpendSenseUserPrincipal principal = currentUser.require();
        UUID userId = principal.getUserId();

        List<ProactiveInsightEntity> existing = proactiveInsightRepository
                .findByUser_IdAndDismissedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now());

        if (!existing.isEmpty()) {
            return existing.stream().map(this::mapToDto).toList();
        }

        // If no active stored insights, evaluate deterministically on-demand
        return detectAndGenerateInsights(principal);
    }

    @Transactional
    public List<ProactiveInsightDto> detectAndGenerateInsights(SpendSenseUserPrincipal principal) {
        UUID userId = principal.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        List<ProactiveInsightCandidate> rawCandidates = proactiveInsightDetector.detectCandidates(principal);
        List<ProactiveInsightCandidate> filtered = proactiveSignificanceFilter.filterAndRank(userId, rawCandidates);

        List<ProactiveInsightDto> result = new ArrayList<>();
        Instant expiresAt = Instant.now().plus(DEFAULT_EXPIRATION);

        for (ProactiveInsightCandidate c : filtered) {
            String factsJson;
            try {
                factsJson = objectMapper.writeValueAsString(c.supportingFacts());
            } catch (Exception e) {
                log.warn("Could not serialize supporting facts for insight {}: {}", c.deduplicationKey(), e.getMessage());
                factsJson = "{}";
            }

            ProactiveInsightEntity entity = new ProactiveInsightEntity(
                    user,
                    c.type(),
                    c.severity(),
                    c.title(),
                    c.summary(),
                    factsJson,
                    c.actionRecommendation(),
                    c.deduplicationKey(),
                    expiresAt
            );

            ProactiveInsightEntity saved = proactiveInsightRepository.save(entity);
            result.add(mapToDto(saved));
            log.info("Persisted proactive insight id={} type={} for user={}", saved.getId(), saved.getType(), userId);
        }

        return result;
    }

    @Transactional
    public void dismissInsight(UUID insightId) {
        SpendSenseUserPrincipal principal = currentUser.require();
        int rows = proactiveInsightRepository.dismissInsight(insightId, principal.getUserId());
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INSIGHT_NOT_FOUND", "Proactive insight not found or already dismissed");
        }
        log.info("Dismissed proactive insight id={} for user={}", insightId, principal.getUserId());
    }

    private ProactiveInsightDto mapToDto(ProactiveInsightEntity entity) {
        Map<String, Object> facts;
        try {
            facts = objectMapper.readValue(entity.getSupportingFactsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            facts = Collections.emptyMap();
        }

        return new ProactiveInsightDto(
                entity.getId(),
                entity.getType(),
                entity.getSeverity(),
                entity.getTitle(),
                entity.getSummary(),
                facts,
                entity.getActionRecommendation(),
                entity.getDeduplicationKey(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
