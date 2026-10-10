import { CurrencyPipe, DecimalPipe, NgClass } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  AggregationPeriod,
  FinancialInsight,
  InsightsOverview,
  SpendingAnomaly,
  TransactionCategory,
} from '../../core/api/api.types';
import { InsightsService } from './insights.service';

export interface ChartBar {
  label: string;
  displayLabel: string;
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
  TRAVEL: '#4a77e5',
  SHOPPING: '#9c59b6',
  ENTERTAINMENT: '#8e44ad',
  BILLS: '#2e9f8a',
  HEALTH: '#27ae60',
  EDUCATION: '#2980b9',
  PERSONAL: '#d35400',
  OTHER: '#73808c',
};

@Component({
  selector: 'app-insights',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, DecimalPipe, NgClass],
  templateUrl: './insights.component.html',
  styleUrl: './insights.component.scss',
})
export class InsightsComponent implements OnInit {
  private readonly insightsService = inject(InsightsService);
  private readonly fb = inject(FormBuilder);

  loading = true;
  error = '';
  overview: InsightsOverview | null = null;

  // Date range filter
  rangePreset: 'this-month' | 'last-month' | 'last-3-months' | 'custom' = 'this-month';
  trendPeriod: AggregationPeriod = 'DAILY';

  readonly dateForm = this.fb.nonNullable.group({
    fromDate: [''],
    toDate: [''],
  });

  // Chart data
  chartBars: ChartBar[] = [];
  hoveredBar: ChartBar | null = null;

  // Donut chart segments & simplified category display
  donutSegments: DonutSegment[] = [];
  displayCategories: Array<{
    category: string;
    label: string;
    amount: number;
    percentage: number;
    color: string;
  }> = [];

  // Progressive disclosure toggle
  showDetailedBreakdown = false;

  get topInsight(): FinancialInsight | null {
    return this.overview?.insights && this.overview.insights.length > 0
      ? this.overview.insights[0]
      : null;
  }

  get topAnomaly(): SpendingAnomaly | null {
    return this.overview?.anomalies && this.overview.anomalies.length > 0
      ? this.overview.anomalies[0]
      : null;
  }

  get topCategoryShift() {
    if (!this.overview?.categories?.categories?.length) return null;
    // Find category with highest percentage increase or highest change
    const sorted = [...this.overview.categories.categories].sort(
      (a, b) => Math.abs(b.percentageChange) - Math.abs(a.percentageChange)
    );
    return sorted[0] || null;
  }

  toggleDetailedBreakdown(): void {
    this.showDetailedBreakdown = !this.showDetailedBreakdown;
  }

  ngOnInit(): void {
    this.applyPreset('this-month');
  }

  applyPreset(preset: 'this-month' | 'last-month' | 'last-3-months' | 'custom'): void {
    this.rangePreset = preset;
    const now = new Date();

    if (preset === 'this-month') {
      const year = now.getFullYear();
      const month = now.getMonth();
      const first = new Date(year, month, 1);
      const last = new Date(year, month + 1, 0);
      this.dateForm.patchValue({
        fromDate: this.formatDate(first),
        toDate: this.formatDate(last),
      });
      this.loadData();
    } else if (preset === 'last-month') {
      const year = now.getFullYear();
      const month = now.getMonth();
      const first = new Date(year, month - 1, 1);
      const last = new Date(year, month, 0);
      this.dateForm.patchValue({
        fromDate: this.formatDate(first),
        toDate: this.formatDate(last),
      });
      this.loadData();
    } else if (preset === 'last-3-months') {
      const year = now.getFullYear();
      const month = now.getMonth();
      const first = new Date(year, month - 2, 1);
      const last = new Date(year, month + 1, 0);
      this.dateForm.patchValue({
        fromDate: this.formatDate(first),
        toDate: this.formatDate(last),
      });
      this.loadData();
    }
  }

  loadData(): void {
    this.loading = true;
    this.error = '';

    const { fromDate, toDate } = this.dateForm.getRawValue();

    this.insightsService
      .getOverview({
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        trendPeriod: this.trendPeriod,
      })
      .subscribe({
        next: (data) => {
          this.overview = data;
          this.buildChart();
          this.buildDonut();
          this.loading = false;
        },
        error: () => {
          this.error = 'Failed to load financial intelligence and analytics data.';
          this.loading = false;
        },
      });
  }

  changeTrendPeriod(period: AggregationPeriod): void {
    this.trendPeriod = period;
    const { fromDate, toDate } = this.dateForm.getRawValue();

    this.insightsService
      .getTrends(period, {
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
      })
      .subscribe({
        next: (trendData) => {
          if (this.overview) {
            this.overview = { ...this.overview, trends: trendData };
          }
          this.buildChart();
        },
      });
  }

  buildChart(): void {
    if (!this.overview || !this.overview.trends || !this.overview.trends.points) {
      this.chartBars = [];
      return;
    }

    const pts = this.overview.trends.points;
    let maxVal = 1;
    for (const p of pts) {
      const m = Math.max(p.totalExpense, p.totalIncome);
      if (m > maxVal) maxVal = m;
    }

    const totalPoints = pts.length;
    // Adaptively show X-axis labels to prevent text crowding and horizontal overflow
    const step =
      totalPoints <= 8 ? 1 : totalPoints <= 16 ? 2 : totalPoints <= 35 ? 5 : Math.ceil(totalPoints / 7);

    this.chartBars = pts.map((p, idx) => {
      const isBoundary = idx === 0 || idx === totalPoints - 1;
      const isStep = idx % step === 0;
      const tooCloseToLast = totalPoints - 1 - idx < Math.floor(step / 2) && !isBoundary;
      const showLabel = (isBoundary || isStep) && !tooCloseToLast;

      return {
        label: p.label,
        displayLabel: showLabel ? p.label : '',
        dateKey: p.date,
        expense: p.totalExpense,
        income: p.totalIncome,
        heightPct: p.totalExpense > 0 ? Math.max(8, Math.round((p.totalExpense / maxVal) * 100)) : 4,
      };
    });
  }

  buildDonut(): void {
    if (!this.overview || !this.overview.categories || !this.overview.categories.categories) {
      this.donutSegments = [];
      this.displayCategories = [];
      return;
    }

    const radius = 54;
    const circumference = 2 * Math.PI * radius;
    let cumulativePercent = 0;

    const sorted = [...this.overview.categories.categories]
      .filter((c) => c.amount > 0)
      .sort((a, b) => b.amount - a.amount);

    let displayList: Array<{
      category: string;
      label: string;
      amount: number;
      percentage: number;
      color: string;
    }> = [];

    if (sorted.length <= 5) {
      displayList = sorted.map((c) => ({
        category: c.category,
        label: this.categoryLabel(c.category),
        amount: c.amount,
        percentage: c.percentage,
        color: this.getCategoryColor(c.category as TransactionCategory),
      }));
    } else {
      const top4 = sorted.slice(0, 4);
      const remaining = sorted.slice(4);
      const otherAmount = remaining.reduce((sum, c) => sum + c.amount, 0);
      const otherPct = Number(
        remaining.reduce((sum, c) => sum + c.percentage, 0).toFixed(1)
      );

      displayList = top4.map((c) => ({
        category: c.category,
        label: this.categoryLabel(c.category),
        amount: c.amount,
        percentage: c.percentage,
        color: this.getCategoryColor(c.category as TransactionCategory),
      }));

      if (otherAmount > 0) {
        displayList.push({
          category: 'OTHER',
          label: 'Other',
          amount: otherAmount,
          percentage: otherPct,
          color: '#73808c',
        });
      }
    }

    this.displayCategories = displayList;

    this.donutSegments = displayList.map((c) => {
      const segmentLen = (c.percentage / 100) * circumference;
      const strokeDasharray = `${segmentLen} ${circumference}`;
      const strokeDashoffset = -((cumulativePercent / 100) * circumference);
      cumulativePercent += c.percentage;

      return {
        category: c.category as TransactionCategory,
        amount: c.amount,
        percentage: c.percentage,
        strokeDasharray,
        strokeDashoffset,
        color: c.color,
      };
    });
  }

  getCategoryColor(category: TransactionCategory): string {
    return CATEGORY_COLORS[category] || '#73808c';
  }

  categoryLabel(cat: string): string {
    return cat.charAt(0) + cat.slice(1).toLowerCase();
  }

  getSeverityClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'badge-critical';
      case 'WARNING': return 'badge-warning';
      case 'POSITIVE': return 'badge-positive';
      default: return 'badge-info';
    }
  }

  getAnomalyTypeLabel(type: string): string {
    switch (type) {
      case 'LARGE_TRANSACTION': return 'Large Transaction';
      case 'CATEGORY_SPIKE': return 'Category Spending Spike';
      case 'PERIOD_SPIKE': return 'Period Spending Surge';
      default: return type;
    }
  }

  private formatDate(d: Date): string {
    return d.toISOString().slice(0, 10);
  }
}
