import { loadEnvConfig } from "@next/env";
import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

/** Carrega `.env` subindo a partir de `cwd` (funciona com `npm run dev` no frontend ou na raiz do repo). */
for (let dir = process.cwd(), i = 0; i < 5; i++) {
  if (existsSync(join(dir, ".env"))) {
    loadEnvConfig(dir);
  }
  const parent = resolve(dir, "..");
  if (parent === dir) break;
  dir = parent;
}

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

/** Em dev, URL direta ao Java evita o proxy dos rewrites (limite ~30s) que causa ECONNRESET no chat longo. */
const defaultPublicApiBase =
  process.env.NODE_ENV === "development" ? backend : "";

/**
 * Hostnames permitidos para HMR, fontes dev, etc., ao abrir o app por outro host na LAN.
 * Com vírgula, sem protocolo — ex.: NEXT_DEV_ALLOWED_ORIGINS=192.168.15.5
 *
 * Faz fallback lendo `.env` na subida de diretórios: `loadEnvConfig` nem sempre popula
 * `process.env` antes deste módulo ser avaliado em todos os fluxos do Next.
 */
function readAllowedDevOriginsFromEnvFiles(): string[] {
  const fromProcess = process.env.NEXT_DEV_ALLOWED_ORIGINS;
  if (fromProcess) {
    return fromProcess
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
  }
  let dir = process.cwd();
  for (let i = 0; i < 5; i++) {
    const envPath = join(dir, ".env");
    if (existsSync(envPath)) {
      const text = readFileSync(envPath, "utf8");
      const m = text.match(/^NEXT_DEV_ALLOWED_ORIGINS=(.+)$/m);
      if (m?.[1]) {
        return m[1]
          .split(",")
          .map((s) => s.trim().replace(/^['"]|['"]$/g, ""))
          .filter(Boolean);
      }
    }
    const parent = resolve(dir, "..");
    if (parent === dir) break;
    dir = parent;
  }
  return [];
}

const devAllowedOrigins =
  process.env.NODE_ENV !== "production" ? readAllowedDevOriginsFromEnvFiles() : [];

const nextConfig: NextConfig = {
  env: {
    NEXT_PUBLIC_API_BASE:
      process.env.NEXT_PUBLIC_API_BASE?.trim() || defaultPublicApiBase,
  },
  async rewrites() {
    return [
      { source: "/api/v1/chat", destination: `${backend}/api/v1/chat` },
      { source: "/api/v1/ingest", destination: `${backend}/v1/ingest` },
      {
        source: "/api/v1/bot-settings",
        destination: `${backend}/v1/bot-settings`,
      },
      {
        source: "/api/v1/auth/register",
        destination: `${backend}/v1/auth/register`,
      },
      {
        source: "/api/v1/auth/me",
        destination: `${backend}/v1/auth/me`,
      },
      {
        source: "/api/v1/tenant/settings",
        destination: `${backend}/api/v1/tenant/settings`,
      },
      {
        source: "/api/v1/messages",
        destination: `${backend}/api/v1/messages`,
      },
      {
        source: "/api/v1/messages/:path*",
        destination: `${backend}/api/v1/messages/:path*`,
      },
      {
        source: "/api/v1/conversations/:path*",
        destination: `${backend}/api/v1/conversations/:path*`,
      },
      {
        source: "/api/v1/dashboard/summary",
        destination: `${backend}/api/v1/dashboard/summary`,
      },
      {
        source: "/api/v1/analytics/intents",
        destination: `${backend}/api/v1/analytics/intents`,
      },
      {
        source: "/api/v1/analytics/export",
        destination: `${backend}/api/v1/analytics/export`,
      },
      {
        source: "/api/v1/knowledge-base",
        destination: `${backend}/api/v1/knowledge-base`,
      },
      {
        source: "/api/v1/knowledge-base/:batchId",
        destination: `${backend}/api/v1/knowledge-base/:batchId`,
      },
      {
        source: "/api/v1/appointments",
        destination: `${backend}/api/v1/appointments`,
      },
      {
        source: "/api/v1/appointments/:path*",
        destination: `${backend}/api/v1/appointments/:path*`,
      },
      {
        source: "/api/v1/crm/summary",
        destination: `${backend}/api/v1/crm/summary`,
      },
      {
        source: "/api/v1/crm/customers/:path*",
        destination: `${backend}/api/v1/crm/customers/:path*`,
      },
      {
        source: "/api/v1/crm/opportunities",
        destination: `${backend}/api/v1/crm/opportunities`,
      },
      {
        source: "/api/v1/crm/opportunities/:path*",
        destination: `${backend}/api/v1/crm/opportunities/:path*`,
      },
    ];
  },
  ...(devAllowedOrigins.length > 0 ? { allowedDevOrigins: devAllowedOrigins } : {}),
};

const withIntl = withNextIntl(nextConfig);

export default {
  ...withIntl,
  ...(devAllowedOrigins.length > 0
    ? { allowedDevOrigins: devAllowedOrigins }
    : {}),
} satisfies NextConfig;
