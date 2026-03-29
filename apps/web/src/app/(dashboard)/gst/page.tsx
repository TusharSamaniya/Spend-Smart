"use client";

import { useCallback, useMemo, useRef, useState } from "react";
import * as Tabs from "@radix-ui/react-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { format, isValid, parseISO } from "date-fns";

import { api } from "@/lib/api";

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

function formatCurrency(amount?: number) {
  if (typeof amount !== "number") return "₹0";
  return currency.format(amount);
}

function formatDate(value: string) {
  const parsed = parseISO(value);
  if (!isValid(parsed)) return value;
  return format(parsed, "MMM d, yyyy");
}

type GstSummary = {
  cgst: number;
  sgst: number;
  igst: number;
  itcEligible: number;
  total?: number;
};

type ValidateResponse = {
  valid: boolean;
  message?: string;
};

type Invoice = {
  id: string;
  invoiceNumber: string;
  supplier: string;
  gstin: string;
  date: string;
  amount: number;
  gstAmount?: number;
};

type ReconcileResponse = {
  matched: Invoice[];
  missingInPortal: Invoice[];
  missingInSpendSmart: Invoice[];
};

export default function GstPage() {
  const queryClient = useQueryClient();
  const [gstin, setGstin] = useState("");
  const [reconcileResult, setReconcileResult] = useState<ReconcileResponse | null>(null);
  const [activeTab, setActiveTab] = useState("matched");
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const summaryQuery = useQuery<GstSummary>({
    queryKey: ["gst-summary"],
    queryFn: async () => {
      const { data } = await api.get<GstSummary>("/v1/gst/summary");
      return data;
    },
    refetchInterval: 60000,
  });

  const validateQuery = useQuery<ValidateResponse>({
    queryKey: ["gst-validate", gstin],
    queryFn: async () => {
      const { data } = await api.get<ValidateResponse>("/v1/gst/validate", {
        params: { gstin },
      });
      return data;
    },
    enabled: gstin.trim().length >= 10,
  });

  const reconcileMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      const { data } = await api.post<ReconcileResponse>("/v1/gst/reconcile", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      return data;
    },
    onSuccess: (data) => {
      setReconcileResult(data);
      setActiveTab("matched");
    },
  });

  const handleFiles = useCallback(
    (files?: FileList | null) => {
      if (!files || files.length === 0) return;
      const file = files[0];
      reconcileMutation.mutate(file);
    },
    [reconcileMutation]
  );

  const onDrop: React.DragEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    setIsDragging(false);
    handleFiles(e.dataTransfer.files);
  };

  const onExport = async (path: string, filename: string) => {
    try {
      const { data } = await api.get<Blob>(path, { responseType: "blob" });
      const blob = new Blob([data]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Export failed", error);
    }
  };

  const summary = summaryQuery.data;
  const validation = validateQuery.data;

  const tabs = useMemo(
    () => [
      { key: "matched", label: "Matched", rows: reconcileResult?.matched ?? [] },
      { key: "missingPortal", label: "Missing in Portal", rows: reconcileResult?.missingInPortal ?? [] },
      { key: "missingSpendSmart", label: "Missing in SpendSmart", rows: reconcileResult?.missingInSpendSmart ?? [] },
    ],
    [reconcileResult]
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">GST Reports</h1>
          <p className="text-sm text-zinc-600">Reconciliation and exports</p>
        </div>
        <div className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 shadow-sm">
          <label className="text-sm text-zinc-600" htmlFor="gstin">GSTIN</label>
          <input
            id="gstin"
            value={gstin}
            onChange={(e) => setGstin(e.target.value.trim())}
            placeholder="22AAAAA0000A1Z5"
            className="w-56 rounded-md border border-zinc-200 px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-200"
          />
          {validateQuery.isFetching ? (
            <span className="text-xs text-zinc-500">Checking…</span>
          ) : validation ? (
            <span className={`text-xs font-semibold ${validation.valid ? "text-emerald-600" : "text-rose-600"}`}>
              {validation.valid ? "Valid" : validation.message ?? "Invalid"}
            </span>
          ) : null}
        </div>
      </div>

      <section className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
        <h2 className="text-lg font-semibold text-zinc-900">GST Summary (This Month)</h2>
        <div className="mt-3 overflow-hidden rounded-xl border border-zinc-100">
          <table className="min-w-full text-sm">
            <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
              <tr>
                <th className="px-4 py-3">Metric</th>
                <th className="px-4 py-3">CGST</th>
                <th className="px-4 py-3">SGST</th>
                <th className="px-4 py-3">IGST</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 bg-white">
              <tr>
                <td className="px-4 py-3 font-semibold text-zinc-800">Total Paid</td>
                <td className="px-4 py-3 text-zinc-700">{formatCurrency(summary?.cgst)}</td>
                <td className="px-4 py-3 text-zinc-700">{formatCurrency(summary?.sgst)}</td>
                <td className="px-4 py-3 text-zinc-700">{formatCurrency(summary?.igst)}</td>
              </tr>
              <tr>
                <td className="px-4 py-3 font-semibold text-emerald-700">ITC Eligible</td>
                <td className="px-4 py-3 text-emerald-700" colSpan={3}>
                  {formatCurrency(summary?.itcEligible)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-4 rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-zinc-900">GSTR-2B Reconciliation</h2>
            <p className="text-sm text-zinc-600">Upload the JSON file from the GST portal</p>
          </div>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300"
          >
            Browse File
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json"
            className="hidden"
            onChange={(e) => handleFiles(e.target.files)}
          />
        </div>
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setIsDragging(true);
          }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={onDrop}
          className={`flex min-h-[160px] items-center justify-center rounded-xl border-2 border-dashed px-4 text-sm transition ${
            isDragging ? "border-indigo-400 bg-indigo-50" : "border-zinc-200 bg-zinc-50"
          }`}
        >
          {reconcileMutation.isLoading ? (
            <p className="text-zinc-600">Uploading and reconciling…</p>
          ) : (
            <p className="text-zinc-600">Drag and drop your GSTR-2B JSON here, or click Browse.</p>
          )}
        </div>

        {reconcileResult ? (
          <Tabs.Root value={activeTab} onValueChange={setActiveTab} className="space-y-3">
            <Tabs.List className="flex gap-2">
              {tabs.map((tab) => (
                <Tabs.Trigger
                  key={tab.key}
                  value={tab.key}
                  className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                    activeTab === tab.key
                      ? "bg-indigo-600 text-white"
                      : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200"
                  }`}
                >
                  {tab.label} ({tab.rows.length})
                </Tabs.Trigger>
              ))}
            </Tabs.List>
            {tabs.map((tab) => (
              <Tabs.Content key={tab.key} value={tab.key} className="rounded-xl border border-zinc-200">
                <InvoiceTable rows={tab.rows} />
              </Tabs.Content>
            ))}
          </Tabs.Root>
        ) : null}
      </section>

      <section className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-zinc-900">Exports</h2>
            <p className="text-sm text-zinc-600">Download reconciled data</p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => onExport("/v1/gst/export/xlsx", "gst-report.xlsx")}
              className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
            >
              Download XLSX Report
            </button>
            <button
              type="button"
              onClick={() => onExport("/v1/gst/export/tally", "gst-tally.xml")}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:border-zinc-300"
            >
              Download Tally XML
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

type InvoiceTableProps = {
  rows: Invoice[];
};

function InvoiceTable({ rows }: InvoiceTableProps) {
  return (
    <div className="overflow-hidden rounded-xl">
      <table className="min-w-full divide-y divide-zinc-200 text-sm">
        <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
          <tr>
            <th className="px-4 py-3">Invoice</th>
            <th className="px-4 py-3">Supplier</th>
            <th className="px-4 py-3">GSTIN</th>
            <th className="px-4 py-3">Date</th>
            <th className="px-4 py-3 text-right">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-100 bg-white">
          {rows.map((row) => (
            <tr key={row.id}>
              <td className="px-4 py-3 text-zinc-800">{row.invoiceNumber}</td>
              <td className="px-4 py-3 text-zinc-700">{row.supplier}</td>
              <td className="px-4 py-3 text-zinc-600">{row.gstin}</td>
              <td className="px-4 py-3 text-zinc-600">{formatDate(row.date)}</td>
              <td className="px-4 py-3 text-right font-semibold text-zinc-900">{formatCurrency(row.amount)}</td>
            </tr>
          ))}
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-4 py-6 text-center text-sm text-zinc-500">
                No invoices in this list.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}
