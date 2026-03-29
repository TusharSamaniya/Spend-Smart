"use client";

import { useEffect, useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { format, isValid, parseISO } from "date-fns";

import { api } from "@/lib/api";

type Budget = {
  id: string;
  name: string;
  amount: number;
  currentSpend: number;
  categoryId?: string | null;
  category?: string | null;
  team?: string | null;
  scope?: "ORG" | "CATEGORY" | "TEAM" | string;
  period?: "MONTHLY" | "QUARTERLY" | "ANNUAL" | string;
  startDate?: string | null;
  endDate?: string | null;
  status?: string | null;
  alertThresholds?: number[];
};

type Category = {
  id: string;
  name: string;
};

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatCurrency(amount?: number | null) {
  if (typeof amount !== "number") return "₹0";
  return currency.format(amount);
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy");
}

function progressPercent(budget: Budget) {
  if (!budget.amount || budget.amount <= 0) return 0;
  return ((budget.currentSpend ?? 0) / budget.amount) * 100;
}

function progressColor(percent: number) {
  if (percent >= 90) return "bg-rose-500";
  if (percent >= 75) return "bg-amber-500";
  return "bg-emerald-500";
}

export default function BudgetsPage() {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingBudget, setEditingBudget] = useState<Budget | null>(null);

  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [categoryId, setCategoryId] = useState<string | "">("");
  const [period, setPeriod] = useState<"MONTHLY" | "QUARTERLY" | "ANNUAL">("MONTHLY");
  const [startDate, setStartDate] = useState(() => format(new Date(), "yyyy-MM-dd"));
  const [endDate, setEndDate] = useState("");
  const [alertThresholds, setAlertThresholds] = useState<Set<number>>(new Set([75, 90]));

  const budgetsQuery = useQuery<Budget[]>({
    queryKey: ["budgets"],
    queryFn: async () => {
      const { data } = await api.get<Budget[] | { budgets: Budget[] }>("/v1/budgets");
      if (Array.isArray(data)) return data;
      if (Array.isArray((data as { budgets?: Budget[] }).budgets)) return (data as { budgets: Budget[] }).budgets;
      return [];
    },
    refetchInterval: 60000,
  });

  const categoriesQuery = useQuery<Category[]>({
    queryKey: ["categories"],
    queryFn: async () => {
      const { data } = await api.get<Category[]>("/v1/categories");
      return data;
    },
  });

  const upsertBudget = useMutation({
    mutationFn: async () => {
      const payload = {
        name: name.trim(),
        amount: parseFloat(amount) || 0,
        categoryId: categoryId || null,
        period,
        startDate: startDate || null,
        endDate: endDate || null,
        alertThresholds: Array.from(alertThresholds).sort((a, b) => a - b),
      };

      if (editingBudget) {
        await api.put(`/v1/budgets/${editingBudget.id}`, payload);
      } else {
        await api.post("/v1/budgets", payload);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["budgets"] });
      resetForm();
      setDialogOpen(false);
    },
  });

  const deactivateBudget = useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/v1/budgets/${id}/deactivate`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["budgets"] });
    },
  });

  const budgets = budgetsQuery.data ?? [];
  const activeBudgets = useMemo(() => {
    return budgets.filter((b) => {
      const status = (b.status ?? "ACTIVE").toUpperCase();
      return status !== "INACTIVE" && status !== "DEACTIVATED";
    });
  }, [budgets]);

  const exceededBudgets = useMemo(
    () => activeBudgets.filter((b) => progressPercent(b) >= 100),
    [activeBudgets]
  );

  useEffect(() => {
    if (!dialogOpen) return;
    if (editingBudget) {
      setName(editingBudget.name ?? "");
      setAmount(String(editingBudget.amount ?? ""));
      setCategoryId(editingBudget.categoryId ?? "");
      setPeriod((editingBudget.period as typeof period) ?? "MONTHLY");
      setStartDate(editingBudget.startDate ? editingBudget.startDate.slice(0, 10) : "");
      setEndDate(editingBudget.endDate ? editingBudget.endDate.slice(0, 10) : "");
      setAlertThresholds(new Set(editingBudget.alertThresholds ?? [75, 90]));
    } else {
      resetForm();
    }
  }, [dialogOpen, editingBudget]);

  function resetForm() {
    setName("");
    setAmount("");
    setCategoryId("");
    setPeriod("MONTHLY");
    setStartDate(format(new Date(), "yyyy-MM-dd"));
    setEndDate("");
    setAlertThresholds(new Set([75, 90]));
    setEditingBudget(null);
  }

  function toggleThreshold(value: number) {
    setAlertThresholds((prev) => {
      const next = new Set(prev);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return next;
    });
  }

  function scopeLabel(budget: Budget) {
    if (budget.scope === "ORG") return "Org-wide";
    if (budget.scope === "TEAM") return budget.team ? `Team: ${budget.team}` : "Team";
    if (budget.category) return budget.category;
    return "General";
  }

  const anyExceeded = exceededBudgets.length > 0;

  return (
    <div className="space-y-6">
      {anyExceeded ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
          {exceededBudgets.length === 1
            ? `${exceededBudgets[0].name} has exceeded its limit.`
            : `${exceededBudgets.length} budgets have exceeded their limits.`}
        </div>
      ) : null}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">Budgets</h1>
          <p className="text-sm text-zinc-600">Track active budgets and guardrails</p>
        </div>
        <Dialog.Root open={dialogOpen} onOpenChange={setDialogOpen}>
          <Dialog.Trigger asChild>
            <button
              type="button"
              onClick={() => {
                setEditingBudget(null);
                setDialogOpen(true);
              }}
              className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500"
            >
              Create Budget
            </button>
          </Dialog.Trigger>
          <Dialog.Portal>
            <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
            <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[95vw] max-w-xl -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl focus:outline-none">
              <div className="flex items-start justify-between gap-3">
                <Dialog.Title className="text-lg font-semibold text-zinc-900">
                  {editingBudget ? "Edit Budget" : "Create Budget"}
                </Dialog.Title>
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-md p-1 text-zinc-500 transition hover:bg-zinc-100"
                    aria-label="Close"
                    onClick={resetForm}
                  >
                    ✕
                  </button>
                </Dialog.Close>
              </div>

              <form
                className="mt-4 space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  upsertBudget.mutate();
                }}
              >
                <div className="space-y-1">
                  <label className="text-sm font-medium text-zinc-800">Name</label>
                  <input
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                    placeholder="Q2 Marketing Budget"
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-zinc-800">Amount</label>
                    <input
                      required
                      type="number"
                      min="0"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      placeholder="100000"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-zinc-800">Category</label>
                    <select
                      value={categoryId}
                      onChange={(e) => setCategoryId(e.target.value)}
                      className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                    >
                      <option value="">Org-wide</option>
                      {(categoriesQuery.data ?? []).map((cat) => (
                        <option key={cat.id} value={cat.id}>
                          {cat.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-zinc-800">Period</label>
                    <select
                      value={period}
                      onChange={(e) => setPeriod(e.target.value as typeof period)}
                      className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                    >
                      <option value="MONTHLY">Monthly</option>
                      <option value="QUARTERLY">Quarterly</option>
                      <option value="ANNUAL">Annual</option>
                    </select>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">Start</label>
                      <input
                        type="date"
                        value={startDate}
                        onChange={(e) => setStartDate(e.target.value)}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">End</label>
                      <input
                        type="date"
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                  </div>
                </div>

                <div className="space-y-2">
                  <p className="text-sm font-medium text-zinc-800">Alerts</p>
                  <div className="grid grid-cols-3 gap-2 text-sm text-zinc-700">
                    {[75, 90, 100].map((value) => (
                      <label key={value} className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 shadow-sm">
                        <input
                          type="checkbox"
                          checked={alertThresholds.has(value)}
                          onChange={() => toggleThreshold(value)}
                          className="h-4 w-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-200"
                        />
                        {value}%
                      </label>
                    ))}
                  </div>
                </div>

                {upsertBudget.isError ? (
                  <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
                    Unable to save budget. Try again.
                  </div>
                ) : null}

                <div className="flex items-center justify-end gap-2 pt-2">
                  <Dialog.Close asChild>
                    <button
                      type="button"
                      className="rounded-lg border border-zinc-200 px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300"
                      onClick={resetForm}
                    >
                      Cancel
                    </button>
                  </Dialog.Close>
                  <button
                    type="submit"
                    disabled={upsertBudget.isLoading}
                    className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {upsertBudget.isLoading ? "Saving..." : editingBudget ? "Update" : "Create"}
                  </button>
                </div>
              </form>
            </Dialog.Content>
          </Dialog.Portal>
        </Dialog.Root>
      </div>

      {budgetsQuery.isLoading ? (
        <div className="rounded-xl border border-zinc-200 bg-white p-6 text-sm text-zinc-600">Loading budgets...</div>
      ) : activeBudgets.length === 0 ? (
        <div className="rounded-xl border border-dashed border-zinc-300 bg-white p-6 text-center text-sm text-zinc-600">
          No active budgets yet.
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {activeBudgets.map((budget) => {
            const percent = progressPercent(budget);
            const barWidth = Math.min(percent, 120);
            const remaining = Math.max((budget.amount ?? 0) - (budget.currentSpend ?? 0), 0);
            const exceeded = percent >= 100;

            return (
              <article key={budget.id} className="flex flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <p className="text-base font-semibold text-zinc-900">{budget.name}</p>
                    <p className="text-sm text-zinc-600">{scopeLabel(budget)}</p>
                    <p className="text-xs text-zinc-500">{budget.period ?? ""}</p>
                  </div>
                  <div className="flex flex-col items-end gap-2 text-right">
                    {exceeded ? (
                      <span className="inline-flex items-center rounded-full bg-rose-100 px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-rose-700">
                        Exceeded
                      </span>
                    ) : null}
                    <span className="text-sm text-zinc-500">{formatDate(budget.startDate)} – {formatDate(budget.endDate)}</span>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-zinc-600">Current spend</span>
                    <span className="font-semibold text-zinc-900">{formatCurrency(budget.currentSpend)}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-zinc-600">Budget</span>
                    <span className="font-semibold text-zinc-900">{formatCurrency(budget.amount)}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-zinc-600">Remaining</span>
                    <span className="font-semibold text-zinc-900">{formatCurrency(remaining)}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-zinc-500">
                    <span>Progress</span>
                    <span className="font-semibold text-zinc-700">{percent.toFixed(0)}%</span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-zinc-100">
                    <div
                      className={`h-full rounded-full ${progressColor(percent)}`}
                      style={{ width: `${barWidth}%` }}
                    />
                  </div>
                </div>

                <div className="mt-auto flex items-center justify-between gap-2 pt-1">
                  <div className="text-xs text-zinc-500">
                    Alerts: {[75, 90, 100]
                      .filter((value) => budget.alertThresholds?.includes(value))
                      .join(", ") || "None"}
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingBudget(budget);
                        setDialogOpen(true);
                      }}
                      className="rounded-lg border border-zinc-200 px-3 py-2 text-sm font-semibold text-zinc-700 shadow-sm transition hover:border-zinc-300"
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      onClick={() => deactivateBudget.mutate(budget.id)}
                      disabled={deactivateBudget.isLoading}
                      className="rounded-lg border border-rose-200 px-3 py-2 text-sm font-semibold text-rose-700 shadow-sm transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      Deactivate
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
