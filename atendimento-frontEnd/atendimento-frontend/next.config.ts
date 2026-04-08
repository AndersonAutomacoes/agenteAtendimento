import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

/** Em dev, URL direta ao Java evita o proxy dos rewrites (limite ~30s) que causa ECONNRESET no chat longo. */
const defaultPublicApiBase =
  process.env.NODE_ENV === "development" ? backend : "";

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
        source: "/api/v1/tenant/settings",
        destination: `${backend}/api/v1/tenant/settings`,
      },
    ];
  },
};

export default nextConfig;
