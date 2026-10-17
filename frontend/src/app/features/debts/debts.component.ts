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

  get topMatchCandidate(): DebtCandidateTransactionDto | undefined {
    return this.candidateTransactions.find((c) => c.isTopMatch);
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

  // Settle Modal Actions
  openSettleModal(debt: DebtDto): void {
    this.settlingDebt = debt;
    this.settleForm.reset({ linkedTransactionId: '' });
    this.settleFormError = '';
    this.candidateTransactions = [];
    this.loadingCandidates = true;
    this.showSettleModal = true;

    this.debtService.getCandidates(debt.id).subscribe({
      next: (res) => {
        this.candidateTransactions = res.data ?? [];
        this.loadingCandidates = false;
        const topMatch = this.candidateTransactions.find((c) => c.isTopMatch);
        if (topMatch) {
          this.settleForm.patchValue({ linkedTransactionId: topMatch.id });
        }
      },
      error: () => {
        this.candidateTransactions = [];
        this.loadingCandidates = false;
      },
    });
  }

  closeSettleModal(): void {
    this.showSettleModal = false;
    this.settlingDebt = null;
    this.settleFormError = '';
    this.candidateTransactions = [];
    this.loadingCandidates = false;
  }

  submitSettleDebt(): void {
    if (!this.settlingDebt) return;

    this.settleSubmitting = true;
    this.settleFormError = '';

    const txId = this.settleForm.value.linkedTransactionId?.trim() || null;
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
