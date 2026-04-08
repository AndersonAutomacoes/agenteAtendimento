/**
 * Chamadas HTTP ao backend Cérebro.
 *
 * - Sem `NEXT_PUBLIC_API_BASE`: URLs relativas (`/api/v1/...`) — os rewrites do Next
 *   encaminham para o Java (ingest: `/api/v1/ingest` → `/v1/ingest` no servidor).
 * - Com `NEXT_PUBLIC_API_BASE` (ex.: http://localhost:8080): pedidos diretos ao
 *   backend — exige CORS configurado no Spring.
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
