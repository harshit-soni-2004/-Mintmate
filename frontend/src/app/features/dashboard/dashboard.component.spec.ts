import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DashboardData } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { TransactionService } from '../transactions/transaction.service';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
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

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DashboardService, useValue: dashboardServiceSpy },
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
});
