import { getTranslations } from "next-intl/server";

import { DashboardPanel } from "@/components/dashboard/dashboard-panel";

export default async function DashboardPage() {
  const t = await getTranslations("dashboard");

  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="max-w-2xl text-muted-foreground">{t("subtitle")}</p>
      </div>
      <DashboardPanel />
    </div>
  );
}
