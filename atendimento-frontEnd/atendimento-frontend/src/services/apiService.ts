/**
 * Chamadas HTTP ao backend Cérebro.
 *
 * - `next.config.ts` define em **development** `NEXT_PUBLIC_API_BASE` para o Java
 *   (ex.: http://localhost:8080) por defeito, para o browser falar direto com o
 *   backend e evitar o proxy dos rewrites (~timeout curto) que causa ECONNRESET
 *   em respostas longas (chat + IA). Em produção o defeito é URL relativa + rewrites.
 * - Com `NEXT_PUBLIC_API_BASE`: pedidos diretos — CORS já está no Spring para localhost:3000.
 */

function normalizeBase(raw: string | undefined): string {
  if (!raw?.trim()) return "";
  return raw.replace(/\/+$/, "");
}

/** Base pública opcional (ex.: http://localhost:8080). Vazia = mesma origem (rewrites). */
export function getApiBaseUrl(): string {
  return normalizeBase(process.env.NEXT_PUBLIC_API_BASE);
}

function chatUrl(): string {
  const base = getApiBaseUrl();
  return base ? `${base}/api/v1/chat` : "/api/v1/chat";
}

/** Ingest: no Java real o path é `/v1/ingest`; com proxy Next usamos `/api/v1/ingest`. */
function ingestUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/v1/ingest?${q}`;
  }
  return `/api/v1/ingest?${q}`;
}

/** PUT bot-settings: no Java é `/v1/bot-settings`; com proxy Next usamos `/api/v1/bot-settings`. */
function botSettingsUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/v1/bot-settings?${q}`;
  }
  return `/api/v1/bot-settings?${q}`;
}

export type ChatRequestBody = {
  tenantId: string;
  sessionId: string;
  message: string;
  topK?: number;
  aiProvider?: string;
};

export type ChatSuccess = {
  assistantMessage: string;
};

export type IngestSuccess = {
  chunksIngested: number;
};

export type ApiErrorBody = {
  error?: string;
  message?: string;
};

function parseErrorMessage(json: unknown, fallback: string): string {
  if (json && typeof json === "object") {
    const o = json as ApiErrorBody;
    if (typeof o.error === "string" && o.error.length > 0) return o.error;
    if (typeof o.message === "string" && o.message.length > 0) return o.message;
  }
  return fallback;
}

export async function postChat(body: ChatRequestBody): Promise<ChatSuccess> {
  const res = await fetch(chatUrl(), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(text || `Resposta inválida (${res.status})`);
  }

  if (!res.ok) {
    throw new Error(parseErrorMessage(json, `Chat falhou (${res.status})`));
  }

  const obj = json as Partial<ChatSuccess>;
  if (typeof obj.assistantMessage !== "string") {
    throw new Error("Resposta sem assistantMessage");
  }
  return { assistantMessage: obj.assistantMessage };
}

export async function postIngest(
  tenantId: string,
  file: File,
): Promise<IngestSuccess> {
  const form = new FormData();
  form.append("file", file);

  const res = await fetch(ingestUrl(tenantId), {
    method: "POST",
    body: form,
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(text || `Resposta inválida (${res.status})`);
  }

  if (!res.ok) {
    throw new Error(parseErrorMessage(json, `Ingestão falhou (${res.status})`));
  }

  const obj = json as Partial<IngestSuccess>;
  if (typeof obj.chunksIngested !== "number") {
    throw new Error("Resposta sem chunksIngested");
  }
  return { chunksIngested: obj.chunksIngested };
}

export async function putBotSettings(
  tenantId: string,
  personality: string,
): Promise<void> {
  const res = await fetch(botSettingsUrl(tenantId), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ botPersonality: personality }),
  });

  const text = await res.text();
  if (!res.ok) {
    let json: unknown;
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      throw new Error(text || `Resposta inválida (${res.status})`);
    }
    throw new Error(
      parseErrorMessage(json, `Gravar configurações falhou (${res.status})`),
    );
  }
}

/** Alinhado ao enum Java e ao Camel {@code TenantSettingsHttpRequest}. */
export type WhatsAppProviderType = "SIMULATED" | "META" | "EVOLUTION";

export type TenantSettings = {
  tenantId: string;
  systemPrompt: string;
  whatsappProviderType: WhatsAppProviderType;
  whatsappApiKey: string | null;
  whatsappInstanceId: string | null;
  whatsappBaseUrl: string | null;
};

export type TenantSettingsPayload = {
  tenantId: string;
  systemPrompt: string;
  whatsappProviderType: WhatsAppProviderType;
  whatsappApiKey: string | null;
  whatsappInstanceId: string | null;
  whatsappBaseUrl: string | null;
};

/** GET: servlet Camel {@code /api/v1/tenant/settings?tenantId=}. */
function tenantSettingsGetUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/tenant/settings?${q}`;
  }
  return `/api/v1/tenant/settings?${q}`;
}

/** PUT: corpo JSON (sem query). */
function tenantSettingsPutUrl(): string {
  const base = getApiBaseUrl();
  return base ? `${base}/api/v1/tenant/settings` : "/api/v1/tenant/settings";
}

function isWhatsAppProviderType(v: string): v is WhatsAppProviderType {
  return v === "SIMULATED" || v === "META" || v === "EVOLUTION";
}

export async function getTenantSettings(tenantId: string): Promise<TenantSettings> {
  const res = await fetch(tenantSettingsGetUrl(tenantId), {
    method: "GET",
    headers: { Accept: "application/json" },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(text || `Resposta inválida (${res.status})`);
  }

  if (!res.ok) {
    throw new Error(
      parseErrorMessage(json, `Carregar configurações falhou (${res.status})`),
    );
  }

  const o = json as Record<string, unknown>;
  if (typeof o.tenantId !== "string" || typeof o.systemPrompt !== "string") {
    throw new Error("Resposta de configurações inválida");
  }
  const raw = typeof o.whatsappProviderType === "string" ? o.whatsappProviderType : "SIMULATED";
  const whatsappProviderType: WhatsAppProviderType = isWhatsAppProviderType(raw)
    ? raw
    : "SIMULATED";

  return {
    tenantId: o.tenantId,
    systemPrompt: o.systemPrompt,
    whatsappProviderType,
    whatsappApiKey: typeof o.whatsappApiKey === "string" ? o.whatsappApiKey : null,
    whatsappInstanceId:
      typeof o.whatsappInstanceId === "string" ? o.whatsappInstanceId : null,
    whatsappBaseUrl: typeof o.whatsappBaseUrl === "string" ? o.whatsappBaseUrl : null,
  };
}

export async function putTenantSettings(
  payload: TenantSettingsPayload,
): Promise<void> {
  const res = await fetch(tenantSettingsPutUrl(), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const text = await res.text();
  if (!res.ok) {
    let json: unknown;
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      throw new Error(text || `Resposta inválida (${res.status})`);
    }
    throw new Error(
      parseErrorMessage(
        json,
        `Gravar configurações do canal falhou (${res.status})`,
      ),
    );
  }
}
