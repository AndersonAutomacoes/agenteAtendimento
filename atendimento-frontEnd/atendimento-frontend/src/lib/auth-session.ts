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

/** Login com prefixo de locale quando a URL actual o tem (ex.: `/en/dashboard` → `/en/login`). */
export function buildLoginHrefWithCurrentLocale(): string {
  if (typeof window === "undefined") return "/login";
  const parts = window.location.pathname.split("/").filter(Boolean);
  const first = parts[0] as AppLocale | undefined;
  if (first && routing.locales.includes(first)) {
    return `/${first}/login`;
  }
  return "/login";
}

function headersInitHasBearer(headers: HeadersInit): boolean {
  if (headers instanceof Headers) {
    const v =
      headers.get("Authorization") ?? headers.get("authorization");
    return typeof v === "string" && v.trim().startsWith("Bearer ");
  }
  if (Array.isArray(headers)) {
    return headers.some(
      ([k, v]) =>
        k.toLowerCase() === "authorization" &&
        String(v).trim().startsWith("Bearer "),
    );
  }
  const rec = headers as Record<string, string>;
  for (const key of Object.keys(rec)) {
    if (
      key.toLowerCase() === "authorization" &&
      String(rec[key] ?? "").trim().startsWith("Bearer ")
    ) {
      return true;
    }
  }
  return false;
}

/**
 * Indica se o pedido foi feito com JWT (header {@code Authorization}) ou, em último recurso, com token ainda em
 * {@code localStorage} para um URL da API — cobre {@code fetch(new Request(...))} sem segundo argumento.
 */
function requestAppearsAuthenticated(
  input: RequestInfo | URL,
  init?: RequestInit,
): boolean {
  if (init?.headers !== undefined && headersInitHasBearer(init.headers)) {
    return true;
  }
  if (typeof Request !== "undefined" && input instanceof Request) {
    const h =
      input.headers.get("Authorization") ?? input.headers.get("authorization");
    if (typeof h === "string" && h.trim().startsWith("Bearer ")) {
      return true;
    }
  }
  let urlStr = "";
  try {
    if (typeof input === "string") {
      urlStr = input;
    } else if (input instanceof URL) {
      urlStr = input.toString();
    } else if (input instanceof Request) {
      urlStr = input.url;
    }
  } catch {
    return false;
  }
  const looksLikeApi =
    urlStr.includes("/api/v1/") || urlStr.includes("/v1/");
  const isRegister = urlStr.includes("/v1/auth/register");
  if (!looksLikeApi || isRegister) {
    return false;
  }
  try {
    return Boolean(localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY));
  } catch {
    return false;
  }
}

/**
 * Interceptor global de fetch: em 401 quando o pedido parecia autenticado (Bearer no {@code Request}/{@code init},
 * ou token em {@code localStorage} para URL da API), dispara o fluxo de sessão expirada (limpeza + login).
 * Devolve a mesma {@link Response} para o chamador poder tratar o erro.
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
    if (res.status === 401 && typeof window !== "undefined") {
      if (requestAppearsAuthenticated(input, init)) {
        onUnauthorized();
      }
    }
    return res;
  };
  return () => {
    window.fetch = orig;
  };
}
