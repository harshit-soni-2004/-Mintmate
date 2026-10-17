import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { DebtCandidateTransactionDto, DebtDto, DebtSummaryDto } from '../../core/api/api.types';
import { DebtService } from '../../core/api/debt.service';
import { DebtsComponent } from './debts.component';

describe('DebtsComponent', () => {
  let component: DebtsComponent;
  let fixture: ComponentFixture<DebtsComponent>;
  let mockDebtService: jasmine.SpyObj<DebtService>;
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
      'linkTransaction',
      'dismissCandidate',
      'cancel',
      'getCandidates',
      'recordPaymentAndSettle',
      'getAccounts',
    ], {
      onDebtMutated$: mutationSubject.asObservable(),
    });

    mockDebtService.list.and.returnValue(of({ data: mockDebts, error: null, meta: null }));
    mockDebtService.getSummary.and.returnValue(of({ data: mockSummary, error: null, meta: null }));
    mockDebtService.getCandidates.and.returnValue(of({ data: [], error: null, meta: null }));
    mockDebtService.getAccounts.and.returnValue(of({ data: [], error: null, meta: null }));

    await TestBed.configureTestingModule({
      imports: [DebtsComponent],
      providers: [
        { provide: DebtService, useValue: mockDebtService },
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
    expect(mockDebtService.getCandidates).toHaveBeenCalledWith('debt-1');

    component.settleForm.patchValue({ linkedTransactionId: 'tx-123' });
    component.submitSettleDebt();

    expect(mockDebtService.settle).toHaveBeenCalledWith('debt-1', { linkedTransactionId: 'tx-123' });
    expect(component.showSettleModal).toBeFalse();
  });

  it('settles debt with recordPayment=false creates no transaction and calls standard settle endpoint', () => {
    mockDebtService.settle.and.returnValue(of({
      data: { ...mockDebts[0], status: 'SETTLED', linkedTransactionId: null },
      error: null,
      meta: null,
    }));

    component.openSettleModal(mockDebts[0]);
    fixture.detectChanges();

    // Verify option is unchecked by default
    expect(component.settleForm.value.recordPayment).toBeFalse();

    component.submitSettleDebt();

    expect(mockDebtService.settle).toHaveBeenCalledWith('debt-1', {});
    expect(mockDebtService.recordPaymentAndSettle).not.toHaveBeenCalled();
    expect(component.showSettleModal).toBeFalse();
  });

  it('settles debt with recordPayment=true calls recordPaymentAndSettle endpoint with prefilled values', () => {
    const mockAccounts = [
      { id: 'acc-1', name: 'HDFC Bank', accountType: 'SAVINGS', currency: 'INR', active: true, primary: true },
    ];
    mockDebtService.getAccounts.and.returnValue(of({ data: mockAccounts as any, error: null, meta: null }));
    mockDebtService.recordPaymentAndSettle.and.returnValue(of({
      data: { ...mockDebts[0], status: 'SETTLED', linkedTransactionId: 'tx-recorded-1' },
      error: null,
      meta: null,
    }));

    component.openSettleModal(mockDebts[0]);
    fixture.detectChanges();

    // Verify prefilling
    expect(component.settleForm.value.paymentAmount).toBe(4500);
    expect(component.settleForm.value.paymentDate).toBe(component.todayIst);
    expect(component.settleForm.value.paymentNotes).toBe('Repayment from Alice Sharma');
    expect(component.settleForm.value.paymentAccountId).toBe('acc-1');

    // Turn on recordPayment
    component.settleForm.patchValue({ recordPayment: true });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#record-payment-fields-panel')).toBeTruthy();

    component.submitSettleDebt();

    expect(mockDebtService.recordPaymentAndSettle).toHaveBeenCalledWith('debt-1', {
      amount: 4500,
      transactionDate: component.todayIst,
      accountId: 'acc-1',
      notes: 'Repayment from Alice Sharma',
    });
    expect(mockDebtService.settle).not.toHaveBeenCalled();
    expect(component.showSettleModal).toBeFalse();
  });

  it('validates invalid amount or future date in record payment flow without submitting', () => {
    component.openSettleModal(mockDebts[0]);
    component.settleForm.patchValue({
      recordPayment: true,
      paymentAmount: 0,
    });

    component.submitSettleDebt();
    expect(mockDebtService.recordPaymentAndSettle).not.toHaveBeenCalled();
    expect(component.settleFormError).toContain('greater than zero');

    // Future date
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 2);
    component.settleForm.patchValue({
      paymentAmount: 4500,
      paymentDate: tomorrow.toISOString().slice(0, 10),
    });

    component.submitSettleDebt();
    expect(mockDebtService.recordPaymentAndSettle).not.toHaveBeenCalled();
    expect(component.settleFormError).toContain('cannot be in the future');
  });

  it('hides record payment checkbox when a linked transaction is selected', () => {
    component.openSettleModal(mockDebts[0]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#record-payment-checkbox')).toBeTruthy();

    component.settleForm.patchValue({ linkedTransactionId: 'tx-some-id' });
    fixture.detectChanges();

    expect(compiled.querySelector('#record-payment-checkbox')).toBeFalsy();
  });

  it('pre-selects top candidate in settle dropdown when match is strong (exact amount AND counterparty present)', () => {
    const mockCandidates: DebtCandidateTransactionDto[] = [
      {
        id: 'candidate-strong',
        amount: 4500,
        description: 'Alice repayment',
        merchant: 'UPI-Alice',
        transactionDate: '2026-10-14',
        transactionType: 'INCOME',
        transferDirection: null,
        isTopMatch: true,
        isStrongMatch: true,
        matchReason: 'Exact amount and counterparty match',
        score: 10010,
      },
      {
        id: 'candidate-other',
        amount: 4000,
        description: 'Transfer in',
        merchant: 'NEFT',
        transactionDate: '2026-10-12',
        transactionType: 'INCOME',
        transferDirection: null,
        isTopMatch: false,
        isStrongMatch: false,
        matchReason: 'Close amount match',
        score: 4008,
      },
    ];

    mockDebtService.getCandidates.and.returnValue(of({
      data: mockCandidates,
      error: null,
      meta: null,
    }));

    component.openSettleModal(mockDebts[0]);
    fixture.detectChanges();

    expect(mockDebtService.getCandidates).toHaveBeenCalledWith('debt-1');
    expect(component.candidateTransactions.length).toBe(2);
    expect(component.strongMatchCandidate).toEqual(mockCandidates[0]);
    // Strong match must be pre-selected in the form
    expect(component.settleForm.value.linkedTransactionId).toBe('candidate-strong');

    const compiled = fixture.nativeElement as HTMLElement;
    const topBadge = compiled.querySelector('#top-match-badge');
    expect(topBadge).toBeTruthy();
    expect(topBadge?.textContent).toContain('Strong match found:');
    expect(topBadge?.textContent).toContain('UPI-Alice');

    // Appears under Suggested optgroup
    const suggestedOptgroup = compiled.querySelector('optgroup[label="Suggested"]');
    expect(suggestedOptgroup).toBeTruthy();
    expect(suggestedOptgroup?.children[0].getAttribute('value')).toBe('candidate-strong');
  });

  it('does not pre-select top candidate when match is not strong, leaving No linked transaction selected and showing candidate under Suggested', () => {
    const mockCandidates: DebtCandidateTransactionDto[] = [
      {
        id: 'candidate-suggested-only',
        amount: 4500,
        description: 'Random transfer',
        merchant: 'Unknown Bank',
        transactionDate: '2026-10-14',
        transactionType: 'INCOME',
        transferDirection: null,
        isTopMatch: true,
        isStrongMatch: false,
        matchReason: 'Exact amount match',
        score: 7510,
      },
      {
        id: 'candidate-recent',
        amount: 200,
        description: 'Coffee',
        merchant: 'Cafe',
        transactionDate: '2026-10-13',
        transactionType: 'INCOME',
        transferDirection: null,
        isTopMatch: false,
        isStrongMatch: false,
        matchReason: 'Recent candidate',
        score: 1008,
      },
    ];

    mockDebtService.getCandidates.and.returnValue(of({
      data: mockCandidates,
      error: null,
      meta: null,
    }));

    component.openSettleModal(mockDebts[0]);
    fixture.detectChanges();

    expect(component.strongMatchCandidate).toBeUndefined();
    // Non-strong match must NOT be pre-selected (remains empty string / No linked transaction)
    expect(component.settleForm.value.linkedTransactionId).toBe('');

    const compiled = fixture.nativeElement as HTMLElement;
    const select = compiled.querySelector('#settle-linked-tx') as HTMLSelectElement;
    expect(select.value).toBe('');

    // Suggested candidate still appears first under Suggested optgroup
    const suggestedOptgroup = compiled.querySelector('optgroup[label="Suggested"]');
    expect(suggestedOptgroup).toBeTruthy();
    expect(suggestedOptgroup?.children[0].getAttribute('value')).toBe('candidate-suggested-only');
    expect(suggestedOptgroup?.children[0].textContent).toContain('Exact amount match');
  });

  it('handles candidate loading error gracefully without breaking modal', () => {
    mockDebtService.getCandidates.and.returnValue(throwError(() => new Error('Server error')));

    component.openSettleModal(mockDebts[0]);

    expect(component.showSettleModal).toBeTrue();
    expect(component.candidateTransactions).toEqual([]);
    expect(component.loadingCandidates).toBeFalse();
    expect(component.settleForm.value.linkedTransactionId).toBe('');
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

  describe('Part 3 Repayment Matching & Suggestion Actions', () => {
    const candidate: DebtCandidateTransactionDto = {
      id: 'tx-cand-1',
      amount: 4500,
      description: 'Transfer from Alice Sharma',
      merchant: 'Alice',
      transactionDate: '2026-10-15',
      transactionType: 'INCOME',
      transferDirection: null,
      isTopMatch: true,
      isStrongMatch: true,
      matchReason: 'Exact amount and counterparty match',
      score: 10010,
    };

    it('renders suggested match callout and settles active debt on Confirm & Link', () => {
      component.suggestionsByDebtId['debt-1'] = [candidate];
      fixture.detectChanges();

      const callout = fixture.nativeElement.querySelector('#debt-suggestion-debt-1');
      expect(callout).toBeTruthy();
      expect(callout.textContent).toContain('Transfer from Alice Sharma');
      expect(callout.textContent).toContain('Exact amount and counterparty match');

      mockDebtService.settle.and.returnValue(of({
        data: { ...mockDebts[0], status: 'SETTLED', linkedTransactionId: 'tx-cand-1' },
        error: null,
        meta: null,
      }));

      const confirmBtn = fixture.nativeElement.querySelector('#btn-confirm-link-debt-1');
      expect(confirmBtn).toBeTruthy();
      confirmBtn.click();

      expect(mockDebtService.settle).toHaveBeenCalledWith('debt-1', { linkedTransactionId: 'tx-cand-1' });
    });

    it('calls dedicated linkTransaction endpoint when Confirm & Link is clicked for settled debt', () => {
      const settledDebt: DebtDto = {
        ...mockDebts[0],
        id: 'debt-settled-1',
        status: 'SETTLED',
        settledAt: '2026-10-12T10:00:00Z',
        linkedTransactionId: null,
      };
      component.debts = [settledDebt];
      component.suggestionsByDebtId['debt-settled-1'] = [candidate];
      fixture.detectChanges();

      mockDebtService.linkTransaction.and.returnValue(of({
        data: { ...settledDebt, linkedTransactionId: 'tx-cand-1' },
        error: null,
        meta: null,
      }));

      component.confirmAndLink(settledDebt, candidate);
      expect(mockDebtService.linkTransaction).toHaveBeenCalledWith('debt-settled-1', { linkedTransactionId: 'tx-cand-1' });
      expect(mockDebtService.settle).not.toHaveBeenCalled();
    });

    it('dismisses candidate when Not a match is clicked and removes it from view', () => {
      component.suggestionsByDebtId['debt-1'] = [candidate];
      fixture.detectChanges();

      mockDebtService.dismissCandidate.and.returnValue(of({ data: null as any, error: null, meta: null }));

      const dismissBtn = fixture.nativeElement.querySelector('#btn-dismiss-match-debt-1');
      expect(dismissBtn).toBeTruthy();
      dismissBtn.click();

      expect(mockDebtService.dismissCandidate).toHaveBeenCalledWith('debt-1', 'tx-cand-1');
      expect(component.suggestionsByDebtId['debt-1']).toEqual([]);
    });
  });
});
