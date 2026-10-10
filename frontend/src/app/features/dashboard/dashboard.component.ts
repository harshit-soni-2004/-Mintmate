import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  BudgetStatus,
  BudgetSummary,
  CategorySpending,
  DailySpending,
  DashboardData,
  FinancialInsight,
  ProactiveInsightDto,
  SpendingAnomaly,
  Transaction,
  TransactionCategory,
  TransactionRequest,
  TransactionType,
} from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { BudgetService } from '../budgets/budget.service';
import { InsightsService } from '../insights/insights.service';
import { AssistantService } from '../assistant/assistant.service';
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
  intensity: 'none' | 'low' | 'moderate' | 'high' | 'very-high';
}

export interface ChartBar {
  label: string;
  dateKey?: string;
  expense: number;
  income: number;
  heightPct: number;
}

export interface CashflowPoint {
  date: string;
  dayNumber: number;
  income: number;
  expense: number;
  net: number;
  cumulativeNet: number;
  x: number;
  y: number;
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
  private readonly insightsService = inject(InsightsService);
  private readonly assistantService = inject(AssistantService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private mutationSub?: Subscription;

  readonly categories: TransactionCategory[] = [
    'FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER'
  ];

  loading = true;
  error = '';
  dashboardData: DashboardData | null = null;
  budgetSummary: BudgetSummary | null = null;
  topInsight: FinancialInsight | null = null;
  recentAnomaly: SpendingAnomaly | null = null;
  activeProactiveInsight: ProactiveInsightDto | null = null;

  // Selected period: "YYYY-MM"
  currentYearMonth = new Date().toISOString().slice(0, 7);

  // Available Balance & Cashflow dynamic chart state
  cashflowPoints: CashflowPoint[] = [];
  cashflowLinePath = '';
  cashflowAreaPath = '';
  cashflowZeroY: number | null = null;
  hoveredCashflowPoint: CashflowPoint | null = null;
  isCashflowEmpty = false;

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

  hoveredDate: string | null = null;
  hoveredDateSummary: {
    date: string;
    totalExpense: number;
    transactionCount: number;
    topCategories: { category: string; amount: number }[];
  } | null = null;

  onDayHover(day: CalendarDay): void {
    if (!day.isCurrentMonth) {
      this.hoveredDate = null;
      this.hoveredDateSummary = null;
      return;
    }
    this.hoveredDate = day.dateString;
    const dayTx = (this.dashboardData?.periodTransactions || []).filter(
      (t) => t.transactionDate === day.dateString && t.transactionType === 'EXPENSE'
    );
    const catMap = new Map<string, number>();
    for (const t of dayTx) {
      const label = this.categoryLabel(t.category);
      catMap.set(label, (catMap.get(label) || 0) + t.amount);
    }
    const topCats = Array.from(catMap.entries())
      .map(([category, amount]) => ({ category, amount }))
      .sort((a, b) => b.amount - a.amount)
      .slice(0, 3);

    this.hoveredDateSummary = {
      date: day.dateString,
      totalExpense: day.totalExpense,
      transactionCount: day.transactionCount,
      topCategories: topCats,
    };
  }

  onDayLeave(): void {
    this.hoveredDate = null;
    this.hoveredDateSummary = null;
  }

  // Selected Category filter for inspection
  selectedCategory: TransactionCategory | null = null;

  // Add / Edit Transaction modal
  showForm = false;
  editing: Transaction | null = null;
  submitting = false;
  formError = '';
  showDeepDive = false;

  readonly form = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    merchant: ['', [Validators.required, Validators.maxLength(120)]],
    category: ['OTHER' as TransactionCategory, Validators.required],
    transactionType: ['EXPENSE' as TransactionType, Validators.required],
    transactionDate: [this.selectedDate, Validators.required],
    notes: ['', Validators.maxLength(1000)],
  });

  toggleDeepDive(): void {
    this.showDeepDive = !this.showDeepDive;
  }

  getGreetingTime(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }

  get topBudget() {
    if (!this.budgetSummary || !this.budgetSummary.budgets.length) return null;
    return this.budgetSummary.budgets.slice().sort((a, b) => b.percentageUsed - a.percentageUsed)[0];
  }

  ngOnInit(): void {
    this.loadDashboard();
    this.loadBudgets();
    this.loadInsights();
    this.loadProactiveInsights();

    // Automatically refresh dashboard, budgets, and insights whenever any transaction is mutated
    const sub1 = this.transactionService.onTransactionMutated$.subscribe(() => {
      this.loadDashboard(false);
      this.loadBudgets();
      this.loadInsights();
      this.loadProactiveInsights();
    });

    const sub2 = this.budgetService.onBudgetMutated$.subscribe(() => {
      this.loadBudgets();
      this.loadInsights();
      this.loadProactiveInsights();
    });

    this.mutationSub = new Subscription();
    this.mutationSub.add(sub1);
    this.mutationSub.add(sub2);
  }

  loadProactiveInsights(): void {
    this.assistantService.getProactiveInsights().subscribe({
      next: (res) => {
        if (res.data && res.data.length > 0) {
          this.activeProactiveInsight = res.data[0];
        } else {
          this.activeProactiveInsight = null;
        }
      },
      error: () => {
        this.activeProactiveInsight = null;
      },
    });
  }

  dismissProactiveInsight(insightId: string): void {
    this.activeProactiveInsight = null;
    this.assistantService.dismissInsight(insightId).subscribe({
      error: () => {},
    });
  }

  askAiAboutInsight(insight: ProactiveInsightDto): void {
    const prompt = `Can you explain this proactive insight: "${insight.title}"? ${insight.summary}`;
    this.router.navigate(['/app/assistant'], { queryParams: { prompt } });
  }

  ngOnDestroy(): void {
    this.mutationSub?.unsubscribe();
  }

  loadInsights(): void {
    this.insightsService.getOverview({ month: this.currentYearMonth }).subscribe({
      next: (res) => {
        this.topInsight = res.insights && res.insights.length > 0 ? res.insights[0] : null;
        this.recentAnomaly = res.anomalies && res.anomalies.length > 0 ? res.anomalies[0] : null;
      },
      error: () => {
        // Silently ignore insights error on dashboard
      },
    });
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
        this.buildCashflowChart();
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
    this.loadInsights();
  }

  goToCurrentMonth(): void {
    this.currentYearMonth = new Date().toISOString().slice(0, 7);
    this.selectedDate = new Date().toISOString().slice(0, 10);
    this.form.patchValue({ transactionDate: this.selectedDate });
    this.loadDashboard();
    this.loadInsights();
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

      let intensity: 'none' | 'low' | 'moderate' | 'high' | 'very-high' = 'none';
      if (totalExpense > 0) {
        if (maxExpense > 0 && totalExpense >= maxExpense * 0.75) {
          intensity = 'very-high';
        } else if (maxExpense > 0 && totalExpense >= maxExpense * 0.45) {
          intensity = 'high';
        } else if (maxExpense > 0 && totalExpense >= maxExpense * 0.2) {
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
      } else if (t.transactionType === 'INCOME') {
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
        } else if (t.transactionType === 'INCOME') {
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

  buildCashflowChart(): void {
    if (!this.dashboardData) {
      this.cashflowPoints = [];
      this.cashflowLinePath = '';
      this.cashflowAreaPath = '';
      this.cashflowZeroY = null;
      this.isCashflowEmpty = true;
      return;
    }

    const [yearStr, monthStr] = this.currentYearMonth.split('-');
    const year = parseInt(yearStr, 10);
    const monthIndex = parseInt(monthStr, 10) - 1;

    // Accurate days in month (handles 28, 29, 30, 31 days)
    const totalDays = new Date(year, monthIndex + 1, 0).getDate();

    // Map daily records from API response
    const dailyMap = new Map<string, { expense: number; income: number }>();
    let hasActivity = false;

    if (this.dashboardData.dailySpending && this.dashboardData.dailySpending.length > 0) {
      for (const d of this.dashboardData.dailySpending) {
        dailyMap.set(d.date, { expense: d.totalExpense, income: d.totalIncome });
        if (d.totalExpense > 0 || d.totalIncome > 0) {
          hasActivity = true;
        }
      }
    }

    const summary = this.dashboardData.summary;
    if (!hasActivity && summary.totalIncome === 0 && summary.totalExpenses === 0) {
      this.isCashflowEmpty = true;
      this.cashflowPoints = [];
      this.cashflowLinePath = '';
      this.cashflowAreaPath = '';
      this.cashflowZeroY = null;
      this.hoveredCashflowPoint = null;
      return;
    }

    this.isCashflowEmpty = false;

    // Generate accurate daily points for each day of the month
    const points: CashflowPoint[] = [];
    let runningNet = 0;

    for (let day = 1; day <= totalDays; day++) {
      const padDay = day < 10 ? `0${day}` : `${day}`;
      const dateStr = `${yearStr}-${monthStr}-${padDay}`;
      const dayData = dailyMap.get(dateStr);
      const expense = dayData ? dayData.expense : 0;
      const income = dayData ? dayData.income : 0;
      const net = income - expense;
      runningNet += net;

      points.push({
        date: dateStr,
        dayNumber: day,
        income,
        expense,
        net,
        cumulativeNet: runningNet,
        x: 0,
        y: 0,
      });
    }

    // SVG coordinates setup: 500w x 64h
    const svgWidth = 500;
    const svgHeight = 64;
    const padTop = 8;
    const padBottom = 12;
    const padLeft = 8;
    const padRight = 8;
    const plotWidth = svgWidth - padLeft - padRight;
    const plotHeight = svgHeight - padTop - padBottom;

    // Determine range including 0 baseline anchor
    let minNet = Math.min(0, ...points.map((p) => p.cumulativeNet));
    let maxNet = Math.max(0, ...points.map((p) => p.cumulativeNet));

    if (maxNet === minNet) {
      maxNet += 1;
    }
    const span = maxNet - minNet;

    // Calculate (x, y) for each day
    for (let i = 0; i < points.length; i++) {
      const p = points[i];
      const x = padLeft + (i / (points.length - 1)) * plotWidth;
      const y = padTop + plotHeight - ((p.cumulativeNet - minNet) / span) * plotHeight;
      p.x = Math.round(x * 10) / 10;
      p.y = Math.round(y * 10) / 10;
    }

    // Zero baseline Y
    const zeroY = padTop + plotHeight - ((0 - minNet) / span) * plotHeight;
    this.cashflowZeroY = Math.round(zeroY * 10) / 10;

    // Build smooth Bezier path
    let linePath = `M ${points[0].x} ${points[0].y}`;
    for (let i = 1; i < points.length; i++) {
      const prev = points[i - 1];
      const curr = points[i];
      const midX = Math.round(((prev.x + curr.x) / 2) * 10) / 10;
      linePath += ` C ${midX} ${prev.y}, ${midX} ${curr.y}, ${curr.x} ${curr.y}`;
    }

    const firstPt = points[0];
    const lastPt = points[points.length - 1];
    const areaPath = `${linePath} L ${lastPt.x} ${svgHeight} L ${firstPt.x} ${svgHeight} Z`;

    this.cashflowPoints = points;
    this.cashflowLinePath = linePath;
    this.cashflowAreaPath = areaPath;
  }

  onCashflowMove(event: MouseEvent): void {
    if (!this.cashflowPoints.length) return;
    const target = event.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    if (rect.width <= 0) return;
    const mouseX = event.clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, mouseX / rect.width));
    const targetX = ratio * 500;

    this.findClosestCashflowPoint(targetX);
  }

  onCashflowTouch(event: TouchEvent): void {
    if (!this.cashflowPoints.length || !event.touches.length) return;
    const target = event.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    if (rect.width <= 0) return;
    const touchX = event.touches[0].clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, touchX / rect.width));
    const targetX = ratio * 500;

    this.findClosestCashflowPoint(targetX);
  }

  onCashflowLeave(): void {
    this.hoveredCashflowPoint = null;
  }

  private findClosestCashflowPoint(targetX: number): void {
    let closest = this.cashflowPoints[0];
    let minDist = Math.abs(closest.x - targetX);

    for (let i = 1; i < this.cashflowPoints.length; i++) {
      const dist = Math.abs(this.cashflowPoints[i].x - targetX);
      if (dist < minDist) {
        minDist = dist;
        closest = this.cashflowPoints[i];
      }
    }
    this.hoveredCashflowPoint = closest;
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
