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

/** ID token Firebase (Bearer) para RBAC analytics/dashboard/export. */
export const CEREBRO_AUTH_TOKEN_KEY = "cerebro-access-token";

export type ProfileLevel = "BASIC" | "PRO" | "ULTRA";

export type PortalRegisterResponse = {
  tenantId: string;
  profileLevel: ProfileLevel;
};

export type PortalSession = {
  tenantId: string;
  profileLevel: ProfileLevel;
};

function authHeaders(): Record<string, string> {
  if (typeof window === "undefined") return {};
  try {
    const t = localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY);
    return t ? { Authorization: `Bearer ${t}` } : {};
  } catch {
    return {};
  }
}

function portalAuthRegisterUrl(): string {
  const base = getApiBaseUrl();
  return base ? `${base}/v1/auth/register` : "/api/v1/auth/register";
}

function portalAuthMeUrl(): string {
  const base = getApiBaseUrl();
  return base ? `${base}/v1/auth/me` : "/api/v1/auth/me";
}

export async function postPortalRegister(inviteCode: string): Promise<PortalRegisterResponse> {
  const res = await fetch(portalAuthRegisterUrl(), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ inviteCode: inviteCode.trim() }),
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(httpErrorUserMessage(res.status, json, "errors.registerFailed"));
  }
  const o = json as Record<string, unknown>;
  if (typeof o.tenantId !== "string" || typeof o.profileLevel !== "string") {
    throw new Error(apiI18nKey("errors.invalidRegisterResponse"));
  }
  return {
    tenantId: o.tenantId,
    profileLevel: o.profileLevel as ProfileLevel,
  };
}

export async function getPortalSession(): Promise<PortalSession> {
  const res = await fetch(portalAuthMeUrl(), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(httpErrorUserMessage(res.status, json, "errors.sessionFailed"));
  }
  const o = json as Record<string, unknown>;
  if (typeof o.tenantId !== "string" || typeof o.profileLevel !== "string") {
    throw new Error(apiI18nKey("errors.invalidSessionResponse"));
  }
  return { tenantId: o.tenantId, profileLevel: o.profileLevel as ProfileLevel };
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

/**
 * Prefixo para mensagens resolvidas via `next-intl` (namespace `api` → `errors.*`).
 * Ex.: `i18n:errors.serverUnavailable`
 */
export const API_I18N_PREFIX = "i18n:" as const;

export function apiI18nKey(errorsDotKey: string): string {
  return `${API_I18N_PREFIX}${errorsDotKey}`;
}

function parseErrorFromBody(json: unknown, fallbackErrorsKey: string): string {
  if (json && typeof json === "object") {
    const o = json as ApiErrorBody;
    if (typeof o.error === "string" && o.error.length > 0) return o.error;
    if (typeof o.message === "string" && o.message.length > 0) return o.message;
  }
  return apiI18nKey(fallbackErrorsKey);
}

/** Erros 5xx e corpo JSON — 4xx usa texto do backend quando existir. */
function httpErrorUserMessage(
  status: number,
  json: unknown,
  fallbackErrorsKey: string,
): string {
  if (status >= 500) {
    return apiI18nKey("errors.serverUnavailable");
  }
  return parseErrorFromBody(json, fallbackErrorsKey);
}

/**
 * Converte exceções da API em texto para toast/UI.
 * Passar `t` de `useTranslations('api')`.
 */
export function toUserFacingApiError(
  e: unknown,
  t: (key: string) => string,
): string {
  if (e instanceof TypeError) {
    return t("errors.serverUnavailable");
  }
  if (e instanceof Error && e.message.startsWith(API_I18N_PREFIX)) {
    return t(e.message.slice(API_I18N_PREFIX.length));
  }
  if (e instanceof Error && e.message.length > 0) {
    return e.message;
  }
  return t("errors.serverUnavailable");
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
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.chatAssistantFailed"),
    );
  }

  const obj = json as Partial<ChatSuccess>;
  if (typeof obj.assistantMessage !== "string") {
    throw new Error(apiI18nKey("errors.invalidAssistantResponse"));
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
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.ingestFailed"),
    );
  }

  const obj = json as Partial<IngestSuccess>;
  if (typeof obj.chunksIngested !== "number") {
    throw new Error(apiI18nKey("errors.chunksMissing"));
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
      throw new Error(apiI18nKey("errors.serverUnavailable"));
    }
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.saveBotSettingsFailed"),
    );
  }
}

/** Alinhado ao enum Java e ao Camel {@code TenantSettingsHttpRequest}. */
export type WhatsAppProviderType = "SIMULATED" | "META" | "EVOLUTION";

export type TenantSettings = {
  tenantId: string;
  profileLevel: ProfileLevel;
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
    headers: { Accept: "application/json", ...authHeaders() },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.loadSettingsFailed"),
    );
  }

  const o = json as Record<string, unknown>;
  if (typeof o.tenantId !== "string" || typeof o.systemPrompt !== "string") {
    throw new Error(apiI18nKey("errors.invalidSettingsPayload"));
  }
  const profileRaw = typeof o.profileLevel === "string" ? o.profileLevel : "BASIC";
  const profileLevel: ProfileLevel =
    profileRaw === "PRO" || profileRaw === "ULTRA" ? profileRaw : "BASIC";
  const raw = typeof o.whatsappProviderType === "string" ? o.whatsappProviderType : "SIMULATED";
  const whatsappProviderType: WhatsAppProviderType = isWhatsAppProviderType(raw)
    ? raw
    : "SIMULATED";

  return {
    tenantId: o.tenantId,
    profileLevel,
    systemPrompt: o.systemPrompt,
    whatsappProviderType,
    whatsappApiKey: typeof o.whatsappApiKey === "string" ? o.whatsappApiKey : null,
    whatsappInstanceId:
      typeof o.whatsappInstanceId === "string" ? o.whatsappInstanceId : null,
    whatsappBaseUrl: typeof o.whatsappBaseUrl === "string" ? o.whatsappBaseUrl : null,
  };
}

/** Mensagens de monitorização WhatsApp (Camel GET {@code /api/v1/messages?tenantId=}). */
export type ChatMessageItem = {
  id: number;
  tenantId: string;
  phoneNumber: string;
  contactDisplayName?: string | null;
  contactProfilePicUrl?: string | null;
  detectedIntent?: string | null;
  role: "USER" | "ASSISTANT";
  content: string;
  status: "RECEIVED" | "SENT" | "ERROR";
  /** ISO-8601 */
  timestamp: string;
};

export type ChatMessagesPayload = {
  messages: ChatMessageItem[];
  /** Por telefone; omissão equivale a IA activa. */
  botEnabledByPhone: Record<string, boolean>;
};

function chatMessagesUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/messages?${q}`;
  }
  return `/api/v1/messages?${q}`;
}

function chatMessageRetryUrl(tenantId: string, messageId: number): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/messages/${messageId}/retry?${q}`;
  }
  return `/api/v1/messages/${messageId}/retry?${q}`;
}

function isChatMessageItem(v: unknown): v is ChatMessageItem {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.id === "number" &&
    typeof o.tenantId === "string" &&
    typeof o.phoneNumber === "string" &&
    (o.role === "USER" || o.role === "ASSISTANT") &&
    typeof o.content === "string" &&
    (o.status === "RECEIVED" || o.status === "SENT" || o.status === "ERROR") &&
    typeof o.timestamp === "string"
  );
}

function isBotEnabledByPhoneMap(v: unknown): v is Record<string, boolean> {
  if (!v || typeof v !== "object") return false;
  return Object.values(v as Record<string, unknown>).every(
    (x) => typeof x === "boolean",
  );
}

function conversationHumanHandoffUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/conversations/human-handoff?${q}`;
  }
  return `/api/v1/conversations/human-handoff?${q}`;
}

function conversationEnableBotUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/conversations/enable-bot?${q}`;
  }
  return `/api/v1/conversations/enable-bot?${q}`;
}

function messagesHumanReplyUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/messages/human-reply?${q}`;
  }
  return `/api/v1/messages/human-reply?${q}`;
}

export async function getChatMessages(tenantId: string): Promise<ChatMessagesPayload> {
  const res = await fetch(chatMessagesUrl(tenantId), {
    method: "GET",
    headers: { Accept: "application/json" },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : [];
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.loadMessagesFailed"),
    );
  }

  if (Array.isArray(json)) {
    const out: ChatMessageItem[] = [];
    for (const el of json) {
      if (isChatMessageItem(el)) out.push(el);
    }
    if (out.length !== json.length) {
      throw new Error(apiI18nKey("errors.invalidMessagesShape"));
    }
    return { messages: out, botEnabledByPhone: {} };
  }

  if (!json || typeof json !== "object") {
    throw new Error(apiI18nKey("errors.invalidMessagesEnvelope"));
  }
  const o = json as Record<string, unknown>;
  const rawMessages = o.messages;
  const rawBot = o.botEnabledByPhone;
  if (!Array.isArray(rawMessages) || !isBotEnabledByPhoneMap(rawBot)) {
    throw new Error(apiI18nKey("errors.invalidMessagesEnvelope"));
  }
  const messages: ChatMessageItem[] = [];
  for (const el of rawMessages) {
    if (isChatMessageItem(el)) messages.push(el);
  }
  if (messages.length !== rawMessages.length) {
    throw new Error(apiI18nKey("errors.invalidMessagesShape"));
  }
  return { messages, botEnabledByPhone: rawBot };
}

export async function humanHandoffConversation(
  tenantId: string,
  phoneNumber: string,
): Promise<void> {
  const res = await fetch(conversationHumanHandoffUrl(tenantId), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ phoneNumber }),
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.conversationActionFailed"),
    );
  }
}

export async function enableBotForConversation(
  tenantId: string,
  phoneNumber: string,
): Promise<void> {
  const res = await fetch(conversationEnableBotUrl(tenantId), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ phoneNumber }),
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.conversationActionFailed"),
    );
  }
}

/** Envio manual pelo monitor (bot desligado) — Evolution via backend + persistência em chat_message. */
export async function sendHumanMonitorMessage(
  tenantId: string,
  phoneNumber: string,
  text: string,
): Promise<void> {
  const res = await fetch(messagesHumanReplyUrl(tenantId), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ phoneNumber, text }),
  });
  const bodyText = await res.text();
  let json: unknown;
  try {
    json = bodyText ? JSON.parse(bodyText) : null;
  } catch {
    json = null;
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.humanReplyFailed"),
    );
  }
}

export type DashboardSeriesPoint = {
  bucketStart: string;
  count: number;
};

export type DashboardRecentInteraction = {
  messageId: number;
  phoneNumber: string;
  contactDisplayName: string | null;
  contactProfilePicUrl: string | null;
  detectedIntent: string | null;
  timestamp: string;
  content: string;
};

export type DashboardSummary = {
  totalClients: number;
  messagesToday: number;
  aiRatePercent: number | null;
  instanceStatus: string;
  series: DashboardSeriesPoint[];
  recentInteractions: DashboardRecentInteraction[];
};

/** Categorias de intenção principal (analytics_intents / classificação Gemini). */
export type PrimaryIntentCategory =
  | "ORCAMENTO"
  | "AGENDAMENTO"
  | "DUVIDA_TECNICA"
  | "RECLAMACAO"
  | "OUTRO";

export type AnalyticsIntentCount = {
  category: PrimaryIntentCategory;
  count: number;
};

export type ConversationSentiment = "POSITIVO" | "NEUTRO" | "NEGATIVO";

export type AnalyticsSentimentCount = {
  sentiment: ConversationSentiment;
  count: number;
};

export type AnalyticsIntentsResponse = {
  tenantId: string;
  days: number;
  periodStart: string;
  periodEnd: string;
  counts: AnalyticsIntentCount[];
  previousCounts: AnalyticsIntentCount[];
  previousPeriodStart: string;
  previousPeriodEnd: string;
  sentimentCounts: AnalyticsSentimentCount[];
};

function dashboardSummaryUrl(tenantId: string, startDate: string, endDate: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId, startDate, endDate }).toString();
  if (base) {
    return `${base}/api/v1/dashboard/summary?${q}`;
  }
  return `/api/v1/dashboard/summary?${q}`;
}

function isDashboardRecentInteraction(v: unknown): v is DashboardRecentInteraction {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.messageId === "number" &&
    typeof o.phoneNumber === "string" &&
    (o.contactDisplayName === null || typeof o.contactDisplayName === "string") &&
    (o.contactProfilePicUrl === null || typeof o.contactProfilePicUrl === "string") &&
    (o.detectedIntent === null || typeof o.detectedIntent === "string") &&
    typeof o.timestamp === "string" &&
    typeof o.content === "string"
  );
}

function isDashboardSummary(v: unknown): v is DashboardSummary {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (
    typeof o.totalClients !== "number" ||
    typeof o.messagesToday !== "number" ||
    (o.aiRatePercent !== null && typeof o.aiRatePercent !== "number") ||
    typeof o.instanceStatus !== "string" ||
    !Array.isArray(o.series) ||
    !Array.isArray(o.recentInteractions)
  ) {
    return false;
  }
  for (const p of o.series) {
    if (!p || typeof p !== "object") return false;
    const pt = p as Record<string, unknown>;
    if (typeof pt.bucketStart !== "string" || typeof pt.count !== "number") return false;
  }
  for (const r of o.recentInteractions) {
    if (!isDashboardRecentInteraction(r)) return false;
  }
  return true;
}

/** Camel GET {@code /api/v1/dashboard/summary?tenantId=&startDate=&endDate=}. */
export async function getDashboardSummary(
  tenantId: string,
  startDate: string,
  endDate: string,
): Promise<DashboardSummary> {
  const res = await fetch(dashboardSummaryUrl(tenantId, startDate, endDate), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.loadDashboardFailed"),
    );
  }

  if (!isDashboardSummary(json)) {
    throw new Error(apiI18nKey("errors.invalidDashboardSummary"));
  }
  return json;
}

const PRIMARY_INTENT_CATEGORIES: readonly PrimaryIntentCategory[] = [
  "ORCAMENTO",
  "AGENDAMENTO",
  "DUVIDA_TECNICA",
  "RECLAMACAO",
  "OUTRO",
];

function analyticsIntentsUrl(tenantId: string, startDate: string, endDate: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    startDate,
    endDate,
  }).toString();
  if (base) {
    return `${base}/api/v1/analytics/intents?${q}`;
  }
  return `/api/v1/analytics/intents?${q}`;
}

function isPrimaryIntentCategory(v: unknown): v is PrimaryIntentCategory {
  return typeof v === "string" && (PRIMARY_INTENT_CATEGORIES as readonly string[]).includes(v);
}

const CONVERSATION_SENTIMENTS: readonly ConversationSentiment[] = ["POSITIVO", "NEUTRO", "NEGATIVO"];

function isConversationSentiment(v: unknown): v is ConversationSentiment {
  return typeof v === "string" && (CONVERSATION_SENTIMENTS as readonly string[]).includes(v);
}

function isAnalyticsIntentCountRow(v: unknown): v is AnalyticsIntentCount {
  if (!v || typeof v !== "object") return false;
  const r = v as Record<string, unknown>;
  return isPrimaryIntentCategory(r.category) && typeof r.count === "number";
}

function isAnalyticsSentimentCountRow(v: unknown): v is AnalyticsSentimentCount {
  if (!v || typeof v !== "object") return false;
  const r = v as Record<string, unknown>;
  return isConversationSentiment(r.sentiment) && typeof r.count === "number";
}

function isAnalyticsIntentsResponse(v: unknown): v is AnalyticsIntentsResponse {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (
    typeof o.tenantId !== "string" ||
    typeof o.days !== "number" ||
    typeof o.periodStart !== "string" ||
    typeof o.periodEnd !== "string" ||
    typeof o.previousPeriodStart !== "string" ||
    typeof o.previousPeriodEnd !== "string" ||
    !Array.isArray(o.counts) ||
    !Array.isArray(o.previousCounts) ||
    !Array.isArray(o.sentimentCounts)
  ) {
    return false;
  }
  for (const row of o.counts) {
    if (!isAnalyticsIntentCountRow(row)) return false;
  }
  for (const row of o.previousCounts) {
    if (!isAnalyticsIntentCountRow(row)) return false;
  }
  for (const row of o.sentimentCounts) {
    if (!isAnalyticsSentimentCountRow(row)) return false;
  }
  return true;
}

/** Camel GET {@code /api/v1/analytics/intents?tenantId=&startDate=&endDate=}. */
export async function getAnalyticsIntents(
  tenantId: string,
  startDate: string,
  endDate: string,
): Promise<AnalyticsIntentsResponse> {
  const res = await fetch(analyticsIntentsUrl(tenantId, startDate, endDate), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.loadAnalyticsIntentsFailed"),
    );
  }

  if (!isAnalyticsIntentsResponse(json)) {
    throw new Error(apiI18nKey("errors.invalidAnalyticsIntents"));
  }
  return json;
}

export type AnalyticsExportFormat = "csv" | "pdf";

function analyticsExportUrl(
  tenantId: string,
  startDate: string,
  endDate: string,
  locale: string,
  format: AnalyticsExportFormat,
): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    startDate,
    endDate,
    locale,
    format,
  }).toString();
  if (base) {
    return `${base}/api/v1/analytics/export?${q}`;
  }
  return `/api/v1/analytics/export?${q}`;
}

export async function exportAnalyticsReport(
  tenantId: string,
  startDate: string,
  endDate: string,
  locale: string,
  format: AnalyticsExportFormat,
): Promise<Blob> {
  const res = await fetch(analyticsExportUrl(tenantId, startDate, endDate, locale, format), {
    method: "GET",
    headers: { Accept: "*/*", ...authHeaders() },
  });
  if (!res.ok) {
    const text = await res.text();
    let msg = apiI18nKey("errors.exportFailed");
    try {
      const j = text ? JSON.parse(text) : {};
      if (j && typeof j === "object") {
        const o = j as { error?: string; message?: string };
        if (typeof o.error === "string" && o.error.length > 0) msg = o.error;
        else if (typeof o.message === "string" && o.message.length > 0) msg = o.message;
      }
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  return res.blob();
}

/** Arquivos indexados (Camel GET {@code /api/v1/knowledge-base?tenantId=}). */
export type KnowledgeBaseFileItem = {
  batchId: string;
  fileName: string;
  /** ISO-8601 */
  uploadedAt: string;
  sizeBytes: number;
  chunkCount: number;
  status: string;
};

function knowledgeBaseListUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/knowledge-base?${q}`;
  }
  return `/api/v1/knowledge-base?${q}`;
}

function knowledgeBaseDeleteUrl(tenantId: string, batchId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  const path = encodeURIComponent(batchId);
  if (base) {
    return `${base}/api/v1/knowledge-base/${path}?${q}`;
  }
  return `/api/v1/knowledge-base/${path}?${q}`;
}

function isKnowledgeBaseFileItem(v: unknown): v is KnowledgeBaseFileItem {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.batchId === "string" &&
    typeof o.fileName === "string" &&
    typeof o.uploadedAt === "string" &&
    typeof o.sizeBytes === "number" &&
    typeof o.chunkCount === "number" &&
    typeof o.status === "string"
  );
}

export async function getKnowledgeBase(
  tenantId: string,
): Promise<KnowledgeBaseFileItem[]> {
  const res = await fetch(knowledgeBaseListUrl(tenantId), {
    method: "GET",
    headers: { Accept: "application/json" },
  });

  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : [];
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }

  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.listKnowledgeFailed"),
    );
  }

  if (!Array.isArray(json)) {
    throw new Error(apiI18nKey("errors.invalidKnowledgeArray"));
  }

  const out: KnowledgeBaseFileItem[] = [];
  for (const el of json) {
    if (isKnowledgeBaseFileItem(el)) out.push(el);
  }
  if (out.length !== json.length) {
    throw new Error(apiI18nKey("errors.invalidKnowledgeShape"));
  }
  return out;
}

export async function deleteKnowledgeFile(
  tenantId: string,
  batchId: string,
): Promise<void> {
  const res = await fetch(knowledgeBaseDeleteUrl(tenantId, batchId), {
    method: "DELETE",
    headers: { Accept: "application/json" },
  });

  if (res.status === 204) {
    return;
  }

  const text = await res.text();
  let json: unknown = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    /* ignore */
  }
  throw new Error(
    httpErrorUserMessage(res.status, json, "errors.deleteKnowledgeFailed"),
  );
}

/** Reenvio de resposta ASSISTANT em ERROR ({@code POST /api/v1/messages/:id/retry}). */
export async function retryChatMessage(
  tenantId: string,
  messageId: number,
): Promise<void> {
  const res = await fetch(chatMessageRetryUrl(tenantId, messageId), {
    method: "POST",
    headers: { Accept: "application/json" },
  });

  const text = await res.text();
  if (res.status === 204) {
    return;
  }

  let json: unknown = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    /* ignore */
  }
  throw new Error(
    httpErrorUserMessage(res.status, json, "errors.retryMessageFailed"),
  );
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
      throw new Error(apiI18nKey("errors.serverUnavailable"));
    }
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.saveTenantChannelFailed"),
    );
  }
}

/** Filtro da lista {@code GET /api/v1/appointments?scope=}. */
export type TenantAppointmentScope = "all" | "today" | "future";

export type TenantAppointmentRow = {
  id: number;
  startsAt: string;
  endsAt: string;
  clientName: string;
  serviceName: string;
  conversationId: string | null;
  statusLabel: string;
  todayHighlight: boolean;
  status: string;
};

export type TenantAppointmentsListResponse = {
  appointments: TenantAppointmentRow[];
};

export type UpcomingAppointmentsCountResponse = {
  count: number;
  days: number;
};

export type AppointmentLookupEntry = {
  startsAt: string;
  clientName: string;
  serviceName: string;
};

export type AppointmentLookupResponse = {
  byPhoneDigits: Record<string, AppointmentLookupEntry>;
};

function appointmentsListUrl(tenantId: string, scope: TenantAppointmentScope): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId, scope }).toString();
  if (base) {
    return `${base}/api/v1/appointments?${q}`;
  }
  return `/api/v1/appointments?${q}`;
}

function appointmentsUpcomingCountUrl(tenantId: string, days: number): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    days: String(days),
  }).toString();
  if (base) {
    return `${base}/api/v1/appointments/upcoming-count?${q}`;
  }
  return `/api/v1/appointments/upcoming-count?${q}`;
}

function appointmentsLookupUpcomingUrl(tenantId: string, phones: string[]): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    phones: phones.join(","),
  }).toString();
  if (base) {
    return `${base}/api/v1/appointments/lookup-upcoming?${q}`;
  }
  return `/api/v1/appointments/lookup-upcoming?${q}`;
}

function isTenantAppointmentRow(v: unknown): v is TenantAppointmentRow {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.id === "number" &&
    typeof o.startsAt === "string" &&
    typeof o.endsAt === "string" &&
    typeof o.clientName === "string" &&
    typeof o.serviceName === "string" &&
    (o.conversationId === null || typeof o.conversationId === "string") &&
    typeof o.statusLabel === "string" &&
    typeof o.todayHighlight === "boolean" &&
    typeof o.status === "string"
  );
}

function isTenantAppointmentsListResponse(v: unknown): v is TenantAppointmentsListResponse {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (!Array.isArray(o.appointments)) return false;
  return o.appointments.every(isTenantAppointmentRow);
}

function isAppointmentLookupEntry(v: unknown): v is AppointmentLookupEntry {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.startsAt === "string" &&
    typeof o.clientName === "string" &&
    typeof o.serviceName === "string"
  );
}

function isAppointmentLookupResponse(v: unknown): v is AppointmentLookupResponse {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  const m = o.byPhoneDigits;
  if (!m || typeof m !== "object") return false;
  return Object.values(m).every(isAppointmentLookupEntry);
}

/** Lista agendamentos do tenant (Bearer). */
export async function getTenantAppointments(
  tenantId: string,
  scope: TenantAppointmentScope = "all",
): Promise<TenantAppointmentsListResponse> {
  const res = await fetch(appointmentsListUrl(tenantId, scope), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.listAppointmentsFailed"),
    );
  }
  if (!isTenantAppointmentsListResponse(json)) {
    throw new Error(apiI18nKey("errors.invalidAppointmentsList"));
  }
  return json;
}

/** Contagem de inícios no intervalo [agora, agora+N dias). */
export async function getUpcomingAppointmentsCount(
  tenantId: string,
  days = 7,
): Promise<UpcomingAppointmentsCountResponse> {
  const res = await fetch(appointmentsUpcomingCountUrl(tenantId, days), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.appointmentsCountFailed"),
    );
  }
  const o = json as Record<string, unknown>;
  if (typeof o.count !== "number" || typeof o.days !== "number") {
    throw new Error(apiI18nKey("errors.invalidAppointmentsCount"));
  }
  return { count: o.count, days: o.days };
}

/**
 * Próximo agendamento futuro por dígitos do telefone (conversation_id wa-*).
 * {@code phones} — lista de strings com dígitos (ex.: após normalizar número WhatsApp).
 */
export async function lookupUpcomingAppointmentsByPhones(
  tenantId: string,
  phoneDigits: string[],
): Promise<AppointmentLookupResponse> {
  const res = await fetch(appointmentsLookupUpcomingUrl(tenantId, phoneDigits), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.appointmentsLookupFailed"),
    );
  }
  if (!isAppointmentLookupResponse(json)) {
    throw new Error(apiI18nKey("errors.invalidAppointmentsLookup"));
  }
  return json;
}

// --- CRM (ficha do cliente) ---

export type CrmCustomerDto = {
  id: string;
  tenantId: string;
  conversationId: string;
  phoneNumber: string | null;
  fullName: string | null;
  email: string | null;
  firstInteraction: string;
  totalAppointments: number;
  internalNotes: string | null;
  lastIntent?: string | null;
  lastDetectedIntent?: string | null;
  leadScore?: number;
  isConverted?: boolean;
  intentStatus?: string | null;
  lastIntentAt?: string | null;
  aiSalesInsight?: string | null;
};

export type CrmMessageDto = {
  id: number;
  role: string;
  content: string;
  occurredAt: string;
};

export type CrmAppointmentDto = {
  id: number;
  startsAt: string;
  endsAt: string;
  clientName: string;
  serviceName: string;
  statusLabel: string;
  status: string;
};

export type CrmSummaryResponse = {
  customer: CrmCustomerDto | null;
  messages: CrmMessageDto[];
  appointments: CrmAppointmentDto[];
};

function crmSummaryUrl(tenantId: string, conversationId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    conversationId,
  }).toString();
  if (base) {
    return `${base}/api/v1/crm/summary?${q}`;
  }
  return `/api/v1/crm/summary?${q}`;
}

function crmPatchNotesUrl(tenantId: string, customerId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  const path = `/api/v1/crm/customers/${encodeURIComponent(customerId)}/notes?${q}`;
  if (base) {
    return `${base}${path}`;
  }
  return path;
}

function isCrmCustomerDto(v: unknown): v is CrmCustomerDto {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.id === "string" &&
    typeof o.tenantId === "string" &&
    typeof o.conversationId === "string" &&
    (o.phoneNumber === null || typeof o.phoneNumber === "string") &&
    (o.fullName === null || typeof o.fullName === "string") &&
    (o.email === null || typeof o.email === "string") &&
    typeof o.firstInteraction === "string" &&
    typeof o.totalAppointments === "number" &&
    (o.internalNotes === null || typeof o.internalNotes === "string") &&
    (o.lastIntent === undefined ||
      o.lastIntent === null ||
      typeof o.lastIntent === "string") &&
    (o.intentStatus === undefined ||
      o.intentStatus === null ||
      typeof o.intentStatus === "string") &&
    (o.lastIntentAt === undefined ||
      o.lastIntentAt === null ||
      typeof o.lastIntentAt === "string") &&
    (o.lastDetectedIntent === undefined ||
      o.lastDetectedIntent === null ||
      typeof o.lastDetectedIntent === "string") &&
    (o.leadScore === undefined ||
      typeof o.leadScore === "number") &&
    (o.isConverted === undefined || typeof o.isConverted === "boolean") &&
    (o.aiSalesInsight === undefined ||
      o.aiSalesInsight === null ||
      typeof o.aiSalesInsight === "string")
  );
}

function isCrmMessageDto(v: unknown): v is CrmMessageDto {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.id === "number" &&
    typeof o.role === "string" &&
    typeof o.content === "string" &&
    typeof o.occurredAt === "string"
  );
}

function isCrmAppointmentDto(v: unknown): v is CrmAppointmentDto {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  return (
    typeof o.id === "number" &&
    typeof o.startsAt === "string" &&
    typeof o.endsAt === "string" &&
    typeof o.clientName === "string" &&
    typeof o.serviceName === "string" &&
    typeof o.statusLabel === "string" &&
    typeof o.status === "string"
  );
}

function isCrmSummaryResponse(v: unknown): v is CrmSummaryResponse {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  const c = o.customer;
  if (c !== null && c !== undefined && !isCrmCustomerDto(c)) {
    return false;
  }
  if (!Array.isArray(o.messages) || !o.messages.every(isCrmMessageDto)) {
    return false;
  }
  if (!Array.isArray(o.appointments) || !o.appointments.every(isCrmAppointmentDto)) {
    return false;
  }
  return true;
}

/** Ficha agregada (CRM + mensagens + agendamentos). */
export async function getCrmSummary(
  tenantId: string,
  conversationId: string,
): Promise<CrmSummaryResponse> {
  const res = await fetch(crmSummaryUrl(tenantId, conversationId), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(httpErrorUserMessage(res.status, json, "errors.crmSummaryFailed"));
  }
  if (!isCrmSummaryResponse(json)) {
    throw new Error(apiI18nKey("errors.invalidCrmSummary"));
  }
  return json;
}

export async function patchCrmCustomerNotes(
  tenantId: string,
  customerId: string,
  internalNotes: string,
): Promise<void> {
  const res = await fetch(crmPatchNotesUrl(tenantId, customerId), {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ internalNotes }),
  });
  if (res.status === 204) {
    return;
  }
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  throw new Error(httpErrorUserMessage(res.status, json, "errors.crmNotesSaveFailed"));
}

export type CrmOpportunitiesResponse = {
  opportunities: CrmCustomerDto[];
};

function crmOpportunitiesUrl(tenantId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  if (base) {
    return `${base}/api/v1/crm/opportunities?${q}`;
  }
  return `/api/v1/crm/opportunities?${q}`;
}

function crmAssumeOpportunityUrl(tenantId: string, customerId: string): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId }).toString();
  const path = `/api/v1/crm/opportunities/${encodeURIComponent(customerId)}/assume?${q}`;
  if (base) {
    return `${base}${path}`;
  }
  return path;
}

function isCrmOpportunitiesResponse(v: unknown): v is CrmOpportunitiesResponse {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (!Array.isArray(o.opportunities)) return false;
  return o.opportunities.every(
    (x) => isCrmCustomerDto(x),
  );
}

/** Leads com intenção não fechada (PENDING_LEAD). */
export async function getCrmOpportunities(
  tenantId: string,
): Promise<CrmOpportunitiesResponse> {
  const res = await fetch(crmOpportunitiesUrl(tenantId), {
    method: "GET",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(apiI18nKey("errors.serverUnavailable"));
  }
  if (!res.ok) {
    throw new Error(
      httpErrorUserMessage(res.status, json, "errors.crmOpportunitiesFailed"),
    );
  }
  if (!isCrmOpportunitiesResponse(json)) {
    throw new Error(apiI18nKey("errors.invalidCrmOpportunities"));
  }
  return json;
}

export async function assumeCrmOpportunity(
  tenantId: string,
  customerId: string,
): Promise<void> {
  const res = await fetch(crmAssumeOpportunityUrl(tenantId, customerId), {
    method: "POST",
    headers: { Accept: "application/json", ...authHeaders() },
  });
  if (res.status === 204) {
    return;
  }
  const text = await res.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = {};
  }
  throw new Error(
    httpErrorUserMessage(res.status, json, "errors.crmAssumeOpportunityFailed"),
  );
}
