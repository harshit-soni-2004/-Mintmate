import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
  CreateDebtRequest,
  DebtCandidateTransactionDto,
  DebtDto,
  DebtStatus,
  DebtSummaryDto,
  DebtType,
  FinancialAccountResponse,
  RecordPaymentAndSettleRequest,
  SettleDebtRequest,
} from '../../core/api/api.types';
import { DebtService } from '../../core/api/debt.service';

@Component({
  selector: 'app-debts',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CurrencyPipe, DatePipe, NgClass],
  templateUrl: './debts.component.html',
  styleUrl: './debts.component.scss',
})
export class DebtsComponent implements OnInit, OnDestroy {
  private readonly debtService = inject(DebtService);
  private readonly fb = inject(FormBuilder);

  private sub = new Subscription();

  debts: DebtDto[] = [];
  summary: DebtSummaryDto | null = null;
  candidateTransactions: DebtCandidateTransactionDto[] = [];
  loadingCandidates = false;

  accounts: FinancialAccountResponse[] = [];
  loadingAccounts = false;
  todayIst = '';

  get topMatchCandidate(): DebtCandidateTransactionDto | undefined {
    return this.candidateTransactions.find((c) => c.isTopMatch);
  }

  get strongMatchCandidate(): DebtCandidateTransactionDto | undefined {
    return this.candidateTransactions.find((c) => c.isStrongMatch);
  }

  get suggestedCandidates(): DebtCandidateTransactionDto[] {
    return this.candidateTransactions.filter((c) => c.isTopMatch || c.score >= 3000);
  }

  get otherCandidates(): DebtCandidateTransactionDto[] {
    return this.candidateTransactions.filter((c) => !c.isTopMatch && c.score < 3000);
  }

  loading = true;
  error = '';
  submitting = false;

  // Filter state
  statusFilter: DebtStatus | 'ALL' = 'ACTIVE';
  typeFilter: DebtType | 'ALL' = 'ALL';
  searchQuery = '';

  // Modals
  showCreateModal = false;
  createFormSubmitted = false;
  createFormError = '';

  showSettleModal = false;
  settlingDebt: DebtDto | null = null;
  settleSubmitting = false;
  settleFormError = '';

  // Forms
  readonly debtForm = this.fb.group({
    counterpartyName: ['', [Validators.required, Validators.maxLength(100)]],
    debtType: ['OWED_TO_ME' as DebtType, [Validators.required]],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    dueDate: [''],
    notes: ['', [Validators.maxLength(500)]],
  });

  readonly settleForm = this.fb.group({
    linkedTransactionId: [''],
    recordPayment: [false],
    paymentAmount: [null as number | null],
    paymentDate: [''],
    paymentAccountId: [''],
    paymentNotes: [''],
  });

  ngOnInit(): void {
    this.loadData();

    this.sub.add(
      this.debtService.onDebtMutated$.subscribe(() => {
        this.loadData(false);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  suggestionsByDebtId: Record<string, DebtCandidateTransactionDto[]> = {};
  loadingSuggestions: Record<string, boolean> = {};

  loadData(showSpinner = true): void {
    if (showSpinner) {
      this.loading = true;
    }
    this.error = '';

    // Load debts based on statusFilter
    const statusParam = this.statusFilter === 'ALL' ? undefined : this.statusFilter;
    this.debtService.list(statusParam).subscribe({
      next: (res) => {
        this.debts = res.data ?? [];
        this.loading = false;
        // Load candidates for active debts and settled debts without links
        this.debts.forEach((d) => {
          if (d.status === 'ACTIVE' || (d.status === 'SETTLED' && !d.linkedTransactionId)) {
            this.loadSuggestionsForDebt(d.id);
          }
        });
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to load debts. Please try again.';
        this.loading = false;
      },
    });

    // Load summary
    this.debtService.getSummary().subscribe({
      next: (res) => {
        this.summary = res.data ?? null;
      },
    });
  }

  loadSuggestionsForDebt(debtId: string): void {
    this.loadingSuggestions[debtId] = true;
    this.debtService.getCandidates(debtId).subscribe({
      next: (res) => {
        this.suggestionsByDebtId[debtId] = res.data ?? [];
        this.loadingSuggestions[debtId] = false;
      },
      error: () => {
        this.suggestionsByDebtId[debtId] = [];
        this.loadingSuggestions[debtId] = false;
      },
    });
  }

  getTopSuggestion(debtId: string): DebtCandidateTransactionDto | undefined {
    const list = this.suggestionsByDebtId[debtId];
    return list && list.length > 0 ? list[0] : undefined;
  }

  confirmAndLink(debt: DebtDto, candidate: DebtCandidateTransactionDto): void {
    this.submitting = true;
    const obs =
      debt.status === 'SETTLED'
        ? this.debtService.linkTransaction(debt.id, { linkedTransactionId: candidate.id })
        : this.debtService.settle(debt.id, { linkedTransactionId: candidate.id });

    obs.subscribe({
      next: () => {
        this.submitting = false;
        this.loadData(false);
      },
      error: (err) => {
        this.submitting = false;
        this.error = err.error?.message || 'Failed to link transaction.';
      },
    });
  }

  dismissCandidate(debt: DebtDto, candidate: DebtCandidateTransactionDto): void {
    this.debtService.dismissCandidate(debt.id, candidate.id).subscribe({
      next: () => {
        if (this.suggestionsByDebtId[debt.id]) {
          this.suggestionsByDebtId[debt.id] = this.suggestionsByDebtId[debt.id].filter(
            (c) => c.id !== candidate.id
          );
        }
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to dismiss suggestion.';
      },
    });
  }

  setStatusFilter(status: DebtStatus | 'ALL'): void {
    this.statusFilter = status;
    this.loadData(true);
  }

  setTypeFilter(type: DebtType | 'ALL'): void {
    this.typeFilter = type;
  }

  get filteredDebts(): DebtDto[] {
    return this.debts.filter((d) => {
      if (this.typeFilter !== 'ALL' && d.debtType !== this.typeFilter) {
        return false;
      }
      if (this.searchQuery.trim()) {
        const query = this.searchQuery.toLowerCase();
        const matchesName = d.counterpartyName.toLowerCase().includes(query);
        const matchesNotes = (d.notes || '').toLowerCase().includes(query);
        if (!matchesName && !matchesNotes) {
          return false;
        }
      }
      return true;
    });
  }

  // Create Modal Actions
  openCreateModal(): void {
    this.debtForm.reset({
      counterpartyName: '',
      debtType: 'OWED_TO_ME',
      amount: null,
      dueDate: '',
      notes: '',
    });
    this.createFormSubmitted = false;
    this.createFormError = '';
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.createFormSubmitted = false;
    this.createFormError = '';
  }

  submitCreateDebt(): void {
    this.createFormSubmitted = true;
    if (this.debtForm.invalid) {
      return;
    }

    const val = this.debtForm.getRawValue();
    const payload: CreateDebtRequest = {
      counterpartyName: (val.counterpartyName || '').trim(),
      debtType: val.debtType as DebtType,
      amount: Number(val.amount),
      dueDate: val.dueDate ? val.dueDate : null,
      notes: val.notes?.trim() ? val.notes.trim() : null,
    };

    this.submitting = true;
    this.createFormError = '';

    this.debtService.create(payload).subscribe({
      next: () => {
        this.submitting = false;
        this.closeCreateModal();
        this.loadData(false);
      },
      error: (err) => {
        this.submitting = false;
        this.createFormError = err.error?.message || 'Failed to create debt. Please verify the details.';
      },
    });
  }

  private getTodayIst(): string {
    try {
      const formatter = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Kolkata',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      });
      return formatter.format(new Date());
    } catch {
      return new Date().toISOString().slice(0, 10);
    }
  }

  // Settle Modal Actions
  openSettleModal(debt: DebtDto): void {
    this.settlingDebt = debt;
    this.todayIst = this.getTodayIst();
    const defaultNote = debt.debtType === 'OWED_TO_ME'
      ? `Repayment from ${debt.counterpartyName}`
      : `Payment to ${debt.counterpartyName}`;

    this.settleForm.reset({
      linkedTransactionId: '',
      recordPayment: false,
      paymentAmount: debt.amount,
      paymentDate: this.todayIst,
      paymentAccountId: '',
      paymentNotes: defaultNote,
    });
    this.settleFormError = '';
    this.candidateTransactions = [];
    this.loadingCandidates = true;
    this.showSettleModal = true;

    this.debtService.getCandidates(debt.id).subscribe({
      next: (res) => {
        this.candidateTransactions = res.data ?? [];
        this.loadingCandidates = false;
        const strongMatch = this.candidateTransactions.find((c) => c.isStrongMatch);
        if (strongMatch) {
          // Only pre-select when the match is strong (exact amount AND counterparty name present)
          this.settleForm.patchValue({ linkedTransactionId: strongMatch.id });
        } else {
          // Otherwise show it under "Suggested" with "No linked transaction" selected
          this.settleForm.patchValue({ linkedTransactionId: '' });
        }
      },
      error: () => {
        this.candidateTransactions = [];
        this.loadingCandidates = false;
      },
    });

    this.loadingAccounts = true;
    this.debtService.getAccounts().subscribe({
      next: (res) => {
        this.accounts = (res.data ?? []).filter((a) => a.active);
        this.loadingAccounts = false;
        if (!this.settleForm.value.paymentAccountId && this.accounts.length) {
          const primary = this.accounts.find((a) => a.primary);
          if (primary) {
            this.settleForm.patchValue({ paymentAccountId: primary.id });
          }
        }
      },
      error: () => {
        this.accounts = [];
        this.loadingAccounts = false;
      },
    });
  }

  closeSettleModal(): void {
    this.showSettleModal = false;
    this.settlingDebt = null;
    this.settleFormError = '';
    this.candidateTransactions = [];
    this.loadingCandidates = false;
    this.accounts = [];
    this.loadingAccounts = false;
  }

  submitSettleDebt(): void {
    if (!this.settlingDebt) return;

    this.settleSubmitting = true;
    this.settleFormError = '';

    const txId = this.settleForm.value.linkedTransactionId?.trim() || null;

    // Optional "Record this payment" flow when no linked transaction is selected
    if (!txId && this.settleForm.value.recordPayment) {
      const amount = Number(this.settleForm.value.paymentAmount);
      if (!amount || amount <= 0) {
        this.settleSubmitting = false;
        this.settleFormError = 'Payment amount must be greater than zero.';
        return;
      }

      const dateVal = this.settleForm.value.paymentDate;
      if (!dateVal) {
        this.settleSubmitting = false;
        this.settleFormError = 'Please select a valid payment date.';
        return;
      }

      if (dateVal > this.todayIst) {
        this.settleSubmitting = false;
        this.settleFormError = 'Payment date cannot be in the future.';
        return;
      }

      const payload: RecordPaymentAndSettleRequest = {
        amount,
        transactionDate: dateVal,
        accountId: this.settleForm.value.paymentAccountId || null,
        notes: (this.settleForm.value.paymentNotes || '').trim() || null,
      };

      this.debtService.recordPaymentAndSettle(this.settlingDebt.id, payload).subscribe({
        next: () => {
          this.settleSubmitting = false;
          this.closeSettleModal();
          this.loadData(false);
        },
        error: (err) => {
          this.settleSubmitting = false;
          this.settleFormError = err.error?.message || 'Failed to record payment and settle debt. Please try again.';
        },
      });
      return;
    }

    const payload: SettleDebtRequest = txId ? { linkedTransactionId: txId } : {};

    this.debtService.settle(this.settlingDebt.id, payload).subscribe({
      next: () => {
        this.settleSubmitting = false;
        this.closeSettleModal();
        this.loadData(false);
      },
      error: (err) => {
        this.settleSubmitting = false;
        this.settleFormError = err.error?.message || 'Failed to settle debt. Please try again.';
      },
    });
  }

  // Cancel Action
  cancelDebt(debt: DebtDto): void {
    if (!confirm(`Are you sure you want to cancel the debt record for "${debt.counterpartyName}"?`)) {
      return;
    }

    this.debtService.cancel(debt.id).subscribe({
      next: () => {
        this.loadData(false);
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to cancel debt.');
      },
    });
  }

  // Helper formatting
  isFieldInvalid(fieldName: string): boolean {
    const field = this.debtForm.get(fieldName);
    return !!(field && (field.touched || this.createFormSubmitted) && field.invalid);
  }
}
