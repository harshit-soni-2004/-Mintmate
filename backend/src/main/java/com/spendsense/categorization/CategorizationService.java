package com.spendsense.categorization;

import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.categorization.model.CategorizationInput;
import com.spendsense.categorization.model.CategorizationModel;
import com.spendsense.categorization.model.CategorizationPrediction;
import com.spendsense.categorization.policy.CategorizationConfidencePolicy;
import com.spendsense.categorization.rule.RuleContext;
import com.spendsense.categorization.rule.RuleEngine;
import com.spendsense.categorization.rule.RuleMatchResult;
import com.spendsense.transaction.TransactionCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Central orchestrator for transaction categorization.
 * <p>
 * Implements a strict, explainable precedence hierarchy:
 * 1. Explicit deterministic system rules (Payroll, Tax, ATM, Interest, Transfers)
 * 2. Strong user-specific memory (consistent historical categorization)
 * 3. Global ML model with confidence policy & weak/conflicting memory reconciliation
 * 4. Safe fallback behavior (ML unavailable / unparseable text)
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private final TransactionNormalizer normalizer;
    private final RuleEngine ruleEngine;
    private final UserCategorizationMemoryService userMemoryService;
    private final CategorizationModel categorizationModel;
    private final CategorizationModel semanticModel;
    private final CategorizationConfidencePolicy confidencePolicy;
    private final boolean mlEnabled;
    private final boolean semanticEnabled;
    private final double escalationThreshold;

    public CategorizationService(
            TransactionNormalizer normalizer,
            RuleEngine ruleEngine,
            UserCategorizationMemoryService userMemoryService,
            CategorizationModel categorizationModel,
            CategorizationConfidencePolicy confidencePolicy,
            boolean mlEnabled
    ) {
        this(normalizer, ruleEngine, userMemoryService, categorizationModel, null, confidencePolicy, mlEnabled, false, 0.60);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CategorizationService(
            TransactionNormalizer normalizer,
            RuleEngine ruleEngine,
            UserCategorizationMemoryService userMemoryService,
            @org.springframework.beans.factory.annotation.Qualifier("baselineCategorizationModel") CategorizationModel categorizationModel,
            @org.springframework.beans.factory.annotation.Autowired(required = false) @org.springframework.beans.factory.annotation.Qualifier("semanticCategorizationModel") CategorizationModel semanticModel,
            CategorizationConfidencePolicy confidencePolicy,
            @Value("${spendsense.categorization.ml.enabled:true}") boolean mlEnabled,
            @Value("${spendsense.categorization.semantic.enabled:false}") boolean semanticEnabled,
            @Value("${spendsense.categorization.semantic.escalation-threshold:0.60}") double escalationThreshold
    ) {
        this.normalizer = normalizer;
        this.ruleEngine = ruleEngine;
        this.userMemoryService = userMemoryService;
        this.categorizationModel = categorizationModel;
        this.semanticModel = semanticModel;
        this.confidencePolicy = confidencePolicy;
        this.mlEnabled = mlEnabled;
        this.semanticEnabled = semanticEnabled;
        this.escalationThreshold = escalationThreshold;
    }

    /**
     * Categorizes a transaction using the unified orchestration pipeline.
     */
    public CategorizationResult categorize(CategorizationRequest request) {
        Objects.requireNonNull(request, "CategorizationRequest cannot be null");

        // 1. Transaction Text Normalization
        NormalizedTransaction normalized = normalizer.normalize(request.merchant(), request.description());
        String normalizedMerchant = normalized.normalizedMerchant();

        if ("unknown".equalsIgnoreCase(normalizedMerchant) && normalized.compositeText().isBlank()) {
            return CategorizationResult.fromFallback(
                    TransactionCategory.OTHER,
                    "Empty or unparseable merchant and description",
                    normalizedMerchant
            );
        }

        // 2. Precedence Stage 1: Deterministic Rules
        RuleContext ruleContext = new RuleContext(
                request.userId(),
                normalized,
                request.amount(),
                request.paymentMethod(),
                request.transactionDate()
        );
        Optional<RuleMatchResult> ruleMatch = ruleEngine.evaluate(ruleContext);
        if (ruleMatch.isPresent()) {
            RuleMatchResult match = ruleMatch.get();
            return CategorizationResult.fromRule(
                    match.category(),
                    match.confidence(),
                    match.confidenceLevel(),
                    match.ruleId(),
                    match.explanation(),
                    normalizedMerchant
            );
        }

        // 3. Precedence Stage 2: User-Specific Categorization Memory
        Optional<UserMemoryPreference> memoryPrefOpt = Optional.empty();
        if (request.userId() != null) {
            memoryPrefOpt = userMemoryService.getPreference(request.userId(), normalizedMerchant);
        }

        if (memoryPrefOpt.isPresent()) {
            UserMemoryPreference pref = memoryPrefOpt.get();
            if (pref.isStrongPreference()) {
                log.debug("Strong user memory match for user={} merchant='{}' -> {}",
                        request.userId(), normalizedMerchant, pref.category());
                return CategorizationResult.fromUserMemory(
                        pref.category(),
                        pref.strengthScore(),
                        pref.confidenceLevel(),
                        true,
                        String.format("Strong user-specific memory (%d/%d observations)",
                                pref.categoryUsageCount(), pref.totalUsageCount()),
                        normalizedMerchant,
                        List.of(new CategorizationCandidate(pref.category(), pref.strengthScore()))
                );
            }
        }

        // 4. Precedence Stage 3: Global ML Categorization Model (Fast Baseline -> Tiered Semantic Escalation)
        CategorizationPrediction mlPrediction = null;
        boolean isEscalated = false;
        String escalationDetail = null;

        DayOfWeek dayOfWeek = request.transactionDate() != null ? request.transactionDate().getDayOfWeek() : null;
        CategorizationInput mlInput = CategorizationInput.from(
                normalized,
                request.amount(),
                request.paymentMethod(),
                dayOfWeek
        );

        // 4a. Fast Baseline ML Inference
        if (mlEnabled && categorizationModel != null && categorizationModel.getModelHealth().isAvailable()) {
            try {
                mlPrediction = categorizationModel.predict(mlInput);
            } catch (Exception e) {
                log.warn("Baseline ML categorization prediction failed for merchant='{}': {}", normalizedMerchant, e.getMessage());
            }
        }

        // 4b. Tiered Semantic Escalation (if baseline is uncertain below threshold, or baseline unavailable/failed)
        boolean shouldEscalate = (mlPrediction == null || mlPrediction.confidence() < escalationThreshold);
        if (shouldEscalate && semanticEnabled && semanticModel != null && semanticModel.getModelHealth().isAvailable()) {
            try {
                CategorizationPrediction semPrediction = semanticModel.predict(mlInput);
                if (mlPrediction == null || semPrediction.confidence() > mlPrediction.confidence()) {
                    escalationDetail = mlPrediction != null
                            ? String.format("Semantic escalation applied: baseline confidence (%.2f) below threshold (%.2f), semantic confidence (%.2f)",
                                    mlPrediction.confidence(), escalationThreshold, semPrediction.confidence())
                            : "Semantic escalation applied: baseline model unavailable";
                    mlPrediction = semPrediction;
                    isEscalated = true;
                    log.debug("Semantic escalation applied for merchant='{}': {}", normalizedMerchant, escalationDetail);
                }
            } catch (Exception e) {
                log.warn("Semantic escalation failed safely for merchant='{}': {}", normalizedMerchant, e.getMessage());
            }
        }

        if (mlPrediction != null) {
            ConfidenceLevel evaluatedLevel = confidencePolicy.evaluateLevel(mlPrediction.confidence());

            // Reconcile ML prediction with weak or conflicting user memory
            if (memoryPrefOpt.isPresent()) {
                UserMemoryPreference pref = memoryPrefOpt.get();

                if (!pref.hasConflict()) {
                    // WEAK / TENTATIVE MEMORY CASE (e.g. 1 observation)
                    if (evaluatedLevel == ConfidenceLevel.HIGH) {
                        // Weak preference must NOT blindly override strong ML
                        boolean agreement = pref.category() == mlPrediction.predictedCategory();
                        String baseRationale = agreement
                                ? "ML high confidence aligned with tentative user preference"
                                : String.format("ML high confidence (%s) prioritized over tentative user preference (%s)",
                                mlPrediction.predictedCategory(), pref.category());
                        String finalRationale = isEscalated ? baseRationale + " (" + escalationDetail + ")" : baseRationale;

                        return CategorizationResult.fromMl(
                                mlPrediction.predictedCategory(),
                                mlPrediction.confidence(),
                                ConfidenceLevel.HIGH,
                                reorderCandidatesWithPreference(mlPrediction.alternatives(), pref.category()),
                                mlPrediction.modelVersion(),
                                true,
                                !agreement, // require confirmation if ML disagreed with tentative memory
                                finalRationale,
                                normalizedMerchant
                        );
                    } else {
                        // ML is uncertain (MEDIUM or LOW) -> Weak user preference wins as leading signal
                        return CategorizationResult.fromUserMemory(
                                pref.category(),
                                pref.strengthScore(),
                                ConfidenceLevel.MEDIUM,
                                false,
                                String.format("Tentative user preference prioritized over uncertain ML (%s, conf=%.2f)",
                                        mlPrediction.predictedCategory(), mlPrediction.confidence()),
                                normalizedMerchant,
                                reorderCandidatesWithPreference(mlPrediction.alternatives(), pref.category())
                        );
                    }
                } else {
                    // CONFLICTING MEMORY CASE (e.g. User has picked multiple categories historically)
                    String rationale = String.format("ML prediction selected due to conflicting user history (%d total uses)",
                            pref.totalUsageCount());
                    if (isEscalated) {
                        rationale = rationale + " (" + escalationDetail + ")";
                    }
                    return CategorizationResult.fromMl(
                            mlPrediction.predictedCategory(),
                            mlPrediction.confidence(),
                            evaluatedLevel,
                            mlPrediction.alternatives(),
                            mlPrediction.modelVersion(),
                            true,
                            true, // always require confirmation when history is divided
                            rationale,
                            normalizedMerchant
                    );
                }
            }

            // NO USER MEMORY CASE (Cold start / unknown merchant)
            boolean requiresConfirmation = confidencePolicy.requiresConfirmation(evaluatedLevel);
            String rationale = switch (evaluatedLevel) {
                case HIGH -> "Global ML model prediction with high confidence";
                case MEDIUM -> "Global ML model prediction with medium confidence; confirmation recommended";
                case LOW, NONE -> "Global ML model prediction with low confidence; manual verification required";
            };
            if (isEscalated) {
                rationale = rationale + " (" + escalationDetail + ")";
            }

            return CategorizationResult.fromMl(
                    mlPrediction.predictedCategory(),
                    mlPrediction.confidence(),
                    evaluatedLevel,
                    mlPrediction.alternatives(),
                    mlPrediction.modelVersion(),
                    false,
                    requiresConfirmation,
                    rationale,
                    normalizedMerchant
            );
        }

        // 5. Precedence Stage 4: Fallback (ML Disabled, Model Unavailable, or Failed)
        if (memoryPrefOpt.isPresent()) {
            UserMemoryPreference pref = memoryPrefOpt.get();
            return CategorizationResult.fromUserMemory(
                    pref.category(),
                    pref.strengthScore(),
                    pref.confidenceLevel(),
                    pref.isStrongPreference(),
                    "User memory preference applied as fallback (ML model unavailable)",
                    normalizedMerchant,
                    List.of(new CategorizationCandidate(pref.category(), pref.strengthScore()))
            );
        }

        return CategorizationResult.fromFallback(
                TransactionCategory.OTHER,
                "No deterministic rule, user memory, or ML prediction available; defaulted to OTHER",
                normalizedMerchant
        );
    }

    /**
     * Convenience method for simple merchant and description input without user context.
     */
    public CategorizationResult categorize(String merchant, String description) {
        return categorize(CategorizationRequest.of(merchant, description));
    }

    /**
     * Convenience method for merchant, description with user context.
     */
    public CategorizationResult categorize(UUID userId, String merchant, String description) {
        return categorize(CategorizationRequest.of(userId, merchant, description));
    }

    private List<CategorizationCandidate> reorderCandidatesWithPreference(
            List<CategorizationCandidate> candidates,
            TransactionCategory preferredCategory
    ) {
        List<CategorizationCandidate> result = new ArrayList<>();
        CategorizationCandidate preferredCandidate = null;

        for (CategorizationCandidate c : candidates) {
            if (c.category() == preferredCategory) {
                preferredCandidate = c;
            } else {
                result.add(c);
            }
        }

        if (preferredCandidate != null) {
            result.add(0, preferredCandidate);
        }

        return result;
    }
}
