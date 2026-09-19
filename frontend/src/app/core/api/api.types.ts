export interface ApiError {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
}

export interface ApiResponse<T, TMeta = unknown> {
  data: T | null;
  error: ApiError | null;
  meta: TMeta | null;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}

export type TransactionCategory = 'FOOD' | 'TRAVEL' | 'SHOPPING' | 'ENTERTAINMENT' | 'BILLS' | 'HEALTH' | 'EDUCATION' | 'PERSONAL' | 'OTHER';
export type TransactionType = 'EXPENSE' | 'INCOME';
export type PaymentMethod = 'UPI' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'CASH' | 'BANK_TRANSFER' | 'OTHER';
export type CategorizationConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
export type CategorizationSource = 'RULE' | 'USER_MEMORY' | 'ML' | 'FALLBACK' | 'MANUAL';

export interface CategorizationAlternative {
  category: TransactionCategory;
  confidence: number;
}

export interface Transaction {
  id: string;
  amount: number;
  merchant: string;
  description?: string | null;
  category: TransactionCategory;
  transactionType: TransactionType;
  paymentMethod?: PaymentMethod | null;
  transactionDate: string;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  confidence?: number | null;
  confidenceLevel?: CategorizationConfidenceLevel | null;
  categorizationSource?: CategorizationSource | null;
  requiresUserConfirmation?: boolean;
  predictedCategory?: TransactionCategory | null;
  alternatives?: CategorizationAlternative[];
}

export interface TransactionRequest {
  amount: number;
  merchant: string;
  description?: string | null;
  category?: TransactionCategory | null;
  transactionType: TransactionType;
  paymentMethod?: PaymentMethod | null;
  transactionDate: string;
  notes?: string | null;
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
}

export interface DashboardSummary {
  totalIncome: number;
  totalExpenses: number;
  netSavings: number;
  savingsRate: number;
  previousPeriodIncome: number;
  previousPeriodExpenses: number;
  incomeChangePercentage: number | null;
  expenseChangePercentage: number | null;
}

export interface CategorySpending {
  category: TransactionCategory;
  amount: number;
  percentage: number;
  transactionCount: number;
}

export interface DailySpending {
  date: string;
  totalExpense: number;
  totalIncome: number;
  transactionCount: number;
}

export interface DashboardData {
  summary: DashboardSummary;
  categorySpending: CategorySpending[];
  dailySpending: DailySpending[];
  recentTransactions: Transaction[];
  periodTransactions: Transaction[];
  fromDate: string;
  toDate: string;
}

export type BudgetPeriod = 'MONTHLY' | 'WEEKLY' | 'YEARLY' | 'CUSTOM';
export type BudgetStatus = 'ON_TRACK' | 'WARNING' | 'EXCEEDED';

export interface BudgetRequest {
  category: TransactionCategory;
  limitAmount: number;
  period: BudgetPeriod;
  startDate: string;
  endDate: string;
}

export interface Budget {
  id: number;
  userId: number;
  category: TransactionCategory;
  limitAmount: number;
  period: BudgetPeriod;
  startDate: string;
  endDate: string;
  spentAmount: number;
  remainingAmount: number;
  percentageUsed: number;
  status: BudgetStatus;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetSummary {
  totalBudget: number;
  totalSpent: number;
  totalRemaining: number;
  percentageUsed: number;
  onTrackCount: number;
  warningCount: number;
  exceededCount: number;
  totalBudgets: number;
  budgets: Budget[];
}

export interface GoalRequest {
  name: string;
  targetAmount: number;
  currentAmount?: number;
  targetDate: string;
}

export interface GoalContributionRequest {
  amount: number;
}

export interface Goal {
  id: number;
  userId: number;
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  progressPercentage: number;
  remainingAmount: number;
  isCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export type TrendDirection = 'INCREASED' | 'DECREASED' | 'UNCHANGED';
export type AggregationPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type InsightType =
  | 'CATEGORY_INCREASE'
  | 'CATEGORY_DECREASE'
  | 'TOP_SPENDING_CATEGORY'
  | 'SAVINGS_IMPROVED'
  | 'SAVINGS_DETERIORATED'
  | 'LARGE_TRANSACTION'
  | 'BUDGET_WARNING'
  | 'BUDGET_EXCEEDED';
export type InsightSeverity = 'INFO' | 'POSITIVE' | 'WARNING' | 'CRITICAL';
export type AnomalyType = 'LARGE_TRANSACTION' | 'CATEGORY_SPIKE' | 'PERIOD_SPIKE';

export interface SpendingSummary {
  totalIncome: number;
  totalExpenses: number;
  netSavings: number;
  savingsRate: number;
  transactionCount: number;
  averageExpense: number;
  largestExpense: number;
  fromDate: string;
  toDate: string;
}

export interface TrendMetric {
  currentAmount: number;
  previousAmount: number;
  changeAmount: number;
  percentageChange: number;
  direction: TrendDirection;
}

export interface PeriodComparison {
  currentFrom: string;
  currentTo: string;
  previousFrom: string;
  previousTo: string;
  income: TrendMetric;
  expenses: TrendMetric;
  netSavings: TrendMetric;
  currentSavingsRate: number;
  previousSavingsRate: number;
  savingsRateChange: number;
}

export interface CategoryAnalysis {
  category: TransactionCategory;
  amount: number;
  percentage: number;
  previousAmount: number;
  changeAmount: number;
  percentageChange: number;
  direction: TrendDirection;
  transactionCount: number;
}

export interface CategoryBreakdown {
  totalExpenses: number;
  categories: CategoryAnalysis[];
  topCategory?: TransactionCategory | null;
}

export interface SpendingTrendPoint {
  label: string;
  date: string;
  totalExpense: number;
  totalIncome: number;
  transactionCount: number;
}

export interface SpendingTrend {
  period: AggregationPeriod;
  points: SpendingTrendPoint[];
  fromDate: string;
  toDate: string;
}

export interface FinancialInsight {
  id: string;
  type: InsightType;
  severity: InsightSeverity;
  title: string;
  description: string;
  category?: TransactionCategory | null;
  amount?: number | null;
  percentageChange?: number | null;
  recommendation?: string | null;
}

export interface SpendingAnomaly {
  id: string;
  type: AnomalyType;
  title: string;
  description: string;
  reason: string;
  amount: number;
  baselineAmount: number;
  percentageOrMultiplier: number;
  category?: TransactionCategory | null;
  date?: string | null;
  transactionId?: string | null;
}

export interface InsightsOverview {
  summary: SpendingSummary;
  comparison: PeriodComparison;
  categories: CategoryBreakdown;
  trends: SpendingTrend;
  insights: FinancialInsight[];
  anomalies: SpendingAnomaly[];
}

