"use client";

import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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

  const [tenantId, setTenantId] = React.useState("");
  const [personality, setPersonality] = React.useState("");
  const [whatsappProviderType, setWhatsappProviderType] =
    React.useState<WhatsAppProviderType>("SIMULATED");
  const [whatsappApiKey, setWhatsappApiKey] = React.useState("");
  const [whatsappInstanceId, setWhatsappInstanceId] = React.useState("");
  const [whatsappBaseUrl, setWhatsappBaseUrl] = React.useState("");
  const [showAccessToken, setShowAccessToken] = React.useState(false);
  const [loadingInitial, setLoadingInitial] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

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
        setPersonality(data.systemPrompt);
        setWhatsappProviderType(data.whatsappProviderType);
        setWhatsappApiKey(data.whatsappApiKey ?? "");
        setWhatsappInstanceId(data.whatsappInstanceId ?? "");
        setWhatsappBaseUrl(data.whatsappBaseUrl ?? "");
      })
      .catch((e: unknown) => {
        toast.error(toUserFacingApiError(e, translateApi));
      })
      .finally(() => {
        if (!cancelled) setLoadingInitial(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, translateApi]);

  const buildPayload = () => {
    const tid = tenantId.trim();
    const p = personality;
    const type = whatsappProviderType;
    if (type === "SIMULATED") {
      return {
        tenantId: tid,
        systemPrompt: p,
        whatsappProviderType: type,
        whatsappApiKey: "",
        whatsappInstanceId: "",
        whatsappBaseUrl: "",
      };
    }
    if (type === "META") {
      return {
        tenantId: tid,
        systemPrompt: p,
        whatsappProviderType: type,
        whatsappApiKey: whatsappApiKey,
        whatsappInstanceId: whatsappInstanceId,
        whatsappBaseUrl: "",
      };
    }
    return {
      tenantId: tid,
      systemPrompt: p,
      whatsappProviderType: type,
      whatsappApiKey: whatsappApiKey,
      whatsappInstanceId: whatsappInstanceId,
      whatsappBaseUrl: whatsappBaseUrl,
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
      await putTenantSettings(buildPayload());
      toast.success(t("toastSaved"));
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSaving(false);
    }
  };

  const busy = saving || loadingInitial;

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="tenantId">{t("accountId")}</Label>
        <Input
          id="tenantId"
          placeholder={t("placeholderTenant")}
          value={tenantId}
          onChange={(e) => persistTenant(e.target.value)}
          autoComplete="off"
          className="rounded-xl"
        />
        <p className="text-xs text-muted-foreground">{t("tenantHint")}</p>
      </div>

      <div className="space-y-2">
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
      </div>

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
  );
}
