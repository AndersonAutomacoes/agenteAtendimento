import { routing, type AppLocale } from "@/i18n/routing";
import { clearBillingBlockedCookieClient } from "@/lib/billing-cookie";

import { CEREBRO_AUTH_TOKEN_KEY } from "@/services/apiService";

export const TENANT_STORAGE_KEY = "cerebro-tenant-id";

/** Pedidos autenticados que devolvam 401 devem disparar fluxo de sessão expirada. */
export const SESSION_EXPIRED_EVENT = "cerebro-session-expired";

const PUBLIC_ROUTE_SEGMENTS = [
  "login",
  "register",
  "forgot-password",
  "landing",
  "pricing",
  "billing",
];

/** Normaliza pathname removendo prefixo de locale quando presente (ex.: /en/dashboard → /dashboard). */
export function normalizeAppPathname(pathname: string): string {
  const trimmed = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const parts = trimmed.split("/").filter(Boolean);
  if (
    parts.length > 0 &&
    routing.locales.includes(parts[0] as AppLocale)
  ) {
    const rest = parts.slice(1).join("/");
    return rest ? `/${rest}` : "/";
  }
  return trimmed || "/";
}

export function isPublicAppPath(pathname: string): boolean {
  const base = normalizeAppPathname(pathname);
  const seg = base.replace(/^\//, "").split("/")[0];
  return seg ? PUBLIC_ROUTE_SEGMENTS.includes(seg) : false;
}

export function clearAuthStorage(): void {
  try {
    localStorage.removeItem(CEREBRO_AUTH_TOKEN_KEY);
    localStorage.removeItem(TENANT_STORAGE_KEY);
  } catch {
    /* ignore */
  }
  clearBillingBlockedCookieClient();
}

export function triggerSessionExpired(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
}

function requestUsedBearerAuth(init?: RequestInit): boolean {
  const headers = init?.headers;
  if (!headers) return false;
  if (headers instanceof Headers) {
    const v =
      headers.get("Authorization") ?? headers.get("authorization");
    return typeof v === "string" && v.startsWith("Bearer ");
  }
  if (Array.isArray(headers)) {
    return headers.some(
      ([k, v]) =>
        k.toLowerCase() === "authorization" &&
        String(v).startsWith("Bearer "),
    );
  }
  const rec = headers as Record<string, string>;
  for (const key of Object.keys(rec)) {
    if (
      key.toLowerCase() === "authorization" &&
      String(rec[key]).startsWith("Bearer ")
    ) {
      return true;
    }
  }
  return false;
}

/**
 * Interceptor global de fetch: em 401 com Bearer no pedido, limpa sessão e redireciona para login.
 * Devolve a mesma Response para o chamador poder tratar o erro.
 */
export function installAuth401FetchInterceptor(
  onUnauthorized: () => void,
): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }
  const orig = window.fetch.bind(window);
  window.fetch = async (
    input: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> => {
    const res = await orig(input, init);
    if (
      res.status === 401 &&
      requestUsedBearerAuth(init) &&
      typeof window !== "undefined"
    ) {
      onUnauthorized();
    }
    return res;
  };
  return () => {
    window.fetch = orig;
  };
}
