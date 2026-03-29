"use client";

import { useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useQuery } from "@tanstack/react-query";
import {
  endOfMonth,
  format,
  isValid,
  parseISO,
  startOfMonth,
  subMonths,
} from "date-fns";

import { api } from "@/lib/api";

const PIE_COLORS = [
  "#4f46e5",
  "#10b981",
  "#f59e0b",
  "#ef4444",
  "#6366f1",
  "#06b6d4",
  "#8b5cf6",
  "#f97316",
  "#84cc16",
  "#0ea5e9",
];

type TrendPoint = {
  month: string; // yyyy-MM
  currentYear: number;
  previousYear: number;
};

type CategorySlice = {
  categoryId: string;
  category: string;
  amount: number;
};

type MerchantBar = {
  merchant: string;
  amount: number;
};

type Anomaly = {
  id: string;
  category: string;
  current: number;
  baseline: number;
  delta: number;
};

type CategoryExpense = {
  id: string;
  date: string;
  merchant: string;
  amount: number;
  status: string;
};

type TrendsResponse = {
  points: TrendPoint[];
};

type CategoriesResponse = {
  slices: CategorySlice[];
};

type MerchantsResponse = {
  merchants: MerchantBar[];
};

type AnomaliesResponse = {
  anomalies: Anomaly[];
};

type CategoryExpensesResponse = {
  expenses: CategoryExpense[];
};

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatCurrency(amount?: number) {
  if (typeof amount !== "number") return "₹0";
  return currency.format(amount);
}

export default function AnalyticsPage() {
  const [startDate, setStartDate] = useState(() =>
    format(startOfMonth(subMonths(new Date(), 5)), "yyyy-MM-dd")
  );
  const [endDate, setEndDate] = useState(() =>
    format(endOfMonth(new Date()), "yyyy-MM-dd")
  );
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);

  const dateRangeInvalid = useMemo(() => new Date(startDate) > new Date(endDate), [startDate, endDate]);

  const trendsQuery = useQuery<TrendsResponse>({
    queryKey: ["analytics-trends", startDate, endDate],
    queryFn: async () => {
      const { data } = await api.get<TrendsResponse>("/v1/analytics/trends", {
        params: { startDate, endDate },
      });
      return data;
    },
    refetchInterval: 60000,
    enabled: !dateRangeInvalid,
  });

  const categoriesQuery = useQuery<CategoriesResponse>({
    queryKey: ["analytics-categories", startDate, endDate],
    queryFn: async () => {
      const { data } = await api.get<CategoriesResponse>("/v1/analytics/categories", {
        params: { startDate, endDate },
      });
      return data;
    },
    refetchInterval: 60000,
    enabled: !dateRangeInvalid,
  });

  const merchantsQuery = useQuery<MerchantsResponse>({
    queryKey: ["analytics-merchants", startDate, endDate],
    queryFn: async () => {
      const { data } = await api.get<MerchantsResponse>("/v1/analytics/top-merchants", {
        params: { startDate, endDate, limit: 10 },
      });
      return data;
    },
    refetchInterval: 60000,
    enabled: !dateRangeInvalid,
  });

  const anomaliesQuery = useQuery<AnomaliesResponse>({
    queryKey: ["analytics-anomalies", startDate, endDate],
    queryFn: async () => {
      const { data } = await api.get<AnomaliesResponse>("/v1/analytics/anomalies", {
        params: { startDate, endDate },
      });
      return data;
    },
    refetchInterval: 60000,
    enabled: !dateRangeInvalid,
  });

  const categoryExpensesQuery = useQuery<CategoryExpensesResponse>({
    queryKey: ["analytics-category-expenses", selectedCategoryId, startDate, endDate],
    queryFn: async () => {
      const { data } = await api.get<CategoryExpensesResponse>(
        `/v1/analytics/categories/${selectedCategoryId}/expenses`,
        { params: { startDate, endDate } }
      );
      return data;
    },
    enabled: Boolean(selectedCategoryId) && !dateRangeInvalid,
    refetchInterval: 60000,
  });

  const trendData = trendsQuery.data?.points ?? [];
  const categorySlices = categoriesQuery.data?.slices ?? [];
  const merchantBars = merchantsQuery.data?.merchants ?? [];
  const anomalies = anomaliesQuery.data?.anomalies ?? [];
  const drillExpenses = categoryExpensesQuery.data?.expenses ?? [];

  const selectedCategoryName = useMemo(() => {
    return categorySlices.find((s) => s.categoryId === selectedCategoryId)?.category ?? null;
  }, [categorySlices, selectedCategoryId]);

  return (
    <div className="space-y-8">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">Analytics</h1>
          <p className="text-sm text-zinc-600">Spend intelligence and trends</p>
        </div>
        <div className="flex items-center gap-2">
          <DateInput label="From" value={startDate} onChange={setStartDate} />
          <DateInput label="To" value={endDate} onChange={setEndDate} />
        </div>
      </div>

      {dateRangeInvalid ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          End date must be after start date.
        </div>
      ) : null}

      {anomalies.length > 0 ? (
        <div className="space-y-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm font-semibold text-amber-900">Anomalies detected</p>
          <div className="space-y-2 text-sm text-amber-900">
            {anomalies.map((item) => (
              <div key={item.id} className="rounded-md border border-amber-200 bg-amber-100 px-3 py-2">
                <span className="font-semibold">{item.category}</span> — current {formatCurrency(item.current)} vs baseline {formatCurrency(item.baseline)} ({formatCurrency(item.delta)} above normal)
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <section className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between pb-4">
          <h2 className="text-lg font-semibold text-zinc-900">Spend Trend</h2>
          <p className="text-xs text-zinc-500">YoY comparison</p>
        </div>
        <div className="h-96">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={trendData} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="month" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{ borderRadius: 12, borderColor: "#e5e7eb" }}
                formatter={(value: number) => formatCurrency(value)}
              />
              <Legend />
              <Line type="monotone" dataKey="currentYear" name="Current Year" stroke="#4f46e5" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="previousYear" name="Previous Year" stroke="#94a3b8" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-5">
        <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm lg:col-span-2">
          <div className="flex items-center justify-between pb-4">
            <h2 className="text-lg font-semibold text-zinc-900">Category Breakdown</h2>
            <p className="text-xs text-zinc-500">Click to drill down</p>
          </div>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={categorySlices}
                  dataKey="amount"
                  nameKey="category"
                  innerRadius={60}
                  outerRadius={110}
                  paddingAngle={2}
                  onClick={(data) => setSelectedCategoryId(data.categoryId)}
                >
                  {categorySlices.map((entry, index) => (
                    <Cell key={entry.categoryId} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value: number, _name, payload) => [formatCurrency(value), payload?.payload?.category]}
                  contentStyle={{ borderRadius: 12, borderColor: "#e5e7eb" }}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm lg:col-span-3">
          <div className="flex items-center justify-between pb-4">
            <h2 className="text-lg font-semibold text-zinc-900">Top Merchants</h2>
            <p className="text-xs text-zinc-500">Top 10 by spend</p>
          </div>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={merchantBars} layout="vertical" margin={{ top: 10, right: 20, left: 40, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis type="number" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                <YAxis type="category" dataKey="merchant" tick={{ fontSize: 12 }} width={120} tickLine={false} axisLine={false} />
                <Tooltip
                  formatter={(value: number) => formatCurrency(value)}
                  contentStyle={{ borderRadius: 12, borderColor: "#e5e7eb" }}
                />
                <Bar dataKey="amount" fill="#4f46e5" radius={[4, 4, 4, 4]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      {selectedCategoryId ? (
        <section className="space-y-3 rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-zinc-900">{selectedCategoryName ?? "Category"} expenses</h3>
              <p className="text-xs text-zinc-500">Click another slice to change category</p>
            </div>
            <button
              type="button"
              className="text-sm text-indigo-600"
              onClick={() => setSelectedCategoryId(null)}
            >
              Clear
            </button>
          </div>
          <div className="overflow-hidden rounded-xl border border-zinc-200">
            <table className="min-w-full divide-y divide-zinc-200 text-sm">
              <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
                <tr>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3">Merchant</th>
                  <th className="px-4 py-3 text-right">Amount</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 bg-white">
                {drillExpenses.map((expense) => (
                  <tr key={expense.id}>
                    <td className="px-4 py-3 text-zinc-700">{formatSafeDate(expense.date)}</td>
                    <td className="px-4 py-3 text-zinc-800">{expense.merchant}</td>
                    <td className="px-4 py-3 text-right font-semibold text-zinc-900">{formatCurrency(expense.amount)}</td>
                    <td className="px-4 py-3 text-zinc-600">{expense.status}</td>
                  </tr>
                ))}
                {categoryExpensesQuery.isLoading ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-6 text-center text-sm text-zinc-500">
                      Loading expenses...
                    </td>
                  </tr>
                ) : null}
                {!categoryExpensesQuery.isLoading && drillExpenses.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-6 text-center text-sm text-zinc-500">
                      No expenses for this category.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
    </div>
  );
}

type DateInputProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
};

function DateInput({ label, value, onChange }: DateInputProps) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 shadow-sm">
      <label className="text-sm text-zinc-600">{label}</label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-md border border-zinc-200 px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
      />
    </div>
  );
}

function formatSafeDate(value: string) {
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy");
}
