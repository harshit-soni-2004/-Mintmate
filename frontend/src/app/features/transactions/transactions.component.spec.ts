import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Transaction, TransactionCategory, TransactionType } from '../../core/api/api.types';
import { TransactionService } from './transaction.service';
import { TransactionsComponent } from './transactions.component';

describe('TransactionsComponent', () => {
  let component: TransactionsComponent;
  let fixture: ComponentFixture<TransactionsComponent>;
  let apiSpy: jasmine.SpyObj<TransactionService>;

  const mockTransaction: Transaction = {
    id: 'tx-1',
    amount: 500,
    merchant: 'Swiggy',
    description: 'Dinner delivery',
    category: 'FOOD',
    transactionType: 'EXPENSE',
    paymentMethod: 'UPI',
    transactionDate: '2026-09-08',
    notes: 'Tasty food',
    createdAt: '2026-09-08T12:00:00Z',
    updatedAt: '2026-09-08T12:00:00Z',
  };

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('TransactionService', [
      'list',
      'create',
      'update',
      'delete',
      'confirmCategory',
      'correctCategory',
    ]);

    apiSpy.list.and.returnValue(
      of({
        data: [mockTransaction],
        error: null,
        meta: { page: 0, size: 15, totalElements: 1, totalPages: 1, first: true, last: true },
      }),
    );

    await TestBed.configureTestingModule({
      imports: [TransactionsComponent],
      providers: [{ provide: TransactionService, useValue: apiSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads transactions on init', () => {
    expect(apiSpy.list).toHaveBeenCalled();
    expect(component.transactions.length).toBe(1);
    expect(component.transactions[0].merchant).toBe('Swiggy');
    expect(component.transactions[0].paymentMethod).toBe('UPI');
  });

  it('validates amount and merchant fields', () => {
    component.openCreate();
    expect(component.form.valid).toBeFalse();

    component.form.controls.amount.setValue(0);
    component.form.controls.merchant.setValue('');
    expect(component.form.valid).toBeFalse();

    component.form.controls.amount.setValue(500);
    component.form.controls.merchant.setValue('Amazon');
    expect(component.form.valid).toBeTrue();
  });

  it('creates transaction with payment method and description', () => {
    apiSpy.create.and.returnValue(
      of({
        data: mockTransaction,
        error: null,
        meta: null,
      }),
    );

    component.openCreate();
    component.form.setValue({
      amount: 500,
      merchant: 'Swiggy',
      description: 'Dinner delivery',
      category: 'FOOD',
      transactionType: 'EXPENSE',
      paymentMethod: 'UPI',
      transactionDate: '2026-09-08',
      notes: 'Notes test',
    });

    component.save();

    expect(apiSpy.create).toHaveBeenCalledWith({
      amount: 500,
      merchant: 'Swiggy',
      description: 'Dinner delivery',
      category: 'FOOD' as TransactionCategory,
      transactionType: 'EXPENSE' as TransactionType,
      paymentMethod: 'UPI',
      transactionDate: '2026-09-08',
      notes: 'Notes test',
    });
    expect(component.showForm).toBeFalse();
  });

  it('creates transaction with auto-categorization when category is AUTO', () => {
    apiSpy.create.and.returnValue(
      of({
        data: mockTransaction,
        error: null,
        meta: null,
      }),
    );

    component.openCreate();
    component.form.setValue({
      amount: 150,
      merchant: 'Uber Ride',
      description: 'Trip to office',
      category: 'AUTO',
      transactionType: 'EXPENSE',
      paymentMethod: 'UPI',
      transactionDate: '2026-09-08',
      notes: '',
    });

    component.save();

    expect(apiSpy.create).toHaveBeenCalledWith({
      amount: 150,
      merchant: 'Uber Ride',
      description: 'Trip to office',
      category: null,
      transactionType: 'EXPENSE' as TransactionType,
      paymentMethod: 'UPI',
      transactionDate: '2026-09-08',
      notes: null,
    });
    expect(component.showForm).toBeFalse();
  });

  it('opens edit modal and populates all fields', () => {
    component.openEdit(mockTransaction);
    expect(component.showForm).toBeTrue();
    expect(component.editing?.id).toBe('tx-1');
    expect(component.form.value.merchant).toBe('Swiggy');
    expect(component.form.value.paymentMethod).toBe('UPI');
    expect(component.form.value.description).toBe('Dinner delivery');
  });

  it('applies filters with search, category, type, paymentMethod, and reviewStatus', () => {
    component.filters.setValue({
      search: 'Swiggy',
      category: 'FOOD',
      type: 'EXPENSE',
      paymentMethod: 'UPI',
      reviewStatus: 'NEEDS_REVIEW',
      sort: 'highest_amount',
    });

    component.load(0);

    expect(apiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        search: 'Swiggy',
        category: 'FOOD',
        type: 'EXPENSE',
        paymentMethod: 'UPI',
        needsReview: true,
        sort: 'highest_amount',
      }),
    );
  });

  it('confirms suggested category and updates transaction state', () => {
    const pendingTx: Transaction = {
      ...mockTransaction,
      id: 'tx-2',
      category: 'FOOD',
      requiresUserConfirmation: true,
    };

    apiSpy.confirmCategory.and.returnValue(
      of({
        data: { ...pendingTx, requiresUserConfirmation: false },
        error: null,
        meta: null,
      }),
    );

    component.confirmCategory(pendingTx);

    expect(pendingTx.requiresUserConfirmation).toBeFalse();
    expect(apiSpy.confirmCategory).toHaveBeenCalledWith('tx-2');
  });

  it('corrects category and updates transaction state', () => {
    const reviewTx: Transaction = {
      ...mockTransaction,
      id: 'tx-3',
      category: 'OTHER',
      requiresUserConfirmation: true,
    };

    apiSpy.correctCategory.and.returnValue(
      of({
        data: { ...reviewTx, category: 'FOOD', requiresUserConfirmation: false },
        error: null,
        meta: null,
      }),
    );

    component.openCategoryPicker(reviewTx);
    expect(component.activeReviewTransaction).toBe(reviewTx);

    component.selectCategory('FOOD');

    expect(reviewTx.category).toBe('FOOD');
    expect(reviewTx.requiresUserConfirmation).toBeFalse();
    expect(component.activeReviewTransaction).toBeNull();
    expect(apiSpy.correctCategory).toHaveBeenCalledWith('tx-3', 'FOOD');
  });

  it('extracts top alternatives excluding the current category', () => {
    const txWithAlts: Transaction = {
      ...mockTransaction,
      category: 'OTHER',
      alternatives: [
        { category: 'OTHER', confidence: 0.5 },
        { category: 'FOOD', confidence: 0.35 },
        { category: 'SHOPPING', confidence: 0.12 },
        { category: 'TRAVEL', confidence: 0.03 },
      ],
    };

    const alts = component.getTopAlternatives(txWithAlts);
    expect(alts.length).toBe(2);
    expect(alts[0].category).toBe('FOOD');
    expect(alts[1].category).toBe('SHOPPING');
  });
});
