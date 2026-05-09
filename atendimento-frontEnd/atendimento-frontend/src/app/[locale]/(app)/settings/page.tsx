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
  postTenantEvolutionPairingQr,
  putTenantSettings,
  toUserFacingApiError,
  type ProfileLevel,
  type WhatsAppProviderType,
} from "@/services/apiService";

import { TENANT_STORAGE_KEY } from "@/lib/auth-session";

const selectClassName = cn(
  "flex h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
  "disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
);

export default function SettingsPage() {
  const t = useTranslations("settings");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const { setTier, profileLevel: sessionProfileLevel, featuresHydrated } = usePlan();

  const [tenantId, setTenantId] = React.useState("");
  const [profileLevel, setProfileLevel] = React.useState<ProfileLevel>("BASIC");

  const resolvedPlanLevel = React.useMemo(
    () => (featuresHydrated ? sessionProfileLevel : profileLevel),
    [featuresHydrated, sessionProfileLevel, profileLevel],
  );

  const planTierTitle = React.useMemo(() => {
    switch (resolvedPlanLevel) {
      case "PRO":
        return t("planTierPro");
      case "ULTRA":
        return t("planTierUltra");
      case "COMERCIAL":
        return t("planTierCommercial");
      default:
        return t("planTierBasic");
    }
  }, [resolvedPlanLevel, t]);

  const [personality, setPersonality] = React.useState("");
  const [whatsappProviderType, setWhatsappProviderType] =
    React.useState<WhatsAppProviderType>("SIMULATED");
  const [whatsappApiKey, setWhatsappApiKey] = React.useState("");
  const [whatsappInstanceId, setWhatsappInstanceId] = React.useState("");
  const [whatsappBaseUrl, setWhatsappBaseUrl] = React.useState("");
  /** ID do calendário Google do tenant (`tenant_configuration.google_calendar_id`). */
  const [googleCalendarId, setGoogleCalendarId] = React.useState("");
  const [showAccessToken, setShowAccessToken] = React.useState(false);
  const [loadingInitial, setLoadingInitial] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [pairingQrLoading, setPairingQrLoading] = React.useState(false);
  const [pairingQrSrc, setPairingQrSrc] = React.useState<string | null>(null);

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
        setGoogleCalendarId(data.googleCalendarId ?? "");
      })
      .catch((e: unknown) => {
        if (!cancelled) toast.error(toUserFacingApiError(e, translateApi));
      })
      .finally(() => {
      if (!cancelled) {
        setLoadingInitial(false);
        setPairingQrSrc(null);
      }
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
        googleCalendarId: googleCalendarId.trim(),
        establishmentName: null,
        businessAddress: null,
        businessMapsUrl: null,
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
        googleCalendarId: googleCalendarId.trim(),
        establishmentName: null,
        businessAddress: null,
        businessMapsUrl: null,
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
      googleCalendarId: googleCalendarId.trim(),
      establishmentName: null,
      businessAddress: null,
      businessMapsUrl: null,
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
  /** BASIC/PRO/ULTRA: integração técnica só leitura no portal (backoffice). COMERCIAL: edição permitida aqui. */
  const whatsappFieldsLocked = resolvedPlanLevel !== "COMERCIAL";
  const whatsappIntegrationDisabled = busy || whatsappFieldsLocked;

  const fetchEvolutionQr = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      toast.error(t("toastNeedAccount"));
      return;
    }
    setPairingQrLoading(true);
    try {
      const r = await postTenantEvolutionPairingQr(tid);
      const src = r.qrcodeDataUri || (r.qrcodePlainBase64 ? `data:image/png;base64,${r.qrcodePlainBase64}` : "");
      if (!src) {
        toast.error(tApi("errors.serverUnavailable"));
        return;
      }
      setPairingQrSrc(src);
      toast.success(t("evoPairingLoaded"));
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setPairingQrLoading(false);
    }
  }, [tenantId, t, tApi, translateApi]);

  return (
    <FeatureGuard requiredPlan="pro" requiredFeature="SETTINGS">
      <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <h2 className="sr-only">{t("pageContentSection")}</h2>
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
        <CardContent className="space-y-2">
          <Label htmlFor="current-plan-tier">{t("planLabel")}</Label>
          <Input
            id="current-plan-tier"
            readOnly
            aria-readonly
            value={planTierTitle}
            className="rounded-xl bg-muted/50 font-semibold text-foreground"
          />
          <p className="font-mono text-xs text-muted-foreground">{resolvedPlanLevel}</p>
          <p className="text-xs text-muted-foreground">{t("planCodeHint")}</p>
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
          <CardTitle>{t("sectionCalendar")}</CardTitle>
          <CardDescription>{t("sectionCalendarDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          <Label htmlFor="google-calendar-id">{t("googleCalendarId")}</Label>
          <Input
            id="google-calendar-id"
            placeholder="exemplo@group.calendar.google.com…"
            value={googleCalendarId}
            onChange={(e) => setGoogleCalendarId(e.target.value)}
            disabled={busy}
            autoComplete="off"
            className="rounded-xl font-mono text-sm"
          />
          <p className="text-xs text-muted-foreground">{t("googleCalendarIdHint")}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("sectionChannel")}</CardTitle>
          {whatsappFieldsLocked ? (
            <CardDescription>{t("whatsappIntegrationReadOnlyDesc")}</CardDescription>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="provider">{t("provider")}</Label>
            <select
              id="provider"
              className={selectClassName}
              value={whatsappProviderType}
              disabled={whatsappIntegrationDisabled}
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
                  disabled={whatsappIntegrationDisabled}
                  readOnly={whatsappFieldsLocked}
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
                    disabled={whatsappIntegrationDisabled}
                    readOnly={whatsappFieldsLocked}
                    autoComplete="off"
                    className="rounded-xl"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    className="shrink-0 rounded-xl"
                    disabled={whatsappIntegrationDisabled}
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
                  disabled={whatsappIntegrationDisabled}
                  readOnly={whatsappFieldsLocked}
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
                  disabled={whatsappIntegrationDisabled}
                  readOnly={whatsappFieldsLocked}
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
                  disabled={whatsappIntegrationDisabled}
                  readOnly={whatsappFieldsLocked}
                  autoComplete="off"
                  className="rounded-xl"
                />
                <p className="text-xs text-muted-foreground">{t("evoKeyHint")}</p>
              </div>
              <div className="space-y-2 border-t border-border pt-4">
                <Button
                  type="button"
                  variant="secondary"
                  className="rounded-xl"
                  disabled={
                    loadingInitial || saving || pairingQrLoading || !tenantId.trim()
                  }
                  onClick={() => void fetchEvolutionQr()}
                >
                  {pairingQrLoading ? t("evoPairingLoading") : t("evoPairingButton")}
                </Button>
                <p className="text-xs text-muted-foreground">{t("evoPairingHint")}</p>
                {pairingQrSrc ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={pairingQrSrc}
                    alt={t("evoPairingAlt")}
                    className="max-w-[260px] rounded-xl border border-border bg-muted/30 p-2"
                  />
                ) : null}
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
