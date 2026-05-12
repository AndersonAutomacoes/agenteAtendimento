import { AppAuthenticatedGate } from "@/components/auth/app-authenticated-gate";
import { AppShell } from "@/components/layout/app-shell";

export default function AppLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <AppAuthenticatedGate>
      <AppShell>{children}</AppShell>
    </AppAuthenticatedGate>
  );
}
