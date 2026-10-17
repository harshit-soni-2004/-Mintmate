# Decisions Made Unattended

## Workstream A: Debt Management
1. **Reference Date for Candidate Matching**:
   - `Debt` entity does not contain a separate `lent_date` / `start_date` field.
   - Per requirement, `createdAt` converted to IST `LocalDate` is used with a named constant `LOOKBACK_DAYS = 7` (`transactionDate >= createdAt.toLocalDate() - 7`).
   - Boundary tests added for exactly 7 days before, 8 days before, and same day.
2. **Separate Local Database Configuration & Branch Merge Plan**:
   - **Database Isolation**: Database connectivity is driven via environment variables with zero password fallback in `application.yml`:
     - `DB_PASSWORD`: Read directly from environment (no default/fallback).
     - `DB_NAME`: Defaults to branch-scoped database (`spendsense_auth` on `feat/auth-google-email-otp`, `spendsense_debt` on `feat/debt-reminders-and-matching`), overridable via `DB_NAME` or `DB_URL`.
     - Hand-written DDL and `flyway:repair` are strictly avoided.
   - **Merge Plan**:
     - **Step 1**: The auth branch (`feat/auth-google-email-otp`) merges first into `develop` with migrations `V17__add_google_auth_and_email_verification_to_users.sql` and `V18__create_email_otps.sql`.
     - **Step 2**: The debt branch (`feat/debt-reminders-and-matching`) is then rebased onto `develop`.
     - **Step 3**: The debt dismissal migration `V17__create_debt_match_dismissals.sql` is renamed to `V19__create_debt_match_dismissals.sql` (renaming only, as it has never been applied to any remote/shared database).
     - **Step 4**: Complete test verification and merge.
3. **Statement Import Duplicate Guard for Repayments**:
   - Rule: When an imported statement transaction matches a manually created repayment (`direction` match, `amount` match, transaction date within +/- 2 days), it is flagged as a potential duplicate (`DUPLICATE_SUSPECT`) rather than automatically merged or discarded, surfacing it in the reconciliation UI for user review.

## Workstream B: Authentication (Google Sign-In + Email OTP)
1. **Pre-Registration Takeover Protection**:
   - When a user signs in via Google with an existing email whose `email_verified` is false (e.g. unverified account created via password registration):
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
