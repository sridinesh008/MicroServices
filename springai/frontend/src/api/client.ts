import type {
  AggregateView,
  EditExpenseRequest,
  ExpenseDraftView,
  ExpenseFilters,
  ExpenseView,
  Page,
  RuleView
} from "./types";

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : null;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (method !== "GET" && method !== "HEAD") {
    const token = readCookie("XSRF-TOKEN");
    if (token) {
      headers.set("X-XSRF-TOKEN", token);
    }
  }
  const res = await fetch(path, { ...init, headers, credentials: "include" });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.message ?? message;
    } catch {
      // no JSON body
    }
    throw new Error(message || `Request failed (${res.status})`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

function filterParams(filters: ExpenseFilters, extra: Record<string, string | number | undefined> = {}) {
  const params = new URLSearchParams();
  if (filters.dateFrom) params.set("dateFrom", filters.dateFrom);
  if (filters.dateTo) params.set("dateTo", filters.dateTo);
  if (filters.year != null) params.set("year", String(filters.year));
  if (filters.month != null) params.set("month", String(filters.month));
  if (filters.categories?.length) params.set("categories", filters.categories.join(","));
  if (filters.amountMin) params.set("amountMin", filters.amountMin);
  if (filters.amountMax) params.set("amountMax", filters.amountMax);
  if (filters.sourceMode) params.set("sourceMode", filters.sourceMode);
  if (filters.status) params.set("status", filters.status);
  if (filters.search) params.set("search", filters.search);
  for (const [key, value] of Object.entries(extra)) {
    if (value !== undefined) params.set(key, String(value));
  }
  return params;
}

export const api = {
  submitTextExpense(message: string): Promise<ExpenseDraftView> {
    return request("/api/expenses/text", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message })
    });
  },

  submitImageExpense(images: File[], caption: string): Promise<ExpenseDraftView> {
    const form = new FormData();
    images.forEach((file) => form.append("images", file));
    if (caption) {
      form.append("caption", caption);
    }
    return request("/api/expenses/image", { method: "POST", body: form });
  },

  drafts(): Promise<ExpenseView[]> {
    return request("/api/expenses/drafts");
  },

  confirmExpense(id: number, overrides?: EditExpenseRequest): Promise<ExpenseView> {
    return request(`/api/expenses/${id}/confirm`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: overrides ? JSON.stringify(overrides) : undefined
    });
  },

  editExpense(id: number, edits: EditExpenseRequest): Promise<ExpenseView> {
    return request(`/api/expenses/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(edits)
    });
  },

  discardExpense(id: number): Promise<void> {
    return request(`/api/expenses/${id}/discard`, { method: "POST" });
  },

  searchExpenses(filters: ExpenseFilters, page = 0, size = 50): Promise<Page<ExpenseView>> {
    const params = filterParams(filters, { page, size, sort: "expenseDate,desc" });
    return request(`/api/expenses?${params.toString()}`);
  },

  categories(): Promise<string[]> {
    return request("/api/expenses/categories");
  },

  aggregates(filters: ExpenseFilters): Promise<AggregateView> {
    const params = filterParams(filters);
    return request(`/api/expenses/aggregates?${params.toString()}`);
  },

  reapplyRules(): Promise<{ updatedCount: number }> {
    return request("/api/expenses/reapply-rules", { method: "POST" });
  },

  listRules(): Promise<RuleView[]> {
    return request("/api/rules");
  },

  createRule(instructionText: string): Promise<RuleView> {
    return request("/api/rules", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ instructionText })
    });
  },

  deactivateRule(id: number): Promise<void> {
    return request(`/api/rules/${id}`, { method: "DELETE" });
  }
};

/** Streams /api/chat's SSE text response, invoking onChunk as each piece arrives. */
export async function streamChat(message: string, onChunk: (text: string) => void, signal?: AbortSignal) {
  const token = readCookie("XSRF-TOKEN");
  const res = await fetch("/api/chat", {
    method: "POST",
    credentials: "include",
    signal,
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { "X-XSRF-TOKEN": token } : {})
    },
    body: JSON.stringify({ message })
  });
  if (!res.ok || !res.body) {
    let msg = res.statusText;
    try {
      const body = await res.json();
      msg = body.message ?? msg;
    } catch {
      // ignore
    }
    throw new Error(msg || `Chat request failed (${res.status})`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split("\n\n");
    buffer = events.pop() ?? "";
    for (const event of events) {
      const dataLines = event
        .split("\n")
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.slice(5).replace(/^ /, ""));
      if (dataLines.length) {
        onChunk(dataLines.join("\n"));
      }
    }
  }
}
