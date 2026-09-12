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
}

export interface TransactionRequest {
  amount: number;
  merchant: string;
  description?: string | null;
  category: TransactionCategory;
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

