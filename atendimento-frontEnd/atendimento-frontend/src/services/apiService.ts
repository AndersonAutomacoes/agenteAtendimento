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

export async function getChatMessages(tenantId: string): Promise<ChatMessageItem[]> {
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

  if (!Array.isArray(json)) {
    throw new Error(apiI18nKey("errors.invalidMessagesArray"));
  }

  const out: ChatMessageItem[] = [];
  for (const el of json) {
    if (isChatMessageItem(el)) out.push(el);
  }
  if (out.length !== json.length) {
    throw new Error(apiI18nKey("errors.invalidMessagesShape"));
  }
  return out;
}

export type DashboardRange = "day" | "week" | "month";

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

function dashboardSummaryUrl(tenantId: string, range: DashboardRange): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({ tenantId, range }).toString();
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

/** Camel GET {@code /api/v1/dashboard/summary?tenantId=&range=}. */
export async function getDashboardSummary(
  tenantId: string,
  range: DashboardRange,
): Promise<DashboardSummary> {
  const res = await fetch(dashboardSummaryUrl(tenantId, range), {
    method: "GET",
    headers: { Accept: "application/json" },
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

function analyticsIntentsUrl(tenantId: string, days: number): string {
  const base = getApiBaseUrl();
  const q = new URLSearchParams({
    tenantId,
    days: String(days),
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

/** Camel GET {@code /api/v1/analytics/intents?tenantId=&days=}. */
export async function getAnalyticsIntents(
  tenantId: string,
  days: number,
): Promise<AnalyticsIntentsResponse> {
  const res = await fetch(analyticsIntentsUrl(tenantId, days), {
    method: "GET",
    headers: { Accept: "application/json" },
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
