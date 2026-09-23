-- Flyway migration: V11__reconcile_existing_transfers_and_cash_withdrawals.sql
-- Repairs existing imported transactions by reclassifying cash withdrawals
-- and reconciling paired bilateral internal transfers across accounts.

-- 1. Reclassify CASH WITHDRAWALS from EXPENSE to CASH_WITHDRAWAL
UPDATE transactions
SET transaction_type = 'CASH_WITHDRAWAL',
    category = 'OTHER'
WHERE transaction_type = 'EXPENSE'
  AND (
      UPPER(merchant) REGEXP '(^|[^A-Za-z0-9])(ATM|ATW|CASH WDL|CASH WITHDRAWAL|ATM CASH)([^A-Za-z0-9]|$)'
      OR UPPER(COALESCE(description, '')) REGEXP '(^|[^A-Za-z0-9])(ATM|ATW|CASH WDL|CASH WITHDRAWAL|ATM CASH)([^A-Za-z0-9]|$)'
  );

-- 2. Reconcile bilateral internal transfers between different accounts of the same user
-- Matching criteria: opposite directions, same user, different accounts, same amount,
-- date within 3 days, and matching 12-digit UTR/RRN reference or source_reference.

UPDATE transactions debit
JOIN transactions credit ON debit.user_id = credit.user_id
    AND debit.id != credit.id
    AND debit.account_id IS NOT NULL
    AND credit.account_id IS NOT NULL
    AND debit.account_id != credit.account_id
    AND debit.amount = credit.amount
    AND ABS(DATEDIFF(debit.transaction_date, credit.transaction_date)) <= 3
    AND (
        (debit.source_reference IS NOT NULL AND debit.source_reference != '' AND debit.source_reference = credit.source_reference)
        OR (
            credit.source_reference IS NOT NULL 
            AND LENGTH(credit.source_reference) >= 6 
            AND (
                debit.merchant LIKE CONCAT('%', credit.source_reference, '%')
                OR COALESCE(debit.description, '') LIKE CONCAT('%', credit.source_reference, '%')
            )
        )
        OR (
            debit.source_reference IS NOT NULL 
            AND LENGTH(debit.source_reference) >= 6 
            AND (
                credit.merchant LIKE CONCAT('%', debit.source_reference, '%')
                OR COALESCE(credit.description, '') LIKE CONCAT('%', debit.source_reference, '%')
            )
        )
    )
SET 
    debit.transaction_type = 'TRANSFER',
    debit.transfer_direction = 'OUT',
    debit.transfer_match_status = 'AUTO_MATCHED',
    debit.linked_transaction_id = credit.id,
    debit.counterparty_account_id = credit.account_id,
    
    credit.transaction_type = 'TRANSFER',
    credit.transfer_direction = 'IN',
    credit.transfer_match_status = 'AUTO_MATCHED',
    credit.linked_transaction_id = debit.id,
    credit.counterparty_account_id = debit.account_id
WHERE 
    (debit.transaction_type = 'EXPENSE' OR (debit.transaction_type = 'TRANSFER' AND debit.transfer_match_status = 'UNMATCHED'))
    AND (credit.transaction_type = 'INCOME' OR (credit.transaction_type = 'TRANSFER' AND credit.transfer_match_status = 'UNMATCHED'))
    AND debit.linked_transaction_id IS NULL
    AND credit.linked_transaction_id IS NULL;
