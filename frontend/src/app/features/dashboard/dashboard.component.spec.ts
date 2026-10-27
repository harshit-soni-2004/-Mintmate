import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { DashboardData, DebtDto, DebtSummaryDto, InsightsOverview, Transaction } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { DebtService } from '../../core/api/debt.service';
import { InsightsService } from '../insights/insights.service';
import { TransactionService } from '../transactions/transaction.service';
import { AssistantService } from '../assistant/assistant.service';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
  let insightsServiceSpy: jasmine.SpyObj<InsightsService>;
  let assistantServiceSpy: jasmine.SpyObj<AssistantService>;
  let debtServiceSpy: jasmine.SpyObj<DebtService>;
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
    dashboardServiceSpy.getDashboard.and.callFake(() =>
      of({ data: JSON.parse(JSON.stringify(mockDashboardData)), error: null, meta: null })
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

    assistantServiceSpy = jasmine.createSpyObj('AssistantService', ['getProactiveInsights', 'dismissInsight']);
    assistantServiceSpy.getProactiveInsights.and.returnValue(
      of({ data: [], error: null, meta: null })
    );

    debtServiceSpy = jasmine.createSpyObj('DebtService', ['getSummary']);
    (debtServiceSpy as any).onDebtMutated$ = new Subject<void>();
    debtServiceSpy.getSummary.and.returnValue(
      of({ data: null, error: null, meta: null })
    );

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: InsightsService, useValue: insightsServiceSpy },
        { provide: AssistantService, useValue: assistantServiceSpy },
        { provide: DebtService, useValue: debtServiceSpy },
        {
          provide: AuthService,
          useValue: {
            currentUser: () => ({ id: 'ada-1', displayName: 'Ada Lovelace', email: 'ada@example.com' }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    component.currentYearMonth = '2026-09';
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

  it('shows fallback "No spending activity yet this month" when topInsight is null and total expenses is zero', () => {
    component.topInsight = null;
    if (component.dashboardData) {
      component.dashboardData.summary.totalExpenses = 0;
    }
    fixture.detectChanges();

    expect(component.displayInsightTitle).toBe('No spending activity yet this month');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.insight-headline')?.textContent).toContain('No spending activity yet this month');
    expect(compiled.querySelector('.insight-desc')?.textContent).toContain('Once you record transactions or import statements');
  });

  it('displays declared baseline income and targets when zero transaction inflow', () => {
    if (component.dashboardData) {
      component.dashboardData.summary.totalIncome = 0;
      component.dashboardData.summary.declaredIncome = 95000;
      component.dashboardData.summary.monthlySavingsTarget = 30000;
      component.dashboardData.summary.monthlyInvestmentTarget = 20000;
      component.dashboardData.summary.hasOnboardingBaseline = true;
    }
    fixture.detectChanges();

    expect(component.effectiveIncome).toBe(95000);
    expect(component.isDeclaredBaselineIncome).toBeTrue();
    expect(component.savingsTarget).toBe(30000);
    expect(component.investTarget).toBe(20000);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.income-card .submetric-val')?.textContent).toContain('95,000');
    expect(compiled.querySelector('.income-card .baseline-subtext')?.textContent).toContain('Declared baseline');
    expect(compiled.querySelector('.goal-guardrail .g-numbers')?.textContent).toContain('30,000');
    expect(compiled.querySelector('.invest-guardrail .g-numbers')?.textContent).toContain('20,000');
  });

  it('builds dynamic cashflow chart matching calendar days and daily spending', () => {
    expect(component.cashflowPoints.length).toBe(30); // September 2026 has 30 days
    expect(component.isCashflowEmpty).toBeFalse();
    expect(component.cashflowLinePath).toContain('M ');
    expect(component.cashflowAreaPath).toContain(' Z');

    // Day 8 has 2450 expense
    const day8 = component.cashflowPoints.find((p) => p.date === '2026-09-08');
    expect(day8).toBeDefined();
    expect(day8?.expense).toBe(2450);
    expect(day8?.net).toBe(-2450);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.cashflow-line')).toBeTruthy();
  });

  it('updates cashflow chart dynamically when month changes with different day counts', () => {
    // Navigate from September 2026 (30 days) to August 2026 (31 days)
    component.changeMonth(-1);
    expect(component.currentYearMonth).toBe('2026-08');
    expect(component.cashflowPoints.length).toBe(31);

    // Navigate to February 2026 (28 days non-leap)
    component.currentYearMonth = '2026-02';
    component.buildCashflowChart();
    expect(component.cashflowPoints.length).toBe(28);

    // Navigate to February 2024 (29 days leap year)
    component.currentYearMonth = '2024-02';
    component.buildCashflowChart();
    expect(component.cashflowPoints.length).toBe(29);
  });

  it('renders empty state notice when selected month has zero financial activity', () => {
    const emptyData: DashboardData = {
      ...mockDashboardData,
      summary: {
        ...mockDashboardData.summary,
        totalIncome: 0,
        totalExpenses: 0,
        netSavings: 0,
        savingsRate: 0,
      },
      dailySpending: [],
      periodTransactions: [],
    };

    dashboardServiceSpy.getDashboard.and.returnValue(
      of({ data: emptyData, error: null, meta: null })
    );

    component.loadDashboard();
    fixture.detectChanges();

    expect(component.isCashflowEmpty).toBeTrue();
    expect(component.cashflowPoints.length).toBe(0);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.empty-sparkline-notice')).toBeTruthy();
    expect(compiled.querySelector('.empty-sparkline-text')?.textContent).toContain(
      'No financial activity recorded for this month.'
    );
  });

  it('handles hover and touch interactions on cashflow chart to display responsive tooltip', () => {
    expect(component.hoveredCashflowPoint).toBeNull();

    // Find Day 8 point
    const day8 = component.cashflowPoints.find((p) => p.date === '2026-09-08');
    expect(day8).toBeDefined();

    // Trigger hover on chart
    const compiled = fixture.nativeElement as HTMLElement;
    const chartWrap = compiled.querySelector('.hero-sparkline-wrap') as HTMLElement;
    expect(chartWrap).toBeTruthy();

    // Simulate mousemove with targetX matching day8.x
    component['findClosestCashflowPoint'](day8!.x);
    fixture.detectChanges();

    expect(component.hoveredCashflowPoint).toBe(day8!);
    expect(compiled.querySelector('.cashflow-tooltip')).toBeTruthy();
    expect(compiled.querySelector('.cashflow-cursor-dot')).toBeTruthy();

    // Leave chart
    component.onCashflowLeave();
    fixture.detectChanges();

    expect(component.hoveredCashflowPoint).toBeNull();
    expect(compiled.querySelector('.cashflow-tooltip')).toBeFalsy();
  });

  it('loads and renders proactive insight banner when available and allows dismissal', () => {
    const mockInsight = {
      id: 'insight-1',
      type: 'CATEGORY_SPIKE',
      severity: 'WARNING' as const,
      title: 'Food spending is 28% above your recent average',
      summary: 'You spent ₹12,000 on Food this month.',
      supportingFacts: {},
      createdAt: '2026-09-28T00:00:00Z',
    };
    assistantServiceSpy.getProactiveInsights.and.returnValue(
      of({ data: [mockInsight], error: null, meta: null })
    );

    component.loadProactiveInsights();
    fixture.detectChanges();

    expect(component.activeProactiveInsight).toBeDefined();
    expect(component.activeProactiveInsight?.title).toContain('Food spending is 28%');

    const banner = fixture.nativeElement.querySelector('.proactive-insight-banner');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Food spending is 28%');

    // Dismiss
    assistantServiceSpy.dismissInsight.and.returnValue(of({ data: { message: 'ok' }, error: null, meta: null }));
    component.dismissProactiveInsight('insight-1');
    fixture.detectChanges();

    expect(component.activeProactiveInsight).toBeNull();
    expect(fixture.nativeElement.querySelector('.proactive-insight-banner')).toBeNull();
  });

  it('renders debt card metrics and active reminders on dashboard', () => {
    if (component.dashboardData) {
      component.dashboardData.summary.debtOwedToUser = 6000;
      component.dashboardData.summary.debtOwedByUser = 2500;
      component.dashboardData.summary.netDebtPosition = 3500;
      component.dashboardData.summary.activeDebtRemindersCount = 2;
    }
    fixture.detectChanges();

    expect(component.debtOwedToUser).toBe(6000);
    expect(component.debtOwedByUser).toBe(2500);
    expect(component.netDebtPosition).toBe(3500);
    expect(component.activeDebtRemindersCount).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#dash-debt-owed-to-user')?.textContent).toContain('6,000');
    expect(compiled.querySelector('#dash-debt-owed-by-user')?.textContent).toContain('2,500');
    expect(compiled.querySelector('#dash-debt-net-position')?.textContent).toContain('3,500');
    expect(compiled.querySelector('#dash-debt-reminder-badge')?.textContent).toContain('2 items');
    expect(compiled.querySelector('#dash-view-debts-link')).toBeTruthy();
  });

  describe('Net Savings Semantic Styling & Cashflow Delta Calculations', () => {
    it('applies positive class when net savings is greater than zero', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.netSavings = 55000;
      }
      fixture.detectChanges();

      const heroNum = fixture.nativeElement.querySelector('.financial-hero-number');
      const submetricVal = fixture.nativeElement.querySelector('.savings-card .metric-value');

      expect(heroNum?.classList.contains('positive')).toBeTrue();
      expect(heroNum?.classList.contains('negative')).toBeFalse();
      expect(heroNum?.classList.contains('neutral')).toBeFalse();

      expect(submetricVal?.classList.contains('positive')).toBeTrue();
      expect(submetricVal?.classList.contains('negative')).toBeFalse();
    });

    it('applies negative class when net savings is less than zero', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.netSavings = -1580;
      }
      fixture.detectChanges();

      const heroNum = fixture.nativeElement.querySelector('.financial-hero-number');
      const submetricVal = fixture.nativeElement.querySelector('.savings-card .metric-value');

      expect(heroNum?.classList.contains('negative')).toBeTrue();
      expect(heroNum?.classList.contains('positive')).toBeFalse();
      expect(heroNum?.classList.contains('neutral')).toBeFalse();

      expect(submetricVal?.classList.contains('negative')).toBeTrue();
      expect(submetricVal?.classList.contains('positive')).toBeFalse();
    });

    it('applies neutral class when net savings is exactly zero', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.netSavings = 0;
      }
      fixture.detectChanges();

      const heroNum = fixture.nativeElement.querySelector('.financial-hero-number');
      const submetricVal = fixture.nativeElement.querySelector('.savings-card .metric-value');

      expect(heroNum?.classList.contains('neutral')).toBeTrue();
      expect(heroNum?.classList.contains('positive')).toBeFalse();
      expect(heroNum?.classList.contains('negative')).toBeFalse();

      expect(submetricVal?.classList.contains('neutral')).toBeTrue();
    });

    it('displays positive cashflow percentage badge with up arrow and positive class', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.incomeChangePercentage = 11.1;
      }
      fixture.detectChanges();

      expect(component.cashflowDelta).toEqual(jasmine.objectContaining({
        percentage: 11.1,
        formatted: '↑ +11.1% this month',
        isPositive: true,
        isNegative: false,
        isNeutral: false,
      }));

      const pill = fixture.nativeElement.querySelector('.delta-pill');
      expect(pill).toBeTruthy();
      expect(pill?.textContent).toContain('↑ +11.1% this month');
      expect(pill?.classList.contains('positive')).toBeTrue();
    });

    it('displays negative cashflow percentage badge with down arrow and negative class', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.incomeChangePercentage = -8.5;
      }
      fixture.detectChanges();

      expect(component.cashflowDelta).toEqual(jasmine.objectContaining({
        percentage: -8.5,
        formatted: '↓ -8.5% this month',
        isPositive: false,
        isNegative: true,
        isNeutral: false,
      }));

      const pill = fixture.nativeElement.querySelector('.delta-pill');
      expect(pill).toBeTruthy();
      expect(pill?.textContent).toContain('↓ -8.5% this month');
      expect(pill?.classList.contains('negative')).toBeTrue();
    });

    it('displays neutral cashflow percentage badge when change is zero', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.incomeChangePercentage = 0;
      }
      fixture.detectChanges();

      expect(component.cashflowDelta).toEqual(jasmine.objectContaining({
        percentage: 0,
        formatted: '→ 0% this month',
        isPositive: false,
        isNegative: false,
        isNeutral: true,
      }));

      const pill = fixture.nativeElement.querySelector('.delta-pill');
      expect(pill).toBeTruthy();
      expect(pill?.textContent).toContain('→ 0% this month');
      expect(pill?.classList.contains('neutral')).toBeTrue();
    });

    it('omits badge and does not display NaN or empty percentage when comparison data is missing', () => {
      if (component.dashboardData) {
        component.dashboardData.summary.incomeChangePercentage = null as any;
        component.dashboardData.summary.previousPeriodIncome = 0;
      }
      fixture.detectChanges();

      expect(component.cashflowDelta).toBeNull();
      const pill = fixture.nativeElement.querySelector('.delta-pill');
      expect(pill).toBeNull();

      const heroHeader = fixture.nativeElement.querySelector('.hero-header');
      expect(heroHeader?.textContent).not.toContain('NaN');
      expect(heroHeader?.textContent).not.toContain('undefined');
      expect(heroHeader?.textContent).not.toContain('null');
      expect(heroHeader?.textContent).not.toContain('% this month');
    });
  });

  describe('Spending Calendar - Expandable Daily Transactions', () => {
    const sampleExpenses: Transaction[] = [
      {
        id: 'tx-1',
        amount: 1450,
        merchant: 'Swiggy',
        description: 'Dinner delivery',
        category: 'FOOD',
        transactionType: 'EXPENSE',
        transactionDate: '2026-09-08',
        createdAt: '2026-09-08T20:30:00Z',
        updatedAt: '2026-09-08T20:30:00Z',
      },
      {
        id: 'tx-2',
        amount: 1000,
        merchant: 'Starbucks',
        description: 'Coffee meeting',
        category: 'FOOD',
        transactionType: 'EXPENSE',
        transactionDate: '2026-09-08',
        createdAt: '2026-09-08T11:15:00Z',
        updatedAt: '2026-09-08T11:15:00Z',
      },
    ];

    const sampleDay2Expenses: Transaction[] = [
      {
        id: 'tx-3',
        amount: 850,
        merchant: 'Uber',
        description: 'Ride to airport',
        category: 'TRAVEL',
        transactionType: 'EXPENSE',
        transactionDate: '2026-09-12',
        createdAt: '2026-09-12T06:45:00Z',
        updatedAt: '2026-09-12T06:45:00Z',
      },
    ];

    const sampleMixedTransactions: Transaction[] = [
      ...sampleExpenses,
      {
        id: 'tx-inc-1',
        amount: 50000,
        merchant: 'Employer',
        description: 'Monthly payroll',
        category: 'OTHER',
        transactionType: 'INCOME',
        transactionDate: '2026-09-08',
        createdAt: '2026-09-08T09:00:00Z',
        updatedAt: '2026-09-08T09:00:00Z',
      },
    ];

    it('shows "No spending on this day" notice and omits expand button on days with zero spending', () => {
      component.selectDate('2026-09-20');
      fixture.detectChanges();

      const emptyNotice = fixture.nativeElement.querySelector('.popover-empty-notice');
      expect(emptyNotice).toBeTruthy();
      expect(emptyNotice?.textContent).toContain('No spending on this day');

      const toggleBtn = fixture.nativeElement.querySelector('#btn-toggle-daily-tx');
      expect(toggleBtn).toBeNull();
      expect(component.isDailyTxExpanded).toBeFalse();
    });

    it('shows "View transactions" control with aria-expanded=false when a day with spending is selected', () => {
      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      fixture.detectChanges();

      const toggleBtn = fixture.nativeElement.querySelector('#btn-toggle-daily-tx');
      expect(toggleBtn).toBeTruthy();
      expect(toggleBtn.getAttribute('aria-expanded')).toBe('false');
      expect(toggleBtn.getAttribute('aria-controls')).toBe('daily-tx-card');
      expect(toggleBtn.textContent).toContain('View transactions');

      const expandedCard = fixture.nativeElement.querySelector('#daily-tx-card');
      expect(expandedCard).toBeNull();
    });

    it('expands card, fetches transactions via TransactionService.list with single-day filter, and updates aria-expanded to true', () => {
      spyOn(transactionService, 'list').and.returnValue(
        of({
          data: sampleExpenses,
          error: null,
          meta: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
        })
      );

      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      fixture.detectChanges();

      const toggleBtn = fixture.nativeElement.querySelector('#btn-toggle-daily-tx') as HTMLButtonElement;
      toggleBtn.click();
      fixture.detectChanges();

      expect(component.isDailyTxExpanded).toBeTrue();
      expect(transactionService.list).toHaveBeenCalledWith({
        fromDate: '2026-09-08',
        toDate: '2026-09-08',
        type: 'EXPENSE',
        page: 0,
        size: 50,
        sort: 'newest',
      });

      expect(toggleBtn.getAttribute('aria-expanded')).toBe('true');
      expect(toggleBtn.textContent).toContain('Hide transactions');

      const expandedCard = fixture.nativeElement.querySelector('#daily-tx-card');
      expect(expandedCard).toBeTruthy();

      const rows = fixture.nativeElement.querySelectorAll('.daily-tx-row');
      expect(rows.length).toBe(2);
      expect(rows[0].textContent).toContain('Dinner delivery');
      expect(rows[1].textContent).toContain('Coffee meeting');
    });

    it('collapses card when clicking toggle button again', () => {
      spyOn(transactionService, 'list').and.returnValue(
        of({
          data: sampleExpenses,
          error: null,
          meta: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
        })
      );

      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      fixture.detectChanges();

      const toggleBtn = fixture.nativeElement.querySelector('#btn-toggle-daily-tx') as HTMLButtonElement;
      toggleBtn.click();
      fixture.detectChanges();
      expect(component.isDailyTxExpanded).toBeTrue();

      toggleBtn.click();
      fixture.detectChanges();

      expect(component.isDailyTxExpanded).toBeFalse();
      expect(toggleBtn.getAttribute('aria-expanded')).toBe('false');
      expect(toggleBtn.textContent).toContain('View transactions');
      expect(fixture.nativeElement.querySelector('#daily-tx-card')).toBeNull();
    });

    it('persists expanded state when switching days and fetches new day transactions', () => {
      const listSpy = spyOn(transactionService, 'list').and.callFake((filters) => {
        if (filters.fromDate === '2026-09-08') {
          return of({
            data: sampleExpenses,
            error: null,
            meta: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
          });
        }
        return of({
          data: sampleDay2Expenses,
          error: null,
          meta: { page: 0, size: 50, totalElements: 1, totalPages: 1 },
        });
      });

      component.dashboardData!.periodTransactions = [...sampleExpenses, ...sampleDay2Expenses];
      component.selectDate('2026-09-08');
      component.toggleDailyTransactions();
      fixture.detectChanges();

      expect(component.isDailyTxExpanded).toBeTrue();
      expect(listSpy).toHaveBeenCalledWith(jasmine.objectContaining({ fromDate: '2026-09-08' }));

      // Switch to another day with spending
      component.selectDate('2026-09-12');
      fixture.detectChanges();

      expect(component.isDailyTxExpanded).toBeTrue();
      expect(listSpy).toHaveBeenCalledWith(jasmine.objectContaining({ fromDate: '2026-09-12' }));

      const rows = fixture.nativeElement.querySelectorAll('.daily-tx-row');
      expect(rows.length).toBe(1);
      expect(rows[0].textContent).toContain('Ride to airport');
    });

    it('shows loading indicator while transactions are loading', () => {
      spyOn(transactionService, 'list').and.returnValue(new Observable());

      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      component.toggleDailyTransactions();
      fixture.detectChanges();

      expect(component.dailyTxLoading).toBeTrue();
      const loadingEl = fixture.nativeElement.querySelector('.daily-tx-loading');
      expect(loadingEl).toBeTruthy();
      expect(loadingEl.textContent).toContain('Loading transactions…');
    });

    it('shows clean error state and allows retrying when API request fails', () => {
      const listSpy = spyOn(transactionService, 'list').and.returnValue(
        throwError(() => new Error('Server timeout 500'))
      );

      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      component.toggleDailyTransactions();
      fixture.detectChanges();

      expect(component.dailyTxLoading).toBeFalse();
      expect(component.dailyTxError).toBe("Couldn't load transactions. Try again.");

      const errorEl = fixture.nativeElement.querySelector('.daily-tx-error');
      expect(errorEl).toBeTruthy();
      expect(errorEl.textContent).toContain("Couldn't load transactions. Try again.");
      expect(errorEl.textContent).not.toContain('500');
      expect(errorEl.textContent).not.toContain('Server timeout');

      const retryBtn = errorEl.querySelector('.btn-retry-daily-tx') as HTMLButtonElement;
      expect(retryBtn).toBeTruthy();

      // Setup successful response for retry
      listSpy.and.returnValue(
        of({
          data: sampleExpenses,
          error: null,
          meta: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
        })
      );

      retryBtn.click();
      fixture.detectChanges();

      expect(component.dailyTxError).toBe('');
      expect(fixture.nativeElement.querySelectorAll('.daily-tx-row').length).toBe(2);
    });

    it('maintains strict consistency: row count matches transactionCount and amounts sum to totalExpense', () => {
      component.dashboardData!.periodTransactions = sampleMixedTransactions;
      component.selectDate('2026-09-08');

      spyOn(transactionService, 'list').and.returnValue(
        of({
          data: sampleMixedTransactions,
          error: null,
          meta: { page: 0, size: 50, totalElements: 3, totalPages: 1 },
        })
      );

      component.toggleDailyTransactions();
      fixture.detectChanges();

      // Check summary bar figures
      expect(component.selectedDateSummary.transactionCount).toBe(2);
      expect(component.selectedDateSummary.totalExpense).toBe(2450);

      // Check daily transactions in expanded list
      expect(component.dailyTransactions.length).toBe(component.selectedDateSummary.transactionCount);
      const rowSum = component.dailyTransactions.reduce((acc, tx) => acc + tx.amount, 0);
      expect(rowSum).toBe(component.selectedDateSummary.totalExpense);

      // Verify no income or transfers in daily list
      const allExpenses = component.dailyTransactions.every((tx) => tx.transactionType === 'EXPENSE');
      expect(allExpenses).toBeTrue();
    });

    it('opens existing edit modal when a transaction row is clicked', () => {
      spyOn(transactionService, 'list').and.returnValue(
        of({
          data: sampleExpenses,
          error: null,
          meta: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
        })
      );

      component.dashboardData!.periodTransactions = sampleExpenses;
      component.selectDate('2026-09-08');
      component.toggleDailyTransactions();
      fixture.detectChanges();

      const spyOpenEdit = spyOn(component, 'openEdit').and.callThrough();
      const firstRow = fixture.nativeElement.querySelector('.daily-tx-row') as HTMLElement;
      firstRow.click();
      fixture.detectChanges();

      expect(spyOpenEdit).toHaveBeenCalledWith(sampleExpenses[0]);
      expect(component.showForm).toBeTrue();
      expect(component.editing).toEqual(sampleExpenses[0]);
    });
  });

  describe('Dashboard - Debt Reminder Combined Banner', () => {
    beforeEach(() => {
      localStorage.clear();
      spyOn(component, 'getTodayIST').and.returnValue('2026-09-08');
    });

    it('does not render banner when there are no qualifying debts', () => {
      component.updateDebtReminderBanner({
        totalReceivable: 0,
        totalPayable: 0,
        netDebtPosition: 0,
        activeCount: 0,
        overdueCount: 0,
        upcomingDebts: [],
        overdueDebts: [],
      });
      fixture.detectChanges();

      expect(component.debtReminderBanner).toBeNull();
      expect(component.showDebtBanner).toBeFalse();
      expect(fixture.nativeElement.querySelector('#debt-reminder-banner')).toBeNull();
    });

    it('renders amber banner when a debt is due in exactly 3 days (due soon boundary)', () => {
      const debtDueIn3Days: DebtDto = {
        id: 'd-3days',
        counterpartyName: 'Rahul',
        debtType: 'OWED_TO_ME',
        amount: 1500,
        dueDate: '2026-09-11', // 2026-09-08 + 3 days
        status: 'ACTIVE',
        overdue: false,
        createdAt: '2026-09-01T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 1500,
        totalPayable: 0,
        netDebtPosition: 1500,
        activeCount: 1,
        overdueCount: 0,
        upcomingDebts: [debtDueIn3Days],
        overdueDebts: [],
      });
      fixture.detectChanges();

      expect(component.showDebtBanner).toBeTrue();
      expect(component.debtReminderBanner?.severity).toBe('warning');
      expect(component.debtReminderBanner?.title).toContain('1 debt due soon · ₹1,500 due');
      expect(component.debtReminderBanner?.subtitle).toBe('Due within 3 days');

      const bannerEl = fixture.nativeElement.querySelector('#debt-reminder-banner');
      expect(bannerEl).toBeTruthy();
      expect(bannerEl.classList.contains('status-due-soon')).toBeTrue();
      expect(bannerEl.querySelector('#debt-banner-title')?.textContent).toContain('1 debt due soon · ₹1,500 due');

      const link = bannerEl.querySelector('#btn-debt-banner-link') as HTMLAnchorElement;
      expect(link).toBeTruthy();
      expect(link.getAttribute('routerLink')).toBe('/app/debts');
    });

    it('does NOT render banner when debt is due in 4 days (outside 3-day threshold)', () => {
      const debtDueIn4Days: DebtDto = {
        id: 'd-4days',
        counterpartyName: 'Priya',
        debtType: 'I_OWE',
        amount: 2000,
        dueDate: '2026-09-12', // 2026-09-08 + 4 days
        status: 'ACTIVE',
        overdue: false,
        createdAt: '2026-09-01T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 0,
        totalPayable: 2000,
        netDebtPosition: -2000,
        activeCount: 1,
        overdueCount: 0,
        upcomingDebts: [debtDueIn4Days],
        overdueDebts: [],
      });
      fixture.detectChanges();

      expect(component.debtReminderBanner).toBeNull();
      expect(component.showDebtBanner).toBeFalse();
      expect(fixture.nativeElement.querySelector('#debt-reminder-banner')).toBeNull();
    });

    it('renders red banner when there is an overdue debt', () => {
      const overdueDebt: DebtDto = {
        id: 'd-overdue',
        counterpartyName: 'Anil',
        debtType: 'OWED_TO_ME',
        amount: 3400,
        dueDate: '2026-09-07', // 1 day overdue relative to 2026-09-08
        status: 'ACTIVE',
        overdue: true,
        createdAt: '2026-08-20T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 3400,
        totalPayable: 0,
        netDebtPosition: 3400,
        activeCount: 1,
        overdueCount: 1,
        upcomingDebts: [],
        overdueDebts: [overdueDebt],
      });
      fixture.detectChanges();

      expect(component.showDebtBanner).toBeTrue();
      expect(component.debtReminderBanner?.severity).toBe('danger');
      expect(component.debtReminderBanner?.title).toContain('1 debt needs attention · ₹3,400 overdue');

      const bannerEl = fixture.nativeElement.querySelector('#debt-reminder-banner');
      expect(bannerEl).toBeTruthy();
      expect(bannerEl.classList.contains('status-overdue')).toBeTrue();
    });

    it('renders combined red banner with plural count when both overdue and due soon debts exist', () => {
      const overdueDebt: DebtDto = {
        id: 'd-od',
        counterpartyName: 'Anil',
        debtType: 'OWED_TO_ME',
        amount: 3400,
        dueDate: '2026-09-05',
        status: 'ACTIVE',
        overdue: true,
        createdAt: '2026-08-20T10:00:00Z',
      };
      const dueSoonDebt: DebtDto = {
        id: 'd-ds',
        counterpartyName: 'Kiran',
        debtType: 'I_OWE',
        amount: 1200,
        dueDate: '2026-09-10', // 2 days away
        status: 'ACTIVE',
        overdue: false,
        createdAt: '2026-08-25T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 3400,
        totalPayable: 1200,
        netDebtPosition: 2200,
        activeCount: 2,
        overdueCount: 1,
        upcomingDebts: [dueSoonDebt],
        overdueDebts: [overdueDebt],
      });
      fixture.detectChanges();

      expect(component.debtReminderBanner?.severity).toBe('danger');
      expect(component.debtReminderBanner?.title).toContain('2 debts need attention · ₹3,400 overdue');
      expect(component.debtReminderBanner?.subtitle).toContain('1 overdue, 1 due within 3 days');
    });

    it('persists dismissal in localStorage for 24 hours and snoozes banner', () => {
      const overdueDebt: DebtDto = {
        id: 'd-od',
        counterpartyName: 'Anil',
        debtType: 'OWED_TO_ME',
        amount: 3400,
        dueDate: '2026-09-05',
        status: 'ACTIVE',
        overdue: true,
        createdAt: '2026-08-20T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 3400,
        totalPayable: 0,
        netDebtPosition: 3400,
        activeCount: 1,
        overdueCount: 1,
        upcomingDebts: [],
        overdueDebts: [overdueDebt],
      });
      fixture.detectChanges();

      const dismissBtn = fixture.nativeElement.querySelector('#btn-dismiss-debt-banner') as HTMLButtonElement;
      expect(dismissBtn).toBeTruthy();

      // Click dismiss
      dismissBtn.click();
      fixture.detectChanges();

      expect(component.isDebtBannerDismissed).toBeTrue();
      expect(component.showDebtBanner).toBeFalse();
      expect(fixture.nativeElement.querySelector('#debt-reminder-banner')).toBeNull();

      // Verify localStorage was written with current timestamp
      const storedSnooze = localStorage.getItem('spendsense_debt_banner_snooze_ada-1');
      expect(storedSnooze).toBeTruthy();
      expect(Number(storedSnooze)).toBeGreaterThan(0);

      // Re-evaluating loadDebtSummary while within 24 hours keeps banner hidden
      debtServiceSpy.getSummary.and.returnValue(
        of({
          data: {
            totalReceivable: 3400,
            totalPayable: 0,
            netDebtPosition: 3400,
            activeCount: 1,
            overdueCount: 1,
            upcomingDebts: [],
            overdueDebts: [overdueDebt],
          },
          error: null,
          meta: null,
        })
      );
      component.loadDebtSummary();
      fixture.detectChanges();
      expect(component.showDebtBanner).toBeFalse();

      // When snooze expires after 24h (e.g. 25h ago)
      const expiredTimestamp = Date.now() - 25 * 60 * 60 * 1000;
      localStorage.setItem('spendsense_debt_banner_snooze_ada-1', String(expiredTimestamp));

      component.loadDebtSummary();
      fixture.detectChanges();
      expect(component.showDebtBanner).toBeTrue();
      expect(fixture.nativeElement.querySelector('#debt-reminder-banner')).toBeTruthy();
    });

    it('banner state reflects current debts and is unaffected by dashboard month changes', () => {
      const overdueDebt: DebtDto = {
        id: 'd-od',
        counterpartyName: 'Anil',
        debtType: 'OWED_TO_ME',
        amount: 3400,
        dueDate: '2026-09-05',
        status: 'ACTIVE',
        overdue: true,
        createdAt: '2026-08-20T10:00:00Z',
      };

      component.updateDebtReminderBanner({
        totalReceivable: 3400,
        totalPayable: 0,
        netDebtPosition: 3400,
        activeCount: 1,
        overdueCount: 1,
        upcomingDebts: [],
        overdueDebts: [overdueDebt],
      });
      fixture.detectChanges();

      expect(component.showDebtBanner).toBeTrue();
      const initialBannerTitle = component.debtReminderBanner?.title;

      // User navigates month backwards and forwards on dashboard
      component.changeMonth(-1);
      fixture.detectChanges();
      expect(component.showDebtBanner).toBeTrue();
      expect(component.debtReminderBanner?.title).toBe(initialBannerTitle);

      component.changeMonth(2);
      fixture.detectChanges();
      expect(component.showDebtBanner).toBeTrue();
      expect(component.debtReminderBanner?.title).toBe(initialBannerTitle);
    });
  });
});

