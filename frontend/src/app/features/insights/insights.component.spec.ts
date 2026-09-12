import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { InsightsOverview } from '../../core/api/api.types';
import { InsightsComponent } from './insights.component';
import { InsightsService } from './insights.service';

describe('InsightsComponent', () => {
  let component: InsightsComponent;
  let fixture: ComponentFixture<InsightsComponent>;
  let insightsServiceSpy: jasmine.SpyObj<InsightsService>;

  const mockOverview: InsightsOverview = {
    summary: {
      totalIncome: 120000,
      totalExpenses: 45000,
      netSavings: 75000,
      savingsRate: 62.5,
      transactionCount: 22,
      averageExpense: 2045.45,
      largestExpense: 9000,
      fromDate: '2026-09-01',
      toDate: '2026-09-30',
    },
    comparison: {
      currentFrom: '2026-09-01',
      currentTo: '2026-09-30',
      previousFrom: '2026-08-01',
      previousTo: '2026-08-31',
      income: {
        currentAmount: 120000,
        previousAmount: 110000,
        changeAmount: 10000,
        percentageChange: 9.09,
        direction: 'INCREASED',
      },
      expenses: {
        currentAmount: 45000,
        previousAmount: 35000,
        changeAmount: 10000,
        percentageChange: 28.57,
        direction: 'INCREASED',
      },
      netSavings: {
        currentAmount: 75000,
        previousAmount: 75000,
        changeAmount: 0,
        percentageChange: 0,
        direction: 'UNCHANGED',
      },
      currentSavingsRate: 62.5,
      previousSavingsRate: 68.18,
      savingsRateChange: -5.68,
    },
    categories: {
      totalExpenses: 45000,
      categories: [
        {
          category: 'FOOD',
          amount: 20000,
          percentage: 44.4,
          previousAmount: 12000,
          changeAmount: 8000,
          percentageChange: 66.67,
          direction: 'INCREASED',
          transactionCount: 14,
        },
        {
          category: 'ENTERTAINMENT',
          amount: 2000,
          percentage: 4.4,
          previousAmount: 5000,
          changeAmount: -3000,
          percentageChange: -60.0,
          direction: 'DECREASED',
          transactionCount: 2,
        },
      ],
      topCategory: 'FOOD',
    },
    trends: {
      period: 'MONTHLY',
      fromDate: '2026-07-01',
      toDate: '2026-09-30',
      points: [
        { label: 'Jul 2026', date: '2026-07-01', totalExpense: 30000, totalIncome: 100000, transactionCount: 15 },
        { label: 'Aug 2026', date: '2026-08-01', totalExpense: 35000, totalIncome: 110000, transactionCount: 20 },
        { label: 'Sep 2026', date: '2026-09-01', totalExpense: 45000, totalIncome: 120000, transactionCount: 22 },
      ],
    },
    anomalies: [
      {
        id: 'anom-1',
        type: 'PERIOD_SPIKE',
        title: 'Monthly Spending Surge',
        description: 'Overall spending increased by 28.6% compared to previous period.',
        reason: 'Surpassed period spike threshold of 30%',
        amount: 45000,
        baselineAmount: 35000,
        percentageOrMultiplier: 28.57,
        date: '2026-09-01',
      },
    ],
    insights: [
      {
        id: 'ins-1',
        type: 'CATEGORY_INCREASE',
        title: 'Food Spending Spike',
        description: 'Food spending rose by 66.7% this period.',
        category: 'FOOD',
        severity: 'WARNING',
        recommendation: 'Review recurring grocery and delivery orders.',
      },
    ],
  };

  beforeEach(async () => {
    insightsServiceSpy = jasmine.createSpyObj('InsightsService', [
      'getOverview',
      'getSummary',
      'getComparison',
      'getCategories',
      'getTrends',
      'getAnomalies',
      'getInsights',
    ]);
    insightsServiceSpy.getOverview.and.returnValue(of(mockOverview));
    insightsServiceSpy.getTrends.and.returnValue(of(mockOverview.trends));

    await TestBed.configureTestingModule({
      imports: [InsightsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InsightsService, useValue: insightsServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InsightsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders summary KPIs on init', () => {
    expect(insightsServiceSpy.getOverview).toHaveBeenCalled();
    expect(component.overview).toEqual(mockOverview);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('120,000'); // Total Income
    expect(el.textContent).toContain('45,000'); // Total Expense
    expect(el.textContent).toContain('75,000'); // Net Savings
    expect(el.textContent).toContain('62.5%'); // Savings Rate
  });

  it('displays automated financial insights and anomaly alerts', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Food Spending Spike');
    expect(el.textContent).toContain('Review recurring grocery and delivery orders.');
    expect(el.textContent).toContain('Monthly Spending Surge');
  });

  it('displays category shifts comparison correctly', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Food');
    expect(el.textContent).toContain('+66.67%');
    expect(el.textContent).toContain('Entertainment');
    expect(el.textContent).toContain('-60%');
  });

  it('supports trend period switching', () => {
    component.changeTrendPeriod('WEEKLY');
    expect(component.trendPeriod).toBe('WEEKLY');
    expect(insightsServiceSpy.getTrends).toHaveBeenCalledWith('WEEKLY', jasmine.any(Object));
  });

  it('supports preset filtering', () => {
    insightsServiceSpy.getOverview.calls.reset();
    component.applyPreset('last-month');
    expect(component.rangePreset).toBe('last-month');
    expect(insightsServiceSpy.getOverview).toHaveBeenCalled();
  });
});
