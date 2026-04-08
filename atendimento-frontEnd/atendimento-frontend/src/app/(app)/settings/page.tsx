"use client";

import * as React from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  getTenantSettings,
  putTenantSettings,
  type WhatsAppProviderType,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

const selectClassName = cn(
  "flex h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
  "disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
);

export default function SettingsPage() {
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
        const msg = e instanceof Error ? e.message : "Falha ao carregar";
        toast.error(msg);
      })
      .finally(() => {
        if (!cancelled) setLoadingInitial(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId]);

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
      toast.error("Indique o tenantId antes de salvar.");
      return;
    }
    setSaving(true);
    try {
      await putTenantSettings(buildPayload());
      toast.success("Configurações de canal atualizadas com sucesso!");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Falha ao salvar";
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const busy = saving || loadingInitial;

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Configurações do bot
        </h1>
        <p className="text-muted-foreground">
          Personalidade do assistente e integração WhatsApp por tenant.
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="tenantId">Tenant ID</Label>
        <Input
          id="tenantId"
          placeholder="ex.: tenant-demo"
          value={tenantId}
          onChange={(e) => persistTenant(e.target.value)}
          autoComplete="off"
          className="rounded-xl"
        />
        <p className="text-xs text-muted-foreground">
          Guardado localmente neste browser (mesmo valor que na base de
          conhecimento e no chat de teste). Ao alterar, as definições são
          recarregadas do servidor.
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="personality">Personalidade do Bot</Label>
        <Textarea
          id="personality"
          placeholder="Descreva o tom e o contexto do assistente…"
          value={personality}
          onChange={(e) => setPersonality(e.target.value)}
          rows={6}
          disabled={busy}
          className="rounded-xl"
        />
        <p className="text-xs text-muted-foreground">
          Define o system prompt enviado ao modelo de IA (persona do canal).
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="provider">Tipo de provedor WhatsApp</Label>
        <select
          id="provider"
          className={selectClassName}
          value={whatsappProviderType}
          disabled={busy}
          onChange={(e) =>
            setWhatsappProviderType(e.target.value as WhatsAppProviderType)
          }
        >
          <option value="SIMULATED">SIMULATED — Simulado (log)</option>
          <option value="META">META — WhatsApp Oficial (Cloud API)</option>
          <option value="EVOLUTION">EVOLUTION — Evolution API</option>
        </select>
        <p className="text-xs text-muted-foreground">
          Simulado regista respostas em log; Meta e Evolution enviam mensagens
          reais quando o backend estiver configurado.
        </p>
      </div>

      {whatsappProviderType === "SIMULATED" && (
        <p className="rounded-xl border border-border bg-card/50 px-4 py-3 text-sm text-muted-foreground">
          Nenhuma credencial necessária. O canal não envia mensagens reais —
          útil para desenvolvimento e testes.
        </p>
      )}

      {whatsappProviderType === "META" && (
        <div className="space-y-6 rounded-xl border border-border bg-card/30 p-4">
          <div className="space-y-2">
            <Label htmlFor="phoneId">Phone Number ID</Label>
            <Input
              id="phoneId"
              placeholder="ID do número no Graph API"
              value={whatsappInstanceId}
              onChange={(e) => setWhatsappInstanceId(e.target.value)}
              disabled={busy}
              autoComplete="off"
              className="rounded-xl"
            />
            <p className="text-xs text-muted-foreground">
              Meta for Developers → a sua app WhatsApp → API Setup: copie o{" "}
              <strong>Phone number ID</strong> do número de produção ou de
              teste.
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="accessToken">Access Token</Label>
            <div className="flex gap-2">
              <Input
                id="accessToken"
                type={showAccessToken ? "text" : "password"}
                placeholder="Token permanente ou de teste"
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
                {showAccessToken ? "Ocultar" : "Mostrar"}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Painel Meta → WhatsApp → API Setup: gere um token temporário ou
              configure um <strong>System User</strong> com token permanente.
            </p>
          </div>
        </div>
      )}

      {whatsappProviderType === "EVOLUTION" && (
        <div className="space-y-6 rounded-xl border border-border bg-card/30 p-4">
          <div className="space-y-2">
            <Label htmlFor="evoUrl">URL da instância</Label>
            <Input
              id="evoUrl"
              placeholder="https://seu-servidor:8080"
              value={whatsappBaseUrl}
              onChange={(e) => setWhatsappBaseUrl(e.target.value)}
              disabled={busy}
              autoComplete="off"
              className="rounded-xl"
            />
            <p className="text-xs text-muted-foreground">
              URL base pública da Evolution API (sem barra final), ex.: onde o
              manager ou a API respondem.
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="evoInstance">Nome da instância</Label>
            <Input
              id="evoInstance"
              placeholder="minha-instancia"
              value={whatsappInstanceId}
              onChange={(e) => setWhatsappInstanceId(e.target.value)}
              disabled={busy}
              autoComplete="off"
              className="rounded-xl"
            />
            <p className="text-xs text-muted-foreground">
              Nome da instância criada no Evolution Manager / pairing — deve
              coincidir com o registo no servidor.
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="evoKey">API Key</Label>
            <Input
              id="evoKey"
              type="password"
              placeholder="Chave de autenticação global"
              value={whatsappApiKey}
              onChange={(e) => setWhatsappApiKey(e.target.value)}
              disabled={busy}
              autoComplete="off"
              className="rounded-xl"
            />
            <p className="text-xs text-muted-foreground">
              Definida no ficheiro de configuração da Evolution (ex.{" "}
              <code className="text-foreground/90">AUTHENTICATION_API_KEY</code>
              ).
            </p>
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
          {saving ? "A salvar…" : loadingInitial ? "A carregar…" : "Salvar"}
        </Button>
      </div>
    </div>
  );
}
