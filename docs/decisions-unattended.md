# Unattended Decisions: Debt Reminders & Repayment Matching

## 1. Candidate Search & Filtering Rules

All candidate transactions for a debt are evaluated on-read via `GET /api/v1/debts/{id}/candidates` according to the following canonical criteria:

- **Security Isolation**: Scoped strictly to the authenticated user from the security context (`CurrentUser`). Foreign debt IDs or transactions return `404 NOT_FOUND` or `403 FORBIDDEN`.
- **Direction Matching**:
  - `OWED_TO_ME` (receivable): Requires incoming credits (`INCOME` or `TRANSFER` with direction `IN`).
  - `I_OWE` (payable): Requires outgoing debits (`EXPENSE` or `TRANSFER` with direction `OUT`).
- **Date Boundary & Lookback**:
  - Reference date is the debt creation date (`createdAt` in `Asia/Kolkata` / IST zone).
  - A transaction qualifies if `transactionDate >= createdAt.minusDays(7)` (`CREATED_AT_LOOKBACK_DAYS = 7`).
  - Due date (`dueDate`) is never used for candidate lookback.
- **Global Linkage Exclusion**:
  - Any transaction already linked to any debt of the user is excluded from candidate lists.
  - Confirming and linking a transaction to one debt immediately removes that transaction from all other debts' candidate suggestions.
- **Dismissal Filtering**:
  - Any transaction dismissed by the user for this specific debt is excluded.
- **Settled-Without-Link Debts (Part 3 Ingestion Matching)**:
  - Debts with status `SETTLED` that already have a `linkedTransaction` return an empty candidate list.
  - Debts with status `SETTLED` without a linked transaction qualify for candidate suggestions **only if**:
    1. A counterparty name match exists (`matchesCounterparty` on merchant or narration), **OR**
    2. The transaction date falls within 60 days after `settled_at` (`transactionDate <= settledDate + 60 days`).
  - Transactions dated outside 60 days after settlement without a counterparty text match are strictly excluded.

---

## 2. Ranking & Scoring Architecture

Candidates are scored deterministically by the backend so that frontend clients do not duplicate ranking logic:

- **Base Scoring Categories**:
  - Exact amount match + Counterparty text match: `100` base score (`isStrongMatch = true`).
  - Exact amount match only: `75` base score.
  - Close amount match (within 10% or ₹100 difference) + Counterparty match: `55` base score.
  - Close amount match only: `40` base score.
  - Counterparty text match only: `30` base score.
  - Direction/lookback candidate only: `10` base score.
- **Recency Bonus**: `max(0, 10 - min(10, daysBetween(refDate, txDate)))`.
- **Total Score**: `(baseScore * 100) + recencyBonus`.
- **Pre-selection Rule**: The settle modal dropdown only pre-selects a candidate if `isStrongMatch` is true (exact amount AND counterparty name match). Otherwise, top suggestions are displayed under "Suggested" with "No linked transaction" selected by default.

---

## 3. Storage & Action Endpoints

- **On-Read Computation**: Candidate suggestions are computed dynamically on read. No suggestions or tentative match tables are written during statement import.
- **Dismissal Persistence**: Only explicit user dismissals ("Not a match") are persisted to the database in the `debt_match_dismissals` table (Flyway migration `V17__create_debt_match_dismissals.sql`), with unique constraint on `(user_id, debt_id, transaction_id)`.
- **Dedicated Link Path vs Settle**:
  - `POST /api/v1/debts/{id}/settle`: Settles an `ACTIVE` debt (with optional linked transaction). Calling this on an already-settled debt returns `400 BAD_REQUEST` (`DEBT_ALREADY_SETTLED`).
  - `POST /api/v1/debts/{id}/link-transaction`: Dedicated endpoint to attach a transaction to an already `SETTLED` debt without a link. Calling on an `ACTIVE` debt returns `400 BAD_REQUEST` (`DEBT_NOT_SETTLED`).
  - `POST /api/v1/debts/{id}/candidates/{transactionId}/dismiss`: Persists user dismissal for a candidate match.
- **Reconciliation & Double-Counting Prevention**:
  - Both `settle` and `link-transaction` paths reconcile the linked transaction to `TRANSFER`:
    - `OWED_TO_ME`: Reclassified as `TRANSFER IN` and `IncomeTier.TIER_3_REFUND` (excluded from regular salary/income metrics).
    - `I_OWE`: Reclassified as `TRANSFER OUT` with category `OTHER` (protects discretionary category budgets from distortion).

---

## 4. Ingestion Hook Isolation

- Statement ingestion in `StatementIngestionService` invokes `debtService.evaluateSuggestionsAfterIngestion(user.getId(), importedTransactions)` after persisting new transactions.
- **Failure-Isolated**: Enclosed in a strict try/catch block. If matching evaluation fails or throws an exception, it is logged as a warning; statement ingestion always succeeds and never rolls back.
- **Suggest-Only**: Statement import never automatically settles debts or reclassifies transactions without explicit user confirmation.

---

## 5. Record Payment and Settle Flow (Atomic Settlement)

- **Optional Selection**: When no candidate transaction is linked, the Settle Debt modal provides an unchecked checkbox: *"Also record this payment in my transactions"*.
- **Direction Lock**: The transaction direction is fixed by the debt type and cannot be edited:
  - `OWED_TO_ME`: Created as `TRANSFER` with direction `IN`, category `OTHER`, and `IncomeTier.TIER_3_REFUND` (excluded from core salary/income calculations).
  - `I_OWE`: Created as `TRANSFER` with direction `OUT`, category `OTHER` (excluded from expense totals and budget category burn).
- **Invariance of Analytics**: Because the transaction is created as a `TRANSFER` in category `OTHER`, income, expenses, and savings rate remain unchanged.
- **Single Atomic Operation**: Executed in a single backend database transaction via `POST /api/v1/debts/{id}/record-payment-and-settle` (never multiple frontend calls). If either creation or settlement fails, all changes are rolled back.
- **Source Identification**: Stamped with `sourceType = MANUAL` and `sourceReference = "debt:repayment:<debtId>"`.

---

## 6. Duplicate Guard on Statement Import

- **Current Deduplication Baseline**: `DeduplicationService.checkDuplicate` identifies exact duplicates (same date, same amount, same direction, matching merchant/ref) and drops them automatically during ingestion (`exactDuplicates++`). Non-exact candidates with fuzzy or amount-date matches are flagged as probable duplicates (`requiresReview = true`).
- **Debt Repayment Guard Rule**:
  - When an incoming imported candidate matches a manually created debt repayment (`sourceType == MANUAL` and `sourceReference.startsWith("debt:repayment:")`), same user, matching direction, exact amount, and `transactionDate` within 2 days (`|candidate.date - repayment.date| <= 2`):
  - **Do NOT auto-merge or drop**: The system strictly prevents silent dropping.
  - **Deterministic Flagging**: Returns `DuplicateCheckResult.probable(...)`, causing the statement ingestion flow to persist the candidate with `requiresReview = true` so the user can explicitly review and confirm whether it is a duplicate or an independent transaction.
  - Near-miss amounts, opposite directions, or dates outside the 2-day window are NOT flagged by this guard.
