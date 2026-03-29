"use client";

import { useEffect, useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import * as Tabs from "@radix-ui/react-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import AuthGate from "@/components/layout/AuthGate";
import Sidebar from "@/components/layout/Sidebar";
import TopBar from "@/components/layout/TopBar";
import { api } from "@/lib/api";

type OrgSettings = {
  name: string;
  baseCurrency: string;
  gstin: string;
  stateCode: string;
};

type WorkflowStep = {
  role: string;
  amountThreshold?: number | null;
};

type Workflow = {
  id: string;
  name: string;
  status?: string;
  conditions?: {
    amountThreshold?: number | null;
    categories?: string[];
  };
  steps: WorkflowStep[];
};

type Member = {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
};

const roleOptions = ["Admin", "Manager", "Finance", "Approver", "Viewer"];

export default function SettingsPage() {
  const queryClient = useQueryClient();

  const orgQuery = useQuery<OrgSettings>({
    queryKey: ["org-settings"],
    queryFn: async () => {
      const { data } = await api.get<OrgSettings>('/v1/org/settings');
      return data;
    },
  });

  const workflowsQuery = useQuery<Workflow[]>({
    queryKey: ["approval-workflows"],
    queryFn: async () => {
      const { data } = await api.get<Workflow[]>("/v1/approvals/workflows");
      return data;
    },
    refetchInterval: 60000,
  });

  const membersQuery = useQuery<Member[]>({
    queryKey: ["members"],
    queryFn: async () => {
      const { data } = await api.get<Member[]>("/v1/users");
      return data;
    },
    refetchInterval: 60000,
  });

  const [orgName, setOrgName] = useState("");
  const [baseCurrency, setBaseCurrency] = useState("INR");
  const [gstin, setGstin] = useState("");
  const [stateCode, setStateCode] = useState("");

  useEffect(() => {
    if (!orgQuery.data) return;
    setOrgName(orgQuery.data.name ?? "");
    setBaseCurrency(orgQuery.data.baseCurrency ?? "INR");
    setGstin(orgQuery.data.gstin ?? "");
    setStateCode(orgQuery.data.stateCode ?? "");
  }, [orgQuery.data]);

  const saveOrg = useMutation({
    mutationFn: async () => {
      await api.post("/v1/org/settings", {
        name: orgName.trim(),
        baseCurrency,
        gstin: gstin.trim(),
        stateCode: stateCode.trim(),
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["org-settings"] }),
  });

  const [workflowDialogOpen, setWorkflowDialogOpen] = useState(false);
  const [workflowStep, setWorkflowStep] = useState<1 | 2>(1);
  const [workflowName, setWorkflowName] = useState("");
  const [conditionAmount, setConditionAmount] = useState("");
  const [conditionCategories, setConditionCategories] = useState("");
  const [steps, setSteps] = useState<WorkflowStep[]>([]);
  const [stepRole, setStepRole] = useState(roleOptions[0]);
  const [stepAmount, setStepAmount] = useState("");
  const [editingWorkflowId, setEditingWorkflowId] = useState<string | null>(null);

  const resetWorkflowForm = () => {
    setWorkflowStep(1);
    setWorkflowName("");
    setConditionAmount("");
    setConditionCategories("");
    setSteps([]);
    setStepRole(roleOptions[0]);
    setStepAmount("");
    setEditingWorkflowId(null);
  };

  const addStep = () => {
    setSteps((prev) => [
      ...prev,
      { role: stepRole, amountThreshold: stepAmount ? Number(stepAmount) : null },
    ]);
    setStepAmount("");
    setStepRole(roleOptions[0]);
  };

  const createWorkflow = useMutation({
    mutationFn: async () => {
      const payload = {
        name: workflowName.trim(),
        conditions: {
          amountThreshold: conditionAmount ? Number(conditionAmount) : null,
          categories: conditionCategories
            .split(",")
            .map((c) => c.trim())
            .filter(Boolean),
        },
        steps,
      };
      if (editingWorkflowId) {
        await api.put(`/v1/approvals/workflows/${editingWorkflowId}`, payload);
      } else {
        await api.post("/v1/approvals/workflows", payload);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["approval-workflows"] });
      setWorkflowDialogOpen(false);
      resetWorkflowForm();
    },
  });

  const deactivateWorkflow = useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/v1/approvals/workflows/${id}/deactivate`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["approval-workflows"] }),
  });

  const updateRole = useMutation({
    mutationFn: async ({ id, role }: { id: string; role: string }) => {
      await api.post(`/v1/users/${id}/role`, { role });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["members"] }),
  });

  const inviteUser = useMutation({
    mutationFn: async (email: string) => {
      await api.post("/v1/users/invite", { email });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["members"] }),
  });

  const [inviteEmail, setInviteEmail] = useState("");

  const activeWorkflows = useMemo(
    () => (workflowsQuery.data ?? []).filter((w) => (w.status ?? "ACTIVE").toUpperCase() === "ACTIVE"),
    [workflowsQuery.data]
  );

  return (
    <AuthGate>
      <div className="flex min-h-screen">
        <Sidebar />
        <div className="flex flex-1 flex-col">
          <TopBar />
          <div className="flex-1 px-6 py-6 lg:px-10">
            <Tabs.Root defaultValue="org" className="space-y-6">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h1 className="text-2xl font-semibold text-zinc-900">Settings</h1>
                  <p className="text-sm text-zinc-600">Configure your organization</p>
                </div>
                <Tabs.List className="flex flex-wrap gap-2 rounded-lg border border-zinc-200 bg-white p-1 shadow-sm">
                  <Tabs.Trigger value="org" className="rounded-md px-3 py-2 text-sm font-semibold text-zinc-700 data-[state=active]:bg-indigo-50 data-[state=active]:text-indigo-700">
                    Organization
                  </Tabs.Trigger>
                  <Tabs.Trigger value="workflows" className="rounded-md px-3 py-2 text-sm font-semibold text-zinc-700 data-[state=active]:bg-indigo-50 data-[state=active]:text-indigo-700">
                    Approval Workflows
                  </Tabs.Trigger>
                  <Tabs.Trigger value="team" className="rounded-md px-3 py-2 text-sm font-semibold text-zinc-700 data-[state=active]:bg-indigo-50 data-[state=active]:text-indigo-700">
                    Team Members
                  </Tabs.Trigger>
                  <Tabs.Trigger value="integrations" className="rounded-md px-3 py-2 text-sm font-semibold text-zinc-700 data-[state=active]:bg-indigo-50 data-[state=active]:text-indigo-700">
                    Integrations
                  </Tabs.Trigger>
                </Tabs.List>
              </div>

              <Tabs.Content value="org" className="space-y-4">
                <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
                  <h2 className="text-lg font-semibold text-zinc-900">Organization</h2>
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">Name</label>
                      <input
                        value={orgName}
                        onChange={(e) => setOrgName(e.target.value)}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">Base Currency</label>
                      <select
                        value={baseCurrency}
                        onChange={(e) => setBaseCurrency(e.target.value)}
                        className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      >
                        <option value="INR">INR</option>
                        <option value="USD">USD</option>
                        <option value="EUR">EUR</option>
                        <option value="GBP">GBP</option>
                      </select>
                    </div>
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">GSTIN</label>
                      <input
                        value={gstin}
                        onChange={(e) => setGstin(e.target.value)}
                        placeholder="22AAAAA0000A1Z5"
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-sm font-medium text-zinc-800">State Code</label>
                      <input
                        value={stateCode}
                        onChange={(e) => setStateCode(e.target.value)}
                        placeholder="KA"
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                  </div>
                  {saveOrg.isError ? (
                    <p className="mt-3 text-sm text-rose-600">Failed to save. Try again.</p>
                  ) : null}
                  <div className="mt-4 flex justify-end">
                    <button
                      type="button"
                      onClick={() => saveOrg.mutate()}
                      disabled={saveOrg.isLoading}
                      className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {saveOrg.isLoading ? "Saving..." : "Save"}
                    </button>
                  </div>
                </section>
              </Tabs.Content>

              <Tabs.Content value="workflows" className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-semibold text-zinc-900">Approval Workflows</h2>
                    <p className="text-sm text-zinc-600">Routes and guardrails for spend</p>
                  </div>
                  <Dialog.Root
                    open={workflowDialogOpen}
                    onOpenChange={(open) => {
                      setWorkflowDialogOpen(open);
                      if (!open) resetWorkflowForm();
                    }}
                  >
                    <Dialog.Trigger asChild>
                      <button
                        type="button"
                        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500"
                        onClick={() => {
                          setWorkflowStep(1);
                          setEditingWorkflowId(null);
                        }}
                      >
                        Add Workflow
                      </button>
                    </Dialog.Trigger>
                    <Dialog.Portal>
                      <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
                      <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[95vw] max-w-xl -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl focus:outline-none">
                        <div className="flex items-start justify-between gap-3">
                          <Dialog.Title className="text-lg font-semibold text-zinc-900">New Workflow</Dialog.Title>
                          <Dialog.Close asChild>
                            <button
                              type="button"
                              aria-label="Close"
                              className="rounded-md p-1 text-zinc-500 transition hover:bg-zinc-100"
                              onClick={resetWorkflowForm}
                            >
                              ✕
                            </button>
                          </Dialog.Close>
                        </div>
                        <div className="mt-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">
                          <span className={workflowStep === 1 ? "text-indigo-600" : "text-zinc-400"}>Step 1</span>
                          <div className="h-px flex-1 bg-zinc-200" />
                          <span className={workflowStep === 2 ? "text-indigo-600" : "text-zinc-400"}>Step 2</span>
                        </div>

                        {workflowStep === 1 ? (
                          <div className="mt-4 space-y-4">
                            <div className="space-y-1">
                              <label className="text-sm font-medium text-zinc-800">Workflow Name</label>
                              <input
                                value={workflowName}
                                onChange={(e) => setWorkflowName(e.target.value)}
                                placeholder="Marketing > 1L requires Director"
                                className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                              />
                            </div>
                            <div className="grid gap-4 sm:grid-cols-2">
                              <div className="space-y-1">
                                <label className="text-sm font-medium text-zinc-800">Amount Threshold</label>
                                <input
                                  type="number"
                                  min="0"
                                  value={conditionAmount}
                                  onChange={(e) => setConditionAmount(e.target.value)}
                                  placeholder="100000"
                                  className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                                />
                              </div>
                              <div className="space-y-1">
                                <label className="text-sm font-medium text-zinc-800">Categories</label>
                                <input
                                  value={conditionCategories}
                                  onChange={(e) => setConditionCategories(e.target.value)}
                                  placeholder="Travel, Marketing"
                                  className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                                />
                              </div>
                            </div>
                            <div className="flex justify-end">
                              <button
                                type="button"
                                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500"
                                onClick={() => setWorkflowStep(2)}
                                disabled={!workflowName.trim()}
                              >
                                Next: Steps
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="mt-4 space-y-4">
                            <div className="flex items-end gap-3">
                              <div className="flex-1 space-y-1">
                                <label className="text-sm font-medium text-zinc-800">Role</label>
                                <select
                                  value={stepRole}
                                  onChange={(e) => setStepRole(e.target.value)}
                                  className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                                >
                                  {roleOptions.map((role) => (
                                    <option key={role} value={role}>{role}</option>
                                  ))}
                                </select>
                              </div>
                              <div className="w-40 space-y-1">
                                <label className="text-sm font-medium text-zinc-800">Amt Threshold (optional)</label>
                                <input
                                  type="number"
                                  min="0"
                                  value={stepAmount}
                                  onChange={(e) => setStepAmount(e.target.value)}
                                  className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                                />
                              </div>
                              <button
                                type="button"
                                onClick={addStep}
                                className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-semibold text-zinc-700 shadow-sm transition hover:border-zinc-300"
                              >
                                Add Step
                              </button>
                            </div>
                            {steps.length === 0 ? (
                              <p className="text-sm text-zinc-600">No steps yet. Add at least one approver.</p>
                            ) : (
                              <div className="space-y-2 rounded-lg border border-zinc-200 bg-zinc-50 p-3">
                                {steps.map((step, idx) => (
                                  <div key={`${step.role}-${idx}`} className="flex items-center justify-between text-sm text-zinc-800">
                                    <div className="flex items-center gap-3">
                                      <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700">{idx + 1}</span>
                                      <span>{step.role}</span>
                                    </div>
                                    {step.amountThreshold ? (
                                      <span className="text-xs text-zinc-500">≥ {step.amountThreshold}</span>
                                    ) : null}
                                  </div>
                                ))}
                              </div>
                            )}
                            {createWorkflow.isError ? (
                              <p className="text-sm text-rose-600">Could not create workflow. Try again.</p>
                            ) : null}
                            <div className="flex justify-between">
                              <button
                                type="button"
                                className="rounded-lg border border-zinc-200 px-3 py-2 text-sm font-semibold text-zinc-700 shadow-sm hover:border-zinc-300"
                                onClick={() => setWorkflowStep(1)}
                              >
                                Back
                              </button>
                              <div className="flex gap-2">
                                <Dialog.Close asChild>
                                  <button
                                    type="button"
                                    className="rounded-lg border border-zinc-200 px-3 py-2 text-sm font-semibold text-zinc-700 shadow-sm hover:border-zinc-300"
                                    onClick={resetWorkflowForm}
                                  >
                                    Cancel
                                  </button>
                                </Dialog.Close>
                                <button
                                  type="button"
                                  disabled={steps.length === 0 || createWorkflow.isLoading}
                                  onClick={() => createWorkflow.mutate()}
                                  className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                  {createWorkflow.isLoading ? "Saving..." : "Create"}
                                </button>
                              </div>
                            </div>
                          </div>
                        )}
                      </Dialog.Content>
                    </Dialog.Portal>
                  </Dialog.Root>
                </div>

                {workflowsQuery.isLoading ? (
                  <div className="rounded-xl border border-zinc-200 bg-white p-6 text-sm text-zinc-600">Loading workflows...</div>
                ) : activeWorkflows.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-zinc-300 bg-white p-6 text-center text-sm text-zinc-600">No active workflows.</div>
                ) : (
                  <div className="grid gap-4 md:grid-cols-2">
                    {activeWorkflows.map((wf) => (
                      <article key={wf.id} className="flex flex-col gap-3 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-base font-semibold text-zinc-900">{wf.name}</p>
                            <p className="text-xs uppercase tracking-wide text-zinc-500">Active</p>
                          </div>
                          <div className="flex gap-2">
                            <button
                              type="button"
                              className="rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-semibold text-zinc-700 shadow-sm hover:border-zinc-300"
                              onClick={() => {
                                setWorkflowDialogOpen(true);
                                setWorkflowStep(1);
                                setWorkflowName(wf.name ?? "");
                                setConditionAmount(wf.conditions?.amountThreshold ? String(wf.conditions.amountThreshold) : "");
                                setConditionCategories((wf.conditions?.categories ?? []).join(", "));
                                setSteps(wf.steps ?? []);
                                setEditingWorkflowId(wf.id);
                              }}
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              disabled={deactivateWorkflow.isLoading}
                              onClick={() => deactivateWorkflow.mutate(wf.id)}
                              className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 shadow-sm transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              Deactivate
                            </button>
                          </div>
                        </div>
                        <div className="rounded-lg border border-zinc-100 bg-zinc-50 p-3 text-sm text-zinc-700">
                          <p className="font-semibold text-zinc-800">Conditions</p>
                          <p className="mt-1 text-zinc-600">
                            {wf.conditions?.amountThreshold ? `≥ ${wf.conditions.amountThreshold}` : "No amount gate"}
                            {wf.conditions?.categories?.length ? ` • Categories: ${wf.conditions.categories.join(", ")}` : ""}
                          </p>
                          <div className="mt-2 space-y-2">
                            {wf.steps.map((step, idx) => (
                              <div key={`${wf.id}-step-${idx}`} className="flex items-center gap-3 rounded-md bg-white px-3 py-2 shadow-sm">
                                <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700">{idx + 1}</span>
                                <span className="font-medium text-zinc-800">{step.role}</span>
                                {step.amountThreshold ? (
                                  <span className="text-xs text-zinc-500">≥ {step.amountThreshold}</span>
                                ) : null}
                              </div>
                            ))}
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </Tabs.Content>

              <Tabs.Content value="team" className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-semibold text-zinc-900">Team Members</h2>
                    <p className="text-sm text-zinc-600">Manage access and roles</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="email"
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                      placeholder="user@company.com"
                      className="w-56 rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                    />
                    <button
                      type="button"
                      onClick={() => inviteEmail && inviteUser.mutate(inviteEmail)}
                      disabled={!inviteEmail || inviteUser.isLoading}
                      className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {inviteUser.isLoading ? "Sending..." : "Invite User"}
                    </button>
                  </div>
                </div>

                <div className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-zinc-200 text-sm">
                      <thead className="bg-zinc-50 text-left text-xs font-semibold uppercase tracking-wide text-zinc-500">
                        <tr>
                          <th className="px-4 py-3">Name</th>
                          <th className="px-4 py-3">Email</th>
                          <th className="px-4 py-3">Role</th>
                          <th className="px-4 py-3">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-zinc-100 bg-white">
                        {(membersQuery.data ?? []).map((member) => (
                          <tr key={member.id}>
                            <td className="px-4 py-3 font-medium text-zinc-900">{member.name}</td>
                            <td className="px-4 py-3 text-zinc-700">{member.email}</td>
                            <td className="px-4 py-3 text-zinc-700">
                              <select
                                value={member.role}
                                onChange={(e) => updateRole.mutate({ id: member.id, role: e.target.value })}
                                className="rounded-md border border-zinc-200 bg-white px-2 py-1 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                              >
                                {roleOptions.map((role) => (
                                  <option key={role} value={role}>{role}</option>
                                ))}
                              </select>
                            </td>
                            <td className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-zinc-600">{member.status}</td>
                          </tr>
                        ))}
                        {membersQuery.isLoading ? (
                          <tr>
                            <td colSpan={4} className="px-4 py-6 text-center text-sm text-zinc-500">Loading members...</td>
                          </tr>
                        ) : null}
                        {(membersQuery.data ?? []).length === 0 && !membersQuery.isLoading ? (
                          <tr>
                            <td colSpan={4} className="px-4 py-6 text-center text-sm text-zinc-500">No members found.</td>
                          </tr>
                        ) : null}
                      </tbody>
                    </table>
                  </div>
                </div>
              </Tabs.Content>

              <Tabs.Content value="integrations" className="space-y-4">
                <h2 className="text-lg font-semibold text-zinc-900">Integrations</h2>
                <p className="text-sm text-zinc-600">Connect accounting and communication tools</p>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                  {["Tally", "Zoho Books", "Slack"].map((name) => (
                    <div key={name} className="flex flex-col gap-2 rounded-xl border border-dashed border-zinc-300 bg-white p-4 text-sm shadow-sm">
                      <div className="flex items-center justify-between">
                        <p className="text-base font-semibold text-zinc-900">{name}</p>
                        <span className="rounded-full bg-zinc-100 px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-zinc-500">Coming Soon</span>
                      </div>
                      <p className="text-zinc-600">We are working on a native {name} integration.</p>
                      <button
                        type="button"
                        disabled
                        className="mt-auto rounded-lg bg-zinc-100 px-3 py-2 text-sm font-semibold text-zinc-400"
                      >
                        Connect
                      </button>
                    </div>
                  ))}
                </div>
              </Tabs.Content>
            </Tabs.Root>
          </div>
        </div>
      </div>
    </AuthGate>
  );
}