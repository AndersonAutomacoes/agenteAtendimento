"use client";

import { CalendarClock, Clock, Loader2 } from "lucide-react";
import * as React from "react";
import { useLocale, useTranslations } from "next-intl";

import { CustomerRecordDialog } from "@/components/crm/customer-record-dialog";
import { FeatureGuard } from "@/components/plan/feature-guard";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import {
  getTenantAppointments,
  toUserFacingApiError,
  type TenantAppointmentRow,
  type TenantAppointmentScope,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

export default function AppointmentsPage() {
  const t = useTranslations("appointments");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const [tenantId, setTenantId] = React.useState("");
  const [scope, setScope] = React.useState<TenantAppointmentScope>("all");
  const [rows, setRows] = React.useState<TenantAppointmentRow[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [fichaOpen, setFichaOpen] = React.useState(false);
  const [fichaConversationId, setFichaConversationId] = React.useState<string | null>(null);
  const [fichaTitle, setFichaTitle] = React.useState<string | undefined>(undefined);

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

  React.useEffect(() => {
    const tid = tenantId.trim();
    if (!tid) {
      setRows([]);
      setError(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    void (async () => {
      try {
        const res = await getTenantAppointments(tid, scope);
        if (!cancelled) {
          setRows(res.appointments);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setRows([]);
          setError(toUserFacingApiError(e, translateApi));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tenantId, scope, translateApi]);

  const formatDateTime = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString(dateLocale, {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "—";
    }
  };

  return (
    <FeatureGuard requiredPlan="pro">
      <CustomerRecordDialog
        open={fichaOpen}
        onOpenChange={setFichaOpen}
        tenantId={tenantId}
        conversationId={fichaConversationId}
        titleFallback={fichaTitle}
      />
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6 pb-10 sm:px-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
          <p className="text-muted-foreground">{t("subtitle")}</p>
          <div className="mt-4 max-w-md space-y-1">
            <Label className="text-muted-foreground">{t("accountId")}</Label>
            <p
              className={cn(
                "min-h-9 font-mono text-base font-semibold tracking-tight text-foreground sm:text-lg",
                !tenantId.trim() && "font-normal text-muted-foreground",
              )}
            >
              {tenantId.trim() || "—"}
            </p>
            {!tenantId.trim() ? (
              <p className="pt-1 text-xs text-amber-600 dark:text-amber-400/90">
                {t("needAccount")}
              </p>
            ) : null}
          </div>
        </div>

        <Card className="rounded-2xl border-border/70 shadow-md shadow-black/5 dark:shadow-black/25">
          <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="flex items-center gap-2 text-lg">
                <CalendarClock className="h-5 w-5 text-primary" aria-hidden />
                {t("title")}
              </CardTitle>
              <CardDescription>{t("subtitle")}</CardDescription>
            </div>
            <div
              className="flex rounded-lg bg-muted/50 p-0.5 dark:bg-muted/30"
              role="tablist"
              aria-label={t("filterAria")}
            >
              {(
                [
                  ["all", t("filterAll")] as const,
                  ["today", t("filterToday")] as const,
                  ["future", t("filterFuture")] as const,
                ] satisfies [TenantAppointmentScope, string][]
              ).map(([key, label]) => (
                <Button
                  key={key}
                  type="button"
                  role="tab"
                  variant="ghost"
                  size="sm"
                  aria-selected={scope === key}
                  disabled={!tenantId.trim() || loading}
                  className={cn(
                    "rounded-md px-3 text-xs font-medium",
                    scope === key
                      ? "bg-background text-foreground shadow-sm dark:bg-card"
                      : "text-muted-foreground hover:text-foreground",
                  )}
                  onClick={() => setScope(key)}
                >
                  {label}
                </Button>
              ))}
            </div>
          </CardHeader>
          <CardContent>
            {error ? (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            ) : null}
            {!tenantId.trim() ? null : loading ? (
              <div className="flex items-center gap-2 py-10 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
                <span>{t("loading")}</span>
              </div>
            ) : rows.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">{t("empty")}</p>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-border/80">
                <table className="w-full min-w-[640px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border bg-muted/40 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      <th className="px-4 py-3">{t("colWhen")}</th>
                      <th className="px-4 py-3">{t("colClient")}</th>
                      <th className="px-4 py-3">{t("colService")}</th>
                      <th className="px-4 py-3">{t("colStatus")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr
                        key={row.id}
                        className={cn(
                          "border-b border-border/60 transition-colors last:border-0",
                          row.todayHighlight &&
                            "bg-primary/[0.06] dark:bg-primary/10",
                        )}
                      >
                        <td className="px-4 py-3 align-middle">
                          <span className="inline-flex items-center gap-2 tabular-nums">
                            {row.todayHighlight ? (
                              <Clock
                                className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
                                aria-label={t("todayHint")}
                              />
                            ) : null}
                            {formatDateTime(row.startsAt)}
                          </span>
                        </td>
                        <td className="px-4 py-3 align-middle font-medium">
                          {row.conversationId ? (
                            <button
                              type="button"
                              className="text-left text-primary underline-offset-4 hover:underline"
                              onClick={() => {
                                setFichaConversationId(row.conversationId);
                                setFichaTitle(row.clientName || undefined);
                                setFichaOpen(true);
                              }}
                            >
                              {row.clientName || "—"}
                            </button>
                          ) : (
                            <span>{row.clientName || "—"}</span>
                          )}
                        </td>
                        <td className="px-4 py-3 align-middle text-muted-foreground">
                          {row.serviceName || "—"}
                        </td>
                        <td className="px-4 py-3 align-middle">
                          <span
                            className={cn(
                              "inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium",
                              row.todayHighlight
                                ? "border-amber-500/50 bg-amber-500/10 text-amber-900 dark:text-amber-100"
                                : "border-border bg-muted/40 text-foreground",
                            )}
                          >
                            {row.statusLabel}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </FeatureGuard>
  );
}
