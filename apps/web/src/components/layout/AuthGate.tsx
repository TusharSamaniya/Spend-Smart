"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import { useAuth } from "@/lib/auth";

function AuthGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, isReady } = useAuth();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    // Skip guard while auth state initializes.
    if (!isReady) return;
    const isPublicAuthRoute = pathname === "/login" || pathname === "/signup";

    if (!isAuthenticated && !isPublicAuthRoute) {
      router.replace("/login");
      return;
    }

    if (isPublicAuthRoute && isAuthenticated) {
      router.replace("/");
      return;
    }

    setChecked(true);
  }, [isAuthenticated, isReady, pathname, router]);

  if (!isReady || !checked) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-50 text-sm text-zinc-500">
        Loading...
      </div>
    );
  }

  return <>{children}</>;
}

export default AuthGate;
