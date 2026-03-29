"use client";

import { useMemo } from "react";
import { usePathname } from "next/navigation";

const titleMap: Record<string, string> = {
  "/": "Overview",
  "/expenses": "Expenses",
  "/approvals": "Approvals",
  "/analytics": "Analytics",
  "/gst": "GST Reports",
  "/budgets": "Budgets",
  "/settings": "Settings",
};

function TopBar() {
  const pathname = usePathname();

  const title = useMemo(() => {
    if (!pathname) return "Dashboard";
    const key = Object.keys(titleMap).find((prefix) =>
      prefix === "/" ? pathname === "/" : pathname.startsWith(prefix)
    );
    return key ? titleMap[key] : "Dashboard";
  }, [pathname]);

  return (
    <header className="flex items-center justify-between border-b border-zinc-200 bg-white/80 px-4 py-3 backdrop-blur">
      <div>
        <h1 className="text-lg font-semibold text-zinc-900">{title}</h1>
        <p className="text-sm text-zinc-500">SpendSmart</p>
      </div>
      <button
        type="button"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full border border-zinc-200 text-zinc-600 transition hover:bg-zinc-100"
        aria-label="Notifications"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth="1.5"
          stroke="currentColor"
          className="h-5 w-5"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M14.857 17.25c0 .791-.642 1.417-1.435 1.417h-2.844c-.793 0-1.435-.626-1.435-1.417m9.513-4.664c-.482-1.043-.992-1.483-.992-3.336 0-3.918-2.004-5.625-5.564-5.625-3.559 0-5.563 1.707-5.563 5.625 0 1.853-.51 2.293-.993 3.336-.348.752-.066 1.61.644 1.982.928.482 2.461.993 5.912.993 3.45 0 4.983-.511 5.911-.993.71-.373.992-1.23.644-1.982Z"
          />
        </svg>
        <span className="absolute right-1 top-1 inline-flex h-2 w-2 rounded-full bg-emerald-500" aria-hidden />
      </button>
    </header>
  );
}

export default TopBar;
