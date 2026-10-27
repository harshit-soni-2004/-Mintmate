import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  Budget,
  BudgetPeriod,
  BudgetRequest,
  BudgetStatus,
  BudgetSummary,
  TransactionCategory,
} from '../../core/api/api.types';
import { TransactionService } from '../transactions/transaction.service';
import { BudgetService } from './budget.service';

@Component({
  selector: 'app-budgets',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, NgClass],
  templateUrl: './budgets.component.html',
  styleUrl: './budgets.component.scss',
})
export class BudgetsComponent implements OnInit, OnDestroy {
  private readonly budgetService = inject(BudgetService);
  private readonly transactionService = inject(TransactionService);
  private readonly fb = inject(FormBuilder);

  private subs = new Subscription();

  readonly categories: TransactionCategory[] = [
    'FOOD',
    'TRAVEL',
    'SHOPPING',
    'ENTERTAINMENT',
    'BILLS',
    'HEALTH',
    'EDUCATION',
    'PERSONAL',
    'OTHER',
  ];

  readonly periods: BudgetPeriod[] = ['MONTHLY', 'WEEKLY', 'YEARLY', 'CUSTOM'];

  loading = true;
  error = '';
  formError = '';
  submitting = false;
  showModal = false;
  editingBudget: Budget | null = null;

  summary: BudgetSummary | null = null;
  budgets: Budget[] = [];

  readonly form = this.fb.nonNullable.group({
    category: ['FOOD' as TransactionCategory, Validators.required],
    limitAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    period: ['MONTHLY' as BudgetPeriod, Validators.required],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
  });

  ngOnInit(): void {
    this.setDefaultDatesForMonthly();
    this.loadData();

    // Auto refresh budgets when transactions mutate (e.g. user logs or edits expense)
    this.subs.add(
      this.transactionService.onTransactionMutated$.subscribe(() => {
        this.loadData(false);
      })
    );

    // Auto refresh when budget changes
    this.subs.add(
      this.budgetService.onBudgetMutated$.subscribe(() => {
        this.loadData(false);
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  setDefaultDatesForMonthly(): void {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);

    this.form.patchValue({
      startDate: start.toISOString().slice(0, 10),
      endDate: end.toISOString().slice(0, 10),
    });
  }

  onPeriodChange(newPeriod: BudgetPeriod): void {
    const now = new Date();
    if (newPeriod === 'MONTHLY') {
      this.setDefaultDatesForMonthly();
    } else if (newPeriod === 'WEEKLY') {
      const day = now.getDay();
      const diffToMonday = now.getDate() - day + (day === 0 ? -6 : 1);
      const monday = new Date(now.setDate(diffToMonday));
      const sunday = new Date(monday);
      sunday.setDate(monday.getDate() + 6);
      this.form.patchValue({
        startDate: monday.toISOString().slice(0, 10),
        endDate: sunday.toISOString().slice(0, 10),
      });
    } else if (newPeriod === 'YEARLY') {
      const start = new Date(now.getFullYear(), 0, 1);
      const end = new Date(now.getFullYear(), 11, 31);
      this.form.patchValue({
        startDate: start.toISOString().slice(0, 10),
        endDate: end.toISOString().slice(0, 10),
      });
    }
  }

  loadData(setLoading = true): void {
    if (setLoading) this.loading = true;
    this.error = '';

    this.budgetService.getSummary().subscribe({
      next: (res) => {
        this.summary = res.data;
        this.budgets = res.data?.budgets || [];
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load budgets. Please try again.';
        this.loading = false;
      },
    });
  }

  openCreate(): void {
    this.editingBudget = null;
    this.formError = '';
    this.form.reset({
      category: 'FOOD',
      limitAmount: null,
      period: 'MONTHLY',
      startDate: '',
      endDate: '',
    });
    this.setDefaultDatesForMonthly();
    this.showModal = true;
  }

  openEdit(b: Budget): void {
    this.editingBudget = b;
    this.formError = '';
    this.form.patchValue({
      category: b.category,
      limitAmount: b.limitAmount,
      period: b.period,
      startDate: b.startDate,
      endDate: b.endDate,
    });
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingBudget = null;
    this.formError = '';
  }

  saveBudget(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.getRawValue();
    const payload: BudgetRequest = {
      category: val.category,
      limitAmount: Number(val.limitAmount),
      period: val.period,
      startDate: val.startDate,
      endDate: val.endDate,
    };

    this.submitting = true;
    this.formError = '';

    const req$ = this.editingBudget
      ? this.budgetService.update(this.editingBudget.id, payload)
      : this.budgetService.create(payload);

    req$.subscribe({
      next: () => {
        this.submitting = false;
        this.closeModal();
        this.loadData(false);
      },
      error: (err) => {
        this.submitting = false;
        if (err?.error?.message) {
          this.formError = err.error.message;
        } else {
          this.formError = 'Failed to save budget. Please verify the dates and category.';
        }
      },
    });
  }

  deleteBudget(b: Budget): void {
    if (confirm(`Delete budget for ${this.categoryLabel(b.category)}? This cannot be undone.`)) {
      this.budgetService.delete(b.id).subscribe({
        next: () => this.loadData(false),
        error: () => {
          this.error = 'Failed to delete budget. Please try again.';
        },
      });
    }
  }

  categoryLabel(cat: string): string {
    if (!cat) return 'Other';
    switch (cat.toUpperCase()) {
      case 'FOOD': return 'Food & Dining';
      case 'TRAVEL': return 'Travel & Commute';
      case 'SHOPPING': return 'Shopping';
      case 'ENTERTAINMENT': return 'Entertainment';
      case 'BILLS': return 'Bills & Utilities';
      case 'HEALTH': return 'Health';
      case 'EDUCATION': return 'Education';
      case 'PERSONAL': return 'Personal Care';
      default: return cat.charAt(0).toUpperCase() + cat.slice(1).toLowerCase();
    }
  }

  statusLabel(status: BudgetStatus): string {
    switch (status) {
      case 'ON_TRACK': return 'On Track';
      case 'WARNING': return 'Warning (75%+)';
      case 'EXCEEDED': return 'Exceeded';
    }
  }

  getStatusClass(status: BudgetStatus): string {
    switch (status) {
      case 'ON_TRACK': return 'status-on-track';
      case 'WARNING': return 'status-warning';
      case 'EXCEEDED': return 'status-exceeded';
    }
  }

  getProgressBarClass(status: BudgetStatus): string {
    switch (status) {
      case 'ON_TRACK': return 'fill-on-track';
      case 'WARNING': return 'fill-warning';
      case 'EXCEEDED': return 'fill-exceeded';
    }
  }
}
