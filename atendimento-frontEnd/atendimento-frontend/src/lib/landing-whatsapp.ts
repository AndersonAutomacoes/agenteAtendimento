/**
 * WhatsApp da landing: {@code LANDING_WHATSAPP_PHONE} é lido em runtime no servidor (Docker/.env na VPS).
 * {@code NEXT_PUBLIC_LANDING_WHATSAPP_PHONE} continua válido no build (dev ou build-args na imagem).
 */
export function normalizeWhatsappDigits(raw: string | undefined | null): string {
  return (raw ?? "").replace(/\D/g, "");
}

export function buildWhatsappMeHref(digits: string, prefill?: string | null): string | null {
  const d = normalizeWhatsappDigits(digits);
  if (!d) return null;
  const p = prefill?.trim();
  const q = p ? `?text=${encodeURIComponent(p)}` : "";
  return `https://wa.me/${d}${q}`;
}

/** Resolve no Node (SSR / Route Handler): suporta env sem prefixo NEXT_PUBLIC. */
export function resolveLandingWhatsAppHrefForServer(): string | null {
  const digits = normalizeWhatsappDigits(
      process.env.LANDING_WHATSAPP_PHONE || process.env.NEXT_PUBLIC_LANDING_WHATSAPP_PHONE,
  );
  const prefill =
      process.env.LANDING_WHATSAPP_PREFILL?.trim()
          || process.env.NEXT_PUBLIC_LANDING_WHATSAPP_PREFILL?.trim()
          || null;
  return buildWhatsappMeHref(digits, prefill);
}
