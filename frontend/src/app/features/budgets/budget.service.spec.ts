import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { BudgetRequest } from '../../core/api/api.types';
import { BudgetService } from './budget.service';

describe('BudgetService', () => {
  let service: BudgetService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BudgetService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('fetches budget summary from API', () => {
    service.getSummary().subscribe((res) => {
      expect(res.data?.totalBudget).toBe(10000);
    });

    const req = http.expectOne(`${environment.apiUrl}/budgets/summary`);
    expect(req.request.method).toBe('GET');
    req.flush({
      data: {
        totalBudget: 10000,
        totalSpent: 3000,
        totalRemaining: 7000,
        percentageUsed: 30,
        onTrackCount: 1,
        warningCount: 0,
        exceededCount: 0,
        totalBudgets: 1,
        budgets: [],
      },
      error: null,
      meta: null,
    });
  });

  it('triggers onBudgetMutated$ when creating a budget', (done) => {
    service.onBudgetMutated$.subscribe(() => {
      expect(true).toBeTrue();
      done();
    });

    const payload: BudgetRequest = {
      category: 'FOOD',
      limitAmount: 5000,
      period: 'MONTHLY',
      startDate: '2026-09-01',
      endDate: '2026-09-30',
    };

    service.create(payload).subscribe();

    const req = http.expectOne(`${environment.apiUrl}/budgets`);
    expect(req.request.method).toBe('POST');
    req.flush({ data: { id: 1, ...payload }, error: null, meta: null });
  });
});
