"use client";

import {
  Activity,
  Bot,
  CalendarClock,
  ChevronDown,
  Loader2,
  MessageSquare,
  RefreshCw,
  Target,
  Users,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import * as React from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  type DashboardPeriodPreset,
  computeDashboardPeriodRange,
  periodSpanHours,
} from "@/lib/dashboard-period";
import { FeatureGuard } from "@/components/plan/feature-guard";
import { usePlan } from "@/components/plan/plan-provider";
import { UpgradePlanPanel } from "@/components/plan/upgrade-plan-panel";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { planMeetsRequirement } from "@/lib/plan-tier";
import { useMediaQuery } from "@/lib/use-media-query";
import { cn } from "@/lib/utils";
import { Link, useRouter } from "@/i18n/navigation";
import {
  assumeCrmOpportunity,
  exportAnalyticsReport,
  getAnalyticsIntents,
  getCrmOpportunities,
  getDashboardSummary,
  getUpcomingAppointmentsCount,
  humanHandoffConversation,
  toUserFacingApiError,
  type AnalyticsExportFormat,
  type AnalyticsIntentsResponse,
  type ConversationSentiment,
  type CrmCustomerDto,
  type DashboardSummary,
  type PrimaryIntentCategory,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

function emptyAnalyticsResponse(
  tenantId: string,
  startDate: string,
  endDate: string,
): AnalyticsIntentsResponse {
  return {
    tenantId,
    days: 0,
    periodStart: startDate,
    periodEnd: endDate,
    previousPeriodStart: startDate,
    previousPeriodEnd: endDate,
    counts: [],
    previousCounts: [],
    sentimentCounts: [],
  };
}

const cardClass =
  "rounded-2xl border-border/70 shadow-md shadow-black/5 transition-shadow hover:shadow-lg dark:shadow-black/25";

function formatPhoneDisplay(raw: string): string {
  const t = raw.trim();
  if (!t) return "—";
  const digits = t.replace(/\D/g, "");
  if (digits.length >= 12 && digits.startsWith("55")) {
    const d = digits.slice(2);
    if (d.length === 11) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
    }
    if (d.length === 10) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
    }
  }
  if (digits.length >= 10) {
    return digits;
  }
  return t;
}

function contactInitials(name: string | null, phone: string): string {
  const n = name?.trim();
  if (n) {
    const parts = n.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0]!.slice(0, 1) + parts[1]!.slice(0, 1)).toUpperCase();
    }
    return n.slice(0, 2).toUpperCase();
  }
  const d = phone.replace(/\D/g, "");
  return d.slice(-2).toUpperCase() || "?";
}

function monitorPhoneFromCrmCustomer(c: CrmCustomerDto): string {
  const p = c.phoneNumber?.trim();
  if (p) return p;
  const conv = c.conversationId?.trim() ?? "";
  if (conv.startsWith("wa-")) {
    const d = conv.slice(3).replace(/\D/g, "");
    if (d) return d;
  }
  return conv;
}

type ChartRow = { bucketStart: string; count: number; label: string };

const PIE_COLORS = ["#22d3ee", "#34d399", "#a78bfa", "#fb7185", "#fbbf24"];

const SENTIMENT_ORDER: readonly ConversationSentiment[] = ["POSITIVO", "NEUTRO", "NEGATIVO"];

const SENTIMENT_BAR_COLORS: Record<ConversationSentiment, string> = {
  POSITIVO: "#34d399",
  NEUTRO: "hsl(var(--muted-foreground))",
  NEGATIVO: "#fb7185",
};

const AI_INSIGHT_MIN_PERCENT = 5;

/** evita ResponsiveContainer medir antes do layout (warnings width/height -1 em mobile / WebView) */
function useChartReady() {
  const [ready, setReady] = React.useState(false);
  React.useEffect(() => {
    const id = requestAnimationFrame(() => setReady(true));
    return () => cancelAnimationFrame(id);
  }, []);
  return ready;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function opportunityLeadScorePresentation(
  score: number | null | undefined,
  translate: (key: string) => string,
): { text: string; className: string; title?: string } {
  if (score == null) {
    return { text: "—", className: "text-muted-foreground" };
  }
  if (score <= 30) {
    return {
      text: String(score),
      className: "font-semibold text-zinc-400",
      title: translate("opportunities.scoreTierCold"),
    };
  }
  if (score <= 70) {
    return {
      text: String(score),
      className: "font-semibold text-amber-400",
      title: translate("opportunities.scoreTierWarm"),
    };
  }
  return {
    text: String(score),
    className: "font-semibold text-red-500",
    title: translate("opportunities.scoreTierHot"),
  };
}

export function DashboardPanel() {
  const t = useTranslations("dashboard");
  const tApi = useTranslations("api");
  const tPlan = useTranslations("plan");
  const { tier } = usePlan();
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const [tenantId, setTenantId] = React.useState("");
  const [periodPreset, setPeriodPreset] = React.useState<DashboardPeriodPreset>("today");
  const [customFrom, setCustomFrom] = React.useState("");
  const [customTo, setCustomTo] = React.useState("");
  const [data, setData] = React.useState<DashboardSummary | null>(null);
  const [intentData, setIntentData] = React.useState<AnalyticsIntentsResponse | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [intentError, setIntentError] = React.useState<string | null>(null);
  const [periodError, setPeriodError] = React.useState<string | null>(null);
  const [exportLoading, setExportLoading] = React.useState(false);
  const [exportError, setExportError] = React.useState<string | null>(null);
  const [periodDialogOpen, setPeriodDialogOpen] = React.useState(false);
  const [exportUpgradeOpen, setExportUpgradeOpen] = React.useState(false);
  const [upcomingApptCount, setUpcomingApptCount] = React.useState<number | null>(null);
  const [upcomingApptError, setUpcomingApptError] = React.useState<string | null>(null);
  const [mainTab, setMainTab] = React.useState<"metrics" | "opportunities">("metrics");
  const [opportunities, setOpportunities] = React.useState<CrmCustomerDto[] | null>(null);
  const [opportunitiesLoading, setOpportunitiesLoading] = React.useState(false);
  const [opportunitiesError, setOpportunitiesError] = React.useState<string | null>(null);
  const [assumingId, setAssumingId] = React.useState<string | null>(null);
  const router = useRouter();

  const isSmallViewport = useMediaQuery("(max-width: 767px)");
  const chartTickSize = isSmallViewport ? 10 : 12;
  const pieOuterRadius = isSmallViewport ? 72 : 96;
  const sentimentYAxisW = isSmallViewport ? 76 : 100;
  const chartReady = useChartReady();
  const areaChartPx = isSmallViewport ? 280 : 320;
  const lowerChartPx = isSmallViewport ? 260 : 300;

  const periodRange = React.useMemo(
    () =>
      computeDashboardPeriodRange(
        periodPreset,
        periodPreset === "custom" ? { from: customFrom, to: customTo } : null,
      ),
    [periodPreset, customFrom, customTo],
  );

  const readTenantFromStorage = React.useCallback(() => {
    try {
      setTenantId(localStorage.getItem(TENANT_STORAGE_KEY) ?? "");
    } catch {
      /* ignore */
    }
  }, []);

  React.useEffect(() => {
    readTenantFromStorage();
  }, [readTenantFromStorage]);

  React.useEffect(() => {
    window.addEventListener("focus", readTenantFromStorage);
    return () => window.removeEventListener("focus", readTenantFromStorage);
  }, [readTenantFromStorage]);

  const load = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      setData(null);
      setIntentData(null);
      setError(null);
      setIntentError(null);
      setPeriodError(null);
      setUpcomingApptCount(null);
      setUpcomingApptError(null);
      return;
    }
    if (!periodRange) {
      setPeriodError(t("periodCustomInvalid"));
      setData(null);
      setIntentData(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    setIntentError(null);
    setPeriodError(null);
    const { startDate, endDate } = periodRange;
    const analyticsAllowed = planMeetsRequirement(tier, "pro");
    const intentsPromise = analyticsAllowed
      ? getAnalyticsIntents(tid, startDate, endDate)
      : Promise.resolve(emptyAnalyticsResponse(tid, startDate, endDate));
    const upcomingAllowed = planMeetsRequirement(tier, "pro");
    const upcomingPromise = upcomingAllowed
      ? getUpcomingAppointmentsCount(tid, 7)
      : Promise.resolve(null);
    const [sumRes, intRes, upRes] = await Promise.allSettled([
      getDashboardSummary(tid, startDate, endDate),
      intentsPromise,
      upcomingPromise,
    ]);
    if (sumRes.status === "fulfilled") {
      setData(sumRes.value);
      setError(null);
    } else {
      setData(null);
      setError(toUserFacingApiError(sumRes.reason, (k) => tApi(k)));
    }
    if (intRes.status === "fulfilled") {
      setIntentData(intRes.value);
      setIntentError(null);
    } else {
      setIntentData(null);
      setIntentError(toUserFacingApiError(intRes.reason, (k) => tApi(k)));
    }
    if (upcomingAllowed) {
      if (upRes.status === "fulfilled" && upRes.value) {
        setUpcomingApptCount(upRes.value.count);
        setUpcomingApptError(null);
      } else if (upRes.status === "rejected") {
        setUpcomingApptCount(null);
        setUpcomingApptError(toUserFacingApiError(upRes.reason, (k) => tApi(k)));
      }
    } else {
      setUpcomingApptCount(null);
      setUpcomingApptError(null);
    }
    setLoading(false);
  }, [tenantId, periodRange, tApi, t, tier]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    if (periodPreset !== "custom") setPeriodDialogOpen(false);
  }, [periodPreset]);

  const loadOpportunities = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      setOpportunities(null);
      setOpportunitiesError(null);
      return;
    }
    setOpportunitiesLoading(true);
    setOpportunitiesError(null);
    try {
      const res = await getCrmOpportunities(tid);
      setOpportunities(res.opportunities);
    } catch (e) {
      setOpportunitiesError(toUserFacingApiError(e, (k) => tApi(k)));
      setOpportunities(null);
    } finally {
      setOpportunitiesLoading(false);
    }
  }, [tenantId, tApi]);

  React.useEffect(() => {
    if (mainTab !== "opportunities") return;
    void loadOpportunities();
  }, [mainTab, loadOpportunities]);

  const handleAssumeOpportunity = React.useCallback(
    async (row: CrmCustomerDto) => {
      const tid = tenantId.trim();
      if (!tid) return;
      setAssumingId(row.id);
      setOpportunitiesError(null);
      try {
        await assumeCrmOpportunity(tid, row.id);
        await humanHandoffConversation(tid, monitorPhoneFromCrmCustomer(row));
        await loadOpportunities();
        const digits = monitorPhoneFromCrmCustomer(row).replace(/\D/g, "");
        router.push(
          digits
            ? `/dashboard/monitoramento?phone=${encodeURIComponent(digits)}`
            : "/dashboard/monitoramento",
        );
      } catch {
        setOpportunitiesError(t("opportunities.actionError"));
      } finally {
        setAssumingId(null);
      }
    },
    [tenantId, router, loadOpportunities, t],
  );

  const useHourlyChartLabels = React.useMemo(() => {
    if (!periodRange) return true;
    return periodSpanHours(periodRange.startDate, periodRange.endDate) <= 48;
  }, [periodRange]);

  const chartData: ChartRow[] = React.useMemo(() => {
    if (!data) return [];
    return data.series.map((p) => {
      let label: string;
      try {
        const d = new Date(p.bucketStart);
        if (useHourlyChartLabels) {
          label = d.toLocaleTimeString(dateLocale, { hour: "2-digit", minute: "2-digit" });
        } else {
          label = d.toLocaleDateString(dateLocale, { day: "2-digit", month: "short" });
        }
      } catch {
        label = p.bucketStart;
      }
      return { ...p, label };
    });
  }, [data, useHourlyChartLabels, dateLocale]);

  const runExport = React.useCallback(
    async (format: AnalyticsExportFormat) => {
      const tid = tenantId.trim();
      if (!tid || !periodRange) return;
      setExportError(null);
      setExportLoading(true);
      try {
        const blob = await exportAnalyticsReport(
          tid,
          periodRange.startDate,
          periodRange.endDate,
          locale,
          format,
        );
        const ext = format;
        downloadBlob(blob, `intelizap-report-${tid.replace(/[^a-zA-Z0-9_-]/g, "_")}.${ext}`);
      } catch (e) {
        setExportError(toUserFacingApiError(e, (k) => tApi(k)));
      } finally {
        setExportLoading(false);
      }
    },
    [tenantId, periodRange, locale, tApi],
  );

  const primaryIntentLabel = React.useCallback(
    (raw: string) => {
      switch (raw) {
        case "ORCAMENTO":
          return t("primaryIntents.ORCAMENTO");
        case "AGENDAMENTO":
          return t("primaryIntents.AGENDAMENTO");
        case "DUVIDA_TECNICA":
          return t("primaryIntents.DUVIDA_TECNICA");
        case "RECLAMACAO":
          return t("primaryIntents.RECLAMACAO");
        case "OUTRO":
          return t("primaryIntents.OUTRO");
        default:
          return raw;
      }
    },
    [t],
  );

  const pieRows = React.useMemo(() => {
    if (!intentData) return [];
    return intentData.counts
      .filter((c) => c.count > 0)
      .map((c) => ({
        category: c.category,
        count: c.count,
        label: primaryIntentLabel(c.category),
      }));
  }, [intentData, primaryIntentLabel]);

  const totalIntentCount = React.useMemo(() => {
    if (!intentData) return 0;
    return intentData.counts.reduce((acc, c) => acc + c.count, 0);
  }, [intentData]);

  const totalSentimentClassified = React.useMemo(() => {
    if (!intentData) return 0;
    return intentData.sentimentCounts.reduce((acc, s) => acc + s.count, 0);
  }, [intentData]);

  const sentimentBarRows = React.useMemo(() => {
    if (!intentData) return [];
    const m = new Map(intentData.sentimentCounts.map((s) => [s.sentiment, s.count]));
    return SENTIMENT_ORDER.map((sentiment) => ({
      sentiment,
      count: m.get(sentiment) ?? 0,
      label: t(`sentiment.${sentiment}`),
    }));
  }, [intentData, t]);

  const aiInsight = React.useMemo(() => {
    if (!intentData || totalIntentCount === 0) {
      return { kind: "empty" as const };
    }
    const prevMap = new Map(
      intentData.previousCounts.map((p) => [p.category, p.count] as const),
    );
    let best: {
      category: PrimaryIntentCategory;
      percent: number;
      current: number;
      previous: number;
    } | null = null;
    for (const row of intentData.counts) {
      const previous = prevMap.get(row.category) ?? 0;
      const current = row.count;
      let percent = 0;
      if (previous === 0 && current > 0) {
        percent = 100;
      } else if (previous > 0) {
        percent = Math.round(((current - previous) / previous) * 100);
      }
      if (percent < AI_INSIGHT_MIN_PERCENT) {
        continue;
      }
      if (!best || percent > best.percent) {
        best = { category: row.category, percent, current, previous };
      }
    }
    if (best) {
      return { kind: "spike" as const, ...best };
    }
    return { kind: "stable" as const };
  }, [intentData, totalIntentCount]);

  const intentLabel = React.useCallback(
    (raw: string | null) => {
      if (!raw) return t("activities.noIntent");
      switch (raw) {
        case "greeting":
          return t("intents.greeting");
        case "support":
          return t("intents.support");
        case "sales":
          return t("intents.sales");
        case "complaint":
          return t("intents.complaint");
        case "scheduling":
          return t("intents.scheduling");
        case "other":
          return t("intents.other");
        default:
          return raw;
      }
    },
    [t],
  );

  const instanceBadgeClass = (s: string) =>
    s === "CONFIGURED"
      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-400"
      : s === "SIMULATED"
        ? "border-sky-500/40 bg-sky-500/10 text-sky-400"
        : "border-amber-500/40 bg-amber-500/10 text-amber-300";

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2 sm:gap-3">
          <p
            className={cn(
              "min-h-9 max-w-[min(100%,14rem)] shrink-0 truncate text-base font-semibold tracking-tight text-foreground sm:max-w-xs sm:text-lg",
              !tenantId.trim() && "font-normal text-muted-foreground",
            )}
            aria-label={t("accountId")}
            title={tenantId.trim() || undefined}
          >
            {tenantId.trim() || "—"}
          </p>
          <Button
            type="button"
            variant="outline"
            size="touch"
            className="h-11 shrink-0 touch-manipulation gap-2 px-3"
            onClick={() => {
              void load();
              if (mainTab === "opportunities") void loadOpportunities();
            }}
            disabled={(loading && mainTab === "metrics") || !tenantId.trim()}
            aria-label={t("refresh")}
          >
            <RefreshCw
              className={cn(
                "h-4 w-4 shrink-0",
                (loading && mainTab === "metrics") ||
                  (opportunitiesLoading && mainTab === "opportunities")
                  ? "animate-spin"
                  : false,
              )}
              aria-hidden
            />
            {t("refreshButton")}
          </Button>
          {tenantId.trim() ? (
            <div
              className="flex flex-wrap items-center gap-2"
              role="tablist"
              aria-label={t("mainTabMetrics")}
            >
              <Button
                type="button"
                size="touch"
                variant={mainTab === "metrics" ? "default" : "outline"}
                onClick={() => setMainTab("metrics")}
                className={cn(
                  mainTab === "metrics" &&
                    "bg-primary text-primary-foreground shadow-md shadow-cyan-500/20",
                )}
              >
                {t("mainTabMetrics")}
              </Button>
              <Button
                type="button"
                size="touch"
                variant={mainTab === "opportunities" ? "default" : "outline"}
                onClick={() => setMainTab("opportunities")}
                className={cn(
                  "gap-1.5",
                  mainTab === "opportunities" &&
                    "bg-primary text-primary-foreground shadow-md shadow-cyan-500/20",
                )}
              >
                <Target className="h-4 w-4 shrink-0" aria-hidden />
                {t("mainTabOpportunities")}
              </Button>
            </div>
          ) : null}
          {mainTab === "metrics" ? (
            <>
          <div
            className="flex flex-wrap items-center gap-2 sm:gap-3"
            role="tablist"
            aria-label={t("chart.periodAria")}
          >
            {(
              [
                { value: "today" as const, label: "periodToday" as const },
                { value: "last7" as const, label: "periodLast7" as const },
                { value: "thisMonth" as const, label: "periodThisMonth" as const },
                { value: "custom" as const, label: "periodCustom" as const },
              ] as const
            ).map(({ value, label }) => (
              <Button
                key={value}
                type="button"
                size="touch"
                variant={periodPreset === value ? "default" : "outline"}
                onClick={() => setPeriodPreset(value)}
                className={cn(
                  periodPreset === value &&
                    "bg-primary text-primary-foreground shadow-md shadow-cyan-500/20",
                )}
              >
                {t(label)}
              </Button>
            ))}
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                type="button"
                size="touch"
                variant="outline"
                disabled={!tenantId.trim() || !periodRange || exportLoading}
                className="gap-1 shrink-0"
              >
                {exportLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : null}
                {t("export.button")}
                <ChevronDown className="h-4 w-4 opacity-70" aria-hidden />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="min-w-44">
              <DropdownMenuItem
                disabled={exportLoading}
                onSelect={() => {
                  if (!planMeetsRequirement(tier, "enterprise")) {
                    queueMicrotask(() => setExportUpgradeOpen(true));
                    return;
                  }
                  void runExport("csv");
                }}
              >
                {t("export.csv")}
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={exportLoading}
                onSelect={() => {
                  if (!planMeetsRequirement(tier, "enterprise")) {
                    queueMicrotask(() => setExportUpgradeOpen(true));
                    return;
                  }
                  void runExport("pdf");
                }}
              >
                {t("export.pdf")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
            </>
          ) : null}
        </div>
        {mainTab === "metrics" && periodPreset === "custom" && isSmallViewport ? (
          <Button
            type="button"
            size="touch"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={() => setPeriodDialogOpen(true)}
          >
            {t("periodCustomDialogTitle")}
          </Button>
        ) : null}
        {mainTab === "metrics" && periodPreset === "custom" && !isSmallViewport ? (
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <div className="flex flex-col gap-1">
              <Label htmlFor="dash-from" className="text-xs text-muted-foreground">
                {t("periodCustomFrom")}
              </Label>
              <Input
                id="dash-from"
                type="datetime-local"
                value={customFrom}
                onChange={(e) => setCustomFrom(e.target.value)}
                className="w-full min-h-11 sm:w-52"
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="dash-to" className="text-xs text-muted-foreground">
                {t("periodCustomTo")}
              </Label>
              <Input
                id="dash-to"
                type="datetime-local"
                value={customTo}
                onChange={(e) => setCustomTo(e.target.value)}
                className="w-full min-h-11 sm:w-52"
              />
            </div>
          </div>
        ) : null}
      </div>

      {!tenantId.trim() ? (
        <p className="text-sm text-amber-600 dark:text-amber-400">{t("needAccount")}</p>
      ) : null}

      {periodError ? (
        <p className="text-sm text-amber-600 dark:text-amber-400" role="status">
          {periodError}
        </p>
      ) : null}

      {exportError ? (
        <p className="text-sm text-destructive" role="alert">
          {exportError}
        </p>
      ) : null}

      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      {mainTab === "opportunities" && tenantId.trim() ? (
        <>
          {opportunitiesError ? (
            <p className="text-sm text-destructive" role="alert">
              {opportunitiesError}
            </p>
          ) : null}
          {opportunitiesLoading && opportunities === null ? (
            <p className="text-sm text-muted-foreground">{t("loading")}</p>
          ) : null}
          <FeatureGuard requiredPlan="pro">
            <Card className={cn(cardClass)}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
                  <Target className="h-5 w-5 shrink-0 text-cyan-400" aria-hidden />
                  {t("opportunities.title")}
                </CardTitle>
                <CardDescription>{t("opportunities.subtitle")}</CardDescription>
              </CardHeader>
              <CardContent>
                {opportunities !== null && opportunities.length === 0 && !opportunitiesLoading ? (
                  <p className="text-sm text-muted-foreground">{t("opportunities.empty")}</p>
                ) : null}
                {opportunities !== null && opportunities.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[640px] text-sm">
                      <thead>
                        <tr className="border-b border-border/80 text-left text-muted-foreground">
                          <th className="pb-2 pr-4 font-medium">{t("opportunities.colContact")}</th>
                          <th className="pb-2 pr-4 font-medium">{t("opportunities.colIntent")}</th>
                          <th className="pb-2 pr-4 font-medium">{t("opportunities.colScore")}</th>
                          <th className="pb-2 pr-4 font-medium">{t("opportunities.colSince")}</th>
                          <th className="pb-2 text-right font-medium" />
                        </tr>
                      </thead>
                      <tbody>
                        {opportunities.map((row) => {
                          const scorePresentation = opportunityLeadScorePresentation(
                            row.leadScore,
                            t,
                          );
                          return (
                          <tr
                            key={row.id}
                            className="border-b border-border/40 last:border-0"
                          >
                            <td className="py-3 pr-4">
                              <div className="flex items-center gap-2">
                                <span
                                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold"
                                  aria-hidden
                                >
                                  {contactInitials(
                                    row.fullName,
                                    monitorPhoneFromCrmCustomer(row),
                                  )}
                                </span>
                                <div>
                                  <div className="font-medium">
                                    {row.fullName?.trim() ||
                                      formatPhoneDisplay(monitorPhoneFromCrmCustomer(row))}
                                  </div>
                                  <div className="text-xs text-muted-foreground">
                                    {formatPhoneDisplay(monitorPhoneFromCrmCustomer(row))}
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td className="py-3 pr-4 align-top">
                              <div className="flex flex-wrap items-center gap-1.5">
                                {row.intentStatus === "HOT_LEAD" ? (
                                  <span className="rounded-full bg-orange-500/15 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-orange-400">
                                    {t("opportunities.badgeHot")}
                                  </span>
                                ) : null}
                                <span>
                                  {(row.lastDetectedIntent ?? row.lastIntent)?.trim() || "—"}
                                </span>
                              </div>
                            </td>
                            <td
                              className={cn(
                                "py-3 pr-4 align-top tabular-nums",
                                scorePresentation.className,
                              )}
                              title={scorePresentation.title}
                            >
                              {scorePresentation.text}
                            </td>
                            <td className="py-3 pr-4 align-top text-muted-foreground">
                              {row.lastIntentAt
                                ? new Date(row.lastIntentAt).toLocaleString(dateLocale, {
                                    day: "2-digit",
                                    month: "short",
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })
                                : "—"}
                            </td>
                            <td className="py-3 text-right align-top">
                              <Button
                                type="button"
                                size="sm"
                                disabled={assumingId === row.id}
                                onClick={() => void handleAssumeOpportunity(row)}
                              >
                                {assumingId === row.id ? (
                                  <>
                                    <Loader2
                                      className="mr-1 inline h-3 w-3 animate-spin"
                                      aria-hidden
                                    />
                                    {t("opportunities.assuming")}
                                  </>
                                ) : (
                                  t("opportunities.recoverWhatsApp")
                                )}
                              </Button>
                            </td>
                          </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                ) : null}
              </CardContent>
            </Card>
          </FeatureGuard>
        </>
      ) : null}

      {mainTab === "metrics" && loading && !data ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : null}

      {mainTab === "metrics" && data ? (
        <>
          <div className="grid grid-cols-1 gap-4 sm:gap-5 md:grid-cols-2 lg:grid-cols-4">
            <Card className={cn(cardClass)}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t("cards.clientsTitle")}</CardTitle>
                <Users className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold tabular-nums tracking-tight">
                  {data.totalClients}
                </div>
                <CardDescription>{t("cards.clientsDesc")}</CardDescription>
              </CardContent>
            </Card>
            <Card className={cn(cardClass)}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t("cards.messagesTodayTitle")}</CardTitle>
                <MessageSquare className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold tabular-nums tracking-tight">
                  {data.messagesToday}
                </div>
                <CardDescription>{t("cards.messagesTodayDesc")}</CardDescription>
              </CardContent>
            </Card>
            <Card className={cn(cardClass)}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t("cards.aiRateTitle")}</CardTitle>
                <Bot className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold tabular-nums tracking-tight text-cyan-400">
                  {data.aiRatePercent != null ? `${data.aiRatePercent}%` : "—"}
                </div>
                <CardDescription>{t("cards.aiRateDesc")}</CardDescription>
              </CardContent>
            </Card>
            <Card className={cn(cardClass)}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t("cards.instanceTitle")}</CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className={cn(
                      "inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium",
                      instanceBadgeClass(data.instanceStatus),
                    )}
                  >
                    {data.instanceStatus === "CONFIGURED" ||
                    data.instanceStatus === "SIMULATED" ||
                    data.instanceStatus === "INCOMPLETE"
                      ? t(`instanceStatus.${data.instanceStatus}`)
                      : data.instanceStatus}
                  </span>
                </div>
                <CardDescription className="pt-2">{t("cards.instanceDesc")}</CardDescription>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:gap-5 lg:max-w-md">
            <FeatureGuard requiredPlan="pro">
              <Card className={cn(cardClass)}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t("cards.upcomingAppointmentsTitle")}
                  </CardTitle>
                  <CalendarClock className="h-4 w-4 text-muted-foreground" aria-hidden />
                </CardHeader>
                <CardContent>
                  <div className="text-3xl font-bold tabular-nums tracking-tight">
                    {upcomingApptError ? "—" : upcomingApptCount ?? "—"}
                  </div>
                  {upcomingApptError ? (
                    <p className="pt-1 text-xs text-destructive" role="alert">
                      {upcomingApptError}
                    </p>
                  ) : (
                    <CardDescription className="pt-2">
                      {t("cards.upcomingAppointmentsDesc")}
                    </CardDescription>
                  )}
                  <Link
                    href="/dashboard/appointments"
                    className="mt-3 inline-block text-sm font-medium text-primary underline-offset-4 hover:underline"
                  >
                    {t("cards.upcomingAppointmentsLink")}
                  </Link>
                </CardContent>
              </Card>
            </FeatureGuard>
          </div>

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("chart.title")}</CardTitle>
              <CardDescription>{t("chart.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent className="pt-2">
              <div
                className="w-full min-w-0 shrink-0"
                style={{ height: areaChartPx, minHeight: areaChartPx }}
              >
                {chartReady ? (
                  <ResponsiveContainer
                    width="100%"
                    height="100%"
                    minWidth={0}
                    minHeight={Math.max(200, areaChartPx - 24)}
                    debounce={50}
                    initialDimension={{ width: 360, height: areaChartPx }}
                  >
                    <AreaChart
                      data={chartData}
                      margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
                    >
                      <defs>
                        <linearGradient id="dashMsgGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.85} />
                          <stop offset="100%" stopColor="#34d399" stopOpacity={0.12} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid
                        stroke="hsl(var(--border))"
                        strokeDasharray="3 3"
                        opacity={0.5}
                      />
                      <XAxis
                        dataKey="label"
                        tick={{ fill: "hsl(var(--muted-foreground))", fontSize: chartTickSize }}
                        interval="preserveStartEnd"
                        tickLine={false}
                        axisLine={{ stroke: "hsl(var(--border))" }}
                      />
                      <YAxis
                        width={isSmallViewport ? 36 : 40}
                        tick={{ fill: "hsl(var(--muted-foreground))", fontSize: chartTickSize }}
                        tickLine={false}
                        axisLine={{ stroke: "hsl(var(--border))" }}
                        allowDecimals={false}
                      />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "hsl(var(--card))",
                          border: "1px solid hsl(var(--border))",
                          borderRadius: "0.75rem",
                        }}
                        labelStyle={{ color: "hsl(var(--foreground))" }}
                        itemStyle={{ color: "#22d3ee" }}
                        formatter={(v) => [v ?? 0, t("chart.tooltipCount")]}
                        labelFormatter={(_, payload) =>
                          payload?.[0]?.payload?.bucketStart
                            ? new Date(
                                payload[0].payload.bucketStart as string,
                              ).toLocaleString(dateLocale)
                            : ""
                        }
                      />
                      <Area
                        type="monotone"
                        dataKey="count"
                        stroke="#22d3ee"
                        strokeWidth={2}
                        fill="url(#dashMsgGrad)"
                        dot={false}
                        activeDot={{ r: 4, fill: "#34d399", stroke: "#22d3ee" }}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : null}
              </div>
            </CardContent>
          </Card>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 lg:gap-5">
            <Card className={cn(cardClass)}>
              <CardHeader>
                <CardTitle>{t("primaryIntents.pieTitle")}</CardTitle>
                <CardDescription>{t("primaryIntents.pieSubtitle")}</CardDescription>
              </CardHeader>
              <CardContent className="pt-2">
                <FeatureGuard requiredPlan="pro">
                  {intentError ? (
                    <p className="text-sm text-destructive" role="alert">
                      {intentError}
                    </p>
                  ) : !intentData ? (
                    <p className="text-sm text-muted-foreground">{t("loading")}</p>
                  ) : totalIntentCount === 0 ? (
                    <p className="text-sm text-muted-foreground">{t("primaryIntents.pieEmpty")}</p>
                  ) : (
                    <div
                      className="w-full min-w-0 shrink-0"
                      style={{ height: lowerChartPx, minHeight: lowerChartPx }}
                    >
                      {chartReady ? (
                        <ResponsiveContainer
                          width="100%"
                          height="100%"
                          minWidth={0}
                          minHeight={Math.max(200, lowerChartPx - 24)}
                          debounce={50}
                          initialDimension={{ width: 360, height: lowerChartPx }}
                        >
                          <PieChart margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
                            <Pie
                              data={pieRows}
                              dataKey="count"
                              nameKey="label"
                              cx="50%"
                              cy="50%"
                              innerRadius={0}
                              outerRadius={pieOuterRadius}
                              paddingAngle={2}
                            >
                              {pieRows.map((_, i) => (
                                <Cell
                                  key={pieRows[i]!.category}
                                  fill={PIE_COLORS[i % PIE_COLORS.length]!}
                                  stroke="hsl(var(--background))"
                                  strokeWidth={2}
                                />
                              ))}
                            </Pie>
                            <Tooltip
                              contentStyle={{
                                backgroundColor: "hsl(var(--card))",
                                border: "1px solid hsl(var(--border))",
                                borderRadius: "0.75rem",
                              }}
                              formatter={(v) => [v ?? 0, t("primaryIntents.pieTooltipCount")]}
                            />
                            <Legend
                              verticalAlign="bottom"
                              wrapperStyle={{ fontSize: isSmallViewport ? 10 : 12 }}
                              formatter={(value) => (
                                <span className="text-muted-foreground text-[10px] sm:text-xs">
                                  {value}
                                </span>
                              )}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      ) : null}
                    </div>
                  )}
                </FeatureGuard>
              </CardContent>
            </Card>

            <Card className={cn(cardClass)}>
              <CardHeader>
                <CardTitle>{t("sentiment.title")}</CardTitle>
                <CardDescription>{t("sentiment.subtitle")}</CardDescription>
              </CardHeader>
              <CardContent className="pt-2">
                <FeatureGuard requiredPlan="pro">
                  {intentError ? (
                    <p className="text-sm text-destructive" role="alert">
                      {intentError}
                    </p>
                  ) : !intentData ? (
                    <p className="text-sm text-muted-foreground">{t("loading")}</p>
                  ) : totalSentimentClassified === 0 ? (
                    <p className="text-sm text-muted-foreground">{t("sentiment.empty")}</p>
                  ) : (
                    <div
                      className="w-full min-w-0 shrink-0"
                      style={{ height: lowerChartPx, minHeight: lowerChartPx }}
                    >
                      {chartReady ? (
                        <ResponsiveContainer
                          width="100%"
                          height="100%"
                          minWidth={0}
                          minHeight={Math.max(200, lowerChartPx - 24)}
                          debounce={50}
                          initialDimension={{ width: 360, height: lowerChartPx }}
                        >
                          <BarChart
                            layout="vertical"
                            data={sentimentBarRows}
                            margin={{ top: 8, right: 16, left: 4, bottom: 8 }}
                          >
                            <CartesianGrid
                              stroke="hsl(var(--border))"
                              strokeDasharray="3 3"
                              horizontal={false}
                              opacity={0.5}
                            />
                            <XAxis
                              type="number"
                              allowDecimals={false}
                              tick={{
                                fill: "hsl(var(--muted-foreground))",
                                fontSize: chartTickSize,
                              }}
                              tickLine={false}
                              axisLine={{ stroke: "hsl(var(--border))" }}
                            />
                            <YAxis
                              type="category"
                              dataKey="label"
                              width={sentimentYAxisW}
                              tick={{
                                fill: "hsl(var(--muted-foreground))",
                                fontSize: chartTickSize,
                              }}
                              tickLine={false}
                              axisLine={false}
                            />
                            <Tooltip
                              cursor={{ fill: "hsl(var(--muted))", opacity: 0.15 }}
                              contentStyle={{
                                backgroundColor: "hsl(var(--card))",
                                border: "1px solid hsl(var(--border))",
                                borderRadius: "0.75rem",
                              }}
                              formatter={(v) => [v ?? 0, t("sentiment.tooltipCount")]}
                            />
                            <Bar dataKey="count" radius={[0, 8, 8, 0]} maxBarSize={28}>
                              {sentimentBarRows.map((r) => (
                                <Cell key={r.sentiment} fill={SENTIMENT_BAR_COLORS[r.sentiment]} />
                              ))}
                            </Bar>
                          </BarChart>
                        </ResponsiveContainer>
                      ) : null}
                    </div>
                  )}
                </FeatureGuard>
              </CardContent>
            </Card>
          </div>

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("aiInsight.title")}</CardTitle>
              <CardDescription>{t("aiInsight.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent>
              <FeatureGuard requiredPlan="pro">
                {intentError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {intentError}
                  </p>
                ) : !intentData ? (
                  <p className="text-sm text-muted-foreground">{t("loading")}</p>
                ) : aiInsight.kind === "empty" ? (
                  <p className="text-sm text-muted-foreground">{t("aiInsight.empty")}</p>
                ) : aiInsight.kind === "stable" ? (
                  <p className="text-sm text-foreground/90 leading-relaxed">{t("aiInsight.stable")}</p>
                ) : (
                  <p className="text-sm font-medium leading-relaxed text-amber-700 dark:text-amber-400">
                    {t("aiInsight.spike", {
                      percent: aiInsight.percent,
                      category: primaryIntentLabel(aiInsight.category),
                    })}
                  </p>
                )}
              </FeatureGuard>
            </CardContent>
          </Card>

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("activities.title")}</CardTitle>
              <CardDescription>{t("activities.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {data.recentInteractions.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  {t("activities.empty")}
                </p>
              ) : (
                <>
                  <div className="hidden overflow-x-auto lg:block">
                    <table className="w-full min-w-[520px] text-sm">
                      <thead>
                        <tr className="border-b border-border text-left text-muted-foreground">
                          <th className="pb-3 pr-4 font-medium">{t("activities.colContact")}</th>
                          <th className="pb-3 pr-4 font-medium">{t("activities.colIntent")}</th>
                          <th className="pb-3 pr-4 font-medium">{t("activities.colWhen")}</th>
                          <th className="pb-3 font-medium">{t("activities.colPreview")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.recentInteractions.map((row) => {
                          const title =
                            row.contactDisplayName?.trim() || formatPhoneDisplay(row.phoneNumber);
                          const pic = row.contactProfilePicUrl?.trim();
                          const preview =
                            row.content.length > 96
                              ? `${row.content.slice(0, 96)}…`
                              : row.content;
                          let when = "—";
                          try {
                            when = new Date(row.timestamp).toLocaleString(dateLocale, {
                              day: "2-digit",
                              month: "short",
                              hour: "2-digit",
                              minute: "2-digit",
                            });
                          } catch {
                            /* ignore */
                          }
                          return (
                            <tr key={row.messageId} className="border-b border-border/60 last:border-0">
                              <td className="py-3 pr-4">
                                <div className="flex items-center gap-3">
                                  <div className="relative h-9 w-9 shrink-0 overflow-hidden rounded-full bg-muted ring-1 ring-border">
                                    {pic ? (
                                      // eslint-disable-next-line @next/next/no-img-element
                                      <img
                                        src={pic}
                                        alt=""
                                        className="h-full w-full object-cover"
                                      />
                                    ) : (
                                      <span className="flex h-full w-full items-center justify-center text-[10px] font-semibold text-muted-foreground">
                                        {contactInitials(row.contactDisplayName, row.phoneNumber)}
                                      </span>
                                    )}
                                  </div>
                                  <div>
                                    <div className="font-medium leading-tight">{title}</div>
                                    {row.contactDisplayName?.trim() ? (
                                      <div className="text-xs text-muted-foreground">
                                        {formatPhoneDisplay(row.phoneNumber)}
                                      </div>
                                    ) : null}
                                  </div>
                                </div>
                              </td>
                              <td className="max-w-[140px] pr-4 align-middle">
                                <span className="inline-block truncate rounded-md bg-muted/80 px-2 py-0.5 text-xs text-cyan-400">
                                  {intentLabel(row.detectedIntent)}
                                </span>
                              </td>
                              <td className="whitespace-nowrap pr-4 align-middle text-muted-foreground">
                                {when}
                              </td>
                              <td className="max-w-[280px] align-middle text-muted-foreground">
                                <span className="line-clamp-2">{preview}</span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  <div className="flex flex-col gap-3 lg:hidden">
                    {data.recentInteractions.map((row) => {
                      const title =
                        row.contactDisplayName?.trim() || formatPhoneDisplay(row.phoneNumber);
                      const pic = row.contactProfilePicUrl?.trim();
                      const preview =
                        row.content.length > 96
                          ? `${row.content.slice(0, 96)}…`
                          : row.content;
                      let when = "—";
                      try {
                        when = new Date(row.timestamp).toLocaleString(dateLocale, {
                          day: "2-digit",
                          month: "short",
                          hour: "2-digit",
                          minute: "2-digit",
                        });
                      } catch {
                        /* ignore */
                      }
                      return (
                        <div
                          key={row.messageId}
                          className="space-y-2 rounded-xl border border-border/60 bg-muted/20 p-4 text-sm"
                        >
                          <div className="flex items-start gap-3">
                            <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-full bg-muted ring-1 ring-border">
                              {pic ? (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img src={pic} alt="" className="h-full w-full object-cover" />
                              ) : (
                                <span className="flex h-full w-full items-center justify-center text-[10px] font-semibold text-muted-foreground">
                                  {contactInitials(row.contactDisplayName, row.phoneNumber)}
                                </span>
                              )}
                            </div>
                            <div className="min-w-0 flex-1 space-y-1">
                              <div className="font-medium leading-tight">{title}</div>
                              {row.contactDisplayName?.trim() ? (
                                <div className="text-xs text-muted-foreground">
                                  {formatPhoneDisplay(row.phoneNumber)}
                                </div>
                              ) : null}
                              <div className="text-xs text-muted-foreground">
                                <span className="font-medium text-foreground/80">
                                  {t("activities.colWhen")}:
                                </span>{" "}
                                {when}
                              </div>
                              <div>
                                <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                                  {t("activities.colIntent")}
                                </span>
                                <div className="mt-0.5">
                                  <span className="inline-block rounded-md bg-muted/80 px-2 py-1 text-xs text-cyan-400">
                                    {intentLabel(row.detectedIntent)}
                                  </span>
                                </div>
                              </div>
                              <div>
                                <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                                  {t("activities.colPreview")}
                                </span>
                                <p className="mt-0.5 text-muted-foreground">{preview}</p>
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}

      <Dialog open={exportUpgradeOpen} onOpenChange={setExportUpgradeOpen}>
        <DialogContent className="max-w-[calc(100vw-2rem)] gap-4 sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{tPlan("exportDialogTitle")}</DialogTitle>
            <DialogDescription>{tPlan("exportDialogDescription")}</DialogDescription>
          </DialogHeader>
          <UpgradePlanPanel requiredPlan="enterprise" className="pb-2" />
        </DialogContent>
      </Dialog>

      <Dialog open={periodDialogOpen} onOpenChange={setPeriodDialogOpen}>
        <DialogContent className="max-w-[calc(100vw-2rem)] gap-4">
          <DialogHeader>
            <DialogTitle>{t("periodCustomDialogTitle")}</DialogTitle>
            <DialogDescription>{t("periodCustomDialogHint")}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <Label htmlFor="dash-from-mobile" className="text-sm text-muted-foreground">
                {t("periodCustomFrom")}
              </Label>
              <Input
                id="dash-from-mobile"
                type="datetime-local"
                value={customFrom}
                onChange={(e) => setCustomFrom(e.target.value)}
                className="min-h-11 w-full"
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="dash-to-mobile" className="text-sm text-muted-foreground">
                {t("periodCustomTo")}
              </Label>
              <Input
                id="dash-to-mobile"
                type="datetime-local"
                value={customTo}
                onChange={(e) => setCustomTo(e.target.value)}
                className="min-h-11 w-full"
              />
            </div>
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              type="button"
              variant="outline"
              size="touch"
              className="w-full sm:w-auto"
              onClick={() => setPeriodDialogOpen(false)}
            >
              {t("periodCustomCancel")}
            </Button>
            <Button
              type="button"
              size="touch"
              className="w-full sm:w-auto"
              onClick={() => {
                setPeriodDialogOpen(false);
                void load();
              }}
            >
              {t("periodCustomApply")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
