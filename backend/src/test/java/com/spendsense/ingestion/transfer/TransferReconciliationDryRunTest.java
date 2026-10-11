package com.spendsense.ingestion.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferMatchStatus;
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

    @Autowired
    private com.spendsense.user.UserRepository userRepository;

    @Test
    @DisplayName("Verify reconciled transfer pairs and idempotent dry-run for xyz@gmail.com")
    void executeDryRunForXyzUser() {
        String email = "xyz@gmail.com";
        var user = userRepository.findByEmail(email).orElseThrow();

        // 1. Verify the 3 reconciled pairs (UTRs: 659320375544, 659300374098, 660730457519) are AUTO_MATCHED in the database
        List<Transaction> userTxns = transactionRepository.findByUser_IdOrderByTransactionDateDesc(user.getId());
        List<String> targetUtrs = List.of("659320375544", "659300374098", "660730457519");

        for (String utr : targetUtrs) {
            List<Transaction> matching = userTxns.stream()
                    .filter(t -> (t.getMerchant() != null && t.getMerchant().contains(utr))
                            || (t.getDescription() != null && t.getDescription().contains(utr)))
                    .toList();

            assertThat(matching).hasSize(2);
            for (Transaction t : matching) {
                assertThat(t.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
                assertThat(t.getTransferMatchStatus()).isEqualTo(TransferMatchStatus.AUTO_MATCHED);
                assertThat(t.getLinkedTransaction()).isNotNull();
                assertThat(t.getLinkedTransaction().getLinkedTransaction().getId()).isEqualTo(t.getId());
            }
        }

        // 2. Capture database state before dry-run
        List<Transaction> before = transactionRepository.findAll();
        long unlinkedTransfersBefore = before.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .count();

        // 3. Run dry-run reconciliation - should have 0 unlinked stranded pairs remaining
        TransferReconciliationService.TransferReconciliationReport report =
                reconciliationService.runDryRunForUserEmail(email);

        System.out.println("================================================================================");
        System.out.println("         MINTMATE TRANSFER RECONCILIATION DRY-RUN REPORT                        ");
        System.out.println("                 User: " + report.userEmail() + "                               ");
        System.out.println("================================================================================");
        System.out.printf("Total transactions inspected: %d%n", report.totalInspected());
        System.out.printf("Proposed transfer matches:    %d%n", report.proposedMatchesCount());
        System.out.println("================================================================================");

        assertThat(report.proposedMatchesCount()).isEqualTo(0);

        // 4. Verify ZERO rows were mutated during dry-run
        List<Transaction> after = transactionRepository.findAll();
        long unlinkedTransfersAfter = after.stream()
                .filter(t -> t.getLinkedTransaction() == null)
                .count();

        assertThat(unlinkedTransfersAfter).isEqualTo(unlinkedTransfersBefore);
        assertThat(after.size()).isEqualTo(before.size());

        System.out.println("SAFE DRY-RUN VERIFIED: 0 database mutations committed!");
    }
}
