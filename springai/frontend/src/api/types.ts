export type SourceMode = "IMAGE" | "TEXT";
export type ExpenseStatus = "PENDING_CONFIRMATION" | "CONFIRMED" | "DISCARDED";

export interface ExpenseView {
  id: number;
  batchId: number | null;
  category: string;
  originalCategory: string;
  amount: string;
  description: string | null;
  sourceMode: SourceMode;
  status: ExpenseStatus;
  expenseDate: string;
  createdAt: string;
  updatedAt: string;
  recategorizedAt: string | null;
}

export interface ExpenseDraftView {
  batchId: number;
  items: ExpenseView[];
}

export interface CategoryTotal {
  category: string;
  total: string;
}

export interface MonthTotal {
  year: number;
  month: number;
  total: string;
}

export interface AggregateView {
  totalsByCategory: CategoryTotal[];
  totalsByMonth: MonthTotal[];
}

export interface RuleView {
  id: number;
  instructionText: string;
  active: boolean;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ExpenseFilters {
  dateFrom?: string;
  dateTo?: string;
  year?: number;
  month?: number;
  categories?: string[];
  amountMin?: string;
  amountMax?: string;
  sourceMode?: SourceMode;
  status?: ExpenseStatus;
  search?: string;
}

export interface EditExpenseRequest {
  category?: string | null;
  amount?: string | null;
  description?: string | null;
  expenseDate?: string | null;
}
