package com.spendsense.ingestion.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/spendsense?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "spring.datasource.username=root",
        "spring.datasource.password=Harshit#$04",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
class TransferReconciliationDryRunTest {

    @Autowired
    private TransferReconciliationService reconciliationService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Execute dry-run transfer reconciliation for xyz@gmail.com - 3 stranded pairs identified with zero mutations")
    void executeDryRunForXyzUser() {
        String email = "xyz@gmail.com";

        // 1. Capture database state before dry-run
        List<Transaction> before = transactionRepository.findAll();
        long unlinkedTransfersBefore = before.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .count();

        // 2. Run dry-run reconciliation
        TransferReconciliationService.TransferReconciliationReport report =
                reconciliationService.runDryRunForUserEmail(email);

        System.out.println("================================================================================");
        System.out.println("         MINTMATE TRANSFER RECONCILIATION DRY-RUN REPORT                        ");
        System.out.println("                 User: " + report.userEmail() + "                               ");
        System.out.println("================================================================================");
        System.out.printf("Total transactions inspected: %d%n", report.totalInspected());
        System.out.printf("Proposed transfer matches:    %d%n", report.proposedMatchesCount());
        System.out.println("--------------------------------------------------------------------------------");

        for (int i = 0; i < report.proposedMatches().size(); i++) {
            TransferReconciliationService.ProposedTransferMatch match = report.proposedMatches().get(i);
            System.out.printf("MATCH #%d: Amount: INR %s%n", i + 1, match.amount());
            System.out.printf("  [DEBIT]  ID: %s | Date: %s | Account: %s%n",
                    match.debitTransactionId(), match.debitDate(), match.debitAccountName());
            System.out.printf("           Merchant: %s%n", match.debitMerchant());
            System.out.printf("           Extracted UTR: %s%n", match.debitUtr());
            System.out.printf("  [CREDIT] ID: %s | Date: %s | Account: %s%n",
                    match.creditTransactionId(), match.creditDate(), match.creditAccountName());
            System.out.printf("           Merchant: %s%n", match.creditMerchant());
            System.out.printf("           Extracted UTR: %s%n", match.creditUtr());
            System.out.printf("  Signal:  %s%n%n", match.matchEvidence());
        }
        System.out.println("================================================================================");

        // 3. Verify exactly the 3 stranded pairs are detected
        assertThat(report.proposedMatchesCount()).isEqualTo(3);

        List<BigDecimal> matchedAmounts = report.proposedMatches().stream()
                .map(TransferReconciliationService.ProposedTransferMatch::amount)
                .toList();

        assertThat(matchedAmounts).containsExactlyInAnyOrder(
                new BigDecimal("7337.00"),
                new BigDecimal("950.00"),
                new BigDecimal("1250.00")
        );

        // Verify UTRs match the known transaction pairs
        List<String> matchedUtrs = report.proposedMatches().stream()
                .map(TransferReconciliationService.ProposedTransferMatch::debitUtr)
                .toList();

        assertThat(matchedUtrs).containsExactlyInAnyOrder(
                "659320375544",
                "659300374098",
                "660730457519"
        );

        // 4. Verify ZERO rows were mutated in the database (DRY-RUN ONLY!)
        List<Transaction> after = transactionRepository.findAll();
        long unlinkedTransfersAfter = after.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .count();

        assertThat(unlinkedTransfersAfter).isEqualTo(unlinkedTransfersBefore);
        assertThat(after.size()).isEqualTo(before.size());

        java.util.Map<java.util.UUID, Transaction> beforeMap = before.stream()
                .collect(java.util.stream.Collectors.toMap(Transaction::getId, t -> t));

        for (Transaction a : after) {
            Transaction b = beforeMap.get(a.getId());
            assertThat(b).isNotNull();
            assertThat(a.getTransactionType()).isEqualTo(b.getTransactionType());
            if (b.getLinkedTransaction() == null) {
                assertThat(a.getLinkedTransaction()).isNull();
            } else {
                assertThat(a.getLinkedTransaction()).isNotNull();
                assertThat(a.getLinkedTransaction().getId()).isEqualTo(b.getLinkedTransaction().getId());
            }
        }

        System.out.println("SAFE DRY-RUN VERIFIED: 0 database mutations committed!");
    }
}
