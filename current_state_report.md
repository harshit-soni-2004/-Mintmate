# SpendSense — Current State Report

## A. Current Git Branch

`main` (single production branch, no feature branches in flight)

---

## B. Git Status

**Large body of uncommitted work** exists. This is the V10-V12 financial-account/transfer work that was implemented but never committed on top of the last commit (`80d0222 feat: complete statement intelligence and ingestion`).

### Modified files (27):

| File | Area |
|------|------|
| `AnalyticsService.java` | Analytics — now tracks totalTransfers + investmentContributions |
| `SpendingSummaryResponse.java` | Analytics DTO — extended with transfer/investment fields |
| `DashboardService.java` | Dashboard — now segregates TRANSFER/INVESTMENT from EXPENSE/INCOME |
| `DashboardSummaryResponse.java` | Dashboard DTO — extended with totalTransfers + investmentContributions |
| `FinancialDocument.java` | Document entity — now has `account` FK to FinancialAccount |
| `FinancialDocumentService.java` | Document service — transfer unlinking on delete |
| `FinancialDocumentResponse.java` | Document DTO — includes accountId/accountName |
| `StatementNormalizer.java` | Normalizer — CASH_WITHDRAWAL, INVESTMENT classification added |
| `PdfStatementExtractor.java` | PDF extractor — layout fallback, SBI/Kotak real-statement support |
| `StatementIngestionService.java` | Ingestion pipeline — full V10 pipeline with transfer matching wiring |
| `SettlementResolutionService.java` | Settlement — minor updates |
| `StatementSettlementRepository.java` | Settlement repo |
| `Transaction.java` | Entity — account_id, transfer_direction, counterparty_account_id, linked_transaction_id, transfer_match_status |
| `TransactionRepository.java` | Repo — new queries: findUnmatchedTransfers, sumByDirection, findPotentialTransferCounterparts |
| `TransactionType.java` | Enum — TRANSFER, INVESTMENT, CASH_WITHDRAWAL added |
| `TransactionResponse.java` | DTO — full transfer metadata included |
| `FinancialDocumentServiceTest.java` | Test — updated for V10 |
| `CsvStatementExtractorTest.java` | Test — updated |
| `PdfInspectionTest.java` | Test — updated |
| `PdfStatementExtractorTest.java` | Test — updated |
| `StatementIngestionServiceTest.java` | Test — updated |
| `api.types.ts` | Frontend — FinancialAccount, TransferDirection, TransferMatchStatus |
| `dashboard.component.ts` | Frontend — displays totalTransfers/investmentContributions |
| `statements.component.ts` | Frontend — account form fields |
| `transactions.component.html` | Frontend — transfer badges |
| `transactions.component.scss` | Frontend — transfer styling |
| `transactions.component.ts` | Frontend — transfer-aware display |

### Untracked (new) files:

| File/Dir | Area |
|----------|------|
| `account/` package (FinancialAccount, Controller, Repository, Service, dto/) | New domain package |
| `ingestion/transfer/TransferMatchingService.java` | Transfer matching engine |
| `transaction/TransferDirection.java` | New enum |
| `transaction/TransferMatchStatus.java` | New enum |
| `db/migration/V10__create_financial_accounts_and_support_transfers.sql` | Flyway V10 |
| `db/migration/V11__reconcile_existing_transfers_and_cash_withdrawals.sql` | Flyway V11 |
| `db/migration/V12__repair_historical_accounts_and_transfers.sql` | Flyway V12 |
| `ingestion/KotakSbiTransferIngestionIntegrationTest.java` | New integration test |
| `ml/models/`, `ml/reports/*.json` | ML artifacts |

> [!IMPORTANT]
> **Nothing from V10-V12 has been committed.** All this work lives only in the working tree. The last committed state (`80d0222`) predates V10.

---

## C. Latest Commits

```
80d0222 (HEAD -> main) feat: complete statement intelligence and ingestion
6770b41 feat: add intelligent transaction categorization UX
1fe7535 feat: integrate semantic transaction categorization
600d509 feat: add financial intelligence and analytics
531f288 feat: add budgets and financial goals
aca11ba feat: complete phase 4a transaction foundation
c7ef2b8 chore: establish SpendSense foundation
```

All V10/V11/V12 work is **uncommitted**.

---

## D. Current Flyway Version

**Filesystem has V1 through V12** (V10, V11, V12 are git-untracked).

> [!WARNING]
> V10-V12 migration files exist on disk but are git-untracked. Whether they have been applied to the live MySQL database cannot be confirmed from the repository alone. The previous agent reported V12 was applied, but this must be verified by querying `flyway_schema_history` directly.

---

## E. Current Transaction Model

**[Transaction.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/transaction/Transaction.java)** — 308 lines, full V10 model:

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | PK |
| `user` | User | ManyToOne |
| `amount` | BigDecimal(19,2) | |
| `merchant` | String(120) | |
| `description` | String(255) | |
| `category` | TransactionCategory | FOOD/TRAVEL/SHOPPING/ENTERTAINMENT/BILLS/HEALTH/EDUCATION/PERSONAL/OTHER |
| `transactionType` | TransactionType | **EXPENSE / INCOME / TRANSFER / INVESTMENT / CASH_WITHDRAWAL** |
| `paymentMethod` | PaymentMethod | UPI/CREDIT_CARD/DEBIT_CARD/CASH/BANK_TRANSFER/OTHER |
| `transactionDate` | LocalDate | |
| `notes` | String(1000) | |
| `categorizationConfidence` | BigDecimal(5,4) | Phase 6 ML |
| `categorizationConfidenceLevel` | ConfidenceLevel | HIGH/MEDIUM/LOW/NONE |
| `categorizationSource` | CategorizationSource | RULE/USER_MEMORY/ML/FALLBACK/MANUAL |
| `requiresReview` | boolean | |
| `predictedCategory` | TransactionCategory | |
| `categorizationAlternatives` | String(1000) | JSON |
| `sourceType` | TransactionSourceType | MANUAL/STATEMENT_IMPORT |
| `sourceDocument` | FinancialDocument | ManyToOne |
| `sourceReference` | String(120) | `doc:{id}:row:{n}[:externalRef]` |
| **`account`** | FinancialAccount | **V10** — owning account |
| **`transferDirection`** | TransferDirection | **V10** — IN/OUT |
| **`counterpartyAccount`** | FinancialAccount | **V10** |
| **`linkedTransaction`** | Transaction | **V10** — opposite transfer leg |
| **`transferMatchStatus`** | TransferMatchStatus | **V10** — NONE/UNMATCHED/AUTO_MATCHED/MANUALLY_CONFIRMED |

Helper methods: `linkTransfer()`, `unlinkTransfer()` cleanly implemented.

---

## F. Current FinancialAccount Model

**[FinancialAccount.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/account/FinancialAccount.java)** — 130 lines:

| Field | Notes |
|-------|-------|
| `id` | UUID PK |
| `user` | ManyToOne |
| `name` | Display name e.g. "State Bank of India (SAVINGS - 8821)" |
| `institutionName` | "State Bank of India", "Kotak Mahindra Bank" |
| `accountType` | DocumentAccountType: SAVINGS/CURRENT/CREDIT_CARD/INVESTMENT/CASH_WALLET |
| `accountNumberLast4` | "8821" (SBI), "1795" (Kotak) — V12 verified values |
| `currency` | "INR" default |
| `active` | boolean |
| `primary` | boolean |

**`getOrCreateForStatement()` key logic:**
- `institutionName == null AND last4 == null` → creates unique "Unspecified (TYPE)" account each call (no silent collapsing)
- Otherwise: match by `institutionName + accountType + last4`, or create new

**V12 verified account mapping:**
- SBI → `State Bank of India`, last4 = `8821`
- Kotak → `Kotak Mahindra Bank`, last4 = `1795`

---

## G. Current Statement Ingestion Pipeline

**[StatementIngestionService.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/ingestion/service/StatementIngestionService.java)** — Full 586-line pipeline:

1. **Document retrieval** + extractor dispatch (PDF/CSV/XLSX via `DocumentExtractor`)
2. **Account association** — `getOrCreateForStatement()` if document has no account yet
3. **Bounded pre-fetch** — existing transactions for date range ±5 days
4. **Per-candidate pipeline:**
   - `CandidateValidator.validate()` — structural validation
   - `DeduplicationService.checkDuplicate()` — exact + probable dup (account-scoped)
   - `SettlementResolutionService.processCandidate()` — settlement/refund detection
   - **Classification priority:** `CASH_WITHDRAWAL` → `INVESTMENT` → `TRANSFER` → `INCOME/EXPENSE`
   - TRANSFER detection: keywords + bilateral counterpart search + account reference matching
   - ML categorization (Phase 6) — skipped for TRANSFER/INVESTMENT/CASH_WITHDRAWAL
   - Persist transaction with full account + transfer metadata
   - Post-persist: `retroactivelyUpgradeAndMatch()` or `attemptMatch()`

**Extractors:**
- [PdfStatementExtractor.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/ingestion/extractor/PdfStatementExtractor.java) — PDFBox 3.x, coordinate-aware column mapping, 1073 lines
- `CsvStatementExtractor.java` — multi-format CSV
- `ExcelStatementExtractor.java` — XLS/XLSX

---

## H. Current Transfer Matching Pipeline

**[TransferMatchingService.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/ingestion/transfer/TransferMatchingService.java)** — 455 lines, fully deterministic (no LLM):

**4 confidence signals (any one sufficient):**
1. Matching 12-digit UTR/RRN in merchant/description text
2. Matching `sourceReference` (>=6 chars, after stripping `doc:...:row:...:` prefix)
3. Cross-bank institution name in text + user name signal (display name/email prefix tokens)
4. Internal transfer keywords (`OWN ACC`, `SELF TRF`, `SENT TO SBI`, etc.) + user name signal

**Hard match criteria (all required):**
- Same user, different financial accounts
- Same amount (exact BigDecimal)
- Date within 3 days
- Opposing direction (IN vs. OUT)

**`attemptMatch()` flow:**
1. Find existing `UNMATCHED` TRANSFER legs (opposing direction, same amount) → confidence filter → link both as `AUTO_MATCHED`
2. Else: find EXPENSE/INCOME candidates (retroactive upgrade path) → confidence filter → upgrade both to TRANSFER + link
3. Else: mark as `UNMATCHED` (never falls back to EXPENSE)

Upload-order independence confirmed: both Kotak-first and SBI-first paths work.
`unlinkBeforeDeletion()` properly handles counterpart unlinking on document deletion.

---

## I. Current Dashboard Aggregation Semantics

**[DashboardService.java](file:///c:/Users/ihars/Desktop/Workspace/SpendSense/backend/src/main/java/com/spendsense/dashboard/DashboardService.java)** — correct semantics verified:

| Metric | SQL semantics |
|--------|--------------|
| `totalIncome` | `SUM WHERE type = INCOME` |
| `totalExpenses` | `SUM WHERE type = EXPENSE` |
| `netSavings` | `totalIncome - totalExpenses` |
| `savingsRate` | `max(0, netSavings) / totalIncome * 100` |
| `totalTransfers` | `SUM WHERE type = TRANSFER AND direction = OUT` |
| `investmentContributions` | `SUM WHERE type = INVESTMENT` |

- Category spending: `EXPENSE` only ✅
- Daily spending chart: `EXPENSE + INCOME` only, TRANSFER excluded ✅
- Analytics category breakdown: `EXPENSE` only ✅
- CASH_WITHDRAWAL: excluded from all ordinary metrics ✅

---

## J. Current Tests / Build Status

### Backend — PASSING

```
Tests run: 277, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Notable passing test classes:
- `KotakSbiTransferIngestionIntegrationTest` — 7 tests (transfer matching integration)
- `StatementIngestionServiceTest` — 20 tests
- `StatementIngestionAnalyticsIntegrationTest` — 2 tests
- `PdfStatementExtractorTest` — 12 tests
- `FinancialDocumentServiceTest` — 16 tests
- All categorization, dashboard, analytics, auth tests passing

### Frontend — Verification in progress
Previous reported state: 70/70 tests passing. Test run was kicked off during this inspection.

---

## K. Known Issues / Discrepancies

### Issue 1 — CRITICAL: V10-V12 work is entirely uncommitted
All 27 modified files and 11 new files (V10-V12 implementation) exist only in the working tree.
The last git commit (`80d0222`) predates this entire phase.
**Risk: Any working directory reset would permanently lose this work.**

### Issue 2 — Flyway DB apply status unverifiable from filesystem
The migration files exist on disk but the actual DB schema_history table must be queried to confirm V12 was applied.

### Issue 3 — Duplicate INTERNAL_TRANSFER_KEYWORDS pattern
The same regex pattern is defined as `public static final` in `TransferMatchingService` and independently as a local `static final` in `StatementIngestionService`. Minor maintainability smell; `StatementIngestionService` should reference `TransferMatchingService.INTERNAL_TRANSFER_KEYWORDS`.

### Issue 4 — `findMatchingAccount()` null-last4 fuzzy match
When `last4 = null`, the JPQL `(:last4 IS NULL OR a.accountNumberLast4 = :last4)` clause matches accounts of the same institution/type regardless of last4. Acceptable now (real accounts have verified last4) but a latent ambiguity risk for future ambiguous uploads.

### Issue 5 — Phase 8A not yet started
No AI Financial Assistant architecture or implementation exists. Per project scope, Phase 8A design (tool contracts, guardrails, prompt architecture) is the recommended next major phase.

---

## L. Recommended Immediate Next Steps

### Step 1 (Urgent): Commit the V10-V12 working tree

```bash
git add -A
git commit -m "feat: V10-V12 financial accounts, transfer matching, and historical reconciliation

- Add FinancialAccount domain (entity, service, repository, controller)
- Extend Transaction with account_id, transfer_direction, counterparty_account_id,
  linked_transaction_id, transfer_match_status
- Add TransactionType: TRANSFER, INVESTMENT, CASH_WITHDRAWAL
- Add TransferMatchingService: deterministic 4-signal bilateral matching
- Update StatementIngestionService: full V10 pipeline with transfer/investment/
  cash-withdrawal classification and post-persist matching
- Update DashboardService/AnalyticsService: correct income/expense/transfer/
  investment semantic segregation
- Add Flyway V10/V11/V12 migrations
- Add KotakSbiTransferIngestionIntegrationTest (7 tests)
- Update frontend: FinancialAccount types, transfer badges, dashboard metrics
- All 277 backend tests passing"
```

### Step 2: Design Phase 8A — AI Financial Assistant Architecture
Define tool contracts, guardrails, and prompt architecture before any LLM integration code:
- Read-only tools (spending, budgets, goals, analytics, transactions, insights)
- Confirmation-required tools
- Hallucination protection boundary (LLM must call tools, never calculate from memory)
- User-data isolation per tool call
- Conversation state design
- Error handling + fallback strategy
- Model/provider abstraction layer

> [!IMPORTANT]
> Do not implement any LLM integration until the architecture document is reviewed and approved.
