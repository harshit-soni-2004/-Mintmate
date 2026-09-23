import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import {
  CategorizationAlternative,
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
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, RouterModule],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss',
})
export class TransactionsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly transactionsApi = inject(TransactionService);
  private readonly route = inject(ActivatedRoute, { optional: true });

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

  readonly types: TransactionType[] = ['EXPENSE', 'INCOME', 'TRANSFER', 'INVESTMENT', 'CASH_WITHDRAWAL'];

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
  activeReviewTransaction: Transaction | null = null;
  page = 0;
  totalPages = 0;
  totalElements = 0;
  sourceDocumentId?: string;

  readonly filters = this.fb.nonNullable.group({
    search: '',
    category: '' as TransactionCategory | '',
    type: '' as TransactionType | '',
    paymentMethod: '' as PaymentMethod | '',
    reviewStatus: '' as 'NEEDS_REVIEW' | '',
    sort: 'newest',
  });

  readonly form = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    merchant: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(255)]],
    category: ['AUTO' as TransactionCategory | 'AUTO'],
    transactionType: ['EXPENSE' as TransactionType, Validators.required],
    paymentMethod: ['UPI' as PaymentMethod | ''],
    transactionDate: [new Date().toISOString().slice(0, 10), Validators.required],
    notes: ['', [Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    if (this.route) {
      this.route.queryParams.subscribe((queryParams) => {
        if (queryParams['sourceDocumentId']) {
          this.sourceDocumentId = queryParams['sourceDocumentId'];
        }
        if (queryParams['needsReview'] === 'true') {
          this.filters.patchValue({ reviewStatus: 'NEEDS_REVIEW' });
        }
        this.load();
      });
    } else {
      this.load();
    }
  }

  clearStatementFilter(): void {
    this.sourceDocumentId = undefined;
    this.load(0);
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
      needsReview: filterValues.reviewStatus === 'NEEDS_REVIEW' ? true : undefined,
      sourceDocumentId: this.sourceDocumentId,
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
      reviewStatus: '',
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
      category: 'AUTO',
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
    const isAuto = raw.category === 'AUTO' || !raw.category;
    const payload: TransactionRequest = {
      amount: raw.amount!,
      merchant: raw.merchant.trim(),
      description: raw.description.trim() || null,
      category: isAuto ? null : (raw.category as TransactionCategory),
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

  // --- Phase 6H: Categorization Review & Correction Actions ---

  openCategoryPicker(transaction: Transaction, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.activeReviewTransaction = transaction;
  }

  closeCategoryPicker(): void {
    this.activeReviewTransaction = null;
  }

  selectCategory(category: TransactionCategory): void {
    if (!this.activeReviewTransaction) {
      return;
    }
    const target = this.activeReviewTransaction;
    const previousCategory = target.category;
    const previousReviewState = target.requiresUserConfirmation;

    // Optimistic UI update
    target.category = category;
    target.requiresUserConfirmation = false;
    this.closeCategoryPicker();

    this.transactionsApi.correctCategory(target.id, category).subscribe({
      next: (response) => {
        if (response.data) {
          Object.assign(target, response.data);
        }
      },
      error: () => {
        target.category = previousCategory;
        target.requiresUserConfirmation = previousReviewState;
        this.error = 'Could not update transaction category. Please try again.';
      },
    });
  }

  confirmCategory(transaction: Transaction, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    const previousReviewState = transaction.requiresUserConfirmation;
    transaction.requiresUserConfirmation = false;

    this.transactionsApi.confirmCategory(transaction.id).subscribe({
      next: (response) => {
        if (response.data) {
          Object.assign(transaction, response.data);
        }
      },
      error: () => {
        transaction.requiresUserConfirmation = previousReviewState;
        this.error = 'Could not confirm category. Please try again.';
      },
    });
  }

  quickChangeCategory(transaction: Transaction, newCategory: TransactionCategory, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.activeReviewTransaction = transaction;
    this.selectCategory(newCategory);
  }

  getTopAlternatives(transaction: Transaction): CategorizationAlternative[] {
    if (!transaction.alternatives || !transaction.alternatives.length) {
      return [];
    }
    return transaction.alternatives
      .filter((alt) => alt.category !== transaction.category && alt.confidence >= 0.05)
      .slice(0, 2);
  }

  categoryLabel(category?: string | null): string {
    if (!category) return 'Other';
    switch (category) {
      case 'FOOD': return 'Food & Dining';
      case 'TRAVEL': return 'Travel & Commute';
      case 'SHOPPING': return 'Shopping';
      case 'ENTERTAINMENT': return 'Entertainment';
      case 'BILLS': return 'Bills & Utilities';
      case 'HEALTH': return 'Health & Wellness';
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

  typeLabel(type?: TransactionType | null): string {
    if (!type) return '—';
    switch (type) {
      case 'EXPENSE': return 'Expense';
      case 'INCOME': return 'Income';
      case 'TRANSFER': return 'Transfer';
      case 'INVESTMENT': return 'Investment';
      case 'CASH_WITHDRAWAL': return 'Cash Withdrawal';
      default: return type;
    }
  }
}
