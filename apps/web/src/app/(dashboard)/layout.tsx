"use client";

import Sidebar from "@/components/layout/Sidebar";
import TopBar from "@/components/layout/TopBar";
import AuthGate from "@/components/layout/AuthGate";

function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGate>
      <div className="flex min-h-screen">
        <Sidebar />
        <div className="flex flex-1 flex-col">
          <TopBar />
          <div className="flex-1 px-6 py-6 lg:px-10">{children}</div>
        </div>
      </div>
    </AuthGate>
  );
}

export default DashboardLayout;
