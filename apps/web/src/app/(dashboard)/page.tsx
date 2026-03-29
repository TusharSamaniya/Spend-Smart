"use client";

import { useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useQuery } from "@tanstack/react-query";
import { format, startOfMonth, endOfDay, parseISO, isValid } from "date-fns";

import { api } from "@/lib/api";

type SpendByCategory = {
  category: string;
  amount: number;
};

type RecentExpense = {
  id: string;
  date: string;
  merchant: string;
  category: string;
  amount: number;
  status: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";
};

type SummaryResponse = {
  totalSpend: number;
  totalExpenses: number;
  activeBudgets: number;
  spendByCategory: SpendByCategory[];
  recentExpenses: RecentExpense[];
};

const formatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatCurrency(amount?: number) {
  if (typeof amount !== "number") return "₹0";
  return formatter.format(amount);
}

const statusStyles: Record<RecentExpense["status"], string> = {
  DRAFT: "bg-zinc-100 text-zinc-700",
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-rose-100 text-rose-800",
};

export default function OverviewPage() {
  const [startDate, setStartDate] = useState(() =>
    format(startOfMonth(new Date()), "yyyy-MM-dd")
  );
  const [endDate, setEndDate] = useState(() =>
    format(endOfDay(new Date()), "yyyy-MM-dd")
  );

  const dateRange = useMemo(
    () => ({ startDate, endDate }),
    [startDate, endDate]
  );

  const dateRangeInvalid = useMemo(() => {
    const start = new Date(startDate);
    const end = new Date(endDate);
    return start > end;
  }, [startDate, endDate]);

  const summaryQuery = useQuery<SummaryResponse>({
    queryKey: ["analytics-summary", dateRange],
    queryFn: async () => {
      const { data } = await api.get<SummaryResponse>("/v1/analytics/summary", {
        params: {
          startDate,
          endDate,
        },
      });
      return data;
    },
    refetchInterval: 60000,
    enabled: !dateRangeInvalid,
  });

  const pendingApprovalsQuery = useQuery<number>({
    queryKey: ["pending-approvals"],
    queryFn: async () => {
      const { data } = await api.get<{ count?: number; items?: unknown[] } | any>(
        "/v1/approvals/pending"
      );
      if (Array.isArray(data)) return data.length;
      if (typeof data?.count === "number") return data.count;
      if (Array.isArray(data?.items)) return data.items.length;
      return 0;
    },
    refetchInterval: 60000,
  });

  const isLoading = summaryQuery.isLoading;
  const hasError = summaryQuery.isError;

  const spendByCategory = summaryQuery.data?.spendByCategory ?? [];
  const recentExpenses = summaryQuery.data?.recentExpenses ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">Overview</h1>
          <p className="text-sm text-zinc-600">Current performance at a glance</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 shadow-sm">
            <label className="text-sm text-zinc-600" htmlFor="startDate">
              From
            </label>
            <input
              id="startDate"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="rounded-md border border-zinc-200 px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
            />
          </div>
          <div className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 shadow-sm">
            <label className="text-sm text-zinc-600" htmlFor="endDate">
              To
            </label>
            <input
              id="endDate"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="rounded-md border border-zinc-200 px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
            />
          </div>
        </div>
      </div>

      {dateRangeInvalid ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          End date must be after start date.
        </div>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          title="Total Spend"
          value={formatCurrency(summaryQuery.data?.totalSpend)}
          loading={isLoading}
        />
        <MetricCard
          title="Expenses"
          value={summaryQuery.data?.totalExpenses ?? 0}
          loading={isLoading}
        />
        <MetricCard
          title="Pending Approvals"
          value={pendingApprovalsQuery.data ?? 0}
          loading={pendingApprovalsQuery.isLoading}
        />
        <MetricCard
          title="Active Budgets"
          value={summaryQuery.data?.activeBudgets ?? 0}
          loading={isLoading}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm lg:col-span-3">
          <div className="flex items-center justify-between pb-4">
            <h2 className="text-lg font-semibold text-zinc-900">Spend by Category</h2>
            <p className="text-xs text-zinc-500">Updated every 60s</p>
          </div>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={spendByCategory} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis dataKey="category" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                <Tooltip
                  cursor={{ fill: "#f8fafc" }}
                  contentStyle={{ borderRadius: 12, borderColor: "#e5e7eb" }}
                  formatter={(value: number) => [formatCurrency(value), "Spend"]}
                />
                <Bar dataKey="amount" fill="#4f46e5" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm lg:col-span-2">
          <div className="flex items-center justify-between pb-4">
            <h2 className="text-lg font-semibold text-zinc-900">Recent Expenses</h2>
            <p className="text-xs text-zinc-500">Last {recentExpenses.length} items</p>
          </div>
          <div className="overflow-hidden rounded-xl border border-zinc-100">
            <table className="min-w-full divide-y divide-zinc-200 text-sm">
              <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
                <tr>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3">Merchant</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3 text-right">Amount</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 bg-white">
                {recentExpenses.map((expense) => (
                  <tr key={expense.id}>
                    <td className="px-4 py-3 text-zinc-700">
                      {(() => {
                        const parsed = parseISO(expense.date);
                        return isValid(parsed)
                          ? format(parsed, "MMM d, yyyy")
                          : expense.date;
                      })()}
                    </td>
                    <td className="px-4 py-3 text-zinc-800">{expense.merchant}</td>
                    <td className="px-4 py-3 text-zinc-600">{expense.category}</td>
                    <td className="px-4 py-3 text-right font-semibold text-zinc-900">
                      {formatCurrency(expense.amount)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ${statusStyles[expense.status]}`}
                      >
                        {expense.status.replace("_", " ")}
                      </span>
                    </td>
                  </tr>
                ))}
                {recentExpenses.length === 0 ? (
                  <tr>
                    <td
                      className="px-4 py-6 text-center text-sm text-zinc-500"
                      colSpan={5}
                    >
                      No expenses in this range.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {hasError ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Could not load analytics. Please try again.
        </div>
      ) : null}
    </div>
  );
}

type MetricCardProps = {
  title: string;
  value: string | number;
  loading?: boolean;
};

function MetricCard({ title, value, loading }: MetricCardProps) {
  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
      <p className="text-sm text-zinc-500">{title}</p>
      {loading ? (
        <div className="mt-3 h-6 w-24 animate-pulse rounded-full bg-zinc-200" />
      ) : (
        <p className="mt-2 text-2xl font-semibold text-zinc-900">{value}</p>
      )}
    </div>
  );
}
