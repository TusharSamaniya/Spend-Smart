"use client";

import * as Toast from "@radix-ui/react-toast";
import { ReactNode } from "react";

function Toaster({ children }: { children?: ReactNode }) {
  return (
    <Toast.Provider swipeDirection="right" label="Notifications">
      {children}
      <Toast.Viewport className="fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-3 outline-none" />
    </Toast.Provider>
  );
}

export { Toaster };
