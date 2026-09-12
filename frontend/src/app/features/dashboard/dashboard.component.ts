import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  BudgetStatus,
  BudgetSummary,
  CategorySpending,
  DailySpending,
  DashboardData,
  Transaction,
  TransactionCategory,
  TransactionRequest,
  TransactionType,
} from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { BudgetService } from '../budgets/budget.service';
import { TransactionService } from '../transactions/transaction.service';
import { DashboardService } from './dashboard.service';

export interface CalendarDay {
  dateString: string;
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  totalExpense: number;
  totalIncome: number;
  transactionCount: number;
  intensity: 'none' | 'low' | 'moderate' | 'high';
}

export interface ChartBar {
  label: string;
  dateKey?: string;
  expense: number;
  income: number;
  heightPct: number;
}

export interface DonutSegment {
  category: TransactionCategory;
  amount: number;
  percentage: number;
  strokeDasharray: string;
  strokeDashoffset: number;
  color: string;
}

const CATEGORY_COLORS: Record<TransactionCategory, string> = {
  FOOD: '#e67e3d',
  TRAVEL: '#5579d8',
  SHOPPING: '#a15bb3',
  ENTERTAINMENT: '#8e44ad',
  BILLS: '#3b9c94',
  HEALTH: '#27ae60',
  EDUCATION: '#2980b9',
  PERSONAL: '#d35400',
  OTHER: '#7d8791',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, CurrencyPipe, DatePipe, NgClass],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);
  private readonly transactionService = inject(TransactionService);
  private readonly budgetService = inject(BudgetService);
  private readonly fb = inject(FormBuilder);

  private mutationSub?: Subscription;

  readonly categories: TransactionCategory[] = [
    'FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER'
  ];

  loading = true;
  error = '';
  dashboardData: DashboardData | null = null;
  budgetSummary: BudgetSummary | null = null;

  // Selected period: "YYYY-MM"
  currentYearMonth = new Date().toISOString().slice(0, 7);

  // Chart view mode
  chartMode: 'day' | 'week' | 'month' = 'day';
  chartBars: ChartBar[] = [];
  hoveredBar: ChartBar | null = null;

  // Donut chart segments
  donutSegments: DonutSegment[] = [];

  // Calendar
  calendarDays: CalendarDay[] = [];
  selectedDate: string = new Date().toISOString().slice(0, 10);
  selectedDateSummary = {
    date: this.selectedDate,
    totalExpense: 0,
    totalIncome: 0,
    transactionCount: 0,
    categoryBreakdown: [] as { category: TransactionCategory; amount: number; color: string }[],
    transactions: [] as Transaction[],
  };

  // Selected Category filter for inspection
  selectedCategory: TransactionCategory | null = null;

  // Add / Edit Transaction modal
  showForm = false;
  editing: Transaction | null = null;
  submitting = false;
  formError = '';

  readonly form = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    merchant: ['', [Validators.required, Validators.maxLength(120)]],
    category: ['OTHER' as TransactionCategory, Validators.required],
    transactionType: ['EXPENSE' as TransactionType, Validators.required],
    transactionDate: [this.selectedDate, Validators.required],
    notes: ['', Validators.maxLength(1000)],
  });

  ngOnInit(): void {
    this.loadDashboard();
    this.loadBudgets();

    // Automatically refresh dashboard and budgets whenever any transaction is mutated
    const sub1 = this.transactionService.onTransactionMutated$.subscribe(() => {
      this.loadDashboard(false);
      this.loadBudgets();
    });

    const sub2 = this.budgetService.onBudgetMutated$.subscribe(() => {
      this.loadBudgets();
    });

    this.mutationSub = new Subscription();
    this.mutationSub.add(sub1);
    this.mutationSub.add(sub2);
  }

  ngOnDestroy(): void {
    this.mutationSub?.unsubscribe();
  }

  loadBudgets(): void {
    this.budgetService.getSummary().subscribe({
      next: (res) => {
        this.budgetSummary = res.data;
      },
      error: () => {
        // Silently fail or keep previous summary
      },
    });
  }

  getBudgetStatusClass(status: BudgetStatus): string {
    switch (status) {
      case 'ON_TRACK': return 'status-on-track';
      case 'WARNING': return 'status-warning';
      case 'EXCEEDED': return 'status-exceeded';
    }
  }

  getBudgetFillClass(status: BudgetStatus): string {
    switch (status) {
      case 'ON_TRACK': return 'fill-on-track';
      case 'WARNING': return 'fill-warning';
      case 'EXCEEDED': return 'fill-exceeded';
    }
  }

  loadDashboard(setLoading = true): void {
    if (setLoading) this.loading = true;
    this.error = '';

    this.dashboardService.getDashboard({ month: this.currentYearMonth }).subscribe({
      next: (res) => {
        this.dashboardData = res.data;
        this.loading = false;
        this.buildCalendar();
        this.updateSelectedDateSummary();
        this.buildChart();
        this.buildDonutSegments();
      },
      error: () => {
        this.error = 'Failed to load dashboard data. Please try again.';
        this.loading = false;
      },
    });
  }

  // Month navigation
  changeMonth(delta: number): void {
    const [yearStr, monthStr] = this.currentYearMonth.split('-');
    const curDate = new Date(parseInt(yearStr, 10), parseInt(monthStr, 10) - 1 + delta, 1);
    const newY = curDate.getFullYear();
    const newM = curDate.getMonth() + 1;
    this.currentYearMonth = `${newY}-${newM < 10 ? '0' : ''}${newM}`;

    // Also update selected date to the 1st of that month if not matching
    this.selectedDate = `${this.currentYearMonth}-01`;
    this.form.patchValue({ transactionDate: this.selectedDate });
    this.loadDashboard();
  }

  goToCurrentMonth(): void {
    this.currentYearMonth = new Date().toISOString().slice(0, 7);
    this.selectedDate = new Date().toISOString().slice(0, 10);
    this.form.patchValue({ transactionDate: this.selectedDate });
    this.loadDashboard();
  }

  setChartMode(mode: 'day' | 'week' | 'month'): void {
    this.chartMode = mode;
    this.buildChart();
  }

  buildCalendar(): void {
    if (!this.dashboardData) return;

    const [yearStr, monthStr] = this.currentYearMonth.split('-');
    const year = parseInt(yearStr, 10);
    const monthIndex = parseInt(monthStr, 10) - 1;

    const firstDay = new Date(year, monthIndex, 1);
    const lastDay = new Date(year, monthIndex + 1, 0);
    const totalDays = lastDay.getDate();

    const dailyMap = new Map<string, DailySpending>();
    for (const d of this.dashboardData.dailySpending) {
      dailyMap.set(d.date, d);
    }

    let maxExpense = 0;
    for (const d of this.dashboardData.dailySpending) {
      if (d.totalExpense > maxExpense) maxExpense = d.totalExpense;
    }

    const todayStr = new Date().toISOString().slice(0, 10);
    const days: CalendarDay[] = [];

    // Monday-first offset (0 = Mon, 6 = Sun)
    const startDayOfWeek = (firstDay.getDay() + 6) % 7;
    for (let i = 0; i < startDayOfWeek; i++) {
      const prevDate = new Date(year, monthIndex, 0 - (startDayOfWeek - 1 - i));
      days.push({
        dateString: prevDate.toISOString().slice(0, 10),
        dayNumber: prevDate.getDate(),
        isCurrentMonth: false,
        isToday: false,
        totalExpense: 0,
        totalIncome: 0,
        transactionCount: 0,
        intensity: 'none',
      });
    }

    for (let day = 1; day <= totalDays; day++) {
      const padDay = day < 10 ? `0${day}` : `${day}`;
      const dateStr = `${yearStr}-${monthStr}-${padDay}`;
      const spend = dailyMap.get(dateStr);
      const totalExpense = spend ? spend.totalExpense : 0;
      const totalIncome = spend ? spend.totalIncome : 0;
      const count = spend ? spend.transactionCount : 0;

      let intensity: 'none' | 'low' | 'moderate' | 'high' = 'none';
      if (totalExpense > 0) {
        if (maxExpense > 0 && totalExpense >= maxExpense * 0.6) {
          intensity = 'high';
        } else if (maxExpense > 0 && totalExpense >= maxExpense * 0.25) {
          intensity = 'moderate';
        } else {
          intensity = 'low';
        }
      }

      days.push({
        dateString: dateStr,
        dayNumber: day,
        isCurrentMonth: true,
        isToday: dateStr === todayStr,
        totalExpense,
        totalIncome,
        transactionCount: count,
        intensity,
      });
    }

    this.calendarDays = days;
  }

  selectDate(dateString: string): void {
    this.selectedDate = dateString;
    this.updateSelectedDateSummary();
  }

  updateSelectedDateSummary(): void {
    if (!this.dashboardData) return;

    const dayTx = this.dashboardData.periodTransactions.filter(
      (t) => t.transactionDate === this.selectedDate
    );

    let expense = 0;
    let income = 0;
    const catMap = new Map<TransactionCategory, number>();

    for (const t of dayTx) {
      if (t.transactionType === 'EXPENSE') {
        expense += t.amount;
        catMap.set(t.category, (catMap.get(t.category) || 0) + t.amount);
      } else {
        income += t.amount;
      }
    }

    const catBreakdown = Array.from(catMap.entries())
      .map(([category, amount]) => ({
        category,
        amount,
        color: this.getCategoryColor(category),
      }))
      .sort((a, b) => b.amount - a.amount);

    this.selectedDateSummary = {
      date: this.selectedDate,
      totalExpense: expense,
      totalIncome: income,
      transactionCount: dayTx.length,
      categoryBreakdown: catBreakdown,
      transactions: dayTx,
    };
  }

  buildChart(): void {
    if (!this.dashboardData) return;

    let bars: ChartBar[] = [];

    if (this.chartMode === 'day') {
      const days = this.calendarDays.filter((d) => d.isCurrentMonth);
      let maxVal = 1;
      for (const d of days) {
        if (d.totalExpense > maxVal) maxVal = d.totalExpense;
      }
      bars = days.map((d) => ({
        label: `${d.dayNumber}`,
        dateKey: d.dateString,
        expense: d.totalExpense,
        income: d.totalIncome,
        heightPct: d.totalExpense > 0 ? Math.max(10, Math.round((d.totalExpense / maxVal) * 100)) : 4,
      }));
    } else if (this.chartMode === 'week') {
      const weeks: { label: string; expense: number; income: number }[] = [
        { label: 'W1', expense: 0, income: 0 },
        { label: 'W2', expense: 0, income: 0 },
        { label: 'W3', expense: 0, income: 0 },
        { label: 'W4', expense: 0, income: 0 },
        { label: 'W5', expense: 0, income: 0 },
      ];

      for (const t of this.dashboardData.periodTransactions) {
        const day = parseInt(t.transactionDate.slice(8, 10), 10);
        const wIdx = Math.min(Math.floor((day - 1) / 7), 4);
        if (t.transactionType === 'EXPENSE') {
          weeks[wIdx].expense += t.amount;
        } else {
          weeks[wIdx].income += t.amount;
        }
      }

      let maxVal = 1;
      for (const w of weeks) {
        if (w.expense > maxVal) maxVal = w.expense;
      }

      bars = weeks.map((w) => ({
        ...w,
        heightPct: w.expense > 0 ? Math.max(10, Math.round((w.expense / maxVal) * 100)) : 4,
      }));
    } else if (this.chartMode === 'month') {
      const s = this.dashboardData.summary;
      const prevExpense = s.previousPeriodExpenses;
      const curExpense = s.totalExpenses;
      const maxVal = Math.max(prevExpense, curExpense, 1);

      bars = [
        {
          label: 'Last Month',
          expense: prevExpense,
          income: s.previousPeriodIncome,
          heightPct: prevExpense > 0 ? Math.max(10, Math.round((prevExpense / maxVal) * 100)) : 4,
        },
        {
          label: 'This Month',
          expense: curExpense,
          income: s.totalIncome,
          heightPct: curExpense > 0 ? Math.max(10, Math.round((curExpense / maxVal) * 100)) : 4,
        },
      ];
    }

    this.chartBars = bars;
  }

  buildDonutSegments(): void {
    if (!this.dashboardData || !this.dashboardData.categorySpending.length) {
      this.donutSegments = [];
      return;
    }

    const radius = 54;
    const circumference = 2 * Math.PI * radius; // ~339.29
    let cumulativePercent = 0;

    this.donutSegments = this.dashboardData.categorySpending.map((cat) => {
      const segmentLen = (cat.percentage / 100) * circumference;
      const strokeDasharray = `${segmentLen} ${circumference}`;
      const strokeDashoffset = -((cumulativePercent / 100) * circumference);
      cumulativePercent += cat.percentage;

      return {
        category: cat.category,
        amount: cat.amount,
        percentage: cat.percentage,
        strokeDasharray,
        strokeDashoffset,
        color: this.getCategoryColor(cat.category),
      };
    });
  }

  selectCategory(category: TransactionCategory): void {
    this.selectedCategory = this.selectedCategory === category ? null : category;
  }

  getCategoryColor(category: TransactionCategory): string {
    return CATEGORY_COLORS[category] || '#7d8791';
  }

  openCreate(defaultDate?: string): void {
    this.editing = null;
    this.formError = '';
    this.form.reset({
      amount: null,
      merchant: '',
      category: 'OTHER',
      transactionType: 'EXPENSE',
      transactionDate: defaultDate || this.selectedDate || new Date().toISOString().slice(0, 10),
      notes: '',
    });
    this.showForm = true;
  }

  openEdit(transaction: Transaction): void {
    this.editing = transaction;
    this.formError = '';
    this.form.reset({
      amount: transaction.amount,
      merchant: transaction.merchant,
      category: transaction.category,
      transactionType: transaction.transactionType,
      transactionDate: transaction.transactionDate,
      notes: transaction.notes ?? '',
    });
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.editing = null;
    this.formError = '';
    this.form.reset();
  }

  saveTransaction(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.formError = '';
    const payload = this.form.getRawValue() as TransactionRequest;

    const op = this.editing
      ? this.transactionService.update(this.editing.id, payload)
      : this.transactionService.create(payload);

    op.subscribe({
      next: () => {
        this.submitting = false;
        this.closeForm();
      },
      error: () => {
        this.submitting = false;
        this.formError = 'Could not save transaction. Please check details and try again.';
      },
    });
  }

  removeTransaction(transaction: Transaction): void {
    if (confirm(`Delete ${transaction.merchant} (₹${transaction.amount})?`)) {
      this.transactionService.delete(transaction.id).subscribe({
        error: () => {
          this.error = 'Could not delete transaction.';
        },
      });
    }
  }

  categoryLabel(category: string): string {
    return category.charAt(0) + category.slice(1).toLowerCase();
  }
}
