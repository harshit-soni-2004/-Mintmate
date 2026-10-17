# SpendSense Authentication Setup Guide

This guide details how to configure and verify Google Sign-In and Email OTP Verification in SpendSense.

---

## 1. Google OAuth 2.0 Setup

### Google Cloud Console Steps
1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/apis/credentials).
2. Create a new project or select your existing SpendSense project.
3. Configure the **OAuth Consent Screen**:
   - User Type: **External** (or Internal for Google Workspace).
   - App Name: `SpendSense` (or `Mintmate`).
   - User Support Email: your developer email.
   - Authorized Domains: `localhost` (for local development).
   - Scopes: `openid`, `email`, `profile`.
4. Create **Credentials** -> **OAuth client ID**:
   - Application Type: **Web application**.
   - Name: `SpendSense Web Client`.
   - **Authorized JavaScript Origins**:
     - `http://localhost:4200`
     - `http://127.0.0.1:4200`
   - **Authorized Redirect URIs**:
     - `http://localhost:4200`
5. Copy your **Client ID** (looks like `xxxxxxxxxxxx-xxxxxxxxxxxxxxxx.apps.googleusercontent.com`).

### Environment Variables
Configure the client ID in both frontend and backend:
- **Backend**: Set `GOOGLE_CLIENT_ID` environment variable:
  ```bash
  export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
  ```
- **Frontend**: Update `googleClientId` in `frontend/src/environments/environment.ts` (or `environment.prod.ts`):
  ```typescript
  export const environment = {
    apiUrl: 'http://localhost:8080/api/v1',
    googleClientId: 'your-client-id.apps.googleusercontent.com',
  };
  ```

---

## 2. Email OTP Configuration

SpendSense uses a secure 6-digit numeric OTP verification system powered by HMAC-SHA256 hash storage, 10-minute validity, single-use enforcement, 60-second resend cooldown, and IP / email rate limits.

### Configuration Properties & Environment Variables
| Variable | Description | Default / Example |
|---|---|---|
| `OTP_HMAC_SECRET` | Secret key used to compute HMAC-SHA256 OTP hashes | `SpendSenseDefaultOtpSecretKeyForHmacSigning2026!` (Set in production) |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` (uses local mail stub) or `prod` (uses real SMTP) |
| `SPRING_MAIL_HOST` | SMTP server host | `smtp.gmail.com` / `smtp.sendgrid.net` |
| `SPRING_MAIL_PORT` | SMTP port | `587` |
| `SPRING_MAIL_USERNAME` | SMTP account username / email | `notifications@spendsense.app` |
| `SPRING_MAIL_PASSWORD` | SMTP account password / app password | `your-smtp-app-password` |

### Running with the Local Stub Mailer (Development / Testing)
- By default, running with profile `dev` or `test` activates `StubEmailService`.
- No outbound SMTP calls are made.
- The 6-digit verification code is printed directly to application logs:
  ```
  INFO: [DEV/TEST EMAIL STUB] Verification code for user@example.com: 489201
  ```
- You can copy this code and enter it on the `/verify-otp` screen.

### Running with Real SMTP (Production Profile)
To dispatch real emails in staging or production:
1. Set `SPRING_PROFILES_ACTIVE=prod`.
2. Provide standard Spring Boot SMTP configuration properties:
   ```bash
   export SPRING_MAIL_HOST=smtp.gmail.com
   export SPRING_MAIL_PORT=587
   export SPRING_MAIL_USERNAME=your-email@gmail.com
   export SPRING_MAIL_PASSWORD=your-app-specific-password
   export SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
   export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
   export OTP_HMAC_SECRET="<generate-a-strong-random-64-char-secret>"
   ```
3. Note: In `prod` profile, OTP codes are NEVER logged to console or files.

---

## 3. Account Resolution & Security Safeguards

1. **Pre-Registration Takeover Protection**:
   - If an existing account with an unverified email exists when signing in with Google, SpendSense links the Google identity, marks the email verified, and wipes out the existing password hash (`password_hash = null`), locking out any attacker who registered the victim's email.
2. **Timing & Anti-Enumeration Protection**:
   - Requests to `/auth/otp/send` return the identical friendly confirmation message regardless of whether the email is present in the database.
   - Password login attempts against accounts without passwords return the generic `INVALID_CREDENTIALS` error code.
3. **Database Security**:
   - Plaintext OTP codes are never stored in the database. Only HMAC-SHA256 digests (`code_hash`) with a server-side secret key are persisted.
   - An hourly cron task automatically purges expired and consumed OTP entries.
