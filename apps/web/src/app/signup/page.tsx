"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import axios from "axios";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";

const schema = z
  .object({
    fullName: z.string().min(1, "Full Name is required").max(255),
    organizationName: z.string().min(1, "Organization Name is required").max(255),
    email: z.string().email("Enter a valid email"),
    password: z.string().min(8, "Password must be at least 8 characters").max(100),
    confirmPassword: z.string().min(1, "Confirm Password is required"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof schema>;

type RegisterResponse = {
  accessToken: string;
  refreshToken: string;
};

type StoredUser = {
  userId: string;
  orgId: string;
  email: string;
  role: string;
};

function decodeJwtPayload(token: string): any {
  const parts = token.split(".");
  if (parts.length < 2) throw new Error("Invalid JWT format");

  const base64Url = parts[1];
  const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(
    base64.length + ((4 - (base64.length % 4)) % 4),
    "="
  );

  return JSON.parse(atob(padded));
}

export default function SignupPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: "",
      organizationName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (values: FormValues) => {
    setError(null);
    setIsLoading(true);
    try {
      const response = await axios.post<RegisterResponse>(
        "http://localhost:8080/v1/auth/register",
        {
          name: values.fullName,
          email: values.email,
          password: values.password,
          organizationName: values.organizationName,
        },
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );

      const { accessToken, refreshToken } = response.data;
      if (!accessToken || !refreshToken) {
        throw new Error("Missing tokens in response");
      }

      const payload = decodeJwtPayload(accessToken);
      const user: StoredUser = {
        userId: payload.userId ?? payload.user_id,
        orgId: payload.orgId ?? payload.org_id,
        email: payload.email ?? payload.sub,
        role: payload.role,
      };

      if (!user.userId || !user.orgId || !user.email || !user.role) {
        throw new Error("Token is missing required user fields");
      }

      localStorage.setItem("accessToken", accessToken);
      localStorage.setItem("refreshToken", refreshToken);
      localStorage.setItem("user", JSON.stringify(user));

      router.push("/");
    } catch (err) {
      const apiMessage =
        (err as any)?.response?.data?.message ??
        (err as any)?.response?.data?.error ??
        (err as any)?.message;
      setError(apiMessage || "Could not create account");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-100 px-4">
      <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-8 shadow-md">
        <div className="mb-6">
          <p className="text-lg font-semibold text-zinc-900">Create your account</p>
          <p className="text-sm text-zinc-500">SpendSmart sign up</p>
        </div>

        <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
          <Field
            label="Full Name"
            error={form.formState.errors.fullName?.message}
          >
            <input
              className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
              {...form.register("fullName")}
            />
          </Field>

          <Field
            label="Organization Name"
            error={form.formState.errors.organizationName?.message}
          >
            <input
              className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
              {...form.register("organizationName")}
            />
          </Field>

          <Field label="Email" error={form.formState.errors.email?.message}>
            <input
              type="email"
              autoComplete="email"
              className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
              {...form.register("email")}
            />
          </Field>

          <Field
            label="Password"
            error={form.formState.errors.password?.message}
          >
            <input
              type="password"
              autoComplete="new-password"
              className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
              {...form.register("password")}
            />
          </Field>

          <Field
            label="Confirm Password"
            error={form.formState.errors.confirmPassword?.message}
          >
            <input
              type="password"
              autoComplete="new-password"
              className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
              {...form.register("confirmPassword")}
            />
          </Field>

          {error ? (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {error}
            </div>
          ) : null}

          <button
            type="submit"
            disabled={isLoading}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isLoading ? (
              <span className="flex items-center gap-2">
                <span
                  className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-b-transparent"
                  aria-hidden
                />
                Creating...
              </span>
            ) : (
              "Create Account"
            )}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-zinc-600">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-semibold text-indigo-600 hover:text-indigo-500"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      <label className="text-sm font-medium text-zinc-800">{label}</label>
      {children}
      {error ? <p className="text-sm text-rose-600">{error}</p> : null}
    </div>
  );
}
