import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CreateDebtRequest, DebtDto, DebtSummaryDto, SettleDebtRequest } from './api.types';
import { DebtService } from './debt.service';

describe('DebtService', () => {
  let service: DebtService;
  let httpTesting: HttpTestingController;

  const mockDebt: DebtDto = {
    id: 'deb-1',
    counterpartyName: 'Alice Sharma',
    debtType: 'OWED_TO_ME',
    amount: 3500,
    dueDate: '2026-10-25',
    status: 'ACTIVE',
    settledAt: null,
    linkedTransactionId: null,
    notes: 'Dinner split',
    createdAt: '2026-10-12T10:00:00Z',
    overdue: false,
  };

  const mockSummary: DebtSummaryDto = {
    totalReceivable: 3500,
    totalPayable: 1200,
    netDebtPosition: 2300,
    activeCount: 2,
    overdueCount: 0,
    upcomingDebts: [mockDebt],
    overdueDebts: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DebtService,
      ],
    });

    service = TestBed.inject(DebtService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('lists debts without status parameter', () => {
    service.list().subscribe((res) => {
      expect(res.data?.length).toBe(1);
      expect(res.data?.[0].counterpartyName).toBe('Alice Sharma');
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: [mockDebt], error: null, meta: null });
  });

  it('lists debts with status filter', () => {
    service.list('ACTIVE').subscribe((res) => {
      expect(res.data?.length).toBe(1);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts?status=ACTIVE`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: [mockDebt], error: null, meta: null });
  });

  it('fetches debt summary', () => {
    service.getSummary().subscribe((res) => {
      expect(res.data?.totalReceivable).toBe(3500);
      expect(res.data?.netDebtPosition).toBe(2300);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts/summary`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: mockSummary, error: null, meta: null });
  });

  it('creates new debt and notifies mutation subject', (done) => {
    const payload: CreateDebtRequest = {
      counterpartyName: 'Bob Verma',
      debtType: 'I_OWE',
      amount: 1500,
      dueDate: '2026-10-30',
      notes: 'Concert tickets',
    };

    let mutationFired = false;
    service.onDebtMutated$.subscribe(() => {
      mutationFired = true;
    });

    service.create(payload).subscribe((res) => {
      expect(res.data?.counterpartyName).toBe('Bob Verma');
      expect(mutationFired).toBeTrue();
      done();
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({
      data: { ...mockDebt, id: 'deb-2', counterpartyName: 'Bob Verma', debtType: 'I_OWE', amount: 1500 },
      error: null,
      meta: null,
    });
  });

  it('settles debt with linked transaction', (done) => {
    const settlePayload: SettleDebtRequest = {
      linkedTransactionId: 'tx-999',
    };

    let mutationFired = false;
    service.onDebtMutated$.subscribe(() => {
      mutationFired = true;
    });

    service.settle('deb-1', settlePayload).subscribe((res) => {
      expect(res.data?.status).toBe('SETTLED');
      expect(mutationFired).toBeTrue();
      done();
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts/deb-1/settle`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(settlePayload);
    req.flush({
      data: { ...mockDebt, status: 'SETTLED', linkedTransactionId: 'tx-999' },
      error: null,
      meta: null,
    });
  });

  it('cancels debt', (done) => {
    let mutationFired = false;
    service.onDebtMutated$.subscribe(() => {
      mutationFired = true;
    });

    service.cancel('deb-1').subscribe((res) => {
      expect(res.data?.status).toBe('CANCELLED');
      expect(mutationFired).toBeTrue();
      done();
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/debts/deb-1/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush({
      data: { ...mockDebt, status: 'CANCELLED' },
      error: null,
      meta: null,
    });
  });
});
