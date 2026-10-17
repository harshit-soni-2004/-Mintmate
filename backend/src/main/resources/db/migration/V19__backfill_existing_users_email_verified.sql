-- V19: Backfill existing users as verified so legacy accounts are not locked out
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
