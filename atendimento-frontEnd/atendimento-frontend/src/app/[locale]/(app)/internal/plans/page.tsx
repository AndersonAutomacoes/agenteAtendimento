"use client";

import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { FeatureGuard } from "@/components/plan/feature-guard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  getInternalPlansConfig,
  putInternalPlansConfig,
  toUserFacingApiError,
  type PlanConfigFeature,
  type PlanConfigLimit,
} from "@/services/apiService";

const PLANS: Array<"BASIC" | "PRO" | "ULTRA" | "COMERCIAL"> = [
  "BASIC",
  "PRO",
  "ULTRA",
  "COMERCIAL",
];

const FEATURE_ORDER = [
  "DASHBOARD",
  "ANALYTICS",
  "ANALYTICS_EXPORT_CSV",
  "ANALYTICS_EXPORT_PDF",
  "APPOINTMENTS",
  "KNOWLEDGE_BASE",
  "MONITORING",
  "SETTINGS",
] as const;

export default function InternalPlansPage() {
  const t = useTranslations("internalPlans");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);

  const [features, setFeatures] = React.useState<PlanConfigFeature[]>([]);
  const [limits, setLimits] = React.useState<PlanConfigLimit[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);

  const reload = React.useCallback(async () => {
    setLoading(true);
    try {
      const data = await getInternalPlansConfig();
      setFeatures(data.features);
      setLimits(data.limits);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setLoading(false);
    }
  }, [translateApi]);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const setFeatureEnabled = (
    profileLevel: "BASIC" | "PRO" | "ULTRA" | "COMERCIAL",
    featureKey: string,
    enabled: boolean,
  ) => {
    setFeatures((prev) =>
      prev.map((x) =>
        x.profileLevel === profileLevel && x.featureKey === featureKey
          ? { ...x, enabled }
          : x,
      ),
    );
  };

  const setLimit = (
    profileLevel: "BASIC" | "PRO" | "ULTRA" | "COMERCIAL",
    value: string,
  ) => {
    const parsed = Number.parseInt(value, 10);
    const maxAppointmentsPerMonth =
      Number.isNaN(parsed) || parsed <= 0 ? null : parsed;
    setLimits((prev) =>
      prev.map((x) =>
        x.profileLevel === profileLevel ? { ...x, maxAppointmentsPerMonth } : x,
      ),
    );
  };

  const save = async () => {
    setSaving(true);
    try {
      await putInternalPlansConfig({ features, limits });
      toast.success(t("saved"));
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSaving(false);
    }
  };

  return (
    <FeatureGuard requiredPlan="enterprise" requiredProfile="COMERCIAL">
      <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("featuresTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="p-2 text-left">{t("feature")}</th>
                {PLANS.map((p) => (
                  <th key={p} className="p-2 text-center">
                    {p}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {FEATURE_ORDER.map((f) => (
                <tr key={f} className="border-b border-border/50">
                  <td className="p-2 font-medium">{f}</td>
                  {PLANS.map((p) => {
                    const row =
                      features.find(
                        (x) => x.profileLevel === p && x.featureKey === f,
                      ) ?? {
                        profileLevel: p,
                        featureKey: f,
                        enabled: false,
                      };
                    return (
                      <td key={`${p}-${f}`} className="p-2 text-center">
                        <input
                          type="checkbox"
                          checked={row.enabled}
                          onChange={(e) =>
                            setFeatureEnabled(p, f, e.target.checked)
                          }
                          disabled={loading || saving}
                        />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("limitsTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {PLANS.map((p) => {
            const row =
              limits.find((x) => x.profileLevel === p) ?? {
                profileLevel: p,
                maxAppointmentsPerMonth: null,
              };
            return (
              <div key={p} className="flex items-center gap-3">
                <div className="w-20 font-medium">{p}</div>
                <input
                  type="number"
                  className="h-9 w-52 rounded-xl border border-input bg-transparent px-3 text-sm"
                  value={row.maxAppointmentsPerMonth ?? ""}
                  placeholder={t("unlimitedPlaceholder")}
                  onChange={(e) => setLimit(p, e.target.value)}
                  disabled={loading || saving}
                />
                <span className="text-xs text-muted-foreground">
                  {t("appointmentsPerMonth")}
                </span>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <div className="flex gap-2">
        <Button type="button" variant="outline" onClick={() => void reload()}>
          {t("reload")}
        </Button>
        <Button type="button" onClick={() => void save()} disabled={saving}>
          {saving ? t("saving") : t("save")}
        </Button>
      </div>
      </div>
    </FeatureGuard>
  );
}
