import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { DebtDto, DebtSummaryDto, Transaction } from '../../core/api/api.types';
import { DebtService } from '../../core/api/debt.service';
import { TransactionService } from '../transactions/transaction.service';
import { DebtsComponent } from './debts.component';

describe('DebtsComponent', () => {
  let component: DebtsComponent;
  let fixture: ComponentFixture<DebtsComponent>;
  let mockDebtService: jasmine.SpyObj<DebtService>;
  let mockTxService: jasmine.SpyObj<TransactionService>;
  let mutationSubject: Subject<void>;

  const mockDebts: DebtDto[] = [
    {
      id: 'debt-1',
      counterpartyName: 'Alice Sharma',
      debtType: 'OWED_TO_ME',
      amount: 4500,
      dueDate: '2026-10-30',
      status: 'ACTIVE',
      settledAt: null,
      linkedTransactionId: null,
      notes: 'Weekend trip split',
      createdAt: '2026-10-10T12:00:00Z',
      overdue: false,
    },
    {
      id: 'debt-2',
      counterpartyName: 'Bob Verma',
      debtType: 'I_OWE',
      amount: 1200,
      dueDate: '2026-10-01',
      status: 'ACTIVE',
      settledAt: null,
      linkedTransactionId: null,
      notes: 'Dinner reimbursement',
      createdAt: '2026-09-25T12:00:00Z',
      overdue: true,
    },
  ];

  const mockSummary: DebtSummaryDto = {
    totalReceivable: 4500,
    totalPayable: 1200,
    netDebtPosition: 3300,
    activeCount: 2,
    overdueCount: 1,
    upcomingDebts: [mockDebts[0]],
    overdueDebts: [mockDebts[1]],
  };

  beforeEach(async () => {
    mutationSubject = new Subject<void>();
    mockDebtService = jasmine.createSpyObj('DebtService', [
      'list',
      'getSummary',
      'create',
      'settle',
      'cancel',
    ], {
      onDebtMutated$: mutationSubject.asObservable(),
    });

    mockTxService = jasmine.createSpyObj('TransactionService', ['list']);

    mockDebtService.list.and.returnValue(of({ data: mockDebts, error: null, meta: null }));
    mockDebtService.getSummary.and.returnValue(of({ data: mockSummary, error: null, meta: null }));
    mockTxService.list.and.returnValue(of({
      data: [],
      error: null,
      meta: null as any,
    }));

    await TestBed.configureTestingModule({
      imports: [DebtsComponent],
      providers: [
        { provide: DebtService, useValue: mockDebtService },
        { provide: TransactionService, useValue: mockTxService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DebtsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates component and loads initial debts and summary', () => {
    expect(component).toBeTruthy();
    expect(component.debts.length).toBe(2);
    expect(component.summary?.totalReceivable).toBe(4500);
    expect(component.summary?.totalPayable).toBe(1200);
    expect(component.summary?.netDebtPosition).toBe(3300);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#summary-total-receivable')?.textContent).toContain('4,500');
    expect(compiled.querySelector('#summary-total-payable')?.textContent).toContain('1,200');
    expect(compiled.querySelector('#summary-net-position')?.textContent).toContain('3,300');
  });

  it('filters debts by type and search query', () => {
    component.setTypeFilter('OWED_TO_ME');
    expect(component.filteredDebts.length).toBe(1);
    expect(component.filteredDebts[0].counterpartyName).toBe('Alice Sharma');

    component.setTypeFilter('ALL');
    component.searchQuery = 'Bob';
    expect(component.filteredDebts.length).toBe(1);
    expect(component.filteredDebts[0].counterpartyName).toBe('Bob Verma');
  });

  it('changes status filter and reloads debts', () => {
    mockDebtService.list.and.returnValue(of({ data: [], error: null, meta: null }));
    component.setStatusFilter('SETTLED');
    expect(component.statusFilter).toBe('SETTLED');
    expect(mockDebtService.list).toHaveBeenCalledWith('SETTLED');
  });

  it('validates required fields on create debt form', () => {
    component.openCreateModal();
    fixture.detectChanges();

    expect(component.showCreateModal).toBeTrue();
    expect(component.debtForm.valid).toBeFalse();

    // Submit invalid form
    component.submitCreateDebt();
    expect(component.createFormSubmitted).toBeTrue();
    expect(component.isFieldInvalid('counterpartyName')).toBeTrue();
    expect(component.isFieldInvalid('amount')).toBeTrue();
    expect(mockDebtService.create).not.toHaveBeenCalled();

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#error-debt-counterparty')).toBeTruthy();
    expect(compiled.querySelector('#error-debt-amount')).toBeTruthy();
  });

  it('submits valid create debt request', () => {
    mockDebtService.create.and.returnValue(of({
      data: mockDebts[0],
      error: null,
      meta: null,
    }));

    component.openCreateModal();
    component.debtForm.setValue({
      counterpartyName: 'Charlie',
      debtType: 'OWED_TO_ME',
      amount: 2500,
      dueDate: '2026-11-01',
      notes: 'Lent for cab',
    });

    component.submitCreateDebt();
    expect(mockDebtService.create).toHaveBeenCalledWith({
      counterpartyName: 'Charlie',
      debtType: 'OWED_TO_ME',
      amount: 2500,
      dueDate: '2026-11-01',
      notes: 'Lent for cab',
    });
    expect(component.showCreateModal).toBeFalse();
  });

  it('opens settle modal and settles debt', () => {
    mockDebtService.settle.and.returnValue(of({
      data: { ...mockDebts[0], status: 'SETTLED' },
      error: null,
      meta: null,
    }));

    component.openSettleModal(mockDebts[0]);
    expect(component.showSettleModal).toBeTrue();
    expect(component.settlingDebt?.counterpartyName).toBe('Alice Sharma');

    component.settleForm.setValue({ linkedTransactionId: 'tx-123' });
    component.submitSettleDebt();

    expect(mockDebtService.settle).toHaveBeenCalledWith('debt-1', { linkedTransactionId: 'tx-123' });
    expect(component.showSettleModal).toBeFalse();
  });

  it('cancels debt after user confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    mockDebtService.cancel.and.returnValue(of({
      data: { ...mockDebts[0], status: 'CANCELLED' },
      error: null,
      meta: null,
    }));

    component.cancelDebt(mockDebts[0]);
    expect(mockDebtService.cancel).toHaveBeenCalledWith('debt-1');
  });

  describe('IOUs & Debts Button Rendering & Action Verification', () => {
    it('renders header "+ Log New Debt" primary button and opens modal when clicked', () => {
      const headerBtn = fixture.nativeElement.querySelector('#btn-open-log-debt');
      expect(headerBtn).toBeTruthy();
      expect(headerBtn.classList.contains('btn-primary')).toBeTrue();
      expect(headerBtn.classList.contains('add-debt-btn')).toBeTrue();
      expect(headerBtn.textContent).toContain('Log New Debt');

      headerBtn.click();
      fixture.detectChanges();
      expect(component.showCreateModal).toBeTrue();
    });

    it('renders card primary "Mark Settled" and secondary "Cancel" buttons with proper action classes', () => {
      const settleBtn = fixture.nativeElement.querySelector('#btn-settle-debt-debt-1');
      const cancelBtn = fixture.nativeElement.querySelector('#btn-cancel-debt-debt-1');

      expect(settleBtn).toBeTruthy();
      expect(settleBtn.classList.contains('btn-action-settle')).toBeTrue();
      expect(settleBtn.textContent.trim()).toBe('Mark Settled');

      expect(cancelBtn).toBeTruthy();
      expect(cancelBtn.classList.contains('btn-action-cancel')).toBeTrue();
      expect(cancelBtn.textContent.trim()).toBe('Cancel');
    });

    it('renders styled primary button in empty state and opens modal on click', () => {
      component.debts = [];
      component.loading = false;
      fixture.detectChanges();

      const emptyBox = fixture.nativeElement.querySelector('#debts-empty-state');
      expect(emptyBox).toBeTruthy();

      const emptyBtn = emptyBox.querySelector('#btn-empty-log-debt');
      expect(emptyBtn).toBeTruthy();
      expect(emptyBtn.classList.contains('btn-primary')).toBeTrue();
      expect(emptyBtn.textContent).toContain('Log a new IOU');

      emptyBtn.click();
      fixture.detectChanges();
      expect(component.showCreateModal).toBeTrue();
    });
  });
});
