import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { InsightsOverview, SpendingAnomaly, SpendingTrend } from '../../core/api/api.types';
import { InsightsService } from './insights.service';

describe('InsightsService', () => {
  let service: InsightsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InsightsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('fetches insights overview with month query parameter', () => {
    const mockOverview: InsightsOverview = {
      summary: {
        totalIncome: 100000,
        totalExpenses: 40000,
        netSavings: 60000,
        savingsRate: 60.0,
        transactionCount: 20,
        averageExpense: 2000,
        largestExpense: 8000,
        fromDate: '2026-09-01',
        toDate: '2026-09-30',
      },
      comparison: {
        currentFrom: '2026-09-01',
        currentTo: '2026-09-30',
        previousFrom: '2026-08-01',
        previousTo: '2026-08-31',
        income: {
          currentAmount: 100000,
          previousAmount: 100000,
          changeAmount: 0,
          percentageChange: 0,
          direction: 'UNCHANGED',
        },
        expenses: {
          currentAmount: 40000,
          previousAmount: 30000,
          changeAmount: 10000,
          percentageChange: 33.33,
          direction: 'INCREASED',
        },
        netSavings: {
          currentAmount: 60000,
          previousAmount: 70000,
          changeAmount: -10000,
          percentageChange: -14.29,
          direction: 'DECREASED',
        },
        currentSavingsRate: 60.0,
        previousSavingsRate: 70.0,
        savingsRateChange: -10.0,
      },
      categories: {
        totalExpenses: 40000,
        categories: [],
        topCategory: 'FOOD',
      },
      trends: {
        period: 'MONTHLY',
        fromDate: '2026-07-01',
        toDate: '2026-09-30',
        points: [],
      },
      anomalies: [],
      insights: [
        {
          id: 'ins-1',
          type: 'SAVINGS_IMPROVED',
          title: 'Strong Savings',
          description: 'You saved 60% of income',
          severity: 'INFO',
          recommendation: 'Keep it up',
        },
      ],
    };

    service.getOverview({ month: '2026-09' }).subscribe((res) => {
      expect(res.summary.netSavings).toBe(60000);
      expect(res.insights.length).toBe(1);
    });

    const req = http.expectOne(`${environment.apiUrl}/insights?month=2026-09`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: mockOverview, error: null, meta: null });
  });

  it('fetches anomalies list from API', () => {
    const mockAnomalies: SpendingAnomaly[] = [
      {
        id: 'anom-1',
        type: 'LARGE_TRANSACTION',
        title: 'Large Expense Detected',
        description: 'Single purchase above threshold',
        reason: '3x average spending',
        amount: 15000,
        baselineAmount: 5000,
        percentageOrMultiplier: 3.0,
        category: 'SHOPPING',
        date: '2026-09-10',
      },
    ];

    service.getAnomalies().subscribe((data) => {
      expect(data.length).toBe(1);
      expect(data[0].amount).toBe(15000);
    });

    const req = http.expectOne(`${environment.apiUrl}/insights/anomalies`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: mockAnomalies, error: null, meta: null });
  });

  it('fetches trends with specified aggregation period', () => {
    const mockTrend: SpendingTrend = {
      period: 'WEEKLY',
      fromDate: '2026-09-01',
      toDate: '2026-09-30',
      points: [
        { label: 'Week 1', date: '2026-09-01', totalExpense: 10000, totalIncome: 0, transactionCount: 5 },
      ],
    };

    service.getTrends('WEEKLY').subscribe((data) => {
      expect(data.period).toBe('WEEKLY');
      expect(data.points.length).toBe(1);
    });

    const req = http.expectOne(`${environment.apiUrl}/insights/trends?period=WEEKLY`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: mockTrend, error: null, meta: null });
  });
});
