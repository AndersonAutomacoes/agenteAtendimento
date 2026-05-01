import { Loader2 } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { DashboardPanel } from "@/components/dashboard/dashboard-panel";

export default async function DashboardPage() {
  const t = await getTranslations("dashboard");

  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <h2 className="sr-only">{t("overviewSection")}</h2>
        <p className="max-w-2xl text-muted-foreground">{t("subtitle")}</p>
      </div>
      <Suspense
        fallback={
          <div className="flex min-h-[12rem] items-center justify-center gap-2 text-muted-foreground">
            <Loader2 className="h-6 w-6 shrink-0 animate-spin" aria-hidden />
            <span>{t("loading")}</span>
          </div>
        }
      >
        <DashboardPanel />
      </Suspense>
    </div>
  );
}
