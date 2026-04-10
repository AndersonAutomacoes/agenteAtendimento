import { AppHeader } from "@/components/layout/app-header";
import { AppSidebar } from "@/components/layout/app-sidebar";

type AppShellProps = {
  children: React.ReactNode;
};

export function AppShell({ children }: AppShellProps) {
  return (
    <div className="flex h-screen min-h-0 overflow-hidden bg-background">
      <div className="hidden h-screen shrink-0 md:block">
        <AppSidebar />
      </div>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <AppHeader />
        <main className="min-h-0 flex-1 overflow-y-auto p-4 touch-manipulation md:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
