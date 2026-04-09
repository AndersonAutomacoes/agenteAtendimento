"use client";

import { Activity, Bot, MessageSquare, RefreshCw, Users } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import {
  getAnalyticsIntents,
  getDashboardSummary,
  toUserFacingApiError,
  type AnalyticsIntentsResponse,
  type ConversationSentiment,
  type DashboardRange,
  type DashboardSummary,
  type PrimaryIntentCategory,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

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

type ChartRow = { bucketStart: string; count: number; label: string };

const PIE_COLORS = ["#22d3ee", "#34d399", "#a78bfa", "#fb7185", "#fbbf24"];

const SENTIMENT_ORDER: readonly ConversationSentiment[] = ["POSITIVO", "NEUTRO", "NEGATIVO"];

const SENTIMENT_BAR_COLORS: Record<ConversationSentiment, string> = {
  POSITIVO: "#34d399",
  NEUTRO: "hsl(var(--muted-foreground))",
  NEGATIVO: "#fb7185",
};

const AI_INSIGHT_MIN_PERCENT = 5;

function intentDaysForRange(range: DashboardRange): number {
  if (range === "day") return 1;
  if (range === "week") return 7;
  return 30;
}

export function DashboardPanel() {
  const t = useTranslations("dashboard");
  const tApi = useTranslations("api");
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const [tenantId, setTenantId] = React.useState("");
  const [range, setRange] = React.useState<DashboardRange>("day");
  const [data, setData] = React.useState<DashboardSummary | null>(null);
  const [intentData, setIntentData] = React.useState<AnalyticsIntentsResponse | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [intentError, setIntentError] = React.useState<string | null>(null);

  React.useEffect(() => {
    try {
      const v = localStorage.getItem(TENANT_STORAGE_KEY);
      if (v) setTenantId(v);
    } catch {
      /* ignore */
    }
  }, []);

  const persistTenant = (value: string) => {
    setTenantId(value);
    try {
      localStorage.setItem(TENANT_STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  };

  const load = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      setData(null);
      setIntentData(null);
      setError(null);
      setIntentError(null);
      return;
    }
    setLoading(true);
    setError(null);
    setIntentError(null);
    const days = intentDaysForRange(range);
    const [sumRes, intRes] = await Promise.allSettled([
      getDashboardSummary(tid, range),
      getAnalyticsIntents(tid, days),
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
    setLoading(false);
  }, [tenantId, range, tApi]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const chartData: ChartRow[] = React.useMemo(() => {
    if (!data) return [];
    return data.series.map((p) => {
      let label: string;
      try {
        const d = new Date(p.bucketStart);
        if (range === "day") {
          label = d.toLocaleTimeString(dateLocale, { hour: "2-digit", minute: "2-digit" });
        } else {
          label = d.toLocaleDateString(dateLocale, { day: "2-digit", month: "short" });
        }
      } catch {
        label = p.bucketStart;
      }
      return { ...p, label };
    });
  }, [data, range, dateLocale]);

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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <Label htmlFor="dash-tenant">{t("accountId")}</Label>
          <div className="flex max-w-md flex-col gap-2 sm:flex-row sm:items-center">
            <Input
              id="dash-tenant"
              value={tenantId}
              onChange={(e) => persistTenant(e.target.value)}
              placeholder={t("placeholderTenant")}
              className="sm:flex-1"
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="shrink-0"
              onClick={() => void load()}
              disabled={loading || !tenantId.trim()}
              aria-label={t("refresh")}
            >
              <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">{t("tenantHint")}</p>
        </div>

        <div className="flex flex-wrap gap-2" role="tablist" aria-label={t("chart.periodAria")}>
          {(
            [
              { value: "day" as const, label: "periodDay" as const },
              { value: "week" as const, label: "periodWeek" as const },
              { value: "month" as const, label: "periodMonth" as const },
            ] as const
          ).map(({ value, label }) => (
            <Button
              key={value}
              type="button"
              size="sm"
              variant={range === value ? "default" : "outline"}
              onClick={() => setRange(value)}
              className={cn(
                range === value &&
                  "bg-primary text-primary-foreground shadow-md shadow-cyan-500/20",
              )}
            >
              {t(label)}
            </Button>
          ))}
        </div>
      </div>

      {!tenantId.trim() ? (
        <p className="text-sm text-amber-600 dark:text-amber-400">{t("needAccount")}</p>
      ) : null}

      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      {loading && !data ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : null}

      {data ? (
        <>
          <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-4">
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

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("chart.title")}</CardTitle>
              <CardDescription>{t("chart.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent className="h-[320px] w-full min-w-0 pt-2">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="dashMsgGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.85} />
                      <stop offset="100%" stopColor="#34d399" stopOpacity={0.12} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" opacity={0.5} />
                  <XAxis
                    dataKey="label"
                    tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
                    tickLine={false}
                    axisLine={{ stroke: "hsl(var(--border))" }}
                  />
                  <YAxis
                    width={40}
                    tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
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
                        ? new Date(payload[0].payload.bucketStart as string).toLocaleString(dateLocale)
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
            </CardContent>
          </Card>

          <div className="grid gap-5 md:grid-cols-2">
            <Card className={cn(cardClass)}>
              <CardHeader>
                <CardTitle>{t("primaryIntents.pieTitle")}</CardTitle>
                <CardDescription>{t("primaryIntents.pieSubtitle")}</CardDescription>
              </CardHeader>
              <CardContent className="h-[300px] w-full min-w-0 pt-2">
                {intentError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {intentError}
                  </p>
                ) : !intentData ? (
                  <p className="text-sm text-muted-foreground">{t("loading")}</p>
                ) : totalIntentCount === 0 ? (
                  <p className="text-sm text-muted-foreground">{t("primaryIntents.pieEmpty")}</p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
                      <Pie
                        data={pieRows}
                        dataKey="count"
                        nameKey="label"
                        cx="50%"
                        cy="50%"
                        innerRadius={0}
                        outerRadius={96}
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
                        formatter={(value) => <span className="text-muted-foreground text-xs">{value}</span>}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </CardContent>
            </Card>

            <Card className={cn(cardClass)}>
              <CardHeader>
                <CardTitle>{t("sentiment.title")}</CardTitle>
                <CardDescription>{t("sentiment.subtitle")}</CardDescription>
              </CardHeader>
              <CardContent className="h-[300px] w-full min-w-0 pt-2">
                {intentError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {intentError}
                  </p>
                ) : !intentData ? (
                  <p className="text-sm text-muted-foreground">{t("loading")}</p>
                ) : totalSentimentClassified === 0 ? (
                  <p className="text-sm text-muted-foreground">{t("sentiment.empty")}</p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
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
                        tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
                        tickLine={false}
                        axisLine={{ stroke: "hsl(var(--border))" }}
                      />
                      <YAxis
                        type="category"
                        dataKey="label"
                        width={100}
                        tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
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
                )}
              </CardContent>
            </Card>
          </div>

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("aiInsight.title")}</CardTitle>
              <CardDescription>{t("aiInsight.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent>
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
            </CardContent>
          </Card>

          <Card className={cn(cardClass)}>
            <CardHeader>
              <CardTitle>{t("activities.title")}</CardTitle>
              <CardDescription>{t("activities.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto">
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
                  {data.recentInteractions.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="py-8 text-center text-muted-foreground">
                        {t("activities.empty")}
                      </td>
                    </tr>
                  ) : (
                    data.recentInteractions.map((row) => {
                      const title = row.contactDisplayName?.trim() || formatPhoneDisplay(row.phoneNumber);
                      const pic = row.contactProfilePicUrl?.trim();
                      const preview =
                        row.content.length > 96 ? `${row.content.slice(0, 96)}…` : row.content;
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
                    })
                  )}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  );
}
