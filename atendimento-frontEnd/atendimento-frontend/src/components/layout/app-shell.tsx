import { AppHeader } from "@/components/layout/app-header";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { SkipToMain } from "@/components/layout/skip-to-main";

type AppShellProps = {
  children: React.ReactNode;
};

export function AppShell({ children }: AppShellProps) {
  return (
    <div className="flex h-screen min-h-0 overflow-hidden bg-background">
      <SkipToMain />
      <div className="hidden h-screen shrink-0 md:block">
        <AppSidebar />
      </div>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <AppHeader />
        <main id="main-content" tabIndex={-1} className="min-h-0 flex-1 overflow-y-auto p-4 touch-manipulation scroll-mt-14 outline-none md:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
