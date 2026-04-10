import type { MetadataRoute } from "next";

/** Cores alinhadas ao dark mode (grafite + primário ciano/esmeralda). */
const THEME_BG = "#14151c";
const THEME_TINT = "#1e2836";

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "InteliZap",
    short_name: "InteliZap",
    description:
      "Plataforma de automação de atendimento por IA — dashboards, monitoramento e integração WhatsApp.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait-primary",
    background_color: THEME_BG,
    theme_color: THEME_TINT,
    categories: ["business", "productivity"],
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "maskable",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
      {
        src: "/icons/icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
    ],
  };
}
