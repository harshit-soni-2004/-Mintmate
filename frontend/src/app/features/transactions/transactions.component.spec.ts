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

  it('opens edit modal and populates all fields', () => {
    component.openEdit(mockTransaction);
    expect(component.showForm).toBeTrue();
    expect(component.editing?.id).toBe('tx-1');
    expect(component.form.value.merchant).toBe('Swiggy');
    expect(component.form.value.paymentMethod).toBe('UPI');
    expect(component.form.value.description).toBe('Dinner delivery');
  });

  it('applies filters with search, category, type, and paymentMethod', () => {
    component.filters.setValue({
      search: 'Swiggy',
      category: 'FOOD',
      type: 'EXPENSE',
      paymentMethod: 'UPI',
      sort: 'highest_amount',
    });

    component.load(0);

    expect(apiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        search: 'Swiggy',
        category: 'FOOD',
        type: 'EXPENSE',
        paymentMethod: 'UPI',
        sort: 'highest_amount',
      }),
    );
  });
});
