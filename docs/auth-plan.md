# Workstream B: Authentication Plan (Google Sign-in & Email OTP)

## 1. Overview & Objectives

Workstream B enhances SpendSense / Mintmate authentication by introducing:
1. **Sign in with Google** via Google Identity Services (GIS) on web and server-side ID token verification.
2. **Email OTP Verification** for user signup and resend verification with cryptographically secure storage and rate limiting.

### Constraints & Invariants
- Existing email/password login and JWT token generation remain completely functional and backward compatible.
- AI Assistant / Phase 8A and visual tokens remain untouched.
- All secrets loaded strictly from environment variables with safe defaults for dev/test (`GOOGLE_CLIENT_ID`, `SMTP_*`, `OTP_HMAC_SECRET`).
- Zero external network calls in automated tests (stubbed verifiers and mail services).

---

## 2. Database & Schema Changes (Flyway)

Branch `feat/auth-google-email-otp` is cut from `develop` where the latest migration is `V16__create_debt_tracker.sql`. Therefore, the following migrations are allocated:

### A. `V17__add_google_auth_and_email_verification_to_users.sql`
- Add `google_sub VARCHAR(255) NULL` with unique constraint `uk_users_google_sub`.
- Add `email_verified BOOLEAN NOT NULL DEFAULT FALSE`.
- Modify `password_hash VARCHAR(255) NULL` (allow null for OAuth-only users or invalidated passwords).

> **Flyway Collision Notice**: Branch `feat/debt-reminders-and-matching` also created a migration `V17__create_debt_match_dismissals.sql`. When merging both branches to `develop`, one of them must be renumbered to `V18` or `V19`.

### B. `V18__create_email_otps.sql`
- Table `email_otps`:
  - `id CHAR(36) NOT NULL PRIMARY KEY`
  - `email VARCHAR(320) NOT NULL`
  - `purpose VARCHAR(32) NOT NULL` (e.g., `SIGNUP_VERIFICATION`)
  - `code_hash VARCHAR(128) NOT NULL` (HMAC-SHA256 digest)
  - `expires_at DATETIME(6) NOT NULL`
  - `attempts INT NOT NULL DEFAULT 0`
  - `max_attempts INT NOT NULL DEFAULT 5`
  - `consumed_at DATETIME(6) NULL`
  - `created_at DATETIME(6) NOT NULL`
  - Indexes on `(email, purpose, created_at)` and `(expires_at)`.

---

## 3. Google Sign-in Architecture

### Flow
1. **Frontend**:
   - Google Identity Services (GIS) button on Login and Register pages.
   - On credential receipt, the Google ID token (JWT) is sent to `POST /api/v1/auth/google`.
2. **Backend**:
   - Token verified cryptographically via `GoogleTokenVerifier`:
     - Signature verified against Google's public certificates.
     - Audience must match `GOOGLE_CLIENT_ID`.
     - Issuer must be `https://accounts.google.com` or `accounts.google.com`.
     - Expiration time strictly enforced.
     - Claim `email_verified` must be `true`.
   - Extracted verified claims: `email` and `sub`.
3. **Account Resolution Order**:
   - **Case 1 (Sub match)**: A user with `google_sub == sub` exists -> Issue SpendSense JWT and log in.
   - **Case 2 (Email match without sub)**:
     - If user is `email_verified == true`: Link `google_sub = sub` and log in.
     - If user is `email_verified == false` (pre-registration takeover risk): Link `google_sub = sub`, mark `email_verified = true`, and **invalidate existing password** (`password_hash = null`) so an attacker who pre-registered the address cannot log in.
   - **Case 3 (New user)**: Create user with `email`, `displayName` from token, `google_sub = sub`, `email_verified = true`, `password_hash = null`. Issue SpendSense JWT and log in.
4. **No-Password Login Protection**:
   - Users without a password (`password_hash == null`) attempting email/password login receive `401 INVALID_CREDENTIALS` (same generic message as wrong password, avoiding user enumeration).

---

## 4. Email OTP Design & Security

### Flow & Scope
- **Scope**: Email verification at registration and verification code resend. Passwordless login is excluded from this scope as email/password is the established flow.
- **Code Generation**: 6-digit numeric string generated via `SecureRandom`.
- **HMAC Storage**:
  - Code is HMAC-SHA256 hashed with server-side secret `OTP_HMAC_SECRET`.
  - Raw code is **never** persisted to the database.
- **Expiry & Invalidation**:
  - Code expires in 10 minutes.
  - Single-use: once verified, `consumed_at` is stamped.
  - Max 5 failed attempts: if attempts reach 5, the OTP record is permanently invalidated.
  - Verification uses `MessageDigest.isEqual` to prevent timing attacks.
- **Cooldown & Rate Limiting**:
  - Resend cooldown: 60 seconds per email.
  - Rate limits: Max 5 OTP requests per hour per email, and max 20 per hour per IP (in-memory rate limiter).
- **Anti-Enumeration**:
  - The API returns generic success messages regardless of whether the email is registered or not.
- **Email Delivery Abstraction**:
  - `EmailService` interface with implementations:
    - `StubEmailService` (active under `dev`, `test` profiles): Logs code locally, does not connect to real SMTP.
    - `SmtpEmailService` (active under `prod` profile): Sends email via JavaMail/SMTP without logging OTP.
- **Cleanup**:
  - Spring `@Scheduled` task cleans up expired/consumed OTP rows periodically.

---

## 5. Files to Create and Modify

### Backend
- **Entities & Repositories**:
  - `com.spendsense.user.User`: Add `googleSub`, `emailVerified`, make `passwordHash` nullable.
  - `com.spendsense.auth.otp.EmailOtp`: Entity for OTP records.
  - `com.spendsense.auth.otp.EmailOtpRepository`: JPA repository.
- **Security & Google Verification**:
  - `com.spendsense.auth.google.GoogleTokenVerifier`: Interface.
  - `com.spendsense.auth.google.GoogleTokenVerifierImpl`: Real implementation using Google API Client / JWKS.
  - `com.spendsense.auth.google.GoogleAuthProperties`: Configuration (`google.client-id`).
- **OTP & Email**:
  - `com.spendsense.auth.otp.OtpService`: Generation, hashing, cooldown, rate-limiting, and verification.
  - `com.spendsense.mail.EmailService`: Interface.
  - `com.spendsense.mail.StubEmailService`: Dev/test logger.
  - `com.spendsense.mail.SmtpEmailService`: SMTP sender.
- **Controllers & DTOs**:
  - `com.spendsense.auth.AuthController`: Add `POST /google`, `POST /otp/send`, `POST /otp/verify`.
  - DTOs: `GoogleLoginRequest`, `SendOtpRequest`, `VerifyOtpRequest`.
- **Migrations**:
  - `V17__add_google_auth_and_email_verification_to_users.sql`
  - `V18__create_email_otps.sql`

### Frontend
- **API & Core**:
  - `core/api/api.types.ts`: Add `GoogleLoginRequest`, `SendOtpRequest`, `VerifyOtpRequest`.
  - `core/auth/auth.service.ts`: Add `loginWithGoogle`, `sendOtp`, `verifyOtp`.
- **UI Components**:
  - `features/auth/login.component.html/.ts`: Add Google Sign-in button with GIS script loader.
  - `features/auth/register.component.html/.ts`: Add Google Sign-in button; redirect to OTP verification screen on registration.
  - `features/auth/verify-otp.component.ts/.html/.scss`: OTP verification form with 60s resend timer and friendly alerts.
  - `app.routes.ts`: Add route `/verify-email`.

---

## 6. Risks & Mitigations Summary

| Risk | Mitigation |
| :--- | :--- |
| Pre-registration takeover | Invalidate password hash and force reset on Google linking of unverified accounts. |
| Account enumeration | Generic responses on OTP request and login errors. |
| Brute force OTP | Max 5 attempts per OTP code, 60s resend cooldown, IP/email rate limiting. |
| Leaked plain codes | Store HMAC-SHA256 hashes only. Never log in production profile. |
| Real SMTP in tests | Profile-based `StubEmailService` used in test/dev profiles. |
