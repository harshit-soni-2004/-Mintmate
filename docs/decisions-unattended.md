# Decisions Made Unattended

## Workstream A: Debt Management
1. **Reference Date for Candidate Matching**:
   - `Debt` entity does not contain a separate `lent_date` / `start_date` field.
   - Per requirement, `createdAt` converted to IST `LocalDate` is used with a named constant `LOOKBACK_DAYS = 7` (`transactionDate >= createdAt.toLocalDate() - 7`).
   - Boundary tests added for exactly 7 days before, 8 days before, and same day.
2. **Candidate Search & Exact Matching Rules**:
   - All candidate transactions for a debt are evaluated on-read via `GET /api/v1/debts/{id}/candidates`.
   - Security Isolation: Scoped strictly to the authenticated user from the security context (`CurrentUser`).
   - Direction Matching:
     - `OWED_TO_ME` (receivable): Requires incoming credits (`INCOME` or `TRANSFER` with direction `IN`).
     - `I_OWE` (payable): Requires outgoing debits (`EXPENSE` or `TRANSFER` with direction `OUT`).
   - Settled-without-link debts: Qualify for candidate suggestions only if counterparty text matches OR transaction date is within 60 days after `settled_at`.
3. **Ranking & Scoring Architecture**:
   - Exact amount match + Counterparty text match: `100` base score (`isStrongMatch = true`).
   - Exact amount match only: `75` base score.
   - Close amount match (within 10% or ₹100 difference) + Counterparty match: `55` base score.
   - Close amount match only: `40` base score.
   - Counterparty text match only: `30` base score.
   - Direction/lookback candidate only: `10` base score.
   - Recency Bonus: `max(0, 10 - min(10, daysBetween(refDate, txDate)))`.
   - Pre-selection Rule: The settle modal dropdown only pre-selects a candidate if `isStrongMatch` is true.
4. **Storage & Action Endpoints**:
   - Candidate suggestions are computed dynamically on read.
   - Dismissals are persisted in `debt_match_dismissals` table (`V20__create_debt_match_dismissals.sql`).
   - Dedicated endpoints:
     - `POST /api/v1/debts/{id}/settle`
     - `POST /api/v1/debts/{id}/link-transaction`
     - `POST /api/v1/debts/{id}/candidates/{transactionId}/dismiss`
5. **Record Payment and Settle Flow (Atomic Settlement)**:
   - Settle modal includes optional *"Also record this payment in my transactions"* checkbox.
   - Created as `TRANSFER` in category `OTHER` (invariance of income, expenses, and savings rate).
   - Single atomic transaction: `POST /api/v1/debts/{id}/record-payment-and-settle`.
6. **Duplicate Guard on Statement Import**:
   - Matches against manual debt repayments (`sourceReference.startsWith("debt:repayment:")`) within 2 days with exact amount are flagged as `DUPLICATE_SUSPECT` (`requiresReview = true`) rather than dropped.

## Workstream B: Authentication (Google Sign-In + Email OTP)
1. **Pre-Registration Takeover Protection**:
   - When a user signs in via Google with an existing email whose `email_verified` is false:
     - The Google `sub` is linked.
     - `email_verified` is marked `true`.
     - `password_hash` is invalidated (`user.invalidatePassword()`, set to `null`).
     - Rationale: Prevents an attacker from pre-registering a victim's email address with a known password to retain persistent backdoor access after the victim signs in with Google.
2. **Password Login Rejection for Google-Created Accounts**:
   - Accounts created via Google Sign-In have `password_hash = null`.
   - When attempting password authentication (`/api/v1/auth/login`), if `user.getPasswordHash() == null`, `AuthService` rejects the request with the identical generic `INVALID_CREDENTIALS` (401) error code as an incorrect password, completely preventing account enumeration.
3. **Email OTP Hash Storage & Cryptography**:
   - Plaintext OTPs are never stored in the database.
   - Codes are generated via `SecureRandom` as 6-digit numeric strings (`000000`–`999999`) and stored exclusively as HMAC-SHA256 digests (`code_hash`) keyed by server-side `OTP_HMAC_SECRET`.
   - Verification uses constant-time comparison (`MessageDigest.isEqual`) to prevent timing side-channel attacks.
4. **Anti-Enumeration in OTP Generation**:
   - `/api/v1/auth/otp/send` returns the exact same success response (`"If an account is associated with this email, a verification code has been sent."`) regardless of whether the email exists in the database.
   - For non-existent emails, a dummy HMAC computation is executed to ensure equivalent timing.
5. **Rate Limiting & Cooldown**:
   - 60-second cooldown per email enforced between OTP send requests.
   - Hourly limit of 5 OTP requests per email.
   - In-memory rate limiting of max 20 requests per hour per client IP.
6. **No SMS/Phone OTP**:
   - Exclusively email OTP as specified in task scope.
