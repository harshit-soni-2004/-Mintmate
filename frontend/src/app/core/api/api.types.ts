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

export interface SendOtpRequest {
  email: string;
  purpose?: string;
}

export interface VerifyOtpRequest {
  email: string;
  code: string;
  purpose?: string;
}

export interface OtpResponse {
  success: boolean;
  message: string;
  resendCooldownSeconds: number;
}

export type TransactionCategory = 'FOOD' | 'TRAVEL' | 'SHOPPING' | 'ENTERTAINMENT' | 'BILLS' | 'HEALTH' | 'EDUCATION' | 'PERSONAL' | 'OTHER';
export type TransactionType = 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'INVESTMENT' | 'CASH_WITHDRAWAL';
export type TransferDirection = 'IN' | 'OUT';
export type TransferMatchStatus = 'NONE' | 'UNMATCHED' | 'AUTO_MATCHED' | 'MANUALLY_CONFIRMED';
export type PaymentMethod = 'UPI' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'CASH' | 'BANK_TRANSFER' | 'OTHER';
export type CategorizationConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
export type CategorizationSource = 'RULE' | 'USER_MEMORY' | 'ML' | 'FALLBACK' | 'MANUAL';

export interface FinancialAccount {
  id: string;
  name: string;
  institutionName: string;
  accountType: DocumentAccountType;
  accountNumberLast4?: string | null;
  currency: string;
  primary: boolean;
  active: boolean;
  createdAt: string;
}

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
  sourceType?: string | null;
  sourceDocumentId?: string | null;
  sourceLineNumber?: number | null;
  sourceReferenceId?: string | null;
  importBatchId?: string | null;
  rawDescription?: string | null;
  accountId?: string | null;
  accountName?: string | null;
  transferDirection?: TransferDirection | null;
  counterpartyAccountId?: string | null;
  counterpartyAccountName?: string | null;
  linkedTransactionId?: string | null;
  transferMatchStatus?: TransferMatchStatus;
}

export type DocumentFileType = 'CSV' | 'XLSX' | 'PDF';
export type DocumentAccountType = 'SAVINGS' | 'CURRENT' | 'CREDIT_CARD';
export type DocumentStatus = 'UPLOADED' | 'PARSING' | 'COMPLETED' | 'PARTIALLY_PROCESSED' | 'FAILED';
export type SourceTransactionClassification =
  | 'PURCHASE'
  | 'PAYMENT'
  | 'REFUND'
  | 'REVERSAL'
  | 'FEE'
  | 'INTEREST'
  | 'TRANSFER'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'UNKNOWN';
export type SettlementStatus = 'UNRESOLVED' | 'RESOLVED' | 'DISCARDED';

export interface FinancialDocument {
  id: string;
  originalFilename: string;
  fileType: DocumentFileType;
  fileSizeBytes: number;
  contentHash: string;
  institutionName?: string | null;
  accountType: DocumentAccountType;
  accountNumberLast4?: string | null;
  statementStartDate?: string | null;
  statementEndDate?: string | null;
  status: DocumentStatus;
  totalRowsDetected: number;
  importedCount: number;
  duplicateCount: number;
  errorMessage?: string | null;
  accountId?: string | null;
  accountName?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StatementImportResult {
  documentId: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  importedTransactions: number;
  exactDuplicates: number;
  probableDuplicates: number;
  unresolvedCandidates: number;
  settlementsResolved: number;
  skippedRows: number;
  warnings: string[];
}

export interface StatementUploadResponse {
  document: FinancialDocument;
  importResult: StatementImportResult;
}

export interface StatementSettlement {
  id: string;
  documentId: string;
  rowIndex: number;
  transactionDate: string;
  amount: number;
  rawMerchant: string;
  rawDescription?: string | null;
  classification: SourceTransactionClassification;
  accountType: DocumentAccountType;
  externalReference?: string | null;
  status: SettlementStatus;
  matchedSettlementId?: string | null;
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
  totalTransfers?: number;
  investmentContributions?: number;
  declaredIncome?: number | null;
  savingsTarget?: number | null;
  investTarget?: number | null;
  monthlySavingsTarget?: number | null;
  monthlyInvestmentTarget?: number | null;
  hasOnboardingBaseline?: boolean;
  debtOwedToUser?: number;
  debtOwedByUser?: number;
  netDebtPosition?: number;
  activeDebtRemindersCount?: number;
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
  totalTransfers?: number;
  investmentContributions?: number;
}

export interface TrendMetric {
  currentAmount: number;
  previousAmount: number;
  changeAmount: number;
  percentageChange?: number | null;
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
  hasPriorPeriodData?: boolean;
}

export interface CategoryAnalysis {
  category: TransactionCategory;
  amount: number;
  percentage: number;
  previousAmount: number;
  changeAmount: number;
  percentageChange?: number | null;
  direction: TrendDirection;
  transactionCount: number;
  requiresReviewCount?: number;
  requiresReviewAmount?: number;
}

export interface CategoryBreakdown {
  totalExpenses: number;
  categories: CategoryAnalysis[];
  topCategory?: TransactionCategory | null;
  totalTransactions?: number;
  autoClassifiedCount?: number;
  requiresReviewCount?: number;
  autoClassifiedPercentage?: number;
  requiresReviewPercentage?: number;
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

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ToolCallSummary {
  toolName: string;
  summary: string;
}

export interface AssistantPendingAction {
  actionId: string;
  actionType: string;
  summary: string;
  parameters?: Record<string, unknown>;
  expiresAt: string;
}

export interface AssistantChatRequest {
  message: string;
  conversationHistory?: ChatMessage[];
}

export interface AssistantChatResponse {
  answer: string;
  executedTools: ToolCallSummary[];
  requiresConfirmation: boolean;
  pendingAction?: AssistantPendingAction | null;
}

export interface PendingActionResult {
  actionId: string;
  toolName: string;
  status: string;
  summaryText: string;
  result: unknown;
  executedAt: string;
}

export interface ProactiveInsightDto {
  id: string;
  type: string;
  severity: InsightSeverity;
  title: string;
  summary: string;
  supportingFacts: Record<string, unknown>;
  suggestedAction?: string | null;
  createdAt: string;
}

export type UserPersona = 'STUDENT' | 'SALARIED' | 'BUSINESS_OWNER' | 'FREELANCER';

export type IncomeType =
  | 'SALARY'
  | 'BUSINESS_REVENUE'
  | 'FREELANCE'
  | 'PENSION'
  | 'STIPEND'
  | 'INVESTMENT_RETURN';

export type IncomeFrequency = 'MONTHLY' | 'BIWEEKLY' | 'IRREGULAR';

export interface IncomeSourceDto {
  sourceName: string;
  incomeType: IncomeType;
  frequency: IncomeFrequency;
  declaredAmount: number;
  expectedDayOfMonth?: number | null;
  employerOrPayerKeyword?: string | null;
  primary?: boolean;
  variable?: boolean;
}

export interface CategoryAllocationDto {
  category: TransactionCategory;
  estimatedAmount: number;
}

export interface OnboardingRequest {
  persona: UserPersona;
  monthlySavingsTarget?: number | null;
  monthlyInvestmentTarget?: number | null;
  incomeSources?: IncomeSourceDto[];
  categoryAllocations?: CategoryAllocationDto[];
}

export interface OnboardingResponse {
  profileId: string;
  persona: UserPersona;
  onboardingCompleted: boolean;
  monthlySavingsTarget?: number | null;
  monthlyInvestmentTarget?: number | null;
  incomeSources: IncomeSourceDto[];
  categoryAllocations: CategoryAllocationDto[];
}

export type DebtType = 'OWED_TO_ME' | 'I_OWE';
export type DebtStatus = 'ACTIVE' | 'SETTLED' | 'CANCELLED';

export interface DebtDto {
  id: string;
  counterpartyName: string;
  debtType: DebtType;
  amount: number;
  dueDate?: string | null;
  status: DebtStatus;
  settledAt?: string | null;
  linkedTransactionId?: string | null;
  linkedTransactionAmount?: number | null;
  linkedTransactionDate?: string | null;
  notes?: string | null;
  createdAt: string;
  overdue: boolean;
}

export interface DebtSummaryDto {
  totalReceivable: number;
  totalPayable: number;
  netDebtPosition: number;
  activeCount: number;
  overdueCount: number;
  upcomingDebts: DebtDto[];
  overdueDebts: DebtDto[];
}

export interface CreateDebtRequest {
  counterpartyName: string;
  debtType: DebtType;
  amount: number;
  dueDate?: string | null;
  notes?: string | null;
}

export interface SettleDebtRequest {
  linkedTransactionId?: string | null;
}
export interface GoogleLoginRequest {
  idToken: string;
}

export interface DebtCandidateTransactionDto {
  id: string;
  amount: number;
  description?: string | null;
  merchant?: string | null;
  transactionDate: string;
  transactionType: TransactionType;
  transferDirection?: TransferDirection | null;
  isTopMatch: boolean;
  isStrongMatch: boolean;
  exactAmount: boolean;
  matchReason?: string | null;
  score: number;
}

export interface RecordPaymentAndSettleRequest {
  amount: number;
  transactionDate: string;
  accountId?: string | null;
  notes?: string | null;
}

export interface FinancialAccountResponse {
  id: string;
  name: string;
  institutionName?: string | null;
  accountType: string;
  accountNumberLast4?: string | null;
  currency: string;
  active: boolean;
  primary: boolean;
}
}
