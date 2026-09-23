-- Flyway migration: V12__repair_historical_accounts_and_transfers.sql
-- 1. Separates collapsed historical SBI and Kotak accounts for all users
-- 2. Backfills verified account numbers and institution names:
--    - SBI Account Number: 30582558821 -> Last 4: '8821'
--    - Kotak Account Number: 4056291795 -> Last 4: '1795'
-- 3. Reassigns documents and imported transactions to their respective accounts
-- 4. Reconciles bilateral ₹15,000 transfers with UTR 658828783145

-- Step 1: Update existing account (which currently holds SBI documents) to State Bank of India
UPDATE financial_accounts acc
JOIN financial_documents doc ON doc.account_id = acc.id
SET acc.name = 'State Bank of India (SAVINGS - 8821)',
    acc.institution_name = 'State Bank of India',
    acc.account_number_last4 = '8821',
    acc.is_primary = FALSE
WHERE doc.content_hash = 'd19fa11d70be6e891dfd5386726d0c4c2210f9573da1f8564650ace991256843';

UPDATE financial_documents
SET institution_name = 'State Bank of India',
    account_number_last4 = '8821'
WHERE content_hash = 'd19fa11d70be6e891dfd5386726d0c4c2210f9573da1f8564650ace991256843';

-- Step 2: Create distinct Kotak Mahindra Bank accounts for each user who uploaded the Kotak statement
INSERT INTO financial_accounts (
    id, user_id, name, institution_name, account_type, account_number_last4,
    currency, is_active, is_primary, created_at, updated_at
)
SELECT 
    UUID(),
    doc.user_id,
    'Kotak Mahindra Bank (SAVINGS - 1795)',
    'Kotak Mahindra Bank',
    'SAVINGS',
    '1795',
    'INR',
    TRUE,
    TRUE,
    NOW(6),
    NOW(6)
FROM financial_documents doc
WHERE doc.content_hash = '61a2b07e03bc78303a1f15f029dce9090a9ae1757050a8497b7f0ff5f5cd4677'
  AND NOT EXISTS (
      SELECT 1 FROM financial_accounts fa
      WHERE fa.user_id = doc.user_id
        AND fa.institution_name = 'Kotak Mahindra Bank'
        AND fa.account_number_last4 = '1795'
  );

-- Step 3: Reassign Kotak financial_documents to their user's Kotak account
UPDATE financial_documents doc
JOIN financial_accounts acc ON acc.user_id = doc.user_id
    AND acc.institution_name = 'Kotak Mahindra Bank'
    AND acc.account_number_last4 = '1795'
SET doc.account_id = acc.id,
    doc.institution_name = 'Kotak Mahindra Bank',
    doc.account_number_last4 = '1795'
WHERE doc.content_hash = '61a2b07e03bc78303a1f15f029dce9090a9ae1757050a8497b7f0ff5f5cd4677';

-- Step 4: Reassign all transactions imported from Kotak documents to their user's Kotak account
UPDATE transactions t
JOIN financial_documents doc ON t.source_document_id = doc.id
SET t.account_id = doc.account_id
WHERE doc.content_hash = '61a2b07e03bc78303a1f15f029dce9090a9ae1757050a8497b7f0ff5f5cd4677';

-- Step 5: Reconcile bilateral ₹15,000 transfers between Kotak and SBI with UTR 658828783145
UPDATE transactions debit
JOIN transactions credit ON debit.user_id = credit.user_id
    AND debit.id != credit.id
    AND debit.account_id IS NOT NULL
    AND credit.account_id IS NOT NULL
    AND debit.account_id != credit.account_id
    AND debit.amount = credit.amount
    AND debit.amount = 15000.00
    AND ABS(DATEDIFF(debit.transaction_date, credit.transaction_date)) <= 3
    AND (
        (debit.merchant LIKE '%658828783145%' OR COALESCE(debit.description, '') LIKE '%658828783145%')
        AND (credit.merchant LIKE '%658828783145%' OR COALESCE(credit.description, '') LIKE '%658828783145%')
    )
SET 
    debit.transaction_type = 'TRANSFER',
    debit.transfer_direction = 'OUT',
    debit.transfer_match_status = 'AUTO_MATCHED',
    debit.linked_transaction_id = credit.id,
    debit.counterparty_account_id = credit.account_id,
    debit.category = 'OTHER',
    
    credit.transaction_type = 'TRANSFER',
    credit.transfer_direction = 'IN',
    credit.transfer_match_status = 'AUTO_MATCHED',
    credit.linked_transaction_id = debit.id,
    credit.counterparty_account_id = debit.account_id,
    credit.category = 'OTHER'
WHERE 
    (debit.transaction_type IN ('EXPENSE', 'TRANSFER'))
    AND (credit.transaction_type IN ('INCOME', 'TRANSFER'))
    AND (debit.linked_transaction_id IS NULL OR debit.linked_transaction_id = credit.id)
    AND (credit.linked_transaction_id IS NULL OR credit.linked_transaction_id = debit.id);
