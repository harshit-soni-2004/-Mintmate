import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DashboardData, InsightsOverview } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { InsightsService } from '../insights/insights.service';
import { TransactionService } from '../transactions/transaction.service';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
  let insightsServiceSpy: jasmine.SpyObj<InsightsService>;
  let transactionService: TransactionService;

  const mockDashboardData: DashboardData = {
    summary: {
      totalIncome: 100000,
      totalExpenses: 45000,
      netSavings: 55000,
      savingsRate: 55,
      previousPeriodIncome: 90000,
      previousPeriodExpenses: 50000,
      incomeChangePercentage: 11.1,
      expenseChangePercentage: -10,
    },
    categorySpending: [
      { category: 'FOOD', amount: 25000, percentage: 55.6, transactionCount: 10 },
      { category: 'SHOPPING', amount: 20000, percentage: 44.4, transactionCount: 4 },
    ],
    dailySpending: [
      { date: '2026-09-08', totalExpense: 2450, totalIncome: 0, transactionCount: 2 },
    ],
    recentTransactions: [
      {
        id: 't-1',
        amount: 2450,
        merchant: 'Swiggy',
        category: 'FOOD',
        transactionType: 'EXPENSE',
        transactionDate: '2026-09-08',
        createdAt: '2026-09-08T10:00:00Z',
        updatedAt: '2026-09-08T10:00:00Z',
      },
    ],
    periodTransactions: [
      {
        id: 't-1',
        amount: 2450,
        merchant: 'Swiggy',
        category: 'FOOD',
        transactionType: 'EXPENSE',
        transactionDate: '2026-09-08',
        createdAt: '2026-09-08T10:00:00Z',
        updatedAt: '2026-09-08T10:00:00Z',
      },
    ],
    fromDate: '2026-09-01',
    toDate: '2026-09-30',
  };

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('DashboardService', ['getDashboard']);
    dashboardServiceSpy.getDashboard.and.returnValue(
      of({ data: mockDashboardData, error: null, meta: null })
    );

    insightsServiceSpy = jasmine.createSpyObj('InsightsService', ['getOverview']);
    insightsServiceSpy.getOverview.and.returnValue(
      of({
        summary: {
          totalIncome: 100000,
          totalExpenses: 45000,
          netSavings: 55000,
          savingsRate: 55,
          transactionCount: 14,
          averageExpense: 3214.28,
          largestExpense: 8000,
          fromDate: '2026-09-01',
          toDate: '2026-09-30',
        },
        comparison: {
          currentFrom: '2026-09-01',
          currentTo: '2026-09-30',
          previousFrom: '2026-08-01',
          previousTo: '2026-08-31',
          spendingChange: { currentAmount: 45000, previousAmount: 50000, changeAmount: -5000, percentageChange: -10, direction: 'DECREASED' },
          incomeChange: { currentAmount: 100000, previousAmount: 90000, changeAmount: 10000, percentageChange: 11.1, direction: 'INCREASED' },
          expenses: { currentAmount: 45000, previousAmount: 50000, changeAmount: -5000, percentageChange: -10, direction: 'DECREASED' },
          income: { currentAmount: 100000, previousAmount: 90000, changeAmount: 10000, percentageChange: 11.1, direction: 'INCREASED' },
          netSavings: { currentAmount: 55000, previousAmount: 40000, changeAmount: 15000, percentageChange: 37.5, direction: 'INCREASED' },
          currentSavingsRate: 55,
          previousSavingsRate: 44.4,
          savingsRateChange: 10.6,
        },
        categories: { totalExpenses: 45000, categories: [], topCategory: 'FOOD' },
        trends: { period: 'MONTHLY', fromDate: '2026-07-01', toDate: '2026-09-30', points: [] },
        anomalies: [
          {
            id: 'a-1',
            type: 'LARGE_TRANSACTION',
            title: 'High Transaction',
            description: 'Unusually large expense',
            reason: '3x average spending',
            amount: 8000,
            baselineAmount: 2000,
            percentageOrMultiplier: 4.0,
            date: '2026-09-08',
          },
        ],
        insights: [
          {
            id: 'i-1',
            type: 'SAVINGS_IMPROVED',
            title: 'Strong Savings Rate',
            description: 'You saved 55% of your income this month.',
            severity: 'POSITIVE',
            recommendation: 'Allocate additional funds to savings goals.',
          },
        ],
      } as unknown as InsightsOverview)
    );

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: InsightsService, useValue: insightsServiceSpy },
        {
          provide: AuthService,
          useValue: {
            currentUser: () => ({ displayName: 'Ada Lovelace', email: 'ada@example.com' }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    transactionService = TestBed.inject(TransactionService);
    fixture.detectChanges();
  });

  it('initializes and renders financial summary from database response', () => {
    expect(dashboardServiceSpy.getDashboard).toHaveBeenCalled();
    expect(component.dashboardData).toEqual(mockDashboardData);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.income-card .metric-value')?.textContent).toContain('100,000');
    expect(compiled.querySelector('.expense-card .metric-value')?.textContent).toContain('45,000');
    expect(compiled.querySelector('.savings-card .metric-value')?.textContent).toContain('55,000');
  });

  it('updates selected date details when user clicks a date cell in calendar', () => {
    component.selectDate('2026-09-08');
    fixture.detectChanges();

    expect(component.selectedDateSummary.date).toBe('2026-09-08');
    expect(component.selectedDateSummary.totalExpense).toBe(2450);
    expect(component.selectedDateSummary.transactionCount).toBe(1);
    expect(component.selectedDateSummary.transactions.length).toBe(1);
    expect(component.selectedDateSummary.transactions[0].merchant).toBe('Swiggy');
  });

  it('switches chart periods between day, week, and month', () => {
    component.setChartMode('week');
    expect(component.chartMode).toBe('week');
    expect(component.chartBars.length).toBe(5);

    component.setChartMode('month');
    expect(component.chartMode).toBe('month');
    expect(component.chartBars.length).toBe(2);
    expect(component.chartBars[1].label).toBe('This Month');
  });

  it('re-fetches dashboard data when transaction service emits mutation event', () => {
    dashboardServiceSpy.getDashboard.calls.reset();
    transactionService.notifyMutated();

    expect(dashboardServiceSpy.getDashboard).toHaveBeenCalled();
  });

  it('renders top financial insight and anomaly alert in intelligence widget', () => {
    expect(insightsServiceSpy.getOverview).toHaveBeenCalled();
    expect(component.topInsight).toBeDefined();
    expect(component.topInsight?.title).toBe('Strong Savings Rate');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.dash-insights-content')?.textContent).toContain('Strong Savings Rate');
    expect(compiled.querySelector('.dash-anomaly-alert')?.textContent).toContain('High Transaction');
  });
});
