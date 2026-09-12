import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  PaymentMethod,
  Transaction,
  TransactionCategory,
  TransactionRequest,
  TransactionType,
} from '../../core/api/api.types';
import { TransactionFilterParams, TransactionService } from './transaction.service';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss',
})
export class TransactionsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly transactionsApi = inject(TransactionService);

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

  readonly types: TransactionType[] = ['EXPENSE', 'INCOME'];

  readonly paymentMethods: PaymentMethod[] = [
    'UPI',
    'CREDIT_CARD',
    'DEBIT_CARD',
    'CASH',
    'BANK_TRANSFER',
    'OTHER',
  ];

  transactions: Transaction[] = [];
  loading = true;
  error = '';
  submitting = false;
  showForm = false;
  editing: Transaction | null = null;
  page = 0;
  totalPages = 0;
  totalElements = 0;

  readonly filters = this.fb.nonNullable.group({
    search: '',
    category: '' as TransactionCategory | '',
    type: '' as TransactionType | '',
    paymentMethod: '' as PaymentMethod | '',
    sort: 'newest',
  });

  readonly form = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    merchant: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(255)]],
    category: ['OTHER' as TransactionCategory, Validators.required],
    transactionType: ['EXPENSE' as TransactionType, Validators.required],
    paymentMethod: ['UPI' as PaymentMethod | ''],
    transactionDate: [new Date().toISOString().slice(0, 10), Validators.required],
    notes: ['', [Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(page = this.page): void {
    this.loading = true;
    this.error = '';
    this.page = page;

    const filterValues = this.filters.getRawValue();
    const params: TransactionFilterParams = {
      page,
      size: 15,
      search: filterValues.search.trim() || undefined,
      category: filterValues.category || undefined,
      type: filterValues.type || undefined,
      paymentMethod: filterValues.paymentMethod || undefined,
      sort: filterValues.sort || 'newest',
    };

    this.transactionsApi.list(params).subscribe({
      next: (response) => {
        this.transactions = response.data ?? [];
        this.totalPages = response.meta?.totalPages ?? 0;
        this.totalElements = response.meta?.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.error = 'We could not load your transactions. Please try again.';
        this.loading = false;
      },
    });
  }

  resetFilters(): void {
    this.filters.reset({
      search: '',
      category: '',
      type: '',
      paymentMethod: '',
      sort: 'newest',
    });
    this.load(0);
  }

  openCreate(): void {
    this.editing = null;
    this.form.reset({
      amount: null,
      merchant: '',
      description: '',
      category: 'OTHER',
      transactionType: 'EXPENSE',
      paymentMethod: 'UPI',
      transactionDate: new Date().toISOString().slice(0, 10),
      notes: '',
    });
    this.showForm = true;
  }

  openEdit(transaction: Transaction): void {
    this.editing = transaction;
    this.form.reset({
      amount: transaction.amount,
      merchant: transaction.merchant,
      description: transaction.description ?? '',
      category: transaction.category,
      transactionType: transaction.transactionType,
      paymentMethod: transaction.paymentMethod ?? 'OTHER',
      transactionDate: transaction.transactionDate,
      notes: transaction.notes ?? '',
    });
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.form.reset();
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    const raw = this.form.getRawValue();
    const payload: TransactionRequest = {
      amount: raw.amount!,
      merchant: raw.merchant.trim(),
      description: raw.description.trim() || null,
      category: raw.category,
      transactionType: raw.transactionType,
      paymentMethod: (raw.paymentMethod as PaymentMethod) || null,
      transactionDate: raw.transactionDate,
      notes: raw.notes.trim() || null,
    };

    const request = this.editing
      ? this.transactionsApi.update(this.editing.id, payload)
      : this.transactionsApi.create(payload);

    request.subscribe({
      next: () => {
        this.submitting = false;
        this.closeForm();
        this.load(
          this.editing && this.transactions.length === 1 && this.page > 0
            ? this.page - 1
            : this.page,
        );
      },
      error: () => {
        this.submitting = false;
        this.error =
          'Your transaction could not be saved. Please check the details and try again.';
      },
    });
  }

  remove(transaction: Transaction): void {
    if (confirm(`Delete transaction for "${transaction.merchant}"? This cannot be undone.`)) {
      this.transactionsApi.delete(transaction.id).subscribe({
        next: () =>
          this.load(
            this.transactions.length === 1 && this.page > 0 ? this.page - 1 : this.page,
          ),
        error: () => {
          this.error = 'This transaction could not be deleted. Please try again.';
        },
      });
    }
  }

  categoryLabel(category: string): string {
    switch (category) {
      case 'FOOD': return 'Food & Dining';
      case 'TRAVEL': return 'Travel & Commute';
      case 'SHOPPING': return 'Shopping';
      case 'ENTERTAINMENT': return 'Entertainment';
      case 'BILLS': return 'Bills & Utilities';
      case 'HEALTH': return 'Health';
      case 'EDUCATION': return 'Education';
      case 'PERSONAL': return 'Personal Care';
      default: return 'Other';
    }
  }

  paymentMethodLabel(method?: PaymentMethod | null): string {
    if (!method) return '—';
    switch (method) {
      case 'UPI': return 'UPI';
      case 'CREDIT_CARD': return 'Credit Card';
      case 'DEBIT_CARD': return 'Debit Card';
      case 'CASH': return 'Cash';
      case 'BANK_TRANSFER': return 'Bank Transfer';
      case 'OTHER': return 'Other';
      default: return method;
    }
  }
}
