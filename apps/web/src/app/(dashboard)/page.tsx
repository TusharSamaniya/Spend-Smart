"use client";

import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";

function extractCount(data: any): number {
  if (!data) return 0;
  if (Array.isArray(data)) return data.length;
  if (typeof data.count === "number") return data.count;
  if (Array.isArray(data.items)) return data.items.length;
  if (Array.isArray(data.data)) return data.data.length;
  return 0;
}

export default function OverviewPage() {
  const { user } = useAuth();

  const expensesCountQuery = useQuery<number>({
    queryKey: ["expenses"],
    queryFn: async () => {
      const { data } = await api.get<any>("/v1/expenses");
      return extractCount(data);
    },
    refetchInterval: 60000,
  });

  const pendingApprovalsCountQuery = useQuery<number>({
    queryKey: ["pending-approvals"],
    queryFn: async () => {
      const { data } = await api.get<any>("/v1/approvals/pending");
      return extractCount(data);
    },
    refetchInterval: 60000,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-zinc-900">Overview</h1>
        <p className="text-sm text-zinc-600">
          Welcome{user?.email ? `, ${user.email}` : ""}
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          title="Total Expenses"
          value={expensesCountQuery.data ?? 0}
          loading={expensesCountQuery.isLoading}
          error={expensesCountQuery.isError}
        />
        <MetricCard
          title="Pending Approvals"
          value={pendingApprovalsCountQuery.data ?? 0}
          loading={pendingApprovalsCountQuery.isLoading}
          error={pendingApprovalsCountQuery.isError}
        />
        <MetricCard title="This Month Spend" value="—" loading={false} />
        <MetricCard title="Active Budgets" value="—" loading={false} />
      </div>
    </div>
  );
}

type MetricCardProps = {
  title: string;
  value: string | number;
  loading?: boolean;
  error?: boolean;
};

function MetricCard({ title, value, loading, error }: MetricCardProps) {
  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
      <p className="text-sm text-zinc-500">{title}</p>
      {loading ? (
        <div className="mt-3 h-6 w-24 animate-pulse rounded-full bg-zinc-200" />
      ) : error ? (
        <p className="mt-2 text-sm font-medium text-rose-700">
          Failed to load
        </p>
      ) : (
        <p className="mt-2 text-2xl font-semibold text-zinc-900">{value}</p>
      )}
    </div>
  );
}
