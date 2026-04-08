import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/v1/chat", destination: `${backend}/api/v1/chat` },
      { source: "/api/v1/ingest", destination: `${backend}/v1/ingest` },
    ];
  },
};

export default nextConfig;
