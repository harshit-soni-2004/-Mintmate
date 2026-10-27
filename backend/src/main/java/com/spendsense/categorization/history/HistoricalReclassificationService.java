package com.spendsense.categorization.history;

import com.spendsense.categorization.CategorizationRequest;
import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrating idempotent, safe dry-run historical reclassification.
 * Never modifies database rows during a dry run.
 * Preserves manual user classifications.
 */
@Service
public class HistoricalReclassificationService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalReclassificationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final TransactionRepository transactionRepository;
    private final TransactionNormalizer normalizer;
    private final CategorizationService categorizationService;
    private final com.spendsense.categorization.policy.CategorizationConfidencePolicy confidencePolicy;

    public HistoricalReclassificationService(
            TransactionRepository transactionRepository,
            TransactionNormalizer normalizer,
            CategorizationService categorizationService
    ) {
        this(transactionRepository, normalizer, categorizationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public HistoricalReclassificationService(
            TransactionRepository transactionRepository,
            TransactionNormalizer normalizer,
            CategorizationService categorizationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.categorization.policy.CategorizationConfidencePolicy confidencePolicy
    ) {
        this.transactionRepository = transactionRepository;
        this.normalizer = normalizer;
        this.categorizationService = categorizationService;
        this.confidencePolicy = confidencePolicy != null ? confidencePolicy : new com.spendsense.categorization.policy.CategorizationConfidencePolicy(0.85, 0.60);
    }

    /**
     * Executes a dry-run historical reclassification across a date range.
     * Guaranteed read-only: does NOT update or save any transaction entities.
     */
    @Transactional(readOnly = true)
    public HistoricalReclassificationReport runDryRun(LocalDate startDate, LocalDate endDate) {
        log.info("Starting safe historical reclassification dry run for range {} to {}", startDate, endDate);

        List<Transaction> transactions = transactionRepository.findByTransactionDateBetween(startDate, endDate);
        log.info("Found {} historical transactions to evaluate in date range", transactions.size());

        List<HistoricalReclassificationItem> items = new ArrayList<>();
        Map<TransactionCategory, Long> currentDist = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Long> proposedDist = new EnumMap<>(TransactionCategory.class);
        Map<String, Long> transitions = new LinkedHashMap<>();
        Map<String, Long> ruleMatches = new LinkedHashMap<>();

        int totalChanged = 0;
        int totalPreservedManual = 0;
        int totalRequiringReview = 0;

        for (Transaction tx : transactions) {
            TransactionCategory currentCat = tx.getCategory();
            currentDist.merge(currentCat, 1L, Long::sum);

            boolean manualPreserved = tx.getSourceType() == TransactionSourceType.MANUAL;
            String proposedMerchant = normalizer.deriveDisplayMerchant(tx.getMerchant(), tx.getDescription());

            TransactionCategory proposedCat;
            TransactionCategory predictedCat;
            double confidence;
            com.spendsense.categorization.ConfidenceLevel confidenceLevel;
            com.spendsense.categorization.CategorizationSource source;
            boolean requiresReview;
            String evidence;

            if (manualPreserved) {
                totalPreservedManual++;
                proposedCat = currentCat;
                predictedCat = tx.getPredictedCategory() != null ? tx.getPredictedCategory() : currentCat;
                confidence = tx.getCategorizationConfidence() != null ? tx.getCategorizationConfidence().doubleValue() : 1.0;
                confidenceLevel = tx.getCategorizationConfidenceLevel() != null ? tx.getCategorizationConfidenceLevel() : com.spendsense.categorization.ConfidenceLevel.HIGH;
                source = com.spendsense.categorization.CategorizationSource.USER_MEMORY;
                requiresReview = false;
                evidence = "Preserved manual user classification (sourceType=MANUAL)";
            } else {
                UUID userId = tx.getUser() != null ? tx.getUser().getId() : null;
                CategorizationRequest request = new CategorizationRequest(
                        userId,
                        tx.getMerchant(),
                        tx.getDescription(),
                        tx.getAmount(),
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().name() : null,
                        tx.getTransactionDate()
                );

                CategorizationResult result = categorizationService.categorize(request);
                confidence = result.confidence();
                confidenceLevel = result.confidenceLevel();
                source = result.source();
                predictedCat = result.predictedCategory();

                boolean isLow = confidencePolicy.isLowConfidence(confidenceLevel);
                proposedCat = isLow ? TransactionCategory.OTHER : result.category();
                requiresReview = result.requiresUserConfirmation()
                        || isLow
                        || confidencePolicy.requiresConfirmation(confidenceLevel);
                evidence = result.rationale();

                if (result.source() == com.spendsense.categorization.CategorizationSource.RULE && result.modelVersion() != null) {
                    ruleMatches.merge(result.modelVersion(), 1L, Long::sum);
                }
            }

            proposedDist.merge(proposedCat, 1L, Long::sum);

            boolean changed = currentCat != proposedCat;
            if (changed) {
                totalChanged++;
                String transitionKey = currentCat + " -> " + proposedCat;
                transitions.merge(transitionKey, 1L, Long::sum);
            }

            if (requiresReview) {
                totalRequiringReview++;
            }

            items.add(new HistoricalReclassificationItem(
                    tx.getId(),
                    tx.getTransactionDate(),
                    tx.getAmount(),
                    tx.getMerchant(),
                    tx.getDescription(),
                    proposedMerchant,
                    currentCat,
                    proposedCat,
                    predictedCat,
                    confidence,
                    confidenceLevel,
                    source,
                    requiresReview,
                    evidence,
                    manualPreserved,
                    changed
            ));
        }

        // Sort transitions descending
        Map<String, Long> sortedTransitions = transitions.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        // Sort rule matches descending
        Map<String, Long> sortedRuleMatches = ruleMatches.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        List<HistoricalReclassificationItem> sampleChanged = items.stream()
                .filter(HistoricalReclassificationItem::changed)
                .limit(50)
                .toList();

        return new HistoricalReclassificationReport(
                startDate,
                endDate,
                transactions.size(),
                totalChanged,
                totalPreservedManual,
                totalRequiringReview,
                currentDist,
                proposedDist,
                sortedTransitions,
                sortedRuleMatches,
                sampleChanged
        );
    }

    /**
     * Applies reclassification to database rows in the date range.
     * Takes an immutable backup snapshot to disk before modifying any records.
     * Preserves manual user classifications.
     */
    @Transactional
    public HistoricalReclassificationApplyResult applyReclassification(LocalDate startDate, LocalDate endDate) {
        log.info("Starting safe historical reclassification apply for range {} to {}", startDate, endDate);

        List<Transaction> transactions = transactionRepository.findByTransactionDateBetweenOrderByTransactionDateAsc(startDate, endDate);
        if (transactions.isEmpty()) {
            return new HistoricalReclassificationApplyResult(startDate, endDate, 0, 0, 0, 0, null, Map.of());
        }

        // 1. Create pre-apply backup snapshot
        String backupId = UUID.randomUUID().toString();
        List<HistoricalReclassificationSnapshot.TransactionState> states = transactions.stream()
                .map(tx -> new HistoricalReclassificationSnapshot.TransactionState(
                        tx.getId(),
                        tx.getMerchant(),
                        tx.getCategory(),
                        tx.getPredictedCategory(),
                        tx.isRequiresReview(),
                        tx.getCategorizationConfidence(),
                        tx.getCategorizationConfidenceLevel(),
                        tx.getCategorizationSource()
                ))
                .toList();

        HistoricalReclassificationSnapshot snapshot = new HistoricalReclassificationSnapshot(
                backupId,
                Instant.now(),
                startDate,
                endDate,
                states
        );

        String backupFilePath = persistBackupSnapshot(snapshot);

        // 2. Apply reclassifications
        int totalUpdated = 0;
        int totalPreservedManual = 0;
        int totalRequiringReview = 0;
        Map<TransactionCategory, Long> postApplyDist = new EnumMap<>(TransactionCategory.class);

        for (Transaction tx : transactions) {
            // Preserve manual user classifications
            if (tx.getCategorizationSource() == CategorizationSource.MANUAL) {
                totalPreservedManual++;
                postApplyDist.merge(tx.getCategory() != null ? tx.getCategory() : TransactionCategory.OTHER, 1L, Long::sum);
                continue;
            }

            UUID userId = tx.getUser() != null ? tx.getUser().getId() : null;
            CategorizationRequest request = new CategorizationRequest(
                    userId,
                    tx.getMerchant(),
                    tx.getDescription(),
                    tx.getAmount(),
                    tx.getPaymentMethod() != null ? tx.getPaymentMethod().name() : null,
                    tx.getTransactionDate()
            );

            CategorizationResult result = categorizationService.categorize(request);
            ConfidenceLevel confidenceLevel = result.confidenceLevel();
            boolean isLow = confidencePolicy.isLowConfidence(confidenceLevel);
            TransactionCategory proposedCat = isLow ? TransactionCategory.OTHER : result.category();
            boolean requiresReview = result.requiresUserConfirmation()
                    || isLow
                    || confidencePolicy.requiresConfirmation(confidenceLevel);

            String proposedMerchant = normalizer.deriveDisplayMerchant(tx.getMerchant(), tx.getDescription());
            if (proposedMerchant != null && !proposedMerchant.isBlank() && !"Unknown merchant".equalsIgnoreCase(proposedMerchant)) {
                tx.setMerchant(proposedMerchant);
            }

            boolean changed = tx.getCategory() != proposedCat || tx.isRequiresReview() != requiresReview;
            if (changed) {
                totalUpdated++;
            }

            tx.setCategory(proposedCat);
            tx.setPredictedCategory(result.predictedCategory());
            tx.setRequiresReview(requiresReview);
            tx.setCategorizationConfidence(BigDecimal.valueOf(result.confidence()));
            tx.setCategorizationConfidenceLevel(confidenceLevel);
            tx.setCategorizationSource(result.source());

            if (requiresReview) {
                totalRequiringReview++;
            }
            postApplyDist.merge(proposedCat, 1L, Long::sum);
        }

        transactionRepository.saveAll(transactions);
        log.info("Historical reclassification applied successfully: {} transactions updated, backup saved to {}", totalUpdated, backupFilePath);

        return new HistoricalReclassificationApplyResult(
                startDate,
                endDate,
                transactions.size(),
                totalUpdated,
                totalPreservedManual,
                totalRequiringReview,
                backupFilePath,
                postApplyDist
        );
    }

    /**
     * Fully rolls back a reclassification from a snapshot backup file.
     */
    @Transactional
    public int rollbackReclassification(String backupFilePath) {
        log.info("Rolling back historical reclassification using backup file: {}", backupFilePath);
        try {
            File file = new File(backupFilePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("Backup file does not exist: " + backupFilePath);
            }
            HistoricalReclassificationSnapshot snapshot = OBJECT_MAPPER.readValue(file, HistoricalReclassificationSnapshot.class);
            int restoredCount = 0;

            for (HistoricalReclassificationSnapshot.TransactionState state : snapshot.states()) {
                Optional<Transaction> optTx = transactionRepository.findById(state.transactionId());
                if (optTx.isPresent()) {
                    Transaction tx = optTx.get();
                    tx.setMerchant(state.merchant());
                    tx.setCategory(state.category());
                    tx.setPredictedCategory(state.predictedCategory());
                    tx.setRequiresReview(state.requiresReview());
                    tx.setCategorizationConfidence(state.categorizationConfidence());
                    tx.setCategorizationConfidenceLevel(state.categorizationConfidenceLevel());
                    tx.setCategorizationSource(state.categorizationSource());
                    transactionRepository.save(tx);
                    restoredCount++;
                }
            }
            log.info("Rollback completed: {} transactions restored", restoredCount);
            return restoredCount;
        } catch (Exception e) {
            log.error("Failed to rollback reclassification: {}", e.getMessage(), e);
            throw new RuntimeException("Rollback failed: " + e.getMessage(), e);
        }
    }

    private String persistBackupSnapshot(HistoricalReclassificationSnapshot snapshot) {
        try {
            File dir = new File("data/backups");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String filename = String.format("data/backups/reclassification_backup_%s_%s_%d.json",
                    snapshot.startDate(), snapshot.endDate(), System.currentTimeMillis());
            File file = new File(filename);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, snapshot);
            return file.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist reclassification backup snapshot: " + e.getMessage(), e);
        }
    }
}
