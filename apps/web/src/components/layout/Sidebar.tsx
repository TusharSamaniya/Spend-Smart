"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo } from "react";

import { useAuth } from "@/lib/auth";

const navItems = [
  { label: "Overview", href: "/" },
  { label: "Expenses", href: "/expenses" },
  { label: "Approvals", href: "/approvals" },
  { label: "Analytics", href: "/analytics" },
  { label: "GST Reports", href: "/gst" },
  { label: "Budgets", href: "/budgets" },
  { label: "Settings", href: "/settings" },
];

function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const activeHref = useMemo(() => {
    if (!pathname) return "/";
    const match = navItems.find(({ href }) =>
      href === "/" ? pathname === "/" : pathname.startsWith(href)
    );
    return match?.href ?? "/";
  }, [pathname]);

  return (
    <aside className="hidden w-64 shrink-0 border-r border-zinc-200 bg-white/80 backdrop-blur sm:flex sm:flex-col">
      <div className="flex items-center gap-3 px-6 py-5">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-600 text-lg font-semibold text-white">
          SS
        </div>
        <div>
          <p className="text-base font-semibold text-zinc-900">SpendSmart</p>
          <p className="text-sm text-zinc-500">Dashboard</p>
        </div>
      </div>

      <nav className="flex flex-1 flex-col px-2">
        {navItems.map((item) => {
          const isActive = activeHref === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? "bg-indigo-50 text-indigo-700"
                  : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
              }`}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-zinc-200 px-4 py-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-zinc-900">
              {user?.email ?? "Guest"}
            </p>
            <p className="text-xs text-zinc-500">{user?.role ?? "Viewer"}</p>
          </div>
          <button
            type="button"
            onClick={logout}
            className="rounded-md border border-zinc-200 px-3 py-1 text-xs font-medium text-zinc-700 transition hover:border-zinc-300 hover:bg-zinc-100"
          >
            Logout
          </button>
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
