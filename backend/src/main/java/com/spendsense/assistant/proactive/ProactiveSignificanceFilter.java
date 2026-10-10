package com.spendsense.assistant.proactive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class ProactiveSignificanceFilter {

    private static final Logger log = LoggerFactory.getLogger(ProactiveSignificanceFilter.class);
    private static final Duration DEFAULT_COOLDOWN = Duration.ofDays(7);
    private static final int MAX_SURFACED_INSIGHTS = 3;

    private final ProactiveInsightRepository proactiveInsightRepository;

    public ProactiveSignificanceFilter(ProactiveInsightRepository proactiveInsightRepository) {
        this.proactiveInsightRepository = proactiveInsightRepository;
    }

    public List<ProactiveInsightCandidate> filterAndRank(UUID userId, List<ProactiveInsightCandidate> rawCandidates) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        Instant cooldownCutoff = Instant.now().minus(DEFAULT_COOLDOWN);
        List<ProactiveInsightCandidate> significantCandidates = new ArrayList<>();

        for (ProactiveInsightCandidate candidate : rawCandidates) {
            // Deduplication and cooldown suppression
            boolean recentlySuppressed = proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(
                    userId, candidate.deduplicationKey(), cooldownCutoff);

            if (recentlySuppressed) {
                log.debug("Suppressed duplicate proactive insight for user {} key {}", userId, candidate.deduplicationKey());
                continue;
            }

            // Minimum magnitude thresholds
            if (candidate.type() == ProactiveInsightType.CATEGORY_SPIKE) {
                Object pct = candidate.supportingFacts().get("percentageChange");
                if (pct instanceof Number n && n.doubleValue() < 20.0) {
                    log.debug("Filtered out minor category change ({}%) for key {}", n, candidate.deduplicationKey());
                    continue;
                }
            }

            significantCandidates.add(candidate);
        }

        // Rank by relevance score descending
        significantCandidates.sort(Comparator.comparingDouble(ProactiveInsightCandidate::relevanceScore).reversed());

        // Limit to top 3 insights
        if (significantCandidates.size() > MAX_SURFACED_INSIGHTS) {
            return significantCandidates.subList(0, MAX_SURFACED_INSIGHTS);
        }
        return significantCandidates;
    }
}
