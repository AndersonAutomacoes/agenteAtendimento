"use client";

import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

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
import { Textarea } from "@/components/ui/textarea";
import { usePlan } from "@/components/plan/plan-provider";
import { FeatureGuard } from "@/components/plan/feature-guard";
import { PwaInstallCard } from "@/components/pwa/pwa-install-card";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import { cn } from "@/lib/utils";
import {
  getTenantSettings,
  putTenantSettings,
  toUserFacingApiError,
  type WhatsAppProviderType,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

const selectClassName = cn(
  "flex h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
  "disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
);

export default function SettingsPage() {
  const t = useTranslations("settings");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const { setTier } = usePlan();

  const [tenantId, setTenantId] = React.useState("");
  const [profileLevel, setProfileLevel] = React.useState<string>("BASIC");

  const [personality, setPersonality] = React.useState("");
  const [whatsappProviderType, setWhatsappProviderType] =
    React.useState<WhatsAppProviderType>("SIMULATED");
  const [whatsappApiKey, setWhatsappApiKey] = React.useState("");
  const [whatsappInstanceId, setWhatsappInstanceId] = React.useState("");
  const [whatsappBaseUrl, setWhatsappBaseUrl] = React.useState("");
  const [showAccessToken, setShowAccessToken] = React.useState(false);
  const [loadingInitial, setLoadingInitial] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

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
      return;
    }
    let cancelled = false;
    setLoadingInitial(true);
    void getTenantSettings(tid)
      .then((data) => {
        if (cancelled) return;
        setProfileLevel(data.profileLevel);
        setTier(mapProfileLevelToPlanTier(data.profileLevel));
        setPersonality(data.systemPrompt);
        setWhatsappProviderType(data.whatsappProviderType);
        setWhatsappApiKey(data.whatsappApiKey ?? "");
        setWhatsappInstanceId(data.whatsappInstanceId ?? "");
        setWhatsappBaseUrl(data.whatsappBaseUrl ?? "");
      })
      .catch((e: unknown) => {
        if (!cancelled) toast.error(toUserFacingApiError(e, translateApi));
      })
      .finally(() => {
      if (!cancelled) setLoadingInitial(false);
    });
    return () => {
      cancelled = true;
    };
  }, [tenantId, setTier, translateApi]);

  const buildSettingsPayload = () => {
    const tid = tenantId.trim();
    const type = whatsappProviderType;
    if (type === "SIMULATED") {
      return {
        tenantId: tid,
        systemPrompt: personality,
        whatsappProviderType: type,
        /* strings vazias: backend limpa credenciais ao gravar */
        whatsappApiKey: "",
        whatsappInstanceId: "",
        whatsappBaseUrl: "",
        googleCalendarId: null,
        establishmentName: null,
        businessAddress: null,
        openingHours: null,
        businessContacts: null,
        businessFacilities: null,
        defaultAppointmentMinutes: 30,
        billingCompliant: true,
        calendarAccessNotes: null,
        spreadsheetUrl: null,
        whatsappBusinessNumber: null,
      };
    }
    if (type === "META") {
      return {
        tenantId: tid,
        systemPrompt: personality,
        whatsappProviderType: type,
        whatsappApiKey: whatsappApiKey || null,
        whatsappInstanceId: whatsappInstanceId || null,
        whatsappBaseUrl: null as string | null,
        googleCalendarId: null,
        establishmentName: null,
        businessAddress: null,
        openingHours: null,
        businessContacts: null,
        businessFacilities: null,
        defaultAppointmentMinutes: 30,
        billingCompliant: true,
        calendarAccessNotes: null,
        spreadsheetUrl: null,
        whatsappBusinessNumber: null,
      };
    }
    return {
      tenantId: tid,
      systemPrompt: personality,
      whatsappProviderType: type,
      whatsappApiKey: whatsappApiKey || null,
      whatsappInstanceId: whatsappInstanceId || null,
      whatsappBaseUrl: whatsappBaseUrl || null,
      googleCalendarId: null,
      establishmentName: null,
      businessAddress: null,
      openingHours: null,
      businessContacts: null,
      businessFacilities: null,
      defaultAppointmentMinutes: 30,
      billingCompliant: true,
      calendarAccessNotes: null,
      spreadsheetUrl: null,
      whatsappBusinessNumber: null,
    };
  };

  const save = async () => {
    const tid = tenantId.trim();
    if (!tid) {
      toast.error(t("toastNeedAccount"));
      return;
    }
    setSaving(true);
    try {
      await putTenantSettings(buildSettingsPayload());
      toast.success(t("toastSaved"));
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSaving(false);
    }
  };

  const busy = saving || loadingInitial;

  return (
    <FeatureGuard requiredPlan="pro" requiredFeature="SETTINGS">
      <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">{t("subtitle")}</p>
      </div>

      <PwaInstallCard />

      <div className="space-y-1">
        <Label className="text-muted-foreground">{t("accountId")}</Label>
        <p
          className={cn(
            "min-h-9 text-base font-semibold tracking-tight text-foreground sm:text-lg",
            !tenantId.trim() && "font-normal text-muted-foreground",
          )}
          aria-label={t("accountId")}
        >
          {tenantId.trim() || "—"}
        </p>
        <p className="text-xs text-muted-foreground">{t("tenantHint")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("sectionPlan")}</CardTitle>
          <CardDescription>{t("sectionPlanDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-1">
          <p className="text-sm text-muted-foreground">{t("planLabel")}</p>
          <p className="text-lg font-semibold">
            {profileLevel === "ULTRA" || profileLevel === "COMERCIAL"
              ? t("planUltra")
              : profileLevel === "PRO"
                ? t("planPro")
                : t("planBasic")}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("sectionPersona")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <Label htmlFor="personality">{t("personality")}</Label>
          <Textarea
            id="personality"
            placeholder={t("personalityPlaceholder")}
            value={personality}
            onChange={(e) => setPersonality(e.target.value)}
            rows={6}
            disabled={busy}
            className="rounded-xl"
          />
          <p className="text-xs text-muted-foreground">{t("personalityHint")}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("sectionChannel")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="provider">{t("provider")}</Label>
            <select
              id="provider"
              className={selectClassName}
              value={whatsappProviderType}
              disabled={busy}
              onChange={(e) =>
                setWhatsappProviderType(e.target.value as WhatsAppProviderType)
              }
            >
              <option value="SIMULATED">{t("optionSimulated")}</option>
              <option value="META">{t("optionMeta")}</option>
              <option value="EVOLUTION">{t("optionEvolution")}</option>
            </select>
            <p className="text-xs text-muted-foreground">{t("providerHint")}</p>
          </div>

          {whatsappProviderType === "SIMULATED" && (
            <p className="rounded-xl border border-border bg-card/50 px-4 py-3 text-sm text-muted-foreground">
              {t("simulatedBlurb")}
            </p>
          )}

          {whatsappProviderType === "META" && (
            <div className="space-y-6 rounded-xl border border-border bg-card/30 p-4">
              <div className="space-y-2">
                <Label htmlFor="phoneId">{t("metaPhoneLabel")}</Label>
                <Input
                  id="phoneId"
                  placeholder={t("metaPhonePlaceholder")}
                  value={whatsappInstanceId}
                  onChange={(e) => setWhatsappInstanceId(e.target.value)}
                  disabled={busy}
                  autoComplete="off"
                  className="rounded-xl"
                />
                <p className="text-xs text-muted-foreground">{t("metaPhoneHint")}</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="accessToken">{t("metaTokenLabel")}</Label>
                <div className="flex gap-2">
                  <Input
                    id="accessToken"
                    type={showAccessToken ? "text" : "password"}
                    placeholder={t("metaTokenPlaceholder")}
                    value={whatsappApiKey}
                    onChange={(e) => setWhatsappApiKey(e.target.value)}
                    disabled={busy}
                    autoComplete="off"
                    className="rounded-xl"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    className="shrink-0 rounded-xl"
                    disabled={busy}
                    onClick={() => setShowAccessToken((v) => !v)}
                  >
                    {showAccessToken ? t("metaHide") : t("metaShow")}
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">{t("metaTokenHint")}</p>
              </div>
            </div>
          )}

          {whatsappProviderType === "EVOLUTION" && (
            <div className="space-y-6 rounded-xl border border-border bg-card/30 p-4">
              <div className="space-y-2">
                <Label htmlFor="evoUrl">{t("evoUrlLabel")}</Label>
                <Input
                  id="evoUrl"
                  placeholder={t("evoUrlPlaceholder")}
                  value={whatsappBaseUrl}
                  onChange={(e) => setWhatsappBaseUrl(e.target.value)}
                  disabled={busy}
                  autoComplete="off"
                  className="rounded-xl"
                />
                <p className="text-xs text-muted-foreground">{t("evoUrlHint")}</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="evoInstance">{t("evoInstanceLabel")}</Label>
                <Input
                  id="evoInstance"
                  placeholder={t("evoInstancePlaceholder")}
                  value={whatsappInstanceId}
                  onChange={(e) => setWhatsappInstanceId(e.target.value)}
                  disabled={busy}
                  autoComplete="off"
                  className="rounded-xl"
                />
                <p className="text-xs text-muted-foreground">{t("evoInstanceHint")}</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="evoKey">{t("evoKeyLabel")}</Label>
                <Input
                  id="evoKey"
                  type="password"
                  placeholder={t("evoKeyPlaceholder")}
                  value={whatsappApiKey}
                  onChange={(e) => setWhatsappApiKey(e.target.value)}
                  disabled={busy}
                  autoComplete="off"
                  className="rounded-xl"
                />
                <p className="text-xs text-muted-foreground">{t("evoKeyHint")}</p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          onClick={() => void save()}
          disabled={busy}
          className="rounded-xl"
        >
          {saving ? t("saving") : loadingInitial ? t("loading") : t("save")}
        </Button>
      </div>
      </div>
    </FeatureGuard>
  );
}
