"use client";

import { useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { format, isValid, parseISO } from "date-fns";

import { api } from "@/lib/api";

type PendingApproval = {
  id: string;
  employeeName: string;
  expenseAmount: number;
  merchant: string;
  category: string;
  submittedAt: string;
  escalationAt?: string;
};

type ApprovalHistoryItem = {
  id: string;
  expense: string;
  submitter: string;
  approver: string;
  action: string;
  timestamp: string;
};

function formatDate(value: string) {
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy");
}

function formatDateTime(value: string) {
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy • h:mm a");
}

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatCurrency(amount?: number) {
  if (typeof amount !== "number") return "₹0";
  return currency.format(amount);
}

export default function ApprovalsPage() {
  const queryClient = useQueryClient();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [rejectComment, setRejectComment] = useState("");

  const pendingQuery = useQuery<PendingApproval[]>({
    queryKey: ["approvals", "pending"],
    queryFn: async () => {
      const { data } = await api.get<PendingApproval[]>("/v1/approvals/pending");
      return data;
    },
    refetchInterval: 60000,
  });

  const historyQuery = useQuery<ApprovalHistoryItem[]>({
    queryKey: ["approvals", "history"],
    queryFn: async () => {
      const { data } = await api.get<ApprovalHistoryItem[]>("/v1/approvals/history");
      return data;
    },
    refetchInterval: 60000,
  });

  const approveMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/v1/approvals/${id}/approve`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] });
      queryClient.invalidateQueries({ queryKey: ["approvals", "history"] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: async ({ id, comment }: { id: string; comment: string }) => {
      await api.post(`/v1/approvals/${id}/reject`, { comment });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] });
      queryClient.invalidateQueries({ queryKey: ["approvals", "history"] });
      setRejectId(null);
      setRejectComment("");
    },
  });

  const pending = pendingQuery.data ?? [];
  const history = historyQuery.data ?? [];

  const pendingCount = pending.length;

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const bulkApprove = async () => {
    if (selectedIds.size === 0) return;
    const ids = Array.from(selectedIds);
    try {
      await Promise.all(ids.map((id) => approveMutation.mutateAsync(id)));
      setSelectedIds(new Set());
    } catch (error) {
      console.error("Bulk approve failed", error);
    }
  };

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold text-zinc-900">Pending Your Approval</h1>
            <span className="inline-flex items-center rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700">
              {pendingCount}
            </span>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={selectedIds.size === 0 || approveMutation.isLoading}
              onClick={bulkApprove}
              className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Bulk Approve
            </button>
            <button
              type="button"
              onClick={() => queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] })}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300"
            >
              Refresh
            </button>
          </div>
        </div>

        {pendingQuery.isLoading ? (
          <div className="rounded-xl border border-zinc-200 bg-white p-6 text-sm text-zinc-600">
            Loading pending approvals...
          </div>
        ) : pendingCount === 0 ? (
          <div className="rounded-xl border border-zinc-200 bg-white p-6 text-sm text-zinc-600">
            No items pending your approval.
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {pending.map((item) => (
              <article key={item.id} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-zinc-900">{item.employeeName}</p>
                    <p className="text-sm text-zinc-600">{item.category} • {item.merchant}</p>
                    <p className="text-sm text-zinc-500">{formatDate(item.submittedAt)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-semibold text-zinc-900">{formatCurrency(item.expenseAmount)}</p>
                    {item.escalationAt ? (
                      <p className="text-xs text-amber-600">Escalates {formatDateTime(item.escalationAt)}</p>
                    ) : null}
                  </div>
                </div>

                <div className="mt-3 flex items-center justify-between">
                  <label className="flex items-center gap-2 text-sm text-zinc-600">
                    <input
                      type="checkbox"
                      checked={selectedIds.has(item.id)}
                      onChange={() => toggleSelect(item.id)}
                      className="h-4 w-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-200"
                    />
                    Select
                  </label>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => approveMutation.mutate(item.id)}
                      className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-500"
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      onClick={() => setRejectId(item.id)}
                      className="rounded-lg border border-rose-200 px-3 py-2 text-sm font-semibold text-rose-700 shadow-sm transition hover:bg-rose-50"
                    >
                      Reject
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-zinc-900">All Activity</h2>
          <p className="text-xs text-zinc-500">Auto-refreshes every 60s</p>
        </div>
        <div className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-zinc-200 text-sm">
              <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
                <tr>
                  <th className="px-4 py-3">Expense</th>
                  <th className="px-4 py-3">Submitter</th>
                  <th className="px-4 py-3">Approver</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 bg-white">
                {history.map((item) => (
                  <tr key={item.id}>
                    <td className="px-4 py-3 text-zinc-800">{item.expense}</td>
                    <td className="px-4 py-3 text-zinc-600">{item.submitter}</td>
                    <td className="px-4 py-3 text-zinc-600">{item.approver}</td>
                    <td className="px-4 py-3 text-zinc-700">{item.action}</td>
                    <td className="px-4 py-3 text-zinc-600">{formatDateTime(item.timestamp)}</td>
                  </tr>
                ))}
                {historyQuery.isLoading ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-sm text-zinc-500">
                      Loading history...
                    </td>
                  </tr>
                ) : null}
                {!historyQuery.isLoading && history.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-sm text-zinc-500">
                      No approval activity yet.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <Dialog.Root open={Boolean(rejectId)} onOpenChange={(open) => !open && setRejectId(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/30" />
          <Dialog.Content className="fixed inset-0 flex items-center justify-center px-4">
            <div className="w-full max-w-lg rounded-2xl border border-zinc-200 bg-white p-6 shadow-xl">
              <Dialog.Title className="text-lg font-semibold text-zinc-900">Reject Expense</Dialog.Title>
              <Dialog.Description className="mt-1 text-sm text-zinc-600">
                Please provide a reason for rejection.
              </Dialog.Description>
              <div className="mt-4 space-y-2">
                <label className="text-sm text-zinc-700" htmlFor="reject-comment">
                  Comment
                </label>
                <textarea
                  id="reject-comment"
                  rows={4}
                  value={rejectComment}
                  onChange={(e) => setRejectComment(e.target.value)}
                  className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm shadow-sm focus:border-rose-400 focus:outline-none focus:ring-2 focus:ring-rose-100"
                  placeholder="Add a short note"
                />
              </div>
              <div className="mt-6 flex justify-end gap-3">
                <Dialog.Close className="rounded-lg border border-zinc-200 px-4 py-2 text-sm font-medium text-zinc-700 hover:border-zinc-300">
                  Cancel
                </Dialog.Close>
                <button
                  type="button"
                  disabled={!rejectComment.trim() || rejectMutation.isLoading || !rejectId}
                  onClick={() =>
                    rejectId && rejectMutation.mutate({ id: rejectId, comment: rejectComment.trim() })
                  }
                  className="rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-rose-500 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Confirm Reject
                </button>
              </div>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
