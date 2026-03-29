"use client";

import { useEffect, useMemo, useState, useTransition, type ReactNode } from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import * as Dialog from "@radix-ui/react-dialog";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { format, parseISO, startOfMonth, endOfDay, isValid } from "date-fns";

import { api } from "@/lib/api";

const PAGE_SIZE = 25;

const statusOptions = [
  { label: "All", value: "ALL" },
  { label: "Draft", value: "DRAFT" },
  { label: "Pending", value: "PENDING_APPROVAL" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
];

type Category = {
  id: string;
  name: string;
};

type Expense = {
  id: string;
  date: string;
  merchant: string;
  category: string;
  categoryId?: string;
  paymentMethod?: string;
  amount: number;
  status: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";
  gst?: {
    cgst?: number;
    sgst?: number;
    igst?: number;
    total?: number;
  };
  receiptUrl?: string | null;
  description?: string;
};

type ExpensesResponse = {
  items: Expense[];
  total: number;
  page: number;
  pageSize: number;
};

type ExpenseDetailResponse = Expense;

const statusStyles: Record<Expense["status"], string> = {
  DRAFT: "bg-zinc-100 text-zinc-700",
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-rose-100 text-rose-800",
};

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatDate(value: string) {
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy");
}

function formatCurrency(amount?: number) {
  if (typeof amount !== "number") return "₹0";
  return currency.format(amount);
}

function useQueryParamsSync() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [, startTransition] = useTransition();

  const params = useMemo(() => {
    const obj: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      obj[key] = value;
    });
    return obj;
  }, [searchParams]);

  const setParams = (next: Record<string, string | undefined>) => {
    const url = new URL(window.location.href);
    Object.entries(next).forEach(([key, value]) => {
      if (!value) {
        url.searchParams.delete(key);
      } else {
        url.searchParams.set(key, value);
      }
    });
    startTransition(() => {
      router.replace(`${pathname}?${url.searchParams.toString()}`);
    });
  };

  return { params, setParams };
}

export default function ExpensesPage() {
  const { params, setParams } = useQueryParamsSync();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [startDate, setStartDate] = useState(() => params.startDate ?? format(startOfMonth(new Date()), "yyyy-MM-dd"));
  const [endDate, setEndDate] = useState(() => params.endDate ?? format(endOfDay(new Date()), "yyyy-MM-dd"));
  const [status, setStatus] = useState(() => params.status ?? "ALL");
  const [categoryId, setCategoryId] = useState(() => params.categoryId ?? "");
  const [search, setSearch] = useState(() => params.search ?? "");
  const [page, setPage] = useState(() => Number(params.page ?? 1));

  // keep local state in sync when URL changes (e.g., back/forward)
  useEffect(() => {
    setStartDate(params.startDate ?? format(startOfMonth(new Date()), "yyyy-MM-dd"));
    setEndDate(params.endDate ?? format(endOfDay(new Date()), "yyyy-MM-dd"));
    setStatus(params.status ?? "ALL");
    setCategoryId(params.categoryId ?? "");
    setSearch(params.search ?? "");
    setPage(Number(params.page ?? 1));
  }, [params]);

  const dateRangeInvalid = useMemo(() => new Date(startDate) > new Date(endDate), [startDate, endDate]);

  const { data: categories } = useQuery<Category[]>({
    queryKey: ["categories"],
    queryFn: async () => {
      const { data } = await api.get<Category[]>("/v1/categories");
      return data;
    },
  });

  const expensesQuery = useQuery<ExpensesResponse>({
    queryKey: ["expenses", { startDate, endDate, status, categoryId, search, page }],
    queryFn: async () => {
      const { data } = await api.get<ExpensesResponse>("/v1/expenses", {
        params: {
          startDate,
          endDate,
          status: status === "ALL" ? undefined : status,
          categoryId: categoryId || undefined,
          search: search || undefined,
          page,
          pageSize: PAGE_SIZE,
        },
      });
      return data;
    },
    enabled: !dateRangeInvalid,
    keepPreviousData: true,
    refetchInterval: 60000,
  });

  const totalPages = useMemo(() => {
    if (!expensesQuery.data?.total) return 1;
    return Math.max(1, Math.ceil(expensesQuery.data.total / PAGE_SIZE));
  }, [expensesQuery.data?.total]);

  const selectedExpenseId = selectedId;

  const expenseDetailQuery = useQuery<ExpenseDetailResponse>({
    queryKey: ["expense-detail", selectedExpenseId],
    queryFn: async () => {
      const { data } = await api.get<ExpenseDetailResponse>(`/v1/expenses/${selectedExpenseId}`);
      return data;
    },
    enabled: Boolean(selectedExpenseId),
  });

  const onFilterChange = (next: Partial<{ startDate: string; endDate: string; status: string; categoryId: string; search: string; page: number }>) => {
    const merged = {
      startDate,
      endDate,
      status,
      categoryId,
      search,
      page,
      ...next,
    };
    setStartDate(merged.startDate);
    setEndDate(merged.endDate);
    setStatus(merged.status);
    setCategoryId(merged.categoryId);
    setSearch(merged.search);
    setPage(merged.page);
    setParams({
      startDate: merged.startDate,
      endDate: merged.endDate,
      status: merged.status,
      categoryId: merged.categoryId || undefined,
      search: merged.search || undefined,
      page: String(merged.page ?? 1),
    });
  };

  const onExport = async () => {
    try {
      const { data } = await api.get<Blob>("/v1/export/csv", {
        params: {
          startDate,
          endDate,
          status: status === "ALL" ? undefined : status,
          categoryId: categoryId || undefined,
          search: search || undefined,
        },
        responseType: "blob",
      });
      const blob = new Blob([data], { type: "text/csv" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "expenses.csv";
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      // TODO: surface toast
      console.error("Failed to export", error);
    }
  };

  const onDelete = async (id: string) => {
    try {
      await api.delete(`/v1/expenses/${id}`);
      queryClient.invalidateQueries({ queryKey: ["expenses"] });
    } catch (error) {
      console.error("Failed to delete", error);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">Expenses</h1>
          <p className="text-sm text-zinc-600">Track spend across all categories</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => onFilterChange({ page: 1 })}
            className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300"
          >
            Refresh
          </button>
          <button
            type="button"
            onClick={onExport}
            className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
          >
            Export CSV
          </button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <FilterDateField
          label="From"
          value={startDate}
          onChange={(val) => onFilterChange({ startDate: val, page: 1 })}
        />
        <FilterDateField
          label="To"
          value={endDate}
          onChange={(val) => onFilterChange({ endDate: val, page: 1 })}
        />
        <div className="flex flex-col gap-1">
          <label className="text-sm text-zinc-600">Category</label>
          <select
            value={categoryId}
            onChange={(e) => onFilterChange({ categoryId: e.target.value, page: 1 })}
            className="h-10 rounded-lg border border-zinc-200 bg-white px-3 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
          >
            <option value="">All</option>
            {categories?.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-sm text-zinc-600">Status</label>
          <select
            value={status}
            onChange={(e) => onFilterChange({ status: e.target.value, page: 1 })}
            className="h-10 rounded-lg border border-zinc-200 bg-white px-3 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
          >
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <input
            type="search"
            placeholder="Search merchant"
            value={search}
            onChange={(e) => onFilterChange({ search: e.target.value, page: 1 })}
            className="h-10 w-full min-w-[220px] rounded-lg border border-zinc-200 bg-white px-3 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
          />
        </div>
        <p className="text-xs text-zinc-500">Showing {PAGE_SIZE} per page</p>
      </div>

      {dateRangeInvalid ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          End date must be after start date.
        </div>
      ) : null}

      <div className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-zinc-200 text-sm">
            <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
              <tr>
                <th className="px-4 py-3">Date</th>
                <th className="px-4 py-3">Merchant</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Payment Method</th>
                <th className="px-4 py-3 text-right">Amount</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 bg-white">
              {expensesQuery.data?.items?.map((expense) => (
                <tr key={expense.id} className="hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-700">{formatDate(expense.date)}</td>
                  <td className="px-4 py-3 text-zinc-800">{expense.merchant}</td>
                  <td className="px-4 py-3 text-zinc-600">{expense.category}</td>
                  <td className="px-4 py-3 text-zinc-600">{expense.paymentMethod ?? "-"}</td>
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
                  <td className="px-4 py-3 text-right">
                    <DropdownMenu.Root>
                      <DropdownMenu.Trigger asChild>
                        <button className="rounded-md border border-zinc-200 bg-white px-2 py-1 text-xs font-medium text-zinc-700 shadow-sm hover:border-zinc-300">
                          Actions
                        </button>
                      </DropdownMenu.Trigger>
                      <DropdownMenu.Portal>
                        <DropdownMenu.Content
                          sideOffset={4}
                          className="min-w-[160px] rounded-lg border border-zinc-200 bg-white p-1 text-sm shadow-lg"
                        >
                          <DropdownMenu.Item
                            className="rounded px-2 py-1.5 text-zinc-800 outline-none hover:bg-zinc-100"
                            onSelect={() => setSelectedId(expense.id)}
                          >
                            View Details
                          </DropdownMenu.Item>
                          <DropdownMenu.Item
                            className="rounded px-2 py-1.5 text-rose-700 outline-none hover:bg-rose-50"
                            onSelect={() => onDelete(expense.id)}
                          >
                            Delete
                          </DropdownMenu.Item>
                        </DropdownMenu.Content>
                      </DropdownMenu.Portal>
                    </DropdownMenu.Root>
                  </td>
                </tr>
              ))}
              {expensesQuery.isLoading ? (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-sm text-zinc-500">
                    Loading expenses...
                  </td>
                </tr>
              ) : null}
              {!expensesQuery.isLoading && (expensesQuery.data?.items?.length ?? 0) === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-sm text-zinc-500">
                    No expenses found.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between border-t border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-600">
          <div>
            Page {page} of {totalPages}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => onFilterChange({ page: Math.max(1, page - 1) })}
              className="rounded-md border border-zinc-200 bg-white px-3 py-1.5 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => onFilterChange({ page: Math.min(totalPages, page + 1) })}
              className="rounded-md border border-zinc-200 bg-white px-3 py-1.5 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      <Dialog.Root open={Boolean(selectedExpenseId)} onOpenChange={(open) => !open && setSelectedId(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/30" />
          <Dialog.Content className="fixed inset-y-0 right-0 w-full max-w-xl overflow-y-auto bg-white p-6 shadow-xl">
            {expenseDetailQuery.isLoading ? (
              <p className="text-sm text-zinc-600">Loading details...</p>
            ) : expenseDetailQuery.data ? (
              <div className="space-y-4">
                <div className="flex items-start justify-between">
                  <div>
                    <Dialog.Title className="text-xl font-semibold text-zinc-900">
                      {expenseDetailQuery.data.merchant}
                    </Dialog.Title>
                    <p className="text-sm text-zinc-600">{formatDate(expenseDetailQuery.data.date)}</p>
                  </div>
                  <Dialog.Close className="rounded-md border border-zinc-200 px-2 py-1 text-sm text-zinc-600">
                    Close
                  </Dialog.Close>
                </div>
                <div className="grid grid-cols-2 gap-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-sm">
                  <InfoRow label="Category" value={expenseDetailQuery.data.category} />
                  <InfoRow label="Payment" value={expenseDetailQuery.data.paymentMethod ?? "-"} />
                  <InfoRow label="Amount" value={formatCurrency(expenseDetailQuery.data.amount)} />
                  <InfoRow
                    label="Status"
                    value={
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ${statusStyles[expenseDetailQuery.data.status]}`}>
                        {expenseDetailQuery.data.status.replace("_", " ")}
                      </span>
                    }
                  />
                </div>
                {expenseDetailQuery.data.gst ? (
                  <div className="rounded-lg border border-zinc-200 bg-white p-3 text-sm">
                    <p className="mb-2 text-sm font-semibold text-zinc-800">GST Breakdown</p>
                    <div className="grid grid-cols-2 gap-2">
                      <InfoRow label="CGST" value={formatCurrency(expenseDetailQuery.data.gst.cgst)} />
                      <InfoRow label="SGST" value={formatCurrency(expenseDetailQuery.data.gst.sgst)} />
                      <InfoRow label="IGST" value={formatCurrency(expenseDetailQuery.data.gst.igst)} />
                      <InfoRow label="Total GST" value={formatCurrency(expenseDetailQuery.data.gst.total)} />
                    </div>
                  </div>
                ) : null}
                {expenseDetailQuery.data.description ? (
                  <div className="rounded-lg border border-zinc-200 bg-white p-3 text-sm">
                    <p className="mb-2 text-sm font-semibold text-zinc-800">Description</p>
                    <p className="text-zinc-700">{expenseDetailQuery.data.description}</p>
                  </div>
                ) : null}
                {expenseDetailQuery.data.receiptUrl ? (
                  <div className="rounded-lg border border-zinc-200 bg-white p-3 text-sm">
                    <p className="mb-2 text-sm font-semibold text-zinc-800">Receipt</p>
                    <img
                      src={expenseDetailQuery.data.receiptUrl}
                      alt="Receipt"
                      className="max-h-96 w-full rounded-lg object-contain"
                    />
                  </div>
                ) : null}
              </div>
            ) : (
              <p className="text-sm text-rose-600">Could not load expense.</p>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}

type FilterDateFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
};

function FilterDateField({ label, value, onChange }: FilterDateFieldProps) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-sm text-zinc-600">{label}</label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-10 rounded-lg border border-zinc-200 bg-white px-3 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
      />
    </div>
  );
}

type InfoRowProps = {
  label: string;
  value: ReactNode;
};

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <div className="flex flex-col gap-1 text-sm">
      <p className="text-xs uppercase tracking-wide text-zinc-500">{label}</p>
      <div className="text-zinc-800">{value}</div>
    </div>
  );
}
